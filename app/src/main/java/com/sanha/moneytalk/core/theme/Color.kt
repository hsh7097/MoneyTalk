package com.sanha.moneytalk.core.theme

import androidx.compose.ui.graphics.Color

// MoneyTalk의 중립 표면과 녹색 액션 팔레트. 역할 매핑은 Theme.kt에서 관리한다.
// 기존 Navy 이름은 호출부 호환을 위해 유지하며, 중립 텍스트 역할이다.
val NavyDark = Color(0xFF202731)
val NavyMedium = Color(0xFF404B58)
val NavyLight = Color(0xFF606B78)
val NavyTint = Color(0xFFF0F2F4)

val Primary = Color(0xFF087F5B)
val PrimaryLight = Color(0xFF15926A)
val PrimaryDark = Color(0xFF07543F)
val PrimaryContainer = Color(0xFFE7F4EE)
val OnPrimary = Color.White
val OnPrimaryContainer = Color(0xFF086548)

// 보조 액션도 동일한 체계를 사용한다. 소득/요일은 아래 의미 색상으로 구분한다.
val Secondary = Primary
val SecondaryLight = Color(0xFF6DDBB0)
val SecondaryDark = Color(0xFF183C30)
val SecondaryContainer = PrimaryContainer
val OnSecondary = Color.White
val OnSecondaryContainer = OnPrimaryContainer

val Tertiary = NavyMedium
val TertiaryLight = Color(0xFFB1BBC5)
val TertiaryDark = Color(0xFF303841)
val TertiaryContainer = NavyTint
val OnTertiary = Color.White
val OnTertiaryContainer = NavyDark

val Error = Color(0xFFCA3C49)
val ErrorLight = Color(0xFFFF929B)
val ErrorDark = Color(0xFF49272C)
val ErrorContainer = Color(0xFFFCECEE)
val OnError = Color.White
val OnErrorContainer = Color(0xFF9B2835)

val Background = Color(0xFFF4F5F7)
val BackgroundDark = Color(0xFF14171B)
val Surface = Color.White
val SurfaceDark = Color(0xFF20252B)
val SurfaceVariant = Color(0xFFF0F2F4)
val SurfaceVariantDark = Color(0xFF2B323A)
val OnBackground = Color(0xFF202731)
val OnBackgroundDark = Color(0xFFF0F3F6)
val OnSurface = OnBackground
val OnSurfaceDark = OnBackgroundDark
val OnSurfaceVariant = Color(0xFF606B78)

val Gray900 = OnSurface
val Gray600 = OnSurfaceVariant
val Gray400 = Color(0xFF65717E)
val Gray200 = Color(0xFFE4E8EC)
val Gray100 = SurfaceVariant
val Gray50 = Background

val Outline = Color(0xFF9AA5B1)
val OutlineDark = Color(0xFF697582)
val OutlineVariant = Gray200

// 금액은 수입=빨강, 지출=파랑으로 통일하고 부호를 함께 표시한다.
val IncomeLight = Color(0xFFC6283D)
val IncomeDark = Color(0xFFFF929B)
val ExpenseLight = Color(0xFF1D5FC4)
val ExpenseDark = Color(0xFF7BB3FF)

val CalendarSunday = Error
val CalendarSaturday = Color(0xFF386AA6)

val DarkPrimary = SecondaryLight
val DarkPrimaryContainer = SecondaryDark
val DarkOnPrimaryContainer = Color(0xFFB8EFD7)
val DarkTertiary = TertiaryLight
val DarkTertiaryContainer = TertiaryDark
val DarkOnTertiaryContainer = Color(0xFFE0E7ED)
val DarkGrey400 = Color(0xFFB1BBC5)
val DarkGrey700 = Color(0xFF373F49)
