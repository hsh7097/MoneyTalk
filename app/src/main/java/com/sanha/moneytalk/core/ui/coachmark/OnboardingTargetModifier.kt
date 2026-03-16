package com.sanha.moneytalk.core.ui.coachmark

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned

/**
 * 코치마크 타겟 요소의 좌표를 저장하는 레지스트리.
 * 화면 단위로 `remember { CoachMarkTargetRegistry() }`로 생성하여
 * Modifier와 CoachMarkOverlay에 동일 인스턴스를 전달한다.
 */
@Stable
class CoachMarkTargetRegistry {
    private val _targets = mutableStateMapOf<String, Rect>()

    /** 등록된 타겟 좌표 맵 (key → Rect) */
    val targets: Map<String, Rect> get() = _targets

    /** 타겟 좌표 등록/갱신 */
    fun register(key: String, bounds: Rect) {
        _targets[key] = bounds
    }

    /** 타겟 좌표 제거 (Composable dispose 시 호출) */
    fun unregister(key: String) {
        _targets.remove(key)
    }
}

/**
 * 코치마크 스포트라이트 대상으로 등록하는 Modifier.
 * 이 Modifier가 붙은 Composable의 위치가 [CoachMarkTargetRegistry]에 기록된다.
 * Composable이 dispose되면 자동으로 레지스트리에서 제거된다.
 *
 * ```kotlin
 * val registry = remember { CoachMarkTargetRegistry() }
 *
 * MonthlyOverviewSection(
 *     modifier = Modifier.onboardingTarget("home_overview", registry)
 * )
 *
 * CoachMarkOverlay(state = coachMarkState, targetRegistry = registry, ...)
 * ```
 */
@Composable
fun Modifier.onboardingTarget(key: String, registry: CoachMarkTargetRegistry): Modifier {
    DisposableEffect(key) {
        onDispose { registry.unregister(key) }
    }
    return this.onGloballyPositioned { coordinates ->
        registry.register(key, coordinates.boundsInRoot())
    }
}
