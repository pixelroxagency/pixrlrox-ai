package com.example.ui.screens.audio

import android.app.Application
import android.media.MediaPlayer
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.audio.AudioMergeTrack
import com.example.core.audio.AudioMergerEngine
import com.example.core.audio.AudioMergerResult
import com.example.core.audio.AudioOutputFormat
import com.example.core.audio.AudioOutputPublisher
import com.example.core.audio.AudioPublishResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface AudioMergerUiState {
    data object Idle : AudioMergerUiState

    data class Loaded(
        val tracks: List<AudioMergeTrack>,
        val targetFormat: AudioOutputFormat = AudioOutputFormat.M4A,
        val totalDurationMs: Long = tracks.sumOf { it.durationMs },
        val totalSizeBytes: Long = tracks.sumOf { it.fileSizeBytes }
    ) : AudioMergerUiState

    data class Processing(
        val progress: Int,
        val statusMessage: String
    ) : AudioMergerUiState

    data class Success(
        val contentUri: Uri,
        val displayName: String,
        val durationMs: Long,
        val fileSizeBytes: Long,
        val format: AudioOutputFormat,
        val trackCount: Int,
        val isResultPlaying: Boolean = false
    ) : AudioMergerUiState

    data class Error(val message: String) : AudioMergerUiState
}

class AudioMergerViewModel(application: Application) : AndroidViewModel(application) {

    private val engine = AudioMergerEngine(application)
    private val publisher = AudioOutputPublisher(application)

    private val _uiState = MutableStateFlow<AudioMergerUiState>(AudioMergerUiState.Idle)
    val uiState: StateFlow<AudioMergerUiState> = _uiState.asStateFlow()

    private var mediaPlayer: MediaPlayer? = null
    private var playbackJob: Job? = null

    fun addTracks(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            try {
                val newTracks = engine.inspectTracks(uris)
                if (newTracks.isEmpty()) {
                    _uiState.value = AudioMergerUiState.Error("No readable audio tracks could be extracted from the selected files.")
                    return@launch
                }

                val currentTracks = when (val state = _uiState.value) {
                    is AudioMergerUiState.Loaded -> state.tracks
                    else -> emptyList()
                }

                val combined = currentTracks + newTracks
                _uiState.value = AudioMergerUiState.Loaded(
                    tracks = combined,
                    targetFormat = (_uiState.value as? AudioMergerUiState.Loaded)?.targetFormat ?: AudioOutputFormat.M4A
                )
            } catch (e: Exception) {
                _uiState.value = AudioMergerUiState.Error("Failed to inspect audio tracks: ${e.message}")
            }
        }
    }

    fun moveTrackUp(index: Int) {
        val current = _uiState.value as? AudioMergerUiState.Loaded ?: return
        if (index <= 0 || index >= current.tracks.size) return
        val list = current.tracks.toMutableList()
        val temp = list[index]
        list[index] = list[index - 1]
        list[index - 1] = temp
        _uiState.update { current.copy(tracks = list) }
    }

    fun moveTrackDown(index: Int) {
        val current = _uiState.value as? AudioMergerUiState.Loaded ?: return
        if (index < 0 || index >= current.tracks.size - 1) return
        val list = current.tracks.toMutableList()
        val temp = list[index]
        list[index] = list[index + 1]
        list[index + 1] = temp
        _uiState.update { current.copy(tracks = list) }
    }

    fun removeTrack(index: Int) {
        val current = _uiState.value as? AudioMergerUiState.Loaded ?: return
        if (index < 0 || index >= current.tracks.size) return
        val list = current.tracks.toMutableList()
        list.removeAt(index)
        if (list.isEmpty()) {
            _uiState.value = AudioMergerUiState.Idle
        } else {
            _uiState.update { current.copy(tracks = list) }
        }
    }

    fun setTargetFormat(format: AudioOutputFormat) {
        val current = _uiState.value as? AudioMergerUiState.Loaded ?: return
        _uiState.update { current.copy(targetFormat = format) }
    }

    fun toggleResultPlayback() {
        val current = _uiState.value as? AudioMergerUiState.Success ?: return
        if (current.isResultPlaying) {
            stopPlayback()
            _uiState.update { current.copy(isResultPlaying = false) }
        } else {
            stopPlayback()
            try {
                val mp = MediaPlayer().apply {
                    setDataSource(getApplication(), current.contentUri)
                    prepare()
                    start()
                    setOnCompletionListener {
                        _uiState.update {
                            if (it is AudioMergerUiState.Success) it.copy(isResultPlaying = false) else it
                        }
                    }
                }
                mediaPlayer = mp
                _uiState.update { current.copy(isResultPlaying = true) }
            } catch (_: Exception) {
                stopPlayback()
                _uiState.update { current.copy(isResultPlaying = false) }
            }
        }
    }

    fun mergeAndExport() {
        val current = _uiState.value as? AudioMergerUiState.Loaded ?: return
        if (current.tracks.size < 2) {
            _uiState.value = AudioMergerUiState.Error("Please select at least 2 tracks to merge.")
            return
        }
        stopPlayback()

        viewModelScope.launch {
            _uiState.value = AudioMergerUiState.Processing(
                progress = 0,
                statusMessage = "Sequencing and merging ${current.tracks.size} audio tracks..."
            )

            val mergeResult = engine.mergeAudio(
                tracks = current.tracks,
                targetFormat = current.targetFormat,
                onProgress = { p ->
                    _uiState.update {
                        if (it is AudioMergerUiState.Processing) {
                            it.copy(progress = p, statusMessage = "Merging: $p%")
                        } else it
                    }
                }
            )

            when (mergeResult) {
                is AudioMergerResult.Success -> {
                    _uiState.value = AudioMergerUiState.Processing(
                        progress = 95,
                        statusMessage = "Saving to Music/PixelRox..."
                    )

                    val firstTrackName = current.tracks.first().displayName.substringBeforeLast(".")
                    val publishResult = publisher.publishAudio(
                        sourceFile = mergeResult.tempFile,
                        desiredDisplayName = "merged_${firstTrackName}_${current.tracks.size}tracks",
                        format = mergeResult.format
                    )

                    try { mergeResult.tempFile.delete() } catch (_: Exception) {}

                    when (publishResult) {
                        is AudioPublishResult.Success -> {
                            _uiState.value = AudioMergerUiState.Success(
                                contentUri = publishResult.contentUri,
                                displayName = publishResult.displayName,
                                durationMs = mergeResult.durationMs,
                                fileSizeBytes = publishResult.fileSizeBytes,
                                format = mergeResult.format,
                                trackCount = mergeResult.trackCount
                            )
                        }
                        is AudioPublishResult.Failure -> {
                            _uiState.value = AudioMergerUiState.Error("Failed to save audio to MediaStore: ${publishResult.errorMessage}")
                        }
                    }
                }
                is AudioMergerResult.Failure -> {
                    _uiState.value = AudioMergerUiState.Error(mergeResult.errorMessage)
                }
                is AudioMergerResult.Cancelled -> {
                    _uiState.value = current
                }
            }
        }
    }

    fun reset() {
        stopPlayback()
        _uiState.value = AudioMergerUiState.Idle
    }

    private fun stopPlayback() {
        playbackJob?.cancel()
        playbackJob = null
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (_: Exception) {}
        mediaPlayer = null
    }

    override fun onCleared() {
        super.onCleared()
        stopPlayback()
    }
}
