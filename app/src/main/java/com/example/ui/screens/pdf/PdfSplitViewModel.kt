package com.example.ui.screens.pdf

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.pdf.PdfOutputPublisher
import com.example.core.pdf.PdfPublishResult
import com.example.core.pdf.PdfSplitEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SplitMode(val displayName: String) {
    RANGE("Custom Page Range (e.g. 1-3,5,8-10)"),
    ALL_PAGES("Split Every Page into Individual PDFs")
}

data class PdfSplitUiState(
    val selectedPdfUri: Uri? = null,
    val selectedPdfName: String? = null,
    val totalPages: Int = 0,
    val splitMode: SplitMode = SplitMode.RANGE,
    val pageRangeInput: String = "1",
    val parsedPages: List<Int> = listOf(0),
    val isProcessing: Boolean = false,
    val progressCurrent: Int = 0,
    val progressTotal: Int = 0,
    val statusMessage: String? = null,
    val singleResult: PdfPublishResult.Success? = null,
    val multipleResults: List<PdfPublishResult.Success> = emptyList(),
    val errorMessage: String? = null
)

class PdfSplitViewModel(application: Application) : AndroidViewModel(application) {

    private val engine = PdfSplitEngine(application)
    private val publisher = PdfOutputPublisher(application)

    private val _uiState = MutableStateFlow(PdfSplitUiState())
    val uiState: StateFlow<PdfSplitUiState> = _uiState.asStateFlow()

    fun selectPdf(uri: Uri) {
        val name = uri.lastPathSegment ?: "document.pdf"
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isProcessing = true,
                    statusMessage = "Analyzing PDF document...",
                    errorMessage = null,
                    singleResult = null,
                    multipleResults = emptyList()
                )
            }

            try {
                val pageCount = engine.getPageCount(uri)
                val defaultRange = if (pageCount > 1) "1-${minOf(pageCount, 3)}" else "1"
                val parsed = engine.parsePageRange(defaultRange, pageCount)
                _uiState.update {
                    it.copy(
                        selectedPdfUri = uri,
                        selectedPdfName = name,
                        totalPages = pageCount,
                        pageRangeInput = defaultRange,
                        parsedPages = parsed,
                        isProcessing = false,
                        statusMessage = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        statusMessage = null,
                        errorMessage = "Failed to load PDF: ${e.message}"
                    )
                }
            }
        }
    }

    fun setSplitMode(mode: SplitMode) {
        _uiState.update { it.copy(splitMode = mode) }
    }

    fun updateRangeInput(input: String) {
        val total = _uiState.value.totalPages
        val parsed = engine.parsePageRange(input, total)
        _uiState.update {
            it.copy(
                pageRangeInput = input,
                parsedPages = parsed
            )
        }
    }

    fun clear() {
        _uiState.value = PdfSplitUiState()
    }

    fun splitPdf() {
        val uri = _uiState.value.selectedPdfUri ?: return
        val total = _uiState.value.totalPages
        if (total <= 0) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isProcessing = true,
                    statusMessage = "Processing PDF split...",
                    errorMessage = null,
                    singleResult = null,
                    multipleResults = emptyList()
                )
            }

            try {
                if (_uiState.value.splitMode == SplitMode.RANGE) {
                    val indices = _uiState.value.parsedPages
                    if (indices.isEmpty()) {
                        _uiState.update {
                            it.copy(
                                isProcessing = false,
                                errorMessage = "No valid page numbers found in range expression."
                            )
                        }
                        return@launch
                    }

                    val baseName = (_uiState.value.selectedPdfName ?: "document").substringBeforeLast(".")
                    val tempSplitFile = engine.extractPagesToSinglePdf(uri, indices, "${baseName}_split")

                    _uiState.update { it.copy(statusMessage = "Saving to Documents/PixelRox...") }
                    val pubResult = publisher.publishPdf(tempSplitFile, "${baseName}_extracted")
                    tempSplitFile.delete()

                    when (pubResult) {
                        is PdfPublishResult.Success -> {
                            _uiState.update {
                                it.copy(
                                    isProcessing = false,
                                    statusMessage = null,
                                    singleResult = pubResult
                                )
                            }
                        }
                        is PdfPublishResult.Failure -> {
                            _uiState.update {
                                it.copy(
                                    isProcessing = false,
                                    statusMessage = null,
                                    errorMessage = pubResult.errorMessage
                                )
                            }
                        }
                    }
                } else {
                    // Split every page
                    val splitRes = engine.splitEveryPage(
                        uri = uri,
                        onProgress = { cur, tot ->
                            _uiState.update {
                                it.copy(
                                    progressCurrent = cur,
                                    progressTotal = tot,
                                    statusMessage = "Splitting page $cur of $tot..."
                                )
                            }
                        }
                    )

                    val successfulPublishes = mutableListOf<PdfPublishResult.Success>()
                    val baseName = (_uiState.value.selectedPdfName ?: "document").substringBeforeLast(".")

                    for (i in splitRes.outputFiles.indices) {
                        val file = splitRes.outputFiles[i]
                        val pub = publisher.publishPdf(file, "${baseName}_page_${i + 1}")
                        file.delete()
                        if (pub is PdfPublishResult.Success) {
                            successfulPublishes.add(pub)
                        }
                    }

                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            statusMessage = null,
                            multipleResults = successfulPublishes
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        statusMessage = null,
                        errorMessage = e.message ?: "Failed to split PDF"
                    )
                }
            }
        }
    }
}
