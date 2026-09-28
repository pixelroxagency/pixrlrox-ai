package com.example.ui.screens.productivity

import androidx.lifecycle.ViewModel
import com.example.data.repository.PreferencesRepository
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class PomodoroViewModel(private val repository: PreferencesRepository) : ViewModel() {
    private val _remainingTime = MutableStateFlow(25 * 60 * 1000L)
    val remainingTime: StateFlow<Long> = _remainingTime

    fun start() {}
    fun pause() {}
    fun reset() {}
}
