package com.sanha.moneytalk.feature.chat.ui.coachmark

import com.sanha.moneytalk.R
import com.sanha.moneytalk.core.ui.coachmark.CoachMarkStep
import com.sanha.moneytalk.core.ui.coachmark.TooltipPosition

/**
 * 채팅 화면 코치마크 스텝 정의.
 *
 * show() 시점에 등록된 타겟만 필터링하여 표시.
 */
fun chatCoachMarkSteps(): List<CoachMarkStep> = listOf(
    // Step 1: 추천 질문으로 대화 시작
    CoachMarkStep(
        id = "chat_start",
        titleResId = R.string.coach_mark_chat_start_title,
        descriptionResId = R.string.coach_mark_chat_start_desc,
        targetKey = "chat_start",
        tooltipPosition = TooltipPosition.BELOW
    ),
    // Step 2: 대화 관리 (세션 목록)
    CoachMarkStep(
        id = "chat_sessions",
        titleResId = R.string.coach_mark_chat_sessions_title,
        descriptionResId = R.string.coach_mark_chat_sessions_desc,
        targetKey = "chat_sessions",
        tooltipPosition = TooltipPosition.BELOW
    )
)
