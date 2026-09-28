package com.example.ui.screens.files

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.file.ExtractedFileContent
import com.example.core.file.FileContentExtractor
import com.example.core.network.ChatCompletionRequest
import com.example.core.network.ChatMessageDto
import com.example.data.repository.DirectAiRepository
import com.example.data.repository.NoteRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FileAssistantUiState(
    val extractedContent: ExtractedFileContent? = null,
    val selectedAction: String = "Summarize",
    val customPrompt: String = "",
    val showConsentDialog: Boolean = false,
    val isAnalyzing: Boolean = false,
    val analysisResult: String? = null,
    val errorMessage: String? = null,
    val noteSavedSuccess: Boolean = false
)

class FileAssistantViewModel(
    private val directAiRepository: DirectAiRepository,
    private val noteRepository: NoteRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(FileAssistantUiState())
    val uiState: StateFlow<FileAssistantUiState> = _uiState.asStateFlow()

    fun onFileSelected(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                val extracted = FileContentExtractor.extractContent(context, uri)
                _uiState.update {
                    it.copy(
                        extractedContent = extracted,
                        analysisResult = null,
                        errorMessage = null,
                        noteSavedSuccess = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Failed to extract file content: ${e.localizedMessage}") }
            }
        }
    }

    fun selectAction(action: String) {
        _uiState.update { it.copy(selectedAction = action) }
    }

    fun updateCustomPrompt(prompt: String) {
        _uiState.update { it.copy(customPrompt = prompt) }
    }

    fun requestAnalysisConsent() {
        if (_uiState.value.extractedContent == null) return
        _uiState.update { it.copy(showConsentDialog = true) }
    }

    fun dismissConsentDialog() {
        _uiState.update { it.copy(showConsentDialog = false) }
    }

    fun confirmAndAnalyze() {
        val state = _uiState.value
        val extracted = state.extractedContent ?: return
        _uiState.update { it.copy(showConsentDialog = false, isAnalyzing = true, errorMessage = null, analysisResult = "") }

        viewModelScope.launch {
            try {
                if (!directAiRepository.isCurrentProviderConfigured()) {
                    _uiState.update { it.copy(isAnalyzing = false, errorMessage = "Direct AI is not configured. Please add an API key in Settings.") }
                    return@launch
                }
                val provider = directAiRepository.currentProvider.value
                val actionPrompt = when (state.selectedAction) {
                    "Summarize" -> "Summarize the following document concisely with high-level takeaways:"
                    "Ask Questions" -> "Analyze the following document and suggest 5 insightful questions based on its content:"
                    "Explain" -> "Explain the key concepts contained in this document in clear, plain language:"
                    "Extract Key Points" -> "Extract bulleted key points from this document:"
                    "Extract Action Items" -> "Extract all actionable tasks or action items from this document as bullet points:"
                    "Translate" -> "Translate the main contents of this document into English:"
                    "Custom Prompt" -> state.customPrompt.ifBlank { "Analyze this document:" }
                    else -> "Analyze the following file:"
                }

                val fullPrompt = "$actionPrompt\n\nFilename: ${extracted.fileName}\nSize: ${extracted.fileSize} bytes\n\nDocument Content:\n${extracted.fullText}"

                val request = ChatCompletionRequest(
                    model = provider?.selectedModel ?: "gpt-4o-mini",
                    messages = listOf(ChatMessageDto(role = "user", content = fullPrompt)),
                    stream = true
                )

                val sb = StringBuilder()
                directAiRepository.streamDirectAiChat(request).collect { chunk ->
                    sb.append(chunk)
                    _uiState.update { current -> current.copy(analysisResult = sb.toString()) }
                }

                if (sb.isEmpty()) {
                    _uiState.update { it.copy(isAnalyzing = false, errorMessage = "No response received from Direct AI.") }
                } else {
                    _uiState.update { it.copy(isAnalyzing = false) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isAnalyzing = false, errorMessage = "Analysis failed: ${e.localizedMessage}") }
            }
        }
    }

    fun clearSelectedFile() {
        _uiState.update { FileAssistantUiState() }
    }

    fun saveResultAsNote(title: String = "Document Analysis") {
        val result = _uiState.value.analysisResult ?: return
        viewModelScope.launch {
            try {
                noteRepository.saveNote(
                    title = title.ifBlank { "Document Analysis" },
                    content = result
                )
                _uiState.update { it.copy(noteSavedSuccess = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Failed to save note: ${e.localizedMessage}") }
            }
        }
    }
}
