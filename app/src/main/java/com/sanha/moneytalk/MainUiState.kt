package com.sanha.moneytalk

import androidx.compose.runtime.Stable

/**
 * Activity-scoped 메인 UI 상태
 *
 * SMS 동기화, 권한, 광고 등 앱 전역 상태를 관리합니다.
 * HomeScreen/HistoryScreen에서 공통으로 참조합니다.
 *
 * @property isSyncing SMS 동기화 진행 중 여부
 * @property hasSmsPermission SMS 읽기 권한 보유 여부
 * @property monthStartDay 월 시작일 (1~28, 사용자 설정) — 동기화 범위 계산에 사용
 */
@Stable
data class MainUiState(
    val isSyncing: Boolean = false,
    val hasSmsPermission: Boolean = false,
    val monthStartDay: Int = 1,
    // SMS 동기화 다이얼로그
    val showSyncDialog: Boolean = false,
    val syncProgress: String = "",
    val syncProgressCurrent: Int = 0,
    val syncProgressTotal: Int = 0,
    /** 현재 동기화 단계 인덱스 (Stepper UI용, 0~4) */
    val syncStepIndex: Int = 0,
    /** 동기화 다이얼로그가 사용자에 의해 dismiss되었는지 여부 */
    val syncDialogDismissed: Boolean = false,
    // AI 성과 요약 카드 (초기 동기화 완료 후)
    val showEngineSummary: Boolean = false,
    val engineSummaryTotalSms: Int = 0,
    val engineSummaryPatterns: Int = 0,
    val engineSummaryExpenses: Int = 0,
    val engineSummaryIncomes: Int = 0,
    // 월별 동기화 해제 (리워드 광고)
    val syncedMonths: Set<String> = emptySet(),
    val isLegacyFullSyncUnlocked: Boolean = false,
    val showFullSyncAdDialog: Boolean = false,
    /** 광고 다이얼로그 대상 연도 */
    val fullSyncAdYear: Int = 0,
    /** 광고 다이얼로그 대상 월 */
    val fullSyncAdMonth: Int = 0
)
