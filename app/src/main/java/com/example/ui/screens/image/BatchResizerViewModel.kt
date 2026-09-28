package com.example.ui.screens.image

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.image.ImagePublishResult
import com.example.core.image.OutputFormat
import com.example.data.util.ImageBitmapHelper
import com.example.data.util.ImageProcessingPipeline
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class BatchResizeItem(
    val uri: Uri,
    val fileName: String,
    val originalWidth: Int = 0,
    val originalHeight: Int = 0,
    val outputWidth: Int = 0,
    val outputHeight: Int = 0,
    val status: BatchItemStatus = BatchItemStatus.PENDING,
    val error: String? = null,
    val resultUri: Uri? = null
)

enum class BatchItemStatus {
    PENDING,
    PROCESSING,
    SUCCESS,
    FAILED
}

enum class BatchResizeMode {
    DIMENSIONS,
    LONG_EDGE
}

sealed interface BatchResizerUiState {
    data object Idle : BatchResizerUiState

    data class Ready(
        val items: List<BatchResizeItem>,
        val mode: BatchResizeMode = BatchResizeMode.LONG_EDGE,
        val targetLongEdge: Int = 1920,
        val targetWidth: Int = 1080,
        val targetHeight: Int = 1080,
        val preserveAspectRatio: Boolean = true,
        val noUpscale: Boolean = true,
        val outputFormat: OutputFormat = OutputFormat.JPEG,
        val quality: Int = 85
    ) : BatchResizerUiState

    data class Processing(
        val items: List<BatchResizeItem>,
        val currentIndex: Int,
        val totalCount: Int,
        val successCount: Int,
        val failedCount: Int
    ) : BatchResizerUiState

    data class Completed(
        val items: List<BatchResizeItem>,
        val successCount: Int,
        val failedCount: Int
    ) : BatchResizerUiState
}

class BatchResizerViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow<BatchResizerUiState>(BatchResizerUiState.Idle)
    val uiState: StateFlow<BatchResizerUiState> = _uiState.asStateFlow()

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
            BatchResizeItem(uri = uri, fileName = name)
        }

        _uiState.value = BatchResizerUiState.Ready(items = items)
    }

    fun setMode(mode: BatchResizeMode) {
        val current = _uiState.value as? BatchResizerUiState.Ready ?: return
        _uiState.update { current.copy(mode = mode) }
    }

    fun setTargetLongEdge(edge: Int) {
        val current = _uiState.value as? BatchResizerUiState.Ready ?: return
        _uiState.update { current.copy(targetLongEdge = edge.coerceIn(128, 4096)) }
    }

    fun setTargetDimensions(width: Int, height: Int) {
        val current = _uiState.value as? BatchResizerUiState.Ready ?: return
        _uiState.update {
            current.copy(
                targetWidth = width.coerceIn(64, 4096),
                targetHeight = height.coerceIn(64, 4096)
            )
        }
    }

    fun setNoUpscale(noUpscale: Boolean) {
        val current = _uiState.value as? BatchResizerUiState.Ready ?: return
        _uiState.update { current.copy(noUpscale = noUpscale) }
    }

    fun setPreserveAspectRatio(preserve: Boolean) {
        val current = _uiState.value as? BatchResizerUiState.Ready ?: return
        _uiState.update { current.copy(preserveAspectRatio = preserve) }
    }

    fun setOutputFormat(format: OutputFormat) {
        val current = _uiState.value as? BatchResizerUiState.Ready ?: return
        _uiState.update { current.copy(outputFormat = format) }
    }

    fun setQuality(quality: Int) {
        val current = _uiState.value as? BatchResizerUiState.Ready ?: return
        _uiState.update { current.copy(quality = quality.coerceIn(1, 100)) }
    }

    fun startBatchProcessing() {
        val ready = _uiState.value as? BatchResizerUiState.Ready ?: return
        val context = getApplication<Application>()

        processJob = viewModelScope.launch {
            val itemList = ready.items.toMutableList()
            var successCount = 0
            var failedCount = 0

            _uiState.value = BatchResizerUiState.Processing(
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
                _uiState.value = BatchResizerUiState.Processing(
                    items = itemList.toList(),
                    currentIndex = i + 1,
                    totalCount = itemList.size,
                    successCount = successCount,
                    failedCount = failedCount
                )

                // Safe bounded decode and resize
                val result = withContext(Dispatchers.Default) {
                    try {
                        val bitmap = ImageBitmapHelper.decodeSafeBitmap(context, item.uri, maxDimension = 3000)
                            ?: return@withContext Result.failure(Exception("Cannot decode bitmap"))

                        val (outW, outH) = if (ready.mode == BatchResizeMode.LONG_EDGE) {
                            ImageProcessingPipeline.calculateLongEdgeDimensions(
                                originalWidth = bitmap.width,
                                originalHeight = bitmap.height,
                                targetLongEdge = ready.targetLongEdge,
                                noUpscale = ready.noUpscale
                            )
                        } else {
                            ImageProcessingPipeline.calculateResizeDimensions(
                                originalWidth = bitmap.width,
                                originalHeight = bitmap.height,
                                targetWidth = ready.targetWidth,
                                targetHeight = ready.targetHeight,
                                preserveAspectRatio = ready.preserveAspectRatio,
                                noUpscale = ready.noUpscale
                            )
                        }

                        val resized = if (outW != bitmap.width || outH != bitmap.height) {
                            ImageBitmapHelper.resizeBitmap(bitmap, outW, outH)
                        } else {
                            bitmap
                        }

                        val pubResult = ImageBitmapHelper.saveAndPublish(
                            context = context,
                            bitmap = resized,
                            format = ready.outputFormat,
                            quality = if (ready.outputFormat.supportsQuality) ready.quality else 100,
                            baseName = "batch_resized"
                        )

                        if (resized != bitmap) {
                            resized.recycle()
                        }
                        bitmap.recycle()

                        when (pubResult) {
                            is ImagePublishResult.Success -> Result.success(Pair(pubResult.contentUri, Pair(outW, outH)))
                            is ImagePublishResult.Failure -> Result.failure(Exception(pubResult.errorMessage))
                        }
                    } catch (e: Exception) {
                        Result.failure(e)
                    }
                }

                result.fold(
                    onSuccess = { (resUri, dims) ->
                        successCount++
                        itemList[i] = item.copy(
                            status = BatchItemStatus.SUCCESS,
                            resultUri = resUri,
                            outputWidth = dims.first,
                            outputHeight = dims.second
                        )
                    },
                    onFailure = { err ->
                        failedCount++
                        itemList[i] = item.copy(
                            status = BatchItemStatus.FAILED,
                            error = err.message ?: "Processing error"
                        )
                    }
                )

                _uiState.value = BatchResizerUiState.Processing(
                    items = itemList.toList(),
                    currentIndex = i + 1,
                    totalCount = itemList.size,
                    successCount = successCount,
                    failedCount = failedCount
                )
            }

            _uiState.value = BatchResizerUiState.Completed(
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
        _uiState.value = BatchResizerUiState.Idle
    }
}
