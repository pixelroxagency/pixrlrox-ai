package com.example.ui.screens.pdf

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.pdf.PdfMetadataEngine
import com.example.core.pdf.PdfMetadataInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PdfMetadataUiState(
    val selectedPdfUri: Uri? = null,
    val isInspecting: Boolean = false,
    val metadata: PdfMetadataInfo? = null,
    val errorMessage: String? = null
)

class PdfMetadataViewModel(application: Application) : AndroidViewModel(application) {

    private val engine = PdfMetadataEngine(application)

    private val _uiState = MutableStateFlow(PdfMetadataUiState())
    val uiState: StateFlow<PdfMetadataUiState> = _uiState.asStateFlow()

    fun inspectPdf(uri: Uri) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    selectedPdfUri = uri,
                    isInspecting = true,
                    metadata = null,
                    errorMessage = null
                )
            }

            try {
                val info = engine.extractMetadata(uri)
                _uiState.update {
                    it.copy(
                        isInspecting = false,
                        metadata = info
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isInspecting = false,
                        errorMessage = "Failed to inspect metadata: ${e.message}"
                    )
                }
            }
        }
    }

    fun clear() {
        _uiState.value = PdfMetadataUiState()
    }
}
