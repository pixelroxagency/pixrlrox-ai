package com.example.ui.screens.website

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.database.entity.website.WebsiteEntity
import com.example.data.repository.WebsiteRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class WebsiteUiState(
    val websites: List<WebsiteEntity> = emptyList(),
    val searchQuery: String = "",
    val environmentFilter: String = "ALL", // "ALL", "Production", "Staging", "Internal Service", "Client Website"
    val isCheckingStatus: Boolean = false,
    val selectedWebsiteForCheck: String? = null
)

class WebsiteViewModel(
    private val websiteRepository: WebsiteRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _environmentFilter = MutableStateFlow("ALL")
    private val _isCheckingStatus = MutableStateFlow(false)

    private val _uiState = MutableStateFlow(WebsiteUiState())
    val uiState: StateFlow<WebsiteUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                websiteRepository.getAllWebsites(),
                _searchQuery,
                _environmentFilter,
                _isCheckingStatus
            ) { websites, query, env, checking ->
                val filtered = websites.filter { site ->
                    val matchesQuery = query.isBlank() ||
                            site.name.contains(query, ignoreCase = true) ||
                            site.domain.contains(query, ignoreCase = true) ||
                            site.url.contains(query, ignoreCase = true)
                    val matchesEnv = env == "ALL" || site.environment.equals(env, ignoreCase = true)
                    matchesQuery && matchesEnv
                }
                WebsiteUiState(
                    websites = filtered,
                    searchQuery = query,
                    environmentFilter = env,
                    isCheckingStatus = checking
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setEnvironmentFilter(env: String) {
        _environmentFilter.value = env
    }

    fun addWebsite(
        name: String,
        domain: String,
        url: String,
        environment: String,
        notes: String,
        clientId: String?
    ) {
        viewModelScope.launch {
            websiteRepository.addWebsite(
                name = name,
                domain = domain,
                url = url,
                environment = environment,
                notes = notes,
                clientId = clientId
            )
        }
    }

    fun checkStatus(website: WebsiteEntity) {
        viewModelScope.launch {
            _isCheckingStatus.value = true
            websiteRepository.checkWebsiteStatus(website)
            _isCheckingStatus.value = false
        }
    }

    fun deleteWebsite(id: String) {
        viewModelScope.launch {
            websiteRepository.deleteWebsite(id)
        }
    }
}
