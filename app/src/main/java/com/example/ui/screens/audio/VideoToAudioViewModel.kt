package com.example.ui.screens.audio

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.audio.AudioMetadata
import com.example.core.audio.AudioOutputFormat
import com.example.core.audio.AudioOutputPublisher
import com.example.core.audio.AudioPublishResult
import com.example.core.audio.AudioQualityPreset
import com.example.core.audio.IVideoToAudioEngine
import com.example.core.audio.Mp3Bitrate
import com.example.core.audio.VideoToAudioEngine
import com.example.core.audio.VideoToAudioResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class VideoToAudioViewModel(
    application: Application,
    private val engine: IVideoToAudioEngine,
    private val publisher: AudioOutputPublisher
) : AndroidViewModel(application) {

    constructor(application: Application) : this(
        application = application,
        engine = VideoToAudioEngine(application),
        publisher = AudioOutputPublisher(application)
    )

    private val context: Context get() = getApplication()

    private val _uiState = MutableStateFlow<VideoToAudioUiState>(VideoToAudioUiState.NoVideoSelected)
    val uiState: StateFlow<VideoToAudioUiState> = _uiState.asStateFlow()

    fun onVideoSelected(uri: Uri) {
        viewModelScope.launch {
            try {
                val metadata = AudioMetadata.extract(context, uri)
                if (!metadata.hasAudioTrack) {
                    _uiState.value = VideoToAudioUiState.Error(
                        sourceUri = uri,
                        metadata = metadata,
                        message = "No audio track was found in this video."
                    )
                    return@launch
                }

                _uiState.value = VideoToAudioUiState.Ready(
                    uri = uri,
                    metadata = metadata,
                    selectedFormat = AudioOutputFormat.MP3,
                    selectedMp3Bitrate = Mp3Bitrate.HIGH_192,
                    selectedM4aPreset = if (metadata.canDirectRemux) AudioQualityPreset.DIRECT_REMUX else AudioQualityPreset.HIGH_192
                )
            } catch (e: Exception) {
                _uiState.value = VideoToAudioUiState.Error(
                    sourceUri = uri,
                    metadata = null,
                    message = "Error analyzing video: ${e.localizedMessage ?: "Unknown error"}"
                )
            }
        }
    }

    fun selectFormat(format: AudioOutputFormat) {
        val currentState = _uiState.value as? VideoToAudioUiState.Ready ?: return
        _uiState.value = currentState.copy(selectedFormat = format)
    }

    fun selectMp3Bitrate(bitrate: Mp3Bitrate) {
        val currentState = _uiState.value as? VideoToAudioUiState.Ready ?: return
        _uiState.value = currentState.copy(selectedMp3Bitrate = bitrate)
    }

    fun selectM4aPreset(preset: AudioQualityPreset) {
        val currentState = _uiState.value as? VideoToAudioUiState.Ready ?: return
        _uiState.value = currentState.copy(selectedM4aPreset = preset)
    }

    fun startExtraction() {
        val currentState = _uiState.value as? VideoToAudioUiState.Ready ?: return
        val sourceUri = currentState.uri
        val metadata = currentState.metadata
        val format = currentState.selectedFormat
        val mp3Bitrate = currentState.selectedMp3Bitrate
        val m4aPreset = currentState.selectedM4aPreset

        val initialText = if (format == AudioOutputFormat.MP3) {
            "Preparing MP3 encoding..."
        } else if (m4aPreset == AudioQualityPreset.DIRECT_REMUX) {
            "Extracting raw audio track..."
        } else {
            "Encoding AAC audio track..."
        }

        _uiState.value = VideoToAudioUiState.Extracting(
            uri = sourceUri,
            metadata = metadata,
            progressPercent = 0,
            statusText = initialText
        )

        viewModelScope.launch {
            val result = engine.extractAudio(
                sourceUri = sourceUri,
                format = format,
                mp3Bitrate = mp3Bitrate,
                m4aPreset = m4aPreset,
                onProgress = { progress ->
                    val extractState = _uiState.value as? VideoToAudioUiState.Extracting
                    if (extractState != null) {
                        val text = if (progress >= 95) {
                            "Saving to Music/PixelRox..."
                        } else if (format == AudioOutputFormat.MP3) {
                            "Converting to MP3 ($progress%)..."
                        } else {
                            "Extracting audio ($progress%)..."
                        }
                        _uiState.value = extractState.copy(
                            progressPercent = progress,
                            statusText = text
                        )
                    }
                }
            )

            when (result) {
                is VideoToAudioResult.Success -> {
                    val displayName = AudioOutputPublisher.generateSafeAudioDisplayName(
                        rawName = metadata.displayName,
                        format = result.format
                    )
                    val publishResult = publisher.publishAudio(
                        sourceFile = result.tempFile,
                        desiredDisplayName = displayName,
                        format = result.format
                    )

                    if (result.tempFile.exists()) {
                        result.tempFile.delete()
                    }

                    when (publishResult) {
                        is AudioPublishResult.Success -> {
                            _uiState.value = VideoToAudioUiState.Success(
                                outputUri = publishResult.contentUri,
                                savedFileName = publishResult.displayName,
                                durationMs = result.durationMs,
                                fileSizeBytes = publishResult.fileSizeBytes,
                                format = result.format,
                                bitrateKbps = result.bitrateKbps,
                                isDirectRemux = result.isDirectRemux
                            )
                        }
                        is AudioPublishResult.Failure -> {
                            _uiState.value = VideoToAudioUiState.Error(
                                sourceUri = sourceUri,
                                metadata = metadata,
                                message = "Failed to save audio file: ${publishResult.errorMessage}"
                            )
                        }
                    }
                }
                is VideoToAudioResult.Failure -> {
                    _uiState.value = VideoToAudioUiState.Error(
                        sourceUri = sourceUri,
                        metadata = metadata,
                        message = result.message
                    )
                }
                is VideoToAudioResult.Cancelled -> {
                    _uiState.value = VideoToAudioUiState.Ready(
                        uri = sourceUri,
                        metadata = metadata,
                        selectedFormat = format,
                        selectedMp3Bitrate = mp3Bitrate,
                        selectedM4aPreset = m4aPreset
                    )
                }
            }
        }
    }

    fun cancelExtraction() {
        engine.cancel()
    }

    fun resetToSelect() {
        _uiState.value = VideoToAudioUiState.NoVideoSelected
    }
}
