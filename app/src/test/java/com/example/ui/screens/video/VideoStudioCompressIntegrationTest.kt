package com.example.ui.screens.video

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import com.example.core.video.VideoCompressorViewModel
import com.example.ui.navigation.Screen
import com.example.ui.screens.tools.ToolCatalog
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VideoStudioCompressIntegrationTest {

    @Test
    fun `video studio exposes compress and preserves existing six functions`() {
        val capabilities = VideoStudioCapability.entries
        assertEquals("VideoStudioCapability must now contain exactly 7 capabilities", 7, capabilities.size)

        // 1. Verify Compress capability
        val compressCap = capabilities.find { it == VideoStudioCapability.COMPRESS }
        assertNotNull("COMPRESS capability must be exposed in VideoStudioCapability", compressCap)
        assertEquals("Video Compressor", compressCap?.title)
        assertEquals("studio_cap_compress", compressCap?.tag)

        // 2. Verify existing six capabilities remain intact
        assertTrue(capabilities.contains(VideoStudioCapability.TRIM))
        assertTrue(capabilities.contains(VideoStudioCapability.MUTE))
        assertTrue(capabilities.contains(VideoStudioCapability.ROTATE))
        assertTrue(capabilities.contains(VideoStudioCapability.CROP))
        assertTrue(capabilities.contains(VideoStudioCapability.SPEED))
        assertTrue(capabilities.contains(VideoStudioCapability.FRAME_EXTRACT))
    }

    @Test
    fun `video compressor viewmodel constructor and factory contracts are valid`() {
        val clazz = VideoCompressorViewModel::class.java

        // 1. JVM Constructor taking Application (required for AndroidViewModelFactory reflection)
        val appConstructor = clazz.getConstructor(Application::class.java)
        assertNotNull("<init>(Application) constructor must physically exist", appConstructor)

        // 2. JVM Constructor taking Context (backward compatibility for tests and standalone callers)
        val contextConstructor = clazz.getConstructor(android.content.Context::class.java)
        assertNotNull("<init>(Context) constructor must physically exist", contextConstructor)

        // 3. AndroidViewModelFactory instantiation
        val app = RuntimeEnvironment.getApplication()
        val factory = ViewModelProvider.AndroidViewModelFactory.getInstance(app)
        val viewModel = factory.create(VideoCompressorViewModel::class.java)
        assertNotNull("ViewModel must instantiate successfully via AndroidViewModelFactory", viewModel)
    }

    @Test
    fun `video compressor catalog cleanup - absent from catalog and search, route preserved, count 46`() {
        // 1. Route check preserved
        assertEquals("video_compressor", Screen.VideoCompressor.route)

        // 2. ToolCatalog verifies media_compressor is absent from top-level catalog
        val allTools = ToolCatalog.getAllTools().associateBy { it.id }
        assertNull("media_compressor top-level card must be removed from catalog", allTools["media_compressor"])

        // 3. media_compressor is absent from catalog-derived search
        val searchResults = ToolCatalog.getAllTools().filter {
            it.id.equals("media_compressor", ignoreCase = true) || it.title.equals("Video Compressor", ignoreCase = true)
        }
        assertTrue("Catalog-derived search for media_compressor or Video Compressor must be empty", searchResults.isEmpty())

        // 4. Catalog count: visible count = 36, searchable count = 36, unique IDs = 36
        val totalToolsInCategories = ToolCatalog.categories.sumOf { it.tools.size }
        assertEquals(36, totalToolsInCategories)
        assertEquals(36, allTools.size)
        val uniqueIds = ToolCatalog.getAllTools().map { it.id }.toSet()
        assertEquals(36, uniqueIds.size)
    }
}
