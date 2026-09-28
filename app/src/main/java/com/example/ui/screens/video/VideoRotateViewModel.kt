package com.example.ui.screens.video

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.video.IVideoStudioEngine
import com.example.core.video.VideoMetadata
import com.example.core.video.VideoRotationAngle
import com.example.core.video.VideoStudioEngine
import com.example.core.video.VideoStudioResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface VideoRotateUiState {
    data object Idle : VideoRotateUiState
    data class Ready(
        val uri: Uri,
        val metadata: VideoMetadata,
        val selectedAngle: VideoRotationAngle
    ) : VideoRotateUiState
    data class Processing(val uri: Uri, val progress: Int) : VideoRotateUiState
    data class Success(val result: VideoStudioResult.Success, val angle: VideoRotationAngle) : VideoRotateUiState
    data class Error(val message: String) : VideoRotateUiState
}

class VideoRotateViewModel(
    application: Application,
    private val engine: IVideoStudioEngine
) : AndroidViewModel(application) {

    constructor(application: Application) : this(
        application = application,
        engine = VideoStudioEngine(application)
    )

    private val context: Context get() = getApplication()

    private val _uiState = MutableStateFlow<VideoRotateUiState>(VideoRotateUiState.Idle)
    val uiState: StateFlow<VideoRotateUiState> = _uiState.asStateFlow()

    fun onVideoSelected(uri: Uri) {
        viewModelScope.launch {
            try {
                val metadata = VideoMetadata.extract(context, uri)
                _uiState.value = VideoRotateUiState.Ready(uri, metadata, VideoRotationAngle.ROTATION_90)
            } catch (e: Exception) {
                _uiState.value = VideoRotateUiState.Error("Failed to load video: ${e.localizedMessage}")
            }
        }
    }

    fun selectAngle(angle: VideoRotationAngle) {
        val current = _uiState.value as? VideoRotateUiState.Ready ?: return
        _uiState.value = current.copy(selectedAngle = angle)
    }

    fun rotateVideo() {
        val ready = _uiState.value as? VideoRotateUiState.Ready ?: return
        val uri = ready.uri
        val angle = ready.selectedAngle
        _uiState.value = VideoRotateUiState.Processing(uri, 0)

        viewModelScope.launch {
            when (val result = engine.rotateVideo(uri, angle) { progress ->
                _uiState.value = VideoRotateUiState.Processing(uri, progress)
            }) {
                is VideoStudioResult.Success -> {
                    _uiState.value = VideoRotateUiState.Success(result, angle)
                }
                is VideoStudioResult.Failure -> {
                    _uiState.value = VideoRotateUiState.Error(result.message)
                }
                VideoStudioResult.Cancelled -> {
                    _uiState.value = VideoRotateUiState.Ready(uri, ready.metadata, angle)
                }
            }
        }
    }

    fun reset() {
        _uiState.value = VideoRotateUiState.Idle
    }
}
