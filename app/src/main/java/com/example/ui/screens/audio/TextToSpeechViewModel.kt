package com.example.ui.screens.audio

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.audio.AudioOutputPublisher
import com.example.core.audio.AudioPublishResult
import com.example.core.audio.tts.AndroidTextToSpeechEngine
import com.example.core.audio.tts.ITextToSpeechEngine
import com.example.core.audio.tts.TtsEngineState
import com.example.core.audio.tts.TtsLanguage
import com.example.core.audio.tts.TtsPlaybackStatus
import com.example.core.audio.tts.TtsVoice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class TtsExportSuccess(
    val contentUri: Uri,
    val displayName: String,
    val fileSizeBytes: Long,
    val mimeType: String,
    val textSnippet: String
)

data class TextToSpeechUiState(
    val text: String = "Welcome to PixelRox. Convert any text into realistic spoken voice audio files.",
    val selectedLanguage: TtsLanguage? = null,
    val selectedVoice: TtsVoice? = null,
    val availableLanguages: List<TtsLanguage> = emptyList(),
    val availableVoices: List<TtsVoice> = emptyList(),
    val filteredVoices: List<TtsVoice> = emptyList(),
    val speechRate: Float = 1.0f,
    val pitch: Float = 1.0f,
    val engineState: TtsEngineState = TtsEngineState.Initializing,
    val playbackStatus: TtsPlaybackStatus = TtsPlaybackStatus.Idle,
    val isSynthesizing: Boolean = false,
    val exportSuccess: TtsExportSuccess? = null,
    val errorMessage: String? = null,
    val infoMessage: String? = null
)

