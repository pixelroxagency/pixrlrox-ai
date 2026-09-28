package com.example.core.voice

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

enum class VoiceState {
    IDLE,
    LISTENING,
    RECOGNIZING,
    SENDING,
    THINKING,
    SPEAKING,
    INTERRUPTED,
    ERROR
}

class VoiceManager(private val context: Context) : TextToSpeech.OnInitListener {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var isTtsInitialized = false
    private var audioFocusRequest: AudioFocusRequest? = null

    private val _voiceState = MutableStateFlow(VoiceState.IDLE)
    val voiceState: StateFlow<VoiceState> = _voiceState.asStateFlow()

    private val _rmsAudioLevel = MutableStateFlow(0f)
    val rmsAudioLevel: StateFlow<Float> = _rmsAudioLevel.asStateFlow()

    private val _transcription = MutableStateFlow("")
    val transcription: StateFlow<String> = _transcription.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var currentLanguageTag = "bn-BD" // default Bangla, toggleable to "en-US"
    private val _currentLanguage = MutableStateFlow(currentLanguageTag)
    val currentLanguage: StateFlow<String> = _currentLanguage.asStateFlow()

    init {
        textToSpeech = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isTtsInitialized = true
            setTtsLanguage(currentLanguageTag)
            textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    _voiceState.value = VoiceState.SPEAKING
                }

