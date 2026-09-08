package com.sanha.moneytalk.core.ui.component.chart

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import java.time.LocalDate
import kotlin.math.roundToInt

@Immutable
data class CumulativeInspectionValue(val label: String, val amount: Long, val color: Color, val isPrimary: Boolean)

@Immutable
data class CumulativeChartInspection(
    val dayIndex: Int,
    val date: LocalDate,
    val values: List<CumulativeInspectionValue>,
    val primaryUnavailable: Boolean,
    val primaryLimitedToToday: Boolean,
    val primaryLabel: String
)

/** 0번은 시작 전 기준점이다. 실제 날짜는 1번부터이며 미래/없는 포인트는 표시하지 않는다. */
internal fun cumulativeChartInspection(
    dayIndex: Int?,
    periodStart: LocalDate,
    primaryLine: CumulativeChartLine,
    visibleComparisons: List<CumulativeChartLine>,
    daysInMonth: Int,
    todayDayIndex: Int
): CumulativeChartInspection? {
    val lastIndex = lastInspectableDay(primaryLine, visibleComparisons, daysInMonth, todayDayIndex)
    if (dayIndex == null || dayIndex !in 1..lastIndex) return null
    val primaryAvailable = dayIndex <= primaryLine.points.lastIndex &&
        (todayDayIndex < 0 || dayIndex <= todayDayIndex)
    val values = buildList {
        if (primaryAvailable) {
            add(CumulativeInspectionValue(primaryLine.label, primaryLine.points[dayIndex], primaryLine.color, true))
        }
        visibleComparisons.forEach { line ->
            line.points.getOrNull(dayIndex)?.let { add(CumulativeInspectionValue(line.label, it, line.color, false)) }
        }
    }
    if (values.isEmpty()) return null
    return CumulativeChartInspection(
        dayIndex = dayIndex,
        date = periodStart.plusDays(dayIndex.toLong() - 1),
        values = values,
        primaryUnavailable = !primaryAvailable,
        primaryLimitedToToday = todayDayIndex >= 0 && dayIndex > todayDayIndex,
        primaryLabel = primaryLine.label
    )
}

/** 선택 범위는 보이는 선의 실제 길이. 이번 달의 미래 포인트만 제외한다. */
internal fun lastInspectableDay(
    primaryLine: CumulativeChartLine,
    visibleComparisons: List<CumulativeChartLine>,
    daysInMonth: Int,
    todayDayIndex: Int
): Int {
    val primaryLast = minOf(primaryLine.points.lastIndex, if (todayDayIndex >= 0) todayDayIndex else daysInMonth)
    val comparisonLast = visibleComparisons.maxOfOrNull { it.points.lastIndex } ?: 0
    return minOf(daysInMonth, maxOf(primaryLast, comparisonLast)).coerceAtLeast(0)
}

/** Vico가 측정한 실제 plot 좌표. draw가 갱신하고 입력이 읽으며 Compose state를 쓰지 않는다. */
internal class CumulativeInspectionGeometry {
    var left = 0f
    var right = 0f
    var top = 0f
    var bottom = 0f
    var originX = 0f
    var dayWidth = 0f

    fun dayAt(x: Float, y: Float, lastIndex: Int): Int? {
        if (right <= left || bottom <= top || dayWidth == 0f || x !in left..right || y !in top..bottom) return null
        val day = ((x - originX) / dayWidth).roundToInt()
        return day.takeIf { it in 1..lastIndex }
    }
}
