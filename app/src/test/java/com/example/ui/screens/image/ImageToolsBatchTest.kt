package com.example.ui.screens.image

import android.graphics.Color
import android.graphics.Rect
import com.example.core.image.OutputFormat
import com.example.data.util.RecognizedImageResult
import com.example.data.util.RecognizedTextRegion
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.roundToInt

class ImageToolsBatchTest {

    // ==========================================
    // Tool #38: Image Text Remover & Editor Tests
    // ==========================================

    @Test
    fun tool38_recognizedTextRegionModel_creationAndEquality() {
        val rect = Rect(10, 20, 110, 70)
        val region = RecognizedTextRegion(
            id = "test-region-1",
            text = "PixelRox Text",
            boundingBox = rect
        )

        assertEquals("test-region-1", region.id)
        assertEquals("PixelRox Text", region.text)
        assertEquals(100, region.boundingBox.width())
        assertEquals(50, region.boundingBox.height())

        val result = RecognizedImageResult(
            fullText = "PixelRox Text",
            regions = listOf(region)
        )
        assertEquals(1, result.regions.size)
        assertEquals("PixelRox Text", result.fullText)
    }

    @Test
    fun tool38_replacementTextState_boundsAndValues() {
        var size = 24f
        val clampedSize1 = size.coerceIn(12f, 72f)
        assertEquals(24f, clampedSize1)

        size = 5f
        val clampedSize2 = size.coerceIn(12f, 72f)
        assertEquals(12f, clampedSize2)

        size = 120f
        val clampedSize3 = size.coerceIn(12f, 72f)
        assertEquals(72f, clampedSize3)

        val black = Color.BLACK
        val red = Color.RED
        assertNotEquals(black, red)
    }

    // ==========================================
    // Tool #39: Photo Resizer & Cropper Tests
    // ==========================================

    @Test
    fun tool39_aspectRatioFormulas_areAccurate() {
        assertEquals(1.0f, CropAspectRatio.SQUARE_1_1.ratio)
        assertEquals(0.8f, CropAspectRatio.PORTRAIT_4_5.ratio!!, 0.001f)
        assertEquals(9f / 16f, CropAspectRatio.VERTICAL_9_16.ratio!!, 0.001f)
        assertEquals(16f / 9f, CropAspectRatio.LANDSCAPE_16_9.ratio!!, 0.001f)
        assertNull(CropAspectRatio.FREE.ratio)
        assertNull(CropAspectRatio.ORIGINAL.ratio)
    }

    @Test
    fun tool39_aspectRatioLock_widthToHeightCalculation() {
        val croppedW = 1000
        val croppedH = 500
        val aspect = croppedW.toFloat() / croppedH.toFloat() // 2.0f

        val newW = 800
        val computedH = (newW / aspect).roundToInt()
        assertEquals(400, computedH)
    }

    @Test
    fun tool39_aspectRatioLock_heightToWidthCalculation() {
        val croppedW = 1080
        val croppedH = 1920
        val aspect = croppedW.toFloat() / croppedH.toFloat() // 0.5625f

        val newH = 960
        val computedW = (newH * aspect).roundToInt()
        assertEquals(540, computedW)
    }

    @Test
    fun tool39_noUpscale_clampsToCroppedDimensions() {
        val croppedW = 800
        val croppedH = 600

        val requestedW = 1200
        val requestedH = 900
        val clampedW = kotlin.math.min(requestedW, croppedW)
        val clampedH = kotlin.math.min(requestedH, croppedH)

        assertEquals(800, clampedW)
        assertEquals(600, clampedH)
    }

    @Test
    fun tool39_outputFormats_haveCorrectMimeAndExtensions() {
        assertEquals("image/jpeg", OutputFormat.JPEG.mimeType)
        assertEquals(".jpg", OutputFormat.JPEG.extension)
        assertTrue(OutputFormat.JPEG.supportsQuality)
        assertFalse(OutputFormat.JPEG.supportsTransparency)

        assertEquals("image/png", OutputFormat.PNG.mimeType)
        assertEquals(".png", OutputFormat.PNG.extension)
        assertFalse(OutputFormat.PNG.supportsQuality)
        assertTrue(OutputFormat.PNG.supportsTransparency)

        assertEquals("image/webp", OutputFormat.WEBP.mimeType)
        assertEquals(".webp", OutputFormat.WEBP.extension)
        assertTrue(OutputFormat.WEBP.supportsQuality)
        assertTrue(OutputFormat.WEBP.supportsTransparency)
    }

    // ==========================================
    // Tool #40: Image to Text OCR Tests
    // ==========================================

    @Test
    fun tool40_ocrUiState_distinguishesTextVsEmpty() {
        val emptyText = ""
        val emptyState = OcrUiState.Success(
            originalText = emptyText,
            hasText = emptyText.isNotBlank()
        )
        assertFalse(emptyState.hasText)

        val foundText = "Invoice #12345"
        val successState = OcrUiState.Success(
            originalText = foundText,
            hasText = foundText.isNotBlank()
        )
        assertTrue(successState.hasText)
        assertEquals("Invoice #12345", successState.originalText)
    }

    @Test
    fun tool40_ocrEditedText_preservesUserEdits() {
        var userText = "Raw OCR text with tyypo"
        // User corrects typo manually
        userText = userText.replace("tyypo", "typo")
        assertEquals("Raw OCR text with typo", userText)

        val words = userText.trim().split("\\s+".toRegex()).size
        assertEquals(5, words)
        assertEquals(23, userText.length)
    }
}
