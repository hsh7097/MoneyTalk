package com.sanha.moneytalk.feature.transactionedit.ui.coachmark

import com.sanha.moneytalk.R
import com.sanha.moneytalk.core.ui.coachmark.CoachMarkStep
import com.sanha.moneytalk.core.ui.coachmark.TooltipPosition

/**
 * 거래 편집 화면 코치마크 스텝 정의.
 *
 * show() 시점에 등록된 타겟만 필터링하여 표시.
 * 신규 거래 추가 시에는 일괄적용 체크박스가 없으므로 자동 스킵.
 */
fun transactionEditCoachMarkSteps(): List<CoachMarkStep> = listOf(
    // Step 1: 카테고리 변경 + 일괄 적용
    CoachMarkStep(
        id = "edit_category",
        titleResId = R.string.coach_mark_edit_category_title,
        descriptionResId = R.string.coach_mark_edit_category_desc,
        targetKey = "edit_category",
        tooltipPosition = TooltipPosition.BELOW
    ),
    // Step 2: 고정 거래 토글
    CoachMarkStep(
        id = "edit_fixed",
        titleResId = R.string.coach_mark_edit_fixed_title,
        descriptionResId = R.string.coach_mark_edit_fixed_desc,
        targetKey = "edit_fixed",
        tooltipPosition = TooltipPosition.ABOVE
    )
)
