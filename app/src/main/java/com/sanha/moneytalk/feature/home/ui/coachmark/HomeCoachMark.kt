package com.sanha.moneytalk.feature.home.ui.coachmark

import com.sanha.moneytalk.R
import com.sanha.moneytalk.core.ui.coachmark.CoachMarkStep
import com.sanha.moneytalk.core.ui.coachmark.TooltipPosition

/**
 * 홈 화면 코치마크 스텝 정의.
 *
 * show() 시점에 [CoachMarkTargetRegistry]에 등록된 타겟만 필터링하여
 * 실제 화면에 보이는 요소만 안내한다.
 *
 * targetKey는 HomePageContent에서 Modifier.onboardingTarget(key, registry)로 등록.
 */
fun homeCoachMarkSteps(): List<CoachMarkStep> = listOf(
    // Step 1: 월간 현황 히어로 카드 (항상 표시)
    CoachMarkStep(
        id = "home_overview",
        titleResId = R.string.coach_mark_home_sync_alt_title,
        descriptionResId = R.string.coach_mark_home_sync_alt_desc,
        targetKey = "home_overview",
        tooltipPosition = TooltipPosition.BELOW
    ),
    // Step 2: 소비 추이 차트 (데이터 있을 때만 표시)
    // LazyColumn 하단이라 화면 높이에 따라 compose 시점에 미등록될 수 있음 → 자동 스킵
    CoachMarkStep(
        id = "home_trend",
        titleResId = R.string.coach_mark_home_trend_title,
        descriptionResId = R.string.coach_mark_home_trend_desc,
        targetKey = "home_trend",
        tooltipPosition = TooltipPosition.BELOW
    )
)
