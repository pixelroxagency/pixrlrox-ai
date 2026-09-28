package com.example.ui.screens.audio

import android.app.Application
import android.media.MediaPlayer
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.audio.AudioCutterEngine
import com.example.core.audio.AudioCutterResult
import com.example.core.audio.AudioMetadata
import com.example.core.audio.AudioOutputFormat
import com.example.core.audio.AudioOutputPublisher
import com.example.core.audio.AudioPublishResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

sealed interface AudioCutterUiState {
    data object Idle : AudioCutterUiState

    data class Loaded(
        val sourceUri: Uri,
        val displayName: String,
        val durationMs: Long,
        val fileSizeBytes: Long,
        val audioCodec: String,
        val startMs: Long = 0L,
        val endMs: Long = durationMs,
        val targetFormat: AudioOutputFormat = AudioOutputFormat.MP3,
        val isPreviewPlaying: Boolean = false,
        val previewPositionMs: Long = 0L
    ) : AudioCutterUiState

    data class Processing(
        val progress: Int,
        val statusMessage: String
    ) : AudioCutterUiState

    data class Success(
        val contentUri: Uri,
        val displayName: String,
        val durationMs: Long,
        val fileSizeBytes: Long,
        val mimeType: String,
        val isResultPlaying: Boolean = false
    ) : AudioCutterUiState

    data class Error(val message: String) : AudioCutterUiState
}

class AudioCutterViewModel(application: Application) : AndroidViewModel(application) {

    private val engine = AudioCutterEngine(application)
    private val publisher = AudioOutputPublisher(application)

    private val _uiState = MutableStateFlow<AudioCutterUiState>(AudioCutterUiState.Idle)
    val uiState: StateFlow<AudioCutterUiState> = _uiState.asStateFlow()

    private var mediaPlayer: MediaPlayer? = null
    private var playbackTrackerJob: Job? = null

    fun selectAudio(uri: Uri) {
        stopPlayback()
        viewModelScope.launch {
            try {
                val metadata = AudioMetadata.extract(getApplication(), uri)
                if (!metadata.hasAudioTrack || metadata.durationMs <= 0L) {
                    _uiState.value = AudioCutterUiState.Error("Selected file does not contain a supported audio track.")
                    return@launch
                }

                val detectedFormat = if (metadata.displayName.endsWith(".m4a", ignoreCase = true) || metadata.canDirectRemux) {
                    AudioOutputFormat.M4A
                } else {
                    AudioOutputFormat.MP3
                }

                _uiState.value = AudioCutterUiState.Loaded(
                    sourceUri = uri,
                    displayName = metadata.displayName,
                    durationMs = metadata.durationMs,
                    fileSizeBytes = metadata.fileSizeBytes,
                    audioCodec = metadata.audioCodec,
                    startMs = 0L,
                    endMs = metadata.durationMs,
                    targetFormat = detectedFormat
                )
            } catch (e: Exception) {
                _uiState.value = AudioCutterUiState.Error("Failed to inspect audio file: ${e.message}")
            }
        }
    }

    fun updateRange(startMs: Long, endMs: Long) {
        val current = _uiState.value as? AudioCutterUiState.Loaded ?: return
        val clampedStart = startMs.coerceIn(0L, current.durationMs - 500L)
        val clampedEnd = endMs.coerceIn(clampedStart + 500L, current.durationMs)
        stopPlayback()
        _uiState.update {
            current.copy(startMs = clampedStart, endMs = clampedEnd, isPreviewPlaying = false)
        }
    }

    fun setTargetFormat(format: AudioOutputFormat) {
        val current = _uiState.value as? AudioCutterUiState.Loaded ?: return
        _uiState.update { current.copy(targetFormat = format) }
    }

    fun togglePreviewPlayback() {
        val current = _uiState.value as? AudioCutterUiState.Loaded ?: return

        if (current.isPreviewPlaying) {
            stopPlayback()
            _uiState.update { current.copy(isPreviewPlaying = false) }
        } else {
            startPreviewPlayback(current)
        }
    }

