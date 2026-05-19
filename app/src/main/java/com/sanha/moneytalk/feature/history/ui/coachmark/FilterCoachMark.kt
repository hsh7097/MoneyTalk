package com.sanha.moneytalk.feature.history.ui.coachmark

import com.sanha.moneytalk.R
import com.sanha.moneytalk.core.ui.coachmark.CoachMarkStep
import com.sanha.moneytalk.core.ui.coachmark.TooltipPosition

/**
 * 필터 BottomSheet 코치마크 스텝 정의.
 *
 * BottomSheet 내부에서 show() 시점에 등록된 타겟만 필터링하여 표시.
 */
fun filterCoachMarkSteps(): List<CoachMarkStep> = listOf(
    // Step 1: 정렬 + 고정 거래 필터
    CoachMarkStep(
        id = "filter_sort",
        titleResId = R.string.coach_mark_filter_sort_title,
        descriptionResId = R.string.coach_mark_filter_sort_desc,
        targetKey = "filter_sort",
        tooltipPosition = TooltipPosition.BELOW
    ),
    // Step 2: 거래 유형 선택
    CoachMarkStep(
        id = "filter_type",
        titleResId = R.string.coach_mark_filter_type_title,
        descriptionResId = R.string.coach_mark_filter_type_desc,
        targetKey = "filter_type",
        tooltipPosition = TooltipPosition.BELOW
    ),
    // Step 3: 유형별 카테고리 선택
    CoachMarkStep(
        id = "filter_category",
        titleResId = R.string.coach_mark_filter_category_title,
        descriptionResId = R.string.coach_mark_filter_category_desc,
        targetKey = "filter_category",
        tooltipPosition = TooltipPosition.BELOW
    )
)
