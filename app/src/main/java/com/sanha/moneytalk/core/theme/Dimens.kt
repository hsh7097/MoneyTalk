package com.sanha.moneytalk.core.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ============================================================================
// MoneyTalk Spacing & Radius 토큰
// ============================================================================
// 모든 여백/패딩/반지름 수치를 이 파일에서 단일 관리.
// 하드코딩된 dp 값 대신 이 토큰을 사용하여 일관된 레이아웃 유지.
//
// 기본 단위: 4dp (8dp 그리드 시스템)
// 디자인 기준: 2026-09-08 금융 UI 정리
// ============================================================================

object MoneyTalkDimens {

    // ==================== Spacing ====================

    /** 4dp — 인라인 간격 (아이콘-텍스트, 제목-부제) */
    val SpacingXs: Dp = 4.dp

    /** 8dp — 요소 내부 간격 */
    val SpacingSm: Dp = 8.dp

    /** 12dp — 카드 내부 패딩 (compact) */
    val SpacingMd: Dp = 12.dp

    /** 16dp — 카드 내부 패딩 (standard), 화면 좌우 패딩 */
    val SpacingLg: Dp = 16.dp

    /** 20dp — 섹션 간 간격 (LazyColumn item spacing) */
    val SpacingXl: Dp = 20.dp

    /** 24dp — 히어로 영역 상하 패딩 */
    val SpacingXxl: Dp = 24.dp

    // ==================== Card ====================

    /** 20dp — 요약/섹션 카드 내부 패딩 */
    val CardPadding: Dp = 20.dp

    /** 16dp — 카드 간 간격 */
    val CardGap: Dp = 16.dp

    /** 24dp — 요약/섹션 카드 corner radius */
    val CardRadius: Dp = 24.dp

    /** 0dp — 카드 elevation (중립 표면으로 구분) */
    val CardElevation: Dp = 0.dp

    /** 1dp — 카드 border width */
    val CardBorderWidth: Dp = 1.dp

    // ==================== Screen ====================

    /** 20dp — 화면 좌우 패딩 */
    val ScreenHorizontalPadding: Dp = 20.dp

    /** 16dp — 화면 상단 패딩 */
    val ScreenTopPadding: Dp = 16.dp

    // ==================== Chart ====================

    /** 8dp — 수평 바 차트 바 높이 */
    val ChartBarHeight: Dp = 8.dp

    /** 4dp — 차트 바 corner radius */
    val ChartBarRadius: Dp = 4.dp

    /** 12dp — 오늘 마커 크기 */
    val ChartTodayMarkerSize: Dp = 12.dp

    // ==================== Component ====================

    /** 8dp — 버튼 내부 수평 패딩 (compact) */
    val ButtonPaddingHorizontalSm: Dp = 8.dp

    /** 16dp — 버튼 내부 수평 패딩 (standard) */
    val ButtonPaddingHorizontal: Dp = 16.dp

    /** 24dp — 버튼 내부 수평 패딩 (large) */
    val ButtonPaddingHorizontalLg: Dp = 24.dp

    /** 14dp — 버튼 corner radius */
    val ButtonRadius: Dp = 14.dp

    /** 12dp — 칩 corner radius */
    val ChipRadius: Dp = 12.dp

    /** 48dp — 리스트 아이템 최소 높이 */
    val ListItemMinHeight: Dp = 48.dp

    /** 40dp — 카테고리 아이콘 크기 */
    val CategoryIconSize: Dp = 40.dp
}
