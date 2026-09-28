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

enum class QuoteCanvasFormat(val label: String, val width: Int, val height: Int) {
    SQUARE("Square (1:1)", 1080, 1080),
    PORTRAIT("Portrait (4:5)", 1080, 1350),
    STORY("Story (9:16)", 1080, 1920),
    LANDSCAPE("Landscape (16:9)", 1280, 720)
}

data class PostQuoteUiState(
    val quoteText: String = "The secret of getting ahead is getting started.",
    val authorText: String = "Mark Twain",
    val canvasFormat: QuoteCanvasFormat = QuoteCanvasFormat.SQUARE,
    val backgroundColor: Int = Color.rgb(20, 24, 34), // Dark Navy
    val backgroundImage: Bitmap? = null,
    val scrimOpacity: Float = 0.4f,
    val quoteTextSizeSp: Float = 38f,
    val quoteTextColor: Int = Color.WHITE,
    val authorTextSizeSp: Float = 22f,
    val authorTextColor: Int = Color.LTGRAY,
    val alignment: ImageBitmapHelper.TextAlignmentOption = ImageBitmapHelper.TextAlignmentOption.CENTER,
    val verticalPosition: ImageBitmapHelper.VerticalPositionOption = ImageBitmapHelper.VerticalPositionOption.CENTER,
    val hasShadow: Boolean = true,
    val outputFormat: OutputFormat = OutputFormat.JPEG,
    val previewBitmap: Bitmap? = null,
    val isRenderingPreview: Boolean = false,
    val isProcessingExport: Boolean = false,
    val exportProgressMessage: String = "",
    val successResult: ExportSuccessData? = null,
    val errorMessage: String? = null
) {
    data class ExportSuccessData(
        val contentUri: Uri,
        val displayName: String,
        val outputWidth: Int,
        val outputHeight: Int,
        val fileSizeBytes: Long,
        val format: OutputFormat,
        val resultBitmap: Bitmap
    )
}

class PostQuoteMakerViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(PostQuoteUiState())
    val uiState: StateFlow<PostQuoteUiState> = _uiState.asStateFlow()

    init {
        updatePreview(_uiState.value)
    }

    fun setQuoteText(text: String) {
        val updated = _uiState.value.copy(quoteText = text)
        _uiState.value = updated
        updatePreview(updated)
    }

    fun setAuthorText(author: String) {
        val updated = _uiState.value.copy(authorText = author)
        _uiState.value = updated
        updatePreview(updated)
    }

    fun setCanvasFormat(format: QuoteCanvasFormat) {
        val updated = _uiState.value.copy(canvasFormat = format)
        _uiState.value = updated
        updatePreview(updated)
    }

    fun setBackgroundColor(color: Int) {
        val updated = _uiState.value.copy(backgroundColor = color, backgroundImage = null)
        _uiState.value = updated
        updatePreview(updated)
    }

    fun setBackgroundImage(uri: Uri) {
        viewModelScope.launch {
            val bmp = ImageBitmapHelper.decodeSafeBitmap(getApplication(), uri, maxDimension = 1920)
            if (bmp != null) {
                val updated = _uiState.value.copy(backgroundImage = bmp)
                _uiState.value = updated
                updatePreview(updated)
            }
        }
    }

    fun clearBackgroundImage() {
        val updated = _uiState.value.copy(backgroundImage = null)
        _uiState.value = updated
        updatePreview(updated)
    }

    fun setScrimOpacity(opacity: Float) {
        val updated = _uiState.value.copy(scrimOpacity = opacity.coerceIn(0f, 0.9f))
        _uiState.value = updated
        updatePreview(updated)
    }

    fun setQuoteTextSize(sizeSp: Float) {
        val updated = _uiState.value.copy(quoteTextSizeSp = sizeSp.coerceIn(20f, 64f))
        _uiState.value = updated
        updatePreview(updated)
    }

    fun setQuoteTextColor(color: Int) {
        val updated = _uiState.value.copy(quoteTextColor = color)
        _uiState.value = updated
        updatePreview(updated)
    }

    fun setAlignment(align: ImageBitmapHelper.TextAlignmentOption) {
        val updated = _uiState.value.copy(alignment = align)
        _uiState.value = updated
        updatePreview(updated)
    }

    fun setVerticalPosition(pos: ImageBitmapHelper.VerticalPositionOption) {
        val updated = _uiState.value.copy(verticalPosition = pos)
        _uiState.value = updated
        updatePreview(updated)
    }

    fun setShadow(enabled: Boolean) {
        val updated = _uiState.value.copy(hasShadow = enabled)
        _uiState.value = updated
        updatePreview(updated)
    }

    fun setOutputFormat(format: OutputFormat) {
        _uiState.update { it.copy(outputFormat = format) }
    }

    private fun updatePreview(state: PostQuoteUiState) {
        viewModelScope.launch {
            _uiState.update { it.copy(isRenderingPreview = true) }

            val preview = withContext(Dispatchers.Default) {
                val previewMaxDim = 600
                val aspect = state.canvasFormat.width.toFloat() / state.canvasFormat.height.toFloat()
                val prevW: Int
                val prevH: Int
                if (aspect >= 1f) {
                    prevW = previewMaxDim
                    prevH = (previewMaxDim / aspect).toInt().coerceAtLeast(1)
                } else {
                    prevH = previewMaxDim
                    prevW = (previewMaxDim * aspect).toInt().coerceAtLeast(1)
                }

                val previewQuoteSize = state.quoteTextSizeSp * (prevW.toFloat() / state.canvasFormat.width).coerceAtLeast(0.4f)
                val previewAuthorSize = state.authorTextSizeSp * (prevW.toFloat() / state.canvasFormat.width).coerceAtLeast(0.4f)

                ImageBitmapHelper.renderPostQuote(
                    targetWidth = prevW,
                    targetHeight = prevH,
                    quoteText = state.quoteText.ifBlank { " " },
                    authorText = state.authorText,
                    backgroundColor = state.backgroundColor,
                    backgroundImage = state.backgroundImage,
                    scrimOpacity = state.scrimOpacity,
                    quoteTextSizeSp = previewQuoteSize,
                    quoteTextColor = state.quoteTextColor,
                    authorTextSizeSp = previewAuthorSize,
                    authorTextColor = state.authorTextColor,
                    alignment = state.alignment,
                    verticalPos = state.verticalPosition,
                    hasShadow = state.hasShadow
                )
            }

            _uiState.update { it.copy(previewBitmap = preview, isRenderingPreview = false) }
        }
    }

    fun exportGraphic() {
        val current = _uiState.value

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isProcessingExport = true,
                    exportProgressMessage = "Rendering full-resolution graphic..."
                )
            }

            val rendered = withContext(Dispatchers.Default) {
                ImageBitmapHelper.renderPostQuote(
                    targetWidth = current.canvasFormat.width,
                    targetHeight = current.canvasFormat.height,
                    quoteText = current.quoteText.ifBlank { " " },
                    authorText = current.authorText,
                    backgroundColor = current.backgroundColor,
                    backgroundImage = current.backgroundImage,
                    scrimOpacity = current.scrimOpacity,
                    quoteTextSizeSp = current.quoteTextSizeSp,
                    quoteTextColor = current.quoteTextColor,
                    authorTextSizeSp = current.authorTextSizeSp,
                    authorTextColor = current.authorTextColor,
                    alignment = current.alignment,
                    verticalPos = current.verticalPosition,
                    hasShadow = current.hasShadow
                )
            }

            _uiState.update { it.copy(exportProgressMessage = "Saving to Pictures/PixelRox...") }

            val publishResult = ImageBitmapHelper.saveAndPublish(
                context = getApplication(),
                bitmap = rendered,
                format = current.outputFormat,
                baseName = "quote_${current.canvasFormat.label.take(6).lowercase().trim()}"
            )

            when (publishResult) {
                is ImagePublishResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isProcessingExport = false,
                            successResult = PostQuoteUiState.ExportSuccessData(
                                contentUri = publishResult.contentUri,
                                displayName = publishResult.displayName,
                                outputWidth = current.canvasFormat.width,
                                outputHeight = current.canvasFormat.height,
                                fileSizeBytes = publishResult.fileSizeBytes,
                                format = current.outputFormat,
                                resultBitmap = rendered
                            )
                        )
                    }
                }
                is ImagePublishResult.Failure -> {
                    _uiState.update {
                        it.copy(
                            isProcessingExport = false,
                            errorMessage = "Export failed: ${publishResult.errorMessage}"
                        )
                    }
                }
            }
        }
    }

    fun dismissSuccess() {
        _uiState.update { it.copy(successResult = null) }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
