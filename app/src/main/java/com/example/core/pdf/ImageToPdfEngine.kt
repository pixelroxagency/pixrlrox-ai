package com.example.core.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import kotlin.coroutines.coroutineContext

enum class PdfPageSize(val displayName: String, val widthPt: Int, val heightPt: Int) {
    A4("A4 (595 x 842 pt)", 595, 842),
    LETTER("US Letter (612 x 792 pt)", 612, 792),
    AUTO("Match Image Size (Auto)", 0, 0)
}

enum class ImageScaleMode(val displayName: String) {
    FIT("Fit to Page (Preserve Aspect)"),
    FILL("Fill Page Area")
}

enum class PageMargin(val displayName: String, val marginPt: Int) {
    NONE("No Margin (0 pt)", 0),
    SMALL("Small Margin (18 pt)", 18),
    MEDIUM("Medium Margin (36 pt)", 36)
}

data class ImageToPdfConfig(
    val pageSize: PdfPageSize = PdfPageSize.A4,
    val scaleMode: ImageScaleMode = ImageScaleMode.FIT,
    val margin: PageMargin = PageMargin.SMALL
)

class ImageToPdfEngine(private val context: Context) {

    suspend fun convertImagesToPdf(
        imageUris: List<Uri>,
        config: ImageToPdfConfig,
        onProgress: (current: Int, total: Int) -> Unit = { _, _ -> }
    ): File = withContext(Dispatchers.IO) {
        if (imageUris.isEmpty()) {
            throw IllegalArgumentException("At least one image must be selected.")
        }

        val tempOutputFile = File.createTempFile("img_to_pdf_", ".pdf", context.cacheDir)
        val document = PdfDocument()

        try {
            val total = imageUris.size
            for (index in imageUris.indices) {
                coroutineContext.ensureActive()
                val uri = imageUris[index]
                val bitmap = decodeOrientedBitmap(context, uri) ?: continue

                val pageWidth: Int
                val pageHeight: Int

                if (config.pageSize == PdfPageSize.AUTO) {
                    pageWidth = bitmap.width.coerceAtLeast(100)
                    pageHeight = bitmap.height.coerceAtLeast(100)
                } else {
                    pageWidth = config.pageSize.widthPt
                    pageHeight = config.pageSize.heightPt
                }

                val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, index + 1).create()
                val page = document.startPage(pageInfo)
                val canvas: Canvas = page.canvas

                val margin = if (config.pageSize == PdfPageSize.AUTO) 0f else config.margin.marginPt.toFloat()
                val targetRect = RectF(
                    margin,
                    margin,
                    pageWidth - margin,
                    pageHeight - margin
                )

                drawImageToCanvas(canvas, bitmap, targetRect, config.scaleMode)
                document.finishPage(page)
                bitmap.recycle()

                onProgress(index + 1, total)
            }

            FileOutputStream(tempOutputFile).use { fos ->
                document.writeTo(fos)
                fos.flush()
            }
        } finally {
            document.close()
        }

        tempOutputFile
    }

    private fun drawImageToCanvas(
        canvas: Canvas,
        bitmap: Bitmap,
        targetRect: RectF,
        scaleMode: ImageScaleMode
    ) {
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        val availableWidth = targetRect.width()
        val availableHeight = targetRect.height()

        if (scaleMode == ImageScaleMode.FILL) {
            canvas.drawBitmap(bitmap, null, targetRect, paint)
        } else {
            // FIT mode: preserve aspect ratio
            val srcAspect = bitmap.width.toFloat() / bitmap.height.toFloat()
            val dstAspect = availableWidth / availableHeight

            val drawWidth: Float
            val drawHeight: Float

            if (srcAspect > dstAspect) {
                drawWidth = availableWidth
                drawHeight = availableWidth / srcAspect
            } else {
                drawHeight = availableHeight
                drawWidth = availableHeight * srcAspect
            }

            val left = targetRect.left + (availableWidth - drawWidth) / 2f
            val top = targetRect.top + (availableHeight - drawHeight) / 2f
            val destRect = RectF(left, top, left + drawWidth, top + drawHeight)

            canvas.drawBitmap(bitmap, null, destRect, paint)
        }
    }

    private fun decodeOrientedBitmap(context: Context, uri: Uri): Bitmap? {
        val resolver = context.contentResolver
        var orientation = ExifInterface.ORIENTATION_NORMAL
        try {
            resolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            }
        } catch (_: Exception) {}

        // Decode bounds first to avoid OOM
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        resolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        }

        var inSampleSize = 1
        val maxDimension = 3000
        while (options.outWidth / inSampleSize > maxDimension || options.outHeight / inSampleSize > maxDimension) {
            inSampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply {
            this.inSampleSize = inSampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        val rawBitmap = resolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOptions)
        } ?: return null

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
        }

        return if (!matrix.isIdentity) {
            val transformed = Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
            if (transformed != rawBitmap) {
                rawBitmap.recycle()
            }
            transformed
        } else {
            rawBitmap
        }
    }
}
