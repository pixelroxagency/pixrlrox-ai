package com.example.core.text

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.core.pdf.PdfOutputPublisher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets

data class OpenedTextFile(
    val uri: Uri,
    val fileName: String,
    val content: String,
    val sizeBytes: Long,
    val canWrite: Boolean
)

object TextFileManager {

    suspend fun openFile(context: Context, uri: Uri, maxBytes: Long = 5 * 1024 * 1024): OpenedTextFile = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        var fileName = "document.txt"
        var sizeBytes = 0L

        try {
            resolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx != -1) fileName = cursor.getString(nameIdx) ?: fileName
                    if (sizeIdx != -1) sizeBytes = cursor.getLong(sizeIdx)
                }
            }
        } catch (_: Exception) {}

        val content = resolver.openInputStream(uri)?.use { stream ->
            val bytes = stream.readBytes()
            if (bytes.size > maxBytes) {
                throw IllegalArgumentException("File size exceeds 5MB text editor limit.")
            }
            String(bytes, StandardCharsets.UTF_8)
        } ?: throw IllegalStateException("Could not read text file")

        // Test if writable
        val isWritable = try {
            resolver.openOutputStream(uri, "wa")?.use { true } ?: false
        } catch (_: Exception) {
            false
        }

        OpenedTextFile(
            uri = uri,
            fileName = fileName,
            content = content,
            sizeBytes = if (sizeBytes > 0) sizeBytes else content.length.toLong(),
            canWrite = isWritable
        )
    }

    suspend fun saveToUri(context: Context, uri: Uri, content: String) = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        resolver.openOutputStream(uri, "wt")?.use { stream ->
            stream.write(content.toByteArray(StandardCharsets.UTF_8))
            stream.flush()
        } ?: throw IllegalStateException("Could not open file for writing")
    }

    suspend fun saveNewFileToPixelRox(context: Context, fileName: String, content: String): Uri = withContext(Dispatchers.IO) {
        val tempFile = File.createTempFile("editor_", ".txt", context.cacheDir)
        FileOutputStream(tempFile).use { out ->
            out.write(content.toByteArray(StandardCharsets.UTF_8))
            out.flush()
        }
        val publisher = PdfOutputPublisher(context)
        val res = publisher.publishPdf(tempFile, fileName)
        tempFile.delete()
        when (res) {
            is com.example.core.pdf.PdfPublishResult.Success -> res.contentUri
            is com.example.core.pdf.PdfPublishResult.Failure -> throw IllegalStateException(res.errorMessage)
        }
    }
}
