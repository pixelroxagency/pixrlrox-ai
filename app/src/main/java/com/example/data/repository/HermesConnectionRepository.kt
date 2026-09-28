package com.example.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import com.example.core.network.ConnectionTestResult

data class HermesProfile(
    val id: String = "default",
    val displayName: String = "Direct AI",
    val baseUrl: String = "https://api.openai.com/v1/",
    val defaultModel: String = "gpt-4o-mini"
)

sealed class HermesConnectionState {
    object Connected : HermesConnectionState()
    object Connecting : HermesConnectionState()
    object NotConfigured : HermesConnectionState()
    object Unauthorized : HermesConnectionState()
    object Offline : HermesConnectionState()
    object Error : HermesConnectionState()
}

class HermesConnectionRepository(
    private val directAiRepository: DirectAiRepository
) {
    private val _currentProfile = MutableStateFlow<HermesProfile?>(HermesProfile())
    val currentProfile: StateFlow<HermesProfile?> = _currentProfile.asStateFlow()

    private val _isConnected = MutableStateFlow(true)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _connectionState = MutableStateFlow<HermesConnectionState>(HermesConnectionState.Connected)
    val connectionState: StateFlow<HermesConnectionState> = _connectionState.asStateFlow()

    suspend fun initialize() {}

    fun getAllProfiles(): Flow<List<HermesProfile>> = flowOf(listOf(HermesProfile()))

    fun getProfileById(id: String): HermesProfile? = HermesProfile()

    fun isProfileConfigured(id: String): Boolean = true

    suspend fun switchProfile(id: String) {}

    suspend fun testConnection(rawUrl: String, rawApiKey: String): ConnectionTestResult {
        return ConnectionTestResult(isSuccess = true)
    }

    suspend fun saveProfile(profileId: String, displayName: String, baseUrl: String, rawApiKey: String, isDefault: Boolean, enabled: Boolean) {}

    suspend fun saveProfile(profile: HermesProfile, apiKey: String) {}

    suspend fun saveAndConnect(url: String, key: String): ConnectionTestResult {
        return ConnectionTestResult(isSuccess = true)
    }

    suspend fun saveAndConnect(profileId: String, displayName: String, baseUrl: String, rawApiKey: String): ConnectionTestResult {
        return ConnectionTestResult(isSuccess = true)
    }

    fun getProfileApiKey(id: String): String = ""
}
