package com.example.ui.screens.image

import android.app.Application
import android.content.Context
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

enum class CollageAspect(val label: String, val width: Int, val height: Int) {
    SQUARE("Square 1:1", 1080, 1080),
    PORTRAIT("Portrait 4:5", 1080, 1350),
    STORY("Story 9:16", 1080, 1920)
}

sealed interface CollageMakerUiState {
    data object Idle : CollageMakerUiState

    data class Loaded(
        val sourceBitmaps: List<Bitmap>,
        val layoutVariant: Int = 0,
        val spacingPx: Int = 12,
        val marginPx: Int = 12,
        val backgroundColor: Int = Color.WHITE,
        val aspect: CollageAspect = CollageAspect.SQUARE,
        val outputFormat: OutputFormat = OutputFormat.JPEG,
        val previewBitmap: Bitmap? = null,
        val isRenderingPreview: Boolean = false
    ) : CollageMakerUiState

    data class Processing(val message: String) : CollageMakerUiState

    data class Success(
        val contentUri: Uri,
        val displayName: String,
        val outputWidth: Int,
        val outputHeight: Int,
        val fileSizeBytes: Long,
        val format: OutputFormat,
        val resultBitmap: Bitmap
    ) : CollageMakerUiState

    data class Error(val message: String) : CollageMakerUiState
}

class CollageMakerViewModel(application: Application) : AndroidViewModel(application) {

    constructor(context: Context) : this(context.applicationContext as Application)

    private val _uiState = MutableStateFlow<CollageMakerUiState>(CollageMakerUiState.Idle)
    val uiState: StateFlow<CollageMakerUiState> = _uiState.asStateFlow()

    fun selectImages(uris: List<Uri>) {
        if (uris.size < 2) {
            _uiState.value = CollageMakerUiState.Error("Please select at least 2 images to create a collage.")
            return
        }

        viewModelScope.launch {
            _uiState.value = CollageMakerUiState.Processing("Loading and safely downsampling ${uris.size} photos...")

            val decodedList = mutableListOf<Bitmap>()
            for (uri in uris.take(9)) {
                // Keep maxDimension 1024 to prevent OOM across up to 9 photos
                val bmp = ImageBitmapHelper.decodeSafeBitmap(getApplication(), uri, maxDimension = 1024)
                if (bmp != null) {
                    decodedList.add(bmp)
                }
            }

            if (decodedList.size < 2) {
                _uiState.value = CollageMakerUiState.Error("Could not decode enough valid images (minimum 2).")
                return@launch
            }

            val initialState = CollageMakerUiState.Loaded(sourceBitmaps = decodedList)
            _uiState.value = initialState
            updatePreview(initialState)
        }
    }

    fun setLayoutVariant(variant: Int) {
        val current = _uiState.value as? CollageMakerUiState.Loaded ?: return
        val updated = current.copy(layoutVariant = variant)
        _uiState.value = updated
        updatePreview(updated)
    }

    fun setSpacing(spacing: Int) {
        val current = _uiState.value as? CollageMakerUiState.Loaded ?: return
        val updated = current.copy(spacingPx = spacing.coerceIn(0, 48))
        _uiState.value = updated
        updatePreview(updated)
    }

    fun setMargin(margin: Int) {
        val current = _uiState.value as? CollageMakerUiState.Loaded ?: return
        val updated = current.copy(marginPx = margin.coerceIn(0, 48))
        _uiState.value = updated
        updatePreview(updated)
    }

    fun setBackgroundColor(color: Int) {
        val current = _uiState.value as? CollageMakerUiState.Loaded ?: return
        val updated = current.copy(backgroundColor = color)
        _uiState.value = updated
        updatePreview(updated)
    }

    fun setAspect(aspect: CollageAspect) {
        val current = _uiState.value as? CollageMakerUiState.Loaded ?: return
        val updated = current.copy(aspect = aspect)
        _uiState.value = updated
        updatePreview(updated)
    }

    fun setOutputFormat(format: OutputFormat) {
        val current = _uiState.value as? CollageMakerUiState.Loaded ?: return
        _uiState.update { current.copy(outputFormat = format) }
    }

    private fun updatePreview(state: CollageMakerUiState.Loaded) {
        viewModelScope.launch {
            _uiState.update { if (it is CollageMakerUiState.Loaded) it.copy(isRenderingPreview = true) else it }

            val preview = withContext(Dispatchers.Default) {
                // Downscaled target for smooth interactive preview
                val previewMaxDim = 600
                val aspect = state.aspect.width.toFloat() / state.aspect.height.toFloat()
                val prevW: Int
                val prevH: Int
                if (aspect >= 1f) {
                    prevW = previewMaxDim
                    prevH = (previewMaxDim / aspect).toInt().coerceAtLeast(1)
                } else {
                    prevH = previewMaxDim
                    prevW = (previewMaxDim * aspect).toInt().coerceAtLeast(1)
                }

                val previewSpacing = (state.spacingPx * (prevW.toFloat() / state.aspect.width)).toInt()
                val previewMargin = (state.marginPx * (prevW.toFloat() / state.aspect.width)).toInt()

                ImageBitmapHelper.composeCollage(
                    bitmaps = state.sourceBitmaps,
                    targetWidth = prevW,
                    targetHeight = prevH,
                    layoutVariant = state.layoutVariant,
                    spacingPx = previewSpacing,
                    marginPx = previewMargin,
                    backgroundColor = state.backgroundColor
                )
            }

            _uiState.update {
                if (it is CollageMakerUiState.Loaded) {
                    it.copy(previewBitmap = preview, isRenderingPreview = false)
                } else it
            }
        }
    }

    fun exportCollage() {
        val current = _uiState.value as? CollageMakerUiState.Loaded ?: return

        viewModelScope.launch {
            _uiState.value = CollageMakerUiState.Processing("Rendering high-res collage...")

            val composed = withContext(Dispatchers.Default) {
                ImageBitmapHelper.composeCollage(
                    bitmaps = current.sourceBitmaps,
                    targetWidth = current.aspect.width,
                    targetHeight = current.aspect.height,
                    layoutVariant = current.layoutVariant,
                    spacingPx = current.spacingPx,
                    marginPx = current.marginPx,
                    backgroundColor = current.backgroundColor
                )
            }

            _uiState.value = CollageMakerUiState.Processing("Saving to Pictures/PixelRox...")

            val publishResult = ImageBitmapHelper.saveAndPublish(
                context = getApplication(),
                bitmap = composed,
                format = current.outputFormat,
                baseName = "collage_${current.sourceBitmaps.size}pics"
            )

            when (publishResult) {
                is ImagePublishResult.Success -> {
                    _uiState.value = CollageMakerUiState.Success(
                        contentUri = publishResult.contentUri,
                        displayName = publishResult.displayName,
                        outputWidth = current.aspect.width,
                        outputHeight = current.aspect.height,
                        fileSizeBytes = publishResult.fileSizeBytes,
                        format = current.outputFormat,
                        resultBitmap = composed
                    )
                }
                is ImagePublishResult.Failure -> {
                    _uiState.value = CollageMakerUiState.Error("Export failed: ${publishResult.errorMessage}")
                }
            }
        }
    }

    fun reset() {
        _uiState.value = CollageMakerUiState.Idle
    }
}
