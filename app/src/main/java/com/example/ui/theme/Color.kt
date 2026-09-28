package com.example.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Color Palette for PixelRox AI
 * References centralized tokens in PixelRoxColors while preserving backwards compatibility aliases.
 */

// Modern Light Palette
val BentoBgLight = PixelRoxColors.Light.background
val BentoSurfaceLight = PixelRoxColors.Light.surface
val BentoSurfaceVariantLight = PixelRoxColors.Light.surfaceVariant
val BentoBorderLight = PixelRoxColors.Light.divider

val BentoPrimary = PixelRoxColors.Light.primary
val BentoOnPrimary = PixelRoxColors.Light.onPrimary
val BentoPrimaryContainer = PixelRoxColors.Light.primaryContainer
val BentoOnPrimaryContainer = PixelRoxColors.Light.onPrimaryContainer
val BentoBorderLavender = Color(0xFFDDD6FE)

val BentoSecondaryContainer = PixelRoxColors.Light.secondaryContainer
val BentoOnSecondaryContainer = PixelRoxColors.Light.onSecondaryContainer
val BentoBorderPink = Color(0xFFFDE68A)

val BentoTertiaryContainer = PixelRoxColors.Light.surfaceVariant
val BentoBorderTertiary = PixelRoxColors.Light.dividerSubtle

val BentoTextPrimary = PixelRoxColors.Light.textPrimary
val BentoTextSecondary = PixelRoxColors.Light.textSecondary

// Modern Dark Mode Palette
val BentoBgDark = PixelRoxColors.Dark.background
val BentoSurfaceDark = PixelRoxColors.Dark.surface
val BentoSurfaceVariantDark = PixelRoxColors.Dark.surfaceVariant
val BentoBorderDark = PixelRoxColors.Dark.divider
val BentoPrimaryContainerDark = PixelRoxColors.Dark.primaryContainer
val BentoOnPrimaryContainerDark = PixelRoxColors.Dark.onPrimaryContainer
val BentoSecondaryContainerDark = PixelRoxColors.Dark.secondaryContainer
val BentoOnSecondaryContainerDark = PixelRoxColors.Dark.onSecondaryContainer
val BentoBorderPinkDark = Color(0xFF78350F)
val BentoBorderLavenderDark = Color(0xFF4C1D95)
val BentoTextPrimaryDark = PixelRoxColors.Dark.textPrimary
val BentoTextSecondaryDark = PixelRoxColors.Dark.textSecondary

// Accent & Status colors adapted to modern aesthetic
val ElectricCyan = PixelRoxColors.Light.primary
val ElectricCyanDark = PixelRoxColors.Dark.primary
val NeonViolet = PixelRoxColors.Light.primary
val ElectricIndigo = Color(0xFF5B21B6)
val EmeraldGreen = PixelRoxColors.Light.success
val AmberAccent = PixelRoxColors.Light.warning
val CoralRed = PixelRoxColors.Light.error

// Legacy aliases preserved for backwards compatibility
val ObsidianBackground = BentoBgDark
val ObsidianSurface = BentoSurfaceDark
val ObsidianSurfaceVariant = BentoSurfaceVariantDark
val ObsidianBorder = BentoBorderDark

val TextPrimaryDark = BentoTextPrimaryDark
val TextSecondaryDark = BentoTextSecondaryDark
val TextMutedDark = PixelRoxColors.Dark.textTertiary

val LightBackground = BentoBgLight
val LightSurface = BentoSurfaceLight
val LightSurfaceVariant = BentoSurfaceVariantLight
val LightBorder = BentoBorderLight
val TextPrimaryLight = BentoTextPrimary
val TextSecondaryLight = BentoTextSecondary
