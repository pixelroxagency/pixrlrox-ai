package com.example.core.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.coroutineContext

data class PageManagerItem(
    val originalIndex: Int,
    val initialRotation: Int = 0,
    val additionalRotation: Int = 0,
    val thumbnail: Bitmap? = null
) {
    val totalRotation: Int
        get() = (initialRotation + additionalRotation) % 360
}

class PdfPageManagerEngine(private val context: Context) {

    suspend fun loadPageThumbnails(
        uri: Uri,
        onProgress: (current: Int, total: Int) -> Unit = { _, _ -> }
    ): List<PageManagerItem> = withContext(Dispatchers.IO) {
        val items = mutableListOf<PageManagerItem>()

        // 1. Read initial rotations with PDFBox
        val initialRotations = mutableListOf<Int>()
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val doc = PDDocument.load(stream)
            try {
                if (doc.isEncrypted) {
                    throw IllegalStateException("PDF is password-protected.")
                }
                for (i in 0 until doc.numberOfPages) {
                    initialRotations.add(doc.getPage(i).rotation)
                }
            } finally {
                doc.close()
            }
        } ?: throw IllegalStateException("Could not read PDF uri")

        // 2. Render low-res thumbnail previews with PdfRenderer
        val pfd = context.contentResolver.openFileDescriptor(uri, "r")
            ?: throw IllegalStateException("Failed to open file descriptor for PDF")

        pfd.use { descriptor ->
            val renderer = PdfRenderer(descriptor)
            try {
                val pageCount = renderer.pageCount
                for (i in 0 until pageCount) {
                    coroutineContext.ensureActive()
                    val page = renderer.openPage(i)
                    try {
                        val maxThumbDim = 240
                        val aspect = page.width.toFloat() / page.height.toFloat()
                        val thumbWidth: Int
                        val thumbHeight: Int
                        if (aspect > 1f) {
                            thumbWidth = maxThumbDim
                            thumbHeight = (maxThumbDim / aspect).toInt().coerceAtLeast(50)
                        } else {
                            thumbHeight = maxThumbDim
                            thumbWidth = (maxThumbDim * aspect).toInt().coerceAtLeast(50)
                        }

                        val bitmap = Bitmap.createBitmap(thumbWidth, thumbHeight, Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                        val rot = if (i < initialRotations.size) initialRotations[i] else 0
                        items.add(
                            PageManagerItem(
                                originalIndex = i,
                                initialRotation = rot,
                                additionalRotation = 0,
                                thumbnail = bitmap
                            )
                        )
                    } finally {
                        page.close()
                    }
                    onProgress(i + 1, pageCount)
                }
            } finally {
                renderer.close()
            }
        }

        items
    }

    suspend fun exportModifiedPdf(
        sourceUri: Uri,
        pageSequence: List<PageManagerItem>
    ): File = withContext(Dispatchers.IO) {
        if (pageSequence.isEmpty()) {
            throw IllegalArgumentException("The output PDF cannot have zero pages.")
        }

        val tempOutputFile = File.createTempFile("pdf_pagemanager_", ".pdf", context.cacheDir)

        context.contentResolver.openInputStream(sourceUri)?.use { stream ->
            val sourceDoc = PDDocument.load(stream)
            try {
                if (sourceDoc.isEncrypted) {
                    throw IllegalStateException("PDF is password-protected.")
                }

                val outDoc = PDDocument()
                try {
                    for (item in pageSequence) {
                        coroutineContext.ensureActive()
                        if (item.originalIndex in 0 until sourceDoc.numberOfPages) {
                            val originalPage = sourceDoc.getPage(item.originalIndex)
                            // Apply new rotation
                            originalPage.rotation = item.totalRotation
                            outDoc.addPage(originalPage)
                        }
                    }
                    outDoc.save(tempOutputFile)
                } finally {
                    outDoc.close()
                }
            } finally {
                sourceDoc.close()
            }
        } ?: throw IllegalStateException("Could not read source PDF stream")

        tempOutputFile
    }
}
