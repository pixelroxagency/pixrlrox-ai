package com.example.core.audio

import java.io.File

sealed interface VideoToAudioResult {
    data class Success(
        val tempFile: File,
        val durationMs: Long,
        val fileSizeBytes: Long,
        val format: AudioOutputFormat,
        val bitrateKbps: Int,
        val isDirectRemux: Boolean
    ) : VideoToAudioResult

    data class Failure(
        val message: String,
        val cause: Throwable? = null
    ) : VideoToAudioResult

    data object Cancelled : VideoToAudioResult
}
