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
    val income: Color,              // 수입 금액
    val expense: Color,             // 지출 금액
    val calendarSunday: Color,      // 달력 일요일
    val calendarSaturday: Color,    // 달력 토요일
    // Navy 계열 확장
    val navyDark: Color,            // 주요 중립 텍스트
    val navyMedium: Color,          // 보조 중립 텍스트
    val navyTint: Color,            // 중립 보조 표면
    // Gray Scale 확장
    val textPrimary: Color,         // gray900 — 1차 텍스트
    val textSecondary: Color,       // gray600 — 2차 텍스트
    val textTertiary: Color,        // gray400 — 3차 텍스트, 힌트
    val divider: Color,             // gray200 — 구분선
    val cardBackground: Color       // gray100 — 보조 카드 배경
)

// CompositionLocal로 하위 Composable에 전달
val LocalMoneyTalkColors = staticCompositionLocalOf {
    MoneyTalkExtendedColors(
        income = Color.Unspecified,
        expense = Color.Unspecified,
        calendarSunday = Color.Unspecified,
        calendarSaturday = Color.Unspecified,
        navyDark = Color.Unspecified,
        navyMedium = Color.Unspecified,
        navyTint = Color.Unspecified,
        textPrimary = Color.Unspecified,
        textSecondary = Color.Unspecified,
        textTertiary = Color.Unspecified,
        divider = Color.Unspecified,
        cardBackground = Color.Unspecified
    )
}

// MaterialTheme 확장 프로퍼티 — 사용법: MaterialTheme.moneyTalkColors.income
val MaterialTheme.moneyTalkColors: MoneyTalkExtendedColors
    @Composable
    @ReadOnlyComposable
    get() = LocalMoneyTalkColors.current

// MaterialTheme 확장 프로퍼티 — 숫자 Typography
val MaterialTheme.moneyTalkTypography: MoneyTalkNumberTypography
    @Composable
    @ReadOnlyComposable
    get() = LocalMoneyTalkNumberTypography.current

// ============================================================================
// 라이트/다크 확장 색상 정의
// ============================================================================

// 화면과 공통 컴포넌트가 같은 의미 색상을 공유한다.
private val LightExtendedColors = MoneyTalkExtendedColors(
    income = IncomeLight,
    expense = ExpenseColor,
    calendarSunday = CalendarSunday,
    calendarSaturday = CalendarSaturday,
    navyDark = NavyDark,
    navyMedium = NavyMedium,
    navyTint = NavyTint,
    textPrimary = Gray900,
    textSecondary = Gray600,
    textTertiary = Gray400,
    divider = Gray200,
    cardBackground = Gray100
)

private val DarkExtendedColors = MoneyTalkExtendedColors(
    income = IncomeDark,
    expense = OnSurfaceDark,
    calendarSunday = ErrorLight,
    calendarSaturday = Color(0xFF9DC6FA),
    navyDark = OnSurfaceDark,
    navyMedium = Color(0xFFD1D9E1),
    navyTint = SurfaceVariantDark,
    textPrimary = OnSurfaceDark,
    textSecondary = DarkGrey400,
    textTertiary = Color(0xFF9AA5B1),
    divider = DarkGrey700,
    cardBackground = SurfaceVariantDark
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = Color(0xFF073A2A),
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    inversePrimary = Primary,
    secondary = SecondaryLight,
    onSecondary = Color(0xFF073A2A),
    secondaryContainer = SecondaryDark,
    onSecondaryContainer = DarkOnPrimaryContainer,
    tertiary = DarkTertiary,
    onTertiary = OnSurface,
    tertiaryContainer = DarkTertiaryContainer,
    onTertiaryContainer = DarkOnTertiaryContainer,
    error = ErrorLight,
    onError = Color(0xFF50151E),
    errorContainer = ErrorDark,
    onErrorContainer = Color(0xFFFFDADD),
    background = BackgroundDark,
    onBackground = OnBackgroundDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = DarkGrey400,
    surfaceTint = Color.Transparent,
    inverseSurface = OnSurfaceDark,
    inverseOnSurface = OnSurface,
    outline = OutlineDark,
    outlineVariant = DarkGrey700,
    surfaceBright = Color(0xFF353D46),
    surfaceDim = BackgroundDark,
    surfaceContainerLowest = BackgroundDark,
    surfaceContainerLow = SurfaceDark,
    surfaceContainer = SurfaceDark,
    surfaceContainerHigh = SurfaceVariantDark,
    surfaceContainerHighest = Color(0xFF353D46)
)

private val LightColorScheme = lightColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = OnPrimaryContainer,
    inversePrimary = DarkPrimary,
    secondary = Secondary,
    onSecondary = OnSecondary,
    secondaryContainer = SecondaryContainer,
    onSecondaryContainer = OnSecondaryContainer,
    tertiary = Tertiary,
    onTertiary = OnTertiary,
    tertiaryContainer = TertiaryContainer,
    onTertiaryContainer = OnTertiaryContainer,
    error = Error,
    onError = OnError,
    errorContainer = ErrorContainer,
    onErrorContainer = OnErrorContainer,
    background = Background,
    onBackground = OnBackground,
    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = OnSurfaceVariant,
    surfaceTint = Color.Transparent,
    inverseSurface = SurfaceDark,
    inverseOnSurface = OnSurfaceDark,
    outline = Outline,
    outlineVariant = OutlineVariant,
    surfaceBright = Surface,
    surfaceDim = Color(0xFFE6E9ED),
    surfaceContainerLowest = Surface,
    surfaceContainerLow = Surface,
    surfaceContainer = Surface,
    surfaceContainerHigh = SurfaceVariant,
    surfaceContainerHighest = Color(0xFFE8ECF0)
)

// ============================================================================
// MoneyTalkTheme
/** MoneyTalk 앱 테마. 라이트/다크 모드에 따른 색상 스킴과 상태바 설정을 적용 */
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

    // 상태바 아이콘 색상 설정 (라이트/다크)
    // API 35+에서 statusBarColor는 deprecated (enableEdgeToEdge()가 투명 처리)
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            @Suppress("DEPRECATION")
            window.statusBarColor = colorScheme.background.toArgb()
            // 라이트 테마: 어두운 아이콘, 다크 테마: 밝은 아이콘
            WindowCompat.getInsetsController(window, view)
                .isAppearanceLightStatusBars = !darkTheme
        }
    }

    CompositionLocalProvider(
        LocalMoneyTalkColors provides extendedColors,
        LocalMoneyTalkNumberTypography provides NumberTypography
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
