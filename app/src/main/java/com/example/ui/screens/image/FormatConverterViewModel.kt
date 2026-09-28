package com.example.ui.screens.image

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.image.ImagePublishResult
import com.example.core.image.OutputFormat
import com.example.data.util.ImageBitmapHelper
import com.example.data.util.ImageProcessingPipeline
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface FormatConverterUiState {
    data object Idle : FormatConverterUiState

    data class Loaded(
        val sourceUri: Uri,
        val sourceBitmap: Bitmap,
        val originalWidth: Int,
        val originalHeight: Int,
        val originalMimeType: String?,
        val originalFileSizeBytes: Long,
        val targetFormat: OutputFormat = OutputFormat.PNG,
        val quality: Int = 90,
        val flattenColor: Int = Color.WHITE, // For transparency when converting to JPEG
        val previewBitmap: Bitmap? = null
    ) : FormatConverterUiState

    data class Converting(val message: String) : FormatConverterUiState

    data class Success(
        val contentUri: Uri,
        val displayName: String,
        val fileSizeBytes: Long,
        val outputFormat: OutputFormat,
        val outputWidth: Int,
        val outputHeight: Int,
        val resultBitmap: Bitmap
    ) : FormatConverterUiState

    data class Error(val message: String) : FormatConverterUiState
}

class FormatConverterViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow<FormatConverterUiState>(FormatConverterUiState.Idle)
    val uiState: StateFlow<FormatConverterUiState> = _uiState.asStateFlow()

    fun selectSourceImage(uri: Uri) {
        viewModelScope.launch {
            _uiState.value = FormatConverterUiState.Converting("Inspecting and decoding image...")

            val context = getApplication<Application>()
            var fileSize = 0L
            var mimeType: String? = null

            try {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    fileSize = pfd.statSize
                }
                mimeType = context.contentResolver.getType(uri)
            } catch (_: Exception) {}

            val bitmap = ImageBitmapHelper.decodeSafeBitmap(context, uri, maxDimension = 2560)
            if (bitmap == null) {
                _uiState.value = FormatConverterUiState.Error("Failed to decode image. Format may not be supported by this Android device.")
                return@launch
            }

            // Default target format: if original is JPEG, default to PNG, else JPEG
            val defaultTarget = if (mimeType?.contains("jpeg", ignoreCase = true) == true || mimeType?.contains("jpg", ignoreCase = true) == true) {
                OutputFormat.PNG
            } else {
                OutputFormat.JPEG
            }

            _uiState.value = FormatConverterUiState.Loaded(
                sourceUri = uri,
                sourceBitmap = bitmap,
                originalWidth = bitmap.width,
                originalHeight = bitmap.height,
                originalMimeType = mimeType,
                originalFileSizeBytes = fileSize,
                targetFormat = defaultTarget,
                quality = 90,
                previewBitmap = bitmap
            )
        }
    }

    fun setTargetFormat(format: OutputFormat) {
        val current = _uiState.value as? FormatConverterUiState.Loaded ?: return
        _uiState.update { current.copy(targetFormat = format) }
    }

    fun setQuality(quality: Int) {
        val current = _uiState.value as? FormatConverterUiState.Loaded ?: return
        _uiState.update { current.copy(quality = quality.coerceIn(1, 100)) }
    }

    fun setFlattenColor(color: Int) {
        val current = _uiState.value as? FormatConverterUiState.Loaded ?: return
        _uiState.update { current.copy(flattenColor = color) }
    }

    fun convertImage() {
        val current = _uiState.value as? FormatConverterUiState.Loaded ?: return

        viewModelScope.launch {
            _uiState.value = FormatConverterUiState.Converting("Converting to ${current.targetFormat.title}...")

            val context = getApplication<Application>()

            val result = withContext(Dispatchers.Default) {
                var processed = current.sourceBitmap
                var tempFlattened: Bitmap? = null

                if (current.targetFormat == OutputFormat.JPEG && current.sourceBitmap.hasAlpha()) {
                    tempFlattened = ImageProcessingPipeline.flattenAlpha(current.sourceBitmap, current.flattenColor)
                    processed = tempFlattened
                }

                val publishResult = ImageBitmapHelper.saveAndPublish(
                    context = context,
                    bitmap = processed,
                    format = current.targetFormat,
                    quality = if (current.targetFormat.supportsQuality) current.quality else 100,
                    baseName = "converted_${current.targetFormat.name.lowercase()}"
                )

                tempFlattened?.recycle()
                publishResult
            }

            when (result) {
                is ImagePublishResult.Success -> {
                    _uiState.value = FormatConverterUiState.Success(
                        contentUri = result.contentUri,
                        displayName = result.displayName,
                        fileSizeBytes = result.fileSizeBytes,
                        outputFormat = current.targetFormat,
                        outputWidth = current.originalWidth,
                        outputHeight = current.originalHeight,
                        resultBitmap = current.sourceBitmap
                    )
                }
                is ImagePublishResult.Failure -> {
                    _uiState.value = FormatConverterUiState.Error("Failed to convert image: ${result.errorMessage}")
                }
            }
        }
    }

    fun reset() {
        val current = _uiState.value
        if (current is FormatConverterUiState.Loaded) {
            current.sourceBitmap.recycle()
        }
        _uiState.value = FormatConverterUiState.Idle
    }

    override fun onCleared() {
        super.onCleared()
        val current = _uiState.value
        if (current is FormatConverterUiState.Loaded) {
            current.sourceBitmap.recycle()
        }
    }
}
