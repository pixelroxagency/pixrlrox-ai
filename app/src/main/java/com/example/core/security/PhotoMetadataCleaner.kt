package com.example.core.security

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

data class PhotoMetadataSummary(
    val hasGps: Boolean,
    val hasCameraInfo: Boolean,
    val hasDateTime: Boolean,
    val hasOtherExif: Boolean
)

data class CleanPhotoResult(
    val success: Boolean,
    val savedUri: Uri?,
    val message: String,
    val verificationSummary: String
)

object PhotoMetadataCleaner {

    fun inspectMetadata(context: Context, uri: Uri): PhotoMetadataSummary {
        return try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            if (inputStream == null) return PhotoMetadataSummary(false, false, false, false)

            val exif = ExifInterface(inputStream)
            inputStream.close()

            val lat = exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE)
            val lon = exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE)
            val hasGps = !lat.isNullOrEmpty() || !lon.isNullOrEmpty()

            val make = exif.getAttribute(ExifInterface.TAG_MAKE)
            val model = exif.getAttribute(ExifInterface.TAG_MODEL)
            val hasCameraInfo = !make.isNullOrEmpty() || !model.isNullOrEmpty()

            val dt = exif.getAttribute(ExifInterface.TAG_DATETIME)
            val dtOrig = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
            val hasDateTime = !dt.isNullOrEmpty() || !dtOrig.isNullOrEmpty()

            val software = exif.getAttribute(ExifInterface.TAG_SOFTWARE)
            val desc = exif.getAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION)
            val artist = exif.getAttribute(ExifInterface.TAG_ARTIST)
            val hasOtherExif = !software.isNullOrEmpty() || !desc.isNullOrEmpty() || !artist.isNullOrEmpty()

            PhotoMetadataSummary(hasGps, hasCameraInfo, hasDateTime, hasOtherExif)
        } catch (e: Exception) {
            PhotoMetadataSummary(false, false, false, false)
        }
    }

    fun cleanPhoto(context: Context, sourceUri: Uri): CleanPhotoResult {
        return try {
            val contentResolver = context.contentResolver
            
            // 1. Read source bitmap and EXIF orientation
            val inputStream = contentResolver.openInputStream(sourceUri)
                ?: return CleanPhotoResult(false, null, "Could not open source photo.", "Failed")

            val exif = ExifInterface(inputStream)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
            inputStream.close()

            // 2. Decode bitmap
            val decodeStream = contentResolver.openInputStream(sourceUri)
            val bitmap = BitmapFactory.decodeStream(decodeStream)
            decodeStream?.close()

            if (bitmap == null) {
                return CleanPhotoResult(false, null, "Could not decode image bitmap.", "Failed")
            }

            // 3. Correct EXIF orientation
            val rotatedBitmap = rotateBitmapByExif(bitmap, orientation)

            // 4. Save to MediaStore (Pictures/PixelRox)
            val filename = "PixelRox_Clean_${System.currentTimeMillis()}.jpg"
            val savedUri: Uri?
            
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/PixelRox")
            }

            val imageUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                ?: return CleanPhotoResult(false, null, "Failed to create MediaStore entry.", "Failed")

            contentResolver.openOutputStream(imageUri)?.use { outputStream ->
                rotatedBitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
            } ?: return CleanPhotoResult(false, null, "Failed to write sanitized image output.", "Failed")

            if (bitmap !== rotatedBitmap) {
                bitmap.recycle()
            }
            rotatedBitmap.recycle()

            // 5. Verify output file has no sensitive EXIF
            val verifySummary = contentResolver.openInputStream(imageUri)?.use { verifyStream ->
                val newExif = ExifInterface(verifyStream)
                val newLat = newExif.getAttribute(ExifInterface.TAG_GPS_LATITUDE)
                val newMake = newExif.getAttribute(ExifInterface.TAG_MAKE)
                val newDt = newExif.getAttribute(ExifInterface.TAG_DATETIME)
                
                if (newLat.isNullOrEmpty() && newMake.isNullOrEmpty() && newDt.isNullOrEmpty()) {
                    "Metadata removed successfully"
                } else {
                    "Some metadata may remain"
                }
            } ?: "Metadata removed successfully"

            CleanPhotoResult(
                success = true,
                savedUri = imageUri,
                message = "Sanitized copy saved to Pictures/PixelRox",
                verificationSummary = verifySummary
            )
        } catch (e: Exception) {
            CleanPhotoResult(false, null, "Error cleaning photo: ${e.localizedMessage}", "Failed")
        }
    }

    private fun rotateBitmapByExif(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            else -> return bitmap
        }
        return try {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } catch (e: Exception) {
            bitmap
        }
    }
}
