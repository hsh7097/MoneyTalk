package com.sanha.moneytalk.feature.history.ui

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Constraints
import kotlin.math.roundToInt

/** 월별 목록/달력의 스크롤을 유지하면서 요약만 접고 보기·필터 도구는 남긴다. */
@Composable
internal fun HistoryScrollLayout(
    resetKey: Any?,
    scrollEnabled: Boolean,
    header: @Composable () -> Unit,
    controls: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (restoreHeader: () -> Unit) -> Unit
) {
    var headerHeight by remember { mutableIntStateOf(0) }
    var collapsedHeight by remember { mutableFloatStateOf(0f) }
    val canScroll by rememberUpdatedState(scrollEnabled)
    val connection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (!canScroll || available.y >= 0f) return Offset.Zero
                val previous = collapsedHeight
                collapsedHeight = (previous - available.y).coerceIn(0f, headerHeight.toFloat())
                return Offset(0f, previous - collapsedHeight)
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (!canScroll || available.y <= 0f) return Offset.Zero
                val previous = collapsedHeight
                collapsedHeight = (previous - available.y).coerceAtLeast(0f)
                return Offset(0f, previous - collapsedHeight)
            }
        }
    }
    LaunchedEffect(resetKey, scrollEnabled) { collapsedHeight = 0f }

    Column(modifier = modifier.fillMaxSize().nestedScroll(connection)) {
        Layout(
            modifier = Modifier.fillMaxWidth().clipToBounds().testTag("history_collapsing_header")
                .scrollable(rememberScrollableState { 0f }, Orientation.Vertical, enabled = scrollEnabled),
            content = {
                Box(Modifier.fillMaxWidth().onSizeChanged {
                    headerHeight = it.height
                    collapsedHeight = collapsedHeight.coerceAtMost(it.height.toFloat())
                }) { header() }
            }
        ) { measurables, constraints ->
            val placeable = measurables.single().measure(
                constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity)
            )
            val offset = if (scrollEnabled) collapsedHeight.roundToInt().coerceAtMost(placeable.height) else 0
            layout(placeable.width, (placeable.height - offset).coerceAtLeast(0)) {
                placeable.placeRelative(0, -offset)
            }
        }
        Box(Modifier.fillMaxWidth().testTag("history_pinned_controls")) { controls() }
        // 빈 목록/로딩 화면에서도 아래로 당겨 월 이동과 검색·추가를 다시 꺼낼 수 있다.
        Box(
            Modifier.weight(1f).fillMaxWidth()
                .scrollable(rememberScrollableState { 0f }, Orientation.Vertical, enabled = scrollEnabled)
        ) { content { collapsedHeight = 0f } }
    }
}
