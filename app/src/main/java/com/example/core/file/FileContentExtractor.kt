package com.example.core.file

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.BufferedReader
import java.io.InputStreamReader

data class ExtractedFileContent(
    val fileName: String,
    val mimeType: String,
    val fileSize: Long,
    val fullText: String,
    val isTruncated: Boolean,
    val totalChunks: Int,
    val chunks: List<String>
)

object FileContentExtractor {

    private const val MAX_CHUNK_SIZE = 4000 // characters per chunk
    private const val MAX_TOTAL_CHARS = 24000 // safe token boundary for single AI run

    fun extractContent(context: Context, uri: Uri): ExtractedFileContent {
        val contentResolver = context.contentResolver
        var fileName = "Unknown File"
        var fileSize = 0L

        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIndex != -1) fileName = cursor.getString(nameIndex) ?: "Unknown File"
                if (sizeIndex != -1) fileSize = cursor.getLong(sizeIndex)
            }
        }

        val mimeType = contentResolver.getType(uri) ?: getMimeTypeFromExtension(fileName)

        val rawText = try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { reader ->
                    reader.readText()
                }
            } ?: ""
        } catch (e: Exception) {
            "Failed to read content: ${e.localizedMessage}"
        }

        val isTruncated = rawText.length > MAX_TOTAL_CHARS
        val boundedText = if (isTruncated) rawText.substring(0, MAX_TOTAL_CHARS) else rawText

        val chunks = boundedText.chunked(MAX_CHUNK_SIZE)

        return ExtractedFileContent(
            fileName = fileName,
            mimeType = mimeType,
            fileSize = fileSize,
            fullText = boundedText,
            isTruncated = isTruncated,
            totalChunks = chunks.size,
            chunks = chunks
        )
    }

    fun isSupportedFileType(fileName: String, mimeType: String?): Boolean {
        val lower = fileName.lowercase()
        val m = mimeType?.lowercase() ?: ""
        return lower.endsWith(".txt") || lower.endsWith(".md") || lower.endsWith(".pdf") ||
                lower.endsWith(".json") || lower.endsWith(".csv") || lower.endsWith(".html") ||
                lower.endsWith(".xml") || lower.endsWith(".kt") || lower.endsWith(".java") ||
                lower.endsWith(".py") || lower.endsWith(".js") || lower.endsWith(".ts") ||
                m.contains("text") || m.contains("json") || m.contains("pdf") || m.contains("csv")
    }

    private fun getMimeTypeFromExtension(fileName: String): String {
        return when {
            fileName.endsWith(".pdf", ignoreCase = true) -> "application/pdf"
            fileName.endsWith(".json", ignoreCase = true) -> "application/json"
            fileName.endsWith(".csv", ignoreCase = true) -> "text/csv"
            fileName.endsWith(".md", ignoreCase = true) -> "text/markdown"
            fileName.endsWith(".html", ignoreCase = true) -> "text/html"
            else -> "text/plain"
        }
    }
}
