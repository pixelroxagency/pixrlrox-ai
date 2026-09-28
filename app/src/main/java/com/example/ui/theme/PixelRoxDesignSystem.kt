package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * PIXELROX_MODERN_UI_DESIGN_SYSTEM_V1
 * Centralized semantic tokens: Colors, Spacing, Shapes, and Typography.
 */

object PixelRoxColors {
    object Light {
        // Surfaces
        val background = Color(0xFFF8F9FA)
        val surface = Color(0xFFFFFFFF)
        val surfaceVariant = Color(0xFFF1F3F5)
        val surfaceContainerHighest = Color(0xFFE9ECEF)
        val surfaceSubtle = Color(0xFFF6F7F9)

        // Brand Accents
        val primary = Color(0xFF6B46C1) // Premium restrained purple
        val onPrimary = Color(0xFFFFFFFF)
        val primaryContainer = Color(0xFFF3E8FF) // Soft tinted purple
        val onPrimaryContainer = Color(0xFF3B0764)

        val secondaryAccent = Color(0xFFF59E0B) // Warm yellow / gold
        val onSecondary = Color(0xFFFFFFFF)
        val secondaryContainer = Color(0xFFFEF3C7) // Soft warm yellow tint
        val onSecondaryContainer = Color(0xFF78350F)

        // Text Hierarchy
        val textPrimary = Color(0xFF111827) // Near-black
        val textSecondary = Color(0xFF6B7280) // Medium neutral gray
        val textTertiary = Color(0xFF9CA3AF) // Subtle muted gray

        // Dividers & Borders
        val divider = Color(0xFFE5E7EB) // Subtle neutral line
        val dividerSubtle = Color(0xFFF3F4F6)
        val border = Color(0xFFE5E7EB)

        // Semantics & Status
        val success = Color(0xFF10B981) // Controlled emerald green
        val successContainer = Color(0xFFD1FAE5)
        val onSuccessContainer = Color(0xFF065F46)

        val warning = Color(0xFFF59E0B) // Warm amber
        val warningContainer = Color(0xFFFEF3C7)
        val onWarningContainer = Color(0xFF92400E)

        val error = Color(0xFFEF4444) // Controlled red
        val errorContainer = Color(0xFFFEE2E2)
        val onErrorContainer = Color(0xFF991B1B)

        val forbidden = Color(0xFFDC2626)
        val forbiddenContainer = Color(0xFFFEF2F2)
        val onForbidden = Color(0xFF991B1B)
    }

    object Dark {
        // Surfaces
        val background = Color(0xFF0F0F14)
        val surface = Color(0xFF171720)
        val surfaceVariant = Color(0xFF21212C)
        val surfaceContainerHighest = Color(0xFF2B2C3A)
        val surfaceSubtle = Color(0xFF1C1C26)

        // Brand Accents
        val primary = Color(0xFFA78BFA) // Vibrant purple accent in dark mode
        val onPrimary = Color(0xFF1E1035)
        val primaryContainer = Color(0xFF2E1065)
        val onPrimaryContainer = Color(0xFFE9D5FF)

        val secondaryAccent = Color(0xFFFBBF24) // Warm yellow
        val onSecondary = Color(0xFF3A2800)
        val secondaryContainer = Color(0xFF451A03)
        val onSecondaryContainer = Color(0xFFFDE68A)

        // Text Hierarchy
        val textPrimary = Color(0xFFF9FAFB)
        val textSecondary = Color(0xFF9CA3AF)
        val textTertiary = Color(0xFF6B7280)

        // Dividers & Borders
        val divider = Color(0xFF272935)
        val dividerSubtle = Color(0xFF1E202B)
        val border = Color(0xFF2D303E)

        // Semantics & Status
        val success = Color(0xFF34D399)
        val successContainer = Color(0xFF064E3B)
        val onSuccessContainer = Color(0xFFA7F3D0)

        val warning = Color(0xFFFBBF24)
        val warningContainer = Color(0xFF78350F)
        val onWarningContainer = Color(0xFFFDE68A)

        val error = Color(0xFFF87171)
        val errorContainer = Color(0xFF450A0A)
        val onErrorContainer = Color(0xFFFECACA)

        val forbidden = Color(0xFFF87171)
        val forbiddenContainer = Color(0xFF450A0A)
        val onForbidden = Color(0xFFFECACA)
    }
}

object PixelRoxSpacing {
    val xxs: Dp = 2.dp
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val lg: Dp = 16.dp
    val xl: Dp = 20.dp
    val xxl: Dp = 24.dp
    val huge: Dp = 32.dp
}

object PixelRoxShapes {
    val control = RoundedCornerShape(12.dp)
    val input = RoundedCornerShape(14.dp)
    val button = RoundedCornerShape(14.dp)
    val card = RoundedCornerShape(18.dp)
    val cardLarge = RoundedCornerShape(22.dp)
    val container = RoundedCornerShape(24.dp)
    val pill = RoundedCornerShape(50)
}

object PixelRoxTypography {
    private val defaultFontFamily = FontFamily.Default

    val hero = TextStyle(
        fontFamily = defaultFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.5).sp
    )

    val screenTitle = TextStyle(
        fontFamily = defaultFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.2).sp
    )

    val sectionTitle = TextStyle(
        fontFamily = defaultFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp
    )

    val cardTitle = TextStyle(
        fontFamily = defaultFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp
    )

    val body = TextStyle(
        fontFamily = defaultFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.2.sp
    )

    val secondaryBody = TextStyle(
        fontFamily = defaultFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.1.sp
    )

    val caption = TextStyle(
        fontFamily = defaultFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.2.sp
    )

    val buttonLabel = TextStyle(
        fontFamily = defaultFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.3.sp
    )

    val navLabel = TextStyle(
        fontFamily = defaultFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.2.sp
    )

    val overline = TextStyle(
        fontFamily = defaultFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        letterSpacing = 1.sp
    )
}
