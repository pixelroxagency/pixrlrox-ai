package com.example.core.audio

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

sealed interface AudioPublishResult {
    data class Success(
        val contentUri: Uri,
        val displayName: String,
        val fileSizeBytes: Long,
        val mimeType: String
    ) : AudioPublishResult

    data class Failure(
        val errorMessage: String,
        val cause: Throwable? = null
    ) : AudioPublishResult
}

class AudioOutputPublisher(private val context: Context) {

    suspend fun publishAudio(
        sourceFile: File,
        desiredDisplayName: String,
        format: AudioOutputFormat
    ): AudioPublishResult {
        return publishAudioFile(
            sourceFile = sourceFile,
            desiredDisplayName = desiredDisplayName,
            mimeType = format.mimeType,
            extension = format.extension
        )
    }

    suspend fun publishAudioFile(
        sourceFile: File,
        desiredDisplayName: String,
        mimeType: String,
        extension: String
    ): AudioPublishResult = withContext(Dispatchers.IO) {
        if (!sourceFile.exists() || sourceFile.length() == 0L) {
            return@withContext AudioPublishResult.Failure("Source audio file does not exist or is empty")
        }

        val finalDisplayName = generateSafeAudioDisplayName(desiredDisplayName, extension)
        val resolver = context.contentResolver
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

        val contentValues = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, finalDisplayName)
            put(MediaStore.Audio.Media.MIME_TYPE, mimeType)
            val titleName = finalDisplayName.substringBeforeLast(".")
            put(MediaStore.Audio.Media.TITLE, titleName)
            put(MediaStore.Audio.Media.ARTIST, "PixelRox AI")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/PixelRox")
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
        }

        val uri = try {
            resolver.insert(collection, contentValues)
        } catch (e: Exception) {
            return@withContext AudioPublishResult.Failure("Failed to create MediaStore entry: ${e.message}", e)
        }

        if (uri == null) {
            return@withContext AudioPublishResult.Failure("MediaStore returned null Uri")
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
                return@withContext AudioPublishResult.Failure("No bytes written to MediaStore")
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Audio.Media.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
            }

            return@withContext AudioPublishResult.Success(
                contentUri = uri,
                displayName = finalDisplayName,
                fileSizeBytes = bytesWritten,
                mimeType = mimeType
            )

        } catch (e: Exception) {
            try { resolver.delete(uri, null, null) } catch (_: Exception) {}
            return@withContext AudioPublishResult.Failure("Failed to publish audio file: ${e.message}", e)
        }
    }

    companion object {
        fun generateSafeAudioDisplayName(
            rawName: String,
            extension: String,
            timestamp: Long = System.currentTimeMillis()
        ): String {
            val nameWithoutExt = if (rawName.contains(".")) {
                rawName.substringBeforeLast(".")
            } else {
                rawName.ifBlank { "audio" }
            }
            val safeBaseName = nameWithoutExt.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(25)
            val timeFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
            val dateStr = timeFormat.format(Date(timestamp))
            val ext = if (extension.startsWith(".")) extension else ".$extension"
            return "${safeBaseName}_audio_$dateStr$ext"
        }

        fun generateSafeAudioDisplayName(
            rawName: String,
            format: AudioOutputFormat,
            timestamp: Long = System.currentTimeMillis()
        ): String {
            return generateSafeAudioDisplayName(rawName, format.extension, timestamp)
        }
    }
}
