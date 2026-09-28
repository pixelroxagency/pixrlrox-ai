package com.example.ui.screens.video

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.video.FrameExtractResult
import com.example.core.video.FrameImageFormat
import com.example.core.video.IVideoStudioEngine
import com.example.core.video.VideoMetadata
import com.example.core.video.VideoStudioEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface VideoFrameExtractorUiState {
    data object Idle : VideoFrameExtractorUiState
    data class Ready(
        val uri: Uri,
        val metadata: VideoMetadata,
        val selectedTimestampMs: Long,
        val previewBitmap: Bitmap?,
        val selectedFormat: FrameImageFormat,
        val quality: Int,
        val isExtractingPreview: Boolean
    ) : VideoFrameExtractorUiState
    data class Extracting(val uri: Uri, val timestampMs: Long) : VideoFrameExtractorUiState
    data class Success(val result: FrameExtractResult.Success) : VideoFrameExtractorUiState
    data class Error(val message: String) : VideoFrameExtractorUiState
}

class VideoFrameExtractorViewModel(
    application: Application,
    private val engine: IVideoStudioEngine
) : AndroidViewModel(application) {

    constructor(application: Application) : this(
        application = application,
        engine = VideoStudioEngine(application)
    )

    private val context: Context get() = getApplication()

    private val _uiState = MutableStateFlow<VideoFrameExtractorUiState>(VideoFrameExtractorUiState.Idle)
    val uiState: StateFlow<VideoFrameExtractorUiState> = _uiState.asStateFlow()

    fun onVideoSelected(uri: Uri) {
        viewModelScope.launch {
            try {
                val metadata = VideoMetadata.extract(context, uri)
                val initialTs = if (metadata.durationMs > 1000) 1000L else 0L
                _uiState.value = VideoFrameExtractorUiState.Ready(
                    uri = uri,
                    metadata = metadata,
                    selectedTimestampMs = initialTs,
                    previewBitmap = null,
                    selectedFormat = FrameImageFormat.JPEG,
                    quality = 95,
                    isExtractingPreview = true
                )
                loadPreview(uri, initialTs)
            } catch (e: Exception) {
                _uiState.value = VideoFrameExtractorUiState.Error("Failed to load video: ${e.localizedMessage}")
            }
        }
    }

    fun onTimestampChanged(timestampMs: Long) {
        val current = _uiState.value as? VideoFrameExtractorUiState.Ready ?: return
        val clamped = timestampMs.coerceIn(0L, current.metadata.durationMs)
        _uiState.value = current.copy(selectedTimestampMs = clamped, isExtractingPreview = true)
        loadPreview(current.uri, clamped)
    }

    fun selectFormat(format: FrameImageFormat) {
        val current = _uiState.value as? VideoFrameExtractorUiState.Ready ?: return
        _uiState.value = current.copy(selectedFormat = format)
    }

    fun selectQuality(quality: Int) {
        val current = _uiState.value as? VideoFrameExtractorUiState.Ready ?: return
        _uiState.value = current.copy(quality = quality.coerceIn(10, 100))
    }

    private fun loadPreview(uri: Uri, timestampMs: Long) {
        viewModelScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(context, uri)
                    val timeUs = timestampMs * 1000L
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                        retriever.getScaledFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST, 480, 270)
                            ?: retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
                    } else {
                        retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
                    }
                } catch (_: Exception) {
                    null
                } finally {
                    try { retriever.release() } catch (_: Exception) {}
                }
            }

            val current = _uiState.value as? VideoFrameExtractorUiState.Ready ?: return@launch
            if (current.selectedTimestampMs == timestampMs) {
                _uiState.value = current.copy(previewBitmap = bitmap, isExtractingPreview = false)
            }
        }
    }

    fun extractAndSaveFrame() {
        val ready = _uiState.value as? VideoFrameExtractorUiState.Ready ?: return
        val uri = ready.uri
        val ts = ready.selectedTimestampMs
        val fmt = ready.selectedFormat
        val qual = ready.quality

        _uiState.value = VideoFrameExtractorUiState.Extracting(uri, ts)

        viewModelScope.launch {
            when (val result = engine.extractFrame(uri, ts, fmt, qual)) {
                is FrameExtractResult.Success -> {
                    _uiState.value = VideoFrameExtractorUiState.Success(result)
                }
                is FrameExtractResult.Failure -> {
                    _uiState.value = VideoFrameExtractorUiState.Error(result.message)
                }
            }
        }
    }

    fun reset() {
        _uiState.value = VideoFrameExtractorUiState.Idle
    }
}
