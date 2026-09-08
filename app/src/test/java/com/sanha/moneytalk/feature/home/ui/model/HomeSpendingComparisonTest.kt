package com.sanha.moneytalk.feature.home.ui.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeSpendingComparisonTest {

    @Test
    fun firstDayUsesFirstDaysAmountAfterTheZeroOrigin() {
        val result = compare(
            current = listOf(0L, 12_000L, 90_000L),
            previous = listOf(0L, 10_000L, 70_000L),
            todayDayIndex = 1
        )

        assertEquals(12_000L, result.currentAmount)
        assertEquals(10_000L, result.previousAmount)
        assertEquals(2_000L, result.difference)
    }

    @Test
    fun currentPeriodExcludesFutureRecordsAndComparesTheSameElapsedDay() {
        val result = compare(
            current = listOf(0L, 100_000L, 100_000L, 300_000L),
            previous = listOf(0L, 50_000L, 150_000L, 450_000L),
            todayDayIndex = 2
        )

        assertEquals(100_000L, result.currentAmount)
        assertEquals(150_000L, result.previousAmount)
        assertEquals(-50_000L, result.difference)
        assertTrue(result.isAvailable)
    }

    @Test
    fun pastPeriodUsesEachFullMonthEvenWhenTheirLengthsDiffer() {
        val result = compare(
            current = listOf(0L, 25_000L, 50_000L),
            previous = listOf(0L, 30_000L, 75_000L, 90_000L),
            todayDayIndex = -1
        )

        assertEquals(50_000L, result.currentAmount)
        assertEquals(90_000L, result.previousAmount)
        assertEquals(-40_000L, result.difference)
    }

    @Test
    fun shorterPreviousMonthCarriesItsFinalAmountToTheCurrentElapsedDay() {
        val result = compare(
            current = (0..31).map { it * 1_000L },
            previous = (0..28).map { it * 1_500L },
            todayDayIndex = 31
        )

        assertEquals(31_000L, result.currentAmount)
        assertEquals(42_000L, result.previousAmount)
        assertEquals(-11_000L, result.difference)
    }

    @Test
    fun twoCompleteZeroSpendingPeriodsAreAnAvailableEqualComparison() {
        val result = compare(listOf(0L, 0L), listOf(0L, 0L), todayDayIndex = 1)

        assertTrue(result.isAvailable)
        assertEquals(0L, result.difference)
        assertNull(result.unavailableReason)
    }

    @Test
    fun completePreviousZeroSpendingCanBeComparedWithoutAPercentage() {
        val result = compare(listOf(0L, 7_500L), listOf(0L, 0L), todayDayIndex = 1)

        assertTrue(result.isAvailable)
        assertEquals(0L, result.previousAmount)
        assertEquals(7_500L, result.difference)
    }

    @Test
    fun currentZeroSpendingIsLessThanACompletePreviousPeriod() {
        val result = compare(listOf(0L, 0L), listOf(0L, 7_500L), todayDayIndex = 1)

        assertTrue(result.isAvailable)
        assertEquals(-7_500L, result.difference)
    }

    @Test
    fun incompleteCurrentPeriodKeepsRecordedAmountButDoesNotEvaluateTheDifference() {
        val result = compare(
            current = listOf(0L, 1_000L),
            previous = listOf(0L, 10_000L),
            todayDayIndex = 1,
            currentComplete = false
        )

        assertEquals(1_000L, result.currentAmount)
        assertFalse(result.isAvailable)
        assertNull(result.difference)
        assertEquals(HomeSpendingComparison.UnavailableReason.CURRENT_PERIOD_INCOMPLETE, result.unavailableReason)
    }

    @Test
    fun incompletePreviousZeroSpendingIsUnavailableRatherThanARealZeroComparison() {
        val result = compare(
            current = listOf(0L, 7_500L),
            previous = listOf(0L, 0L),
            todayDayIndex = 1,
            previousComplete = false
        )

        assertFalse(result.isAvailable)
        assertNull(result.difference)
        assertEquals(HomeSpendingComparison.UnavailableReason.PREVIOUS_PERIOD_INCOMPLETE, result.unavailableReason)
    }

    @Test
    fun currentPeriodNoticeTakesPriorityWhenBothPeriodsAreIncomplete() {
        val result = compare(
            current = listOf(0L, 1_000L),
            previous = listOf(0L, 7_500L),
            todayDayIndex = 1,
            currentComplete = false,
            previousComplete = false
        )

        assertNull(result.difference)
        assertEquals(HomeSpendingComparison.UnavailableReason.CURRENT_PERIOD_INCOMPLETE, result.unavailableReason)
    }

    @Test
    fun missingPointsRemainUnavailableEvenWhenCollectionIsMarkedComplete() {
        listOf(
            emptyList<Long>() to listOf(0L, 7_500L),
            listOf(0L, 7_500L) to emptyList(),
            emptyList<Long>() to emptyList()
        ).forEach { (current, previous) ->
            val result = compare(current, previous, todayDayIndex = 1)

            assertFalse(result.isAvailable)
            assertNull(result.difference)
            assertEquals(HomeSpendingComparison.UnavailableReason.MISSING_DATA, result.unavailableReason)
        }
    }

    @Test
    fun cumulativeAmountsRemainLongValuesAboveTheIntRange() {
        val result = compare(
            current = listOf(0L, 3_000_000_000L, 4_000_000_000L),
            previous = listOf(0L, 2_500_000_000L, 3_500_000_000L),
            todayDayIndex = 1
        )

        assertEquals(3_000_000_000L, result.currentAmount)
        assertEquals(500_000_000L, result.difference)
    }

    private fun compare(
        current: List<Long>,
        previous: List<Long>,
        todayDayIndex: Int,
        currentComplete: Boolean = true,
        previousComplete: Boolean = true
    ) = HomeSpendingComparison.calculate(
        currentPoints = current,
        previousPoints = previous,
        todayDayIndex = todayDayIndex,
        isCurrentPeriodComplete = currentComplete,
        isPreviousPeriodComplete = previousComplete
    )
}
