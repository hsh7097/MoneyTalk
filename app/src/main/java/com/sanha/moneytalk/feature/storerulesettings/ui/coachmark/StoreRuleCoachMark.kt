package com.sanha.moneytalk.feature.storerulesettings.ui.coachmark

import com.sanha.moneytalk.R
import com.sanha.moneytalk.core.ui.coachmark.CoachMarkStep
import com.sanha.moneytalk.core.ui.coachmark.TooltipPosition

/**
 * 거래처 규칙 설정 화면 코치마크 스텝 정의.
 *
 * show() 시점에 등록된 타겟만 필터링하여 표시.
 */
fun storeRuleCoachMarkSteps(): List<CoachMarkStep> = listOf(
    // Step 1: 규칙 추가 버튼
    CoachMarkStep(
        id = "store_rule_add",
        titleResId = R.string.coach_mark_store_rule_add_title,
        descriptionResId = R.string.coach_mark_store_rule_add_desc,
        targetKey = "store_rule_add",
        tooltipPosition = TooltipPosition.BELOW
    )
)
