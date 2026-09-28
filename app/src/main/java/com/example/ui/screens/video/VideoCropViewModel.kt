package com.example.ui.screens.video

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.video.IVideoStudioEngine
import com.example.core.video.VideoCropPreset
import com.example.core.video.VideoMetadata
import com.example.core.video.VideoStudioEngine
import com.example.core.video.VideoStudioResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface VideoCropUiState {
    data object Idle : VideoCropUiState
    data class Ready(
        val uri: Uri,
        val metadata: VideoMetadata,
        val selectedPreset: VideoCropPreset
    ) : VideoCropUiState
    data class Processing(val uri: Uri, val progress: Int) : VideoCropUiState
    data class Success(val result: VideoStudioResult.Success, val preset: VideoCropPreset) : VideoCropUiState
    data class Error(val message: String) : VideoCropUiState
}

class VideoCropViewModel(
    application: Application,
    private val engine: IVideoStudioEngine
) : AndroidViewModel(application) {

    constructor(application: Application) : this(
        application = application,
        engine = VideoStudioEngine(application)
    )

    private val context: Context get() = getApplication()

    private val _uiState = MutableStateFlow<VideoCropUiState>(VideoCropUiState.Idle)
    val uiState: StateFlow<VideoCropUiState> = _uiState.asStateFlow()

    fun onVideoSelected(uri: Uri) {
        viewModelScope.launch {
            try {
                val metadata = VideoMetadata.extract(context, uri)
                _uiState.value = VideoCropUiState.Ready(uri, metadata, VideoCropPreset.SQUARE_1_1)
            } catch (e: Exception) {
                _uiState.value = VideoCropUiState.Error("Failed to load video: ${e.localizedMessage}")
            }
        }
    }

    fun selectPreset(preset: VideoCropPreset) {
        val current = _uiState.value as? VideoCropUiState.Ready ?: return
        _uiState.value = current.copy(selectedPreset = preset)
    }

    fun cropVideo() {
        val ready = _uiState.value as? VideoCropUiState.Ready ?: return
        val uri = ready.uri
        val preset = ready.selectedPreset
        _uiState.value = VideoCropUiState.Processing(uri, 0)

        viewModelScope.launch {
            when (val result = engine.cropVideo(
                sourceUri = uri,
                preset = preset,
                onProgress = { progress ->
                    _uiState.value = VideoCropUiState.Processing(uri, progress)
                }
            )) {
                is VideoStudioResult.Success -> {
                    _uiState.value = VideoCropUiState.Success(result, preset)
                }
                is VideoStudioResult.Failure -> {
                    _uiState.value = VideoCropUiState.Error(result.message)
                }
                VideoStudioResult.Cancelled -> {
                    _uiState.value = VideoCropUiState.Ready(uri, ready.metadata, preset)
                }
            }
        }
    }

    fun reset() {
        _uiState.value = VideoCropUiState.Idle
    }
}
