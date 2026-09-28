package com.example.core.video

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

interface IVideoOutputPublisher {
    suspend fun publishVideo(
        sourceFile: File,
        desiredDisplayName: String,
        mimeType: String = "video/mp4"
    ): VideoPublishResult
}

class VideoOutputPublisher(private val context: Context) : IVideoOutputPublisher {

    /**
     * Publishes a successfully compressed temporary video file into Android MediaStore.
     * Uses scoped storage (Movies/PixelRox) on API 29+.
     * On failure, cleans up any incomplete MediaStore row while preserving the source temp file for retry.
     */
    override suspend fun publishVideo(
        sourceFile: File,
        desiredDisplayName: String,
        mimeType: String
    ): VideoPublishResult = withContext(Dispatchers.IO) {
        if (!sourceFile.exists() || sourceFile.length() == 0L) {
            return@withContext VideoPublishResult.Failure("Source compressed file does not exist or is empty")
        }

        val finalDisplayName = generateSafeDisplayName(desiredDisplayName)
        val resolver = context.contentResolver
        val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI

        val contentValues = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, finalDisplayName)
            put(MediaStore.Video.Media.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/PixelRox")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }

        val uri = try {
            resolver.insert(collection, contentValues)
        } catch (e: Exception) {
            return@withContext VideoPublishResult.Failure("Failed to create MediaStore entry: ${e.message}", e)
        }

        if (uri == null) {
            return@withContext VideoPublishResult.Failure("MediaStore returned null Uri")
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
                try {
                    resolver.delete(uri, null, null)
                } catch (_: Exception) {}
                return@withContext VideoPublishResult.Failure("No bytes written to MediaStore")
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Video.Media.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
            }

            return@withContext VideoPublishResult.Success(
                contentUri = uri,
                displayName = finalDisplayName,
                fileSizeBytes = bytesWritten,
                mimeType = mimeType
            )

        } catch (e: Exception) {
            try {
                resolver.delete(uri, null, null)
            } catch (_: Exception) {}

            return@withContext VideoPublishResult.Failure("Failed to publish video: ${e.message}", e)
        }
    }

    companion object {
        fun generateSafeDisplayName(rawName: String): String {
            val sanitized = rawName.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
            val baseName = if (sanitized.isBlank()) {
                "video"
            } else {
                val lastDot = sanitized.lastIndexOf('.')
                if (lastDot > 0) sanitized.substring(0, lastDot) else sanitized
            }

            val extension = if (sanitized.contains('.')) {
                val ext = sanitized.substring(sanitized.lastIndexOf('.'))
                if (ext.length in 2..5) ext else ".mp4"
            } else {
                ".mp4"
            }

            val cleanBase = if (baseName.endsWith("_compressed")) baseName else "${baseName}_compressed"
            val timestamp = System.currentTimeMillis()
            return "$cleanBase$timestamp$extension"
        }
    }
}
