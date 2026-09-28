package com.example.ui.screens.pdf

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.pdf.PageManagerItem
import com.example.core.pdf.PdfOutputPublisher
import com.example.core.pdf.PdfPageManagerEngine
import com.example.core.pdf.PdfPublishResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PdfPageManagerUiState(
    val selectedPdfUri: Uri? = null,
    val selectedPdfName: String? = null,
    val pages: List<PageManagerItem> = emptyList(),
    val selectedPageIndex: Int = 0,
    val isProcessing: Boolean = false,
    val progressCurrent: Int = 0,
    val progressTotal: Int = 0,
    val statusMessage: String? = null,
    val result: PdfPublishResult.Success? = null,
    val errorMessage: String? = null
)

class PdfPageManagerViewModel(application: Application) : AndroidViewModel(application) {

    private val engine = PdfPageManagerEngine(application)
    private val publisher = PdfOutputPublisher(application)

    private val _uiState = MutableStateFlow(PdfPageManagerUiState())
    val uiState: StateFlow<PdfPageManagerUiState> = _uiState.asStateFlow()

    fun selectPdf(uri: Uri) {
        val name = uri.lastPathSegment ?: "document.pdf"
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isProcessing = true,
                    statusMessage = "Loading page thumbnails...",
                    errorMessage = null,
                    result = null
                )
            }

            try {
                val items = engine.loadPageThumbnails(
                    uri = uri,
                    onProgress = { cur, tot ->
                        _uiState.update {
                            it.copy(
                                progressCurrent = cur,
                                progressTotal = tot,
                                statusMessage = "Rendering thumbnail $cur of $tot..."
                            )
                        }
                    }
                )

                _uiState.update {
                    it.copy(
                        selectedPdfUri = uri,
                        selectedPdfName = name,
                        pages = items,
                        selectedPageIndex = 0,
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

    fun selectPage(index: Int) {
        if (index in _uiState.value.pages.indices) {
            _uiState.update { it.copy(selectedPageIndex = index) }
        }
    }

    fun rotatePage(index: Int, degrees: Int = 90) {
        val list = _uiState.value.pages.toMutableList()
        if (index in list.indices) {
            val item = list[index]
            val newRot = (item.additionalRotation + degrees) % 360
            list[index] = item.copy(additionalRotation = newRot)
            _uiState.update { it.copy(pages = list) }
        }
    }

    fun rotateAllPages(degrees: Int = 90) {
        val updated = _uiState.value.pages.map { item ->
            item.copy(additionalRotation = (item.additionalRotation + degrees) % 360)
        }
        _uiState.update { it.copy(pages = updated) }
    }

    fun movePage(fromIndex: Int, toIndex: Int) {
        val list = _uiState.value.pages.toMutableList()
        if (fromIndex in list.indices && toIndex in list.indices) {
            val item = list.removeAt(fromIndex)
            list.add(toIndex, item)
            _uiState.update {
                it.copy(
                    pages = list,
                    selectedPageIndex = toIndex
                )
            }
        }
    }

    fun deletePage(index: Int) {
        val list = _uiState.value.pages.toMutableList()
        if (list.size > 1 && index in list.indices) {
            list.removeAt(index)
            val newSelected = (index).coerceAtMost(list.size - 1)
            _uiState.update {
                it.copy(
                    pages = list,
                    selectedPageIndex = newSelected
                )
            }
        } else if (list.size <= 1) {
            _uiState.update { it.copy(errorMessage = "Cannot delete the only page in the document.") }
        }
    }

    fun clear() {
        _uiState.value = PdfPageManagerUiState()
    }

    fun exportPdf(desiredName: String? = null) {
        val uri = _uiState.value.selectedPdfUri ?: return
        val pages = _uiState.value.pages
        if (pages.isEmpty()) return

        val baseName = desiredName ?: run {
            val orig = (_uiState.value.selectedPdfName ?: "document").substringBeforeLast(".")
            "${orig}_edited"
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isProcessing = true,
                    statusMessage = "Exporting reorganized PDF...",
                    errorMessage = null,
                    result = null
                )
            }

            try {
                val tempFile = engine.exportModifiedPdf(uri, pages)
                _uiState.update { it.copy(statusMessage = "Saving to Documents/PixelRox...") }
                val pubResult = publisher.publishPdf(tempFile, baseName)
                tempFile.delete()

                when (pubResult) {
                    is PdfPublishResult.Success -> {
                        _uiState.update {
                            it.copy(
                                isProcessing = false,
                                statusMessage = null,
                                result = pubResult
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
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        statusMessage = null,
                        errorMessage = e.message ?: "Failed to export PDF"
                    )
                }
            }
        }
    }
}
