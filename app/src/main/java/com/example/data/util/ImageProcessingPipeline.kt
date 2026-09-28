package com.example.data.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import com.example.core.image.ImagePublishResult
import com.example.core.image.OutputFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object ImageProcessingPipeline {

    /**
     * Calculates new dimensions for a given original width and height based on target constraints.
     */
    fun calculateResizeDimensions(
        originalWidth: Int,
        originalHeight: Int,
        targetWidth: Int?,
        targetHeight: Int?,
        preserveAspectRatio: Boolean = true,
        noUpscale: Boolean = false
    ): Pair<Int, Int> {
        val origW = originalWidth.coerceAtLeast(1)
        val origH = originalHeight.coerceAtLeast(1)

        var finalW: Int
        var finalH: Int

        if (targetWidth != null && targetHeight != null) {
            if (preserveAspectRatio) {
                val scaleW = targetWidth.toFloat() / origW
                val scaleH = targetHeight.toFloat() / origH
                val scale = min(scaleW, scaleH)
                finalW = (origW * scale).roundToInt().coerceAtLeast(1)
                finalH = (origH * scale).roundToInt().coerceAtLeast(1)
            } else {
                finalW = targetWidth.coerceAtLeast(1)
                finalH = targetHeight.coerceAtLeast(1)
            }
        } else if (targetWidth != null) {
            finalW = targetWidth.coerceAtLeast(1)
            finalH = if (preserveAspectRatio) {
                ((origH.toFloat() / origW) * finalW).roundToInt().coerceAtLeast(1)
            } else {
                origH
            }
        } else if (targetHeight != null) {
            finalH = targetHeight.coerceAtLeast(1)
            finalW = if (preserveAspectRatio) {
                ((origW.toFloat() / origH) * finalH).roundToInt().coerceAtLeast(1)
            } else {
                origW
            }
        } else {
            finalW = origW
            finalH = origH
        }

        if (noUpscale) {
            if (finalW > origW || finalH > origH) {
                val scale = min(origW.toFloat() / finalW, origH.toFloat() / finalH)
                finalW = (finalW * scale).roundToInt().coerceAtLeast(1)
                finalH = (finalH * scale).roundToInt().coerceAtLeast(1)
            }
        }

        return Pair(finalW, finalH)
    }

    /**
     * Resizes by specifying the maximum length of the long edge.
     */
    fun calculateLongEdgeDimensions(
        originalWidth: Int,
        originalHeight: Int,
        targetLongEdge: Int,
        noUpscale: Boolean = false
    ): Pair<Int, Int> {
        val origW = originalWidth.coerceAtLeast(1)
        val origH = originalHeight.coerceAtLeast(1)
        val currentLong = max(origW, origH)

        if (noUpscale && targetLongEdge >= currentLong) {
            return Pair(origW, origH)
        }

        val scale = targetLongEdge.toFloat() / currentLong
        val finalW = (origW * scale).roundToInt().coerceAtLeast(1)
        val finalH = (origH * scale).roundToInt().coerceAtLeast(1)
        return Pair(finalW, finalH)
    }

    /**
     * Flattens a bitmap with alpha onto a solid background color (e.g. for JPEG export).
     */
    fun flattenAlpha(bitmap: Bitmap, backgroundColor: Int = Color.WHITE): Bitmap {
        val flattened = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(flattened)
        canvas.drawColor(backgroundColor)
        canvas.drawBitmap(bitmap, 0f, 0f, null)
        return flattened
    }

    /**
     * Renders a stylized avatar image:
     * - Square dimensions (targetSize x targetSize)
     * - Circular crop or framing
     * - Background: solid or transparent
     * - Optional circular border/ring
     */
    fun renderAvatar(
        source: Bitmap,
        targetSize: Int, // 512 or 1024
        backgroundColor: Int?, // null for transparent PNG
        borderColor: Int,
        borderWidthPx: Float,
        cropRectNorm: android.graphics.RectF? = null // normalized source rect (0f..1f)
    ): Bitmap {
        val size = targetSize.coerceAtLeast(64)
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        // 1. Draw background if provided
        if (backgroundColor != null) {
            val bgPaint = Paint().apply {
                color = backgroundColor
                isAntiAlias = true
                style = Paint.Style.FILL
            }
            canvas.drawCircle(size / 2f, size / 2f, size / 2f, bgPaint)
        }

        // 2. Source bitmap extraction (crop region or center crop)
        val srcRect = if (cropRectNorm != null) {
            Rect(
                (cropRectNorm.left * source.width).toInt().coerceIn(0, source.width - 1),
                (cropRectNorm.top * source.height).toInt().coerceIn(0, source.height - 1),
                (cropRectNorm.right * source.width).toInt().coerceIn(1, source.width),
                (cropRectNorm.bottom * source.height).toInt().coerceIn(1, source.height)
            )
        } else {
            val minEdge = min(source.width, source.height)
            val left = (source.width - minEdge) / 2
            val top = (source.height - minEdge) / 2
            Rect(left, top, left + minEdge, top + minEdge)
        }

        // 3. Draw cropped source as circle inside avatar (respecting border inset)
        val inset = borderWidthPx.coerceAtLeast(0f)
        val avatarRadius = (size / 2f) - inset

        val shaderBitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val shaderCanvas = Canvas(shaderBitmap)

        val destRect = Rect(inset.toInt(), inset.toInt(), (size - inset).toInt(), (size - inset).toInt())
        val photoPaint = Paint().apply {
            isAntiAlias = true
            isFilterBitmap = true
        }
        shaderCanvas.drawBitmap(source, srcRect, destRect, photoPaint)

        // Mask with circular path / paint using PorterDuff
        val circularMask = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val maskCanvas = Canvas(circularMask)
        val maskPaint = Paint().apply {
            isAntiAlias = true
            color = Color.BLACK
        }
        maskCanvas.drawCircle(size / 2f, size / 2f, avatarRadius.coerceAtLeast(1f), maskPaint)

        val compositePaint = Paint().apply {
            isAntiAlias = true
            xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN)
        }
        maskCanvas.drawBitmap(shaderBitmap, 0f, 0f, compositePaint)
        canvas.drawBitmap(circularMask, 0f, 0f, null)

        shaderBitmap.recycle()
        circularMask.recycle()

        // 4. Draw border ring if width > 0
        if (borderWidthPx > 0f) {
            val strokePaint = Paint().apply {
                color = borderColor
                strokeWidth = borderWidthPx
                style = Paint.Style.STROKE
                isAntiAlias = true
            }
            canvas.drawCircle(size / 2f, size / 2f, (size / 2f) - (borderWidthPx / 2f), strokePaint)
        }

        return output
    }
}
