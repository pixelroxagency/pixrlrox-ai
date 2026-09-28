package com.example.data.repository

import android.content.Context
import com.example.data.datastore.UserPreferences
import com.example.data.datastore.UserPreferencesDataStore
import com.example.data.datastore.userPreferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class AppThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

enum class VisualMode {
    SYSTEM,
    LIGHT,
    DARK,
    MIDNIGHT,
    MINIMAL
}

enum class UiStyle {
    MODERN_GLASS,
    AURORA_GRADIENT,
    BENTO_MODERN,
    MINIMAL_PRO,
    AMOLED_NEON,
    SOFT_NEUMORPHIC,
    MATERIAL_EXPRESSIVE,
    IMAGE_CANVAS
}

enum class DisplayMode {
    SYSTEM,
    LIGHT,
    DARK,
    MIDNIGHT
}

enum class AppColorTheme {
    CLASSIC_BLUE,
    EMERALD_GREEN,
    PASTEL_PINK,
    NEON_VIOLET,
    SUNSET_ORANGE,
    CHARCOAL_GRAY,
    MATERIAL_DYNAMIC
}

enum class IconPack {
    FILLED,
    OUTLINED,
    ROUNDED,
    SHARP,
    DUOTONE,
    GLASS_GLOW
}

typealias AppIconStyle = IconPack

enum class BackgroundStyle {
    SOLID,
    SOFT_GRADIENT,
    AURORA,
    MESH,
    GLASS_CANVAS,
    ABSTRACT,
    CUSTOM_IMAGE,
    AMOLED_BLACK,
    AUTO
}

enum class BackgroundScope {
    GLOBAL,
    HOME_ONLY
}

enum class UiDensity {
    COMFORTABLE,
    COMPACT,
    DENSE
}

enum class CardTransparency {
    SOLID,
    SUBTLE,
    MEDIUM,
    GLASS
}

enum class BlurIntensity {
    OFF,
    LOW,
    MEDIUM,
    HIGH
}

enum class CornerRoundness {
    SHARP,
    SMALL,
    MEDIUM,
    ROUNDED,
    EXTRA_ROUNDED
}

enum class AccentIntensity {
    SUBTLE,
    BALANCED,
    VIVID
}

enum class MotionPreference {
    STANDARD,
    REDUCED,
    EXPRESSIVE
}

