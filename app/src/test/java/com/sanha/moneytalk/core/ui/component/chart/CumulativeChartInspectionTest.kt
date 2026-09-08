package com.sanha.moneytalk.core.ui.component.chart

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class CumulativeChartInspectionTest {
    private val start = LocalDate.parse("2026-08-19")
    private val primary = line("current", 31) { it * 1_000L }

    @Test fun customPeriodUsesActualDateAndKeepsTheElapsedDay() {
        val result = requireNotNull(cumulativeChartInspection(15, start, primary, emptyList(), 31, 20))
        assertEquals(LocalDate.parse("2026-09-02"), result.date)
        assertEquals(15, result.dayIndex)
        assertEquals(15_000L, result.values.single().amount)
    }

    @Test fun originFutureAndMissingCurrentPointsCannotBeInspected() {
        listOf(null, 0, -1, 10, 31, 32).forEach { index ->
            assertNull(cumulativeChartInspection(index, start, primary, emptyList(), 31, 9))
        }
        assertEquals(9_000L, cumulativeChartInspection(9, start, primary, emptyList(), 31, 9)?.values?.single()?.amount)
        assertNull(cumulativeChartInspection(8, start, line("short", 7) { 0 }, emptyList(), 31, -1))
    }

    @Test fun shorterPreviousMonthHasNoInventedTailValue() {
        val previous = line("previous", 28) { it * 2_000L }
        val day28 = requireNotNull(cumulativeChartInspection(28, start, primary, listOf(previous), 31, -1))
        val day29 = requireNotNull(cumulativeChartInspection(29, start, primary, listOf(previous), 31, -1))
        assertEquals(listOf(28_000L, 56_000L), day28.values.map { it.amount })
        assertEquals(listOf("current"), day29.values.map { it.label })
    }

    @Test fun previousMonthRemainsInspectableBeyondTodayWithoutInventingCurrentSpending() {
        val previous = line("previous", 28) { it * 2_000L }
        val result = requireNotNull(cumulativeChartInspection(15, start, primary, listOf(previous), 31, 9))
        assertEquals(listOf("previous"), result.values.map { it.label })
        assertEquals(listOf(30_000L), result.values.map { it.amount })
        assertEquals(true, result.primaryUnavailable)
        assertEquals(true, result.primaryLimitedToToday)
        assertEquals(false, result.values.single().isPrimary)
        assertEquals(28, lastInspectableDay(primary, listOf(previous), 31, 9))
        assertNull(cumulativeChartInspection(15, start, primary, emptyList(), 31, 9))
    }

    @Test fun originalLongAndActualZeroArePreservedWithoutAnimatedOrDisabledValues() {
        val original = line("current", 31) { if (it == 1) 0 else 9_007_199_254_740_993L }
        val enabledBudget = line("budget", 31) { it * 2_000L }
        val result = requireNotNull(cumulativeChartInspection(2, start, original, listOf(enabledBudget), 31, -1))
        assertEquals(9_007_199_254_740_993L, result.values.first().amount)
        assertEquals(listOf("current", "budget"), result.values.map { it.label })
        assertEquals(0L, cumulativeChartInspection(1, start, original, emptyList(), 31, -1)?.values?.single()?.amount)
    }

    @Test fun actualPlotPaddingAndRtlSelectTheSameDayAndRejectAxes() {
        val geometry = CumulativeInspectionGeometry().apply {
            left = 40f; right = 350f; top = 10f; bottom = 180f
            originX = 45f; dayWidth = 10f
        }
        assertEquals(5, geometry.dayAt(95f, 90f, 20))
        assertNull(geometry.dayAt(45f, 90f, 20))
        assertNull(geometry.dayAt(300f, 90f, 20))
        assertNull(geometry.dayAt(95f, 190f, 20))
        geometry.originX = 345f
        geometry.dayWidth = -10f
        assertEquals(5, geometry.dayAt(295f, 90f, 20))
    }

    private fun line(label: String, days: Int, amount: (Int) -> Long) = CumulativeChartLine(
        points = (0..days).map(amount), color = Color.Blue, label = label
    )
}
