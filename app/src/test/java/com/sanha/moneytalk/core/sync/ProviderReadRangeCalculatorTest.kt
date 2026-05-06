package com.sanha.moneytalk.core.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class ProviderReadRangeCalculatorTest {

    @Test
    fun lastSuccessfulScanTime_isUsedEvenWhenItIsOlderThan24Hours() {
        val endTime = 10L * DAY
        val lastScanTime = endTime - (3L * DAY)
        val fallbackStart = endTime - (60L * DAY)
        val overlapMargin = 5L * MINUTE

        val start = ProviderReadRangeCalculator.calculateCatchUpStart(
            endTime = endTime,
            lastSuccessfulScanTime = lastScanTime,
            fallbackStart = fallbackStart,
            overlapMargin = overlapMargin
        )

        assertEquals(lastScanTime - overlapMargin, start)
    }

    @Test
    fun fallbackStart_isUsedWhenThereIsNoSuccessfulScanTime() {
        val endTime = 10L * DAY
        val fallbackStart = 2L * DAY

        val start = ProviderReadRangeCalculator.calculateCatchUpStart(
            endTime = endTime,
            lastSuccessfulScanTime = 0L,
            fallbackStart = fallbackStart,
            overlapMargin = 5L * MINUTE
        )

        assertEquals(fallbackStart, start)
    }

    @Test
    fun calculatedStart_isClampedToValidRange() {
        val endTime = 10L * MINUTE

        val belowZero = ProviderReadRangeCalculator.calculateCatchUpStart(
            endTime = endTime,
            lastSuccessfulScanTime = 1L * MINUTE,
            fallbackStart = 0L,
            overlapMargin = 5L * MINUTE
        )
        val aboveEnd = ProviderReadRangeCalculator.calculateCatchUpStart(
            endTime = endTime,
            lastSuccessfulScanTime = endTime + (1L * DAY),
            fallbackStart = 0L,
            overlapMargin = 5L * MINUTE
        )

        assertEquals(0L, belowZero)
        assertEquals(endTime, aboveEnd)
    }

    private companion object {
        private const val MINUTE = 60_000L
        private const val DAY = 24L * 60 * MINUTE
    }
}
