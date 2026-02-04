package com.sanha.moneytalk.ui.theme

import androidx.compose.ui.graphics.Color

// ==================== Primary Colors ====================
// 메인 브랜드 색상 (초록 계열 - 돈/재무 느낌)
val Primary = Color(0xFF2E7D32)          // Green 800
val PrimaryLight = Color(0xFF4CAF50)     // Green 500
val PrimaryDark = Color(0xFF1B5E20)      // Green 900
val PrimaryContainer = Color(0xFFC8E6C9) // Green 100
val OnPrimary = Color(0xFFFFFFFF)
val OnPrimaryContainer = Color(0xFF1B5E20)

// ==================== Secondary Colors ====================
// 보조 색상 (블루 계열)
val Secondary = Color(0xFF1976D2)        // Blue 700
val SecondaryLight = Color(0xFF42A5F5)   // Blue 400
val SecondaryDark = Color(0xFF0D47A1)    // Blue 900
val SecondaryContainer = Color(0xFFBBDEFB) // Blue 100
val OnSecondary = Color(0xFFFFFFFF)
val OnSecondaryContainer = Color(0xFF0D47A1)

// ==================== Tertiary Colors ====================
// 강조 색상 (오렌지 계열)
val Tertiary = Color(0xFFFF9800)         // Orange 500
val TertiaryLight = Color(0xFFFFB74D)    // Orange 300
val TertiaryDark = Color(0xFFF57C00)     // Orange 700
val TertiaryContainer = Color(0xFFFFE0B2) // Orange 100
val OnTertiary = Color(0xFFFFFFFF)
val OnTertiaryContainer = Color(0xFFE65100)

// ==================== Error Colors ====================
// 오류/경고 색상 (빨강 계열 - 지출 표시에도 사용)
val Error = Color(0xFFD32F2F)            // Red 700
val ErrorLight = Color(0xFFEF5350)       // Red 400
val ErrorDark = Color(0xFFB71C1C)        // Red 900
val ErrorContainer = Color(0xFFFFCDD2)   // Red 100
val OnError = Color(0xFFFFFFFF)
val OnErrorContainer = Color(0xFFB71C1C)

// ==================== Background & Surface ====================
// 배경 색상
val Background = Color(0xFFFAFAFA)       // Grey 50
val BackgroundDark = Color(0xFF121212)   // Dark background
val Surface = Color(0xFFFFFFFF)
val SurfaceDark = Color(0xFF1E1E1E)
val SurfaceVariant = Color(0xFFF5F5F5)   // Grey 100
val SurfaceVariantDark = Color(0xFF2D2D2D)
val OnBackground = Color(0xFF212121)     // Grey 900
val OnBackgroundDark = Color(0xFFE0E0E0)
val OnSurface = Color(0xFF212121)
val OnSurfaceDark = Color(0xFFE0E0E0)
val OnSurfaceVariant = Color(0xFF757575) // Grey 600

// ==================== Outline ====================
val Outline = Color(0xFFBDBDBD)          // Grey 400
val OutlineDark = Color(0xFF424242)      // Grey 800
val OutlineVariant = Color(0xFFE0E0E0)   // Grey 300

// ==================== Semantic Colors ====================
// 수입 (파랑/초록)
val Income = Color(0xFF2196F3)           // Blue 500
val IncomeLight = Color(0xFF64B5F6)      // Blue 300
val IncomeDark = Color(0xFF1565C0)       // Blue 800
val IncomeContainer = Color(0xFFE3F2FD)  // Blue 50

// 지출 (빨강)
val Expense = Color(0xFFF44336)          // Red 500
val ExpenseLight = Color(0xFFE57373)     // Red 300
val ExpenseDark = Color(0xFFC62828)      // Red 800
val ExpenseContainer = Color(0xFFFFEBEE) // Red 50

// 저축 (보라)
val Saving = Color(0xFF9C27B0)           // Purple 500
val SavingLight = Color(0xFFBA68C8)      // Purple 300
val SavingDark = Color(0xFF6A1B9A)       // Purple 800
val SavingContainer = Color(0xFFF3E5F5)  // Purple 50

// 성공 (초록)
val Success = Color(0xFF4CAF50)          // Green 500
val SuccessLight = Color(0xFF81C784)     // Green 300
val SuccessDark = Color(0xFF2E7D32)      // Green 800
val SuccessContainer = Color(0xFFE8F5E9) // Green 50

