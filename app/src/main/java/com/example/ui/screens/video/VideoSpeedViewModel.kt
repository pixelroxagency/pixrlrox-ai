package com.example.ui.screens.video

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.video.IVideoStudioEngine
import com.example.core.video.VideoMetadata
import com.example.core.video.VideoSpeedPreset
import com.example.core.video.VideoStudioEngine
import com.example.core.video.VideoStudioResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface VideoSpeedUiState {
    data object Idle : VideoSpeedUiState
    data class Ready(
        val uri: Uri,
        val metadata: VideoMetadata,
        val selectedSpeed: VideoSpeedPreset
    ) : VideoSpeedUiState
    data class Processing(val uri: Uri, val progress: Int) : VideoSpeedUiState
    data class Success(val result: VideoStudioResult.Success, val speed: VideoSpeedPreset) : VideoSpeedUiState
    data class Error(val message: String) : VideoSpeedUiState
}

class VideoSpeedViewModel(
    application: Application,
    private val engine: IVideoStudioEngine
) : AndroidViewModel(application) {

    constructor(application: Application) : this(
        application = application,
        engine = VideoStudioEngine(application)
    )

    private val context: Context get() = getApplication()

    private val _uiState = MutableStateFlow<VideoSpeedUiState>(VideoSpeedUiState.Idle)
    val uiState: StateFlow<VideoSpeedUiState> = _uiState.asStateFlow()

    fun onVideoSelected(uri: Uri) {
        viewModelScope.launch {
            try {
                val metadata = VideoMetadata.extract(context, uri)
                _uiState.value = VideoSpeedUiState.Ready(uri, metadata, VideoSpeedPreset.SPEED_1_5X)
            } catch (e: Exception) {
                _uiState.value = VideoSpeedUiState.Error("Failed to load video: ${e.localizedMessage}")
            }
        }
    }

    fun selectSpeed(speed: VideoSpeedPreset) {
        val current = _uiState.value as? VideoSpeedUiState.Ready ?: return
        _uiState.value = current.copy(selectedSpeed = speed)
    }

    fun applySpeed() {
        val ready = _uiState.value as? VideoSpeedUiState.Ready ?: return
        val uri = ready.uri
        val speed = ready.selectedSpeed
        _uiState.value = VideoSpeedUiState.Processing(uri, 0)

        viewModelScope.launch {
            when (val result = engine.changeSpeed(
                sourceUri = uri,
                speedPreset = speed,
                onProgress = { progress ->
                    _uiState.value = VideoSpeedUiState.Processing(uri, progress)
                }
            )) {
                is VideoStudioResult.Success -> {
                    _uiState.value = VideoSpeedUiState.Success(result, speed)
                }
                is VideoStudioResult.Failure -> {
                    _uiState.value = VideoSpeedUiState.Error(result.message)
                }
                VideoStudioResult.Cancelled -> {
                    _uiState.value = VideoSpeedUiState.Ready(uri, ready.metadata, speed)
                }
            }
        }
    }

    fun reset() {
        _uiState.value = VideoSpeedUiState.Idle
    }
}
