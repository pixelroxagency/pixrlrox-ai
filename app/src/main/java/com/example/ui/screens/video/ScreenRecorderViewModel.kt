package com.example.core.recorder

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ScreenRecorderViewModel(application: Application) : AndroidViewModel(application) {

    private val _recordingMode = MutableStateFlow(RecordingMode.SCREEN_ONLY)
    val recordingMode: StateFlow<RecordingMode> = _recordingMode.asStateFlow()

    private val _videoQuality = MutableStateFlow(VideoQuality.FULL_HD_1080P)
    val videoQuality: StateFlow<VideoQuality> = _videoQuality.asStateFlow()

    private val _frameRate = MutableStateFlow(FrameRate.FPS_30)
    val frameRate: StateFlow<FrameRate> = _frameRate.asStateFlow()

    private val _orientation = MutableStateFlow(RecordingOrientation.AUTO)
    val orientation: StateFlow<RecordingOrientation> = _orientation.asStateFlow()

    private val _floatingControlsEnabled = MutableStateFlow(true)
    val floatingControlsEnabled: StateFlow<Boolean> = _floatingControlsEnabled.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _countdownValue = MutableStateFlow<Int?>(null)
    val countdownValue: StateFlow<Int?> = _countdownValue.asStateFlow()

    private val _recordings = MutableStateFlow<List<ScreenRecordItem>>(emptyList())
    val recordings: StateFlow<List<ScreenRecordItem>> = _recordings.asStateFlow()

    init {
        loadRecordings()
    }

    fun loadRecordings() {
        viewModelScope.launch {
            val list = ScreenRecordRepository.queryRecordings(getApplication())
            _recordings.value = list
        }
    }

    fun deleteRecording(item: ScreenRecordItem, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = ScreenRecordRepository.deleteRecording(getApplication(), item.uri)
            if (success) {
                loadRecordings()
            }
            onComplete(success)
        }
    }

    fun renameRecording(item: ScreenRecordItem, newName: String, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            val finalName = if (newName.endsWith(".mp4", ignoreCase = true)) newName else "$newName.mp4"
            val success = ScreenRecordRepository.renameRecording(getApplication(), item.uri, finalName)
            if (success) {
                loadRecordings()
            }
            onComplete(success)
        }
    }

    fun setRecordingMode(mode: RecordingMode) {
        _recordingMode.value = mode
    }

    fun setVideoQuality(quality: VideoQuality) {
        _videoQuality.value = quality
    }

    fun setFrameRate(fps: FrameRate) {
        _frameRate.value = fps
    }

    fun setOrientation(orientation: RecordingOrientation) {
        _orientation.value = orientation
    }

    fun setFloatingControlsEnabled(enabled: Boolean) {
        _floatingControlsEnabled.value = enabled
    }

    fun startCountdown(onReadyToRecord: () -> Unit) {
        viewModelScope.launch {
            for (i in 3 downTo 1) {
                _countdownValue.value = i
                kotlinx.coroutines.delay(1000)
            }
            _countdownValue.value = null
            _isRecording.value = true
            onReadyToRecord()
        }
    }

    fun setRecordingState(recording: Boolean) {
        _isRecording.value = recording
    }
}
