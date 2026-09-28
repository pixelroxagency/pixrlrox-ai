package com.example.core.video

import android.net.Uri

sealed interface VideoCompressorUiState {
    data object NoVideoSelected : VideoCompressorUiState

    data class Selected(
        val sourceUri: Uri,
        val metadata: VideoMetadata,
        val selectedPreset: VideoCompressionPreset,
        val estimatedOutputSizeBytes: Long
    ) : VideoCompressorUiState

    data class Compressing(
        val sourceUri: Uri,
        val metadata: VideoMetadata,
        val selectedPreset: VideoCompressionPreset,
        val progressPercent: Int?,
        val estimatedOutputSizeBytes: Long
    ) : VideoCompressorUiState

    data class Publishing(
        val sourceUri: Uri,
        val metadata: VideoMetadata,
        val selectedPreset: VideoCompressionPreset,
        val compressedSizeBytes: Long
    ) : VideoCompressorUiState

    data class Success(
        val sourceUri: Uri,
        val metadata: VideoMetadata,
        val selectedPreset: VideoCompressionPreset,
        val finalContentUri: Uri,
        val displayName: String,
        val originalSizeBytes: Long,
        val compressedSizeBytes: Long,
        val bytesSaved: Long,
        val percentageSaved: Float?,
        val mimeType: String,
        val outputWidth: Int = 0,
        val outputHeight: Int = 0
    ) : VideoCompressorUiState

    data class Error(
        val errorMessage: String,
        val sourceUri: Uri? = null,
        val metadata: VideoMetadata? = null,
        val selectedPreset: VideoCompressionPreset? = null,
        val estimatedOutputSizeBytes: Long = 0L
    ) : VideoCompressorUiState
}
