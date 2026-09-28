package com.example.core.pdf

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

data class PdfMetadataInfo(
    val fileName: String,
    val fileSizeBytes: Long,
    val pageCount: Int,
    val pdfVersion: Float,
    val isEncrypted: Boolean,
    val title: String? = null,
    val author: String? = null,
    val subject: String? = null,
    val keywords: String? = null,
    val creator: String? = null,
    val producer: String? = null,
    val creationDate: String? = null,
    val modificationDate: String? = null
)

class PdfMetadataEngine(private val context: Context) {

    suspend fun extractMetadata(uri: Uri): PdfMetadataInfo = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        var displayName = "document.pdf"
        var sizeBytes = 0L

        try {
            resolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIndex != -1) displayName = cursor.getString(nameIndex) ?: displayName
                    if (sizeIndex != -1) sizeBytes = cursor.getLong(sizeIndex)
                }
            }
        } catch (_: Exception) {}

        val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.getDefault())

        resolver.openInputStream(uri)?.use { stream ->
            try {
                val document = PDDocument.load(stream)
                try {
                    val isEncrypted = document.isEncrypted
                    val pageCount = document.numberOfPages
                    val version = document.version

                    val info = document.documentInformation
                    val title = info?.title?.trim()?.ifBlank { null }
                    val author = info?.author?.trim()?.ifBlank { null }
                    val subject = info?.subject?.trim()?.ifBlank { null }
                    val keywords = info?.keywords?.trim()?.ifBlank { null }
                    val creator = info?.creator?.trim()?.ifBlank { null }
                    val producer = info?.producer?.trim()?.ifBlank { null }
                    val creationDate = info?.creationDate?.time?.let { dateFormat.format(it) }
                    val modDate = info?.modificationDate?.time?.let { dateFormat.format(it) }

                    PdfMetadataInfo(
                        fileName = displayName,
                        fileSizeBytes = sizeBytes,
                        pageCount = pageCount,
                        pdfVersion = version,
                        isEncrypted = isEncrypted,
                        title = title,
                        author = author,
                        subject = subject,
                        keywords = keywords,
                        creator = creator,
                        producer = producer,
                        creationDate = creationDate,
                        modificationDate = modDate
                    )
                } finally {
                    document.close()
                }
            } catch (e: InvalidPasswordException) {
                PdfMetadataInfo(
                    fileName = displayName,
                    fileSizeBytes = sizeBytes,
                    pageCount = 0,
                    pdfVersion = 1.4f,
                    isEncrypted = true,
                    title = "Password Protected PDF"
                )
            }
        } ?: throw IllegalStateException("Failed to open PDF stream for metadata inspection")
    }
}
