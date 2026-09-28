package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.example.data.repository.AppColorTheme
import com.example.data.repository.DisplayMode
import com.example.data.repository.IconPack
import com.example.data.repository.VisualMode

fun getPrimaryForTheme(theme: AppColorTheme, isDark: Boolean): Color = when (theme) {
    AppColorTheme.CLASSIC_BLUE -> if (isDark) Color(0xFFA78BFA) else Color(0xFF6B46C1)
    AppColorTheme.EMERALD_GREEN -> if (isDark) Color(0xFF34D399) else Color(0xFF10B981)
    AppColorTheme.PASTEL_PINK -> if (isDark) Color(0xFFF472B6) else Color(0xFFEC4899)
    AppColorTheme.NEON_VIOLET -> if (isDark) Color(0xFFC084FC) else Color(0xFF8B5CF6)
    AppColorTheme.SUNSET_ORANGE -> if (isDark) Color(0xFFFB923C) else Color(0xFFF97316)
    AppColorTheme.CHARCOAL_GRAY -> if (isDark) Color(0xFF9CA3AF) else Color(0xFF4B5563)
    AppColorTheme.MATERIAL_DYNAMIC -> if (isDark) Color(0xFFA78BFA) else Color(0xFF6B46C1)
}

@Composable
fun PixelRoxTheme(
    displayMode: DisplayMode = DisplayMode.SYSTEM,
    visualMode: VisualMode = VisualMode.SYSTEM,
    colorTheme: AppColorTheme = AppColorTheme.CLASSIC_BLUE,
    iconPack: IconPack = IconPack.ROUNDED,
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current

    val effectiveMode = when (displayMode) {
        DisplayMode.LIGHT -> VisualMode.LIGHT
        DisplayMode.DARK -> VisualMode.DARK
        DisplayMode.MIDNIGHT -> VisualMode.MIDNIGHT
        DisplayMode.SYSTEM -> visualMode
    }

    val isDark = when (effectiveMode) {
        VisualMode.DARK, VisualMode.MIDNIGHT -> true
        VisualMode.LIGHT, VisualMode.MINIMAL -> false
        VisualMode.SYSTEM -> darkTheme
    }

    val isOled = (effectiveMode == VisualMode.MIDNIGHT)
    val isMinimal = (effectiveMode == VisualMode.MINIMAL)

    val primaryColor = getPrimaryForTheme(colorTheme, isDark)

    val colorScheme = when {
        dynamicColor && colorTheme == AppColorTheme.MATERIAL_DYNAMIC && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        isOled -> darkColorScheme(
            primary = primaryColor,
            onPrimary = Color.Black,
            primaryContainer = Color(0xFF1E1E1E),
            onPrimaryContainer = Color.White,
            background = Color(0xFF000000),
            onBackground = Color(0xFFFAFAFA),
            surface = Color(0xFF0D0D0D),
            onSurface = Color(0xFFFAFAFA),
            surfaceVariant = Color(0xFF181818),
            onSurfaceVariant = Color(0xFFB0B0B0),
            outline = Color(0xFF2C2C2C)
        )
        isMinimal -> lightColorScheme(
            primary = primaryColor,
            onPrimary = Color.White,
            background = Color(0xFFFFFFFF),
            onBackground = Color(0xFF111111),
            surface = Color(0xFFFFFFFF),
            onSurface = Color(0xFF111111),
            surfaceVariant = Color(0xFFF8F9FA),
            onSurfaceVariant = Color(0xFF555555),
            outline = Color(0xFFE0E0E0)
        )
        isDark -> darkColorScheme(
            primary = primaryColor,
            onPrimary = PixelRoxColors.Dark.onPrimary,
            primaryContainer = PixelRoxColors.Dark.primaryContainer,
            onPrimaryContainer = PixelRoxColors.Dark.onPrimaryContainer,
            secondary = primaryColor,
            onSecondary = PixelRoxColors.Dark.onSecondary,
            background = PixelRoxColors.Dark.background,
            onBackground = PixelRoxColors.Dark.textPrimary,
            surface = PixelRoxColors.Dark.surface,
            onSurface = PixelRoxColors.Dark.textPrimary,
            surfaceVariant = PixelRoxColors.Dark.surfaceVariant,
            onSurfaceVariant = PixelRoxColors.Dark.textSecondary,
            outline = PixelRoxColors.Dark.divider
        )
        else -> lightColorScheme(
            primary = primaryColor,
            onPrimary = PixelRoxColors.Light.onPrimary,
            primaryContainer = PixelRoxColors.Light.primaryContainer,
            onPrimaryContainer = PixelRoxColors.Light.onPrimaryContainer,
            secondary = primaryColor,
            onSecondary = PixelRoxColors.Light.onSecondary,
            background = PixelRoxColors.Light.background,
            onBackground = PixelRoxColors.Light.textPrimary,
            surface = PixelRoxColors.Light.surface,
            onSurface = PixelRoxColors.Light.textPrimary,
            surfaceVariant = PixelRoxColors.Light.surfaceVariant,
            onSurfaceVariant = PixelRoxColors.Light.textSecondary,
            outline = PixelRoxColors.Light.divider
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    PixelRoxTheme(
        darkTheme = darkTheme,
        dynamicColor = dynamicColor,
        content = content
    )
}
