package com.example.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.PreferencesRepository
import com.example.data.search.*
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class GlobalSearchUiState(
    val searchQuery: String = "",
    val selectedSourceFilter: String = "ALL",
    val isSearching: Boolean = false,
    val results: List<GlobalSearchResult> = emptyList(),
    val groupedResults: Map<String, List<GlobalSearchResult>> = emptyMap(),
    val recentSearches: List<String> = emptyList()
)

class GlobalSearchViewModel(
    private val providers: List<GlobalSearchProvider>,
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(GlobalSearchUiState())
    val uiState: StateFlow<GlobalSearchUiState> = _uiState.asStateFlow()

    private val _queryFlow = MutableStateFlow("")

    init {
        viewModelScope.launch {
            preferencesRepository.userPreferences.collect { prefs ->
                _uiState.update { it.copy(recentSearches = prefs.recentSearches) }
            }
        }

        @OptIn(FlowPreview::class)
        viewModelScope.launch {
            _queryFlow
                .debounce(300)
                .distinctUntilChanged()
                .collect { query ->
                    executeSearch(query)
                }
        }
    }

    fun onQueryChange(newQuery: String) {
        _uiState.update { it.copy(searchQuery = newQuery) }
        _queryFlow.value = newQuery
    }

    fun setSourceFilter(filter: String) {
        _uiState.update { it.copy(selectedSourceFilter = filter) }
        filterAndGroupResults(_uiState.value.results, filter)
    }

    private suspend fun executeSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.length < 2) {
            _uiState.update { it.copy(isSearching = false, results = emptyList(), groupedResults = emptyMap()) }
            return
        }

        _uiState.update { it.copy(isSearching = true) }

        val allResults = mutableListOf<GlobalSearchResult>()
        providers.forEach { provider ->
            try {
                val res = provider.search(trimmed)
                allResults.addAll(res)
            } catch (_: Exception) {}
        }

        saveRecentSearch(trimmed)

        _uiState.update { it.copy(isSearching = false, results = allResults) }
        filterAndGroupResults(allResults, _uiState.value.selectedSourceFilter)
    }

    private fun filterAndGroupResults(allResults: List<GlobalSearchResult>, filter: String) {
        val filtered = if (filter == "ALL") {
            allResults
        } else {
            allResults.filter { it.source.equals(filter, ignoreCase = true) }
        }

        val grouped = filtered.groupBy { it.source }
        _uiState.update { it.copy(groupedResults = grouped) }
    }

    private fun saveRecentSearch(query: String) {
        viewModelScope.launch {
            val current = _uiState.value.recentSearches.toMutableList()
            current.remove(query)
            current.add(0, query)
            val updated = current.take(10)
            preferencesRepository.saveRecentSearches(updated)
        }
    }

    fun clearRecentSearches() {
        viewModelScope.launch {
            preferencesRepository.saveRecentSearches(emptyList())
        }
    }
}
