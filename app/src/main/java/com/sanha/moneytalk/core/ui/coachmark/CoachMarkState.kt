package com.sanha.moneytalk.core.ui.coachmark

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 코치마크 오버레이 상태 홀더.
 * 현재 스텝, 표시 여부, 네비게이션 로직을 관리한다.
 */
@Stable
class CoachMarkState(
    initialSteps: List<CoachMarkStep> = emptyList()
) {
    /** 표시할 스텝 목록. [show] 호출 시 동적으로 교체 가능 */
    var steps by mutableStateOf(initialSteps)
        private set

    var currentStepIndex by mutableIntStateOf(0)
        private set

    var isVisible by mutableStateOf(false)
        private set

    /** 현재 스텝 (없으면 null) */
    val currentStep: CoachMarkStep?
        get() = steps.getOrNull(currentStepIndex)

    val totalSteps: Int get() = steps.size

    val isLastStep: Boolean get() = currentStepIndex >= steps.size - 1

    /**
     * 오버레이 표시 (첫 스텝부터).
     * @param newSteps 전달 시 기존 스텝 목록을 교체 (미등록 타겟 필터링 용도)
     */
    fun show(newSteps: List<CoachMarkStep>? = null) {
        if (newSteps != null) steps = newSteps
        currentStepIndex = 0
        isVisible = true
    }

    /** 다음 스텝으로 이동. 마지막이면 닫기 */
    fun next() {
        if (!isLastStep) {
            currentStepIndex++
        } else {
            dismiss()
        }
    }

    /** 오버레이 닫기 */
    fun dismiss() {
        isVisible = false
    }
}
