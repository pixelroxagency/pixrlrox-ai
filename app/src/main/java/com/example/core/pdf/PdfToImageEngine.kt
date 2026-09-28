package com.example.core.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.coroutineContext

enum class ImageRenderFormat(val extension: String, val mimeType: String, val compressFormat: Bitmap.CompressFormat) {
    PNG(".png", "image/png", Bitmap.CompressFormat.PNG),
    JPEG(".jpg", "image/jpeg", Bitmap.CompressFormat.JPEG)
}

data class RenderedPageImage(
    val pageIndex: Int,
    val file: File,
    val width: Int,
    val height: Int
)

class PdfToImageEngine(private val context: Context) {

    suspend fun renderPagesToImages(
        uri: Uri,
        pageIndices: List<Int>,
        format: ImageRenderFormat = ImageRenderFormat.PNG,
        quality: Int = 90,
        scaleFactor: Float = 2.0f,
        onProgress: (current: Int, total: Int) -> Unit = { _, _ -> }
    ): List<RenderedPageImage> = withContext(Dispatchers.IO) {
        val results = mutableListOf<RenderedPageImage>()
        val pfd = context.contentResolver.openFileDescriptor(uri, "r")
            ?: throw IllegalStateException("Failed to open file descriptor for PDF")

        pfd.use { descriptor ->
            val renderer = PdfRenderer(descriptor)
            try {
                val totalPages = renderer.pageCount
                val targetIndices = if (pageIndices.isEmpty()) {
                    (0 until totalPages).toList()
                } else {
                    pageIndices.filter { it in 0 until totalPages }
                }

                val total = targetIndices.size
                for (i in targetIndices.indices) {
                    coroutineContext.ensureActive()
                    val pageIdx = targetIndices[i]
                    val page = renderer.openPage(pageIdx)
                    try {
                        val renderWidth = (page.width * scaleFactor).toInt().coerceIn(100, 4000)
                        val renderHeight = (page.height * scaleFactor).toInt().coerceIn(100, 4000)

                        val bitmap = Bitmap.createBitmap(renderWidth, renderHeight, Bitmap.Config.ARGB_8888)
                        // Fill white background for PDF rendering (especially for JPEG and transparent backgrounds)
                        bitmap.eraseColor(Color.WHITE)

                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                        val tempImageFile = File.createTempFile("pdf_page_${pageIdx + 1}_", format.extension, context.cacheDir)
                        FileOutputStream(tempImageFile).use { out ->
                            bitmap.compress(format.compressFormat, quality, out)
                            out.flush()
                        }

                        results.add(
                            RenderedPageImage(
                                pageIndex = pageIdx,
                                file = tempImageFile,
                                width = renderWidth,
                                height = renderHeight
                            )
                        )
                        bitmap.recycle()
                    } finally {
                        page.close()
                    }
                    onProgress(i + 1, total)
                }
            } finally {
                renderer.close()
            }
        }

        results
    }
}
