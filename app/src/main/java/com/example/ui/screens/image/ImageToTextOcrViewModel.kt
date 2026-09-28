package com.example.ui.screens.image

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.util.ImageTextRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class OcrUiState {
    object Idle : OcrUiState()
    object Loading : OcrUiState()
    data class Success(val originalText: String, val hasText: Boolean) : OcrUiState()
    data class Error(val message: String) : OcrUiState()
}

class ImageToTextOcrViewModel(private val recognizer: ImageTextRecognizer) : ViewModel() {
    private val _uiState = MutableStateFlow<OcrUiState>(OcrUiState.Idle)
    val uiState: StateFlow<OcrUiState> = _uiState.asStateFlow()

    private val _editableText = MutableStateFlow("")
    val editableText: StateFlow<String> = _editableText.asStateFlow()

    private val _selectedImageUri = MutableStateFlow<Uri?>(null)
    val selectedImageUri: StateFlow<Uri?> = _selectedImageUri.asStateFlow()

    fun processImage(uri: Uri) {
        _selectedImageUri.value = uri
        viewModelScope.launch {
            _uiState.value = OcrUiState.Loading
            try {
                val ocrResult = recognizer.recognizeText(uri)
                val text = ocrResult.text.trim()
                _editableText.value = text
                _uiState.value = OcrUiState.Success(
                    originalText = text,
                    hasText = text.isNotEmpty()
                )
            } catch (e: Exception) {
                _uiState.value = OcrUiState.Error(e.message ?: "Failed to recognize text from image.")
            }
        }
    }

    fun updateEditableText(newText: String) {
        _editableText.value = newText
    }

    fun reset() {
        _uiState.value = OcrUiState.Idle
        _editableText.value = ""
        _selectedImageUri.value = null
    }
}
