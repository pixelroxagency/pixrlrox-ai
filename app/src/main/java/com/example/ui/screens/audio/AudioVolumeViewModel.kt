package com.example.ui.screens.audio

import android.app.Application
import android.media.MediaPlayer
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.audio.AudioMetadata
import com.example.core.audio.AudioOutputFormat
import com.example.core.audio.AudioOutputPublisher
import com.example.core.audio.AudioPublishResult
import com.example.core.audio.AudioVolumeEngine
import com.example.core.audio.AudioVolumeResult
import com.example.core.audio.VolumePreset
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface AudioVolumeUiState {
    data object Idle : AudioVolumeUiState

    data class Loaded(
        val sourceUri: Uri,
        val displayName: String,
        val durationMs: Long,
        val fileSizeBytes: Long,
        val audioCodec: String,
        val gainFactor: Float = 1.5f,
        val targetFormat: AudioOutputFormat = AudioOutputFormat.MP3,
        val isPreviewPlaying: Boolean = false
    ) : AudioVolumeUiState

    data class Processing(
        val progress: Int,
        val statusMessage: String
    ) : AudioVolumeUiState

    data class Success(
        val contentUri: Uri,
        val displayName: String,
        val durationMs: Long,
        val fileSizeBytes: Long,
        val format: AudioOutputFormat,
        val gainFactor: Float,
        val isResultPlaying: Boolean = false
    ) : AudioVolumeUiState

    data class Error(val message: String) : AudioVolumeUiState
}

class AudioVolumeViewModel(application: Application) : AndroidViewModel(application) {

    private val engine = AudioVolumeEngine(application)
    private val publisher = AudioOutputPublisher(application)

    private val _uiState = MutableStateFlow<AudioVolumeUiState>(AudioVolumeUiState.Idle)
    val uiState: StateFlow<AudioVolumeUiState> = _uiState.asStateFlow()

    private var mediaPlayer: MediaPlayer? = null
    private var playbackJob: Job? = null

    fun selectAudio(uri: Uri) {
        stopPlayback()
        viewModelScope.launch {
            try {
                val metadata = AudioMetadata.extract(getApplication(), uri)
                if (!metadata.hasAudioTrack || metadata.durationMs <= 0L) {
                    _uiState.value = AudioVolumeUiState.Error("Selected file does not contain a supported audio track.")
                    return@launch
                }

                _uiState.value = AudioVolumeUiState.Loaded(
                    sourceUri = uri,
                    displayName = metadata.displayName,
                    durationMs = metadata.durationMs,
                    fileSizeBytes = metadata.fileSizeBytes,
                    audioCodec = metadata.audioCodec,
                    gainFactor = 1.5f,
                    targetFormat = AudioOutputFormat.MP3
                )
            } catch (e: Exception) {
                _uiState.value = AudioVolumeUiState.Error("Failed to inspect audio file: ${e.message}")
            }
        }
    }

    fun setGainFactor(gain: Float) {
        val current = _uiState.value as? AudioVolumeUiState.Loaded ?: return
        _uiState.update { current.copy(gainFactor = gain.coerceIn(0.25f, 3.0f)) }
    }

    fun setTargetFormat(format: AudioOutputFormat) {
        val current = _uiState.value as? AudioVolumeUiState.Loaded ?: return
        _uiState.update { current.copy(targetFormat = format) }
    }

    fun togglePreviewPlayback() {
        val current = _uiState.value as? AudioVolumeUiState.Loaded ?: return
        if (current.isPreviewPlaying) {
            stopPlayback()
            _uiState.update { current.copy(isPreviewPlaying = false) }
        } else {
            stopPlayback()
            try {
                val mp = MediaPlayer().apply {
                    setDataSource(getApplication(), current.sourceUri)
                    prepare()
                    start()
                    setOnCompletionListener {
                        _uiState.update {
                            if (it is AudioVolumeUiState.Loaded) it.copy(isPreviewPlaying = false) else it
                        }
                    }
                }
                mediaPlayer = mp
                _uiState.update { current.copy(isPreviewPlaying = true) }
            } catch (_: Exception) {
                stopPlayback()
                _uiState.update { current.copy(isPreviewPlaying = false) }
            }
        }
    }

    fun toggleResultPlayback() {
        val current = _uiState.value as? AudioVolumeUiState.Success ?: return
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
                            if (it is AudioVolumeUiState.Success) it.copy(isResultPlaying = false) else it
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

    fun boostAndExport() {
        val current = _uiState.value as? AudioVolumeUiState.Loaded ?: return
        stopPlayback()

        viewModelScope.launch {
            _uiState.value = AudioVolumeUiState.Processing(
                progress = 0,
                statusMessage = "Modifying PCM audio sample gain..."
            )

            val boostResult = engine.boostVolume(
                sourceUri = current.sourceUri,
                gainFactor = current.gainFactor,
                targetFormat = current.targetFormat,
                onProgress = { p ->
                    _uiState.update {
                        if (it is AudioVolumeUiState.Processing) {
                            it.copy(progress = p, statusMessage = "Boosting: $p%")
                        } else it
                    }
                }
            )

            when (boostResult) {
                is AudioVolumeResult.Success -> {
                    _uiState.value = AudioVolumeUiState.Processing(
                        progress = 95,
                        statusMessage = "Saving to Music/PixelRox..."
                    )

                    val baseName = current.displayName.substringBeforeLast(".")
                    val gainTag = "${(current.gainFactor * 100).toInt()}pct"
                    val publishResult = publisher.publishAudio(
                        sourceFile = boostResult.tempFile,
                        desiredDisplayName = "${baseName}_boost_${gainTag}",
                        format = boostResult.format
                    )

                    try { boostResult.tempFile.delete() } catch (_: Exception) {}

                    when (publishResult) {
                        is AudioPublishResult.Success -> {
                            _uiState.value = AudioVolumeUiState.Success(
                                contentUri = publishResult.contentUri,
                                displayName = publishResult.displayName,
                                durationMs = boostResult.durationMs,
                                fileSizeBytes = publishResult.fileSizeBytes,
                                format = boostResult.format,
                                gainFactor = boostResult.gainFactor
                            )
                        }
                        is AudioPublishResult.Failure -> {
                            _uiState.value = AudioVolumeUiState.Error("Failed to save audio to MediaStore: ${publishResult.errorMessage}")
                        }
                    }
                }
                is AudioVolumeResult.Failure -> {
                    _uiState.value = AudioVolumeUiState.Error(boostResult.errorMessage)
                }
                is AudioVolumeResult.Cancelled -> {
                    _uiState.value = current
                }
            }
        }
    }

    fun reset() {
        stopPlayback()
        _uiState.value = AudioVolumeUiState.Idle
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
