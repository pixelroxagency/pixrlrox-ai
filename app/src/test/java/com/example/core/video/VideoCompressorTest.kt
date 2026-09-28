package com.example.core.video

import org.junit.Assert.*
import org.junit.Test

class VideoCompressorTest {

    @Test
    fun testPresetDimensionsLandscapeNoUpscale() {
        // Source 3840x2160 (4K), HIGH_QUALITY max 1920
        val (w, h) = VideoCompressionPreset.HIGH_QUALITY.calculateTargetDimensions(3840, 2160)
        assertEquals(1920, w)
        assertEquals(1080, h)
    }

    @Test
    fun testPresetDimensions1920x1080ToSmallPreset() {
        // User scenario: Source 1920x1080, Preset SMALL max 854
        val (w, h) = VideoCompressionPreset.SMALL.calculateTargetDimensions(1920, 1080)
        assertEquals(854, w)
        assertEquals(480, h)
        assertEquals(0, w % 2)
        assertEquals(0, h % 2)
    }

    @Test
    fun testPresetDimensions1920x1080ToMediumPreset() {
        // User scenario: Source 1920x1080, Preset MEDIUM max 1280
        val (w, h) = VideoCompressionPreset.MEDIUM.calculateTargetDimensions(1920, 1080)
        assertEquals(1280, w)
        assertEquals(720, h)
        assertEquals(0, w % 2)
        assertEquals(0, h % 2)
    }

    @Test
    fun testPresetDimensionsPortraitNoUpscale() {
        // Source 2160x3840 (Portrait 4K), HIGH_QUALITY max 1920
        val (w, h) = VideoCompressionPreset.HIGH_QUALITY.calculateTargetDimensions(2160, 3840)
        assertEquals(1080, w)
        assertEquals(1920, h)
    }

    @Test
    fun testPresetDimensionsSourceSmallerThanPresetNeverUpscaled() {
        // Source 640x480 (VGA), HIGH_QUALITY max 1920 -> should NEVER upscale
        val (w, h) = VideoCompressionPreset.HIGH_QUALITY.calculateTargetDimensions(640, 480)
        assertEquals(640, w)
        assertEquals(480, h)

        // Source 640x360 with SMALL (854) -> should NOT upscale
        val (smallW, smallH) = VideoCompressionPreset.SMALL.calculateTargetDimensions(640, 360)
        assertEquals(640, smallW)
        assertEquals(360, smallH)
    }

    @Test
    fun testPresetDimensionsEvenNumberEnforcement() {
        // Source 1281x721 (odd dimensions), MEDIUM max 1280
        val (w, h) = VideoCompressionPreset.MEDIUM.calculateTargetDimensions(1281, 721)
        assertEquals(0, w % 2)
        assertEquals(0, h % 2)
    }

    @Test
    fun testPortraitResolution1080x1920ToSmallPreset() {
        // Portrait 1080x1920, SMALL max 854
        val (w, h) = VideoCompressionPreset.SMALL.calculateTargetDimensions(1080, 1920)
        assertEquals(854, h)
        assertEquals(480, w)
        assertEquals(0, w % 2)
        assertEquals(0, h % 2)
    }

    @Test
    fun testUserScenario8min40sec1080pVideoEstimateCalculation() {
        // Source video from user screenshot:
        // Duration: 8:40 = 520,000 ms
        val durationMs = (8 * 60 + 40) * 1000L // 520,000 ms
        val sourceBitrate = 20_000_000L // 20 Mbps source

        val smallEstimate = VideoCompressionPreset.SMALL.estimateOutputSize(durationMs, sourceBitrate)
        val mediumEstimate = VideoCompressionPreset.MEDIUM.estimateOutputSize(durationMs, sourceBitrate)
        val highEstimate = VideoCompressionPreset.HIGH_QUALITY.estimateOutputSize(durationMs, sourceBitrate)

        // Verify SMALL file size estimate is in the ~70-75 MB range for 8m40s at ~1 Mbps
        val smallMb = smallEstimate / (1024.0 * 1024.0)
        assertTrue("SMALL estimate for 8m40s should be ~70-75 MB, got $smallMb MB", smallMb in 68.0..78.0)

        // Verify strict monotonic ordering of presets
        assertTrue(smallEstimate < mediumEstimate)
        assertTrue(mediumEstimate < highEstimate)
    }
}

