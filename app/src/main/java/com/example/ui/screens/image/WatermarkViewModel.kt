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

enum class WatermarkType {
    TEXT,
    IMAGE
}

enum class WatermarkPositionPreset(val label: String, val normX: Float, val normY: Float) {
    TOP_LEFT("Top Left", 0.05f, 0.1f),
    TOP_RIGHT("Top Right", 0.7f, 0.1f),
    CENTER("Center", 0.4f, 0.5f),
    BOTTOM_LEFT("Bottom Left", 0.05f, 0.9f),
    BOTTOM_RIGHT("Bottom Right", 0.7f, 0.9f)
}

sealed interface WatermarkUiState {
    data object Idle : WatermarkUiState

    data class Loaded(
        val sourceBitmap: Bitmap,
        val watermarkType: WatermarkType = WatermarkType.TEXT,
        // Text watermark settings
        val text: String = "PixelRox",
        val textSizeSp: Float = 36f,
        val textColor: Int = Color.WHITE,
        // Image watermark settings
        val logoBitmap: Bitmap? = null,
        val imageScale: Float = 0.5f,
        // Common settings
        val opacity: Float = 0.8f,
        val normX: Float = 0.7f,
        val normY: Float = 0.9f,
        val outputFormat: OutputFormat = OutputFormat.JPEG,
        val previewBitmap: Bitmap? = null,
        val isRenderingPreview: Boolean = false
    ) : WatermarkUiState

    data class Processing(val message: String) : WatermarkUiState

    data class Success(
        val contentUri: Uri,
        val displayName: String,
        val fileSizeBytes: Long,
        val format: OutputFormat,
        val resultBitmap: Bitmap
    ) : WatermarkUiState

    data class Error(val message: String) : WatermarkUiState
}

class WatermarkViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow<WatermarkUiState>(WatermarkUiState.Idle)
    val uiState: StateFlow<WatermarkUiState> = _uiState.asStateFlow()

    fun selectSourceImage(uri: Uri) {
        viewModelScope.launch {
            _uiState.value = WatermarkUiState.Processing("Loading image...")
            val bitmap = ImageBitmapHelper.decodeSafeBitmap(getApplication(), uri, maxDimension = 2048)
            if (bitmap == null) {
                _uiState.value = WatermarkUiState.Error("Failed to decode source image.")
                return@launch
            }

            val initialState = WatermarkUiState.Loaded(sourceBitmap = bitmap)
            _uiState.value = initialState
            updatePreview(initialState)
        }
    }

    fun selectLogoImage(uri: Uri) {
        val current = _uiState.value as? WatermarkUiState.Loaded ?: return
        viewModelScope.launch {
            val logo = ImageBitmapHelper.decodeSafeBitmap(getApplication(), uri, maxDimension = 800)
            if (logo != null) {
                val updated = current.copy(logoBitmap = logo, watermarkType = WatermarkType.IMAGE)
                _uiState.value = updated
                updatePreview(updated)
            }
        }
    }

    fun setWatermarkType(type: WatermarkType) {
        val current = _uiState.value as? WatermarkUiState.Loaded ?: return
        val updated = current.copy(watermarkType = type)
        _uiState.value = updated
        updatePreview(updated)
    }

    fun setText(text: String) {
        val current = _uiState.value as? WatermarkUiState.Loaded ?: return
        val updated = current.copy(text = text)
        _uiState.value = updated
        updatePreview(updated)
    }

    fun setTextSize(sizeSp: Float) {
        val current = _uiState.value as? WatermarkUiState.Loaded ?: return
        val updated = current.copy(textSizeSp = sizeSp.coerceIn(14f, 72f))
        _uiState.value = updated
        updatePreview(updated)
    }

    fun setTextColor(color: Int) {
        val current = _uiState.value as? WatermarkUiState.Loaded ?: return
        val updated = current.copy(textColor = color)
        _uiState.value = updated
        updatePreview(updated)
    }

    fun setOpacity(opacity: Float) {
        val current = _uiState.value as? WatermarkUiState.Loaded ?: return
        val updated = current.copy(opacity = opacity.coerceIn(0.1f, 1.0f))
        _uiState.value = updated
        updatePreview(updated)
    }

    fun setImageScale(scale: Float) {
        val current = _uiState.value as? WatermarkUiState.Loaded ?: return
        val updated = current.copy(imageScale = scale.coerceIn(0.1f, 1.0f))
        _uiState.value = updated
        updatePreview(updated)
    }

    fun setPosition(normX: Float, normY: Float) {
        val current = _uiState.value as? WatermarkUiState.Loaded ?: return
        val updated = current.copy(
            normX = normX.coerceIn(0.01f, 0.95f),
            normY = normY.coerceIn(0.05f, 0.98f)
        )
        _uiState.value = updated
        updatePreview(updated)
    }

    fun applyPositionPreset(preset: WatermarkPositionPreset) {
        setPosition(preset.normX, preset.normY)
    }

    fun setOutputFormat(format: OutputFormat) {
        val current = _uiState.value as? WatermarkUiState.Loaded ?: return
        _uiState.update { current.copy(outputFormat = format) }
    }

    private fun updatePreview(state: WatermarkUiState.Loaded) {
        viewModelScope.launch {
            _uiState.update { if (it is WatermarkUiState.Loaded) it.copy(isRenderingPreview = true) else it }

            val preview = withContext(Dispatchers.Default) {
                // Downsample source for fast reactive preview
                val previewMaxDim = 800
                val scale = previewMaxDim.toFloat() / maxOf(state.sourceBitmap.width, state.sourceBitmap.height)
                val base = if (scale < 1.0f) {
                    val pw = (state.sourceBitmap.width * scale).toInt().coerceAtLeast(1)
                    val ph = (state.sourceBitmap.height * scale).toInt().coerceAtLeast(1)
                    Bitmap.createScaledBitmap(state.sourceBitmap, pw, ph, true)
                } else {
                    state.sourceBitmap
                }

                val previewTextSize = state.textSizeSp * (base.width.toFloat() / state.sourceBitmap.width.toFloat()).coerceAtLeast(0.4f)

                val result = if (state.watermarkType == WatermarkType.TEXT) {
                    ImageBitmapHelper.applyWatermarkText(
                        source = base,
                        text = state.text.ifBlank { " " },
                        textSizeSp = previewTextSize,
                        textColor = state.textColor,
                        opacity = state.opacity,
                        normX = state.normX,
                        normY = state.normY
                    )
                } else if (state.logoBitmap != null) {
                    ImageBitmapHelper.applyWatermarkImage(
                        source = base,
                        logo = state.logoBitmap,
                        scale = state.imageScale,
                        opacity = state.opacity,
                        normX = state.normX,
                        normY = state.normY
                    )
                } else {
                    base
                }

                if (base != state.sourceBitmap && base != result) {
                    base.recycle()
                }
                result
            }

            _uiState.update {
                if (it is WatermarkUiState.Loaded) {
                    it.copy(previewBitmap = preview, isRenderingPreview = false)
                } else it
            }
        }
    }

    fun exportImage() {
        val current = _uiState.value as? WatermarkUiState.Loaded ?: return

        viewModelScope.launch {
            _uiState.value = WatermarkUiState.Processing("Composing watermark on full-res image...")

            val composed = withContext(Dispatchers.Default) {
                if (current.watermarkType == WatermarkType.TEXT) {
                    ImageBitmapHelper.applyWatermarkText(
                        source = current.sourceBitmap,
                        text = current.text.ifBlank { " " },
                        textSizeSp = current.textSizeSp,
                        textColor = current.textColor,
                        opacity = current.opacity,
                        normX = current.normX,
                        normY = current.normY
                    )
                } else if (current.logoBitmap != null) {
                    ImageBitmapHelper.applyWatermarkImage(
                        source = current.sourceBitmap,
                        logo = current.logoBitmap,
                        scale = current.imageScale,
                        opacity = current.opacity,
                        normX = current.normX,
                        normY = current.normY
                    )
                } else {
                    current.sourceBitmap
                }
            }

            _uiState.value = WatermarkUiState.Processing("Saving to Pictures/PixelRox...")

            val publishResult = ImageBitmapHelper.saveAndPublish(
                context = getApplication(),
                bitmap = composed,
                format = current.outputFormat,
                baseName = "watermarked"
            )

            when (publishResult) {
                is ImagePublishResult.Success -> {
                    _uiState.value = WatermarkUiState.Success(
                        contentUri = publishResult.contentUri,
                        displayName = publishResult.displayName,
                        fileSizeBytes = publishResult.fileSizeBytes,
                        format = current.outputFormat,
                        resultBitmap = composed
                    )
                }
                is ImagePublishResult.Failure -> {
                    _uiState.value = WatermarkUiState.Error("Failed to save: ${publishResult.errorMessage}")
                }
            }
        }
    }

    fun reset() {
        _uiState.value = WatermarkUiState.Idle
    }
}
