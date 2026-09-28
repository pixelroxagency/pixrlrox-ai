package com.example.core.pdf

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed interface PdfPublishResult {
    data class Success(
        val contentUri: Uri,
        val displayName: String,
        val fileSizeBytes: Long,
        val mimeType: String = "application/pdf"
    ) : PdfPublishResult

    data class Failure(
        val errorMessage: String,
        val cause: Throwable? = null
    ) : PdfPublishResult
}

sealed interface ImageExportResult {
    data class Success(
        val contentUri: Uri,
        val displayName: String,
        val fileSizeBytes: Long,
        val mimeType: String
    ) : ImageExportResult

    data class Failure(
        val errorMessage: String,
        val cause: Throwable? = null
    ) : ImageExportResult
}

class PdfOutputPublisher(private val context: Context) {

    suspend fun publishPdf(
        sourceFile: File,
        desiredDisplayName: String
    ): PdfPublishResult = withContext(Dispatchers.IO) {
        if (!sourceFile.exists() || sourceFile.length() == 0L) {
            return@withContext PdfPublishResult.Failure("Source PDF file does not exist or is empty")
        }

        val finalDisplayName = generateSafePdfDisplayName(desiredDisplayName)
        val resolver = context.contentResolver

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Files.getContentUri("external")
        }

        val contentValues = ContentValues().apply {
            put(MediaStore.Files.FileColumns.DISPLAY_NAME, finalDisplayName)
            put(MediaStore.Files.FileColumns.MIME_TYPE, "application/pdf")
            val titleName = finalDisplayName.substringBeforeLast(".")
            put(MediaStore.Files.FileColumns.TITLE, titleName)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Files.FileColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + "/PixelRox")
                put(MediaStore.Files.FileColumns.IS_PENDING, 1)
            }
        }

        val uri = try {
            resolver.insert(collection, contentValues)
        } catch (e: Exception) {
            return@withContext PdfPublishResult.Failure("Failed to create MediaStore entry: ${e.message}", e)
        } ?: return@withContext PdfPublishResult.Failure("MediaStore returned null Uri")

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
                return@withContext PdfPublishResult.Failure("No bytes written to MediaStore")
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Files.FileColumns.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
            }

            return@withContext PdfPublishResult.Success(
                contentUri = uri,
                displayName = finalDisplayName,
                fileSizeBytes = bytesWritten
            )
        } catch (e: Exception) {
            try { resolver.delete(uri, null, null) } catch (_: Exception) {}
            return@withContext PdfPublishResult.Failure("Failed to publish PDF file: ${e.message}", e)
        }
    }

    suspend fun publishImage(
        bitmap: Bitmap,
        desiredDisplayName: String,
        format: Bitmap.CompressFormat = Bitmap.CompressFormat.PNG,
        quality: Int = 100
    ): ImageExportResult = withContext(Dispatchers.IO) {
        val extension = if (format == Bitmap.CompressFormat.JPEG) ".jpg" else ".png"
        val mimeType = if (format == Bitmap.CompressFormat.JPEG) "image/jpeg" else "image/png"
        val finalDisplayName = generateSafeImageDisplayName(desiredDisplayName, extension)
        val resolver = context.contentResolver
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI

        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, finalDisplayName)
            put(MediaStore.Images.Media.MIME_TYPE, mimeType)
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
            return@withContext ImageExportResult.Failure("Failed to create MediaStore image entry: ${e.message}", e)
        } ?: return@withContext ImageExportResult.Failure("MediaStore returned null image Uri")

        try {
            var bytesWritten = 0L
            resolver.openOutputStream(uri)?.use { outputStream ->
                bitmap.compress(format, quality, outputStream)
                outputStream.flush()
                bytesWritten = 1024L // approximate
            } ?: throw IllegalStateException("Could not open image output stream")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
            }

            return@withContext ImageExportResult.Success(
                contentUri = uri,
                displayName = finalDisplayName,
                fileSizeBytes = bytesWritten,
                mimeType = mimeType
            )
        } catch (e: Exception) {
            try { resolver.delete(uri, null, null) } catch (_: Exception) {}
            return@withContext ImageExportResult.Failure("Failed to save image: ${e.message}", e)
        }
    }

    companion object {
        fun generateSafePdfDisplayName(
            rawName: String,
            timestamp: Long = System.currentTimeMillis()
        ): String {
            val nameWithoutExt = if (rawName.endsWith(".pdf", ignoreCase = true)) {
                rawName.substringBeforeLast(".")
            } else {
                rawName.ifBlank { "document" }
            }
            val safeBaseName = nameWithoutExt.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(30)
            val timeFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
            val dateStr = timeFormat.format(Date(timestamp))
            return "${safeBaseName}_$dateStr.pdf"
        }

        fun generateSafeImageDisplayName(
            rawName: String,
            extension: String,
            timestamp: Long = System.currentTimeMillis()
        ): String {
            val nameWithoutExt = rawName.substringBeforeLast(".")
            val safeBaseName = nameWithoutExt.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(30)
            val timeFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
            val dateStr = timeFormat.format(Date(timestamp))
            return "${safeBaseName}_$dateStr$extension"
        }

        fun shareFileUri(context: Context, uri: Uri, mimeType: String) {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share File"))
        }
    }
}
