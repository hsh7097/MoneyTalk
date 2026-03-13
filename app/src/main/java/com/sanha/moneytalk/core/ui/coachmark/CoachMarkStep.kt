package com.sanha.moneytalk.core.ui.coachmark

import androidx.annotation.StringRes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 코치마크 오버레이의 단일 스텝.
 *
 * @param id 고유 식별자 (예: "home_sync_cta")
 * @param titleResId 제목 string resource
 * @param descriptionResId 설명 string resource
 * @param targetKey [onboardingTarget] Modifier 키와 매칭
 * @param tooltipPosition 툴팁 위치 (ABOVE / BELOW)
 * @param spotlightPadding 스포트라이트 컷아웃 여백
 * @param spotlightCornerRadius 스포트라이트 모서리 둥글기
 */
data class CoachMarkStep(
    val id: String,
    @StringRes val titleResId: Int,
    @StringRes val descriptionResId: Int,
    val targetKey: String,
    val tooltipPosition: TooltipPosition = TooltipPosition.BELOW,
    val spotlightPadding: Dp = 8.dp,
    val spotlightCornerRadius: Dp = 12.dp
)

/** 툴팁 카드 위치 */
enum class TooltipPosition {
    /** 스포트라이트 위에 표시 */
    ABOVE,
    /** 스포트라이트 아래에 표시 */
    BELOW
}
