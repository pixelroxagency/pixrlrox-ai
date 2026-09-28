package com.example.ui.screens.trimmer

import android.net.Uri
import com.example.core.video.VideoMetadata
import java.io.File

sealed interface VideoTrimmerUiState {
    data object NoVideoSelected : VideoTrimmerUiState

    data class Ready(
        val uri: Uri,
        val metadata: VideoMetadata,
        val startMs: Long,
        val endMs: Long,
        val isPlayingPreview: Boolean = false,
        val previewCurrentPositionMs: Long = 0L
    ) : VideoTrimmerUiState

    data class Trimming(
        val uri: Uri,
        val metadata: VideoMetadata,
        val progressPercent: Int
    ) : VideoTrimmerUiState

    data class Success(
        val outputUri: Uri,
        val savedFileName: String,
        val trimmedDurationMs: Long,
        val originalDurationMs: Long,
        val fileSizeBytes: Long
    ) : VideoTrimmerUiState

    data class Error(
        val sourceUri: Uri?,
        val metadata: VideoMetadata?,
        val message: String
    ) : VideoTrimmerUiState
}
