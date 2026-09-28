package com.example.core.image

import android.content.Context
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.net.Uri
import android.provider.OpenableColumns
import java.io.InputStream

data class ImageMetadata(
    val contentUri: String,
    val displayName: String,
    val fileSizeBytes: Long,
    val originalWidth: Int,
    val originalHeight: Int,
    val mimeType: String,
    val orientationDegrees: Int,
    val hasAlpha: Boolean
) {
    val isJpeg: Boolean get() = mimeType.equals("image/jpeg", ignoreCase = true) || mimeType.equals("image/jpg", ignoreCase = true)
    val isPng: Boolean get() = mimeType.equals("image/png", ignoreCase = true)
    val isWebp: Boolean get() = mimeType.equals("image/webp", ignoreCase = true)

    companion object {
        fun extract(context: Context, uri: Uri): ImageMetadata {
            val contentResolver = context.contentResolver
            var displayName = "image_${System.currentTimeMillis()}.jpg"
            var fileSizeBytes = 0L

            try {
                contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (cursor.moveToFirst()) {
                        if (nameIndex != -1) {
                            val name = cursor.getString(nameIndex)
                            if (!name.isNullOrBlank()) displayName = name
                        }
                        if (sizeIndex != -1) {
                            fileSizeBytes = cursor.getLong(sizeIndex)
                        }
                    }
                }
            } catch (_: Exception) {}

            if (fileSizeBytes == 0L) {
                try {
                    contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                        fileSizeBytes = pfd.statSize
                    }
                } catch (_: Exception) {}
            }

            var width = 0
            var height = 0
            var mimeType = "image/jpeg"

            try {
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    val options = BitmapFactory.Options().apply {
                        inJustDecodeBounds = true
                    }
                    BitmapFactory.decodeStream(inputStream, null, options)
                    width = options.outWidth
                    height = options.outHeight
                    if (!options.outMimeType.isNullOrBlank()) {
                        mimeType = options.outMimeType
                    }
                }
            } catch (_: Exception) {}

            var orientationDegrees = 0
            try {
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    val exifInterface = ExifInterface(inputStream)
                    val orientation = exifInterface.getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    )
                    orientationDegrees = when (orientation) {
                        ExifInterface.ORIENTATION_ROTATE_90 -> 90
                        ExifInterface.ORIENTATION_ROTATE_180 -> 180
                        ExifInterface.ORIENTATION_ROTATE_270 -> 270
                        else -> 0
                    }
                }
            } catch (_: Exception) {}

            // Swap dimensions if orientation is 90 or 270 so width & height match visual representation
            val (finalWidth, finalHeight) = if (orientationDegrees == 90 || orientationDegrees == 270) {
                Pair(height, width)
            } else {
                Pair(width, height)
            }

            val hasAlpha = mimeType.equals("image/png", ignoreCase = true) || mimeType.equals("image/webp", ignoreCase = true)

            return ImageMetadata(
                contentUri = uri.toString(),
                displayName = displayName,
                fileSizeBytes = fileSizeBytes,
                originalWidth = finalWidth,
                originalHeight = finalHeight,
                mimeType = mimeType,
                orientationDegrees = orientationDegrees,
                hasAlpha = hasAlpha
            )
        }
    }
}
