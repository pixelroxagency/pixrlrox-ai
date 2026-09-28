package com.example.data.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import com.example.core.image.ImageOutputPublisher
import com.example.core.image.ImagePublishResult
import com.example.core.image.OutputFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

object ImageBitmapHelper {

    suspend fun decodeSafeBitmap(
        context: Context,
        uri: Uri,
        maxDimension: Int = 2048
    ): Bitmap? = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        var orientationDegrees = 0

        // 1. Read EXIF orientation
        try {
            resolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                orientationDegrees = when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            }
        } catch (_: Exception) {}

        // 2. Decode bounds for inSampleSize
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        try {
            resolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOptions)
            }
        } catch (e: Exception) {
            return@withContext null
        }

        if (boundsOptions.outWidth <= 0 || boundsOptions.outHeight <= 0) {
            return@withContext null
        }

        var sampleSize = 1
        val maxDim = max(boundsOptions.outWidth, boundsOptions.outHeight)
        while (maxDim / sampleSize > maxDimension) {
            sampleSize *= 2
        }

        // 3. Decode stream
        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        var decoded: Bitmap? = null
        try {
            resolver.openInputStream(uri)?.use { stream ->
                decoded = BitmapFactory.decodeStream(stream, null, decodeOptions)
            }
        } catch (e: Exception) {
            return@withContext null
        }

        val bitmap = decoded ?: return@withContext null

        // 4. Rotate if needed
        if (orientationDegrees != 0) {
            val matrix = Matrix().apply { postRotate(orientationDegrees.toFloat()) }
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotated != bitmap) {
                bitmap.recycle()
            }
            return@withContext rotated
        }

        return@withContext bitmap
    }

    /**
     * Removes text in the specified bounding box by sampling border pixels
     * and filling the area on the bitmap.
     */
    fun removeTextRegion(source: Bitmap, box: Rect): Bitmap {
        val mutable = source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(mutable)

        val left = max(0, box.left)
        val top = max(0, box.top)
        val right = min(source.width, box.right)
        val bottom = min(source.height, box.bottom)

        if (right <= left || bottom <= top) return mutable

        // Sample perimeter pixels (2-pixel border around box)
        var totalR = 0L
        var totalG = 0L
        var totalB = 0L
        var sampleCount = 0

        val margin = 3
        val sampleLeft = max(0, left - margin)
        val sampleTop = max(0, top - margin)
        val sampleRight = min(source.width - 1, right + margin)
        val sampleBottom = min(source.height - 1, bottom + margin)

        // Top and Bottom perimeter stripes
        for (x in sampleLeft..sampleRight step 2) {
            for (y in sampleTop..top) {
                val pixel = source.getPixel(x, y)
                totalR += Color.red(pixel)
                totalG += Color.green(pixel)
                totalB += Color.blue(pixel)
                sampleCount++
            }
            for (y in bottom..sampleBottom) {
                val pixel = source.getPixel(x, y)
                totalR += Color.red(pixel)
                totalG += Color.green(pixel)
                totalB += Color.blue(pixel)
                sampleCount++
            }
        }

        // Left and Right perimeter stripes
        for (y in top..bottom step 2) {
            for (x in sampleLeft..left) {
                val pixel = source.getPixel(x, y)
                totalR += Color.red(pixel)
                totalG += Color.green(pixel)
                totalB += Color.blue(pixel)
                sampleCount++
            }
            for (x in right..sampleRight) {
                val pixel = source.getPixel(x, y)
                totalR += Color.red(pixel)
                totalG += Color.green(pixel)
                totalB += Color.blue(pixel)
                sampleCount++
            }
        }

        val fillColor = if (sampleCount > 0) {
            Color.rgb(
                (totalR / sampleCount).toInt(),
                (totalG / sampleCount).toInt(),
                (totalB / sampleCount).toInt()
            )
        } else {
            Color.WHITE
        }

        val paint = Paint().apply {
            color = fillColor
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        // Draw fill over text region
        canvas.drawRect(
            left.toFloat(),
            top.toFloat(),
            right.toFloat(),
            bottom.toFloat(),
            paint
        )

        return mutable
    }

    /**
     * Renders replacement text into the given bounding box on the bitmap.
     */
    fun addReplacementText(
        source: Bitmap,
        box: Rect,
        text: String,
        textSizeSp: Float,
        textColor: Int
    ): Bitmap {
        val mutable = source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(mutable)

        val paint = Paint().apply {
            color = textColor
            textSize = max(12f, textSizeSp)
            isAntiAlias = true
            style = Paint.Style.FILL
        }

        val textBounds = Rect()
        paint.getTextBounds(text, 0, text.length, textBounds)

        // Draw text with baseline positioned inside the box
        val x = max(0f, box.left.toFloat())
        val y = box.top.toFloat() + paint.textSize.coerceAtLeast(textBounds.height().toFloat())

        canvas.drawText(text, x, y, paint)
        return mutable
    }

    /**
     * Crops bitmap according to pixel boundaries, clamped to bitmap dimensions.
     */
    fun cropBitmap(source: Bitmap, left: Int, top: Int, width: Int, height: Int): Bitmap {
        val clampLeft = max(0, min(left, source.width - 1))
        val clampTop = max(0, min(top, source.height - 1))
        val clampWidth = max(1, min(width, source.width - clampLeft))
        val clampHeight = max(1, min(height, source.height - clampTop))

        return Bitmap.createBitmap(source, clampLeft, clampTop, clampWidth, clampHeight)
    }

    /**
     * Rescales bitmap to target dimensions.
     */
    fun resizeBitmap(source: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
        val w = max(1, targetWidth)
        val h = max(1, targetHeight)
        return Bitmap.createScaledBitmap(source, w, h, true)
    }

    enum class FitMode {
        CROP_TO_FILL,
        FIT_INSIDE
    }

    /**
     * Resizes and fits bitmap into target dimensions using Crop to Fill or Fit Inside.
     */
    fun fitOrCropBitmap(
        source: Bitmap,
        targetWidth: Int,
        targetHeight: Int,
        fitMode: FitMode,
        backgroundColor: Int = Color.WHITE
    ): Bitmap {
        val targetW = max(1, targetWidth)
        val targetH = max(1, targetHeight)

        return when (fitMode) {
            FitMode.CROP_TO_FILL -> {
                val scale = max(targetW.toFloat() / source.width, targetH.toFloat() / source.height)
                val scaledW = (source.width * scale).toInt().coerceAtLeast(1)
                val scaledH = (source.height * scale).toInt().coerceAtLeast(1)
                val scaled = Bitmap.createScaledBitmap(source, scaledW, scaledH, true)

                val xOffset = ((scaledW - targetW) / 2).coerceAtLeast(0)
                val yOffset = ((scaledH - targetH) / 2).coerceAtLeast(0)
                val cropped = Bitmap.createBitmap(scaled, xOffset, yOffset, targetW, targetH)
                if (scaled != cropped && scaled != source) {
                    scaled.recycle()
                }
                cropped
            }
            FitMode.FIT_INSIDE -> {
                val scale = min(targetW.toFloat() / source.width, targetH.toFloat() / source.height)
                val scaledW = (source.width * scale).toInt().coerceAtLeast(1)
                val scaledH = (source.height * scale).toInt().coerceAtLeast(1)
                val scaled = Bitmap.createScaledBitmap(source, scaledW, scaledH, true)

                val result = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(result)
                canvas.drawColor(backgroundColor)

                val xOffset = ((targetW - scaledW) / 2f).coerceAtLeast(0f)
                val yOffset = ((targetH - scaledH) / 2f).coerceAtLeast(0f)
                canvas.drawBitmap(scaled, xOffset, yOffset, null)

                if (scaled != source) {
                    scaled.recycle()
                }
                result
            }
        }
    }

    /**
     * Applies a text watermark to the source bitmap at normalized coordinates (0f..1f).
     */
    fun applyWatermarkText(
        source: Bitmap,
        text: String,
        textSizeSp: Float,
        textColor: Int,
        opacity: Float,
        normX: Float,
        normY: Float
    ): Bitmap {
        val result = source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)

        val alpha = (opacity.coerceIn(0f, 1f) * 255).toInt()
        val paint = Paint().apply {
            color = textColor
            this.alpha = alpha
            textSize = max(14f, textSizeSp)
            isAntiAlias = true
            style = Paint.Style.FILL
            setShadowLayer(4f, 2f, 2f, Color.argb(alpha / 2, 0, 0, 0))
        }

        val textBounds = Rect()
        paint.getTextBounds(text, 0, text.length, textBounds)

        val posX = (normX.coerceIn(0f, 1f) * source.width).coerceIn(10f, (source.width - textBounds.width() - 10).toFloat().coerceAtLeast(10f))
        val posY = (normY.coerceIn(0f, 1f) * source.height).coerceIn((textBounds.height() + 10).toFloat(), (source.height - 10).toFloat())

        canvas.drawText(text, posX, posY, paint)
        return result
    }

    /**
     * Applies an image watermark onto the source bitmap at normalized coordinates (0f..1f).
     */
    fun applyWatermarkImage(
        source: Bitmap,
        logo: Bitmap,
        scale: Float,
        opacity: Float,
        normX: Float,
        normY: Float
    ): Bitmap {
        val result = source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)

        // Scale relative to source width (e.g. 10% to 50% of source width)
        val targetLogoWidth = (source.width * (scale.coerceIn(0.1f, 1.0f) * 0.4f)).toInt().coerceAtLeast(16)
        val logoAspect = logo.height.toFloat() / logo.width.toFloat()
        val targetLogoHeight = (targetLogoWidth * logoAspect).toInt().coerceAtLeast(16)

        val scaledLogo = Bitmap.createScaledBitmap(logo, targetLogoWidth, targetLogoHeight, true)

        val alpha = (opacity.coerceIn(0f, 1f) * 255).toInt()
        val paint = Paint().apply {
            this.alpha = alpha
            isAntiAlias = true
        }

        val posX = (normX.coerceIn(0f, 1f) * source.width).coerceIn(0f, (source.width - targetLogoWidth).toFloat().coerceAtLeast(0f))
        val posY = (normY.coerceIn(0f, 1f) * source.height).coerceIn(0f, (source.height - targetLogoHeight).toFloat().coerceAtLeast(0f))

        canvas.drawBitmap(scaledLogo, posX, posY, paint)

        if (scaledLogo != logo) {
            scaledLogo.recycle()
        }
        return result
    }

    data class CollageCell(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float
    )

    fun getCollageLayout(imageCount: Int, layoutVariant: Int): List<CollageCell> {
        return when (imageCount) {
            2 -> when (layoutVariant % 2) {
                0 -> listOf( // Vertical split
                    CollageCell(0f, 0f, 0.5f, 1f),
                    CollageCell(0.5f, 0f, 1f, 1f)
                )
                else -> listOf( // Horizontal split
                    CollageCell(0f, 0f, 1f, 0.5f),
                    CollageCell(0f, 0.5f, 1f, 1f)
                )
            }
            3 -> when (layoutVariant % 3) {
                0 -> listOf( // 1 Top, 2 Bottom
                    CollageCell(0f, 0f, 1f, 0.5f),
                    CollageCell(0f, 0.5f, 0.5f, 1f),
                    CollageCell(0.5f, 0.5f, 1f, 1f)
                )
                1 -> listOf( // 1 Left, 2 Right
                    CollageCell(0f, 0f, 0.5f, 1f),
                    CollageCell(0.5f, 0f, 1f, 0.5f),
                    CollageCell(0.5f, 0.5f, 1f, 1f)
                )
                else -> listOf( // 3 Vertical stripes
                    CollageCell(0f, 0f, 0.333f, 1f),
                    CollageCell(0.333f, 0f, 0.666f, 1f),
                    CollageCell(0.666f, 0f, 1f, 1f)
                )
            }
            4 -> when (layoutVariant % 3) {
                0 -> listOf( // 2x2 Grid
                    CollageCell(0f, 0f, 0.5f, 0.5f),
                    CollageCell(0.5f, 0f, 1f, 0.5f),
                    CollageCell(0f, 0.5f, 0.5f, 1f),
                    CollageCell(0.5f, 0.5f, 1f, 1f)
                )
                1 -> listOf( // 1 Left large, 3 Right stacked
                    CollageCell(0f, 0f, 0.6f, 1f),
                    CollageCell(0.6f, 0f, 1f, 0.333f),
                    CollageCell(0.6f, 0.333f, 1f, 0.666f),
                    CollageCell(0.6f, 0.666f, 1f, 1f)
                )
                else -> listOf( // 4 Vertical stripes
                    CollageCell(0f, 0f, 0.25f, 1f),
                    CollageCell(0.25f, 0f, 0.5f, 1f),
                    CollageCell(0.5f, 0f, 0.75f, 1f),
                    CollageCell(0.75f, 0f, 1f, 1f)
                )
            }
            5 -> listOf(
                CollageCell(0f, 0f, 0.5f, 0.5f),
                CollageCell(0.5f, 0f, 1f, 0.5f),
                CollageCell(0f, 0.5f, 0.333f, 1f),
                CollageCell(0.333f, 0.5f, 0.666f, 1f),
                CollageCell(0.666f, 0.5f, 1f, 1f)
            )
            6 -> listOf(
                CollageCell(0f, 0f, 0.333f, 0.5f),
                CollageCell(0.333f, 0f, 0.666f, 0.5f),
                CollageCell(0.666f, 0f, 1f, 0.5f),
                CollageCell(0f, 0.5f, 0.333f, 1f),
                CollageCell(0.333f, 0.5f, 0.666f, 1f),
                CollageCell(0.666f, 0.5f, 1f, 1f)
            )
            else -> { // Auto-grid for 7, 8, 9 images
                val cols = 3
                val rows = (imageCount + cols - 1) / cols
                val list = mutableListOf<CollageCell>()
                val cellW = 1f / cols
                val cellH = 1f / rows
                for (i in 0 until imageCount) {
                    val r = i / cols
                    val c = i % cols
                    list.add(CollageCell(c * cellW, r * cellH, (c + 1) * cellW, (r + 1) * cellH))
                }
                list
            }
        }
    }

    /**
     * Composes multiple images into a single collage bitmap.
     */
    fun composeCollage(
        bitmaps: List<Bitmap>,
        targetWidth: Int = 1080,
        targetHeight: Int = 1080,
        layoutVariant: Int = 0,
        spacingPx: Int = 8,
        marginPx: Int = 8,
        backgroundColor: Int = Color.WHITE
    ): Bitmap {
        val result = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawColor(backgroundColor)

        val cells = getCollageLayout(bitmaps.size, layoutVariant)
        val availableW = targetWidth - (marginPx * 2)
        val availableH = targetHeight - (marginPx * 2)

        for (i in bitmaps.indices) {
            val cell = cells.getOrNull(i) ?: break
            val src = bitmaps[i]

            val cellLeft = marginPx + (cell.left * availableW) + (spacingPx / 2f)
            val cellTop = marginPx + (cell.top * availableH) + (spacingPx / 2f)
            val cellRight = marginPx + (cell.right * availableW) - (spacingPx / 2f)
            val cellBottom = marginPx + (cell.bottom * availableH) - (spacingPx / 2f)

            val cellW = (cellRight - cellLeft).toInt().coerceAtLeast(1)
            val cellH = (cellBottom - cellTop).toInt().coerceAtLeast(1)

            // Center-crop source into cell dimensions
            val cellBitmap = fitOrCropBitmap(src, cellW, cellH, FitMode.CROP_TO_FILL)
            canvas.drawBitmap(cellBitmap, cellLeft, cellTop, null)

            if (cellBitmap != src) {
                cellBitmap.recycle()
            }
        }

        return result
    }

    enum class TextAlignmentOption { LEFT, CENTER, RIGHT }
    enum class VerticalPositionOption { TOP, CENTER, BOTTOM }

    /**
     * Deterministically renders a post or quote graphic onto a high-res bitmap.
     */
    fun renderPostQuote(
        targetWidth: Int,
        targetHeight: Int,
        quoteText: String,
        authorText: String,
        backgroundColor: Int,
        backgroundImage: Bitmap? = null,
        scrimOpacity: Float = 0.35f,
        quoteTextSizeSp: Float = 36f,
        quoteTextColor: Int = Color.WHITE,
        authorTextSizeSp: Float = 20f,
        authorTextColor: Int = Color.LTGRAY,
        alignment: TextAlignmentOption = TextAlignmentOption.CENTER,
        verticalPos: VerticalPositionOption = VerticalPositionOption.CENTER,
        hasShadow: Boolean = true
    ): Bitmap {
        val result = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)

        // 1. Draw Background
        if (backgroundImage != null) {
            val bgFit = fitOrCropBitmap(backgroundImage, targetWidth, targetHeight, FitMode.CROP_TO_FILL)
            canvas.drawBitmap(bgFit, 0f, 0f, null)
            if (bgFit != backgroundImage) bgFit.recycle()

            // Dark Scrim Overlay
            if (scrimOpacity > 0f) {
                val scrimPaint = Paint().apply {
                    color = Color.BLACK
                    alpha = (scrimOpacity.coerceIn(0f, 1f) * 255).toInt()
                }
                canvas.drawRect(0f, 0f, targetWidth.toFloat(), targetHeight.toFloat(), scrimPaint)
            }
        } else {
            canvas.drawColor(backgroundColor)
        }

        // 2. Setup Text Paints
        val textPaint = android.text.TextPaint().apply {
            color = quoteTextColor
            textSize = max(16f, quoteTextSizeSp)
            isAntiAlias = true
            if (hasShadow) {
                setShadowLayer(8f, 3f, 3f, Color.argb(180, 0, 0, 0))
            }
        }

        val authorPaint = android.text.TextPaint().apply {
            color = authorTextColor
            textSize = max(12f, authorTextSizeSp)
            isAntiAlias = true
            if (hasShadow) {
                setShadowLayer(6f, 2f, 2f, Color.argb(160, 0, 0, 0))
            }
        }

        val layoutAlign = when (alignment) {
            TextAlignmentOption.LEFT -> android.text.Layout.Alignment.ALIGN_NORMAL
            TextAlignmentOption.CENTER -> android.text.Layout.Alignment.ALIGN_CENTER
            TextAlignmentOption.RIGHT -> android.text.Layout.Alignment.ALIGN_OPPOSITE
        }

        val horizontalPadding = (targetWidth * 0.12f).toInt()
        val textContentWidth = targetWidth - (horizontalPadding * 2)

        // 3. Build StaticLayouts
        val quoteLayout = android.text.StaticLayout.Builder.obtain(
            quoteText, 0, quoteText.length, textPaint, textContentWidth
        ).setAlignment(layoutAlign).build()

        val authorContent = if (authorText.isNotBlank()) "— $authorText" else ""
        val authorLayout = if (authorContent.isNotBlank()) {
            android.text.StaticLayout.Builder.obtain(
                authorContent, 0, authorContent.length, authorPaint, textContentWidth
            ).setAlignment(layoutAlign).build()
        } else null

        val spacing = if (authorLayout != null) (targetHeight * 0.03f) else 0f
        val totalBlockHeight = quoteLayout.height + spacing + (authorLayout?.height ?: 0)

        // 4. Calculate Vertical Placement
        val startY = when (verticalPos) {
            VerticalPositionOption.TOP -> targetHeight * 0.15f
            VerticalPositionOption.CENTER -> ((targetHeight - totalBlockHeight) / 2f).coerceAtLeast(targetHeight * 0.08f)
            VerticalPositionOption.BOTTOM -> (targetHeight * 0.85f - totalBlockHeight).coerceAtLeast(targetHeight * 0.08f)
        }

        // 5. Draw StaticLayouts
        canvas.save()
        canvas.translate(horizontalPadding.toFloat(), startY)
        quoteLayout.draw(canvas)

        if (authorLayout != null) {
            canvas.translate(0f, quoteLayout.height + spacing)
            authorLayout.draw(canvas)
        }
        canvas.restore()

        return result
    }

    /**
     * Saves bitmap and publishes to Pictures/PixelRox via ImageOutputPublisher.
     */
    suspend fun saveAndPublish(
        context: Context,
        bitmap: Bitmap,
        format: OutputFormat,
        quality: Int = 90,
        baseName: String = "image_edit"
    ): ImagePublishResult = withContext(Dispatchers.IO) {
        val tempDir = File(context.cacheDir, "pixelrox_exports").apply { mkdirs() }
        val tempFile = File(tempDir, "export_${UUID.randomUUID().toString().take(8)}${format.extension}")

        var processedBitmap = bitmap
        var flattened: Bitmap? = null

        // Flatten transparency for JPEG
        if (format == OutputFormat.JPEG && bitmap.hasAlpha()) {
            flattened = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(flattened)
            canvas.drawColor(Color.WHITE)
            canvas.drawBitmap(bitmap, 0f, 0f, null)
            processedBitmap = flattened
        }

        val compressFormat = when (format) {
            OutputFormat.JPEG -> Bitmap.CompressFormat.JPEG
            OutputFormat.PNG -> Bitmap.CompressFormat.PNG
            OutputFormat.WEBP -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Bitmap.CompressFormat.WEBP_LOSSY
                } else {
                    @Suppress("DEPRECATION")
                    Bitmap.CompressFormat.WEBP
                }
            }
        }

        try {
            FileOutputStream(tempFile).use { out ->
                processedBitmap.compress(compressFormat, quality.coerceIn(1, 100), out)
                out.flush()
            }
        } catch (e: Exception) {
            flattened?.recycle()
            return@withContext ImagePublishResult.Failure("Failed to compress bitmap: ${e.message}", e)
        } finally {
            flattened?.recycle()
        }

        val publisher = ImageOutputPublisher(context)
        return@withContext publisher.publishImage(tempFile, baseName, format)
    }
}

