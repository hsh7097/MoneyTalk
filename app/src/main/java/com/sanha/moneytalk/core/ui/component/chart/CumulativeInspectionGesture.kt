package com.sanha.moneytalk.core.ui.component.chart

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput

/** 짧은 탭은 선택만, 길게 누른 뒤의 이동만 소비하여 부모의 평상시 스크롤을 보존한다. */
internal fun Modifier.cumulativeInspectionGesture(
    key: Any?,
    onPosition: (Offset) -> Unit
): Modifier = pointerInput(key) {
    awaitEachGesture {
        val down = awaitFirstDown()
        val longPress = awaitLongPressOrCancellation(down.id)
        if (longPress != null) {
            onPosition(longPress.position)
            drag(longPress.id) { change ->
                onPosition(change.position)
                change.consume()
            }
            currentEvent.changes.filter { !it.pressed }.forEach { it.consume() }
        } else {
            val up = currentEvent.changes.firstOrNull { it.id == down.id }
            if (up != null && !up.pressed && !up.isConsumed &&
                (up.position - down.position).getDistance() <= viewConfiguration.touchSlop
            ) {
                onPosition(up.position)
                up.consume()
            }
        }
    }
}
