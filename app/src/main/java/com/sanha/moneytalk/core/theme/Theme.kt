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
    val navyDark: Color,            // #1B2838 — 주요 강조
    val navyMedium: Color,          // #2C3E50 — 카드 헤더, 섹션 제목
    val navyTint: Color,            // #E8EDF2 — 카드 배경 틴트
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

// 라이트 테마 확장 색상
private val LightExtendedColors = MoneyTalkExtendedColors(
    income = IncomeLight,              // #137FEC 파란색
    expense = ExpenseColor,            // #EF4444 빨간색
    calendarSunday = CalendarSunday,   // #EF4444
    calendarSaturday = CalendarSaturday, // #137FEC
    navyDark = NavyDark,              // #1B2838
    navyMedium = NavyMedium,          // #2C3E50
    navyTint = NavyTint,              // #E8EDF2
    textPrimary = Gray900,            // #111827
    textSecondary = Gray600,          // #6B7280
    textTertiary = Gray400,           // #9CA3AF
    divider = Gray200,                // #E5E7EB
    cardBackground = Gray100          // #F3F4F6
)

// 다크 테마 확장 색상 (Green/Orange 기반 복원)
private val DarkExtendedColors = MoneyTalkExtendedColors(
    income = IncomeDark,               // #3AC977 초록색
    expense = ExpenseColor,            // #EF4444 빨간색
    calendarSunday = CalendarSunday,   // #EF4444
    calendarSaturday = CalendarSaturday, // #137FEC
    navyDark = Color(0xFFA5D6A7),     // 다크에서 Green 200 (강조, 가독성)
    navyMedium = Color(0xFF81C784),   // 다크에서 Green 300 (카드 헤더)
    navyTint = Color(0xFF2D3239),     // 다크에서 어두운 Tint (유지)
    textPrimary = Color(0xFFECECEC),  // 다크 메인 텍스트
    textSecondary = DarkGrey400,          // #6B7684
    textTertiary = Color(0xFF4A5568), // 다크 3차 텍스트
    divider = Color(0xFF3A3F47),      // 다크 구분선
    cardBackground = Color(0xFF2D3239) // 다크 보조 카드 배경
)

// ============================================================================
// Material 3 Color Scheme
// ============================================================================

// 다크 테마 — 어두운 배경, 밝은 텍스트 (Green/Orange 기반 복원)
private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,               // Green 500 (#4CAF50)
    onPrimary = OnPrimary,               // White
    primaryContainer = DarkPrimaryContainer, // Green 900 (#1B5E20)
    onPrimaryContainer = DarkOnPrimaryContainer, // Green 100 (#C8E6C9)

    secondary = SecondaryLight,          // Blue 400
    onSecondary = OnSecondary,           // White
    secondaryContainer = SecondaryDark,  // Blue 900
    onSecondaryContainer = SecondaryContainer, // Blue 100

    tertiary = DarkTertiary,             // Orange 300 (#FFB74D)
    onTertiary = OnTertiary,             // White
    tertiaryContainer = DarkTertiaryContainer, // Orange 700 (#F57C00)
    onTertiaryContainer = DarkOnTertiaryContainer, // Orange 100 (#FFE0B2)

    error = ErrorLight,                  // Red 400
    onError = OnError,                   // White
    errorContainer = ErrorDark,          // Red 900
    onErrorContainer = ErrorContainer,   // Red 100

    background = BackgroundDark,         // #171A1E
    onBackground = OnBackgroundDark,     // Grey 300

    surface = SurfaceDark,               // #252A30
    onSurface = OnSurfaceDark,           // Grey 300
    surfaceVariant = SurfaceVariantDark, // #2D3239
    onSurfaceVariant = DarkGrey400,          // Grey 400

    outline = OutlineDark,               // Grey 800
    outlineVariant = DarkGrey700             // Grey 700
)

// 라이트 테마 — 밝은 배경, 어두운 텍스트
private val LightColorScheme = lightColorScheme(
    primary = Primary,                   // Navy Dark (#1B2838)
    onPrimary = OnPrimary,               // White
    primaryContainer = PrimaryContainer, // Navy Tint (#E8EDF2)
    onPrimaryContainer = OnPrimaryContainer, // Navy Dark

    secondary = Secondary,               // Blue 700
    onSecondary = OnSecondary,           // White
    secondaryContainer = SecondaryContainer, // Blue 100
    onSecondaryContainer = OnSecondaryContainer, // Blue 900

    tertiary = Tertiary,                 // Navy Medium (#2C3E50)
    onTertiary = OnTertiary,             // White
    tertiaryContainer = TertiaryContainer, // Navy Tint (#E8EDF2)
    onTertiaryContainer = OnTertiaryContainer, // Navy Dark

    error = Error,                       // Red (#EF4444)
    onError = OnError,                   // White
    errorContainer = ErrorContainer,     // Red 100
    onErrorContainer = OnErrorContainer, // Red 900

    background = Background,             // gray50 (#F9FAFB)
    onBackground = OnBackground,         // gray900 (#111827)

    surface = Surface,                   // White
    onSurface = OnSurface,               // gray900
    surfaceVariant = SurfaceVariant,     // gray100 (#F3F4F6)
    onSurfaceVariant = OnSurfaceVariant, // gray600 (#6B7280)

    outline = Outline,                   // gray200 (#E5E7EB)
    outlineVariant = OutlineVariant      // gray100 (#F3F4F6)
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
