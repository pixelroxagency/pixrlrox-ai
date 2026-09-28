package com.example.core.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageCompressorLogicTest {

    @Test
    fun testTargetDimensionCalculation_original() {
        val (w, h) = ImageCompressorEngine.calculateTargetDimensions(
            origWidth = 4000,
            origHeight = 3000,
            scale = ResizeScale.ORIGINAL
        )
        assertEquals(4000, w)
        assertEquals(3000, h)
    }

    @Test
    fun testTargetDimensionCalculation_fiftyPercent() {
        val (w, h) = ImageCompressorEngine.calculateTargetDimensions(
            origWidth = 4000,
            origHeight = 3000,
            scale = ResizeScale.FIFTY
        )
        assertEquals(2000, w)
        assertEquals(1500, h)
    }

    @Test
    fun testTargetDimensionCalculation_seventyFivePercent() {
        val (w, h) = ImageCompressorEngine.calculateTargetDimensions(
            origWidth = 4000,
            origHeight = 3000,
            scale = ResizeScale.SEVENTY_FIVE
        )
        assertEquals(3000, w)
        assertEquals(2250, h)
    }

    @Test
    fun testTargetDimensionCalculation_customMaxDimensionsPreservingAspectRatio() {
        // 4000 x 3000 (4:3 ratio) constrained to max 1920 x 1080
        val (w, h) = ImageCompressorEngine.calculateTargetDimensions(
            origWidth = 4000,
            origHeight = 3000,
            scale = ResizeScale.CUSTOM,
            customMaxWidth = 1920,
            customMaxHeight = 1080
        )
        assertEquals(1440, w)
        assertEquals(1080, h)
    }

    @Test
    fun testTargetDimensionCalculation_noUpscalingAllowed() {
        // Source 1000 x 800 requesting custom 2000 x 2000 -> must not exceed source dimensions
        val (w, h) = ImageCompressorEngine.calculateTargetDimensions(
            origWidth = 1000,
            origHeight = 800,
            scale = ResizeScale.CUSTOM,
            customMaxWidth = 2000,
            customMaxHeight = 2000
        )
        assertEquals(1000, w)
        assertEquals(800, h)
    }

    @Test
    fun testInSampleSizeCalculation() {
        val sampleSize1 = ImageCompressorEngine.calculateInSampleSize(4000, 3000, 4000, 3000)
        assertEquals(1, sampleSize1)

        val sampleSize2 = ImageCompressorEngine.calculateInSampleSize(4000, 3000, 2000, 1500)
        assertEquals(2, sampleSize2)

        val sampleSize4 = ImageCompressorEngine.calculateInSampleSize(4000, 3000, 1000, 750)
        assertEquals(4, sampleSize4)
    }

    @Test
    fun testSafeImageDisplayNameGeneration() {
        val filename = ImageOutputPublisher.generateSafeImageDisplayName("vacation photo #1.jpg", OutputFormat.JPEG, 1700000000000L)
        assertTrue(filename.startsWith("vacation_photo__1_compres"))
        assertTrue(filename.endsWith(".jpg"))
    }

    @Test
    fun testOutputFormatProperties() {
        assertTrue(OutputFormat.JPEG.supportsQuality)
        assertFalse(OutputFormat.JPEG.supportsTransparency)

        assertTrue(OutputFormat.WEBP.supportsQuality)
        assertTrue(OutputFormat.WEBP.supportsTransparency)

        assertFalse(OutputFormat.PNG.supportsQuality)
        assertTrue(OutputFormat.PNG.supportsTransparency)
    }
}
