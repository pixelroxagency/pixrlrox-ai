package com.example.ui.screens.pdf

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.pdf.PdfMergeEngine
import com.example.core.pdf.PdfMergeInputItem
import com.example.core.pdf.PdfOutputPublisher
import com.example.core.pdf.PdfPublishResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PdfMergeUiState(
    val selectedFiles: List<PdfMergeInputItem> = emptyList(),
    val isProcessing: Boolean = false,
    val progressCurrent: Int = 0,
    val progressTotal: Int = 0,
    val statusMessage: String? = null,
    val result: PdfPublishResult.Success? = null,
    val errorMessage: String? = null
)

class PdfMergeViewModel(application: Application) : AndroidViewModel(application) {

    private val engine = PdfMergeEngine(application)
    private val publisher = PdfOutputPublisher(application)

    private val _uiState = MutableStateFlow(PdfMergeUiState())
    val uiState: StateFlow<PdfMergeUiState> = _uiState.asStateFlow()

    fun addPdfs(uris: List<Uri>) {
        viewModelScope.launch {
            val currentList = _uiState.value.selectedFiles.toMutableList()
            for (uri in uris) {
                val displayName = uri.lastPathSegment ?: "document_${currentList.size + 1}.pdf"
                try {
                    val item = engine.inspectPdf(uri, displayName)
                    currentList.add(item)
                } catch (e: Exception) {
                    _uiState.update { it.copy(errorMessage = "Could not load ${displayName}: ${e.message}") }
                }
            }
            _uiState.update {
                it.copy(
                    selectedFiles = currentList,
                    result = null
                )
            }
        }
    }

    fun removePdf(index: Int) {
        if (index in _uiState.value.selectedFiles.indices) {
            val updated = _uiState.value.selectedFiles.toMutableList().apply { removeAt(index) }
            _uiState.update { it.copy(selectedFiles = updated) }
        }
    }

    fun movePdf(fromIndex: Int, toIndex: Int) {
        val list = _uiState.value.selectedFiles.toMutableList()
        if (fromIndex in list.indices && toIndex in list.indices) {
            val item = list.removeAt(fromIndex)
            list.add(toIndex, item)
            _uiState.update { it.copy(selectedFiles = list) }
        }
    }

    fun clear() {
        _uiState.value = PdfMergeUiState()
    }

    fun mergePdfs(desiredName: String = "Merged_Document") {
        val files = _uiState.value.selectedFiles
        if (files.size < 2) {
            _uiState.update { it.copy(errorMessage = "Please select at least 2 PDF files to merge.") }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isProcessing = true,
                    progressCurrent = 0,
                    progressTotal = files.size,
                    statusMessage = "Merging ${files.size} PDF files...",
                    errorMessage = null,
                    result = null
                )
            }

            try {
                val mergedFile = engine.mergePdfs(
                    items = files,
                    onProgress = { cur, total ->
                        _uiState.update { it.copy(progressCurrent = cur, progressTotal = total) }
                    }
                )

                _uiState.update { it.copy(statusMessage = "Saving to Documents/PixelRox...") }
                val pubResult = publisher.publishPdf(mergedFile, desiredName)
                mergedFile.delete()

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
                        errorMessage = e.message ?: "Failed to merge PDF files"
                    )
                }
            }
        }
    }
}
