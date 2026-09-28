package com.example.ui.screens.image

import android.graphics.Rect
import androidx.compose.ui.geometry.Offset
import com.example.data.util.RecognizedTextRegion
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ImageTextEditorTransformTest {

    @Test
    fun `test A - fit-to-screen bitmap mapping for landscape image`() {
        // Bitmap: 1000 x 500 (aspect 2.0). Container: 500 x 500 (aspect 1.0)
        // Expected: displayed width = 500, displayed height = 250, offset X = 0, offset Y = 125, scale = 0.5
        val baseFit = ImageTextEditorTransform.computeBaseFit(
            bitmapWidth = 1000,
            bitmapHeight = 500,
            containerWidth = 500f,
            containerHeight = 500f
        )

        assertEquals(500f, baseFit.baseWidth, 0.01f)
        assertEquals(250f, baseFit.baseHeight, 0.01f)
        assertEquals(0f, baseFit.baseOffsetX, 0.01f)
        assertEquals(125f, baseFit.baseOffsetY, 0.01f)
        assertEquals(0.5f, baseFit.baseScale, 0.01f)
    }

    @Test
    fun `test B - aspect-ratio letterbox offset for portrait image`() {
        // Bitmap: 400 x 800 (aspect 0.5). Container: 600 x 600 (aspect 1.0)
        // Expected: displayed width = 300, displayed height = 600, offset X = 150, offset Y = 0, scale = 0.75
        val baseFit = ImageTextEditorTransform.computeBaseFit(
            bitmapWidth = 400,
            bitmapHeight = 800,
            containerWidth = 600f,
            containerHeight = 600f
        )

        assertEquals(300f, baseFit.baseWidth, 0.01f)
        assertEquals(600f, baseFit.baseHeight, 0.01f)
        assertEquals(150f, baseFit.baseOffsetX, 0.01f)
        assertEquals(0f, baseFit.baseOffsetY, 0.01f)
        assertEquals(0.75f, baseFit.baseScale, 0.01f)
    }

    @Test
    fun `test C - OCR bitmap rectangle to displayed rectangle at 1x`() {
        // Bitmap: 1000 x 1000. Container: 500 x 500. scale = 0.5, offset = (0, 0)
        val baseFit = ImageTextEditorTransform.computeBaseFit(
            bitmapWidth = 1000,
            bitmapHeight = 1000,
            containerWidth = 500f,
            containerHeight = 500f
        )

        val ocrRect = Rect(100, 200, 300, 400) // width 200, height 200
        val screenRect = ImageTextEditorTransform.regionToScreenRect(
            regionRect = ocrRect,
            baseFit = baseFit,
            containerWidth = 500f,
            containerHeight = 500f,
            userScale = 1f,
            panX = 0f,
            panY = 0f
        )

        assertEquals(50f, screenRect.left, 0.01f)
        assertEquals(100f, screenRect.top, 0.01f)
        assertEquals(150f, screenRect.right, 0.01f)
        assertEquals(200f, screenRect.bottom, 0.01f)
        assertEquals(100f, screenRect.width, 0.01f)
        assertEquals(100f, screenRect.height, 0.01f)
    }

    @Test
    fun `test D - displayed tap to bitmap coordinate inverse mapping at 1x`() {
        val baseFit = ImageTextEditorTransform.computeBaseFit(
            bitmapWidth = 1000,
            bitmapHeight = 500,
            containerWidth = 500f,
            containerHeight = 500f
        ) // scale = 0.5, offsetX = 0, offsetY = 125

        // Tap at (100, 175) on screen
        val bitmapPoint = ImageTextEditorTransform.screenToBitmap(
            screenX = 100f,
            screenY = 175f,
            baseFit = baseFit,
            containerWidth = 500f,
            containerHeight = 500f,
            userScale = 1f,
            panX = 0f,
            panY = 0f
        )

        // bx = (100 - 0) / 0.5 = 200, by = (175 - 125) / 0.5 = 100
        assertEquals(200f, bitmapPoint.x, 0.01f)
        assertEquals(100f, bitmapPoint.y, 0.01f)

        // Forward map back must be exactly (100, 175)
        val screenPoint = ImageTextEditorTransform.bitmapToScreen(
            bx = bitmapPoint.x,
            by = bitmapPoint.y,
            baseFit = baseFit,
            containerWidth = 500f,
            containerHeight = 500f,
            userScale = 1f,
            panX = 0f,
            panY = 0f
        )
        assertEquals(100f, screenPoint.x, 0.01f)
        assertEquals(175f, screenPoint.y, 0.01f)
    }

    @Test
    fun `test E - mapping while zoomed 2x without pan`() {
        val baseFit = ImageTextEditorTransform.computeBaseFit(
            bitmapWidth = 500,
            bitmapHeight = 500,
            containerWidth = 500f,
            containerHeight = 500f
        ) // scale = 1.0, offset = (0, 0), center = (250, 250)

        // Center of bitmap (250, 250) should remain at screen center (250, 250)
        val centerScreen = ImageTextEditorTransform.bitmapToScreen(
            bx = 250f,
            by = 250f,
            baseFit = baseFit,
            containerWidth = 500f,
            containerHeight = 500f,
            userScale = 2f,
            panX = 0f,
            panY = 0f
        )
        assertEquals(250f, centerScreen.x, 0.01f)
        assertEquals(250f, centerScreen.y, 0.01f)

        // Point (150, 150) -> xFit = 150, relative to 250 is -100. Scaled by 2x = -200 -> screen 50
        val ptScreen = ImageTextEditorTransform.bitmapToScreen(
            bx = 150f,
            by = 150f,
            baseFit = baseFit,
            containerWidth = 500f,
            containerHeight = 500f,
            userScale = 2f,
            panX = 0f,
            panY = 0f
        )
        assertEquals(50f, ptScreen.x, 0.01f)
        assertEquals(50f, ptScreen.y, 0.01f)

        // Inverse mapping of screen (50, 50) must yield (150, 150)
        val inv = ImageTextEditorTransform.screenToBitmap(
            screenX = 50f,
            screenY = 50f,
            baseFit = baseFit,
            containerWidth = 500f,
            containerHeight = 500f,
            userScale = 2f,
            panX = 0f,
            panY = 0f
        )
        assertEquals(150f, inv.x, 0.01f)
        assertEquals(150f, inv.y, 0.01f)
    }

    @Test
    fun `test F - mapping while zoomed 2x and panned by (30, -40)`() {
        val baseFit = ImageTextEditorTransform.computeBaseFit(
            bitmapWidth = 500,
            bitmapHeight = 500,
            containerWidth = 500f,
            containerHeight = 500f
        )

        val screenPt = ImageTextEditorTransform.bitmapToScreen(
            bx = 250f,
            by = 250f,
            baseFit = baseFit,
            containerWidth = 500f,
            containerHeight = 500f,
            userScale = 2f,
            panX = 30f,
            panY = -40f
        )
        assertEquals(280f, screenPt.x, 0.01f)
        assertEquals(210f, screenPt.y, 0.01f)

        val inv = ImageTextEditorTransform.screenToBitmap(
            screenX = 280f,
            screenY = 210f,
            baseFit = baseFit,
            containerWidth = 500f,
            containerHeight = 500f,
            userScale = 2f,
            panX = 30f,
            panY = -40f
        )
        assertEquals(250f, inv.x, 0.01f)
        assertEquals(250f, inv.y, 0.01f)
    }

    @Test
    fun `test G - small-region hit testing and smallest overlapping priority`() {
        val baseFit = ImageTextEditorTransform.computeBaseFit(
            bitmapWidth = 1000,
            bitmapHeight = 1000,
            containerWidth = 500f,
            containerHeight = 500f
        ) // scale = 0.5

        // Paragraph box enclosing word box
        val paragraphRegion = RecognizedTextRegion(
            id = "paragraph_1",
            text = "Big paragraph containing word",
            boundingBox = Rect(100, 100, 500, 300) // 400x200
        )
        val wordRegion = RecognizedTextRegion(
            id = "word_1",
            text = "word",
            boundingBox = Rect(120, 120, 180, 160) // 60x40 (tight box)
        )

        val regions = listOf(paragraphRegion, wordRegion)

        // Tap inside the word region: screen space = (120*0.5 + 5, 120*0.5 + 5) = (65, 65)
        val selectedId = ImageTextEditorTransform.findSelectedRegion(
            touchX = 65f,
            touchY = 65f,
            regions = regions,
            baseFit = baseFit,
            containerWidth = 500f,
            containerHeight = 500f,
            userScale = 1f,
            panX = 0f,
            panY = 0f,
            hitSlopPx = 10f,
            maxFallbackDistancePx = 30f
        )

        // Must prefer the tightest/smallest region (word_1)
        assertEquals("word_1", selectedId)
    }

    @Test
    fun `test H - tapping empty space does not incorrectly select distant text`() {
        val baseFit = ImageTextEditorTransform.computeBaseFit(
            bitmapWidth = 1000,
            bitmapHeight = 1000,
            containerWidth = 500f,
            containerHeight = 500f
        )

        val region = RecognizedTextRegion(
            id = "text_corner",
            text = "Corner text",
            boundingBox = Rect(10, 10, 100, 50) // screen: 5..50, 5..25
        )

        // Tap in middle of screen (250, 250) - far away from corner
        val selectedId = ImageTextEditorTransform.findSelectedRegion(
            touchX = 250f,
            touchY = 250f,
            regions = listOf(region),
            baseFit = baseFit,
            containerWidth = 500f,
            containerHeight = 500f,
            userScale = 1f,
            panX = 0f,
            panY = 0f,
            hitSlopPx = 10f,
            maxFallbackDistancePx = 30f
        )

        assertNull("Tapping distant empty space must return null", selectedId)
    }
}
