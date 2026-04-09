package com.sanha.moneytalk.feature.settings.ui.coachmark

import com.sanha.moneytalk.R
import com.sanha.moneytalk.core.ui.coachmark.CoachMarkStep
import com.sanha.moneytalk.core.ui.coachmark.TooltipPosition

/**
 * 설정 화면 코치마크 스텝 정의.
 *
 * show() 시점에 등록된 타겟만 필터링하여 표시.
 */
fun settingsCoachMarkSteps(): List<CoachMarkStep> = listOf(
    // Step 1: 기간/예산 설정
    CoachMarkStep(
        id = "settings_period",
        titleResId = R.string.coach_mark_settings_period_title,
        descriptionResId = R.string.coach_mark_settings_period_desc,
        targetKey = "settings_period",
        tooltipPosition = TooltipPosition.BELOW
    ),
    // Step 2: 카테고리/분류 관리
    CoachMarkStep(
        id = "settings_category",
        titleResId = R.string.coach_mark_settings_category_title,
        descriptionResId = R.string.coach_mark_settings_category_desc,
        targetKey = "settings_category",
        tooltipPosition = TooltipPosition.BELOW
    ),
    // Step 3: 데이터 관리
    // 오프스크린일 수 있어도 SettingsScreen에서 해당 섹션까지 자동 스크롤해 등록을 보장한다.
    CoachMarkStep(
        id = "settings_data",
        titleResId = R.string.coach_mark_settings_data_title,
        descriptionResId = R.string.coach_mark_settings_data_desc,
        targetKey = "settings_data",
        tooltipPosition = TooltipPosition.ABOVE
    )
    // settings_reset: 가이드 초기화는 메타 기능이므로 온보딩 스텝에서 제외
)
