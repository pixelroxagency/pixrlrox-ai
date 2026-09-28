package com.example.ui.screens.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.image.ImagePublishResult
import com.example.core.image.OutputFormat
import com.example.data.util.ImageBitmapHelper
import com.example.data.util.ImageTextRecognizer
import com.example.data.util.RecognizedTextRegion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class TextEditorUiState(
    val isLoading: Boolean = false,
    val isOcrRunning: Boolean = false,
    val isExporting: Boolean = false,
    val selectedRegionId: String? = null,
    val replacementText: String = "",
    val replacementTextSize: Float = 24f,
    val replacementTextColor: Int = Color.BLACK,
    val regions: List<RecognizedTextRegion> = emptyList(),
    val undoAvailable: Boolean = false,
    val exportResult: ImagePublishResult.Success? = null,
    val statusMessage: String? = null,
    val errorMessage: String? = null
)

class ImageTextEditorViewModel(
    private val recognizer: ImageTextRecognizer
) : ViewModel() {

    private val _uiState = MutableStateFlow(TextEditorUiState())
    val uiState: StateFlow<TextEditorUiState> = _uiState.asStateFlow()

    var originalBitmap: Bitmap? = null
        private set

    var currentBitmap: Bitmap? = null
        private set

    private val undoStack = mutableListOf<Bitmap>()

    fun loadImageAndScanOcr(context: Context, uri: Uri) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                isOcrRunning = true,
                errorMessage = null,
                statusMessage = null,
                exportResult = null
            )

            // 1. Decode bitmap safely
            val bmp = ImageBitmapHelper.decodeSafeBitmap(context, uri)
            if (bmp == null) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isOcrRunning = false,
                    errorMessage = "Failed to load image from source."
                )
                return@launch
            }

            originalBitmap = bmp
            currentBitmap = bmp.copy(Bitmap.Config.ARGB_8888, true)
            undoStack.clear()

            _uiState.value = _uiState.value.copy(isLoading = false)

            // 2. Perform OCR on image to detect text bounding regions
            try {
                val ocrResult = recognizer.recognizeStructuredText(uri)
                _uiState.value = _uiState.value.copy(
                    isOcrRunning = false,
                    regions = ocrResult.regions,
                    selectedRegionId = null,
                    undoAvailable = false,
                    statusMessage = if (ocrResult.regions.isEmpty()) {
                        "No text regions detected in image."
                    } else {
                        "Detected ${ocrResult.regions.size} text region(s). Tap any region to edit or remove."
                    }
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isOcrRunning = false,
                    errorMessage = "OCR scanning failed: ${e.message}"
                )
            }
        }
    }

    fun selectRegion(regionId: String?) {
        val region = _uiState.value.regions.firstOrNull { it.id == regionId }
        _uiState.value = _uiState.value.copy(
            selectedRegionId = regionId,
            replacementText = region?.text ?: ""
        )
    }

    fun updateReplacementText(text: String) {
        _uiState.value = _uiState.value.copy(replacementText = text)
    }

    fun updateReplacementTextSize(size: Float) {
        _uiState.value = _uiState.value.copy(replacementTextSize = size.coerceIn(12f, 72f))
    }

    fun updateReplacementTextColor(color: Int) {
        _uiState.value = _uiState.value.copy(replacementTextColor = color)
    }

    fun removeSelectedText() {
        val selectedId = _uiState.value.selectedRegionId ?: return
        val region = _uiState.value.regions.firstOrNull { it.id == selectedId } ?: return
        val bmp = currentBitmap ?: return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            // Save copy to undo stack
            undoStack.add(bmp.copy(Bitmap.Config.ARGB_8888, true))

            val modified = withContext(Dispatchers.Default) {
                ImageBitmapHelper.removeTextRegion(bmp, region.boundingBox)
            }

            currentBitmap = modified

            // Remove the processed region from active selectable regions
            val updatedRegions = _uiState.value.regions.filterNot { it.id == selectedId }

            _uiState.value = _uiState.value.copy(
                isLoading = false,
                regions = updatedRegions,
                selectedRegionId = null,
                replacementText = "",
                undoAvailable = undoStack.isNotEmpty(),
                statusMessage = "Text removed. Border pixels blended into region."
            )
        }
    }

    fun replaceSelectedText() {
        val selectedId = _uiState.value.selectedRegionId ?: return
        val region = _uiState.value.regions.firstOrNull { it.id == selectedId } ?: return
        val newText = _uiState.value.replacementText.trim()
        val bmp = currentBitmap ?: return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            // Save copy to undo stack
            undoStack.add(bmp.copy(Bitmap.Config.ARGB_8888, true))

            val modified = withContext(Dispatchers.Default) {
                // 1. Remove original text
                val cleaned = ImageBitmapHelper.removeTextRegion(bmp, region.boundingBox)
                // 2. Draw replacement text if not blank
                if (newText.isNotBlank()) {
                    ImageBitmapHelper.addReplacementText(
                        source = cleaned,
                        box = region.boundingBox,
                        text = newText,
                        textSizeSp = _uiState.value.replacementTextSize,
                        textColor = _uiState.value.replacementTextColor
                    )
                } else {
                    cleaned
                }
            }

            currentBitmap = modified

            // Update regions: replace text in region or remove
            val updatedRegions = _uiState.value.regions.map {
                if (it.id == selectedId) it.copy(text = newText) else it
            }

            _uiState.value = _uiState.value.copy(
                isLoading = false,
                regions = updatedRegions,
                selectedRegionId = null,
                undoAvailable = undoStack.isNotEmpty(),
                statusMessage = "Text replaced successfully."
            )
        }
    }

    fun undoLastEdit() {
        if (undoStack.isEmpty()) return
        val previous = undoStack.removeAt(undoStack.lastIndex)
        currentBitmap = previous
        _uiState.value = _uiState.value.copy(
            selectedRegionId = null,
            undoAvailable = undoStack.isNotEmpty(),
            statusMessage = "Last edit undone."
        )
    }

    fun resetToOriginal() {
        val orig = originalBitmap ?: return
        currentBitmap = orig.copy(Bitmap.Config.ARGB_8888, true)
        undoStack.clear()
        _uiState.value = _uiState.value.copy(
            selectedRegionId = null,
            undoAvailable = false,
            statusMessage = "Reset to original image."
        )
    }

    fun exportImage(context: Context) {
        val bmp = currentBitmap ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isExporting = true, errorMessage = null)
            val result = ImageBitmapHelper.saveAndPublish(
                context = context,
                bitmap = bmp,
                format = OutputFormat.PNG,
                quality = 95,
                baseName = "edited_text_image"
            )

            when (result) {
                is ImagePublishResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isExporting = false,
                        exportResult = result,
                        statusMessage = "Saved edited image to Pictures/PixelRox"
                    )
                }
                is ImagePublishResult.Failure -> {
                    _uiState.value = _uiState.value.copy(
                        isExporting = false,
                        errorMessage = result.errorMessage
                    )
                }
            }
        }
    }

    fun clearExportResult() {
        _uiState.value = _uiState.value.copy(exportResult = null)
    }

    fun reset() {
        originalBitmap = null
        currentBitmap = null
        undoStack.clear()
        _uiState.value = TextEditorUiState()
    }
}
