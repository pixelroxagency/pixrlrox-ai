package com.example.ui.screens.alerts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.database.entity.AlertEntity
import com.example.data.repository.AlertRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class UnifiedAlertsUiState(
    val alerts: List<AlertEntity> = emptyList(),
    val unreadCount: Int = 0,
    val searchQuery: String = "",
    val sourceFilter: String = "ALL", // "ALL", "AI", "TASKS", "UPTIME", "BACKUP", "SECURITY", "WEBSITES", "N8N", "VPS", "DOCKER"
    val severityFilter: String = "ALL" // "ALL", "Critical", "High", "Medium", "Low", "Info"
)

class UnifiedAlertsViewModel(
    private val alertRepository: AlertRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _sourceFilter = MutableStateFlow("ALL")
    private val _severityFilter = MutableStateFlow("ALL")

    private val _uiState = MutableStateFlow(UnifiedAlertsUiState())
    val uiState: StateFlow<UnifiedAlertsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                alertRepository.getAllAlerts(),
                alertRepository.getUnreadCount(),
                _searchQuery,
                _sourceFilter,
                _severityFilter
            ) { alerts, unread, query, source, severity ->
                val filtered = alerts.filter { alert ->
                    val matchesQuery = query.isBlank() ||
                            alert.title.contains(query, ignoreCase = true) ||
                            alert.message.contains(query, ignoreCase = true)
                    val matchesSource = source == "ALL" || alert.category.equals(source, ignoreCase = true)
                    val matchesSeverity = severity == "ALL" || alert.severity.equals(severity, ignoreCase = true)
                    matchesQuery && matchesSource && matchesSeverity
                }

                UnifiedAlertsUiState(
                    alerts = filtered,
                    unreadCount = unread,
                    searchQuery = query,
                    sourceFilter = source,
                    severityFilter = severity
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSourceFilter(source: String) {
        _sourceFilter.value = source
    }

    fun setSeverityFilter(severity: String) {
        _severityFilter.value = severity
    }

    fun toggleReadState(id: Long, isRead: Boolean) {
        viewModelScope.launch {
            alertRepository.toggleReadState(id, !isRead)
        }
    }

    fun markAllAsRead() {
        viewModelScope.launch {
            alertRepository.markAllAsRead()
        }
    }

    fun deleteAlert(id: Long) {
        viewModelScope.launch {
            alertRepository.deleteAlert(id)
        }
    }

    fun clearAllAlerts() {
        viewModelScope.launch {
            alertRepository.clearAll()
        }
    }
}
