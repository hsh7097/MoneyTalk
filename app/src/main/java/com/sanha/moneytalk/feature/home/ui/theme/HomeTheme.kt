package com.sanha.moneytalk.feature.home.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.sp
import com.sanha.moneytalk.core.theme.LocalMoneyTalkColors
import com.sanha.moneytalk.core.theme.LocalMoneyTalkNumberTypography

/** 600996a의 홈 표현만 복원한다. 앱 테마 선택과 시스템 바 설정은 바깥 테마가 담당한다. */
@Composable
internal fun HomeTheme(content: @Composable () -> Unit) {
    val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val colors = remember(darkTheme) {
        if (darkTheme) {
            darkColorScheme(
                primary = Color(0xFF4CAF50),
                onPrimary = Color.White,
                primaryContainer = Color(0xFF1B5E20),
                onPrimaryContainer = Color(0xFFC8E6C9),
                secondary = Color(0xFF42A5F5),
                onSecondary = Color.White,
                secondaryContainer = Color(0xFF0D47A1),
                onSecondaryContainer = Color(0xFFBBDEFB),
                tertiary = Color(0xFFFFB74D),
                onTertiary = Color.White,
                tertiaryContainer = Color(0xFFF57C00),
                onTertiaryContainer = Color(0xFFFFE0B2),
                error = Color(0xFFEF4444),
                onError = Color.White,
                errorContainer = Color(0xFFB71C1C),
                onErrorContainer = Color(0xFFFFCDD2),
                background = Color(0xFF171A1E),
                onBackground = Color(0xFFECECEC),
                surface = Color(0xFF252A30),
                onSurface = Color(0xFFECECEC),
                surfaceVariant = Color(0xFF2D3239),
                onSurfaceVariant = Color(0xFFB0BAC6),
                outline = Color(0xFF3A3F47),
                outlineVariant = Color(0xFF3A3F47)
            )
        } else {
            lightColorScheme(
                primary = Color(0xFF1B2838),
                onPrimary = Color.White,
                primaryContainer = Color(0xFFE8EDF2),
                onPrimaryContainer = Color(0xFF1B2838),
                secondary = Color(0xFF1976D2),
                onSecondary = Color.White,
                secondaryContainer = Color(0xFFBBDEFB),
                onSecondaryContainer = Color(0xFF0D47A1),
                tertiary = Color(0xFF2C3E50),
                onTertiary = Color.White,
                tertiaryContainer = Color(0xFFE8EDF2),
                onTertiaryContainer = Color(0xFF1B2838),
                error = Color(0xFFEF4444),
                onError = Color.White,
                errorContainer = Color(0xFFFFCDD2),
                onErrorContainer = Color(0xFFB71C1C),
                background = Color(0xFFF9FAFB),
                onBackground = Color(0xFF111827),
                surface = Color.White,
                onSurface = Color(0xFF111827),
                surfaceVariant = Color(0xFFF3F4F6),
                onSurfaceVariant = Color(0xFF6B7280),
                outline = Color(0xFFE5E7EB),
                outlineVariant = Color(0xFFF3F4F6)
            )
        }
    }
    val inheritedColors = LocalMoneyTalkColors.current
    val extendedColors = remember(darkTheme, inheritedColors, colors) {
        inheritedColors.copy(
            income = if (darkTheme) Color(0xFF3AC977) else Color(0xFF137FEC),
            expense = Color(0xFFEF4444),
            calendarSunday = Color(0xFFEF4444),
            calendarSaturday = Color(0xFF137FEC),
            navyDark = if (darkTheme) Color(0xFFA5D6A7) else Color(0xFF1B2838),
            navyMedium = if (darkTheme) Color(0xFF81C784) else Color(0xFF2C3E50),
            navyTint = if (darkTheme) Color(0xFF2D3239) else Color(0xFFE8EDF2),
            textPrimary = colors.onSurface,
            textSecondary = colors.onSurfaceVariant,
            textTertiary = if (darkTheme) Color(0xFF4A5568) else Color(0xFF9CA3AF),
            divider = if (darkTheme) Color(0xFF3A3F47) else Color(0xFFE5E7EB),
            cardBackground = colors.surfaceVariant
        )
    }
    val inheritedTypography = MaterialTheme.typography
    val typography = remember(inheritedTypography) {
        inheritedTypography.copy(
            displayLarge = inheritedTypography.displayLarge.copy(fontFeatureSettings = null),
            titleMedium = inheritedTypography.titleMedium.copy(letterSpacing = 0.15.sp),
            titleSmall = inheritedTypography.titleSmall.copy(letterSpacing = 0.1.sp),
            bodyLarge = inheritedTypography.bodyLarge.copy(letterSpacing = 0.5.sp),
            bodyMedium = inheritedTypography.bodyMedium.copy(letterSpacing = 0.25.sp),
            bodySmall = inheritedTypography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp),
            labelLarge = inheritedTypography.labelLarge.copy(letterSpacing = 0.1.sp),
            labelMedium = inheritedTypography.labelMedium.copy(fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp),
            labelSmall = inheritedTypography.labelSmall.copy(fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp)
        )
    }
    val inheritedNumberTypography = LocalMoneyTalkNumberTypography.current
    val numberTypography = remember(inheritedNumberTypography) {
        inheritedNumberTypography.copy(
            numberLarge = inheritedNumberTypography.numberLarge.copy(fontFeatureSettings = null)
        )
    }

    CompositionLocalProvider(
        LocalMoneyTalkColors provides extendedColors,
        LocalMoneyTalkNumberTypography provides numberTypography
    ) {
        MaterialTheme(colorScheme = colors, typography = typography, content = content)
    }
}