class PreferencesRepository(
    private val context: Context,
    val dataStore: UserPreferencesDataStore = UserPreferencesDataStore(context.userPreferencesDataStore),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) {

    init {
        scope.launch {
            migrateFromSharedPreferencesIfNeeded()
        }
    }

    val userPreferences: StateFlow<UserPreferences> = dataStore.userPreferencesFlow.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = UserPreferences()
    )

    // Theme & Appearance Flows
    val themeMode: StateFlow<AppThemeMode> = userPreferences.map { it.themeMode }.stateIn(scope, SharingStarted.Eagerly, AppThemeMode.SYSTEM)
    val visualMode: StateFlow<VisualMode> = userPreferences.map { it.visualMode }.stateIn(scope, SharingStarted.Eagerly, VisualMode.SYSTEM)
    val uiStyle: StateFlow<UiStyle> = userPreferences.map { it.uiStyle }.stateIn(scope, SharingStarted.Eagerly, UiStyle.MODERN_GLASS)
    val displayMode: StateFlow<DisplayMode> = userPreferences.map { it.displayMode }.stateIn(scope, SharingStarted.Eagerly, DisplayMode.SYSTEM)
    val colorTheme: StateFlow<AppColorTheme> = userPreferences.map { it.colorTheme }.stateIn(scope, SharingStarted.Eagerly, AppColorTheme.CLASSIC_BLUE)
    val iconPack: StateFlow<IconPack> = userPreferences.map { it.iconPack }.stateIn(scope, SharingStarted.Eagerly, IconPack.ROUNDED)
    val iconStyle: StateFlow<IconPack> = iconPack // compatibility alias
    val backgroundStyle: StateFlow<BackgroundStyle> = userPreferences.map { it.backgroundStyle }.stateIn(scope, SharingStarted.Eagerly, BackgroundStyle.AUTO)
    val backgroundScope: StateFlow<BackgroundScope> = userPreferences.map { it.backgroundScope }.stateIn(scope, SharingStarted.Eagerly, BackgroundScope.GLOBAL)
    val customBackgroundUri: StateFlow<String?> = userPreferences.map { it.customBackgroundUri }.stateIn(scope, SharingStarted.Eagerly, null)
    val uiDensity: StateFlow<UiDensity> = userPreferences.map { it.uiDensity }.stateIn(scope, SharingStarted.Eagerly, UiDensity.COMPACT)
    val cardTransparency: StateFlow<CardTransparency> = userPreferences.map { it.cardTransparency }.stateIn(scope, SharingStarted.Eagerly, CardTransparency.MEDIUM)
    val blurIntensity: StateFlow<BlurIntensity> = userPreferences.map { it.blurIntensity }.stateIn(scope, SharingStarted.Eagerly, BlurIntensity.MEDIUM)
    val cornerRoundness: StateFlow<CornerRoundness> = userPreferences.map { it.cornerRoundness }.stateIn(scope, SharingStarted.Eagerly, CornerRoundness.MEDIUM)
    val accentIntensity: StateFlow<AccentIntensity> = userPreferences.map { it.accentIntensity }.stateIn(scope, SharingStarted.Eagerly, AccentIntensity.BALANCED)
    val motionPreference: StateFlow<MotionPreference> = userPreferences.map { it.motionPreference }.stateIn(scope, SharingStarted.Eagerly, MotionPreference.STANDARD)

    val useDynamicColor: StateFlow<Boolean> = userPreferences.map { it.useDynamicColor }.stateIn(scope, SharingStarted.Eagerly, true)
    val highContrast: StateFlow<Boolean> = userPreferences.map { it.highContrast }.stateIn(scope, SharingStarted.Eagerly, false)
    val detectCopiedLinks: StateFlow<Boolean> = userPreferences.map { it.detectCopiedLinks }.stateIn(scope, SharingStarted.Eagerly, true)

    // Notification Flows & Getters
    val masterNotificationsEnabled: StateFlow<Boolean> = userPreferences.map { it.masterNotificationsEnabled }.stateIn(scope, SharingStarted.Eagerly, true)
    val notifyTaskReminders: StateFlow<Boolean> = userPreferences.map { it.notifyTaskReminders }.stateIn(scope, SharingStarted.Eagerly, true)
    val notifyRunCompleted: StateFlow<Boolean> = userPreferences.map { it.notifyRunCompleted }.stateIn(scope, SharingStarted.Eagerly, true)
    val notifyRunFailed: StateFlow<Boolean> = userPreferences.map { it.notifyRunFailed }.stateIn(scope, SharingStarted.Eagerly, true)
    val notifyAttentionRequired: StateFlow<Boolean> = userPreferences.map { it.notifyAttentionRequired }.stateIn(scope, SharingStarted.Eagerly, true)
    val notifyGeneralAlerts: StateFlow<Boolean> = userPreferences.map { it.notifyGeneralAlerts }.stateIn(scope, SharingStarted.Eagerly, true)

    // Voice & AI Flows
    val voiceLanguage: StateFlow<String> = userPreferences.map { it.voiceLanguage }.stateIn(scope, SharingStarted.Eagerly, "bn-BD")
    val voiceSpeed: StateFlow<Float> = userPreferences.map { it.voiceSpeed }.stateIn(scope, SharingStarted.Eagerly, 1.0f)
    val voicePitch: StateFlow<Float> = userPreferences.map { it.voicePitch }.stateIn(scope, SharingStarted.Eagerly, 1.0f)
    val autoSpeakResponses: StateFlow<Boolean> = userPreferences.map { it.autoSpeakResponses }.stateIn(scope, SharingStarted.Eagerly, false)
    val voiceActivationEnabled: StateFlow<Boolean> = userPreferences.map { it.voiceActivationEnabled }.stateIn(scope, SharingStarted.Eagerly, false)
    val aiMode: StateFlow<String> = userPreferences.map { it.aiMode }.stateIn(scope, SharingStarted.Eagerly, "HERMES_AGENT")
    val ludoGameSoundsEnabled: StateFlow<Boolean> = userPreferences.map { it.ludoGameSoundsEnabled }.stateIn(scope, SharingStarted.Eagerly, true)
    val teleprompterScript: StateFlow<String> = userPreferences.map { it.teleprompterScript }.stateIn(scope, SharingStarted.Eagerly, "")
    val teleprompterSpeed: StateFlow<Float> = userPreferences.map { it.teleprompterSpeed }.stateIn(scope, SharingStarted.Eagerly, 1.0f)
    val teleprompterFontSize: StateFlow<Float> = userPreferences.map { it.teleprompterFontSize }.stateIn(scope, SharingStarted.Eagerly, 20.0f)
    val pomodoroFocusDuration: StateFlow<Int> = userPreferences.map { it.pomodoroFocusDuration }.stateIn(scope, SharingStarted.Eagerly, 25)
    val pomodoroShortBreakDuration: StateFlow<Int> = userPreferences.map { it.pomodoroShortBreakDuration }.stateIn(scope, SharingStarted.Eagerly, 5)
    val pomodoroLongBreakDuration: StateFlow<Int> = userPreferences.map { it.pomodoroLongBreakDuration }.stateIn(scope, SharingStarted.Eagerly, 15)

    // Setters
    fun setThemeMode(mode: AppThemeMode) { scope.launch { dataStore.updateThemeMode(mode) } }
    fun setVisualMode(mode: VisualMode) { scope.launch { dataStore.updateVisualMode(mode) } }
    fun setUiStyle(style: UiStyle) { scope.launch { dataStore.updateUiStyle(style) } }
    fun setDisplayMode(mode: DisplayMode) {
        scope.launch {
            dataStore.updateDisplayMode(mode)
            val vMode = when(mode) {
                DisplayMode.LIGHT -> VisualMode.LIGHT
                DisplayMode.DARK -> VisualMode.DARK
                DisplayMode.MIDNIGHT -> VisualMode.MIDNIGHT
                DisplayMode.SYSTEM -> VisualMode.SYSTEM
            }
            dataStore.updateVisualMode(vMode)
        }
    }
    fun setColorTheme(theme: AppColorTheme) { scope.launch { dataStore.updateColorTheme(theme) } }
    fun setIconPack(pack: IconPack) { scope.launch { dataStore.updateIconPack(pack) } }
    fun setIconStyle(style: IconPack) { setIconPack(style) }
    fun setBackgroundStyle(style: BackgroundStyle) { scope.launch { dataStore.updateBackgroundStyle(style) } }
    fun setBackgroundScope(scopeMode: BackgroundScope) { scope.launch { dataStore.updateBackgroundScope(scopeMode) } }
    fun setCustomBackgroundUri(uri: String?) { scope.launch { dataStore.updateCustomBackgroundUri(uri) } }
    fun setUiDensity(density: UiDensity) { scope.launch { dataStore.updateUiDensity(density) } }
    fun setCardTransparency(transparency: CardTransparency) { scope.launch { dataStore.updateCardTransparency(transparency) } }
    fun setBlurIntensity(blur: BlurIntensity) { scope.launch { dataStore.updateBlurIntensity(blur) } }
    fun setCornerRoundness(roundness: CornerRoundness) { scope.launch { dataStore.updateCornerRoundness(roundness) } }
    fun setAccentIntensity(intensity: AccentIntensity) { scope.launch { dataStore.updateAccentIntensity(intensity) } }
    fun setMotionPreference(motion: MotionPreference) { scope.launch { dataStore.updateMotionPreference(motion) } }
    fun setUseDynamicColor(enabled: Boolean) { scope.launch { dataStore.updateUseDynamicColor(enabled) } }
    fun setHighContrast(enabled: Boolean) { scope.launch { dataStore.updateHighContrast(enabled) } }
    fun setDetectCopiedLinks(enabled: Boolean) { scope.launch { dataStore.updateDetectCopiedLinks(enabled) } }

    // Notifications Setters
    fun setMasterNotificationsEnabled(enabled: Boolean) { scope.launch { dataStore.updateMasterNotificationsEnabled(enabled) } }
    fun setNotifyTaskReminders(enabled: Boolean) { scope.launch { dataStore.updateNotifyTaskReminders(enabled) } }
    fun setNotifyRunCompleted(enabled: Boolean) { scope.launch { dataStore.updateNotifyRunCompleted(enabled) } }
    fun setNotifyRunFailed(enabled: Boolean) { scope.launch { dataStore.updateNotifyRunFailed(enabled) } }
    fun setNotifyAttentionRequired(enabled: Boolean) { scope.launch { dataStore.updateNotifyAttentionRequired(enabled) } }
    fun setNotifyGeneralAlerts(enabled: Boolean) { scope.launch { dataStore.updateNotifyGeneralAlerts(enabled) } }

    // Voice & AI Setters
    fun setVoiceLanguage(lang: String) { scope.launch { dataStore.updateVoiceLanguage(lang) } }
    fun setVoiceSpeed(speed: Float) { scope.launch { dataStore.updateVoiceSpeed(speed) } }
    fun setVoicePitch(pitch: Float) { scope.launch { dataStore.updateVoicePitch(pitch) } }
    fun setAutoSpeakResponses(enabled: Boolean) { scope.launch { dataStore.updateAutoSpeakResponses(enabled) } }
    fun setVoiceActivationEnabled(enabled: Boolean) { scope.launch { dataStore.updateVoiceActivationEnabled(enabled) } }
    fun setAiMode(mode: String) { scope.launch { dataStore.updateAiMode(mode) } }
    fun setSavedAiMode(mode: String) { setAiMode(mode) }
    suspend fun getSavedAiMode(): String = userPreferences.first().aiMode
    fun setLudoGameSoundsEnabled(enabled: Boolean) { scope.launch { dataStore.updateLudoGameSoundsEnabled(enabled) } }
    fun setTeleprompterScript(script: String) { scope.launch { dataStore.updateTeleprompterScript(script) } }
    fun setTeleprompterSpeed(speed: Float) { scope.launch { dataStore.updateTeleprompterSpeed(speed) } }
    fun setTeleprompterFontSize(size: Float) { scope.launch { dataStore.updateTeleprompterFontSize(size) } }
    fun setPomodoroFocusDuration(mins: Int) { scope.launch { dataStore.updatePomodoroFocusDuration(mins) } }
    fun setPomodoroShortBreakDuration(mins: Int) { scope.launch { dataStore.updatePomodoroShortBreakDuration(mins) } }
    fun setPomodoroLongBreakDuration(mins: Int) { scope.launch { dataStore.updatePomodoroLongBreakDuration(mins) } }

    suspend fun getRecentSearches(): List<String> = userPreferences.first().recentSearches
    fun saveRecentSearches(searches: List<String>) { scope.launch { dataStore.updateRecentSearches(searches) } }
    fun saveHomeWidgetLayout(layout: String) { scope.launch { dataStore.updateHomeWidgetLayout(layout) } }

    fun applyPreset(presetName: String) {
        scope.launch {
            when (presetName) {
                "PixelRox Glass" -> {
                    dataStore.updateUiStyle(UiStyle.MODERN_GLASS)
                    dataStore.updateDisplayMode(DisplayMode.SYSTEM)
                    dataStore.updateColorTheme(AppColorTheme.CLASSIC_BLUE)
                    dataStore.updateIconPack(IconPack.ROUNDED)
                    dataStore.updateBackgroundStyle(BackgroundStyle.AUTO)
                    dataStore.updateUiDensity(UiDensity.COMPACT)
                    dataStore.updateCardTransparency(CardTransparency.MEDIUM)
                }
                "Executive" -> {
                    dataStore.updateUiStyle(UiStyle.MINIMAL_PRO)
                    dataStore.updateDisplayMode(DisplayMode.SYSTEM)
                    dataStore.updateColorTheme(AppColorTheme.CHARCOAL_GRAY)
                    dataStore.updateIconPack(IconPack.OUTLINED)
                    dataStore.updateBackgroundStyle(BackgroundStyle.SOFT_GRADIENT)
                    dataStore.updateUiDensity(UiDensity.COMPACT)
                    dataStore.updateCardTransparency(CardTransparency.SUBTLE)
                }
                "Cyber Midnight" -> {
                    dataStore.updateUiStyle(UiStyle.AMOLED_NEON)
                    dataStore.updateDisplayMode(DisplayMode.MIDNIGHT)
                    dataStore.updateColorTheme(AppColorTheme.NEON_VIOLET)
                    dataStore.updateIconPack(IconPack.SHARP)
                    dataStore.updateBackgroundStyle(BackgroundStyle.AMOLED_BLACK)
                    dataStore.updateUiDensity(UiDensity.COMPACT)
                    dataStore.updateCardTransparency(CardTransparency.SOLID)
                }
                "Fresh" -> {
                    dataStore.updateUiStyle(UiStyle.BENTO_MODERN)
                    dataStore.updateDisplayMode(DisplayMode.SYSTEM)
                    dataStore.updateColorTheme(AppColorTheme.EMERALD_GREEN)
                    dataStore.updateIconPack(IconPack.ROUNDED)
                    dataStore.updateBackgroundStyle(BackgroundStyle.MESH)
                    dataStore.updateUiDensity(UiDensity.COMPACT)
                    dataStore.updateCardTransparency(CardTransparency.MEDIUM)
                }
                "Clean Light" -> {
                    dataStore.updateUiStyle(UiStyle.MINIMAL_PRO)
                    dataStore.updateDisplayMode(DisplayMode.LIGHT)
                    dataStore.updateColorTheme(AppColorTheme.CLASSIC_BLUE)
                    dataStore.updateIconPack(IconPack.OUTLINED)
                    dataStore.updateBackgroundStyle(BackgroundStyle.SOLID)
                    dataStore.updateUiDensity(UiDensity.COMPACT)
                    dataStore.updateCardTransparency(CardTransparency.SOLID)
                }
                "Soft UI" -> {
                    dataStore.updateUiStyle(UiStyle.SOFT_NEUMORPHIC)
                    dataStore.updateDisplayMode(DisplayMode.SYSTEM)
                    dataStore.updateColorTheme(AppColorTheme.PASTEL_PINK)
                    dataStore.updateIconPack(IconPack.ROUNDED)
                    dataStore.updateBackgroundStyle(BackgroundStyle.SOFT_GRADIENT)
                    dataStore.updateUiDensity(UiDensity.COMFORTABLE)
                    dataStore.updateCardTransparency(CardTransparency.SUBTLE)
                }
                "Material You" -> {
                    dataStore.updateUiStyle(UiStyle.MATERIAL_EXPRESSIVE)
                    dataStore.updateDisplayMode(DisplayMode.SYSTEM)
                    dataStore.updateColorTheme(AppColorTheme.MATERIAL_DYNAMIC)
                    dataStore.updateIconPack(IconPack.ROUNDED)
                    dataStore.updateBackgroundStyle(BackgroundStyle.AUTO)
                    dataStore.updateUiDensity(UiDensity.COMPACT)
                    dataStore.updateCardTransparency(CardTransparency.MEDIUM)
                }
                "My Canvas" -> {
                    dataStore.updateUiStyle(UiStyle.IMAGE_CANVAS)
                    dataStore.updateDisplayMode(DisplayMode.SYSTEM)
                    dataStore.updateColorTheme(AppColorTheme.CLASSIC_BLUE)
                    dataStore.updateIconPack(IconPack.ROUNDED)
                    dataStore.updateBackgroundStyle(BackgroundStyle.CUSTOM_IMAGE)
                    dataStore.updateUiDensity(UiDensity.COMPACT)
                    dataStore.updateCardTransparency(CardTransparency.GLASS)
                }
            }
        }
    }

    fun resetAppearance() {
        scope.launch { dataStore.resetAppearance() }
    }

    private suspend fun migrateFromSharedPreferencesIfNeeded() {}
}
