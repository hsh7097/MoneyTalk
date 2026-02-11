package com.sanha.moneytalk.core.theme

import androidx.compose.ui.graphics.Color

// ============================================================================
// MoneyTalk 색상 팔레트
// ============================================================================
// 모든 색상은 이 파일에서 정의하고, Theme.kt에서 역할을 매핑한다.
// 직접 참조는 최소화하고, MaterialTheme.colorScheme 또는
// MaterialTheme.moneyTalkColors를 통해 접근하는 것을 권장한다.
//
// 디자인 기준: Stitch 디자인 시안 (2026-02-12)
// ============================================================================

// ==================== Primary (Green 계열) ====================
// 앱 메인 브랜드 색상 — 돈/재무 테마
// Light 테마: Primary(800) 메인, Dark 테마: PrimaryLight(500) 메인
val Primary = Color(0xFF2E7D32)              // Green 800 — Light 테마 primary
val PrimaryLight = Color(0xFF4CAF50)         // Green 500 — Dark 테마 primary
val PrimaryDark = Color(0xFF1B5E20)          // Green 900 — Dark 테마 primaryContainer
val PrimaryContainer = Color(0xFFC8E6C9)     // Green 100 — Light 테마 primaryContainer
val OnPrimary = Color(0xFFFFFFFF)            // Primary 위의 텍스트/아이콘 (Light/Dark 공용)
val OnPrimaryContainer = Color(0xFF1B5E20)   // Green 900 — Light 테마 onPrimaryContainer

// ==================== Secondary (Blue 계열) ====================
// 보조 색상 — 정보 표시, 링크
val Secondary = Color(0xFF1976D2)            // Blue 700 — Light 테마 secondary
val SecondaryLight = Color(0xFF42A5F5)       // Blue 400 — Dark 테마 secondary
val SecondaryDark = Color(0xFF0D47A1)        // Blue 900 — Dark 테마 secondaryContainer
val SecondaryContainer = Color(0xFFBBDEFB)   // Blue 100 — Light 테마 secondaryContainer
val OnSecondary = Color(0xFFFFFFFF)          // Secondary 위의 텍스트/아이콘
val OnSecondaryContainer = Color(0xFF0D47A1) // Blue 900 — Light 테마 onSecondaryContainer

// ==================== Tertiary (Orange 계열) ====================
// 강조 색상 — 특별 알림, 배지
val Tertiary = Color(0xFFFF9800)             // Orange 500 — Light 테마 tertiary
val TertiaryLight = Color(0xFFFFB74D)        // Orange 300 — Dark 테마 tertiary
val TertiaryDark = Color(0xFFF57C00)         // Orange 700 — Dark 테마 tertiaryContainer
val TertiaryContainer = Color(0xFFFFE0B2)    // Orange 100 — Light 테마 tertiaryContainer
val OnTertiary = Color(0xFFFFFFFF)           // Tertiary 위의 텍스트/아이콘
val OnTertiaryContainer = Color(0xFFE65100)  // Orange 900 — Light 테마 onTertiaryContainer

// ==================== Error (Red 계열) ====================
// 오류/경고 — 지출 금액 표시에도 사용
val Error = Color(0xFFEF4444)                // 디자인 지출 빨강 — Light 테마 error (SVG 기준)
val ErrorLight = Color(0xFFEF4444)           // 디자인 지출 빨강 — Dark 테마 error (라이트/다크 동일)
val ErrorDark = Color(0xFFB71C1C)            // Red 900 — Dark 테마 errorContainer
val ErrorContainer = Color(0xFFFFCDD2)       // Red 100 — Light 테마 errorContainer
val OnError = Color(0xFFFFFFFF)              // Error 위의 텍스트/아이콘
val OnErrorContainer = Color(0xFFB71C1C)     // Red 900 — Light 테마 onErrorContainer

// ==================== Background & Surface ====================
// 화면 배경 및 카드/시트 표면 — 디자인 시안 기준
val Background = Color(0xFFF6F7F8)           // Light 테마 전체 배경 (SVG 기준)
val BackgroundDark = Color(0xFF171A1E)       // Dark 테마 전체 배경 (진한 다크)
val Surface = Color(0xFFFFFFFF)              // Light 테마 카드 배경 (흰색)
val SurfaceDark = Color(0xFF252A30)          // Dark 테마 카드 배경 (어두운 회색)
val SurfaceVariant = Color(0xFFE8EAED)       // Light 테마 surfaceVariant (탭/필터 배경)
val SurfaceVariantDark = Color(0xFF2D3239)   // Dark 테마 surfaceVariant
val OnBackground = Color(0xFF111827)         // Light 테마 메인 텍스트 (SVG 기준)
val OnBackgroundDark = Color(0xFFECECEC)     // Dark 테마 메인 텍스트 (밝은 회색)
val OnSurface = Color(0xFF111827)            // Light 테마 카드 위 텍스트 (= 가게명, SVG 기준)
val OnSurfaceDark = Color(0xFFECECEC)        // Dark 테마 카드 위 텍스트 (= 가게명)
val OnSurfaceVariant = Color(0xFF6B7280)     // Light 테마 보조 텍스트 (= 카테고리/시간, SVG 기준)

// ==================== Outline ====================
// 테두리, 구분선
val Outline = Color(0xFFBDBDBD)              // Light 테마 outline
val OutlineDark = Color(0xFF3A3F47)          // Dark 테마 outline
val OutlineVariant = Color(0xFFF3F4F6)       // Light 테마 outlineVariant (카드 border, SVG 기준)

// ==================== Semantic: Income / Expense ====================
// 수입 색상 — 라이트(파랑) vs 다크(초록)으로 테마별 다르게 매핑
val IncomeLight = Color(0xFF137FEC)          // Light 테마 수입 (파란색, SVG 기준)
val IncomeDark = Color(0xFF3AC977)           // Dark 테마 수입 (초록색)

// 지출 색상 — 라이트/다크 동일
val ExpenseColor = Color(0xFFEF4444)         // 지출 빨강 (SVG 기준, 라이트/다크 공용)

// ==================== Calendar ====================
// 달력 전용 색상 — 요일 표시
val CalendarSunday = Color(0xFFEF4444)       // 일요일 (빨간 계열, 지출과 동일, SVG 기준)
val CalendarSaturday = Color(0xFF137FEC)     // 토요일 (파란 계열, 수입과 동일, SVG 기준)

// ==================== Dark 테마 전용 Grey ====================
// Theme.kt에서 DarkColorScheme 매핑에 사용
val Grey400 = Color(0xFF6B7684)              // Dark 테마 onSurfaceVariant (= 추가정보)
val Grey700 = Color(0xFF3A3F47)              // Dark 테마 outlineVariant