                override fun onDone(utteranceId: String?) {
                    abandonAudioFocus()
                    _voiceState.value = VoiceState.IDLE
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    abandonAudioFocus()
                    _voiceState.value = VoiceState.IDLE
                }
            })
        }
    }

    fun setLanguage(languageTag: String) {
        currentLanguageTag = languageTag
        _currentLanguage.value = languageTag
        if (isTtsInitialized) {
            setTtsLanguage(languageTag)
        }
    }

    fun getLanguage(): String = currentLanguageTag

    fun speak(text: String) = speakResponse(text)

    fun startListening() = startListening {}

    private fun setTtsLanguage(languageTag: String) {
        val locale = when (languageTag) {
            "bn-BD", "bn" -> Locale("bn", "BD")
            else -> Locale.US
        }
        val result = textToSpeech?.setLanguage(locale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            // Fallback to default locale if language pack not installed
            textToSpeech?.setLanguage(Locale.getDefault())
        }
    }

    private fun requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val playbackAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(playbackAttributes)
                .build()
            audioFocusRequest = focusRequest
            audioManager?.requestAudioFocus(focusRequest)
        } else {
            @Suppress("DEPRECATION")
            audioManager?.requestAudioFocus(
                null,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            )
        }
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
            audioFocusRequest = null
        } else {
            @Suppress("DEPRECATION")
            audioManager?.abandonAudioFocus(null)
        }
    }

    /**
     * Natural Barge-In:
     * Immediately stops TTS, releases audio focus, discards remaining playback,
     * transitions to INTERRUPTED, and begins listening for user speech without delay.
     */
    fun interruptAndListen(onResultTranscription: (String) -> Unit) {
        stopSpeaking()
        _voiceState.value = VoiceState.INTERRUPTED
        startListening(onResultTranscription)
    }

    fun startListening(
        onResultTranscription: (String) -> Unit
    ) {
        // Stop any audio playback BEFORE enabling mic so speaker audio is never picked up
        stopSpeaking()
        _errorMessage.value = null
        _transcription.value = ""

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            _voiceState.value = VoiceState.ERROR
            _errorMessage.value = "Speech recognition is not available on this device"
            return
        }

        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    _voiceState.value = VoiceState.LISTENING
                }

                override fun onBeginningOfSpeech() {
                    _voiceState.value = VoiceState.LISTENING
                }

                override fun onRmsChanged(rmsdB: Float) {
                    _rmsAudioLevel.value = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                }

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    _voiceState.value = VoiceState.RECOGNIZING
                    _rmsAudioLevel.value = 0f
                }

                override fun onError(error: Int) {
                    _voiceState.value = VoiceState.IDLE
                    _rmsAudioLevel.value = 0f
                    val errText = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech input timed out"
                        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission needed"
                        else -> "Speech recognition error ($error)"
                    }
                    _errorMessage.value = errText
                }

                override fun onResults(results: Bundle?) {
                    _voiceState.value = VoiceState.IDLE
                    _rmsAudioLevel.value = 0f
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull()?.trim() ?: ""
                    if (text.isNotBlank()) {
                        _transcription.value = text
                        // Send ONLY the exact spoken transcription
                        onResultTranscription(text)
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val partials = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    partials?.firstOrNull()?.let {
                        _transcription.value = it
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, currentLanguageTag)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, currentLanguageTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        try {
            speechRecognizer?.startListening(intent)
            _voiceState.value = VoiceState.LISTENING
        } catch (e: Exception) {
            _voiceState.value = VoiceState.ERROR
            _errorMessage.value = "Failed to start listening: ${e.message}"
        }
    }

    /**
     * Listens for composer speech-to-text with real-time partial text updates.
     */
    fun startListeningForComposer(
        onPartial: (String) -> Unit,
        onComplete: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        stopSpeaking()
        _errorMessage.value = null
        _transcription.value = ""

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onError("Speech recognition not available")
            return
        }

        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    _voiceState.value = VoiceState.LISTENING
                }

                override fun onBeginningOfSpeech() {
                    _voiceState.value = VoiceState.LISTENING
                }

                override fun onRmsChanged(rmsdB: Float) {
                    _rmsAudioLevel.value = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                }

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    _voiceState.value = VoiceState.RECOGNIZING
                    _rmsAudioLevel.value = 0f
                }

                override fun onError(error: Int) {
                    _voiceState.value = VoiceState.IDLE
                    _rmsAudioLevel.value = 0f
                    val errText = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timed out"
                        SpeechRecognizer.ERROR_AUDIO -> "Audio recording issue"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required"
                        else -> "Speech error ($error)"
                    }
                    onError(errText)
                }

                override fun onResults(results: Bundle?) {
                    _voiceState.value = VoiceState.IDLE
                    _rmsAudioLevel.value = 0f
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull()?.trim() ?: ""
                    if (text.isNotBlank()) {
                        onComplete(text)
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val partials = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    partials?.firstOrNull()?.let {
                        onPartial(it)
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, currentLanguageTag)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, currentLanguageTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        try {
            speechRecognizer?.startListening(intent)
            _voiceState.value = VoiceState.LISTENING
        } catch (e: Exception) {
            _voiceState.value = VoiceState.ERROR
            onError("Failed to listen: ${e.message}")
        }
    }

    fun stopListening() {
        try {
            speechRecognizer?.stopListening()
        } catch (_: Exception) {}
        _voiceState.value = VoiceState.IDLE
        _rmsAudioLevel.value = 0f
    }

    /**
     * CRITICAL:
     * Speaks ONLY the final user-facing response from PixelRox AI.
     * Never speaks "I heard your voice", "Listening", "Thinking", or acknowledgements.
     */
    fun speakResponse(text: String) {
        if (!isTtsInitialized || text.isBlank()) return
        stopSpeaking()

        // Strip markdown code blocks, URLs, and bold/italic asterisks for clean speech
        val cleanSpeech = text
            .replace(Regex("```[\\s\\S]*?```"), "Code omitted.")
            .replace(Regex("`[^`]*`"), "")
            .replace(Regex("\\[([^\\]]+)\\]\\([^)]+\\)"), "$1")
            .replace(Regex("[*#_>~]"), "")
            .trim()

        if (cleanSpeech.isNotBlank()) {
            requestAudioFocus()
            _voiceState.value = VoiceState.SPEAKING
            textToSpeech?.speak(cleanSpeech, TextToSpeech.QUEUE_FLUSH, null, "hermes_response_${System.currentTimeMillis()}")
        }
    }

    fun stopSpeaking() {
        if (isTtsInitialized) {
            textToSpeech?.stop()
        }
        abandonAudioFocus()
        if (_voiceState.value == VoiceState.SPEAKING) {
            _voiceState.value = VoiceState.IDLE
        }
    }

    fun setSending() {
        _voiceState.value = VoiceState.SENDING
    }

    fun setThinking() {
        _voiceState.value = VoiceState.THINKING
    }

    fun setIdle() {
        _voiceState.value = VoiceState.IDLE
    }

    fun release() {
        try {
            speechRecognizer?.destroy()
            speechRecognizer = null
            stopSpeaking()
            textToSpeech?.shutdown()
            textToSpeech = null
        } catch (_: Exception) {}
    }
}
