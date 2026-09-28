package com.example.ui.screens.weather

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

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _searchResults = MutableStateFlow<List<Pair<String, Pair<Double, Double>>>>(emptyList())
    val searchResults: StateFlow<List<Pair<String, Pair<Double, Double>>>> = _searchResults.asStateFlow()

    init {
        viewModelScope.launch {
            appLocationRepository.savedLocation.collect { location ->
                loadWeather(location.displayName, location.latitude, location.longitude)
            }
        }
    }

    fun loadWeather(locationName: String, lat: Double, lon: Double) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val data = repository.fetchWeather(locationName, lat, lon)
                _weatherData.value = data
            } catch (e: Exception) {
                _errorMessage.value = e.localizedMessage ?: "Failed to load weather"
            } finally {
                _isLoading.value = false
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
