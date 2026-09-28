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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class BatchWatermarkItem(
    val uri: Uri,
    val fileName: String,
    val status: BatchItemStatus = BatchItemStatus.PENDING,
    val error: String? = null,
    val resultUri: Uri? = null
)

sealed interface BatchWatermarkUiState {
    data object Idle : BatchWatermarkUiState

    data class Ready(
        val items: List<BatchWatermarkItem>,
        val watermarkType: WatermarkType = WatermarkType.TEXT,
        // Text watermark
        val text: String = "PixelRox",
        val textSizeSp: Float = 36f,
        val textColor: Int = Color.WHITE,
        // Logo watermark
        val logoUri: Uri? = null,
        val logoBitmap: Bitmap? = null,
        val imageScale: Float = 0.4f,
        // Position & styling
        val opacity: Float = 0.8f,
        val normX: Float = 0.7f,
        val normY: Float = 0.9f,
        val outputFormat: OutputFormat = OutputFormat.JPEG,
        val quality: Int = 90
    ) : BatchWatermarkUiState

    data class Processing(
        val items: List<BatchWatermarkItem>,
        val currentIndex: Int,
        val totalCount: Int,
        val successCount: Int,
        val failedCount: Int
    ) : BatchWatermarkUiState

    data class Completed(
        val items: List<BatchWatermarkItem>,
        val successCount: Int,
        val failedCount: Int
    ) : BatchWatermarkUiState
}

class BatchWatermarkViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow<BatchWatermarkUiState>(BatchWatermarkUiState.Idle)
    val uiState: StateFlow<BatchWatermarkUiState> = _uiState.asStateFlow()

    private var processJob: Job? = null

    fun selectImages(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val context = getApplication<Application>()

        val items = uris.mapIndexed { index, uri ->
            val name = try {
                var dispName: String? = null
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1) dispName = cursor.getString(nameIndex)
                    }
                }
                dispName ?: "Image ${index + 1}"
            } catch (_: Exception) {
                "Image ${index + 1}"
            }
            BatchWatermarkItem(uri = uri, fileName = name)
        }

        _uiState.value = BatchWatermarkUiState.Ready(items = items)
    }

    fun setWatermarkType(type: WatermarkType) {
        val current = _uiState.value as? BatchWatermarkUiState.Ready ?: return
        _uiState.update { current.copy(watermarkType = type) }
    }

    fun setText(text: String) {
        val current = _uiState.value as? BatchWatermarkUiState.Ready ?: return
        _uiState.update { current.copy(text = text) }
    }

    fun setTextSizeSp(size: Float) {
        val current = _uiState.value as? BatchWatermarkUiState.Ready ?: return
        _uiState.update { current.copy(textSizeSp = size.coerceIn(12f, 96f)) }
    }

    fun setTextColor(color: Int) {
        val current = _uiState.value as? BatchWatermarkUiState.Ready ?: return
        _uiState.update { current.copy(textColor = color) }
    }

    fun setLogoUri(uri: Uri) {
        val current = _uiState.value as? BatchWatermarkUiState.Ready ?: return
        val context = getApplication<Application>()
        viewModelScope.launch {
            val logoBmp = ImageBitmapHelper.decodeSafeBitmap(context, uri, maxDimension = 512)
            _uiState.update { current.copy(logoUri = uri, logoBitmap = logoBmp, watermarkType = WatermarkType.IMAGE) }
        }
    }

    fun setImageScale(scale: Float) {
        val current = _uiState.value as? BatchWatermarkUiState.Ready ?: return
        _uiState.update { current.copy(imageScale = scale.coerceIn(0.1f, 1.0f)) }
    }

    fun setOpacity(opacity: Float) {
        val current = _uiState.value as? BatchWatermarkUiState.Ready ?: return
        _uiState.update { current.copy(opacity = opacity.coerceIn(0.1f, 1.0f)) }
    }

    fun setPosition(normX: Float, normY: Float) {
        val current = _uiState.value as? BatchWatermarkUiState.Ready ?: return
        _uiState.update { current.copy(normX = normX.coerceIn(0.01f, 0.95f), normY = normY.coerceIn(0.05f, 0.98f)) }
    }

    fun applyPreset(preset: WatermarkPositionPreset) {
        setPosition(preset.normX, preset.normY)
    }

    fun setOutputFormat(format: OutputFormat) {
        val current = _uiState.value as? BatchWatermarkUiState.Ready ?: return
        _uiState.update { current.copy(outputFormat = format) }
    }

    fun setQuality(quality: Int) {
        val current = _uiState.value as? BatchWatermarkUiState.Ready ?: return
        _uiState.update { current.copy(quality = quality.coerceIn(1, 100)) }
    }

    fun startBatchProcessing() {
        val ready = _uiState.value as? BatchWatermarkUiState.Ready ?: return
        val context = getApplication<Application>()

        processJob = viewModelScope.launch {
            val itemList = ready.items.toMutableList()
            var successCount = 0
            var failedCount = 0

            _uiState.value = BatchWatermarkUiState.Processing(
                items = itemList.toList(),
                currentIndex = 0,
                totalCount = itemList.size,
                successCount = 0,
                failedCount = 0
            )

            for (i in itemList.indices) {
                if (!isActive) break

                val item = itemList[i]
                itemList[i] = item.copy(status = BatchItemStatus.PROCESSING)
                _uiState.value = BatchWatermarkUiState.Processing(
                    items = itemList.toList(),
                    currentIndex = i + 1,
                    totalCount = itemList.size,
                    successCount = successCount,
                    failedCount = failedCount
                )

                val result = withContext(Dispatchers.Default) {
                    try {
                        val sourceBmp = ImageBitmapHelper.decodeSafeBitmap(context, item.uri, maxDimension = 3000)
                            ?: return@withContext Result.failure(Exception("Cannot decode image"))

                        val watermarked = if (ready.watermarkType == WatermarkType.TEXT) {
                            ImageBitmapHelper.applyWatermarkText(
                                source = sourceBmp,
                                text = ready.text.ifBlank { "PixelRox" },
                                textSizeSp = ready.textSizeSp,
                                textColor = ready.textColor,
                                opacity = ready.opacity,
                                normX = ready.normX,
                                normY = ready.normY
                            )
                        } else if (ready.logoBitmap != null) {
                            ImageBitmapHelper.applyWatermarkImage(
                                source = sourceBmp,
                                logo = ready.logoBitmap,
                                scale = ready.imageScale,
                                opacity = ready.opacity,
                                normX = ready.normX,
                                normY = ready.normY
                            )
                        } else {
                            sourceBmp
                        }

                        val pubResult = ImageBitmapHelper.saveAndPublish(
                            context = context,
                            bitmap = watermarked,
                            format = ready.outputFormat,
                            quality = if (ready.outputFormat.supportsQuality) ready.quality else 100,
                            baseName = "batch_watermark"
                        )

                        if (watermarked != sourceBmp) {
                            watermarked.recycle()
                        }
                        sourceBmp.recycle()

                        when (pubResult) {
                            is ImagePublishResult.Success -> Result.success(pubResult.contentUri)
                            is ImagePublishResult.Failure -> Result.failure(Exception(pubResult.errorMessage))
                        }
                    } catch (e: Exception) {
                        Result.failure(e)
                    }
                }

                result.fold(
                    onSuccess = { resUri ->
                        successCount++
                        itemList[i] = item.copy(
                            status = BatchItemStatus.SUCCESS,
                            resultUri = resUri
                        )
                    },
                    onFailure = { err ->
                        failedCount++
                        itemList[i] = item.copy(
                            status = BatchItemStatus.FAILED,
                            error = err.message ?: "Failed to watermark"
                        )
                    }
                )

                _uiState.value = BatchWatermarkUiState.Processing(
                    items = itemList.toList(),
                    currentIndex = i + 1,
                    totalCount = itemList.size,
                    successCount = successCount,
                    failedCount = failedCount
                )
            }

            _uiState.value = BatchWatermarkUiState.Completed(
                items = itemList.toList(),
                successCount = successCount,
                failedCount = failedCount
            )
        }
    }

    fun cancelBatch() {
        processJob?.cancel()
    }

    fun reset() {
        cancelBatch()
        val current = _uiState.value as? BatchWatermarkUiState.Ready
        current?.logoBitmap?.recycle()
        _uiState.value = BatchWatermarkUiState.Idle
    }

    override fun onCleared() {
        super.onCleared()
        val current = _uiState.value as? BatchWatermarkUiState.Ready
        current?.logoBitmap?.recycle()
    }
}
