package com.example.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.repository.AppThemeMode
import com.example.data.repository.PreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class UserPreferencesDataStoreTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var userPreferencesDataStore: UserPreferencesDataStore
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
        val testDataStore = PreferenceDataStoreFactory.create(
            scope = testScope,
            produceFile = { tmpFolder.newFile("test_user_preferences_${System.currentTimeMillis()}.preferences_pb") }
        )
        userPreferencesDataStore = UserPreferencesDataStore(testDataStore)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testDefaultPreferences() = runTest {
        val prefs = userPreferencesDataStore.userPreferencesFlow.first()
        assertEquals(AppThemeMode.SYSTEM, prefs.themeMode)
        assertTrue(prefs.useDynamicColor)
        assertFalse(prefs.highContrast)
        assertTrue(prefs.masterNotificationsEnabled)
        assertTrue(prefs.notifyTaskReminders)
        assertEquals("bn-BD", prefs.voiceLanguage)
        assertEquals(1.0f, prefs.voiceSpeed, 0.01f)
        assertEquals(1.0f, prefs.voicePitch, 0.01f)
        assertFalse(prefs.autoSpeakResponses)
        assertFalse(prefs.voiceActivationEnabled)
    }

    @Test
    fun testUpdateThemeSettings() = runTest {
        userPreferencesDataStore.updateThemeMode(AppThemeMode.DARK)
        userPreferencesDataStore.updateUseDynamicColor(false)
        userPreferencesDataStore.updateHighContrast(true)

        val prefs = userPreferencesDataStore.userPreferencesFlow.first()
        assertEquals(AppThemeMode.DARK, prefs.themeMode)
        assertFalse(prefs.useDynamicColor)
        assertTrue(prefs.highContrast)
    }

    @Test
    fun testUpdateNotificationToggles() = runTest {
        userPreferencesDataStore.updateMasterNotificationsEnabled(false)
        userPreferencesDataStore.updateNotifyTaskReminders(false)
        userPreferencesDataStore.updateNotifyRunFailed(false)

        val prefs = userPreferencesDataStore.userPreferencesFlow.first()
        assertFalse(prefs.masterNotificationsEnabled)
        assertFalse(prefs.notifyTaskReminders)
        assertFalse(prefs.notifyRunFailed)
        assertTrue(prefs.notifyRunCompleted)
    }

    @Test
    fun testUpdateVoiceAssistantSettings() = runTest {
        userPreferencesDataStore.updateVoiceLanguage("en-US")
        userPreferencesDataStore.updateVoiceSpeed(1.25f)
        userPreferencesDataStore.updateVoicePitch(0.9f)
        userPreferencesDataStore.updateAutoSpeakResponses(true)
        userPreferencesDataStore.updateVoiceActivationEnabled(true)

        val prefs = userPreferencesDataStore.userPreferencesFlow.first()
        assertEquals("en-US", prefs.voiceLanguage)
        assertEquals(1.25f, prefs.voiceSpeed, 0.01f)
        assertEquals(0.9f, prefs.voicePitch, 0.01f)
        assertTrue(prefs.autoSpeakResponses)
        assertTrue(prefs.voiceActivationEnabled)
    }

    @Test
    fun testPreferencesRepositoryIntegration() = runTest {
        val repository = PreferencesRepository(context = context, dataStore = userPreferencesDataStore, scope = testScope)
        
        repository.setThemeMode(AppThemeMode.LIGHT)
        repository.setVoiceLanguage("es-ES")
        repository.setNotifyGeneralAlerts(false)
        testDispatcher.scheduler.advanceUntilIdle()

        val prefs = userPreferencesDataStore.userPreferencesFlow.first()
        assertEquals(AppThemeMode.LIGHT, prefs.themeMode)
        assertEquals("es-ES", prefs.voiceLanguage)
        assertFalse(prefs.notifyGeneralAlerts)
    }
}
