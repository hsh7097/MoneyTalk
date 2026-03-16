package com.sanha.moneytalk.feature.history.ui.coachmark

import com.sanha.moneytalk.R
import com.sanha.moneytalk.core.ui.coachmark.CoachMarkStep
import com.sanha.moneytalk.core.ui.coachmark.TooltipPosition

/**
 * 내역 화면 코치마크 스텝 정의.
 *
 * show() 시점에 등록된 타겟만 필터링하여 표시.
 */
fun historyCoachMarkSteps(): List<CoachMarkStep> = listOf(
    // Step 1: 기간 선택 + 지출/수입 요약
    CoachMarkStep(
        id = "history_period",
        titleResId = R.string.coach_mark_history_period_title,
        descriptionResId = R.string.coach_mark_history_period_desc,
        targetKey = "history_period",
        tooltipPosition = TooltipPosition.BELOW
    ),
    // Step 2: 목록/달력 뷰 전환 + 검색/필터/추가 아이콘
    // FilterTabRow 전체를 하이라이트하므로 필터·검색·추가 아이콘도 시각적으로 포함됨
    CoachMarkStep(
        id = "history_view_mode",
        titleResId = R.string.coach_mark_history_view_mode_title,
        descriptionResId = R.string.coach_mark_history_view_mode_desc,
        targetKey = "history_view_mode",
        tooltipPosition = TooltipPosition.BELOW
    )
)
