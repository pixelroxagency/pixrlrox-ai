package com.example.ui.screens.security

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.SecurityRepository
import com.example.data.repository.SecurityStatusData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface SecurityUiState {
    object Loading : SecurityUiState
    data class Success(
        val data: SecurityStatusData,
        val isRefreshingSsl: Boolean = false,
        val actionMessage: String? = null
    ) : SecurityUiState
    data class Error(val message: String) : SecurityUiState
}

class SecurityViewModel(
    private val securityRepository: SecurityRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<SecurityUiState>(SecurityUiState.Loading)
    val uiState: StateFlow<SecurityUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _uiState.value = SecurityUiState.Loading
            refreshInternal()
        }
    }

    fun refresh() {
        viewModelScope.launch {
            refreshInternal()
        }
    }

    private suspend fun refreshInternal() {
        val data = securityRepository.fetchSecurityStatus()
        _uiState.value = SecurityUiState.Success(data = data)
    }

    fun refreshSslCerts() {
        viewModelScope.launch {
            val current = _uiState.value
            if (current is SecurityUiState.Success) {
                _uiState.value = current.copy(isRefreshingSsl = true)
                val success = securityRepository.refreshSslCertificates()
                val refreshed = securityRepository.fetchSecurityStatus()
                _uiState.value = SecurityUiState.Success(
                    data = refreshed,
                    isRefreshingSsl = false,
                    actionMessage = if (success) "SSL Certificates refreshed!" else "Failed to refresh SSL certs."
                )
            }
        }
    }

    fun saveConfig(baseUrl: String, apiKey: String) {
        viewModelScope.launch {
            securityRepository.saveConfig(baseUrl, apiKey)
            refresh()
        }
    }
}
