package com.example.ui.screens.image

import android.graphics.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect as ComposeRect
import androidx.compose.ui.geometry.Size
import com.example.data.util.RecognizedTextRegion

data class BaseFit(
    val baseWidth: Float,
    val baseHeight: Float,
    val baseOffsetX: Float,
    val baseOffsetY: Float,
    val baseScale: Float
)

object ImageTextEditorTransform {

    fun computeBaseFit(
        bitmapWidth: Int,
        bitmapHeight: Int,
        containerWidth: Float,
        containerHeight: Float
    ): BaseFit {
        if (bitmapWidth <= 0 || bitmapHeight <= 0 || containerWidth <= 0f || containerHeight <= 0f) {
            return BaseFit(0f, 0f, 0f, 0f, 1f)
        }

        val imgAspect = bitmapWidth.toFloat() / bitmapHeight.toFloat()
        val containerAspect = containerWidth / containerHeight

        val (baseWidth, baseHeight) = if (imgAspect > containerAspect) {
            Pair(containerWidth, containerWidth / imgAspect)
        } else {
            Pair(containerHeight * imgAspect, containerHeight)
        }

        val baseOffsetX = (containerWidth - baseWidth) / 2f
        val baseOffsetY = (containerHeight - baseHeight) / 2f
        val baseScale = baseWidth / bitmapWidth.toFloat()

        return BaseFit(
            baseWidth = baseWidth,
            baseHeight = baseHeight,
            baseOffsetX = baseOffsetX,
            baseOffsetY = baseOffsetY,
            baseScale = baseScale
        )
    }

    fun bitmapToScreen(
        bx: Float,
        by: Float,
        baseFit: BaseFit,
        containerWidth: Float,
        containerHeight: Float,
        userScale: Float,
        panX: Float,
        panY: Float
    ): Offset {
        val cx = containerWidth / 2f
        val cy = containerHeight / 2f

        val xFit = baseFit.baseOffsetX + bx * baseFit.baseScale
        val yFit = baseFit.baseOffsetY + by * baseFit.baseScale

        val screenX = cx + (xFit - cx) * userScale + panX
        val screenY = cy + (yFit - cy) * userScale + panY

        return Offset(screenX, screenY)
    }

    fun screenToBitmap(
        screenX: Float,
        screenY: Float,
        baseFit: BaseFit,
        containerWidth: Float,
        containerHeight: Float,
        userScale: Float,
        panX: Float,
        panY: Float
    ): Offset {
        if (baseFit.baseScale <= 0f || userScale <= 0f) return Offset.Zero

        val cx = containerWidth / 2f
        val cy = containerHeight / 2f

        val xFit = cx + (screenX - cx - panX) / userScale
        val yFit = cy + (screenY - cy - panY) / userScale

        val bx = (xFit - baseFit.baseOffsetX) / baseFit.baseScale
        val by = (yFit - baseFit.baseOffsetY) / baseFit.baseScale

        return Offset(bx, by)
    }

    fun regionToScreenRect(
        regionRect: Rect,
        baseFit: BaseFit,
        containerWidth: Float,
        containerHeight: Float,
        userScale: Float,
        panX: Float,
        panY: Float
    ): ComposeRect {
        val topLeft = bitmapToScreen(
            bx = regionRect.left.toFloat(),
            by = regionRect.top.toFloat(),
            baseFit = baseFit,
            containerWidth = containerWidth,
            containerHeight = containerHeight,
            userScale = userScale,
            panX = panX,
            panY = panY
        )

        val width = regionRect.width() * baseFit.baseScale * userScale
        val height = regionRect.height() * baseFit.baseScale * userScale

        return ComposeRect(
            offset = topLeft,
            size = Size(width, height)
        )
    }

    fun findSelectedRegion(
        touchX: Float,
        touchY: Float,
        regions: List<RecognizedTextRegion>,
        baseFit: BaseFit,
        containerWidth: Float,
        containerHeight: Float,
        userScale: Float,
        panX: Float,
        panY: Float,
        hitSlopPx: Float = 16f,
        maxFallbackDistancePx: Float = 36f
    ): String? {
        if (regions.isEmpty()) return null

        val touch = Offset(touchX, touchY)

        // 1. Direct hit test: Find all regions containing the touch within hitSlop
        val candidateMatches = mutableListOf<Pair<RecognizedTextRegion, ComposeRect>>()
        for (region in regions) {
            val screenRect = regionToScreenRect(
                regionRect = region.boundingBox,
                baseFit = baseFit,
                containerWidth = containerWidth,
                containerHeight = containerHeight,
                userScale = userScale,
                panX = panX,
                panY = panY
            )
            val expandedRect = ComposeRect(
                left = screenRect.left - hitSlopPx,
                top = screenRect.top - hitSlopPx,
                right = screenRect.right + hitSlopPx,
                bottom = screenRect.bottom + hitSlopPx
            )
            if (expandedRect.contains(touch)) {
                candidateMatches.add(Pair(region, screenRect))
            }
        }

        if (candidateMatches.isNotEmpty()) {
            // Prefer tightest/smallest area region, then closest center
            val best = candidateMatches.minWithOrNull(
                compareBy<Pair<RecognizedTextRegion, ComposeRect>> { it.second.width * it.second.height }
                    .thenBy {
                        val center = it.second.center
                        val dx = center.x - touchX
                        val dy = center.y - touchY
                        dx * dx + dy * dy
                    }
            )
            return best?.first?.id
        }

        // 2. Nearest region fallback if within conservative maxFallbackDistancePx
        var nearestId: String? = null
        var minDistanceSq = maxFallbackDistancePx * maxFallbackDistancePx

        for (region in regions) {
            val screenRect = regionToScreenRect(
                regionRect = region.boundingBox,
                baseFit = baseFit,
                containerWidth = containerWidth,
                containerHeight = containerHeight,
                userScale = userScale,
                panX = panX,
                panY = panY
            )
            val clampedX = touchX.coerceIn(screenRect.left, screenRect.right)
            val clampedY = touchY.coerceIn(screenRect.top, screenRect.bottom)
            val dx = touchX - clampedX
            val dy = touchY - clampedY
            val distSq = dx * dx + dy * dy
            if (distSq <= minDistanceSq) {
                minDistanceSq = distSq
                nearestId = region.id
            }
        }

        return nearestId
    }

    fun clampPan(
        panX: Float,
        panY: Float,
        userScale: Float,
        baseFit: BaseFit,
        containerWidth: Float,
        containerHeight: Float
    ): Offset {
        if (userScale <= 1f) {
            return Offset.Zero
        }

        val displayedW = baseFit.baseWidth * userScale
        val displayedH = baseFit.baseHeight * userScale

        val maxPanX = if (displayedW > containerWidth) {
            (displayedW - containerWidth) / 2f + 40f
        } else {
            (containerWidth - displayedW) / 4f
        }

        val maxPanY = if (displayedH > containerHeight) {
            (displayedH - containerHeight) / 2f + 40f
        } else {
            (containerHeight - displayedH) / 4f
        }

        return Offset(
            x = panX.coerceIn(-maxPanX, maxPanX),
            y = panY.coerceIn(-maxPanY, maxPanY)
        )
    }
}
