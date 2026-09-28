package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.datastore.UserPreferences
import com.example.data.datastore.UserPreferencesDataStore
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class CloudSyncUiState(
    val isSyncing: Boolean = false,
    val lastSyncTime: String? = null,
    val syncError: String? = null
)

class CloudPreferencesRepository(
    private val context: Context,
    private val preferencesDataStore: UserPreferencesDataStore,
    private val scope: CoroutineScope
) {
    private val databaseId = "ai-studio-pixelroxai-66887a5b-0d13-4114-aecf-136c71c359f2"

    private val firestore: FirebaseFirestore? by lazy {
        com.example.core.firebase.FirebaseInitializer.getFirestore(context, databaseId)
    }

    private val _syncState = MutableStateFlow(CloudSyncUiState())
    val syncState: StateFlow<CloudSyncUiState> = _syncState.asStateFlow()

    private var isUpdatingFromCloud = false

    suspend fun pullFromCloudAndSync() {
        val db = firestore ?: return
        _syncState.update { it.copy(isSyncing = true, syncError = null) }

        try {
            val docRef = db.collection("app_preferences")
                .document("user_prefs")

            val snapshot = docRef.get().await()

            if (snapshot.exists()) {
                val data = snapshot.data ?: emptyMap()
                isUpdatingFromCloud = true
                try {
                    val themeStr = data["themeMode"] as? String
                    if (themeStr != null) {
                        try {
                            preferencesDataStore.updateThemeMode(AppThemeMode.valueOf(themeStr))
                        } catch (_: Exception) {}
                    }

                    (data["useDynamicColor"] as? Boolean)?.let { preferencesDataStore.updateUseDynamicColor(it) }
                    (data["highContrast"] as? Boolean)?.let { preferencesDataStore.updateHighContrast(it) }

                    (data["masterNotificationsEnabled"] as? Boolean)?.let { preferencesDataStore.updateMasterNotificationsEnabled(it) }
                    (data["notifyTaskReminders"] as? Boolean)?.let { preferencesDataStore.updateNotifyTaskReminders(it) }
                    (data["notifyRunCompleted"] as? Boolean)?.let { preferencesDataStore.updateNotifyRunCompleted(it) }
                    (data["notifyRunFailed"] as? Boolean)?.let { preferencesDataStore.updateNotifyRunFailed(it) }
                    (data["notifyAttentionRequired"] as? Boolean)?.let { preferencesDataStore.updateNotifyAttentionRequired(it) }
                    (data["notifyGeneralAlerts"] as? Boolean)?.let { preferencesDataStore.updateNotifyGeneralAlerts(it) }

                    (data["voiceLanguage"] as? String)?.let { preferencesDataStore.updateVoiceLanguage(it) }
                    (data["voiceSpeed"] as? Double)?.let { preferencesDataStore.updateVoiceSpeed(it.toFloat()) }
                    (data["voicePitch"] as? Double)?.let { preferencesDataStore.updateVoicePitch(it.toFloat()) }
                    (data["autoSpeakResponses"] as? Boolean)?.let { preferencesDataStore.updateAutoSpeakResponses(it) }
                    (data["voiceActivationEnabled"] as? Boolean)?.let { preferencesDataStore.updateVoiceActivationEnabled(it) }

                    (data["aiMode"] as? String)?.let { preferencesDataStore.updateAiMode(it) }

                    val timeFormatted = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date())
                    _syncState.update {
                        it.copy(
                            isSyncing = false,
                            lastSyncTime = timeFormatted,
                            syncError = null
                        )
                    }
                } finally {
                    isUpdatingFromCloud = false
                }
            } else {
                val currentLocalPrefs = preferencesDataStore.userPreferencesFlow.first()
                pushToCloud(currentLocalPrefs)
            }
        } catch (e: Exception) {
            Log.e("CloudPreferencesRepo", "Error pulling preferences from cloud", e)
            _syncState.update {
                it.copy(
                    isSyncing = false,
                    syncError = "Sync error: ${e.localizedMessage ?: "Failed to reach cloud"}"
                )
            }
        }
    }

    suspend fun pushToCloud(prefs: UserPreferences) {
        val db = firestore ?: return
        try {
            val docRef = db.collection("app_preferences")
                .document("user_prefs")

            val map = hashMapOf<String, Any>(
                "themeMode" to prefs.themeMode.name,
                "useDynamicColor" to prefs.useDynamicColor,
                "highContrast" to prefs.highContrast,
                "masterNotificationsEnabled" to prefs.masterNotificationsEnabled,
                "notifyTaskReminders" to prefs.notifyTaskReminders,
                "notifyRunCompleted" to prefs.notifyRunCompleted,
                "notifyRunFailed" to prefs.notifyRunFailed,
                "notifyAttentionRequired" to prefs.notifyAttentionRequired,
                "notifyGeneralAlerts" to prefs.notifyGeneralAlerts,
                "voiceLanguage" to prefs.voiceLanguage,
                "voiceSpeed" to prefs.voiceSpeed,
                "voicePitch" to prefs.voicePitch,
                "autoSpeakResponses" to prefs.autoSpeakResponses,
                "voiceActivationEnabled" to prefs.voiceActivationEnabled,
                "aiMode" to prefs.aiMode,
                "ludoGameSoundsEnabled" to prefs.ludoGameSoundsEnabled,
                "pomodoroFocusDuration" to prefs.pomodoroFocusDuration,
                "pomodoroShortBreakDuration" to prefs.pomodoroShortBreakDuration,
                "pomodoroLongBreakDuration" to prefs.pomodoroLongBreakDuration,
                "lastUpdated" to System.currentTimeMillis()
            )

            docRef.set(map, SetOptions.merge()).await()

            val timeFormatted = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date())
            _syncState.update {
                it.copy(
                    isSyncing = false,
                    lastSyncTime = timeFormatted,
                    syncError = null
                )
            }
        } catch (e: Exception) {
            Log.e("CloudPreferencesRepo", "Error pushing preferences to cloud", e)
        }
    }
}
