package com.sanha.moneytalk.feature.home.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeComparisonPeriodTest {

    private val dayMillis = 24L * 60 * 60 * 1000

    @Test
    fun firstElapsedDayIncludesWholeFirstDay() {
        val start = 1_000L

        assertEquals(
            start + dayMillis - 1,
            HomeComparisonPeriod.endOfElapsedDay(
                periodStart = start,
                periodEnd = start + (30 * dayMillis) - 1,
                elapsedDays = 0
            )
        )
    }

    @Test
    fun twelfthElapsedDayIncludesWholeTwelfthDay() {
        val start = 1_000L

        assertEquals(
            start + (12 * dayMillis) - 1,
            HomeComparisonPeriod.endOfElapsedDay(
                periodStart = start,
                periodEnd = start + (30 * dayMillis) - 1,
                elapsedDays = 11
            )
        )
    }

    @Test
    fun endTimestampDoesNotExceedPeriodEnd() {
        val start = 1_000L
        val end = start + (28 * dayMillis) - 1

        assertEquals(
            end,
            HomeComparisonPeriod.endOfElapsedDay(
                periodStart = start,
                periodEnd = end,
                elapsedDays = 30
            )
        )
    }
}
