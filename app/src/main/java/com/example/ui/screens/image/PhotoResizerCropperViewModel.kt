package com.example.ui.screens.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.image.ImagePublishResult
import com.example.core.image.OutputFormat
import com.example.data.util.ImageBitmapHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class CropAspectRatio(val title: String, val ratio: Float?) {
    FREE("Free", null),
    ORIGINAL("Original", null),
    SQUARE_1_1("1:1", 1.0f),
    PORTRAIT_4_5("4:5", 4f / 5f),
    VERTICAL_9_16("9:16", 9f / 16f),
    LANDSCAPE_16_9("16:9", 16f / 9f)
}

data class ResizerUiState(
    val isLoading: Boolean = false,
    val isProcessing: Boolean = false,
    val originalWidth: Int = 0,
    val originalHeight: Int = 0,
    val cropRatio: CropAspectRatio = CropAspectRatio.FREE,
    val cropRectNorm: RectF = RectF(0f, 0f, 1f, 1f),
    val croppedWidth: Int = 0,
    val croppedHeight: Int = 0,
    val targetWidth: Int = 0,
    val targetHeight: Int = 0,
    val lockAspectRatio: Boolean = true,
    val noUpscale: Boolean = true,
    val outputFormat: OutputFormat = OutputFormat.JPEG,
    val quality: Int = 85,
    val exportResult: ImagePublishResult.Success? = null,
    val errorMessage: String? = null
)

class PhotoResizerCropperViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(ResizerUiState())
    val uiState: StateFlow<ResizerUiState> = _uiState.asStateFlow()

    var loadedBitmap: Bitmap? = null
        private set

    var processedBitmap: Bitmap? = null
        private set

    fun loadImage(context: Context, uri: Uri) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val bmp = ImageBitmapHelper.decodeSafeBitmap(context, uri)
            if (bmp != null) {
                loadedBitmap = bmp
                processedBitmap = null
                val w = bmp.width
                val h = bmp.height
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    originalWidth = w,
                    originalHeight = h,
                    cropRatio = CropAspectRatio.FREE,
                    cropRectNorm = RectF(0f, 0f, 1f, 1f),
                    croppedWidth = w,
                    croppedHeight = h,
                    targetWidth = w,
                    targetHeight = h,
                    exportResult = null,
                    errorMessage = null
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Failed to load image from selected source."
                )
            }
        }
    }

    fun setCropRatio(aspect: CropAspectRatio) {
        val state = _uiState.value
        val bmp = loadedBitmap ?: return
        val imgAspect = bmp.width.toFloat() / bmp.height.toFloat()

        val targetRatio = when (aspect) {
            CropAspectRatio.FREE -> null
            CropAspectRatio.ORIGINAL -> imgAspect
            else -> aspect.ratio
        }

        val newRect = if (targetRatio == null) {
            RectF(0f, 0f, 1f, 1f)
        } else {
            computeCenteredCrop(imgAspect, targetRatio)
        }

        val cW = max(1, ((newRect.right - newRect.left) * bmp.width).roundToInt())
        val cH = max(1, ((newRect.bottom - newRect.top) * bmp.height).roundToInt())

        val (tW, tH) = if (state.noUpscale) {
            Pair(min(state.targetWidth.takeIf { it > 0 } ?: cW, cW), min(state.targetHeight.takeIf { it > 0 } ?: cH, cH))
        } else {
            Pair(cW, cH)
        }

        _uiState.value = state.copy(
            cropRatio = aspect,
            cropRectNorm = newRect,
            croppedWidth = cW,
            croppedHeight = cH,
            targetWidth = tW,
            targetHeight = tH
        )
    }

    private fun computeCenteredCrop(imageAspect: Float, targetAspect: Float): RectF {
        return if (targetAspect > imageAspect) {
            // Target is wider than image: take full width, center height
            val normHeight = imageAspect / targetAspect
            val top = (1f - normHeight) / 2f
            RectF(0f, top, 1f, top + normHeight)
        } else {
            // Target is taller than image: take full height, center width
            val normWidth = targetAspect / imageAspect
            val left = (1f - normWidth) / 2f
            RectF(left, 0f, left + normWidth, 1f)
        }
    }

    fun updateCropRectNorm(left: Float, top: Float, right: Float, bottom: Float) {
        val bmp = loadedBitmap ?: return
        val clampedL = max(0f, min(left, right - 0.05f))
        val clampedT = max(0f, min(top, bottom - 0.05f))
        val clampedR = min(1f, max(right, clampedL + 0.05f))
        val clampedB = min(1f, max(bottom, clampedT + 0.05f))

        val newRect = RectF(clampedL, clampedT, clampedR, clampedB)
        val cW = max(1, ((newRect.right - newRect.left) * bmp.width).roundToInt())
        val cH = max(1, ((newRect.bottom - newRect.top) * bmp.height).roundToInt())

        val state = _uiState.value
        val (tW, tH) = if (state.lockAspectRatio) {
            val aspect = cW.toFloat() / cH.toFloat()
            val newW = if (state.noUpscale) min(state.targetWidth, cW) else state.targetWidth
            val newH = max(1, (newW / aspect).roundToInt())
            Pair(newW, if (state.noUpscale) min(newH, cH) else newH)
        } else {
            Pair(
                if (state.noUpscale) min(state.targetWidth, cW) else state.targetWidth,
                if (state.noUpscale) min(state.targetHeight, cH) else state.targetHeight
            )
        }

        _uiState.value = state.copy(
            cropRectNorm = newRect,
            croppedWidth = cW,
            croppedHeight = cH,
            targetWidth = tW,
            targetHeight = tH
        )
    }

    fun updateTargetWidth(widthStr: String) {
        val state = _uiState.value
        val parsed = widthStr.toIntOrNull() ?: 0
        if (parsed <= 0) {
            _uiState.value = state.copy(targetWidth = 0)
            return
        }

        val effectiveW = if (state.noUpscale) min(parsed, state.croppedWidth) else parsed
        val effectiveH = if (state.lockAspectRatio && state.croppedWidth > 0 && state.croppedHeight > 0) {
            val ratio = state.croppedWidth.toFloat() / state.croppedHeight.toFloat()
            val computedH = max(1, (effectiveW / ratio).roundToInt())
            if (state.noUpscale) min(computedH, state.croppedHeight) else computedH
        } else {
            state.targetHeight
        }

        _uiState.value = state.copy(
            targetWidth = effectiveW,
            targetHeight = effectiveH
        )
    }

    fun updateTargetHeight(heightStr: String) {
        val state = _uiState.value
        val parsed = heightStr.toIntOrNull() ?: 0
        if (parsed <= 0) {
            _uiState.value = state.copy(targetHeight = 0)
            return
        }

        val effectiveH = if (state.noUpscale) min(parsed, state.croppedHeight) else parsed
        val effectiveW = if (state.lockAspectRatio && state.croppedWidth > 0 && state.croppedHeight > 0) {
            val ratio = state.croppedWidth.toFloat() / state.croppedHeight.toFloat()
            val computedW = max(1, (effectiveH * ratio).roundToInt())
            if (state.noUpscale) min(computedW, state.croppedWidth) else computedW
        } else {
            state.targetWidth
        }

        _uiState.value = state.copy(
            targetWidth = effectiveW,
            targetHeight = effectiveH
        )
    }

    fun setLockAspectRatio(lock: Boolean) {
        _uiState.value = _uiState.value.copy(lockAspectRatio = lock)
        if (lock && _uiState.value.croppedWidth > 0 && _uiState.value.croppedHeight > 0) {
            updateTargetWidth(_uiState.value.targetWidth.toString())
        }
    }

    fun setNoUpscale(noUpscale: Boolean) {
        _uiState.value = _uiState.value.copy(noUpscale = noUpscale)
        if (noUpscale) {
            val state = _uiState.value
            val clampedW = min(state.targetWidth, state.croppedWidth)
            val clampedH = min(state.targetHeight, state.croppedHeight)
            _uiState.value = state.copy(targetWidth = clampedW, targetHeight = clampedH)
        }
    }

    fun setOutputFormat(format: OutputFormat) {
        _uiState.value = _uiState.value.copy(outputFormat = format)
    }

    fun setQuality(quality: Int) {
        _uiState.value = _uiState.value.copy(quality = quality.coerceIn(1, 100))
    }

    fun applyCropAndResize() {
        val bmp = loadedBitmap ?: return
        val state = _uiState.value
        if (state.targetWidth <= 0 || state.targetHeight <= 0) {
            _uiState.value = state.copy(errorMessage = "Width and Height must be greater than zero.")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isProcessing = true, errorMessage = null)
            try {
                val processed = withContext(Dispatchers.Default) {
                    val cropL = (state.cropRectNorm.left * bmp.width).roundToInt()
                    val cropT = (state.cropRectNorm.top * bmp.height).roundToInt()
                    val cropW = max(1, ((state.cropRectNorm.right - state.cropRectNorm.left) * bmp.width).roundToInt())
                    val cropH = max(1, ((state.cropRectNorm.bottom - state.cropRectNorm.top) * bmp.height).roundToInt())

                    val cropped = ImageBitmapHelper.cropBitmap(bmp, cropL, cropT, cropW, cropH)
                    val resized = if (cropped.width != state.targetWidth || cropped.height != state.targetHeight) {
                        ImageBitmapHelper.resizeBitmap(cropped, state.targetWidth, state.targetHeight)
                    } else {
                        cropped
                    }
                    resized
                }

                processedBitmap = processed
                _uiState.value = _uiState.value.copy(isProcessing = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isProcessing = false,
                    errorMessage = "Processing failed: ${e.message}"
                )
            }
        }
    }

    fun exportProcessedImage(context: Context) {
        val processed = processedBitmap ?: return
        val state = _uiState.value

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isProcessing = true, errorMessage = null)
            val result = ImageBitmapHelper.saveAndPublish(
                context = context,
                bitmap = processed,
                format = state.outputFormat,
                quality = state.quality,
                baseName = "photo_edit"
            )

            when (result) {
                is ImagePublishResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isProcessing = false,
                        exportResult = result
                    )
                }
                is ImagePublishResult.Failure -> {
                    _uiState.value = _uiState.value.copy(
                        isProcessing = false,
                        errorMessage = result.errorMessage
                    )
                }
            }
        }
    }

    fun clearProcessed() {
        processedBitmap = null
        _uiState.value = _uiState.value.copy(exportResult = null)
    }

    fun reset() {
        loadedBitmap = null
        processedBitmap = null
        _uiState.value = ResizerUiState()
    }
}
