package com.example.ui.screens.image

import android.graphics.Bitmap
import android.graphics.Color
import com.example.core.image.OutputFormat
import com.example.data.util.ImageProcessingPipeline
import com.example.ui.navigation.Screen
import com.example.ui.screens.tools.ToolCatalog
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ImageStudioConsolidationTest {

    @Test
    fun `tool catalog registers Image Studio with unique ID and valid route`() {
        val allTools = ToolCatalog.getAllTools().associateBy { it.id }

        // Assert Image Studio card is registered
        val imageStudio = allTools["media_image_studio"]
        assertNotNull("media_image_studio must be registered in catalog", imageStudio)
        assertEquals(Screen.ImageStudio.route, imageStudio?.route)
        assertEquals("Image Studio", imageStudio?.title)

        // Assert old consolidated cards are no longer top-level cards in catalog
        assertNull("media_img_comp should no longer be a top-level card", allTools["media_img_comp"])
        assertNull("media_photo_resize should no longer be a top-level card", allTools["media_photo_resize"])
        assertNull("media_social_size should no longer be a top-level card", allTools["media_social_size"])
        assertNull("media_watermark should no longer be a top-level card", allTools["media_watermark"])
        assertNull("media_image_convert should no longer be a top-level card", allTools["media_image_convert"])
        assertNull("media_batch_resize should no longer be a top-level card", allTools["media_batch_resize"])
        assertNull("media_batch_watermark should no longer be a top-level card", allTools["media_batch_watermark"])
        assertNull("media_pfp_maker should no longer be a top-level card", allTools["media_pfp_maker"])

        // Total catalog count assertion
        assertEquals(ToolCatalog.getAllTools().size, allTools.size)
    }

    @Test
    fun `IMAGE_STUDIO_VIEWMODEL_FACTORY_CONTRACT_TEST - image studio viewmodels expose exact JVM constructor and construct via factory`() {
        val application = org.robolectric.RuntimeEnvironment.getApplication()
        val factory = androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.getInstance(application)

        // 8 AndroidViewModels instantiated with AndroidViewModelFactory
        val androidVms = listOf(
            ImageCompressorViewModel::class.java,
            SocialMediaSizeMakerViewModel::class.java,
            WatermarkViewModel::class.java,
            FormatConverterViewModel::class.java,
            BatchResizerViewModel::class.java,
            BatchWatermarkViewModel::class.java,
            AvatarMakerViewModel::class.java,
            CollageMakerViewModel::class.java
        )

        for (clazz in androidVms) {
            val constructor = clazz.getConstructor(android.app.Application::class.java)
            assertNotNull("Constructor for ${clazz.simpleName} must exist", constructor)
            val instance = factory.create(clazz)
            assertNotNull("Factory must instantiate ${clazz.simpleName}", instance)
        }

        // PhotoResizerCropperViewModel has 0-arg constructor instantiated via standard viewModel() / NewInstanceFactory
        val resizerConstructor = PhotoResizerCropperViewModel::class.java.getConstructor()
        assertNotNull("PhotoResizerCropperViewModel 0-arg constructor must exist", resizerConstructor)
        val resizerInstance = PhotoResizerCropperViewModel::class.java.getDeclaredConstructor().newInstance()
        assertNotNull(resizerInstance)
    }

    @Test
    fun `calculateResizeDimensions preserves aspect ratio and calculates correct bounds`() {
        // 1000x500 (2:1 aspect ratio), target 400x400 with preserveAspectRatio = true -> 400x200
        val (w1, h1) = ImageProcessingPipeline.calculateResizeDimensions(
            originalWidth = 1000,
            originalHeight = 500,
            targetWidth = 400,
            targetHeight = 400,
            preserveAspectRatio = true
        )
        assertEquals(400, w1)
        assertEquals(200, h1)

        // 500x1000 (1:2 aspect ratio), target 400x400 with preserveAspectRatio = true -> 200x400
        val (w2, h2) = ImageProcessingPipeline.calculateResizeDimensions(
            originalWidth = 500,
            originalHeight = 1000,
            targetWidth = 400,
            targetHeight = 400,
            preserveAspectRatio = true
        )
        assertEquals(200, w2)
        assertEquals(400, h2)

        // preserveAspectRatio = false -> exactly targetWidth x targetHeight
        val (w3, h3) = ImageProcessingPipeline.calculateResizeDimensions(
            originalWidth = 1000,
            originalHeight = 500,
            targetWidth = 800,
            targetHeight = 600,
            preserveAspectRatio = false
        )
        assertEquals(800, w3)
        assertEquals(600, h3)
    }

    @Test
    fun `calculateResizeDimensions respects noUpscale constraint`() {
        // Original 400x300, target 800x600, noUpscale = true -> remains 400x300
        val (w, h) = ImageProcessingPipeline.calculateResizeDimensions(
            originalWidth = 400,
            originalHeight = 300,
            targetWidth = 800,
            targetHeight = 600,
            preserveAspectRatio = true,
            noUpscale = true
        )
        assertEquals(400, w)
        assertEquals(300, h)
    }

    @Test
    fun `calculateLongEdgeDimensions scales long edge accurately`() {
        // Landscape: 1920x1080 -> long edge target 960 -> 960x540
        val (w1, h1) = ImageProcessingPipeline.calculateLongEdgeDimensions(
            originalWidth = 1920,
            originalHeight = 1080,
            targetLongEdge = 960,
            noUpscale = false
        )
        assertEquals(960, w1)
        assertEquals(540, h1)

        // Portrait: 1080x1920 -> long edge target 960 -> 540x960
        val (w2, h2) = ImageProcessingPipeline.calculateLongEdgeDimensions(
            originalWidth = 1080,
            originalHeight = 1920,
            targetLongEdge = 960,
            noUpscale = false
        )
        assertEquals(540, w2)
        assertEquals(960, h2)

        // noUpscale with smaller image -> remains unchanged
        val (w3, h3) = ImageProcessingPipeline.calculateLongEdgeDimensions(
            originalWidth = 640,
            originalHeight = 480,
            targetLongEdge = 1200,
            noUpscale = true
        )
        assertEquals(640, w3)
        assertEquals(480, h3)
    }

    @Test
    fun `flattenAlpha flattens transparent bitmap onto background color`() {
        val src = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        src.setHasAlpha(true)
        // Leave left half transparent, paint right half red
        for (x in 0 until 50) {
            for (y in 0 until 100) {
                src.setPixel(x, y, Color.TRANSPARENT)
            }
        }
        for (x in 50 until 100) {
            for (y in 0 until 100) {
                src.setPixel(x, y, Color.RED)
            }
        }

        val flattened = ImageProcessingPipeline.flattenAlpha(src, Color.WHITE)
        assertEquals(100, flattened.width)
        assertEquals(100, flattened.height)

        // Pixel from formerly transparent region should now match background color
        val pixelTransparentArea = flattened.getPixel(10, 10)
        assertEquals(Color.WHITE, pixelTransparentArea)

        // Red area should remain red
        val pixelRedArea = flattened.getPixel(75, 50)
        assertEquals(Color.RED, pixelRedArea)
    }

    @Test
    fun `renderAvatar produces expected square dimensions and border ring`() {
        val src = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
        src.eraseColor(Color.BLUE)

        val avatar512 = ImageProcessingPipeline.renderAvatar(
            source = src,
            targetSize = 512,
            backgroundColor = Color.WHITE,
            borderColor = Color.GREEN,
            borderWidthPx = 10f
        )
        assertEquals(512, avatar512.width)
        assertEquals(512, avatar512.height)

        val avatar1024 = ImageProcessingPipeline.renderAvatar(
            source = src,
            targetSize = 1024,
            backgroundColor = null, // Transparent PNG
            borderColor = Color.RED,
            borderWidthPx = 16f
        )
        assertEquals(1024, avatar1024.width)
        assertEquals(1024, avatar1024.height)
    }

    @Test
    fun `format converter specifications check`() {
        assertEquals("image/jpeg", OutputFormat.JPEG.mimeType)
        assertFalse(OutputFormat.JPEG.supportsTransparency)
        assertTrue(OutputFormat.JPEG.supportsQuality)

        assertEquals("image/png", OutputFormat.PNG.mimeType)
        assertTrue(OutputFormat.PNG.supportsTransparency)
        assertFalse(OutputFormat.PNG.supportsQuality)

        assertEquals("image/webp", OutputFormat.WEBP.mimeType)
        assertTrue(OutputFormat.WEBP.supportsTransparency)
        assertTrue(OutputFormat.WEBP.supportsQuality)
    }

    @Test
    fun `image studio capability enum has all 10 required capabilities including collage and text editor`() {
        val caps = ImageStudioCapability.entries
        assertEquals(10, caps.size)

        val expected = setOf(
            ImageStudioCapability.COMPRESS,
            ImageStudioCapability.RESIZE_CROP,
            ImageStudioCapability.CONVERT,
            ImageStudioCapability.WATERMARK,
            ImageStudioCapability.BATCH_RESIZE,
            ImageStudioCapability.BATCH_WATERMARK,
            ImageStudioCapability.SOCIAL_SIZES,
            ImageStudioCapability.AVATAR_MAKER,
            ImageStudioCapability.COLLAGE,
            ImageStudioCapability.IMAGE_TEXT_EDITOR
        )
        assertEquals(expected, caps.toSet())
    }

    @Test
    fun `IMAGE_STUDIO_COLLAGE_INTEGRATION_TEST - phase 4A verification`() {
        // 1. Image Studio exposes Collage
        val collageCap = ImageStudioCapability.COLLAGE
        assertEquals("Collage", collageCap.title)
        assertEquals("studio_cap_collage", collageCap.tag)
        assertNotNull(collageCap.icon)

        // 2. All 8 existing Image Studio functions remain available
        val existingCaps = listOf(
            ImageStudioCapability.COMPRESS,
            ImageStudioCapability.RESIZE_CROP,
            ImageStudioCapability.CONVERT,
            ImageStudioCapability.WATERMARK,
            ImageStudioCapability.BATCH_RESIZE,
            ImageStudioCapability.BATCH_WATERMARK,
            ImageStudioCapability.SOCIAL_SIZES,
            ImageStudioCapability.AVATAR_MAKER
        )
        assertTrue(ImageStudioCapability.entries.containsAll(existingCaps))

        // 3. CollageMakerViewModel constructor/factory contract is valid
        val app = org.robolectric.RuntimeEnvironment.getApplication()
        val appConstructor = CollageMakerViewModel::class.java.getConstructor(android.app.Application::class.java)
        assertNotNull("CollageMakerViewModel(Application) constructor must exist", appConstructor)

        val contextConstructor = CollageMakerViewModel::class.java.getConstructor(android.content.Context::class.java)
        assertNotNull("CollageMakerViewModel(Context) constructor must exist", contextConstructor)

        val factory = androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.getInstance(app)
        val vmFromFactory = factory.create(CollageMakerViewModel::class.java)
        assertNotNull("ViewModelProvider factory must instantiate CollageMakerViewModel", vmFromFactory)
        assertEquals(CollageMakerUiState.Idle, vmFromFactory.uiState.value)

        // 4. Standalone route remains available
        assertEquals("collage_maker", Screen.CollageMaker.route)

        // 5. media_collage is removed from ToolCatalog in Phase 4B
        val allTools = ToolCatalog.getAllTools().associateBy { it.id }
        val collageTool = allTools["media_collage"]
        assertNull("media_collage must be removed from ToolCatalog in Phase 4B", collageTool)

        // 6. Catalog count is 36
        val totalToolsInCategories = ToolCatalog.categories.sumOf { it.tools.size }
        assertEquals(36, totalToolsInCategories)
        assertEquals(36, allTools.size)
        val uniqueIds = ToolCatalog.getAllTools().map { it.id }.toSet()
        assertEquals(36, uniqueIds.size)
    }

    @Test
    fun `IMAGE_STUDIO_IMAGE_TEXT_EDITOR_CONSOLIDATION_TEST - phase 5B cleanup verification`() {
        // 1. Image Studio exposes exactly 10 capabilities
        assertEquals(10, ImageStudioCapability.entries.size)

        // 2. IMAGE_TEXT_EDITOR capability exists
        val textEditorCap = ImageStudioCapability.IMAGE_TEXT_EDITOR
        assertEquals("Image Text Editor", textEditorCap.title)
        assertEquals("Remove, replace, or add text on images", textEditorCap.description)
        assertEquals("studio_cap_image_text_editor", textEditorCap.tag)
        assertNotNull(textEditorCap.icon)

        // 3. All 10 capabilities are preserved
        val expectedCaps = listOf(
            ImageStudioCapability.COMPRESS,
            ImageStudioCapability.RESIZE_CROP,
            ImageStudioCapability.CONVERT,
            ImageStudioCapability.WATERMARK,
            ImageStudioCapability.BATCH_RESIZE,
            ImageStudioCapability.BATCH_WATERMARK,
            ImageStudioCapability.SOCIAL_SIZES,
            ImageStudioCapability.AVATAR_MAKER,
            ImageStudioCapability.COLLAGE,
            ImageStudioCapability.IMAGE_TEXT_EDITOR
        )
        assertEquals(expectedCaps, ImageStudioCapability.entries)

        // 4. ImageTextEditorViewModel constructor/factory contract is valid
        val app = org.robolectric.RuntimeEnvironment.getApplication()
        val constructor = ImageTextEditorViewModel::class.java.getConstructor(com.example.data.util.ImageTextRecognizer::class.java)
        assertNotNull("ImageTextEditorViewModel(ImageTextRecognizer) constructor must exist", constructor)

        val recognizer = com.example.data.util.ImageTextRecognizer(app)
        val vm = ImageTextEditorViewModel(recognizer)
        assertNotNull("ImageTextEditorViewModel must instantiate with ImageTextRecognizer", vm)
        assertFalse(vm.uiState.value.isLoading)
        assertNull(vm.currentBitmap)

        // 5. Standalone route remains registered
        assertEquals("image_text_editor", Screen.ImageTextEditor.route)

        // 6. media_image_text_editor is removed from ToolCatalog in Phase 5B
        val allTools = ToolCatalog.getAllTools().associateBy { it.id }
        val editorTool = allTools["media_image_text_editor"]
        assertNull("media_image_text_editor must be removed from ToolCatalog in Phase 5B", editorTool)

        // 7. media_image_text_editor is absent from search results
        val searchResults = ToolCatalog.getAllTools().filter {
            it.id.equals("media_image_text_editor", ignoreCase = true) || it.title.equals("Image Text Remover & Editor", ignoreCase = true)
        }
        assertTrue("media_image_text_editor must not appear in search results", searchResults.isEmpty())

        // 8. media_image_studio is registered
        val studioTool = allTools["media_image_studio"]
        assertNotNull("media_image_studio must be registered", studioTool)
        assertEquals(Screen.ImageStudio.route, studioTool?.route)

        // 9. Catalog count is 36 with 36 unique IDs
        val totalToolsInCategoriesAfter = ToolCatalog.categories.sumOf { it.tools.size }
        assertEquals(36, totalToolsInCategoriesAfter)
        assertEquals(36, allTools.size)
        val uniqueIdsAfter = ToolCatalog.getAllTools().map { it.id }.toSet()
        assertEquals(36, uniqueIdsAfter.size)
    }
}
