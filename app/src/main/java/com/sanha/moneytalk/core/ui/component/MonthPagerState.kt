package com.sanha.moneytalk.core.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.sanha.moneytalk.core.util.DateUtils

/** Pager 측정 중 시계를 다시 읽지 않도록 페이지 수를 Compose가 관찰하는 월에서 계산한다. */
@Composable
internal fun rememberMonthPagerPageCount(
    monthStartDay: Int,
    currentEffectiveMonth: (Int) -> Pair<Int, Int> = DateUtils::getEffectiveCurrentMonth
): Int {
    val readCurrentMonth by rememberUpdatedState(currentEffectiveMonth)
    val monthAtComposition = currentEffectiveMonth(monthStartDay)
    var effectiveMonth by remember(monthStartDay, monthAtComposition) {
        mutableStateOf(monthAtComposition)
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        effectiveMonth = readCurrentMonth(monthStartDay)
    }
    return MonthPagerUtils.yearMonthToPage(effectiveMonth.first, effectiveMonth.second) + 1
}
