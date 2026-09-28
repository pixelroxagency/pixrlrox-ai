package com.example.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.repository.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

val Context.userPreferencesDataStore: androidx.datastore.core.DataStore<Preferences> by preferencesDataStore(name = "pixelrox_user_preferences")

data class UserPreferences(
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val visualMode: VisualMode = VisualMode.SYSTEM,
    val uiStyle: UiStyle = UiStyle.MODERN_GLASS,
    val displayMode: DisplayMode = DisplayMode.SYSTEM,
    val colorTheme: AppColorTheme = AppColorTheme.CLASSIC_BLUE,
    val iconPack: IconPack = IconPack.ROUNDED,
    val backgroundStyle: BackgroundStyle = BackgroundStyle.AUTO,
    val backgroundScope: BackgroundScope = BackgroundScope.GLOBAL,
    val customBackgroundUri: String? = null,
    val uiDensity: UiDensity = UiDensity.COMPACT,
    val cardTransparency: CardTransparency = CardTransparency.MEDIUM,
    val blurIntensity: BlurIntensity = BlurIntensity.MEDIUM,
    val cornerRoundness: CornerRoundness = CornerRoundness.MEDIUM,
    val accentIntensity: AccentIntensity = AccentIntensity.BALANCED,
    val motionPreference: MotionPreference = MotionPreference.STANDARD,
    val useDynamicColor: Boolean = true,
    val highContrast: Boolean = false,
    val masterNotificationsEnabled: Boolean = true,
    val notifyTaskReminders: Boolean = true,
    val notifyRunCompleted: Boolean = true,
    val notifyRunFailed: Boolean = true,
    val notifyAttentionRequired: Boolean = true,
    val notifyGeneralAlerts: Boolean = true,
    val voiceLanguage: String = "bn-BD",
    val voiceSpeed: Float = 1.0f,
    val voicePitch: Float = 1.0f,
    val autoSpeakResponses: Boolean = false,
    val voiceActivationEnabled: Boolean = false,
    val aiMode: String = "HERMES_AGENT",
    val ludoGameSoundsEnabled: Boolean = true,
    val teleprompterScript: String = "",
    val teleprompterSpeed: Float = 1.0f,
    val teleprompterFontSize: Float = 20.0f,
    val pomodoroFocusDuration: Int = 25,
    val pomodoroShortBreakDuration: Int = 5,
    val pomodoroLongBreakDuration: Int = 15,
    val recentSearches: List<String> = emptyList(),
    val homeWidgetLayout: String = "DEFAULT",
    val detectCopiedLinks: Boolean = true
)

class UserPreferencesDataStore(private val dataStore: androidx.datastore.core.DataStore<Preferences>) {

