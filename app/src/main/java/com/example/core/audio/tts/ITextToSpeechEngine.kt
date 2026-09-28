package com.example.core.audio.tts

import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.util.Locale

interface ITextToSpeechEngine {
    val engineState: StateFlow<TtsEngineState>
    val playbackStatus: StateFlow<TtsPlaybackStatus>

    fun initialize()

    fun speak(
        text: String,
        locale: Locale,
        voiceName: String? = null,
        speechRate: Float = 1.0f,
        pitch: Float = 1.0f
    ): Boolean

    fun stop()

    suspend fun synthesizeToFile(
        text: String,
        targetFile: File,
        locale: Locale,
        voiceName: String? = null,
        speechRate: Float = 1.0f,
        pitch: Float = 1.0f
    ): Result<File>

    fun shutdown()
}
