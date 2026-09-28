package com.example.ui.screens.image

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.image.ImageCompressionPreset
import com.example.core.image.ImageCompressionResult
import com.example.core.image.ImageCompressorEngine
import com.example.core.image.ImageMetadata
import com.example.core.image.ImageOutputPublisher
import com.example.core.image.ImagePublishResult
import com.example.core.image.OutputFormat
import com.example.core.image.ResizeScale
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ImageCompressorViewModel(
    application: Application,
    private val engine: ImageCompressorEngine,
    private val publisher: ImageOutputPublisher
) : AndroidViewModel(application) {

    constructor(application: Application) : this(
        application = application,
        engine = ImageCompressorEngine(application),
        publisher = ImageOutputPublisher(application)
    )

    private val context: Context get() = getApplication()

    private val _uiState = MutableStateFlow<ImageCompressorUiState>(ImageCompressorUiState.NoImageSelected)
    val uiState: StateFlow<ImageCompressorUiState> = _uiState.asStateFlow()

    private var activeCompressionJob: Job? = null

    fun onImageSelected(uri: Uri) {
        viewModelScope.launch {
            try {
                val metadata = ImageMetadata.extract(context, uri)
                if (metadata.originalWidth <= 0 || metadata.originalHeight <= 0) {
                    _uiState.value = ImageCompressorUiState.Error(
                        sourceUri = uri,
                        metadata = metadata,
                        message = "Unable to read image dimensions or format. File may be corrupted or unsupported."
                    )
                    return@launch
                }

                val initialFormat = when {
                    metadata.isWebp -> OutputFormat.WEBP
                    metadata.isPng -> OutputFormat.PNG
                    else -> OutputFormat.JPEG
                }

                _uiState.value = ImageCompressorUiState.Ready(
                    uri = uri,
                    metadata = metadata,
                    preset = ImageCompressionPreset.BALANCED,
                    quality = ImageCompressionPreset.BALANCED.defaultQuality,
                    format = initialFormat,
                    scale = ResizeScale.ORIGINAL,
                    customMaxWidth = metadata.originalWidth,
                    customMaxHeight = metadata.originalHeight
                )
            } catch (e: Exception) {
                _uiState.value = ImageCompressorUiState.Error(
                    sourceUri = uri,
                    metadata = null,
                    message = "Error analyzing photo: ${e.localizedMessage ?: "Unknown error"}"
                )
            }
        }
    }

    fun selectPreset(preset: ImageCompressionPreset) {
        val currentState = _uiState.value as? ImageCompressorUiState.Ready ?: return
        val newQuality = if (preset == ImageCompressionPreset.CUSTOM) currentState.quality else preset.defaultQuality
        _uiState.value = currentState.copy(
            preset = preset,
            quality = newQuality,
            previewResult = null
        )
    }

    fun updateQuality(quality: Int) {
        val currentState = _uiState.value as? ImageCompressorUiState.Ready ?: return
        _uiState.value = currentState.copy(
            preset = ImageCompressionPreset.CUSTOM,
            quality = quality,
            previewResult = null
        )
    }

    fun selectFormat(format: OutputFormat) {
        val currentState = _uiState.value as? ImageCompressorUiState.Ready ?: return
        _uiState.value = currentState.copy(
            format = format,
            previewResult = null
        )
    }

    fun selectScale(scale: ResizeScale) {
        val currentState = _uiState.value as? ImageCompressorUiState.Ready ?: return
        _uiState.value = currentState.copy(
            scale = scale,
            previewResult = null
        )
    }

    fun updateCustomDimensions(maxWidth: Int, maxHeight: Int) {
        val currentState = _uiState.value as? ImageCompressorUiState.Ready ?: return
        _uiState.value = currentState.copy(
            customMaxWidth = maxWidth,
            customMaxHeight = maxHeight,
            previewResult = null
        )
    }

    fun generatePreview() {
        val currentState = _uiState.value as? ImageCompressorUiState.Ready ?: return
        if (currentState.isPreviewing) return

        _uiState.value = currentState.copy(isPreviewing = true)

        viewModelScope.launch {
            val result = engine.compressImage(
                sourceUri = currentState.uri,
                metadata = currentState.metadata,
                preset = currentState.preset,
                quality = currentState.quality,
                format = currentState.format,
                scale = currentState.scale,
                customMaxWidth = currentState.customMaxWidth,
                customMaxHeight = currentState.customMaxHeight
            )

            val updatedState = _uiState.value as? ImageCompressorUiState.Ready ?: return@launch
            if (result is ImageCompressionResult.Success) {
                _uiState.value = updatedState.copy(
                    isPreviewing = false,
                    previewResult = result
                )
            } else {
                _uiState.value = updatedState.copy(
                    isPreviewing = false,
                    previewResult = null
                )
            }
        }
    }

    fun startCompression() {
        val currentState = _uiState.value as? ImageCompressorUiState.Ready ?: return
        val sourceUri = currentState.uri
        val metadata = currentState.metadata

        _uiState.value = ImageCompressorUiState.Compressing(
            uri = sourceUri,
            metadata = metadata,
            statusText = "Compressing photo..."
        )

        activeCompressionJob = viewModelScope.launch {
            val compressionResult = engine.compressImage(
                sourceUri = sourceUri,
                metadata = metadata,
                preset = currentState.preset,
                quality = currentState.quality,
                format = currentState.format,
                scale = currentState.scale,
                customMaxWidth = currentState.customMaxWidth,
                customMaxHeight = currentState.customMaxHeight
            )

            when (compressionResult) {
                is ImageCompressionResult.Success -> {
                    val compressingState = _uiState.value as? ImageCompressorUiState.Compressing
                    if (compressingState != null) {
                        _uiState.value = compressingState.copy(statusText = "Saving to Pictures/PixelRox...")
                    }

                    val publishResult = publisher.publishImage(
                        sourceFile = compressionResult.tempFile,
                        desiredDisplayName = metadata.displayName,
                        format = compressionResult.format
                    )

                    if (compressionResult.tempFile.exists()) {
                        compressionResult.tempFile.delete()
                    }

                    when (publishResult) {
                        is ImagePublishResult.Success -> {
                            _uiState.value = ImageCompressorUiState.Success(
                                outputUri = publishResult.contentUri,
                                savedFileName = publishResult.displayName,
                                originalWidth = metadata.originalWidth,
                                originalHeight = metadata.originalHeight,
                                originalSizeBytes = metadata.fileSizeBytes,
                                compressedWidth = compressionResult.outputWidth,
                                compressedHeight = compressionResult.outputHeight,
                                compressedSizeBytes = publishResult.fileSizeBytes,
                                format = compressionResult.format
                            )
                        }
                        is ImagePublishResult.Failure -> {
                            _uiState.value = ImageCompressorUiState.Error(
                                sourceUri = sourceUri,
                                metadata = metadata,
                                message = "Failed to save compressed photo: ${publishResult.errorMessage}"
                            )
                        }
                    }
                }
                is ImageCompressionResult.Failure -> {
                    _uiState.value = ImageCompressorUiState.Error(
                        sourceUri = sourceUri,
                        metadata = metadata,
                        message = compressionResult.message
                    )
                }
                is ImageCompressionResult.Cancelled -> {
                    _uiState.value = currentState
                }
            }
        }
    }

    fun cancelCompression() {
        activeCompressionJob?.cancel()
        val currentState = _uiState.value
        if (currentState is ImageCompressorUiState.Compressing) {
            _uiState.value = ImageCompressorUiState.NoImageSelected
        }
    }

    fun resetToSelect() {
        _uiState.value = ImageCompressorUiState.NoImageSelected
    }
}
