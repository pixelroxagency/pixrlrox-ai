package com.example.ui.screens.tasbih

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.database.dao.tasbih.TasbihDao
import com.example.core.database.entity.tasbih.TasbihSessionEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class TasbihViewModel(private val tasbihDao: TasbihDao) : ViewModel() {
    
    val allSessions: StateFlow<List<TasbihSessionEntity>> = tasbihDao.getAllSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _currentSession = MutableStateFlow<TasbihSessionEntity?>(null)
    val currentSession: StateFlow<TasbihSessionEntity?> = _currentSession.asStateFlow()

    init {
        viewModelScope.launch {
            val sessions = tasbihDao.getAllSessions().stateIn(viewModelScope).value
            if (sessions.isEmpty()) {
                val defaultSession = TasbihSessionEntity(UUID.randomUUID().toString(), "Dhikr", 0, 33, System.currentTimeMillis())
                tasbihDao.insertSession(defaultSession)
                _currentSession.value = defaultSession
            } else {
                _currentSession.value = sessions.first()
            }
        }
    }

    fun loadSession(session: TasbihSessionEntity) {
        _currentSession.value = session
    }

    fun createSession(name: String, target: Int) {
        val newSession = TasbihSessionEntity(UUID.randomUUID().toString(), name, 0, target, System.currentTimeMillis())
        viewModelScope.launch {
            tasbihDao.insertSession(newSession)
            _currentSession.value = newSession
        }
    }

    fun increment() {
        val session = _currentSession.value ?: return
        val updated = session.copy(count = session.count + 1, lastUpdated = System.currentTimeMillis())
        _currentSession.value = updated
        saveSession(updated)
    }

    fun decrement() {
        val session = _currentSession.value ?: return
        if (session.count > 0) {
            val updated = session.copy(count = session.count - 1, lastUpdated = System.currentTimeMillis())
            _currentSession.value = updated
            saveSession(updated)
        }
    }

    fun reset() {
        val session = _currentSession.value ?: return
        val updated = session.copy(count = 0, lastUpdated = System.currentTimeMillis())
        _currentSession.value = updated
        saveSession(updated)
    }
    
    fun setTarget(target: Int) {
        val session = _currentSession.value ?: return
        val updated = session.copy(target = target, lastUpdated = System.currentTimeMillis())
        _currentSession.value = updated
        saveSession(updated)
    }

    private fun saveSession(session: TasbihSessionEntity) {
        viewModelScope.launch {
            tasbihDao.updateSession(session)
        }
    }
    
    fun deleteSession(id: String) {
        viewModelScope.launch {
            tasbihDao.deleteSession(id)
            if (_currentSession.value?.id == id) {
                val sessions = allSessions.value
                if (sessions.isNotEmpty()) {
                    _currentSession.value = sessions.first()
                } else {
                    val defaultSession = TasbihSessionEntity(UUID.randomUUID().toString(), "Dhikr", 0, 33, System.currentTimeMillis())
                    tasbihDao.insertSession(defaultSession)
                    _currentSession.value = defaultSession
                }
            }
        }
    }
}
