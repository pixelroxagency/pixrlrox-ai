package com.example.core.pdf

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.coroutineContext

data class ExtractedPdfImage(
    val pageNumber: Int,
    val imageIndex: Int,
    val width: Int,
    val height: Int,
    val suffix: String,
    val file: File,
    val previewBitmap: Bitmap? = null
)

class PdfImageExtractorEngine(private val context: Context) {

    suspend fun extractEmbeddedImages(
        uri: Uri,
        onProgress: (page: Int, totalPages: Int, imagesFound: Int) -> Unit = { _, _, _ -> }
    ): List<ExtractedPdfImage> = withContext(Dispatchers.IO) {
        val extractedList = mutableListOf<ExtractedPdfImage>()

        context.contentResolver.openInputStream(uri)?.use { stream ->
            val document = PDDocument.load(stream)
            try {
                if (document.isEncrypted) {
                    throw IllegalStateException("PDF is password-protected.")
                }

                val totalPages = document.numberOfPages
                var totalImagesFound = 0

                for (pageIndex in 0 until totalPages) {
                    coroutineContext.ensureActive()
                    val page = document.getPage(pageIndex)
                    val resources = page.resources ?: continue

                    var imgOnPage = 0
                    for (xName in resources.xObjectNames) {
                        coroutineContext.ensureActive()
                        val xObject = try {
                            resources.getXObject(xName)
                        } catch (_: Exception) {
                            null
                        }

                        if (xObject is PDImageXObject) {
                            imgOnPage++
                            totalImagesFound++

                            val bitmap = try {
                                xObject.image
                            } catch (_: Exception) {
                                null
                            }

                            val suffix = xObject.suffix ?: "png"
                            val ext = if (suffix.equals("jpg", ignoreCase = true) || suffix.equals("jpeg", ignoreCase = true)) {
                                ".jpg"
                            } else {
                                ".png"
                            }
                            val compressFormat = if (ext == ".jpg") Bitmap.CompressFormat.JPEG else Bitmap.CompressFormat.PNG

                            val tempFile = File.createTempFile("extracted_p${pageIndex + 1}_img${imgOnPage}_", ext, context.cacheDir)

                            if (bitmap != null) {
                                FileOutputStream(tempFile).use { fos ->
                                    bitmap.compress(compressFormat, 100, fos)
                                    fos.flush()
                                }

                                extractedList.add(
                                    ExtractedPdfImage(
                                        pageNumber = pageIndex + 1,
                                        imageIndex = imgOnPage,
                                        width = xObject.width,
                                        height = xObject.height,
                                        suffix = suffix,
                                        file = tempFile,
                                        previewBitmap = bitmap
                                    )
                                )
                            }
                        }
                    }
                    onProgress(pageIndex + 1, totalPages, totalImagesFound)
                }
            } finally {
                document.close()
            }
        } ?: throw IllegalStateException("Could not read PDF stream")

        extractedList
    }
}
