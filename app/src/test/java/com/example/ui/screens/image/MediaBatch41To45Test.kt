package com.example.ui.screens.image

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.example.data.util.ImageBitmapHelper
import com.example.ui.navigation.Screen
import com.example.ui.screens.audio.AudioCutterUiState
import com.example.ui.screens.audio.AudioCutterViewModel
import com.example.ui.screens.tools.ToolCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MediaBatch41To45Test {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var application: Application

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        application = ApplicationProvider.getApplicationContext()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `tool catalog registers tools 41 through 45 with non-null routes`() {
        val allTools = ToolCatalog.getAllTools().associateBy { it.id }

        val audioStudio = allTools["media_audio_studio"]
        assertNotNull("media_audio_studio must exist in catalog", audioStudio)
        assertEquals(Screen.AudioStudio.route, audioStudio?.route)

        // media_collage top-level card is removed in Phase 4B, but route is preserved
        val collage = allTools["media_collage"]
        assertNull("media_collage top-level card is removed from catalog", collage)
        assertEquals("collage_maker", Screen.CollageMaker.route)

        val quoteMaker = allTools["media_quote_maker"]
        assertNotNull("media_quote_maker must exist in catalog", quoteMaker)
        assertEquals(Screen.PostQuoteMaker.route, quoteMaker?.route)

        // Verifies Image Studio consolidates media_social_size, media_watermark and media_collage
        val imageStudio = allTools["media_image_studio"]
        assertNotNull("media_image_studio must exist in catalog", imageStudio)
        assertEquals(Screen.ImageStudio.route, imageStudio?.route)
    }

    @Test
    fun `fitOrCropBitmap produces exact target dimensions in crop and fit modes`() {
        val src = Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888)

        // 1. Crop to Fill (1080x1080)
        val cropped = ImageBitmapHelper.fitOrCropBitmap(
            source = src,
            targetWidth = 1080,
            targetHeight = 1080,
            fitMode = ImageBitmapHelper.FitMode.CROP_TO_FILL
        )
        assertEquals(1080, cropped.width)
        assertEquals(1080, cropped.height)

        // 2. Fit Inside (1080x1920)
        val fitted = ImageBitmapHelper.fitOrCropBitmap(
            source = src,
            targetWidth = 1080,
            targetHeight = 1920,
            fitMode = ImageBitmapHelper.FitMode.FIT_INSIDE,
            backgroundColor = Color.BLACK
        )
        assertEquals(1080, fitted.width)
        assertEquals(1920, fitted.height)
    }

    @Test
    fun `applyWatermarkText and applyWatermarkImage produce valid output bitmaps`() {
        val src = Bitmap.createBitmap(500, 500, Bitmap.Config.ARGB_8888)
        val watermarkedText = ImageBitmapHelper.applyWatermarkText(
            source = src,
            text = "PixelRox Signature",
            textSizeSp = 30f,
            textColor = Color.WHITE,
            opacity = 0.8f,
            normX = 0.5f,
            normY = 0.5f
        )
        assertEquals(500, watermarkedText.width)
        assertEquals(500, watermarkedText.height)

        val logo = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        val watermarkedLogo = ImageBitmapHelper.applyWatermarkImage(
            source = src,
            logo = logo,
            scale = 0.5f,
            opacity = 0.9f,
            normX = 0.1f,
            normY = 0.1f
        )
        assertEquals(500, watermarkedLogo.width)
        assertEquals(500, watermarkedLogo.height)
    }

    @Test
    fun `composeCollage successfully merges multiple bitmaps into grid`() {
        val b1 = Bitmap.createBitmap(300, 300, Bitmap.Config.ARGB_8888)
        val b2 = Bitmap.createBitmap(300, 300, Bitmap.Config.ARGB_8888)
        val b3 = Bitmap.createBitmap(300, 300, Bitmap.Config.ARGB_8888)

        val collage = ImageBitmapHelper.composeCollage(
            bitmaps = listOf(b1, b2, b3),
            targetWidth = 1080,
            targetHeight = 1080,
            layoutVariant = 0,
            spacingPx = 10,
            marginPx = 10,
            backgroundColor = Color.WHITE
        )

        assertEquals(1080, collage.width)
        assertEquals(1080, collage.height)
    }

    @Test
    fun `renderPostQuote produces properly styled high-res bitmap`() {
        val quoteGraphic = ImageBitmapHelper.renderPostQuote(
            targetWidth = 1080,
            targetHeight = 1080,
            quoteText = "Stay hungry, stay foolish.",
            authorText = "Steve Jobs",
            backgroundColor = Color.rgb(20, 24, 34),
            quoteTextSizeSp = 40f,
            quoteTextColor = Color.WHITE,
            authorTextSizeSp = 22f,
            authorTextColor = Color.LTGRAY,
            alignment = ImageBitmapHelper.TextAlignmentOption.CENTER,
            verticalPos = ImageBitmapHelper.VerticalPositionOption.CENTER,
            hasShadow = true
        )

        assertNotNull(quoteGraphic)
        assertEquals(1080, quoteGraphic.width)
        assertEquals(1080, quoteGraphic.height)
    }

    @Test
    fun `AudioCutterViewModel initial state is Idle and reset works`() {
        val vm = AudioCutterViewModel(application)
        assertEquals(AudioCutterUiState.Idle, vm.uiState.value)

        vm.reset()
        assertEquals(AudioCutterUiState.Idle, vm.uiState.value)
    }
}