class TextToSpeechViewModel(
    application: Application,
    private val ttsEngine: ITextToSpeechEngine = AndroidTextToSpeechEngine(application),
    private val audioPublisher: AudioOutputPublisher = AudioOutputPublisher(application)
) : AndroidViewModel(application) {

    constructor(application: Application) : this(
        application = application,
        ttsEngine = AndroidTextToSpeechEngine(application),
        audioPublisher = AudioOutputPublisher(application)
    )

    constructor(application: Application, ttsEngine: ITextToSpeechEngine) : this(
        application = application,
        ttsEngine = ttsEngine,
        audioPublisher = AudioOutputPublisher(application)
    )

    private val _uiState = MutableStateFlow(TextToSpeechUiState())
    val uiState: StateFlow<TextToSpeechUiState> = _uiState.asStateFlow()

    init {
        observeEngine()
        ttsEngine.initialize()
    }

    private fun observeEngine() {
        viewModelScope.launch {
            ttsEngine.engineState.collect { state ->
                _uiState.update { current ->
                    if (state is TtsEngineState.Ready) {
                        val defaultLang = state.availableLanguages.firstOrNull {
                            it.locale.language == Locale.getDefault().language
                        } ?: state.availableLanguages.firstOrNull {
                            it.locale.language == Locale.ENGLISH.language
                        } ?: state.availableLanguages.firstOrNull()

                        val voicesForLang = if (defaultLang != null) {
                            state.availableVoices.filter { it.locale.language == defaultLang.locale.language }
                        } else {
                            state.availableVoices
                        }

                        current.copy(
                            engineState = state,
                            availableLanguages = state.availableLanguages,
                            availableVoices = state.availableVoices,
                            selectedLanguage = current.selectedLanguage ?: defaultLang,
                            filteredVoices = voicesForLang,
                            selectedVoice = current.selectedVoice ?: voicesForLang.firstOrNull()
                        )
                    } else {
                        current.copy(engineState = state)
                    }
                }
            }
        }

        viewModelScope.launch {
            ttsEngine.playbackStatus.collect { status ->
                _uiState.update { it.copy(playbackStatus = status) }
            }
        }
    }

    fun onTextChanged(newText: String) {
        val limited = if (newText.length > MAX_INPUT_CHARS) {
            newText.take(MAX_INPUT_CHARS)
        } else {
            newText
        }
        _uiState.update {
            it.copy(
                text = limited,
                errorMessage = null
            )
        }
    }

    fun clearText() {
        _uiState.update { it.copy(text = "", errorMessage = null) }
    }

    fun setSpeechRate(rate: Float) {
        val clamped = rate.coerceIn(MIN_SPEECH_RATE, MAX_SPEECH_RATE)
        _uiState.update { it.copy(speechRate = clamped) }
    }

    fun setPitch(pitch: Float) {
        val clamped = pitch.coerceIn(MIN_PITCH, MAX_PITCH)
        _uiState.update { it.copy(pitch = clamped) }
    }

    fun selectLanguage(language: TtsLanguage) {
        _uiState.update { current ->
            val matchingVoices = current.availableVoices.filter {
                it.locale.language == language.locale.language
            }
            current.copy(
                selectedLanguage = language,
                filteredVoices = matchingVoices,
                selectedVoice = matchingVoices.firstOrNull()
            )
        }
    }

    fun selectVoice(voice: TtsVoice?) {
        _uiState.update { it.copy(selectedVoice = voice) }
    }

    fun speak() {
        val state = _uiState.value
        val trimmed = state.text.trim()
        if (trimmed.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Please enter some text to speak") }
            return
        }

        val locale = state.selectedLanguage?.locale ?: Locale.getDefault()
        val voiceName = state.selectedVoice?.name
        val success = ttsEngine.speak(
            text = trimmed,
            locale = locale,
            voiceName = voiceName,
            speechRate = state.speechRate,
            pitch = state.pitch
        )
        if (!success) {
            _uiState.update { it.copy(errorMessage = "Speech output could not be started by the TTS engine") }
        }
    }

    fun stopSpeaking() {
        ttsEngine.stop()
    }

    fun speakAgain() {
        speak()
    }

    fun copyTextToClipboard() {
        val textToCopy = _uiState.value.text
        if (textToCopy.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "No text to copy") }
            return
        }
        try {
            val clipboard = getApplication<Application>().getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clip = ClipData.newPlainText("PixelRox Text to Speech", textToCopy)
            clipboard?.setPrimaryClip(clip)
            _uiState.update { it.copy(infoMessage = "Text copied to clipboard") }
        } catch (e: Exception) {
            _uiState.update { it.copy(errorMessage = "Failed to copy text: ${e.message}") }
        }
    }

    fun synthesizeAndSaveAudio() {
        val state = _uiState.value
        val trimmed = state.text.trim()
        if (trimmed.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Please enter some text to synthesize audio") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSynthesizing = true, errorMessage = null) }

            val tempFile = File(
                getApplication<Application>().cacheDir,
                "tts_export_${System.currentTimeMillis()}.wav"
            )

            val locale = state.selectedLanguage?.locale ?: Locale.getDefault()
            val voiceName = state.selectedVoice?.name
            val synthResult = ttsEngine.synthesizeToFile(
                text = trimmed,
                targetFile = tempFile,
                locale = locale,
                voiceName = voiceName,
                speechRate = state.speechRate,
                pitch = state.pitch
            )

            synthResult.fold(
                onSuccess = { generatedFile ->
                    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                    val baseName = trimmed
                        .replace(Regex("[^a-zA-Z0-9_-]"), "_")
                        .take(20)
                        .ifBlank { "speech" }
                    val desiredDisplayName = "tts_${baseName}_$timestamp.wav"

                    val publishResult = audioPublisher.publishAudioFile(
                        sourceFile = generatedFile,
                        desiredDisplayName = desiredDisplayName,
                        mimeType = "audio/wav",
                        extension = ".wav"
                    )

                    when (publishResult) {
                        is AudioPublishResult.Success -> {
                            _uiState.update {
                                it.copy(
                                    isSynthesizing = false,
                                    exportSuccess = TtsExportSuccess(
                                        contentUri = publishResult.contentUri,
                                        displayName = publishResult.displayName,
                                        fileSizeBytes = publishResult.fileSizeBytes,
                                        mimeType = publishResult.mimeType,
                                        textSnippet = trimmed.take(80)
                                    )
                                )
                            }
                        }
                        is AudioPublishResult.Failure -> {
                            _uiState.update {
                                it.copy(
                                    isSynthesizing = false,
                                    errorMessage = "Failed to save audio to storage: ${publishResult.errorMessage}"
                                )
                            }
                        }
                    }
                    try { generatedFile.delete() } catch (_: Exception) {}
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isSynthesizing = false,
                            errorMessage = "Audio synthesis failed: ${error.message ?: "Unknown error"}"
                        )
                    }
                    try { tempFile.delete() } catch (_: Exception) {}
                }
            )
        }
    }

    fun dismissSuccess() {
        _uiState.update { it.copy(exportSuccess = null) }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun dismissInfo() {
        _uiState.update { it.copy(infoMessage = null) }
    }

    override fun onCleared() {
        super.onCleared()
        ttsEngine.stop()
        ttsEngine.shutdown()
    }

    companion object {
        const val MAX_INPUT_CHARS = 4000
        const val MIN_SPEECH_RATE = 0.5f
        const val MAX_SPEECH_RATE = 2.0f
        const val MIN_PITCH = 0.5f
        const val MAX_PITCH = 2.0f
    }
}
