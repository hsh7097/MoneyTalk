package com.sanha.moneytalk.feature.home.briefing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class SpendingBriefingCalculatorTest {
    private val seoul = ZoneId.of("Asia/Seoul")
    private val newYork = ZoneId.of("America/New_York")

    @Test
    fun customAccountingMonthUsesOnlyItsExpensesAndIncludesTodayInDaysLeft() {
        val result = calculate(
            expenses = listOf(
                expense(90_000, "2026-08-26T12:00"),
                expense(110_000, "2026-09-06T12:00"),
                expense(900_000, "2026-08-24T12:00")
            ),
            start = "2026-08-25",
            end = "2026-09-24",
            budget = 510_000
        )

        assertTrue(result.isCurrentPeriod)
        assertEquals(200_000L, result.recordedExpense)
        assertEquals(310_000L, result.budgetRemaining)
        assertEquals(17, result.remainingDays)
        assertEquals(18_235L, result.dailyReference)
    }

    @Test
    fun missingZeroAndExceededBudgetsRemainDistinct() {
        val records = listOf(expense(20_000, "2026-09-08T12:00"))
        val missing = calculate(records, budget = null)
        val zero = calculate(emptyList(), budget = 0L)
        val exceeded = calculate(records, budget = 15_000)

        assertNull(missing.budgetRemaining)
        assertNull(missing.dailyReference)
        assertEquals(0L, zero.budgetRemaining)
        assertEquals(0L, zero.dailyReference)
        assertEquals(-5_000L, exceeded.budgetRemaining)
        assertNull(exceeded.dailyReference)
    }

    @Test
    fun accountingPeriodLastDayAndLeapDayHaveOneRemainingDay() {
        val lastCustomDay = calculate(emptyList(), start = "2026-08-09", end = "2026-09-08", budget = 15_000)
        val leapDay = calculate(
            emptyList(), start = "2028-02-01", end = "2028-02-29", now = "2028-02-29T23:50", budget = 9_000
        )

        assertEquals(1, lastCustomDay.remainingDays)
        assertEquals(15_000L, lastCustomDay.dailyReference)
        assertEquals(1, leapDay.remainingDays)
        assertEquals(9_000L, leapDay.dailyReference)
    }

    @Test
    fun pastMonthComparesItsFinalWeeksAndDoesNotSuggestFutureDailySpending() {
        val result = calculate(
            listOf(
                expense(2_000, "2026-08-20T12:00"),
                expense(4_000, "2026-08-31T23:59"),
                expense(90_000, "2026-09-01T00:00")
            ),
            start = "2026-08-01", end = "2026-08-31", budget = 20_000
        )

        assertFalse(result.isCurrentPeriod)
        assertEquals(0, result.remainingDays)
        assertNull(result.dailyReference)
        assertEquals(14_000L, result.budgetRemaining)
        val weekly = requireNotNull(result.weeklyComparison)
        assertEquals(LocalDate.parse("2026-08-25"), weekly.recent.startDate)
        assertEquals(LocalDate.parse("2026-08-31"), weekly.recent.endDateInclusive)
        assertEquals(4_000L, weekly.recent.amount)
        assertEquals(2_000L, weekly.previous.amount)
    }

    @Test
    fun weeklyWindowsCrossMonthBoundaryWithoutCountingBoundaryOrFutureTransactionsTwice() {
        val result = calculate(
            listOf(
                expense(99_000, "2026-08-25T23:59:59.999"),
                expense(1_000, "2026-08-26T00:00"),
                expense(2_000, "2026-09-01T23:59:59.999"),
                expense(4_000, "2026-09-02T00:00"),
                expense(8_000, "2026-09-08T15:00"),
                expense(99_000, "2026-09-08T15:00:00.001")
            )
        )

        val weekly = requireNotNull(result.weeklyComparison)
        assertEquals(3_000L, weekly.previous.amount)
        assertEquals(12_000L, weekly.recent.amount)
        assertEquals(2, weekly.recent.transactionCount)
        assertEquals(2, weekly.previous.transactionCount)
        assertEquals(9_000L, weekly.difference)
        // 월 예산은 기존 홈 합계처럼 저장된 미래 시각 기록까지 포함한다.
        assertEquals(113_000L, result.recordedExpense)
    }

    @Test
    fun springDstUsesCalendarBoundariesInsteadOfSevenTimesTwentyFourHours() {
        val result = calculate(
            listOf(
                expense(10_000, "2026-03-14T23:30", zone = newYork),
                expense(20_000, "2026-03-15T00:30", zone = newYork)
            ),
            start = "2026-03-01", end = "2026-03-31", now = "2026-03-21T12:00", zone = newYork
        )

        val weekly = requireNotNull(result.weeklyComparison)
        assertEquals(10_000L, weekly.previous.amount)
        assertEquals(20_000L, weekly.recent.amount)
        assertEquals(11, result.remainingDays)
    }

    @Test
    fun autumnDstKeepsLateFinalDayAndBothRepeatedClockTimes() {
        val result = calculate(
            listOf(
                BriefingExpense(1_000, "식비", Instant.parse("2026-11-01T05:30:00Z").toEpochMilli()),
                BriefingExpense(2_000, "식비", Instant.parse("2026-11-01T06:30:00Z").toEpochMilli()),
                expense(4_000, "2026-11-07T23:30", zone = newYork),
                expense(8_000, "2026-11-08T00:00", zone = newYork)
            ),
            start = "2026-11-01", end = "2026-11-30", now = "2026-11-14T12:00", zone = newYork
        )

        val weekly = requireNotNull(result.weeklyComparison)
        assertEquals(7_000L, weekly.previous.amount)
        assertEquals(3, weekly.previous.transactionCount)
        assertEquals(8_000L, weekly.recent.amount)
    }

    @Test
    fun categoryIncreaseUsesDifferenceRatherThanLargestRecentTotal() {
        val result = calculate(
            listOf(
                expense(9_000, "2026-09-01T12:00", "식비"),
                expense(10_000, "2026-09-03T12:00", "식비"),
                expense(5_000, "2026-09-03T12:00", "교통"),
                expense(7_000, "2026-09-01T12:00", "쇼핑")
            )
        )

        val increase = requireNotNull(result.weeklyComparison?.largestCategoryIncrease)
        assertEquals("교통", increase.category)
        assertEquals(5_000L, increase.increase)
        assertEquals(0L, increase.previousAmount)
    }

    @Test
    fun tiedCategoryIncreasesAreStableAndDecreasesDoNotProduceIncreaseCallout() {
        val tiedRecords = listOf(
            expense(3_000, "2026-09-03T12:00", "B"),
            expense(3_000, "2026-09-03T12:00", "A")
        )
        assertEquals("A", calculate(tiedRecords).weeklyComparison?.largestCategoryIncrease?.category)
        assertEquals("A", calculate(tiedRecords.reversed()).weeklyComparison?.largestCategoryIncrease?.category)

        val decreased = calculate(
            listOf(expense(4_000, "2026-09-01T12:00"), expense(2_000, "2026-09-03T12:00"))
        )
        assertNull(decreased.weeklyComparison?.largestCategoryIncrease)
    }

    @Test
    fun longTotalsDoNotOverflowIntAndInputRecordsAreNotMutated() {
        val records = listOf(expense(2_000_000_000, "2026-09-03T12:00"), expense(2_000_000_000, "2026-09-04T12:00"))
        val result = calculate(records, budget = 5_000_000_000)

        assertEquals(4_000_000_000L, result.recordedExpense)
        assertEquals(4_000_000_000L, result.weeklyComparison?.recent?.amount)
        assertEquals(1_000_000_000L, result.budgetRemaining)
        assertEquals(2, records.size)
        assertEquals(2_000_000_000L, records.first().amount)
    }

    @Test
    fun emptyAndFuturePeriodsDoNotClaimSavingsOrPresentFutureDailyBudget() {
        val empty = calculate(emptyList())
        assertEquals(0, empty.weeklyComparison?.recent?.transactionCount)
        assertEquals(0, empty.weeklyComparison?.previous?.transactionCount)
        assertNull(empty.weeklyComparison?.largestCategoryIncrease)

        val future = calculate(
            listOf(expense(5_000, "2026-10-01T12:00")),
            start = "2026-10-01", end = "2026-10-31", budget = 100_000
        )
        assertFalse(future.isCurrentPeriod)
        assertNull(future.dailyReference)
        assertNull(future.weeklyComparison)
        assertEquals(5_000L, future.recordedExpense)
    }

    private fun calculate(
        expenses: List<BriefingExpense>,
        start: String = "2026-09-01",
        end: String = "2026-09-30",
        now: String = "2026-09-08T15:00",
        budget: Long? = 100_000,
        zone: ZoneId = seoul
    ) = SpendingBriefingCalculator.calculate(
        expenses = expenses,
        periodStart = LocalDate.parse(start),
        periodEndInclusive = LocalDate.parse(end),
        now = LocalDateTime.parse(now).atZone(zone).toInstant(),
        zoneId = zone,
        monthlyBudget = budget
    )

    private fun expense(
        amount: Long,
        time: String,
        category: String = "식비",
        zone: ZoneId = seoul
    ) = BriefingExpense(amount, category, LocalDateTime.parse(time).atZone(zone).toInstant().toEpochMilli())
}
