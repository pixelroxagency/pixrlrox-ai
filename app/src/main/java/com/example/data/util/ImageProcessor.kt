package com.example.data.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream

object ImageProcessor {

    suspend fun loadBitmap(context: Context, uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use {
                val bitmap = BitmapFactory.decodeStream(it)
                rotateIfRequired(context, bitmap, uri)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun rotateIfRequired(context: Context, bitmap: Bitmap, uri: Uri): Bitmap {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return bitmap
        val exif = ExifInterface(inputStream)
        val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    suspend fun cropAndResize(
        bitmap: Bitmap,
        cropRect: Rect?,
        targetWidth: Int,
        targetHeight: Int,
        format: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG,
        quality: Int = 90
    ): Bitmap = withContext(Dispatchers.Default) {
        val cropped = if (cropRect != null) {
            Bitmap.createBitmap(bitmap, cropRect.left, cropRect.top, cropRect.width(), cropRect.height())
        } else {
            bitmap
        }
        Bitmap.createScaledBitmap(cropped, targetWidth, targetHeight, true)
    }

    suspend fun saveImage(
        context: Context,
        bitmap: Bitmap,
        fileName: String,
        format: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG,
        quality: Int = 90
    ): Uri? = withContext(Dispatchers.IO) {
        val mimeType = when (format) {
            Bitmap.CompressFormat.PNG -> "image/png"
            else -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    if (format == Bitmap.CompressFormat.WEBP_LOSSLESS || format == Bitmap.CompressFormat.WEBP_LOSSY) {
                        "image/webp"
                    } else if (format == Bitmap.CompressFormat.JPEG) {
                        "image/jpeg"
                    } else {
                        "image/webp"
                    }
                } else {
                    @Suppress("DEPRECATION")
                    if (format == Bitmap.CompressFormat.WEBP) {
                        "image/webp"
                    } else {
                        "image/jpeg"
                    }
                }
            }
        }

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/PixelRox")
            }
        }

        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        uri?.let {
            context.contentResolver.openOutputStream(it)?.use { outputStream ->
                bitmap.compress(format, quality, outputStream)
            }
        }
        uri
    }
    
    suspend fun removeTextRegion(
        bitmap: Bitmap,
        rect: Rect
    ): Bitmap = withContext(Dispatchers.Default) {
        val config = bitmap.config ?: Bitmap.Config.ARGB_8888
        val result = bitmap.copy(config, true)
        val canvas = Canvas(result)
        val paint = Paint().apply {
            // Simple heuristic: fill with average color of surrounding area
            // Or just use a blur. For now, let's use a very simple approach:
            // Fill with a color sampled from the edge of the rect.
            color = bitmap.getPixel(rect.left.coerceAtLeast(0), rect.top.coerceAtLeast(0))
            style = Paint.Style.FILL
        }
        canvas.drawRect(rect, paint)
        result
    }
}
