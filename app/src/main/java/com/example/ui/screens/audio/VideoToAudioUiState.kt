package com.example.ui.screens.audio

import android.net.Uri
import com.example.core.audio.AudioMetadata
import com.example.core.audio.AudioOutputFormat
import com.example.core.audio.AudioQualityPreset
import com.example.core.audio.Mp3Bitrate

sealed interface VideoToAudioUiState {
    data object NoVideoSelected : VideoToAudioUiState

    data class Ready(
        val uri: Uri,
        val metadata: AudioMetadata,
        val selectedFormat: AudioOutputFormat = AudioOutputFormat.MP3,
        val selectedMp3Bitrate: Mp3Bitrate = Mp3Bitrate.HIGH_192,
        val selectedM4aPreset: AudioQualityPreset = AudioQualityPreset.DIRECT_REMUX
    ) : VideoToAudioUiState

    data class Extracting(
        val uri: Uri,
        val metadata: AudioMetadata,
        val progressPercent: Int,
        val statusText: String
    ) : VideoToAudioUiState

    data class Success(
        val outputUri: Uri,
        val savedFileName: String,
        val durationMs: Long,
        val fileSizeBytes: Long,
        val format: AudioOutputFormat,
        val bitrateKbps: Int,
        val isDirectRemux: Boolean
    ) : VideoToAudioUiState

    data class Error(
        val sourceUri: Uri?,
        val metadata: AudioMetadata?,
        val message: String
    ) : VideoToAudioUiState
}
