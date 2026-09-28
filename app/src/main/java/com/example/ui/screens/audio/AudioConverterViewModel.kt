package com.example.ui.screens.audio

import android.app.Application
import android.media.MediaPlayer
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.audio.AudioConverterEngine
import com.example.core.audio.AudioConverterResult
import com.example.core.audio.AudioMetadata
import com.example.core.audio.AudioOutputFormat
import com.example.core.audio.AudioOutputPublisher
import com.example.core.audio.AudioPublishResult
import com.example.core.audio.Mp3Bitrate
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

sealed interface AudioConverterUiState {
    data object Idle : AudioConverterUiState

    data class Loaded(
        val sourceUri: Uri,
        val displayName: String,
        val durationMs: Long,
        val fileSizeBytes: Long,
        val audioCodec: String,
        val sampleRate: Int,
        val channelCount: Int,
        val targetFormat: AudioOutputFormat = AudioOutputFormat.MP3,
        val mp3Bitrate: Mp3Bitrate = Mp3Bitrate.HIGH_192,
        val isPreviewPlaying: Boolean = false
    ) : AudioConverterUiState

    data class Processing(
        val progress: Int,
        val statusMessage: String
    ) : AudioConverterUiState

    data class Success(
        val contentUri: Uri,
        val displayName: String,
        val durationMs: Long,
        val fileSizeBytes: Long,
        val format: AudioOutputFormat,
        val isResultPlaying: Boolean = false
    ) : AudioConverterUiState

    data class Error(val message: String) : AudioConverterUiState
}

class AudioConverterViewModel(application: Application) : AndroidViewModel(application) {

    private val engine = AudioConverterEngine(application)
    private val publisher = AudioOutputPublisher(application)

    private val _uiState = MutableStateFlow<AudioConverterUiState>(AudioConverterUiState.Idle)
    val uiState: StateFlow<AudioConverterUiState> = _uiState.asStateFlow()

    private var mediaPlayer: MediaPlayer? = null
    private var playbackJob: Job? = null

    fun selectAudio(uri: Uri) {
        stopPlayback()
        viewModelScope.launch {
            try {
                val metadata = AudioMetadata.extract(getApplication(), uri)
                if (!metadata.hasAudioTrack || metadata.durationMs <= 0L) {
                    _uiState.value = AudioConverterUiState.Error("Selected file does not contain a supported audio track.")
                    return@launch
                }

                val defaultTarget = if (metadata.displayName.endsWith(".mp3", ignoreCase = true)) {
                    AudioOutputFormat.M4A
                } else {
                    AudioOutputFormat.MP3
                }

                _uiState.value = AudioConverterUiState.Loaded(
                    sourceUri = uri,
                    displayName = metadata.displayName,
                    durationMs = metadata.durationMs,
                    fileSizeBytes = metadata.fileSizeBytes,
                    audioCodec = metadata.audioCodec,
                    sampleRate = metadata.sampleRate,
                    channelCount = metadata.channelCount,
                    targetFormat = defaultTarget
                )
            } catch (e: Exception) {
                _uiState.value = AudioConverterUiState.Error("Failed to inspect audio file: ${e.message}")
            }
        }
    }

    fun setTargetFormat(format: AudioOutputFormat) {
        val current = _uiState.value as? AudioConverterUiState.Loaded ?: return
        _uiState.update { current.copy(targetFormat = format) }
    }

    fun setMp3Bitrate(bitrate: Mp3Bitrate) {
        val current = _uiState.value as? AudioConverterUiState.Loaded ?: return
        _uiState.update { current.copy(mp3Bitrate = bitrate) }
    }

    fun togglePreviewPlayback() {
        val current = _uiState.value as? AudioConverterUiState.Loaded ?: return
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
                            if (it is AudioConverterUiState.Loaded) it.copy(isPreviewPlaying = false) else it
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
        val current = _uiState.value as? AudioConverterUiState.Success ?: return
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
                            if (it is AudioConverterUiState.Success) it.copy(isResultPlaying = false) else it
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

    fun convertAndExport() {
        val current = _uiState.value as? AudioConverterUiState.Loaded ?: return
        stopPlayback()

        viewModelScope.launch {
            _uiState.value = AudioConverterUiState.Processing(
                progress = 0,
                statusMessage = "Converting audio to ${current.targetFormat.title}..."
            )

            val convResult = engine.convertAudio(
                sourceUri = current.sourceUri,
                targetFormat = current.targetFormat,
                bitrateKbps = current.mp3Bitrate.bitrateKbps,
                onProgress = { p ->
                    _uiState.update {
                        if (it is AudioConverterUiState.Processing) {
                            it.copy(progress = p, statusMessage = "Converting: $p%")
                        } else it
                    }
                }
            )

            when (convResult) {
                is AudioConverterResult.Success -> {
                    _uiState.value = AudioConverterUiState.Processing(
                        progress = 95,
                        statusMessage = "Saving to Music/PixelRox..."
                    )

                    val baseName = current.displayName.substringBeforeLast(".")
                    val publishResult = publisher.publishAudio(
                        sourceFile = convResult.tempFile,
                        desiredDisplayName = "${baseName}_converted",
                        format = convResult.format
                    )

                    try { convResult.tempFile.delete() } catch (_: Exception) {}

                    when (publishResult) {
                        is AudioPublishResult.Success -> {
                            _uiState.value = AudioConverterUiState.Success(
                                contentUri = publishResult.contentUri,
                                displayName = publishResult.displayName,
                                durationMs = convResult.durationMs,
                                fileSizeBytes = publishResult.fileSizeBytes,
                                format = convResult.format
                            )
                        }
                        is AudioPublishResult.Failure -> {
                            _uiState.value = AudioConverterUiState.Error("Failed to save audio to MediaStore: ${publishResult.errorMessage}")
                        }
                    }
                }
                is AudioConverterResult.Failure -> {
                    _uiState.value = AudioConverterUiState.Error(convResult.errorMessage)
                }
                is AudioConverterResult.Cancelled -> {
                    _uiState.value = current
                }
            }
        }
    }

    fun reset() {
        stopPlayback()
        _uiState.value = AudioConverterUiState.Idle
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
