package com.example.ui.screens.video

import com.example.ui.screens.video.VideoMuteViewModel
import com.example.ui.screens.video.VideoRotateViewModel
import com.example.ui.screens.video.VideoCropViewModel
import com.example.ui.screens.video.VideoSpeedViewModel
import com.example.ui.screens.video.VideoFrameExtractorViewModel
import com.example.core.video.VideoCompressorViewModel
import com.example.ui.screens.trimmer.VideoTrimmerViewModel
import com.example.core.trimmer.VideoTrimmerUtils
import com.example.core.video.FrameImageFormat
import com.example.core.video.VideoCropPreset
import com.example.core.video.VideoRotationAngle
import com.example.core.video.VideoSpeedPreset
import com.example.core.video.VideoStudioEngine
import com.example.ui.navigation.Screen
import com.example.ui.screens.tools.ToolCatalog
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VideoStudioConsolidationTest {

    @Test
    fun `tool catalog registers Video Studio and removes old source tool cards`() {
        val allTools = ToolCatalog.getAllTools().associateBy { it.id }

        // 1. Assert Video Studio card is registered
        val videoStudio = allTools["media_video_studio"]
        assertNotNull("media_video_studio must be registered in catalog", videoStudio)
        assertEquals(Screen.VideoStudio.route, videoStudio?.route)
        assertEquals("Video Studio", videoStudio?.title)

        // 2. Assert old 6 consolidated cards are no longer top-level cards in catalog
        assertNull("media_video_trim should no longer be a top-level card", allTools["media_video_trim"])
        assertNull("media_video_mute should no longer be a top-level card", allTools["media_video_mute"])
        assertNull("media_video_rotate should no longer be a top-level card", allTools["media_video_rotate"])
        assertNull("media_video_crop should no longer be a top-level card", allTools["media_video_crop"])
        assertNull("media_video_speed should no longer be a top-level card", allTools["media_video_speed"])
        assertNull("media_video_frame should no longer be a top-level card", allTools["media_video_frame"])

        // 3. Catalog count assertion
        assertEquals(ToolCatalog.getAllTools().size, allTools.size)
    }

    @Test
    fun `video crop presets and normalized bounds calculate accurately`() {
        // Preset aspect ratios
        assertEquals(1.0f, VideoCropPreset.SQUARE_1_1.aspectRatio, 0.001f)
        assertEquals(0.8f, VideoCropPreset.PORTRAIT_4_5.aspectRatio, 0.001f)
        assertEquals(9f / 16f, VideoCropPreset.STORY_9_16.aspectRatio, 0.001f)
        assertEquals(16f / 9f, VideoCropPreset.LANDSCAPE_16_9.aspectRatio, 0.001f)

        // Landscape 16:9 source (1.777), cropping to 1:1 square
        val (l1, r1, b1, t1) = VideoStudioEngine.calculateNormalizedCropBounds(16f / 9f, 1f)
        assertTrue(l1 < 0f && r1 > 0f)
        assertEquals(-1f, b1, 0.001f)
        assertEquals(1f, t1, 0.001f)
        assertEquals(l1 * -1f, r1, 0.001f) // Symmetric

        // Portrait 9:16 source (0.5625), cropping to 1:1 square
        val (l2, r2, b2, t2) = VideoStudioEngine.calculateNormalizedCropBounds(9f / 16f, 1f)
        assertEquals(-1f, l2, 0.001f)
        assertEquals(1f, r2, 0.001f)
        assertTrue(b2 < 0f && t2 > 0f)
        assertEquals(b2 * -1f, t2, 0.001f) // Symmetric
    }

    @Test
    fun `video speed presets have correct speed multipliers and non-empty labels`() {
        val multipliers = VideoSpeedPreset.entries.map { it.multiplier }
        assertTrue(multipliers.contains(0.5f))
        assertTrue(multipliers.contains(0.75f))
        assertTrue(multipliers.contains(1.0f))
        assertTrue(multipliers.contains(1.25f))
        assertTrue(multipliers.contains(1.5f))
        assertTrue(multipliers.contains(2.0f))

        VideoSpeedPreset.entries.forEach {
            assertTrue(it.label.isNotEmpty())
        }
    }

    @Test
    fun `video rotation angles cover 90, 180, and 270 degrees`() {
        val angles = VideoRotationAngle.entries.map { it.degrees }
        assertEquals(listOf(90, 180, 270), angles)
    }

    @Test
    fun `frame image formats define correct extensions and mime types`() {
        assertEquals(".jpg", FrameImageFormat.JPEG.extension)
        assertEquals("image/jpeg", FrameImageFormat.JPEG.mimeType)
        assertEquals(".png", FrameImageFormat.PNG.extension)
        assertEquals("image/png", FrameImageFormat.PNG.mimeType)
    }

    @Test
    fun `duration and file size formatters format accurately`() {
        assertEquals("00:05", VideoTrimmerUtils.formatDuration(5000L))
        assertEquals("01:30", VideoTrimmerUtils.formatDuration(90000L))
        assertEquals("01:00:00", VideoTrimmerUtils.formatDuration(3600000L))

        assertEquals("500 B", VideoTrimmerUtils.formatFileSize(500L))
        assertEquals("1.0 MB", VideoTrimmerUtils.formatFileSize(1024L * 1024L))
    }

    @Test
    fun `video studio viewmodels expose exact Application JVM constructor and construct via factory`() {
        val vms = listOf(
            VideoTrimmerViewModel::class.java,
            VideoMuteViewModel::class.java,
            VideoRotateViewModel::class.java,
            VideoCropViewModel::class.java,
            VideoSpeedViewModel::class.java,
            VideoFrameExtractorViewModel::class.java,
            VideoCompressorViewModel::class.java
        )

        for (clazz in vms) {
            val constructor = clazz.getConstructor(android.app.Application::class.java)
            assertNotNull("Constructor for ${clazz.simpleName} must exist", constructor)
        }

        val application = org.robolectric.RuntimeEnvironment.getApplication()
        val factory = androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.getInstance(application)

        val trimmerVm = factory.create(VideoTrimmerViewModel::class.java)
        assertNotNull(trimmerVm)

        val muteVm = factory.create(VideoMuteViewModel::class.java)
        assertNotNull(muteVm)

        val rotateVm = factory.create(VideoRotateViewModel::class.java)
        assertNotNull(rotateVm)

        val cropVm = factory.create(VideoCropViewModel::class.java)
        assertNotNull(cropVm)

        val speedVm = factory.create(VideoSpeedViewModel::class.java)
        assertNotNull(speedVm)

        val frameVm = factory.create(VideoFrameExtractorViewModel::class.java)
        assertNotNull(frameVm)

        val compressorVm = factory.create(VideoCompressorViewModel::class.java)
        assertNotNull(compressorVm)
    }
}
