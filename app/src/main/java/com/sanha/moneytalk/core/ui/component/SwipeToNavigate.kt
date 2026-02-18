package com.sanha.moneytalk.core.ui.component

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

/**
 * 좌우 스와이프로 월 이동하는 Modifier 확장 함수.
 *
 * 최소 드래그 거리(threshold)를 초과해야 콜백이 호출되며,
 * [detectHorizontalDragGestures]는 수평 드래그만 소비하므로
 * 세로 스크롤(LazyColumn)과 충돌하지 않는다.
 *
 * @param onSwipeLeft  왼쪽으로 스와이프 (다음 월)
 * @param onSwipeRight 오른쪽으로 스와이프 (이전 월)
 */
fun Modifier.swipeToNavigateMonth(
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit
): Modifier = this.then(
    Modifier.pointerInput(onSwipeLeft, onSwipeRight) {
        val threshold = 100f
        var totalDragDistance = 0f

        detectHorizontalDragGestures(
            onDragStart = {
                totalDragDistance = 0f
            },
            onDragEnd = {
                if (totalDragDistance > threshold) {
                    onSwipeRight()
                } else if (totalDragDistance < -threshold) {
                    onSwipeLeft()
                }
            },
            onDragCancel = {
                totalDragDistance = 0f
            },
            onHorizontalDrag = { _, dragAmount ->
                totalDragDistance += dragAmount
            }
        )
    }
)
