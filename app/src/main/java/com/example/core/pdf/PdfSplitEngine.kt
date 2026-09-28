package com.example.core.pdf

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.coroutineContext

data class SplitResult(
    val outputFiles: List<File>,
    val totalExtractedPages: Int
)

class PdfSplitEngine(private val context: Context) {

    suspend fun getPageCount(uri: Uri): Int = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            try {
                val document = PDDocument.load(stream)
                try {
                    if (document.isEncrypted) {
                        throw IllegalStateException("PDF is password-protected.")
                    }
                    document.numberOfPages
                } finally {
                    document.close()
                }
            } catch (e: InvalidPasswordException) {
                throw IllegalStateException("PDF is password-protected: ${e.message}")
            }
        } ?: throw IllegalStateException("Could not read PDF uri")
    }

    /**
     * Parses page expression string like "1-3,5,8-10" or "1, 3, 5" into 0-based page indices.
     */
    fun parsePageRange(rangeStr: String, maxPages: Int): List<Int> {
        val cleaned = rangeStr.trim()
        if (cleaned.isEmpty()) return emptyList()

        val indices = mutableListOf<Int>()
        val segments = cleaned.split(",")

        for (seg in segments) {
            val trimmed = seg.trim()
            if (trimmed.isEmpty()) continue

            if (trimmed.contains("-")) {
                val parts = trimmed.split("-")
                if (parts.size == 2) {
                    val start = parts[0].trim().toIntOrNull() ?: continue
                    val end = parts[1].trim().toIntOrNull() ?: continue
                    val min = minOf(start, end).coerceIn(1, maxPages)
                    val max = maxOf(start, end).coerceIn(1, maxPages)
                    for (p in min..max) {
                        indices.add(p - 1)
                    }
                }
            } else {
                val pageNum = trimmed.toIntOrNull() ?: continue
                if (pageNum in 1..maxPages) {
                    indices.add(pageNum - 1)
                }
            }
        }

        return indices.distinct()
    }

    suspend fun extractPagesToSinglePdf(
        uri: Uri,
        pageIndices: List<Int>,
        outputNamePrefix: String = "split_document"
    ): File = withContext(Dispatchers.IO) {
        if (pageIndices.isEmpty()) {
            throw IllegalArgumentException("No valid pages selected for extraction.")
        }

        val tempOutputFile = File.createTempFile("${outputNamePrefix}_", ".pdf", context.cacheDir)

        context.contentResolver.openInputStream(uri)?.use { stream ->
            val sourceDoc = PDDocument.load(stream)
            try {
                if (sourceDoc.isEncrypted) {
                    throw IllegalStateException("PDF is password-protected.")
                }

                val outDoc = PDDocument()
                try {
                    for (index in pageIndices) {
                        coroutineContext.ensureActive()
                        if (index in 0 until sourceDoc.numberOfPages) {
                            val page = sourceDoc.getPage(index)
                            outDoc.addPage(page)
                        }
                    }
                    outDoc.save(tempOutputFile)
                } finally {
                    outDoc.close()
                }
            } finally {
                sourceDoc.close()
            }
        } ?: throw IllegalStateException("Could not open source PDF stream")

        tempOutputFile
    }

    suspend fun splitEveryPage(
        uri: Uri,
        onProgress: (current: Int, total: Int) -> Unit = { _, _ -> }
    ): SplitResult = withContext(Dispatchers.IO) {
        val outputFiles = mutableListOf<File>()

        context.contentResolver.openInputStream(uri)?.use { stream ->
            val sourceDoc = PDDocument.load(stream)
            try {
                if (sourceDoc.isEncrypted) {
                    throw IllegalStateException("PDF is password-protected.")
                }

                val pageCount = sourceDoc.numberOfPages
                for (i in 0 until pageCount) {
                    coroutineContext.ensureActive()
                    val pageFile = File.createTempFile("page_${i + 1}_", ".pdf", context.cacheDir)
                    val singlePageDoc = PDDocument()
                    try {
                        singlePageDoc.addPage(sourceDoc.getPage(i))
                        singlePageDoc.save(pageFile)
                        outputFiles.add(pageFile)
                    } finally {
                        singlePageDoc.close()
                    }
                    onProgress(i + 1, pageCount)
                }
            } finally {
                sourceDoc.close()
            }
        } ?: throw IllegalStateException("Could not open source PDF stream")

        SplitResult(outputFiles, outputFiles.size)
    }
}
