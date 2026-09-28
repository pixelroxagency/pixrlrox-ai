package com.example.core.pdf

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import kotlin.coroutines.coroutineContext

data class PdfMergeInputItem(
    val uri: Uri,
    val displayName: String,
    val pageCount: Int = 0
)

class PdfMergeEngine(private val context: Context) {

    suspend fun inspectPdf(uri: Uri, displayName: String): PdfMergeInputItem = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        var pageCount = 0
        resolver.openInputStream(uri)?.use { stream ->
            try {
                val document = PDDocument.load(stream)
                try {
                    if (document.isEncrypted) {
                        throw IllegalStateException("PDF is encrypted/password-protected.")
                    }
                    pageCount = document.numberOfPages
                } finally {
                    document.close()
                }
            } catch (e: InvalidPasswordException) {
                throw IllegalStateException("PDF is password-protected: ${e.message}")
            }
        }
        PdfMergeInputItem(uri = uri, displayName = displayName, pageCount = pageCount)
    }

    suspend fun mergePdfs(
        items: List<PdfMergeInputItem>,
        onProgress: (current: Int, total: Int) -> Unit = { _, _ -> }
    ): File = withContext(Dispatchers.IO) {
        if (items.size < 2) {
            throw IllegalArgumentException("At least 2 PDF files are required to merge.")
        }

        val tempOutputFile = File.createTempFile("pdf_merged_", ".pdf", context.cacheDir)
        val merger = PDFMergerUtility()
        merger.destinationFileName = tempOutputFile.absolutePath

        val tempSources = mutableListOf<File>()

        try {
            val total = items.size
            for (i in items.indices) {
                coroutineContext.ensureActive()
                val item = items[i]
                val tempSource = File.createTempFile("merge_src_$i", ".pdf", context.cacheDir)
                tempSources.add(tempSource)

                context.contentResolver.openInputStream(item.uri)?.use { input ->
                    FileOutputStream(tempSource).use { output ->
                        input.copyTo(output)
                    }
                } ?: throw IllegalStateException("Could not read source PDF: ${item.displayName}")

                // Verify file is readable and not encrypted
                try {
                    val testDoc = PDDocument.load(tempSource)
                    if (testDoc.isEncrypted) {
                        testDoc.close()
                        throw IllegalStateException("File '${item.displayName}' is password protected and cannot be merged.")
                    }
                    testDoc.close()
                } catch (e: InvalidPasswordException) {
                    throw IllegalStateException("File '${item.displayName}' is password protected: ${e.message}")
                }

                merger.addSource(tempSource)
                onProgress(i + 1, total)
            }

            coroutineContext.ensureActive()
            merger.mergeDocuments(MemoryUsageSetting.setupTempFileOnly())
            tempOutputFile
        } finally {
            tempSources.forEach { it.delete() }
        }
    }
}
