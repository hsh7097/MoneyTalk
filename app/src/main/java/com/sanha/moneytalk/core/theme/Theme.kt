package com.sanha.moneytalk.core.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ============================================================================
// 테마 모드 — Settings에서 사용자가 선택 가능
// ============================================================================
enum class ThemeMode {
    SYSTEM, // 시스템 설정에 따라 자동 전환 (기본값)
    LIGHT,  // 항상 라이트 모드
    DARK    // 항상 다크 모드
}

// ============================================================================
// 커스텀 확장 색상 — MaterialTheme.colorScheme에 없는 앱 전용 색상
// ============================================================================
@Immutable
data class MoneyTalkExtendedColors(
    val income: Color,          // 수입 금액
    val expense: Color,         // 지출 금액
    val calendarSunday: Color,  // 달력 일요일
    val calendarSaturday: Color // 달력 토요일
)

// CompositionLocal로 하위 Composable에 전달
val LocalMoneyTalkColors = staticCompositionLocalOf {
    MoneyTalkExtendedColors(
        income = Color.Unspecified,
        expense = Color.Unspecified,
        calendarSunday = Color.Unspecified,
        calendarSaturday = Color.Unspecified
    )
}

// MaterialTheme 확장 프로퍼티 — 사용법: MaterialTheme.moneyTalkColors.income
val MaterialTheme.moneyTalkColors: MoneyTalkExtendedColors
    @Composable
    @ReadOnlyComposable
    get() = LocalMoneyTalkColors.current

// ============================================================================
// 라이트/다크 확장 색상 정의
// ============================================================================

// 라이트 테마 확장 색상
private val LightExtendedColors = MoneyTalkExtendedColors(
    income = IncomeLight,              // #137FEC 파란색 — 라이트 테마 수입 (SVG 기준)
    expense = ExpenseColor,            // #EF4444 빨간색 — 지출 (SVG 기준, 라이트/다크 공용)
    calendarSunday = CalendarSunday,   // #EF4444 일요일 (빨간 계열, SVG 기준)
    calendarSaturday = CalendarSaturday // #137FEC 토요일 (파란 계열, SVG 기준)
)

// 다크 테마 확장 색상
private val DarkExtendedColors = MoneyTalkExtendedColors(
    income = IncomeDark,               // #3AC977 초록색 — 다크 테마 수입
    expense = ExpenseColor,            // #EF4444 빨간색 — 지출 (SVG 기준, 라이트/다크 공용)
    calendarSunday = CalendarSunday,   // #EF4444 일요일 (빨간 계열, SVG 기준)
    calendarSaturday = CalendarSaturday // #137FEC 토요일 (파란 계열, SVG 기준)
)

// ============================================================================
// Material 3 Color Scheme
// ============================================================================

// 다크 테마 — 어두운 배경, 밝은 텍스트
private val DarkColorScheme = darkColorScheme(
    primary = PrimaryLight,              // Green 500
    onPrimary = OnPrimary,               // White
    primaryContainer = PrimaryDark,      // Green 900
    onPrimaryContainer = PrimaryContainer, // Green 100

    secondary = SecondaryLight,          // Blue 400
    onSecondary = OnSecondary,           // White
    secondaryContainer = SecondaryDark,  // Blue 900
    onSecondaryContainer = SecondaryContainer, // Blue 100

    tertiary = TertiaryLight,            // Orange 300
    onTertiary = OnTertiary,             // White
    tertiaryContainer = TertiaryDark,    // Orange 700
    onTertiaryContainer = TertiaryContainer, // Orange 100

    error = ErrorLight,                  // Red 400
    onError = OnError,                   // White
    errorContainer = ErrorDark,          // Red 900
    onErrorContainer = ErrorContainer,   // Red 100

    background = BackgroundDark,         // #121212
    onBackground = OnBackgroundDark,     // Grey 300

    surface = SurfaceDark,               // #1E1E1E
    onSurface = OnSurfaceDark,           // Grey 300
    surfaceVariant = SurfaceVariantDark, // #2D2D2D
    onSurfaceVariant = Grey400,          // Grey 400

    outline = OutlineDark,               // Grey 800
    outlineVariant = Grey700             // Grey 700
)

// 라이트 테마 — 밝은 배경, 어두운 텍스트
private val LightColorScheme = lightColorScheme(
    primary = Primary,                   // Green 800
    onPrimary = OnPrimary,               // White
    primaryContainer = PrimaryContainer, // Green 100
    onPrimaryContainer = OnPrimaryContainer, // Green 900

    secondary = Secondary,               // Blue 700
    onSecondary = OnSecondary,           // White
    secondaryContainer = SecondaryContainer, // Blue 100
    onSecondaryContainer = OnSecondaryContainer, // Blue 900

    tertiary = Tertiary,                 // Orange 500
    onTertiary = OnTertiary,             // White
    tertiaryContainer = TertiaryContainer, // Orange 100
    onTertiaryContainer = OnTertiaryContainer, // Orange 900

    error = Error,                       // Red 700
    onError = OnError,                   // White
    errorContainer = ErrorContainer,     // Red 100
    onErrorContainer = OnErrorContainer, // Red 900

    background = Background,             // Grey 50
    onBackground = OnBackground,         // Grey 900

    surface = Surface,                   // White
    onSurface = OnSurface,               // Grey 900
    surfaceVariant = SurfaceVariant,     // Grey 100
    onSurfaceVariant = OnSurfaceVariant, // Grey 600

    outline = Outline,                   // Grey 400
    outlineVariant = OutlineVariant      // Grey 300
)

// ============================================================================
// MoneyTalkTheme
// ============================================================================
@Composable
fun MoneyTalkTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    // 테마 모드에 따라 다크 여부 결정
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val extendedColors = if (darkTheme) DarkExtendedColors else LightExtendedColors

    // 상태바 색상 설정 — 배경색과 동일하게
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            // 라이트 테마: 어두운 아이콘, 다크 테마: 밝은 아이콘
            WindowCompat.getInsetsController(window, view)
                .isAppearanceLightStatusBars = !darkTheme
        }
    }

    CompositionLocalProvider(
        LocalMoneyTalkColors provides extendedColors
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
