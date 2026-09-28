package com.example.ui.screens.productivity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.PreferencesRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class TeleprompterViewModel(private val repository: PreferencesRepository) : ViewModel() {
    val script = repository.teleprompterScript
    val speed = repository.teleprompterSpeed
    val fontSize = repository.teleprompterFontSize

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private var scrollJob: Job? = null

    fun setScript(text: String) = repository.setTeleprompterScript(text)
    fun setSpeed(speed: Float) = repository.setTeleprompterSpeed(speed)
    fun setFontSize(size: Float) = repository.setTeleprompterFontSize(size)

    fun play() {
        _isPlaying.value = true
    }

    fun pause() {
        _isPlaying.value = false
    }

    fun stop() {
        _isPlaying.value = false
    }
}
