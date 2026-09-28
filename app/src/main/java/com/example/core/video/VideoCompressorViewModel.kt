package com.example.core.video

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.workers.VideoCompressionWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class VideoCompressorViewModel(
    application: Application,
    private val workManager: WorkManager?
) : AndroidViewModel(application) {

    constructor(application: Application) : this(
        application = application,
        workManager = try { WorkManager.getInstance(application) } catch (_: Exception) { null }
    )

    constructor(context: Context, workManager: WorkManager?) : this(
        application = context.applicationContext as Application,
        workManager = workManager
    )

    constructor(context: Context) : this(
        application = context.applicationContext as Application,
        workManager = try { WorkManager.getInstance(context) } catch (_: Exception) { null }
    )

    private val context: Context get() = getApplication()

    private val _uiState = MutableStateFlow<VideoCompressorUiState>(VideoCompressorUiState.NoVideoSelected)
    val uiState: StateFlow<VideoCompressorUiState> = _uiState.asStateFlow()

    private var workObserverJob: Job? = null

    // In-memory cache of current selected/running video details to reconnect gracefully across screen recreation
    private var lastSelectedUri: Uri? = null
    private var lastMetadata: VideoMetadata? = null
    private var lastPreset: VideoCompressionPreset = VideoCompressionPreset.MEDIUM

    init {
        observeExistingWork()
    }

    private fun observeExistingWork() {
        workObserverJob?.cancel()
        val wm = workManager ?: return
        workObserverJob = viewModelScope.launch {
            wm.getWorkInfosForUniqueWorkFlow(VideoCompressionWorker.UNIQUE_WORK_NAME)
                .collect { workInfoList ->
                    val activeWorkInfo = workInfoList.firstOrNull() ?: return@collect
                    handleWorkInfoUpdate(activeWorkInfo)
                }
        }
    }

    private fun handleWorkInfoUpdate(workInfo: WorkInfo) {
        val currentState = _uiState.value

        when (workInfo.state) {
            WorkInfo.State.RUNNING -> {
                val progress = workInfo.progress
                val stage = progress.getString(VideoCompressionWorker.KEY_STAGE)
                val progressPercent = progress.getInt(VideoCompressionWorker.KEY_PROGRESS_PERCENT, -1).takeIf { it >= 0 }
                val displayName = progress.getString(VideoCompressionWorker.KEY_DISPLAY_NAME)
                val compressedSize = progress.getLong(VideoCompressionWorker.KEY_COMPRESSED_SIZE_BYTES, 0L)

                val uri = lastSelectedUri ?: (currentState as? VideoCompressorUiState.Compressing)?.sourceUri
                val meta = lastMetadata ?: (currentState as? VideoCompressorUiState.Compressing)?.metadata
                val preset = lastPreset

                if (uri != null && meta != null) {
                    if (stage == VideoCompressionWorker.STAGE_PUBLISHING) {
                        _uiState.value = VideoCompressorUiState.Publishing(
                            sourceUri = uri,
                            metadata = meta,
                            selectedPreset = preset,
                            compressedSizeBytes = compressedSize
                        )
                    } else {
                        val estimate = preset.estimateOutputSize(meta.durationMs, meta.bitrate)
                        _uiState.value = VideoCompressorUiState.Compressing(
                            sourceUri = uri,
                            metadata = meta,
                            selectedPreset = preset,
                            progressPercent = progressPercent,
                            estimatedOutputSizeBytes = estimate
                        )
                    }
                }
            }

            WorkInfo.State.SUCCEEDED -> {
                val output = workInfo.outputData
                val resultUriStr = output.getString(VideoCompressionWorker.KEY_RESULT_URI)
                if (resultUriStr != null) {
                    val resultUri = Uri.parse(resultUriStr)
                    val displayName = output.getString(VideoCompressionWorker.KEY_RESULT_DISPLAY_NAME) ?: "video.mp4"
                    val originalSize = output.getLong(VideoCompressionWorker.KEY_ORIGINAL_SIZE_BYTES, 0L)
                    val compressedSize = output.getLong(VideoCompressionWorker.KEY_COMPRESSED_SIZE_BYTES, 0L)
                    val bytesSaved = output.getLong(VideoCompressionWorker.KEY_BYTES_SAVED, 0L)
                    val percentageSaved = output.getDouble(VideoCompressionWorker.KEY_PERCENTAGE_SAVED, 0.0).toFloat()
                    val mimeType = output.getString(VideoCompressionWorker.KEY_MIME_TYPE) ?: "video/mp4"
                    val outW = output.getInt(VideoCompressionWorker.KEY_OUTPUT_WIDTH, 0)
                    val outH = output.getInt(VideoCompressionWorker.KEY_OUTPUT_HEIGHT, 0)
                    val sourceUriStr = output.getString(VideoCompressionWorker.KEY_SOURCE_URI)
                    val srcUri = if (sourceUriStr != null) Uri.parse(sourceUriStr) else lastSelectedUri ?: Uri.EMPTY

                    val meta = lastMetadata ?: VideoMetadata(
                        contentUri = srcUri.toString(),
                        displayName = displayName,
                        mimeType = mimeType,
                        fileSizeBytes = originalSize,
                        durationMs = 0L,
                        width = outW,
                        height = outH,
                        bitrate = 0L
                    )

                    _uiState.value = VideoCompressorUiState.Success(
                        sourceUri = srcUri,
                        metadata = meta,
                        selectedPreset = lastPreset,
                        finalContentUri = resultUri,
                        displayName = displayName,
                        originalSizeBytes = originalSize,
                        compressedSizeBytes = compressedSize,
                        bytesSaved = bytesSaved,
                        percentageSaved = percentageSaved,
                        mimeType = mimeType,
                        outputWidth = outW,
                        outputHeight = outH
                    )
                }
            }

            WorkInfo.State.FAILED -> {
                // If we're already showing an error or success, don't overwrite with old failed state
                if (currentState !is VideoCompressorUiState.Error && currentState !is VideoCompressorUiState.Success) {
                    val output = workInfo.outputData
                    val errorMessage = output.getString(VideoCompressionWorker.KEY_ERROR_MESSAGE) ?: "Compression failed"
                    val uri = lastSelectedUri ?: (currentState as? VideoCompressorUiState.Compressing)?.sourceUri
                    val meta = lastMetadata ?: (currentState as? VideoCompressorUiState.Compressing)?.metadata
                    val preset = lastPreset
                    val estimate = meta?.let { preset.estimateOutputSize(it.durationMs, it.bitrate) } ?: 0L

                    if (!errorMessage.contains("cancelled", ignoreCase = true)) {
                        _uiState.value = VideoCompressorUiState.Error(
                            errorMessage = errorMessage,
                            sourceUri = uri,
                            metadata = meta,
                            selectedPreset = preset,
                            estimatedOutputSizeBytes = estimate
                        )
                    }
                }
            }

            WorkInfo.State.CANCELLED -> {
                if (currentState is VideoCompressorUiState.Compressing || currentState is VideoCompressorUiState.Publishing) {
                    val uri = lastSelectedUri ?: (currentState as? VideoCompressorUiState.Compressing)?.sourceUri
                    val meta = lastMetadata ?: (currentState as? VideoCompressorUiState.Compressing)?.metadata
                    val preset = lastPreset
                    if (uri != null && meta != null) {
                        val estimate = preset.estimateOutputSize(meta.durationMs, meta.bitrate)
                        _uiState.value = VideoCompressorUiState.Selected(
                            sourceUri = uri,
                            metadata = meta,
                            selectedPreset = preset,
                            estimatedOutputSizeBytes = estimate
                        )
                    } else {
                        _uiState.value = VideoCompressorUiState.NoVideoSelected
                    }
                }
            }

            WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> {
                val uri = lastSelectedUri ?: (currentState as? VideoCompressorUiState.Selected)?.sourceUri
                val meta = lastMetadata ?: (currentState as? VideoCompressorUiState.Selected)?.metadata
                val preset = lastPreset
                if (uri != null && meta != null && currentState !is VideoCompressorUiState.Compressing) {
                    val estimate = preset.estimateOutputSize(meta.durationMs, meta.bitrate)
                    _uiState.value = VideoCompressorUiState.Compressing(
                        sourceUri = uri,
                        metadata = meta,
                        selectedPreset = preset,
                        progressPercent = null,
                        estimatedOutputSizeBytes = estimate
                    )
                }
            }
        }
    }

    fun onVideoSelected(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val metadata = VideoMetadata.extract(context, uri)
                val defaultPreset = VideoCompressionPreset.MEDIUM
                val estimate = defaultPreset.estimateOutputSize(metadata.durationMs, metadata.bitrate)
                
                lastSelectedUri = uri
                lastMetadata = metadata
                lastPreset = defaultPreset

                withContext(Dispatchers.Main) {
                    _uiState.value = VideoCompressorUiState.Selected(
                        sourceUri = uri,
                        metadata = metadata,
                        selectedPreset = defaultPreset,
                        estimatedOutputSizeBytes = estimate
                    )
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _uiState.value = VideoCompressorUiState.Error("Unable to read video: ${e.localizedMessage ?: "Unknown error"}")
                }
            }
        }
    }

    fun selectPreset(preset: VideoCompressionPreset) {
        lastPreset = preset
        val currentState = _uiState.value
        when (currentState) {
            is VideoCompressorUiState.Selected -> {
                val estimate = preset.estimateOutputSize(currentState.metadata.durationMs, currentState.metadata.bitrate)
                _uiState.value = currentState.copy(
                    selectedPreset = preset,
                    estimatedOutputSizeBytes = estimate
                )
            }
            else -> {}
        }
    }

    fun startCompression() {
        val currentState = _uiState.value
        val (uri, metadata, preset, estimate) = when (currentState) {
            is VideoCompressorUiState.Selected -> Quad(currentState.sourceUri, currentState.metadata, currentState.selectedPreset, currentState.estimatedOutputSizeBytes)
            else -> return
        }

        lastSelectedUri = uri
        lastMetadata = metadata
        lastPreset = preset

        _uiState.value = VideoCompressorUiState.Compressing(
            sourceUri = uri,
            metadata = metadata,
            selectedPreset = preset,
            progressPercent = null,
            estimatedOutputSizeBytes = estimate
        )

        val inputData = workDataOf(
            VideoCompressionWorker.KEY_SOURCE_URI to uri.toString(),
            VideoCompressionWorker.KEY_PRESET_NAME to preset.name,
            VideoCompressionWorker.KEY_DISPLAY_NAME to metadata.displayName,
            VideoCompressionWorker.KEY_SOURCE_SIZE to metadata.fileSizeBytes,
            VideoCompressionWorker.KEY_DURATION_MS to metadata.durationMs,
            VideoCompressionWorker.KEY_WIDTH to metadata.width,
            VideoCompressionWorker.KEY_HEIGHT to metadata.height,
            VideoCompressionWorker.KEY_BITRATE to metadata.bitrate,
            VideoCompressionWorker.KEY_MIME_TYPE to metadata.mimeType
        )

        val compressionWorkRequest = OneTimeWorkRequestBuilder<VideoCompressionWorker>()
            .setInputData(inputData)
            .build()

        workManager?.enqueueUniqueWork(
            VideoCompressionWorker.UNIQUE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            compressionWorkRequest
        )
    }

    fun cancelCompression() {
        workManager?.cancelUniqueWork(VideoCompressionWorker.UNIQUE_WORK_NAME)

        val currentState = _uiState.value
        val (uri, metadata, preset) = when (currentState) {
            is VideoCompressorUiState.Compressing -> Triple(currentState.sourceUri, currentState.metadata, currentState.selectedPreset)
            is VideoCompressorUiState.Publishing -> Triple(currentState.sourceUri, currentState.metadata, currentState.selectedPreset)
            else -> null
        } ?: return

        val estimate = preset.estimateOutputSize(metadata.durationMs, metadata.bitrate)
        _uiState.value = VideoCompressorUiState.Selected(
            sourceUri = uri,
            metadata = metadata,
            selectedPreset = preset,
            estimatedOutputSizeBytes = estimate
        )
    }

    fun retry() {
        val currentState = _uiState.value
        if (currentState is VideoCompressorUiState.Error && currentState.sourceUri != null && currentState.metadata != null && currentState.selectedPreset != null) {
            _uiState.value = VideoCompressorUiState.Selected(
                sourceUri = currentState.sourceUri,
                metadata = currentState.metadata,
                selectedPreset = currentState.selectedPreset,
                estimatedOutputSizeBytes = currentState.estimatedOutputSizeBytes
            )
            startCompression()
        }
    }

    fun reset() {
        workManager?.cancelUniqueWork(VideoCompressionWorker.UNIQUE_WORK_NAME)
        lastSelectedUri = null
        lastMetadata = null
        _uiState.value = VideoCompressorUiState.NoVideoSelected
    }

    override fun onCleared() {
        super.onCleared()
        workObserverJob?.cancel()
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
