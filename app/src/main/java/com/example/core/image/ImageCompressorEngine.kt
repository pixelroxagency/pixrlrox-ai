package com.example.core.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class ImageCompressorEngine(private val context: Context) {

    suspend fun compressImage(
        sourceUri: Uri,
        metadata: ImageMetadata,
        preset: ImageCompressionPreset,
        quality: Int,
        format: OutputFormat,
        scale: ResizeScale,
        customMaxWidth: Int = 1920,
        customMaxHeight: Int = 1080
    ): ImageCompressionResult = withContext(Dispatchers.IO) {
        val tempDir = File(context.cacheDir, "compressed_images").apply { mkdirs() }
        val tempFile = File(tempDir, "compressed_${UUID.randomUUID().toString().take(8)}${format.extension}")
        if (tempFile.exists()) tempFile.delete()

        var decodedBitmap: Bitmap? = null
        var rotatedBitmap: Bitmap? = null
        var scaledBitmap: Bitmap? = null
        var flattenedBitmap: Bitmap? = null

        try {
            currentCoroutineContext().ensureActive()

            // 1. Calculate Target Dimensions
            val (targetWidth, targetHeight) = calculateTargetDimensions(
                origWidth = metadata.originalWidth,
                origHeight = metadata.originalHeight,
                scale = scale,
                customMaxWidth = customMaxWidth,
                customMaxHeight = customMaxHeight
            )

            // 2. Compute inSampleSize for Memory-Safe Decoding
            val inSampleSize = calculateInSampleSize(
                rawWidth = metadata.originalWidth,
                rawHeight = metadata.originalHeight,
                reqWidth = targetWidth,
                reqHeight = targetHeight
            )

            // 3. Decode Stream with inSampleSize
            val options = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                this.inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            val inputStream = context.contentResolver.openInputStream(sourceUri)
                ?: return@withContext ImageCompressionResult.Failure("Unable to open image source stream")

            decodedBitmap = BitmapFactory.decodeStream(inputStream, null, options)
            try { inputStream.close() } catch (_: Exception) {}

            if (decodedBitmap == null) {
                return@withContext ImageCompressionResult.Failure("Failed to decode image bitmap")
            }

            currentCoroutineContext().ensureActive()

            // 4. Handle EXIF Orientation Matrix
            if (metadata.orientationDegrees != 0) {
                val matrix = Matrix().apply {
                    postRotate(metadata.orientationDegrees.toFloat())
                }
                rotatedBitmap = Bitmap.createBitmap(
                    decodedBitmap, 0, 0, decodedBitmap.width, decodedBitmap.height, matrix, true
                )
                if (rotatedBitmap != decodedBitmap) {
                    decodedBitmap.recycle()
                    decodedBitmap = null
                }
            } else {
                rotatedBitmap = decodedBitmap
                decodedBitmap = null
            }

            currentCoroutineContext().ensureActive()

            // 5. Precise Rescaling
            val currentW = rotatedBitmap.width
            val currentH = rotatedBitmap.height

            if (currentW != targetWidth || currentH != targetHeight) {
                scaledBitmap = Bitmap.createScaledBitmap(rotatedBitmap, targetWidth, targetHeight, true)
                if (scaledBitmap != rotatedBitmap) {
                    rotatedBitmap.recycle()
                    rotatedBitmap = null
                }
            } else {
                scaledBitmap = rotatedBitmap
                rotatedBitmap = null
            }

            currentCoroutineContext().ensureActive()

            // 6. Handle Transparency if Flattening to JPEG
            val finalBitmapToCompress = if (format == OutputFormat.JPEG && (scaledBitmap.hasAlpha() || metadata.hasAlpha)) {
                flattenedBitmap = Bitmap.createBitmap(scaledBitmap.width, scaledBitmap.height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(flattenedBitmap)
                canvas.drawColor(Color.WHITE)
                canvas.drawBitmap(scaledBitmap, 0f, 0f, null)
                scaledBitmap.recycle()
                scaledBitmap = null
                flattenedBitmap
            } else {
                scaledBitmap
            }

            currentCoroutineContext().ensureActive()

            // 7. Compress to Temp Output File
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

            val finalQuality = quality.coerceIn(1, 100)

            FileOutputStream(tempFile).use { out ->
                val success = finalBitmapToCompress.compress(compressFormat, finalQuality, out)
                out.flush()
                if (!success) {
                    return@withContext ImageCompressionResult.Failure("Failed to compress bitmap into ${format.title} format")
                }
            }

            currentCoroutineContext().ensureActive()

            // 8. Output Validation
            if (!tempFile.exists() || tempFile.length() == 0L) {
                return@withContext ImageCompressionResult.Failure("Compressed output file is missing or empty")
            }

            val outBounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(tempFile.absolutePath, outBounds)

            if (outBounds.outWidth <= 0 || outBounds.outHeight <= 0) {
                tempFile.delete()
                return@withContext ImageCompressionResult.Failure("Compressed output file is corrupted or invalid")
            }

            return@withContext ImageCompressionResult.Success(
                tempFile = tempFile,
                outputWidth = outBounds.outWidth,
                outputHeight = outBounds.outHeight,
                outputSizeBytes = tempFile.length(),
                format = format,
                qualityUsed = finalQuality
            )

        } catch (e: Exception) {
            if (tempFile.exists()) tempFile.delete()
            return@withContext ImageCompressionResult.Failure(e.localizedMessage ?: "Image compression error", e)
        } finally {
            decodedBitmap?.recycle()
            rotatedBitmap?.recycle()
            scaledBitmap?.recycle()
            flattenedBitmap?.recycle()
        }
    }

    companion object {
        fun calculateTargetDimensions(
            origWidth: Int,
            origHeight: Int,
            scale: ResizeScale,
            customMaxWidth: Int = 1920,
            customMaxHeight: Int = 1080
        ): Pair<Int, Int> {
            if (origWidth <= 0 || origHeight <= 0) return Pair(1, 1)

            val maxAllowedWidth: Int
            val maxAllowedHeight: Int

            when (scale) {
                ResizeScale.ORIGINAL -> {
                    return Pair(origWidth, origHeight)
                }
                ResizeScale.SEVENTY_FIVE -> {
                    maxAllowedWidth = (origWidth * 0.75f).toInt()
                    maxAllowedHeight = (origHeight * 0.75f).toInt()
                }
                ResizeScale.FIFTY -> {
                    maxAllowedWidth = (origWidth * 0.5f).toInt()
                    maxAllowedHeight = (origHeight * 0.5f).toInt()
                }
                ResizeScale.TWENTY_FIVE -> {
                    maxAllowedWidth = (origWidth * 0.25f).toInt()
                    maxAllowedHeight = (origHeight * 0.25f).toInt()
                }
                ResizeScale.CUSTOM -> {
                    maxAllowedWidth = customMaxWidth.coerceAtMost(origWidth)
                    maxAllowedHeight = customMaxHeight.coerceAtMost(origHeight)
                }
            }

            val aspectRatio = origWidth.toFloat() / origHeight.toFloat()
            var targetW = maxAllowedWidth
            var targetH = (targetW / aspectRatio).toInt()

            if (targetH > maxAllowedHeight) {
                targetH = maxAllowedHeight
                targetW = (targetH * aspectRatio).toInt()
            }

            // Ensure no upscale and at least 1px
            targetW = targetW.coerceIn(1, origWidth)
            targetH = targetH.coerceIn(1, origHeight)

            return Pair(targetW, targetH)
        }

        fun calculateInSampleSize(
            rawWidth: Int,
            rawHeight: Int,
            reqWidth: Int,
            reqHeight: Int
        ): Int {
            var inSampleSize = 1
            if (rawHeight > reqHeight || rawWidth > reqWidth) {
                val halfHeight = rawHeight / 2
                val halfWidth = rawWidth / 2
                while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                    inSampleSize *= 2
                }
            }
            return inSampleSize.coerceAtLeast(1)
        }
    }
}
