package com.example.ui.screens.pdf

import android.app.Application
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.pdf.ImageExportResult
import com.example.core.pdf.ImageRenderFormat
import com.example.core.pdf.PdfOutputPublisher
import com.example.core.pdf.PdfSplitEngine
import com.example.core.pdf.PdfToImageEngine
import com.example.core.pdf.RenderedPageImage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PdfToImageUiState(
    val selectedPdfUri: Uri? = null,
    val selectedPdfName: String? = null,
    val totalPages: Int = 0,
    val renderAllPages: Boolean = true,
    val pageRangeInput: String = "",
    val parsedPages: List<Int> = emptyList(),
    val format: ImageRenderFormat = ImageRenderFormat.PNG,
    val jpegQuality: Int = 90,
    val scaleFactor: Float = 2.0f,
    val isProcessing: Boolean = false,
    val progressCurrent: Int = 0,
    val progressTotal: Int = 0,
    val statusMessage: String? = null,
    val exportedImages: List<ImageExportResult.Success> = emptyList(),
    val errorMessage: String? = null
)

class PdfToImageViewModel(application: Application) : AndroidViewModel(application) {

    private val splitEngine = PdfSplitEngine(application)
    private val renderEngine = PdfToImageEngine(application)
    private val publisher = PdfOutputPublisher(application)

    private val _uiState = MutableStateFlow(PdfToImageUiState())
    val uiState: StateFlow<PdfToImageUiState> = _uiState.asStateFlow()

    fun selectPdf(uri: Uri) {
        val name = uri.lastPathSegment ?: "document.pdf"
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isProcessing = true,
                    statusMessage = "Reading PDF pages...",
                    errorMessage = null,
                    exportedImages = emptyList()
                )
            }

            try {
                val pageCount = splitEngine.getPageCount(uri)
                val allPages = (0 until pageCount).toList()
                _uiState.update {
                    it.copy(
                        selectedPdfUri = uri,
                        selectedPdfName = name,
                        totalPages = pageCount,
                        renderAllPages = true,
                        pageRangeInput = if (pageCount > 0) "1-$pageCount" else "1",
                        parsedPages = allPages,
                        isProcessing = false,
                        statusMessage = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        statusMessage = null,
                        errorMessage = "Failed to open PDF: ${e.message}"
                    )
                }
            }
        }
    }

    fun setRenderAllPages(all: Boolean) {
        val count = _uiState.value.totalPages
        val parsed = if (all) {
            (0 until count).toList()
        } else {
            splitEngine.parsePageRange(_uiState.value.pageRangeInput, count)
        }
        _uiState.update {
            it.copy(
                renderAllPages = all,
                parsedPages = parsed
            )
        }
    }

    fun updateRangeInput(input: String) {
        val count = _uiState.value.totalPages
        val parsed = splitEngine.parsePageRange(input, count)
        _uiState.update {
            it.copy(
                pageRangeInput = input,
                parsedPages = parsed
            )
        }
    }

    fun setFormat(format: ImageRenderFormat) {
        _uiState.update { it.copy(format = format) }
    }

    fun setJpegQuality(quality: Int) {
        _uiState.update { it.copy(jpegQuality = quality.coerceIn(30, 100)) }
    }

    fun setScaleFactor(scale: Float) {
        _uiState.update { it.copy(scaleFactor = scale) }
    }

    fun clear() {
        _uiState.value = PdfToImageUiState()
    }

    fun renderToImages() {
        val uri = _uiState.value.selectedPdfUri ?: return
        val pagesToRender = if (_uiState.value.renderAllPages) {
            (0 until _uiState.value.totalPages).toList()
        } else {
            _uiState.value.parsedPages
        }

        if (pagesToRender.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Please specify valid pages to render.") }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isProcessing = true,
                    progressCurrent = 0,
                    progressTotal = pagesToRender.size,
                    statusMessage = "Rendering ${pagesToRender.size} pages to images...",
                    errorMessage = null,
                    exportedImages = emptyList()
                )
            }

            try {
                val renderedPages = renderEngine.renderPagesToImages(
                    uri = uri,
                    pageIndices = pagesToRender,
                    format = _uiState.value.format,
                    quality = _uiState.value.jpegQuality,
                    scaleFactor = _uiState.value.scaleFactor,
                    onProgress = { cur, tot ->
                        _uiState.update {
                            it.copy(
                                progressCurrent = cur,
                                progressTotal = tot,
                                statusMessage = "Rendering page $cur of $tot..."
                            )
                        }
                    }
                )

                _uiState.update { it.copy(statusMessage = "Saving images to Pictures/PixelRox...") }
                val successes = mutableListOf<ImageExportResult.Success>()
                val baseName = (_uiState.value.selectedPdfName ?: "document").substringBeforeLast(".")

                for (page in renderedPages) {
                    val bmp = BitmapFactory.decodeFile(page.file.absolutePath)
                    if (bmp != null) {
                        val pub = publisher.publishImage(
                            bitmap = bmp,
                            desiredDisplayName = "${baseName}_page_${page.pageIndex + 1}",
                            format = _uiState.value.format.compressFormat,
                            quality = _uiState.value.jpegQuality
                        )
                        bmp.recycle()
                        if (pub is ImageExportResult.Success) {
                            successes.add(pub)
                        }
                    }
                    page.file.delete()
                }

                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        statusMessage = null,
                        exportedImages = successes
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        statusMessage = null,
                        errorMessage = e.message ?: "Failed to render PDF to images"
                    )
                }
            }
        }
    }
}
