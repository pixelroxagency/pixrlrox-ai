package com.example.ui.screens.pdf

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.pdf.ImageScaleMode
import com.example.core.pdf.ImageToPdfConfig
import com.example.core.pdf.ImageToPdfEngine
import com.example.core.pdf.PageMargin
import com.example.core.pdf.PdfOutputPublisher
import com.example.core.pdf.PdfPageSize
import com.example.core.pdf.PdfPublishResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SelectedImageItem(
    val uri: Uri,
    val name: String
)

data class ImageToPdfUiState(
    val selectedImages: List<SelectedImageItem> = emptyList(),
    val pageSize: PdfPageSize = PdfPageSize.A4,
    val scaleMode: ImageScaleMode = ImageScaleMode.FIT,
    val margin: PageMargin = PageMargin.SMALL,
    val isProcessing: Boolean = false,
    val progressCurrent: Int = 0,
    val progressTotal: Int = 0,
    val statusMessage: String? = null,
    val result: PdfPublishResult.Success? = null,
    val errorMessage: String? = null
)

class ImageToPdfViewModel(application: Application) : AndroidViewModel(application) {

    private val engine = ImageToPdfEngine(application)
    private val publisher = PdfOutputPublisher(application)

    private val _uiState = MutableStateFlow(ImageToPdfUiState())
    val uiState: StateFlow<ImageToPdfUiState> = _uiState.asStateFlow()

    fun addImages(uris: List<Uri>) {
        val newItems = uris.mapIndexed { i, uri ->
            val name = uri.lastPathSegment ?: "Image_${_uiState.value.selectedImages.size + i + 1}"
            SelectedImageItem(uri, name)
        }
        _uiState.update {
            it.copy(
                selectedImages = it.selectedImages + newItems,
                result = null,
                errorMessage = null
            )
        }
    }

    fun removeImage(index: Int) {
        if (index in _uiState.value.selectedImages.indices) {
            val updated = _uiState.value.selectedImages.toMutableList().apply { removeAt(index) }
            _uiState.update { it.copy(selectedImages = updated) }
        }
    }

    fun moveImage(fromIndex: Int, toIndex: Int) {
        val list = _uiState.value.selectedImages.toMutableList()
        if (fromIndex in list.indices && toIndex in list.indices) {
            val item = list.removeAt(fromIndex)
            list.add(toIndex, item)
            _uiState.update { it.copy(selectedImages = list) }
        }
    }

    fun setPageSize(size: PdfPageSize) {
        _uiState.update { it.copy(pageSize = size) }
    }

    fun setScaleMode(mode: ImageScaleMode) {
        _uiState.update { it.copy(scaleMode = mode) }
    }

    fun setMargin(margin: PageMargin) {
        _uiState.update { it.copy(margin = margin) }
    }

    fun clear() {
        _uiState.value = ImageToPdfUiState()
    }

    fun convertToPdf(desiredName: String = "ImagesDocument") {
        val images = _uiState.value.selectedImages
        if (images.isEmpty()) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isProcessing = true,
                    progressCurrent = 0,
                    progressTotal = images.size,
                    statusMessage = "Converting ${images.size} images to PDF...",
                    errorMessage = null,
                    result = null
                )
            }

            try {
                val config = ImageToPdfConfig(
                    pageSize = _uiState.value.pageSize,
                    scaleMode = _uiState.value.scaleMode,
                    margin = _uiState.value.margin
                )

                val tempPdfFile = engine.convertImagesToPdf(
                    imageUris = images.map { it.uri },
                    config = config,
                    onProgress = { cur, total ->
                        _uiState.update { it.copy(progressCurrent = cur, progressTotal = total) }
                    }
                )

                _uiState.update { it.copy(statusMessage = "Saving to Documents/PixelRox...") }
                val pubResult = publisher.publishPdf(tempPdfFile, desiredName)
                tempPdfFile.delete()

                when (pubResult) {
                    is PdfPublishResult.Success -> {
                        _uiState.update {
                            it.copy(
                                isProcessing = false,
                                statusMessage = null,
                                result = pubResult
                            )
                        }
                    }
                    is PdfPublishResult.Failure -> {
                        _uiState.update {
                            it.copy(
                                isProcessing = false,
                                statusMessage = null,
                                errorMessage = pubResult.errorMessage
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        statusMessage = null,
                        errorMessage = e.message ?: "Failed to generate PDF"
                    )
                }
            }
        }
    }
}
