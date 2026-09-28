package com.example.ui.screens.trimmer

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.trimmer.IVideoTrimmerEngine
import com.example.core.trimmer.VideoTrimmerEngine
import com.example.core.trimmer.VideoTrimmerResult
import com.example.core.trimmer.VideoTrimmerUtils
import com.example.core.video.VideoMetadata
import com.example.core.video.VideoOutputPublisher
import com.example.core.video.VideoPublishResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class VideoTrimmerViewModel(
    application: Application,
    private val trimmerEngine: IVideoTrimmerEngine,
    private val publisher: VideoOutputPublisher
) : AndroidViewModel(application) {

    constructor(application: Application) : this(
        application = application,
        trimmerEngine = VideoTrimmerEngine(application),
        publisher = VideoOutputPublisher(application)
    )

    private val context: Context get() = getApplication()

    private val _uiState = MutableStateFlow<VideoTrimmerUiState>(VideoTrimmerUiState.NoVideoSelected)
    val uiState: StateFlow<VideoTrimmerUiState> = _uiState.asStateFlow()

    fun onVideoSelected(uri: Uri) {
        viewModelScope.launch {
            try {
                val metadata = VideoMetadata.extract(context, uri)
                if (metadata.durationMs <= 0) {
                    _uiState.value = VideoTrimmerUiState.Error(
                        sourceUri = uri,
                        metadata = metadata,
                        message = "Unable to read video duration."
                    )
                    return@launch
                }

                _uiState.value = VideoTrimmerUiState.Ready(
                    uri = uri,
                    metadata = metadata,
                    startMs = 0L,
                    endMs = metadata.durationMs
                )
            } catch (e: Exception) {
                _uiState.value = VideoTrimmerUiState.Error(
                    sourceUri = uri,
                    metadata = null,
                    message = "Error loading video: ${e.localizedMessage}"
                )
            }
        }
    }

    fun updateTrimRange(startMs: Long, endMs: Long) {
        val currentState = _uiState.value as? VideoTrimmerUiState.Ready ?: return
        val totalMs = currentState.metadata.durationMs
        val validStart = startMs.coerceIn(0L, totalMs)
        val validEnd = endMs.coerceIn(0L, totalMs)

        if (validStart < validEnd && (validEnd - validStart) >= 500L) {
            _uiState.value = currentState.copy(
                startMs = validStart,
                endMs = validEnd
            )
        }
    }

    fun adjustStartBy(deltaMs: Long) {
        val currentState = _uiState.value as? VideoTrimmerUiState.Ready ?: return
        val newStart = (currentState.startMs + deltaMs).coerceIn(0L, currentState.endMs - 500L)
        updateTrimRange(newStart, currentState.endMs)
    }

    fun adjustEndBy(deltaMs: Long) {
        val currentState = _uiState.value as? VideoTrimmerUiState.Ready ?: return
        val newEnd = (currentState.endMs + deltaMs).coerceIn(currentState.startMs + 500L, currentState.metadata.durationMs)
        updateTrimRange(currentState.startMs, newEnd)
    }

    fun startTrim() {
        val currentState = _uiState.value as? VideoTrimmerUiState.Ready ?: return
        val sourceUri = currentState.uri
        val metadata = currentState.metadata
        val startMs = currentState.startMs
        val endMs = currentState.endMs

        _uiState.value = VideoTrimmerUiState.Trimming(
            uri = sourceUri,
            metadata = metadata,
            progressPercent = 0
        )

        viewModelScope.launch {
            val result = trimmerEngine.trimVideo(
                sourceUri = sourceUri,
                startMs = startMs,
                endMs = endMs,
                onProgress = { progress ->
                    val trimState = _uiState.value as? VideoTrimmerUiState.Trimming
                    if (trimState != null) {
                        _uiState.value = trimState.copy(progressPercent = progress)
                    }
                }
            )

            when (result) {
                is VideoTrimmerResult.Success -> {
                    val displayName = VideoTrimmerUtils.generateTrimmedDisplayName(metadata.displayName)
                    val publishResult = publisher.publishVideo(
                        sourceFile = result.tempFile,
                        desiredDisplayName = displayName
                    )

                    if (result.tempFile.exists()) {
                        result.tempFile.delete()
                    }

                    when (publishResult) {
                        is VideoPublishResult.Success -> {
                            _uiState.value = VideoTrimmerUiState.Success(
                                outputUri = publishResult.contentUri,
                                savedFileName = displayName,
                                trimmedDurationMs = endMs - startMs,
                                originalDurationMs = metadata.durationMs,
                                fileSizeBytes = result.fileSizeBytes
                            )
                        }
                        is VideoPublishResult.Failure -> {
                            _uiState.value = VideoTrimmerUiState.Error(
                                sourceUri = sourceUri,
                                metadata = metadata,
                                message = "Failed to save trimmed video: ${publishResult.errorMessage}"
                            )
                        }
                    }
                }
                is VideoTrimmerResult.Failure -> {
                    _uiState.value = VideoTrimmerUiState.Error(
                        sourceUri = sourceUri,
                        metadata = metadata,
                        message = result.message
                    )
                }
                is VideoTrimmerResult.Cancelled -> {
                    _uiState.value = VideoTrimmerUiState.Ready(
                        uri = sourceUri,
                        metadata = metadata,
                        startMs = startMs,
                        endMs = endMs
                    )
                }
            }
        }
    }

    fun cancelTrim() {
        trimmerEngine.cancel()
    }

    fun resetToSelect() {
        _uiState.value = VideoTrimmerUiState.NoVideoSelected
    }
}
