package com.example.ui.screens.image

import android.net.Uri
import com.example.core.image.ImageCompressionPreset
import com.example.core.image.ImageCompressionResult
import com.example.core.image.ImageMetadata
import com.example.core.image.OutputFormat
import com.example.core.image.ResizeScale

sealed interface ImageCompressorUiState {
    data object NoImageSelected : ImageCompressorUiState

    data class Ready(
        val uri: Uri,
        val metadata: ImageMetadata,
        val preset: ImageCompressionPreset,
        val quality: Int,
        val format: OutputFormat,
        val scale: ResizeScale,
        val customMaxWidth: Int = 1920,
        val customMaxHeight: Int = 1080,
        val isPreviewing: Boolean = false,
        val previewResult: ImageCompressionResult.Success? = null
    ) : ImageCompressorUiState

    data class Compressing(
        val uri: Uri,
        val metadata: ImageMetadata,
        val statusText: String
    ) : ImageCompressorUiState

    data class Success(
        val outputUri: Uri,
        val savedFileName: String,
        val originalWidth: Int,
        val originalHeight: Int,
        val originalSizeBytes: Long,
        val compressedWidth: Int,
        val compressedHeight: Int,
        val compressedSizeBytes: Long,
        val format: OutputFormat
    ) : ImageCompressorUiState

    data class Error(
        val sourceUri: Uri?,
        val metadata: ImageMetadata?,
        val message: String
    ) : ImageCompressorUiState
}
