package com.example.core.image

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed interface ImagePublishResult {
    data class Success(
        val contentUri: Uri,
        val displayName: String,
        val fileSizeBytes: Long,
        val mimeType: String
    ) : ImagePublishResult

    data class Failure(
        val errorMessage: String,
        val cause: Throwable? = null
    ) : ImagePublishResult
}

class ImageOutputPublisher(private val context: Context) {

    suspend fun publishImage(
        sourceFile: File,
        desiredDisplayName: String,
        format: OutputFormat
    ): ImagePublishResult = withContext(Dispatchers.IO) {
        if (!sourceFile.exists() || sourceFile.length() == 0L) {
            return@withContext ImagePublishResult.Failure("Source image file does not exist or is empty")
        }

        val finalDisplayName = generateSafeImageDisplayName(desiredDisplayName, format)
        val resolver = context.contentResolver
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI

        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, finalDisplayName)
            put(MediaStore.Images.Media.MIME_TYPE, format.mimeType)
            val titleName = finalDisplayName.substringBeforeLast(".")
            put(MediaStore.Images.Media.TITLE, titleName)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/PixelRox")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val uri = try {
            resolver.insert(collection, contentValues)
        } catch (e: Exception) {
            return@withContext ImagePublishResult.Failure("Failed to create MediaStore entry: ${e.message}", e)
        }

        if (uri == null) {
            return@withContext ImagePublishResult.Failure("MediaStore returned null Uri")
        }

        var bytesWritten = 0L
        try {
            resolver.openOutputStream(uri)?.use { outputStream ->
                sourceFile.inputStream().use { inputStream ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    while (inputStream.read(buffer).also { read = it } != -1) {
                        currentCoroutineContext().ensureActive()
                        outputStream.write(buffer, 0, read)
                        bytesWritten += read
                    }
                    outputStream.flush()
                }
            } ?: throw IllegalStateException("Failed to open output stream for MediaStore Uri")

            currentCoroutineContext().ensureActive()

            if (bytesWritten <= 0) {
                try { resolver.delete(uri, null, null) } catch (_: Exception) {}
                return@withContext ImagePublishResult.Failure("No bytes written to MediaStore")
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
            }

            return@withContext ImagePublishResult.Success(
                contentUri = uri,
                displayName = finalDisplayName,
                fileSizeBytes = bytesWritten,
                mimeType = format.mimeType
            )

        } catch (e: Exception) {
            try { resolver.delete(uri, null, null) } catch (_: Exception) {}
            return@withContext ImagePublishResult.Failure("Failed to publish image file: ${e.message}", e)
        }
    }

    companion object {
        fun generateSafeImageDisplayName(
            rawName: String,
            format: OutputFormat,
            timestamp: Long = System.currentTimeMillis()
        ): String {
            val nameWithoutExt = if (rawName.contains(".")) {
                rawName.substringBeforeLast(".")
            } else {
                rawName.ifBlank { "photo" }
            }
            val safeBaseName = nameWithoutExt.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(25)
            val timeFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
            val dateStr = timeFormat.format(Date(timestamp))
            return "${safeBaseName}_compressed_$dateStr${format.extension}"
        }
    }
}
