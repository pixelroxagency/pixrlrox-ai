package com.example.ui.screens.image

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RectF
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

enum class AvatarBackgroundMode {
    TRANSPARENT,
    SOLID_WHITE,
    SOLID_DARK,
    SOLID_ACCENT,
    GRADIENT_BLUE,
    GRADIENT_SUNSET
}

enum class AvatarExportSize(val dimension: Int, val label: String) {
    STANDARD_512(512, "512 x 512 px"),
    HIGH_RES_1024(1024, "1024 x 1024 px")
}

sealed interface AvatarMakerUiState {
    data object Idle : AvatarMakerUiState

    data class Loaded(
        val sourceUri: Uri,
        val sourceBitmap: Bitmap,
        val cropRectNorm: RectF = RectF(0f, 0f, 1f, 1f),
        val backgroundMode: AvatarBackgroundMode = AvatarBackgroundMode.SOLID_WHITE,
        val customBgColor: Int = Color.WHITE,
        val borderColor: Int = Color.rgb(33, 150, 243),
        val borderWidthDp: Float = 6f, // border ring thickness
        val exportSize: AvatarExportSize = AvatarExportSize.STANDARD_512,
        val outputFormat: OutputFormat = OutputFormat.PNG,
        val previewBitmap: Bitmap? = null,
        val isRenderingPreview: Boolean = false
    ) : AvatarMakerUiState

    data class Processing(val message: String) : AvatarMakerUiState

    data class Success(
        val contentUri: Uri,
        val displayName: String,
        val fileSizeBytes: Long,
        val outputFormat: OutputFormat,
        val dimension: Int,
        val resultBitmap: Bitmap
    ) : AvatarMakerUiState

    data class Error(val message: String) : AvatarMakerUiState
}

class AvatarMakerViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow<AvatarMakerUiState>(AvatarMakerUiState.Idle)
    val uiState: StateFlow<AvatarMakerUiState> = _uiState.asStateFlow()

    fun selectSourceImage(uri: Uri) {
        viewModelScope.launch {
            _uiState.value = AvatarMakerUiState.Processing("Loading photo...")
            val context = getApplication<Application>()
            val bitmap = ImageBitmapHelper.decodeSafeBitmap(context, uri, maxDimension = 2048)

            if (bitmap == null) {
                _uiState.value = AvatarMakerUiState.Error("Failed to decode photo")
                return@launch
            }

            val loaded = AvatarMakerUiState.Loaded(
                sourceUri = uri,
                sourceBitmap = bitmap
            )
            _uiState.value = loaded
            updatePreview(loaded)
        }
    }

    fun setBackgroundMode(mode: AvatarBackgroundMode) {
        val current = _uiState.value as? AvatarMakerUiState.Loaded ?: return
        val format = if (mode == AvatarBackgroundMode.TRANSPARENT) OutputFormat.PNG else current.outputFormat
        val updated = current.copy(backgroundMode = mode, outputFormat = format)
        _uiState.value = updated
        updatePreview(updated)
    }

    fun setBorderColor(color: Int) {
        val current = _uiState.value as? AvatarMakerUiState.Loaded ?: return
        val updated = current.copy(borderColor = color)
        _uiState.value = updated
        updatePreview(updated)
    }

    fun setBorderWidthDp(width: Float) {
        val current = _uiState.value as? AvatarMakerUiState.Loaded ?: return
        val updated = current.copy(borderWidthDp = width.coerceIn(0f, 32f))
        _uiState.value = updated
        updatePreview(updated)
    }

    fun setExportSize(size: AvatarExportSize) {
        val current = _uiState.value as? AvatarMakerUiState.Loaded ?: return
        _uiState.update { current.copy(exportSize = size) }
    }

    fun setOutputFormat(format: OutputFormat) {
        val current = _uiState.value as? AvatarMakerUiState.Loaded ?: return
        _uiState.update { current.copy(outputFormat = format) }
    }

    private fun updatePreview(state: AvatarMakerUiState.Loaded) {
        viewModelScope.launch {
            val preview = withContext(Dispatchers.Default) {
                val bgColor = when (state.backgroundMode) {
                    AvatarBackgroundMode.TRANSPARENT -> null
                    AvatarBackgroundMode.SOLID_WHITE -> Color.WHITE
                    AvatarBackgroundMode.SOLID_DARK -> Color.rgb(24, 24, 27)
                    AvatarBackgroundMode.SOLID_ACCENT -> Color.rgb(238, 242, 255)
                    AvatarBackgroundMode.GRADIENT_BLUE -> Color.rgb(186, 230, 253)
                    AvatarBackgroundMode.GRADIENT_SUNSET -> Color.rgb(254, 215, 170)
                }

                ImageProcessingPipeline.renderAvatar(
                    source = state.sourceBitmap,
                    targetSize = 512,
                    backgroundColor = bgColor,
                    borderColor = state.borderColor,
                    borderWidthPx = state.borderWidthDp * 2f // approx 2px per dp for preview
                )
            }

            _uiState.update {
                if (it is AvatarMakerUiState.Loaded) {
                    it.copy(previewBitmap = preview)
                } else it
            }
        }
    }

    fun exportAvatar() {
        val current = _uiState.value as? AvatarMakerUiState.Loaded ?: return
        val context = getApplication<Application>()

        viewModelScope.launch {
            _uiState.value = AvatarMakerUiState.Processing("Rendering avatar (${current.exportSize.label})...")

            val targetDim = current.exportSize.dimension
            val density = context.resources.displayMetrics.density
            val borderWidthPx = current.borderWidthDp * density

            val bgColor = when (current.backgroundMode) {
                AvatarBackgroundMode.TRANSPARENT -> null
                AvatarBackgroundMode.SOLID_WHITE -> Color.WHITE
                AvatarBackgroundMode.SOLID_DARK -> Color.rgb(24, 24, 27)
                AvatarBackgroundMode.SOLID_ACCENT -> Color.rgb(238, 242, 255)
                AvatarBackgroundMode.GRADIENT_BLUE -> Color.rgb(186, 230, 253)
                AvatarBackgroundMode.GRADIENT_SUNSET -> Color.rgb(254, 215, 170)
            }

            val result = withContext(Dispatchers.Default) {
                val rendered = ImageProcessingPipeline.renderAvatar(
                    source = current.sourceBitmap,
                    targetSize = targetDim,
                    backgroundColor = bgColor,
                    borderColor = current.borderColor,
                    borderWidthPx = borderWidthPx
                )

                // If user chose JPEG and background is transparent, flatten on white
                val finalBitmap = if (current.outputFormat == OutputFormat.JPEG && bgColor == null) {
                    ImageProcessingPipeline.flattenAlpha(rendered, Color.WHITE)
                } else {
                    rendered
                }

                val pubResult = ImageBitmapHelper.saveAndPublish(
                    context = context,
                    bitmap = finalBitmap,
                    format = current.outputFormat,
                    quality = 95,
                    baseName = "profile_avatar"
                )

                if (finalBitmap != rendered) {
                    finalBitmap.recycle()
                }

                Pair(pubResult, rendered)
            }

            val (pubResult, renderedBitmap) = result
            when (pubResult) {
                is ImagePublishResult.Success -> {
                    _uiState.value = AvatarMakerUiState.Success(
                        contentUri = pubResult.contentUri,
                        displayName = pubResult.displayName,
                        fileSizeBytes = pubResult.fileSizeBytes,
                        outputFormat = current.outputFormat,
                        dimension = targetDim,
                        resultBitmap = renderedBitmap
                    )
                }
                is ImagePublishResult.Failure -> {
                    renderedBitmap.recycle()
                    _uiState.value = AvatarMakerUiState.Error("Failed to save avatar: ${pubResult.errorMessage}")
                }
            }
        }
    }

    fun reset() {
        val current = _uiState.value as? AvatarMakerUiState.Loaded
        current?.sourceBitmap?.recycle()
        current?.previewBitmap?.recycle()
        _uiState.value = AvatarMakerUiState.Idle
    }

    override fun onCleared() {
        super.onCleared()
        val current = _uiState.value as? AvatarMakerUiState.Loaded
        current?.sourceBitmap?.recycle()
        current?.previewBitmap?.recycle()
    }
}
