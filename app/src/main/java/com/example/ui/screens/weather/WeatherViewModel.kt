package com.example.ui.screens.weather

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.WeatherData
import com.example.data.repository.WeatherRepository
import com.example.data.repository.AppLocationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class WeatherViewModel(
    private val repository: WeatherRepository,
    private val appLocationRepository: AppLocationRepository
) : ViewModel() {
    private val _weatherData = MutableStateFlow<WeatherData?>(null)
    val weatherData: StateFlow<WeatherData?> = _weatherData.asStateFlow()

    private val _isInitialLoading = MutableStateFlow(false)
    val isInitialLoading: StateFlow<Boolean> = _isInitialLoading.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _searchResults = MutableStateFlow<List<Pair<String, Pair<Double, Double>>>>(emptyList())
    val searchResults: StateFlow<List<Pair<String, Pair<Double, Double>>>> = _searchResults.asStateFlow()

    init {
        viewModelScope.launch {
            appLocationRepository.savedLocation.collect { location ->
                // Show cached weather immediately if available
                val cached = repository.getCachedWeather(location.displayName)
                if (cached != null) {
                    _weatherData.value = cached
                }
                loadWeather(location.displayName, location.latitude, location.longitude)
            }
        }
    }

    fun loadWeather(locationName: String, lat: Double, lon: Double) {
        viewModelScope.launch {
            if (_weatherData.value == null) {
                _isInitialLoading.value = true
                _errorMessage.value = null
            } else {
                _isRefreshing.value = true
            }
            try {
                val data = repository.fetchWeather(locationName, lat, lon)
                _weatherData.value = data
                _errorMessage.value = null
            } catch (e: Exception) {
                if (_weatherData.value == null) {
                    _errorMessage.value = e.localizedMessage ?: "Failed to load weather"
                } else {
                    Log.e("WeatherViewModel", "Background refresh failed, keeping stale data", e)
                }
            } finally {
                _isInitialLoading.value = false
                _isRefreshing.value = false
            }
        }
    }

    fun searchLocations(query: String) {
        viewModelScope.launch {
            if (query.isNotBlank()) {
                val results = repository.searchLocation(query)
                _searchResults.value = results
            } else {
                _searchResults.value = emptyList()
            }
        }
    }
}
