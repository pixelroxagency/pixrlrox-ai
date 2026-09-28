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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class SocialPreset(
    val title: String,
    val platform: String,
    val width: Int,
    val height: Int,
    val aspectLabel: String
) {
    SQUARE_POST("Square Post", "Instagram / Feed", 1080, 1080, "1:1"),
    PORTRAIT_POST("Portrait Post", "Instagram / FB", 1080, 1350, "4:5"),
    STORY_REEL("Story & Reel", "IG / TikTok / Shorts", 1080, 1920, "9:16"),
    LANDSCAPE_POST("Landscape Post", "Twitter / LinkedIn", 1200, 630, "1.91:1"),
    YOUTUBE_THUMBNAIL("Video Thumbnail", "YouTube / Vimeo", 1280, 720, "16:9"),
    CUSTOM("Custom Dimensions", "Custom", 1080, 1080, "Custom")
}

sealed interface SocialMediaSizeUiState {
    data object Idle : SocialMediaSizeUiState

    data class Loaded(
        val sourceBitmap: Bitmap,
        val sourceWidth: Int,
        val sourceHeight: Int,
        val selectedPreset: SocialPreset = SocialPreset.SQUARE_POST,
        val targetWidth: Int = 1080,
        val targetHeight: Int = 1080,
        val fitMode: ImageBitmapHelper.FitMode = ImageBitmapHelper.FitMode.CROP_TO_FILL,
        val backgroundColor: Int = Color.WHITE,
        val outputFormat: OutputFormat = OutputFormat.JPEG,
        val previewBitmap: Bitmap? = null,
        val isRenderingPreview: Boolean = false
    ) : SocialMediaSizeUiState

    data class Processing(val progressMessage: String) : SocialMediaSizeUiState

    data class Success(
        val contentUri: Uri,
        val displayName: String,
        val outputWidth: Int,
        val outputHeight: Int,
        val fileSizeBytes: Long,
        val format: OutputFormat,
        val resultBitmap: Bitmap
    ) : SocialMediaSizeUiState

    data class Error(val message: String) : SocialMediaSizeUiState
}

class SocialMediaSizeMakerViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow<SocialMediaSizeUiState>(SocialMediaSizeUiState.Idle)
    val uiState: StateFlow<SocialMediaSizeUiState> = _uiState.asStateFlow()

    fun selectImage(uri: Uri) {
        viewModelScope.launch {
            _uiState.value = SocialMediaSizeUiState.Processing("Loading and orienting image...")
            val bitmap = ImageBitmapHelper.decodeSafeBitmap(getApplication(), uri, maxDimension = 2048)
            if (bitmap == null) {
                _uiState.value = SocialMediaSizeUiState.Error("Failed to decode image.")
                return@launch
            }

            val defaultPreset = SocialPreset.SQUARE_POST
            val initialState = SocialMediaSizeUiState.Loaded(
                sourceBitmap = bitmap,
                sourceWidth = bitmap.width,
                sourceHeight = bitmap.height,
                selectedPreset = defaultPreset,
                targetWidth = defaultPreset.width,
                targetHeight = defaultPreset.height,
                fitMode = ImageBitmapHelper.FitMode.CROP_TO_FILL,
                backgroundColor = Color.WHITE
            )
            _uiState.value = initialState
            updatePreview(initialState)
        }
    }

    fun selectPreset(preset: SocialPreset) {
        val current = _uiState.value as? SocialMediaSizeUiState.Loaded ?: return
        val w = if (preset != SocialPreset.CUSTOM) preset.width else current.targetWidth
        val h = if (preset != SocialPreset.CUSTOM) preset.height else current.targetHeight

        val updated = current.copy(
            selectedPreset = preset,
            targetWidth = w,
            targetHeight = h
        )
        _uiState.value = updated
        updatePreview(updated)
    }

    fun updateCustomDimensions(width: Int, height: Int) {
        val current = _uiState.value as? SocialMediaSizeUiState.Loaded ?: return
        val clampedW = width.coerceIn(100, 4096)
        val clampedH = height.coerceIn(100, 4096)

        val updated = current.copy(
            selectedPreset = SocialPreset.CUSTOM,
            targetWidth = clampedW,
            targetHeight = clampedH
        )
        _uiState.value = updated
        updatePreview(updated)
    }

    fun setFitMode(mode: ImageBitmapHelper.FitMode) {
        val current = _uiState.value as? SocialMediaSizeUiState.Loaded ?: return
        val updated = current.copy(fitMode = mode)
        _uiState.value = updated
        updatePreview(updated)
    }

    fun setBackgroundColor(color: Int) {
        val current = _uiState.value as? SocialMediaSizeUiState.Loaded ?: return
        val updated = current.copy(backgroundColor = color)
        _uiState.value = updated
        updatePreview(updated)
    }

    fun setOutputFormat(format: OutputFormat) {
        val current = _uiState.value as? SocialMediaSizeUiState.Loaded ?: return
        _uiState.update { current.copy(outputFormat = format) }
    }

    private fun updatePreview(state: SocialMediaSizeUiState.Loaded) {
        viewModelScope.launch {
            _uiState.update { if (it is SocialMediaSizeUiState.Loaded) it.copy(isRenderingPreview = true) else it }

            val preview = withContext(Dispatchers.Default) {
                // Downscale target for fast smooth preview
                val previewMaxDim = 600
                val aspect = state.targetWidth.toFloat() / state.targetHeight.toFloat()
                val prevW: Int
                val prevH: Int
                if (aspect >= 1f) {
                    prevW = previewMaxDim
                    prevH = (previewMaxDim / aspect).toInt().coerceAtLeast(1)
                } else {
                    prevH = previewMaxDim
                    prevW = (previewMaxDim * aspect).toInt().coerceAtLeast(1)
                }

                ImageBitmapHelper.fitOrCropBitmap(
                    source = state.sourceBitmap,
                    targetWidth = prevW,
                    targetHeight = prevH,
                    fitMode = state.fitMode,
                    backgroundColor = state.backgroundColor
                )
            }

            _uiState.update {
                if (it is SocialMediaSizeUiState.Loaded) {
                    it.copy(previewBitmap = preview, isRenderingPreview = false)
                } else it
            }
        }
    }

    fun exportImage() {
        val current = _uiState.value as? SocialMediaSizeUiState.Loaded ?: return

        viewModelScope.launch {
            _uiState.value = SocialMediaSizeUiState.Processing("Generating final high-res image...")

            val processedBitmap = withContext(Dispatchers.Default) {
                ImageBitmapHelper.fitOrCropBitmap(
                    source = current.sourceBitmap,
                    targetWidth = current.targetWidth,
                    targetHeight = current.targetHeight,
                    fitMode = current.fitMode,
                    backgroundColor = current.backgroundColor
                )
            }

            _uiState.value = SocialMediaSizeUiState.Processing("Saving to Pictures/PixelRox...")

            val publishResult = ImageBitmapHelper.saveAndPublish(
                context = getApplication(),
                bitmap = processedBitmap,
                format = current.outputFormat,
                baseName = "social_${current.selectedPreset.name.lowercase()}"
            )

            when (publishResult) {
                is ImagePublishResult.Success -> {
                    _uiState.value = SocialMediaSizeUiState.Success(
                        contentUri = publishResult.contentUri,
                        displayName = publishResult.displayName,
                        outputWidth = current.targetWidth,
                        outputHeight = current.targetHeight,
                        fileSizeBytes = publishResult.fileSizeBytes,
                        format = current.outputFormat,
                        resultBitmap = processedBitmap
                    )
                }
                is ImagePublishResult.Failure -> {
                    _uiState.value = SocialMediaSizeUiState.Error("Export failed: ${publishResult.errorMessage}")
                }
            }
        }
    }

    fun reset() {
        _uiState.value = SocialMediaSizeUiState.Idle
    }
}
