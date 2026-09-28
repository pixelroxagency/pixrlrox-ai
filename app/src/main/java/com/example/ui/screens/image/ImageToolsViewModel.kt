package com.example.ui.screens.image

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.DirectAiRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ImageToolsUiState(
    val selectedImageUri: Uri? = null,
    val selectedPreset: String = "Enhance",
    val preserveFacialIdentity: Boolean = true,
    val customPrompt: String = "",
    val isCheckingCapabilities: Boolean = false,
    val isSupportedByBackend: Boolean = true,
    val capabilityNotice: String? = null,
    val isProcessing: Boolean = false,
    val generatedImageUri: String? = null,
    val errorMessage: String? = null
)

class ImageToolsViewModel(
    private val directAiRepository: DirectAiRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ImageToolsUiState())
    val uiState: StateFlow<ImageToolsUiState> = _uiState.asStateFlow()

    fun setImageUri(uri: Uri?) {
        _uiState.update { it.copy(selectedImageUri = uri, generatedImageUri = null, errorMessage = null) }
    }

    fun selectPreset(preset: String) {
        _uiState.update { it.copy(selectedPreset = preset) }
    }

    fun togglePreserveFacialIdentity(preserve: Boolean) {
        _uiState.update { it.copy(preserveFacialIdentity = preserve) }
    }

    fun updateCustomPrompt(prompt: String) {
        _uiState.update { it.copy(customPrompt = prompt) }
    }

    fun processImageAction() {
        val state = _uiState.value
        _uiState.update { it.copy(isProcessing = true, errorMessage = null) }

        viewModelScope.launch {
            val identityInstruction = if (state.preserveFacialIdentity) {
                "\n[Preserve Facial Identity: Ensure facial structure, key identity features, proportions, skin texture, and original expression are strictly preserved.]"
            } else ""

            val prompt = when (state.selectedPreset) {
                "Enhance" -> "Enhance lighting, contrast, clarity, and resolution of this image.$identityInstruction"
                "Clean Background" -> "Remove studio clutter and replace with a clean, soft neutral studio background.$identityInstruction"
                "Portrait" -> "Apply professional portrait retouching and studio lighting style.$identityInstruction"
                "Product Photo" -> "Clean product background, enhance reflections, and balance color for professional showcase."
                "Custom Prompt" -> state.customPrompt + identityInstruction
                else -> state.selectedPreset + identityInstruction
            }

            _uiState.update {
                it.copy(
                    isProcessing = false,
                    errorMessage = "Image processing prompt prepared: '$prompt'."
                )
            }
        }
    }
}