    object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val VISUAL_MODE = stringPreferencesKey("visual_mode")
        val UI_STYLE = stringPreferencesKey("ui_style")
        val DISPLAY_MODE = stringPreferencesKey("display_mode")
        val COLOR_THEME = stringPreferencesKey("color_theme")
        val ICON_PACK = stringPreferencesKey("icon_pack")
        val ICON_STYLE = stringPreferencesKey("icon_style")
        val BACKGROUND_STYLE = stringPreferencesKey("background_style")
        val BACKGROUND_SCOPE = stringPreferencesKey("background_scope")
        val CUSTOM_BACKGROUND_URI = stringPreferencesKey("custom_background_uri")
        val UI_DENSITY = stringPreferencesKey("ui_density")
        val CARD_TRANSPARENCY = stringPreferencesKey("card_transparency")
        val BLUR_INTENSITY = stringPreferencesKey("blur_intensity")
        val CORNER_ROUNDNESS = stringPreferencesKey("corner_roundness")
        val ACCENT_INTENSITY = stringPreferencesKey("accent_intensity")
        val MOTION_PREFERENCE = stringPreferencesKey("motion_preference")
        val USE_DYNAMIC_COLOR = booleanPreferencesKey("use_dynamic_color")
        val HIGH_CONTRAST = booleanPreferencesKey("high_contrast")
        val MASTER_NOTIFICATIONS = booleanPreferencesKey("master_notifications_enabled")
        val NOTIFY_TASK_REMINDERS = booleanPreferencesKey("notify_task_reminders")
        val NOTIFY_RUN_COMPLETED = booleanPreferencesKey("notify_run_completed")
        val NOTIFY_RUN_FAILED = booleanPreferencesKey("notify_run_failed")
        val NOTIFY_ATTENTION_REQUIRED = booleanPreferencesKey("notify_attention_required")
        val NOTIFY_GENERAL_ALERTS = booleanPreferencesKey("notify_general_alerts")
        val VOICE_LANGUAGE = stringPreferencesKey("voice_language")
        val VOICE_SPEED = floatPreferencesKey("voice_speed")
        val VOICE_PITCH = floatPreferencesKey("voice_pitch")
        val AUTO_SPEAK_RESPONSES = booleanPreferencesKey("auto_speak_responses")
        val VOICE_ACTIVATION_ENABLED = booleanPreferencesKey("voice_activation_enabled")
        val AI_MODE = stringPreferencesKey("ai_mode")
        val LUDO_SOUNDS = booleanPreferencesKey("ludo_game_sounds_enabled")
        val TELEPROMPTER_SCRIPT = stringPreferencesKey("teleprompter_script")
        val TELEPROMPTER_SPEED = floatPreferencesKey("teleprompter_speed")
        val TELEPROMPTER_FONT_SIZE = floatPreferencesKey("teleprompter_font_size")
        val POMODORO_FOCUS = intPreferencesKey("pomodoro_focus_duration")
        val POMODORO_SHORT = intPreferencesKey("pomodoro_short_break_duration")
        val POMODORO_LONG = intPreferencesKey("pomodoro_long_break_duration")
        val RECENT_SEARCHES = stringPreferencesKey("recent_searches")
        val HOME_WIDGET_LAYOUT = stringPreferencesKey("home_widget_layout")
        val DETECT_COPIED_LINKS = booleanPreferencesKey("detect_copied_links")
    }

    val userPreferencesFlow: Flow<UserPreferences> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val themeMode = runCatching { AppThemeMode.valueOf(preferences[Keys.THEME_MODE] ?: AppThemeMode.SYSTEM.name) }.getOrDefault(AppThemeMode.SYSTEM)
            val visualMode = runCatching { VisualMode.valueOf(preferences[Keys.VISUAL_MODE] ?: VisualMode.SYSTEM.name) }.getOrDefault(VisualMode.SYSTEM)
            val uiStyle = runCatching { UiStyle.valueOf(preferences[Keys.UI_STYLE] ?: UiStyle.MODERN_GLASS.name) }.getOrDefault(UiStyle.MODERN_GLASS)
            val displayMode = runCatching { DisplayMode.valueOf(preferences[Keys.DISPLAY_MODE] ?: DisplayMode.SYSTEM.name) }.getOrDefault(DisplayMode.SYSTEM)
            val colorTheme = runCatching { AppColorTheme.valueOf(preferences[Keys.COLOR_THEME] ?: AppColorTheme.CLASSIC_BLUE.name) }.getOrDefault(AppColorTheme.CLASSIC_BLUE)
            val iconPackStr = preferences[Keys.ICON_PACK] ?: preferences[Keys.ICON_STYLE] ?: IconPack.ROUNDED.name
            val iconPack = runCatching { IconPack.valueOf(iconPackStr) }.getOrDefault(IconPack.ROUNDED)
            val backgroundStyle = runCatching { BackgroundStyle.valueOf(preferences[Keys.BACKGROUND_STYLE] ?: BackgroundStyle.AUTO.name) }.getOrDefault(BackgroundStyle.AUTO)
            val backgroundScope = runCatching { BackgroundScope.valueOf(preferences[Keys.BACKGROUND_SCOPE] ?: BackgroundScope.GLOBAL.name) }.getOrDefault(BackgroundScope.GLOBAL)
            val customBackgroundUri = preferences[Keys.CUSTOM_BACKGROUND_URI]
            val uiDensity = runCatching { UiDensity.valueOf(preferences[Keys.UI_DENSITY] ?: UiDensity.COMPACT.name) }.getOrDefault(UiDensity.COMPACT)
            val cardTransparency = runCatching { CardTransparency.valueOf(preferences[Keys.CARD_TRANSPARENCY] ?: CardTransparency.MEDIUM.name) }.getOrDefault(CardTransparency.MEDIUM)
            val blurIntensity = runCatching { BlurIntensity.valueOf(preferences[Keys.BLUR_INTENSITY] ?: BlurIntensity.MEDIUM.name) }.getOrDefault(BlurIntensity.MEDIUM)
            val cornerRoundness = runCatching { CornerRoundness.valueOf(preferences[Keys.CORNER_ROUNDNESS] ?: CornerRoundness.MEDIUM.name) }.getOrDefault(CornerRoundness.MEDIUM)
            val accentIntensity = runCatching { AccentIntensity.valueOf(preferences[Keys.ACCENT_INTENSITY] ?: AccentIntensity.BALANCED.name) }.getOrDefault(AccentIntensity.BALANCED)
            val motionPreference = runCatching { MotionPreference.valueOf(preferences[Keys.MOTION_PREFERENCE] ?: MotionPreference.STANDARD.name) }.getOrDefault(MotionPreference.STANDARD)
            val useDynamicColor = preferences[Keys.USE_DYNAMIC_COLOR] ?: true
            val highContrast = preferences[Keys.HIGH_CONTRAST] ?: false
            val masterNotificationsEnabled = preferences[Keys.MASTER_NOTIFICATIONS] ?: true
            val notifyTaskReminders = preferences[Keys.NOTIFY_TASK_REMINDERS] ?: true
            val notifyRunCompleted = preferences[Keys.NOTIFY_RUN_COMPLETED] ?: true
            val notifyRunFailed = preferences[Keys.NOTIFY_RUN_FAILED] ?: true
            val notifyAttentionRequired = preferences[Keys.NOTIFY_ATTENTION_REQUIRED] ?: true
            val notifyGeneralAlerts = preferences[Keys.NOTIFY_GENERAL_ALERTS] ?: true
            val voiceLanguage = preferences[Keys.VOICE_LANGUAGE] ?: "bn-BD"
            val voiceSpeed = preferences[Keys.VOICE_SPEED] ?: 1.0f
            val voicePitch = preferences[Keys.VOICE_PITCH] ?: 1.0f
            val autoSpeakResponses = preferences[Keys.AUTO_SPEAK_RESPONSES] ?: false
            val voiceActivationEnabled = preferences[Keys.VOICE_ACTIVATION_ENABLED] ?: false
            val aiMode = preferences[Keys.AI_MODE] ?: "HERMES_AGENT"
            val ludoGameSoundsEnabled = preferences[Keys.LUDO_SOUNDS] ?: true
            val teleprompterScript = preferences[Keys.TELEPROMPTER_SCRIPT] ?: ""
            val teleprompterSpeed = preferences[Keys.TELEPROMPTER_SPEED] ?: 1.0f
            val teleprompterFontSize = preferences[Keys.TELEPROMPTER_FONT_SIZE] ?: 20.0f
            val pomodoroFocusDuration = preferences[Keys.POMODORO_FOCUS] ?: 25
            val pomodoroShortBreakDuration = preferences[Keys.POMODORO_SHORT] ?: 5
            val pomodoroLongBreakDuration = preferences[Keys.POMODORO_LONG] ?: 15
            val recentSearchesStr = preferences[Keys.RECENT_SEARCHES] ?: ""
            val recentSearches = if (recentSearchesStr.isEmpty()) emptyList() else recentSearchesStr.split(",")
            val homeWidgetLayout = preferences[Keys.HOME_WIDGET_LAYOUT] ?: "DEFAULT"
            val detectCopiedLinks = preferences[Keys.DETECT_COPIED_LINKS] ?: true

            UserPreferences(
                themeMode = themeMode,
                visualMode = visualMode,
                uiStyle = uiStyle,
                displayMode = displayMode,
                colorTheme = colorTheme,
                iconPack = iconPack,
                backgroundStyle = backgroundStyle,
                backgroundScope = backgroundScope,
                customBackgroundUri = customBackgroundUri,
                uiDensity = uiDensity,
                cardTransparency = cardTransparency,
                blurIntensity = blurIntensity,
                cornerRoundness = cornerRoundness,
                accentIntensity = accentIntensity,
                motionPreference = motionPreference,
                useDynamicColor = useDynamicColor,
                highContrast = highContrast,
                masterNotificationsEnabled = masterNotificationsEnabled,
                notifyTaskReminders = notifyTaskReminders,
                notifyRunCompleted = notifyRunCompleted,
                notifyRunFailed = notifyRunFailed,
                notifyAttentionRequired = notifyAttentionRequired,
                notifyGeneralAlerts = notifyGeneralAlerts,
                voiceLanguage = voiceLanguage,
                voiceSpeed = voiceSpeed,
                voicePitch = voicePitch,
                autoSpeakResponses = autoSpeakResponses,
                voiceActivationEnabled = voiceActivationEnabled,
                aiMode = aiMode,
                ludoGameSoundsEnabled = ludoGameSoundsEnabled,
                teleprompterScript = teleprompterScript,
                teleprompterSpeed = teleprompterSpeed,
                teleprompterFontSize = teleprompterFontSize,
                pomodoroFocusDuration = pomodoroFocusDuration,
                pomodoroShortBreakDuration = pomodoroShortBreakDuration,
                pomodoroLongBreakDuration = pomodoroLongBreakDuration,
                recentSearches = recentSearches,
                homeWidgetLayout = homeWidgetLayout,
                detectCopiedLinks = detectCopiedLinks
            )
        }

    suspend fun updateThemeMode(mode: AppThemeMode) { dataStore.edit { it[Keys.THEME_MODE] = mode.name } }
    suspend fun updateVisualMode(mode: VisualMode) { dataStore.edit { it[Keys.VISUAL_MODE] = mode.name } }
    suspend fun updateUiStyle(style: UiStyle) { dataStore.edit { it[Keys.UI_STYLE] = style.name } }
    suspend fun updateDisplayMode(mode: DisplayMode) { dataStore.edit { it[Keys.DISPLAY_MODE] = mode.name } }
    suspend fun updateColorTheme(theme: AppColorTheme) { dataStore.edit { it[Keys.COLOR_THEME] = theme.name } }
    suspend fun updateIconPack(pack: IconPack) {
        dataStore.edit {
            it[Keys.ICON_PACK] = pack.name
            it[Keys.ICON_STYLE] = pack.name
        }
    }
    suspend fun updateBackgroundStyle(style: BackgroundStyle) { dataStore.edit { it[Keys.BACKGROUND_STYLE] = style.name } }
    suspend fun updateBackgroundScope(scope: BackgroundScope) { dataStore.edit { it[Keys.BACKGROUND_SCOPE] = scope.name } }
    suspend fun updateCustomBackgroundUri(uri: String?) {
        dataStore.edit {
            if (uri == null) it.remove(Keys.CUSTOM_BACKGROUND_URI)
            else it[Keys.CUSTOM_BACKGROUND_URI] = uri
        }
    }
    suspend fun updateUiDensity(density: UiDensity) { dataStore.edit { it[Keys.UI_DENSITY] = density.name } }
    suspend fun updateCardTransparency(transparency: CardTransparency) { dataStore.edit { it[Keys.CARD_TRANSPARENCY] = transparency.name } }
    suspend fun updateBlurIntensity(blur: BlurIntensity) { dataStore.edit { it[Keys.BLUR_INTENSITY] = blur.name } }
    suspend fun updateCornerRoundness(roundness: CornerRoundness) { dataStore.edit { it[Keys.CORNER_ROUNDNESS] = roundness.name } }
    suspend fun updateAccentIntensity(intensity: AccentIntensity) { dataStore.edit { it[Keys.ACCENT_INTENSITY] = intensity.name } }
    suspend fun updateMotionPreference(motion: MotionPreference) { dataStore.edit { it[Keys.MOTION_PREFERENCE] = motion.name } }
    suspend fun updateUseDynamicColor(enabled: Boolean) { dataStore.edit { it[Keys.USE_DYNAMIC_COLOR] = enabled } }
    suspend fun updateHighContrast(enabled: Boolean) { dataStore.edit { it[Keys.HIGH_CONTRAST] = enabled } }

    suspend fun updateMasterNotificationsEnabled(enabled: Boolean) { dataStore.edit { it[Keys.MASTER_NOTIFICATIONS] = enabled } }
    suspend fun updateNotifyTaskReminders(enabled: Boolean) { dataStore.edit { it[Keys.NOTIFY_TASK_REMINDERS] = enabled } }
    suspend fun updateNotifyRunCompleted(enabled: Boolean) { dataStore.edit { it[Keys.NOTIFY_RUN_COMPLETED] = enabled } }
    suspend fun updateNotifyRunFailed(enabled: Boolean) { dataStore.edit { it[Keys.NOTIFY_RUN_FAILED] = enabled } }
    suspend fun updateNotifyAttentionRequired(enabled: Boolean) { dataStore.edit { it[Keys.NOTIFY_ATTENTION_REQUIRED] = enabled } }
    suspend fun updateNotifyGeneralAlerts(enabled: Boolean) { dataStore.edit { it[Keys.NOTIFY_GENERAL_ALERTS] = enabled } }

    suspend fun updateVoiceLanguage(lang: String) { dataStore.edit { it[Keys.VOICE_LANGUAGE] = lang } }
    suspend fun updateVoiceSpeed(speed: Float) { dataStore.edit { it[Keys.VOICE_SPEED] = speed } }
    suspend fun updateVoicePitch(pitch: Float) { dataStore.edit { it[Keys.VOICE_PITCH] = pitch } }
    suspend fun updateAutoSpeakResponses(enabled: Boolean) { dataStore.edit { it[Keys.AUTO_SPEAK_RESPONSES] = enabled } }
    suspend fun updateVoiceActivationEnabled(enabled: Boolean) { dataStore.edit { it[Keys.VOICE_ACTIVATION_ENABLED] = enabled } }
    suspend fun updateAiMode(mode: String) { dataStore.edit { it[Keys.AI_MODE] = mode } }
    suspend fun updateLudoGameSoundsEnabled(enabled: Boolean) { dataStore.edit { it[Keys.LUDO_SOUNDS] = enabled } }
    suspend fun updateTeleprompterScript(script: String) { dataStore.edit { it[Keys.TELEPROMPTER_SCRIPT] = script } }
    suspend fun updateTeleprompterSpeed(speed: Float) { dataStore.edit { it[Keys.TELEPROMPTER_SPEED] = speed } }
    suspend fun updateTeleprompterFontSize(size: Float) { dataStore.edit { it[Keys.TELEPROMPTER_FONT_SIZE] = size } }
    suspend fun updatePomodoroFocusDuration(mins: Int) { dataStore.edit { it[Keys.POMODORO_FOCUS] = mins } }
    suspend fun updatePomodoroShortBreakDuration(mins: Int) { dataStore.edit { it[Keys.POMODORO_SHORT] = mins } }
    suspend fun updatePomodoroLongBreakDuration(mins: Int) { dataStore.edit { it[Keys.POMODORO_LONG] = mins } }
    suspend fun updateRecentSearches(searches: List<String>) { dataStore.edit { it[Keys.RECENT_SEARCHES] = searches.joinToString(",") } }
    suspend fun updateHomeWidgetLayout(layout: String) { dataStore.edit { it[Keys.HOME_WIDGET_LAYOUT] = layout } }
    suspend fun updateDetectCopiedLinks(enabled: Boolean) { dataStore.edit { it[Keys.DETECT_COPIED_LINKS] = enabled } }

    suspend fun resetAppearance() {
        dataStore.edit {
            it[Keys.THEME_MODE] = AppThemeMode.SYSTEM.name
            it[Keys.VISUAL_MODE] = VisualMode.SYSTEM.name
            it[Keys.UI_STYLE] = UiStyle.MODERN_GLASS.name
            it[Keys.DISPLAY_MODE] = DisplayMode.SYSTEM.name
            it[Keys.COLOR_THEME] = AppColorTheme.CLASSIC_BLUE.name
            it[Keys.ICON_PACK] = IconPack.ROUNDED.name
            it[Keys.ICON_STYLE] = IconPack.ROUNDED.name
            it[Keys.BACKGROUND_STYLE] = BackgroundStyle.AUTO.name
            it[Keys.BACKGROUND_SCOPE] = BackgroundScope.GLOBAL.name
            it.remove(Keys.CUSTOM_BACKGROUND_URI)
            it[Keys.UI_DENSITY] = UiDensity.COMPACT.name
            it[Keys.CARD_TRANSPARENCY] = CardTransparency.MEDIUM.name
            it[Keys.BLUR_INTENSITY] = BlurIntensity.MEDIUM.name
            it[Keys.CORNER_ROUNDNESS] = CornerRoundness.MEDIUM.name
            it[Keys.ACCENT_INTENSITY] = AccentIntensity.BALANCED.name
            it[Keys.MOTION_PREFERENCE] = MotionPreference.STANDARD.name
            it[Keys.USE_DYNAMIC_COLOR] = true
            it[Keys.HIGH_CONTRAST] = false
        }
    }
}
