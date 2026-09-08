package com.sanha.moneytalk.feature.home.recurring

import com.sanha.moneytalk.core.database.entity.ExpenseEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecurringExpenseForecastCalculatorTest {
    private val seoul = ZoneId.of("Asia/Seoul")

    @Test
    fun twoConsecutiveMonthlyPayments_forecastNextDateAndKeepRealSourceId() {
        val result = calculate(listOf(expense(1, "2026-07-15"), expense(2, "2026-08-15")))

        val item = result.items.single()
        assertEquals(LocalDate.of(2026, 9, 15), item.expectedDate)
        assertEquals(2L, item.sourceExpenseId)
        assertEquals(14_500L, item.expectedAmount)
        assertEquals(LocalDate.of(2026, 8, 15), item.lastPaymentDate)
        assertEquals(2, item.observedMonthCount)
        assertEquals(14_500L, result.totalExpectedAmount)
    }

    @Test
    fun singleMonthAndRepeatedPurchases_doNotBecomeMonthlySchedules() {
        assertTrue(calculate(listOf(expense(1, "2026-08-15"))).items.isEmpty())
        assertTrue(calculate(listOf(
            expense(1, "2026-07-15"), expense(2, "2026-08-15"), expense(3, "2026-08-16")
        )).items.isEmpty())
    }

    @Test
    fun nonFixedPurchaseInSameMonth_alsoMakesPatternAmbiguous() {
        val expenses = listOf(
            expense(1, "2026-07-15"),
            expense(2, "2026-08-15"),
            expense(3, "2026-08-16").copy(isFixed = false)
        )

        assertTrue(calculate(expenses).items.isEmpty())
    }

    @Test
    fun missingMonthAndStaleHistory_areNotCarriedForward() {
        assertTrue(calculate(listOf(expense(1, "2026-06-15"), expense(2, "2026-08-15"))).items.isEmpty())
        assertTrue(calculate(listOf(expense(1, "2026-05-15"), expense(2, "2026-06-15"))).items.isEmpty())
        // September 5 already passed without a new payment; do not invent an October payment.
        assertTrue(calculate(listOf(expense(1, "2026-07-05"), expense(2, "2026-08-05"))).items.isEmpty())
    }

    @Test
    fun latestPaymentMustStillBeFixedIncludedExpense() {
        val previous = expense(1, "2026-07-15")
        val latest = expense(2, "2026-08-15")
        val invalidLatest = listOf(
            latest.copy(isFixed = false),
            latest.copy(isExcludedFromStats = true),
            latest.copy(transactionType = "TRANSFER", transferDirection = "DEPOSIT"),
            latest.copy(transactionType = "TRANSFER", transferDirection = "WITHDRAWAL"),
            latest.copy(amount = 0)
        )

        invalidLatest.forEach { item ->
            assertTrue(calculate(listOf(previous, item)).items.isEmpty())
        }
    }

    @Test
    fun changedCategoryOrAmount_doesNotPretendToBeSameSubscription() {
        val previous = expense(1, "2026-07-15")
        val latest = expense(2, "2026-08-15")

        assertTrue(calculate(listOf(previous, latest.copy(category = "쇼핑"))).items.isEmpty())
        assertTrue(calculate(listOf(previous, latest.copy(amount = 17_000))).items.isEmpty())
    }

    @Test
    fun latestMonthCategoryChange_preventsOldCategoryFromForecastingAgain() {
        val expenses = listOf(
            expense(1, "2026-06-15"),
            expense(2, "2026-07-15"),
            expense(3, "2026-08-15").copy(category = "쇼핑")
        )

        assertTrue(calculate(expenses).items.isEmpty())
    }

    @Test
    fun smallDateVariation_isAllowedButWideSpreadIsRejected() {
        val previous = expense(1, "2026-07-15")

        assertEquals(LocalDate.of(2026, 9, 18), calculate(listOf(
            previous, expense(2, "2026-08-18")
        )).items.single().expectedDate)
        assertTrue(calculate(listOf(previous, expense(2, "2026-08-19"))).items.isEmpty())
    }

    @Test
    fun alreadyPaidThisCycle_onlyForecastsTheNextCycleWithinThirtyDays() {
        val result = calculate(
            listOf(expense(1, "2026-07-15"), expense(2, "2026-08-15"), expense(3, "2026-09-15")),
            today = "2026-09-15"
        )

        assertEquals(LocalDate.of(2026, 10, 15), result.items.single().expectedDate)
        assertEquals(3L, result.items.single().sourceExpenseId)
        assertEquals(3, result.items.single().observedMonthCount)
        assertEquals(result.untilDate, result.items.single().expectedDate)
    }

    @Test
    fun todayIsIncluded_butThirtyOneDaysAheadIsNot() {
        val dueToday = calculate(
            listOf(expense(1, "2026-07-08"), expense(2, "2026-08-08"))
        )
        assertEquals(dueToday.asOfDate, dueToday.items.single().expectedDate)

        val outsideHorizon = calculate(
            listOf(expense(1, "2025-12-01"), expense(2, "2026-01-01")),
            today = "2026-01-01"
        )
        assertTrue(outsideHorizon.items.isEmpty())
    }

    @Test
    fun monthEndPattern_handlesFebruaryAndRestoresThirtyFirst() {
        val february = calculate(
            listOf(expense(1, "2025-12-31"), expense(2, "2026-01-31")),
            today = "2026-02-01"
        )
        assertEquals(LocalDate.of(2026, 2, 28), february.items.single().expectedDate)

        val march = calculate(
            listOf(expense(1, "2026-01-31"), expense(2, "2026-02-28")),
            today = "2026-03-01"
        )
        assertEquals(LocalDate.of(2026, 3, 31), march.items.single().expectedDate)

        val leapYear = calculate(
            listOf(expense(1, "2023-12-31"), expense(2, "2024-01-31")),
            today = "2024-02-01"
        )
        assertEquals(LocalDate.of(2024, 2, 29), leapYear.items.single().expectedDate)
    }

    @Test
    fun regularTwentyEighth_isNotPromotedToMonthEnd() {
        val result = calculate(
            listOf(expense(1, "2026-01-28"), expense(2, "2026-02-28")),
            today = "2026-03-01"
        )

        assertEquals(LocalDate.of(2026, 3, 28), result.items.single().expectedDate)
    }

    @Test
    fun differentCardsStaySeparate_andTotalDoesNotOverflowInt() {
        val result = calculate(listOf(
            expense(1, "2026-07-20").copy(cardName = "카드 A", amount = Int.MAX_VALUE),
            expense(2, "2026-08-20").copy(cardName = "카드 A", amount = Int.MAX_VALUE),
            expense(3, "2026-07-15").copy(cardName = "카드 B", amount = Int.MAX_VALUE),
            expense(4, "2026-08-15").copy(cardName = "카드 B", amount = Int.MAX_VALUE)
        ))

        assertEquals(listOf("카드 B", "카드 A"), result.items.map { it.cardName })
        assertEquals(4_294_967_294L, result.totalExpectedAmount)
    }

    @Test
    fun futureRowsAndUnsavedRows_areNotEvidence() {
        val previous = expense(1, "2026-07-15")
        val latest = expense(2, "2026-08-15")
        val result = calculate(listOf(previous, latest, expense(3, "2026-09-15")))

        assertEquals(2L, result.items.single().sourceExpenseId)
        assertTrue(calculate(listOf(previous, latest.copy(id = 0))).items.isEmpty())
    }

    @Test
    fun missingStoreCardOrCategory_doesNotProduceAnUnidentifiableForecast() {
        val previous = expense(1, "2026-07-15")
        val latest = expense(2, "2026-08-15")
        listOf(
            latest.copy(storeName = " "),
            latest.copy(cardName = ""),
            latest.copy(category = " ")
        ).forEach { item ->
            assertTrue(calculate(listOf(previous, item)).items.isEmpty())
        }
    }

    @Test
    fun timeZoneDeterminesCalendarMonthAndExpectedDay() {
        val records = listOf(
            expense(1, "2026-07-01").copy(dateTime = Instant.parse("2026-06-30T15:15:00Z").toEpochMilli()),
            expense(2, "2026-08-01").copy(dateTime = Instant.parse("2026-07-31T15:15:00Z").toEpochMilli())
        )
        val now = Instant.parse("2026-08-31T15:30:00Z")
        val korea = RecurringExpenseForecastCalculator.calculate(records, now, seoul)
        val utc = RecurringExpenseForecastCalculator.calculate(records, now, ZoneId.of("UTC"))

        assertEquals(LocalDate.of(2026, 9, 1), korea.asOfDate)
        assertEquals(LocalDate.of(2026, 9, 1), korea.items.single().expectedDate)
        assertEquals(LocalDate.of(2026, 8, 31), utc.items.single().expectedDate)
    }

    @Test
    fun onlyWhitespaceAndCaseAreNormalized_withoutMutatingSourceTransactions() {
        val records = listOf(
            expense(1, "2026-07-15").copy(storeName = "  Stream   Plus ", cardName = "CARD"),
            expense(2, "2026-08-15").copy(storeName = "stream plus", cardName = "card")
        )
        val before = records.toList()

        assertEquals(1, calculate(records).items.size)
        assertEquals(before, records)
        assertTrue(calculate(listOf(records[0], records[1].copy(storeName = "streamplus"))).items.isEmpty())
    }

    private fun calculate(
        expenses: List<ExpenseEntity>,
        today: String = "2026-09-08"
    ): RecurringExpenseForecast = RecurringExpenseForecastCalculator.calculate(
        expenses = expenses,
        now = LocalDate.parse(today).atTime(12, 0).atZone(seoul).toInstant(),
        zoneId = seoul
    )

    private fun expense(id: Long, date: String): ExpenseEntity = ExpenseEntity(
        id = id,
        amount = 14_500,
        storeName = "스트리밍",
        category = "문화",
        cardName = "신한카드",
        dateTime = LocalDate.parse(date).atTime(10, 0).atZone(seoul).toInstant().toEpochMilli(),
        originalSms = "",
        smsId = "test-$id",
        isFixed = true,
        createdAt = 0L
    )
}