    private fun startPreviewPlayback(state: AudioCutterUiState.Loaded) {
        stopPlayback()
        try {
            val mp = MediaPlayer().apply {
                setDataSource(getApplication(), state.sourceUri)
                prepare()
                seekTo(state.startMs.toInt())
                start()
            }
            mediaPlayer = mp
            _uiState.update { state.copy(isPreviewPlaying = true, previewPositionMs = state.startMs) }

            playbackTrackerJob = viewModelScope.launch {
                while (isActive) {
                    delay(100)
                    val player = mediaPlayer ?: break
                    if (!player.isPlaying) {
                        stopPlayback()
                        _uiState.update {
                            if (it is AudioCutterUiState.Loaded) it.copy(isPreviewPlaying = false) else it
                        }
                        break
                    }
                    val pos = player.currentPosition.toLong()
                    if (pos >= state.endMs) {
                        stopPlayback()
                        _uiState.update {
                            if (it is AudioCutterUiState.Loaded) it.copy(isPreviewPlaying = false) else it
                        }
                        break
                    } else {
                        _uiState.update {
                            if (it is AudioCutterUiState.Loaded) it.copy(previewPositionMs = pos) else it
                        }
                    }
                }
            }
        } catch (_: Exception) {
            stopPlayback()
            _uiState.update { state.copy(isPreviewPlaying = false) }
        }
    }

    fun toggleResultPlayback() {
        val current = _uiState.value as? AudioCutterUiState.Success ?: return

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
                            if (it is AudioCutterUiState.Success) it.copy(isResultPlaying = false) else it
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

    fun trimAndExport() {
        val current = _uiState.value as? AudioCutterUiState.Loaded ?: return
        stopPlayback()

        viewModelScope.launch {
            _uiState.value = AudioCutterUiState.Processing(
                progress = 0,
                statusMessage = "Trimming audio track..."
            )

            val trimResult = engine.trimAudio(
                sourceUri = current.sourceUri,
                startMs = current.startMs,
                endMs = current.endMs,
                targetFormat = current.targetFormat,
                onProgress = { progress ->
                    _uiState.update {
                        if (it is AudioCutterUiState.Processing) {
                            it.copy(progress = progress, statusMessage = "Trimming: $progress%")
                        } else it
                    }
                }
            )

            when (trimResult) {
                is AudioCutterResult.Success -> {
                    _uiState.value = AudioCutterUiState.Processing(
                        progress = 95,
                        statusMessage = "Saving to Music/PixelRox..."
                    )

                    val publishResult = publisher.publishAudio(
                        sourceFile = trimResult.tempFile,
                        desiredDisplayName = "trimmed_${current.displayName.substringBeforeLast(".")}",
                        format = trimResult.format
                    )

                    // Clean up temp file
                    try { trimResult.tempFile.delete() } catch (_: Exception) {}

                    when (publishResult) {
                        is AudioPublishResult.Success -> {
                            _uiState.value = AudioCutterUiState.Success(
                                contentUri = publishResult.contentUri,
                                displayName = publishResult.displayName,
                                durationMs = trimResult.durationMs,
                                fileSizeBytes = publishResult.fileSizeBytes,
                                mimeType = publishResult.mimeType
                            )
                        }
                        is AudioPublishResult.Failure -> {
                            _uiState.value = AudioCutterUiState.Error("Failed to save audio: ${publishResult.errorMessage}")
                        }
                    }
                }
                is AudioCutterResult.Failure -> {
                    _uiState.value = AudioCutterUiState.Error(trimResult.errorMessage)
                }
                is AudioCutterResult.Cancelled -> {
                    _uiState.value = current
                }
            }
        }
    }

    fun reset() {
        stopPlayback()
        _uiState.value = AudioCutterUiState.Idle
    }

    private fun stopPlayback() {
        playbackTrackerJob?.cancel()
        playbackTrackerJob = null
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
