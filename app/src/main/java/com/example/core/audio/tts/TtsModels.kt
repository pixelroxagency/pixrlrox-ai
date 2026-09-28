package com.example.core.audio.tts

import android.net.Uri
import java.io.File
import java.util.Locale

data class TtsLanguage(
    val locale: Locale,
    val displayName: String,
    val languageTag: String
)

data class TtsVoice(
    val name: String,
    val locale: Locale,
    val displayName: String,
    val quality: Int = 300,
    val isNetworkConnectionRequired: Boolean = false
)

sealed interface TtsEngineState {
    data object Uninitialized : TtsEngineState
    data object Initializing : TtsEngineState
    data class Ready(
        val availableLanguages: List<TtsLanguage>,
        val availableVoices: List<TtsVoice>
    ) : TtsEngineState
    data class Error(val message: String) : TtsEngineState
}

sealed interface TtsPlaybackStatus {
    data object Idle : TtsPlaybackStatus
    data class Speaking(val utteranceId: String) : TtsPlaybackStatus
    data object Completed : TtsPlaybackStatus
    data object Stopped : TtsPlaybackStatus
    data class Error(val message: String) : TtsPlaybackStatus
}

sealed interface TtsExportResult {
    data class Success(
        val contentUri: Uri,
        val displayName: String,
        val fileSizeBytes: Long,
        val mimeType: String,
        val localFile: File
    ) : TtsExportResult

    data class Failure(
        val errorMessage: String,
        val cause: Throwable? = null
    ) : TtsExportResult
}
