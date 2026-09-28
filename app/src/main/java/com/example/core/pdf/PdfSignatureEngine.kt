package com.example.core.pdf

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

data class SignatureStampConfig(
    val pageIndex: Int,
    val normalizedX: Float, // 0.0 to 1.0 (from left)
    val normalizedY: Float, // 0.0 to 1.0 (from top)
    val normalizedWidth: Float // 0.1 to 0.8 (fraction of page width)
)

class PdfSignatureEngine(private val context: Context) {

    suspend fun saveSignatureImage(signatureBitmap: Bitmap): File = withContext(Dispatchers.IO) {
        val tempFile = File.createTempFile("signature_export_", ".png", context.cacheDir)
        FileOutputStream(tempFile).use { out ->
            signatureBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.flush()
        }
        tempFile
    }

    suspend fun applySignatureToPdf(
        pdfUri: Uri,
        signatureBitmap: Bitmap,
        config: SignatureStampConfig
    ): File = withContext(Dispatchers.IO) {
        val tempOutputFile = File.createTempFile("signed_document_", ".pdf", context.cacheDir)

        context.contentResolver.openInputStream(pdfUri)?.use { stream ->
            val document = PDDocument.load(stream)
            try {
                if (document.isEncrypted) {
                    throw IllegalStateException("PDF is password-protected.")
                }

                if (config.pageIndex !in 0 until document.numberOfPages) {
                    throw IllegalArgumentException("Invalid page index ${config.pageIndex} for document with ${document.numberOfPages} pages.")
                }

                val page = document.getPage(config.pageIndex)
                val mediaBox = page.mediaBox
                val pageWidth = mediaBox.width
                val pageHeight = mediaBox.height

                // Create Lossless image XObject for transparent PNG signature
                val pdImage = LosslessFactory.createFromImage(document, signatureBitmap)

                val targetWidthPt = (pageWidth * config.normalizedWidth).coerceIn(20f, pageWidth)
                val aspect = signatureBitmap.height.toFloat() / signatureBitmap.width.toFloat()
                val targetHeightPt = targetWidthPt * aspect

                val targetXPt = (config.normalizedX * pageWidth).coerceIn(0f, pageWidth - targetWidthPt)
                // In PDF coordinates, (0,0) is bottom-left, whereas normalizedY is from top:
                val targetYPt = (pageHeight - (config.normalizedY * pageHeight) - targetHeightPt).coerceIn(0f, pageHeight - targetHeightPt)

                // Append signature image to page content stream preserving existing text and graphics
                val contentStream = PDPageContentStream(
                    document,
                    page,
                    PDPageContentStream.AppendMode.APPEND,
                    true,
                    true
                )
                try {
                    contentStream.drawImage(pdImage, targetXPt, targetYPt, targetWidthPt, targetHeightPt)
                } finally {
                    contentStream.close()
                }

                document.save(tempOutputFile)
            } finally {
                document.close()
            }
        } ?: throw IllegalStateException("Failed to open source PDF stream")

        tempOutputFile
    }
}
