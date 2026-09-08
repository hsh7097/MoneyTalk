package com.sanha.moneytalk.core.ui.component.chart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VisibleTrendAxisMaxTest {
    @Test fun smallSpendingDoesNotUseAMillionWonMinimum() {
        assertEquals(100_000L, visibleTrendAxisMax(62_500L))
    }

    @Test fun emptyAndZeroDataKeepANonzeroAxis() {
        assertEquals(1L, visibleTrendAxisMax(0L))
    }

    @Test fun axisRoundsToReadableSteps() {
        mapOf(1L to 1L, 11L to 20L, 21L to 50L, 51L to 100L,
            1_001L to 2_000L, 5_544_692L to 10_000_000L).forEach { (amount, expected) ->
            assertEquals(expected, visibleTrendAxisMax(amount))
        }
    }

    @Test fun selectedMillionWonBudgetFitsTheAxis() {
        assertEquals(1_000_000L, visibleTrendAxisMax(1_000_000L))
    }

    @Test fun largeValuesNeverOverflowBelowTheData() {
        listOf(Int.MAX_VALUE.toLong(), Long.MAX_VALUE / 2, Long.MAX_VALUE).forEach { amount ->
            assertTrue(visibleTrendAxisMax(amount) >= amount)
        }
    }
}
