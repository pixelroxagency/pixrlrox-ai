package com.example.ui.screens.pdf

import android.app.Application
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.pdf.ExtractedPdfImage
import com.example.core.pdf.ImageExportResult
import com.example.core.pdf.PdfImageExtractorEngine
import com.example.core.pdf.PdfOutputPublisher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PdfImageExtractorUiState(
    val selectedPdfUri: Uri? = null,
    val selectedPdfName: String? = null,
    val isProcessing: Boolean = false,
    val progressCurrent: Int = 0,
    val progressTotal: Int = 0,
    val imagesFoundCount: Int = 0,
    val statusMessage: String? = null,
    val extractedImages: List<ExtractedPdfImage> = emptyList(),
    val exportedImages: List<ImageExportResult.Success> = emptyList(),
    val errorMessage: String? = null
)

class PdfImageExtractorViewModel(application: Application) : AndroidViewModel(application) {

    private val engine = PdfImageExtractorEngine(application)
    private val publisher = PdfOutputPublisher(application)

    private val _uiState = MutableStateFlow(PdfImageExtractorUiState())
    val uiState: StateFlow<PdfImageExtractorUiState> = _uiState.asStateFlow()

    fun selectPdf(uri: Uri) {
        val name = uri.lastPathSegment ?: "document.pdf"
        _uiState.update {
            it.copy(
                selectedPdfUri = uri,
                selectedPdfName = name,
                extractedImages = emptyList(),
                exportedImages = emptyList(),
                errorMessage = null
            )
        }
    }

    fun extractImages() {
        val uri = _uiState.value.selectedPdfUri ?: return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isProcessing = true,
                    progressCurrent = 0,
                    progressTotal = 0,
                    imagesFoundCount = 0,
                    statusMessage = "Scanning PDF pages for embedded images...",
                    errorMessage = null,
                    extractedImages = emptyList(),
                    exportedImages = emptyList()
                )
            }

            try {
                val list = engine.extractEmbeddedImages(
                    uri = uri,
                    onProgress = { page, totalPages, found ->
                        _uiState.update {
                            it.copy(
                                progressCurrent = page,
                                progressTotal = totalPages,
                                imagesFoundCount = found,
                                statusMessage = "Scanning page $page of $totalPages (found $found images)..."
                            )
                        }
                    }
                )

                if (list.isEmpty()) {
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            statusMessage = null,
                            errorMessage = "No embedded raster images found in this PDF document."
                        )
                    }
                    return@launch
                }

                _uiState.update { it.copy(statusMessage = "Saving ${list.size} extracted images to Pictures/PixelRox...") }
                val successes = mutableListOf<ImageExportResult.Success>()
                val baseName = (_uiState.value.selectedPdfName ?: "document").substringBeforeLast(".")

                for (item in list) {
                    val bmp = item.previewBitmap ?: BitmapFactory.decodeFile(item.file.absolutePath)
                    if (bmp != null) {
                        val pub = publisher.publishImage(
                            bitmap = bmp,
                            desiredDisplayName = "${baseName}_p${item.pageNumber}_img${item.imageIndex}",
                            format = if (item.suffix.equals("jpg", true) || item.suffix.equals("jpeg", true)) {
                                android.graphics.Bitmap.CompressFormat.JPEG
                            } else {
                                android.graphics.Bitmap.CompressFormat.PNG
                            }
                        )
                        if (pub is ImageExportResult.Success) {
                            successes.add(pub)
                        }
                    }
                }

                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        statusMessage = null,
                        extractedImages = list,
                        exportedImages = successes
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        statusMessage = null,
                        errorMessage = e.message ?: "Failed to extract embedded images"
                    )
                }
            }
        }
    }

    fun clear() {
        _uiState.value = PdfImageExtractorUiState()
    }
}
