package com.example.core.audio.tts

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.util.Locale

class AndroidTextToSpeechEngine(
    context: Context
) : ITextToSpeechEngine, TextToSpeech.OnInitListener {

    private val appContext: Context = context.applicationContext
    private var tts: TextToSpeech? = null

    private val _engineState = MutableStateFlow<TtsEngineState>(TtsEngineState.Uninitialized)
    override val engineState: StateFlow<TtsEngineState> = _engineState.asStateFlow()

    private val _playbackStatus = MutableStateFlow<TtsPlaybackStatus>(TtsPlaybackStatus.Idle)
    override val playbackStatus: StateFlow<TtsPlaybackStatus> = _playbackStatus.asStateFlow()

    @Volatile
    private var pendingSynthesisDeferred: CompletableDeferred<File>? = null
    @Volatile
    private var pendingSynthesisFile: File? = null
    @Volatile
    private var pendingSynthesisUtteranceId: String? = null

    override fun initialize() {
        if (_engineState.value is TtsEngineState.Ready || _engineState.value is TtsEngineState.Initializing) {
            return
        }
        _engineState.value = TtsEngineState.Initializing
        try {
            tts = TextToSpeech(appContext, this)
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Android TextToSpeech", e)
            _engineState.value = TtsEngineState.Error("Failed to instantiate TextToSpeech: ${e.message}")
        }
    }

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            _engineState.value = TtsEngineState.Error("TTS initialization failed with code $status")
            return
        }

        val engine = tts
        if (engine == null) {
            _engineState.value = TtsEngineState.Error("TTS instance is null after initialization")
            return
        }

        setupUtteranceListener(engine)
        discoverCapabilities(engine)
    }

    private fun setupUtteranceListener(engine: TextToSpeech) {
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String) {
                if (utteranceId.startsWith("speak_")) {
                    _playbackStatus.value = TtsPlaybackStatus.Speaking(utteranceId)
                }
            }

            override fun onDone(utteranceId: String) {
                if (utteranceId.startsWith("speak_")) {
                    _playbackStatus.value = TtsPlaybackStatus.Completed
                } else if (utteranceId == pendingSynthesisUtteranceId) {
                    val file = pendingSynthesisFile
                    if (file != null && file.exists() && file.length() > 0) {
                        pendingSynthesisDeferred?.complete(file)
                    } else {
                        pendingSynthesisDeferred?.completeExceptionally(
                            IllegalStateException("Synthesized audio file is missing or zero bytes")
                        )
                    }
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String) {
                handleTtsError(utteranceId, "Audio playback error occurred")
            }

            override fun onError(utteranceId: String, errorCode: Int) {
                val detail = when (errorCode) {
                    TextToSpeech.ERROR_SYNTHESIS -> "Synthesis engine failure"
                    TextToSpeech.ERROR_SERVICE -> "TTS service connection error"
                    TextToSpeech.ERROR_OUTPUT -> "Audio output device error"
                    TextToSpeech.ERROR_NETWORK -> "Network connection required for this voice"
                    TextToSpeech.ERROR_NETWORK_TIMEOUT -> "Network timed out"
                    TextToSpeech.ERROR_INVALID_REQUEST -> "Invalid speech request parameters"
                    TextToSpeech.ERROR_NOT_INSTALLED_YET -> "Voice dataset not installed on device"
                    else -> "TTS error code $errorCode"
                }
                handleTtsError(utteranceId, detail)
            }

            override fun onStop(utteranceId: String, interrupted: Boolean) {
                if (utteranceId.startsWith("speak_")) {
                    _playbackStatus.value = TtsPlaybackStatus.Stopped
                }
            }
        })
    }

    private fun handleTtsError(utteranceId: String, errorMsg: String) {
        if (utteranceId.startsWith("speak_")) {
            _playbackStatus.value = TtsPlaybackStatus.Error(errorMsg)
        } else if (utteranceId == pendingSynthesisUtteranceId) {
            pendingSynthesisDeferred?.completeExceptionally(IllegalStateException(errorMsg))
        }
    }

    private fun discoverCapabilities(engine: TextToSpeech) {
        try {
            // 1. Discover Languages
            val discoveredLocales = try {
                engine.availableLanguages ?: emptySet()
            } catch (e: Exception) {
                Log.w(TAG, "availableLanguages call failed, falling back to locale scan", e)
                emptySet()
            }

            val allLocales = if (discoveredLocales.isNotEmpty()) {
                discoveredLocales.filterNotNull().toSet()
            } else {
                try {
                    Locale.getAvailableLocales().filterNotNull().filter { loc ->
                        try {
                            engine.isLanguageAvailable(loc) >= TextToSpeech.LANG_AVAILABLE
                        } catch (_: Exception) {
                            false
                        }
                    }.toSet()
                } catch (e: Exception) {
                    emptySet()
                }
            }

            val languages = allLocales.mapNotNull { loc ->
                try {
                    val displayName = loc.getDisplayName(Locale.getDefault()).ifBlank { loc.displayName ?: "" }
                    if (displayName.isNotBlank()) {
                        TtsLanguage(
                            locale = loc,
                            displayName = displayName.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() },
                            languageTag = loc.toLanguageTag()
                        )
                    } else null
                } catch (_: Exception) {
                    null
                }
            }.sortedBy { it.displayName }

            // 2. Discover Voices
            val voices = mutableListOf<TtsVoice>()
            try {
                val deviceVoices = engine.voices
                if (deviceVoices != null) {
                    for (v in deviceVoices) {
                        if (v != null && !v.name.isNullOrBlank() && v.locale != null) {
                            val label = formatVoiceDisplayName(v.name, v.locale)
                            voices.add(
                                TtsVoice(
                                    name = v.name,
                                    locale = v.locale,
                                    displayName = label,
                                    quality = v.quality,
                                    isNetworkConnectionRequired = v.isNetworkConnectionRequired
                                )
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "voices discovery failed", e)
            }
            voices.sortBy { it.displayName }

            _engineState.value = TtsEngineState.Ready(
                availableLanguages = languages,
                availableVoices = voices
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed during discoverCapabilities", e)
            _engineState.value = TtsEngineState.Error("TTS initialization capability scan error: ${e.message}")
        }
    }

    private fun applyVoiceOrLanguage(engine: TextToSpeech, locale: Locale, voiceName: String?) {
        if (!voiceName.isNullOrBlank()) {
            try {
                val match = engine.voices?.firstOrNull { it.name == voiceName }
                if (match != null) {
                    engine.voice = match
                    return
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to set voice $voiceName: ${e.message}")
            }
        }
        try {
            engine.language = locale
        } catch (e: Exception) {
            Log.w(TAG, "Failed to set language $locale: ${e.message}")
        }
    }

    override fun speak(
        text: String,
        locale: Locale,
        voiceName: String?,
        speechRate: Float,
        pitch: Float
    ): Boolean {
        val engine = tts ?: return false
        if (text.isBlank()) return false

        applyVoiceOrLanguage(engine, locale, voiceName)
        engine.setSpeechRate(speechRate.coerceIn(0.5f, 2.0f))
        engine.setPitch(pitch.coerceIn(0.5f, 2.0f))

        val utteranceId = "speak_${System.currentTimeMillis()}"
        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
        }

        // Stop any previous speech before starting
        engine.stop()

        val maxLen = TextToSpeech.getMaxSpeechInputLength().coerceAtLeast(1000)
        return if (text.length <= maxLen) {
            val result = engine.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
            result == TextToSpeech.SUCCESS
        } else {
            val chunks = chunkText(text, maxLen)
            var allSucceeded = true
            for (i in chunks.indices) {
                val chunkUtteranceId = if (i == chunks.lastIndex) utteranceId else "speak_sub_${i}_${System.currentTimeMillis()}"
                val chunkParams = Bundle().apply {
                    putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, chunkUtteranceId)
                }
                val mode = if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
                val res = engine.speak(chunks[i], mode, chunkParams, chunkUtteranceId)
                if (res != TextToSpeech.SUCCESS) allSucceeded = false
            }
            allSucceeded
        }
    }

    override fun stop() {
        tts?.stop()
        _playbackStatus.value = TtsPlaybackStatus.Stopped
    }

    override suspend fun synthesizeToFile(
        text: String,
        targetFile: File,
        locale: Locale,
        voiceName: String?,
        speechRate: Float,
        pitch: Float
    ): Result<File> = withContext(Dispatchers.IO) {
        val engine = tts ?: return@withContext Result.failure(IllegalStateException("TTS engine is not initialized"))
        if (text.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Text cannot be empty"))
        }

        applyVoiceOrLanguage(engine, locale, voiceName)
        engine.setSpeechRate(speechRate.coerceIn(0.5f, 2.0f))
        engine.setPitch(pitch.coerceIn(0.5f, 2.0f))

        if (targetFile.exists()) {
            targetFile.delete()
        }
        targetFile.parentFile?.mkdirs()

        val synthUtteranceId = "synth_${System.currentTimeMillis()}"
        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, synthUtteranceId)
        }

        val deferred = CompletableDeferred<File>()
        pendingSynthesisDeferred = deferred
        pendingSynthesisFile = targetFile
        pendingSynthesisUtteranceId = synthUtteranceId

        val maxLen = TextToSpeech.getMaxSpeechInputLength().coerceAtLeast(1000)
        val textToSynthesize = if (text.length > maxLen) text.take(maxLen) else text

        val invokeResult = engine.synthesizeToFile(textToSynthesize, params, targetFile, synthUtteranceId)
        if (invokeResult != TextToSpeech.SUCCESS) {
            pendingSynthesisDeferred = null
            pendingSynthesisFile = null
            pendingSynthesisUtteranceId = null
            return@withContext Result.failure(IllegalStateException("synthesizeToFile failed to start (code $invokeResult)"))
        }

        try {
            val file = withTimeout(15000) {
                deferred.await()
            }
            Result.success(file)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            pendingSynthesisDeferred = null
            pendingSynthesisFile = null
            pendingSynthesisUtteranceId = null
        }
    }

    override fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {
            Log.w(TAG, "Error shutting down TTS: ${e.message}")
        }
        tts = null
        _engineState.value = TtsEngineState.Uninitialized
        _playbackStatus.value = TtsPlaybackStatus.Idle
    }

    companion object {
        private const val TAG = "PixelRoxTTS"

        fun formatVoiceDisplayName(voiceName: String?, locale: Locale?): String {
            val safeName = voiceName ?: "Voice"
            val cleanName = safeName
                .substringAfterLast("x-")
                .replace("#", " ")
                .replace("_", " ")
                .replace("-", " ")
                .trim()
            val locName = locale?.getDisplayLanguage(Locale.getDefault())?.ifBlank { locale.language ?: "" } ?: "Unknown"
            return if (cleanName.isNotBlank() && cleanName != safeName) {
                "$locName: $cleanName"
            } else {
                "$locName ($safeName)"
            }
        }

        fun chunkText(text: String, maxChunkSize: Int): List<String> {
            if (text.length <= maxChunkSize) return listOf(text)
            val chunks = mutableListOf<String>()
            var remaining = text.trim()
            while (remaining.isNotEmpty()) {
                if (remaining.length <= maxChunkSize) {
                    chunks.add(remaining)
                    break
                }
                val candidate = remaining.take(maxChunkSize)
                val breakIdx = candidate.lastIndexOfAny(charArrayOf('.', '!', '?', '\n', ';'))
                val splitAt = if (breakIdx > maxChunkSize / 2) {
                    breakIdx + 1
                } else {
                    val spaceIdx = candidate.lastIndexOf(' ')
                    if (spaceIdx > maxChunkSize / 3) spaceIdx + 1 else maxChunkSize
                }
                val segment = remaining.take(splitAt).trim()
                if (segment.isNotEmpty()) {
                    chunks.add(segment)
                }
                remaining = remaining.substring(splitAt).trim()
            }
            return chunks.ifEmpty { listOf(text) }
        }
    }
}
