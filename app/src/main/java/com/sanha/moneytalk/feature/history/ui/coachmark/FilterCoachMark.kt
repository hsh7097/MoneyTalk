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
    // Step 1: 정렬 + 고정지출 필터 (통합)
    CoachMarkStep(
        id = "filter_sort",
        titleResId = R.string.coach_mark_filter_sort_title,
        descriptionResId = R.string.coach_mark_filter_sort_desc,
        targetKey = "filter_sort",
        tooltipPosition = TooltipPosition.BELOW
    )
)
