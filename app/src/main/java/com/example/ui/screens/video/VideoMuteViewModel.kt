package com.example.ui.screens.video

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.video.IVideoStudioEngine
import com.example.core.video.VideoMetadata
import com.example.core.video.VideoStudioEngine
import com.example.core.video.VideoStudioResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface VideoMuteUiState {
    data object Idle : VideoMuteUiState
    data class Ready(val uri: Uri, val metadata: VideoMetadata) : VideoMuteUiState
    data class Processing(val uri: Uri, val progress: Int) : VideoMuteUiState
    data class Success(val result: VideoStudioResult.Success) : VideoMuteUiState
    data class Error(val message: String) : VideoMuteUiState
}

class VideoMuteViewModel(
    application: Application,
    private val engine: IVideoStudioEngine
) : AndroidViewModel(application) {

    constructor(application: Application) : this(
        application = application,
        engine = VideoStudioEngine(application)
    )

    private val context: Context get() = getApplication()

    private val _uiState = MutableStateFlow<VideoMuteUiState>(VideoMuteUiState.Idle)
    val uiState: StateFlow<VideoMuteUiState> = _uiState.asStateFlow()

    fun onVideoSelected(uri: Uri) {
        viewModelScope.launch {
            try {
                val metadata = VideoMetadata.extract(context, uri)
                _uiState.value = VideoMuteUiState.Ready(uri, metadata)
            } catch (e: Exception) {
                _uiState.value = VideoMuteUiState.Error("Failed to inspect video: ${e.localizedMessage}")
            }
        }
    }

    fun muteVideo() {
        val ready = _uiState.value as? VideoMuteUiState.Ready ?: return
        val uri = ready.uri
        _uiState.value = VideoMuteUiState.Processing(uri, 0)

        viewModelScope.launch {
            when (val result = engine.muteVideo(uri) { progress ->
                _uiState.value = VideoMuteUiState.Processing(uri, progress)
            }) {
                is VideoStudioResult.Success -> {
                    _uiState.value = VideoMuteUiState.Success(result)
                }
                is VideoStudioResult.Failure -> {
                    _uiState.value = VideoMuteUiState.Error(result.message)
                }
                VideoStudioResult.Cancelled -> {
                    _uiState.value = VideoMuteUiState.Ready(uri, ready.metadata)
                }
            }
        }
    }

    fun reset() {
        _uiState.value = VideoMuteUiState.Idle
    }
}