// 경고 (노랑)
val Warning = Color(0xFFFFC107)          // Amber 500
val WarningLight = Color(0xFFFFD54F)     // Amber 300
val WarningDark = Color(0xFFFFA000)      // Amber 700
val WarningContainer = Color(0xFFFFF8E1) // Amber 50

// 정보 (시안)
val Info = Color(0xFF00BCD4)             // Cyan 500
val InfoLight = Color(0xFF4DD0E1)        // Cyan 300
val InfoDark = Color(0xFF00838F)         // Cyan 800
val InfoContainer = Color(0xFFE0F7FA)    // Cyan 50

// ==================== Category Colors ====================
// 카테고리별 색상
val CategoryFood = Color(0xFFFF7043)     // Deep Orange 400 - 식비
val CategoryCafe = Color(0xFF8D6E63)     // Brown 400 - 카페
val CategoryTransport = Color(0xFF42A5F5) // Blue 400 - 교통
val CategoryShopping = Color(0xFFEC407A) // Pink 400 - 쇼핑
val CategorySubscription = Color(0xFF7E57C2) // Deep Purple 400 - 구독
val CategoryHealth = Color(0xFF66BB6A)   // Green 400 - 건강
val CategoryCulture = Color(0xFFFFCA28)  // Amber 400 - 문화
val CategoryEducation = Color(0xFF26C6DA) // Cyan 400 - 교육
val CategoryLiving = Color(0xFF78909C)   // Blue Grey 400 - 생활
val CategoryEtc = Color(0xFFBDBDBD)      // Grey 400 - 기타

// ==================== Chart Colors ====================
// 차트용 색상 팔레트
val ChartColors = listOf(
    Color(0xFF4CAF50),  // Green
    Color(0xFF2196F3),  // Blue
    Color(0xFFFF9800),  // Orange
    Color(0xFFF44336),  // Red
    Color(0xFF9C27B0),  // Purple
    Color(0xFF00BCD4),  // Cyan
    Color(0xFFFFEB3B),  // Yellow
    Color(0xFF795548),  // Brown
    Color(0xFF607D8B),  // Blue Grey
    Color(0xFFE91E63),  // Pink
)

// ==================== Gradient Colors ====================
// 그라데이션용 색상
val GradientGreen = listOf(Color(0xFF81C784), Color(0xFF4CAF50), Color(0xFF2E7D32))
val GradientBlue = listOf(Color(0xFF64B5F6), Color(0xFF2196F3), Color(0xFF1565C0))
val GradientOrange = listOf(Color(0xFFFFB74D), Color(0xFFFF9800), Color(0xFFF57C00))
val GradientRed = listOf(Color(0xFFE57373), Color(0xFFF44336), Color(0xFFC62828))
val GradientPurple = listOf(Color(0xFFBA68C8), Color(0xFF9C27B0), Color(0xFF6A1B9A))

// ==================== Text Colors ====================
val TextPrimary = Color(0xFF212121)      // Grey 900
val TextPrimaryDark = Color(0xFFFFFFFF)
val TextSecondary = Color(0xFF757575)    // Grey 600
val TextSecondaryDark = Color(0xFFB0B0B0)
val TextHint = Color(0xFFBDBDBD)         // Grey 400
val TextDisabled = Color(0xFF9E9E9E)     // Grey 500

// ==================== Divider ====================
val Divider = Color(0xFFE0E0E0)          // Grey 300
val DividerDark = Color(0xFF424242)      // Grey 800

// ==================== Grey Scale ====================
val Grey50 = Color(0xFFFAFAFA)
val Grey100 = Color(0xFFF5F5F5)
val Grey200 = Color(0xFFEEEEEE)
val Grey300 = Color(0xFFE0E0E0)
val Grey400 = Color(0xFFBDBDBD)
val Grey500 = Color(0xFF9E9E9E)
val Grey600 = Color(0xFF757575)
val Grey700 = Color(0xFF616161)
val Grey800 = Color(0xFF424242)
val Grey900 = Color(0xFF212121)

// ==================== Common Colors ====================
val White = Color(0xFFFFFFFF)
val Black = Color(0xFF000000)
val Transparent = Color(0x00000000)

// ==================== Legacy Colors (기존 호환) ====================
val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)
val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)
