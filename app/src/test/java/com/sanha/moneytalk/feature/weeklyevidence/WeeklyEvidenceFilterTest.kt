package com.sanha.moneytalk.feature.weeklyevidence

import com.sanha.moneytalk.core.database.entity.ExpenseEntity
import com.sanha.moneytalk.core.database.entity.isIncludedInExpenseStats
import com.sanha.moneytalk.core.util.CardVisibilityFilter
import com.sanha.moneytalk.feature.home.briefing.BriefingExpense
import com.sanha.moneytalk.feature.home.briefing.SpendingBriefingCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class WeeklyEvidenceFilterTest {
    private val zone = ZoneId.of("Asia/Seoul")

    @Test
    fun evidenceMatchesCategoryCalloutAcrossYearAndAccountingBoundaryWithAllVisibilityPolicies() {
        val rows = listOf(
            expense(1, 1_000, "2025-12-26T23:59:59.999"),
            expense(2, 3_000, "2025-12-27T00:00"),
            expense(3, 5_000, "2026-01-02T15:00"),
            expense(4, 900_000, "2026-01-02T15:00:00.001"),
            expense(5, 900_000, "2025-12-19T23:59:59.999"),
            expense(6, 900_000, "2026-01-01T12:00").copy(isExcludedFromStats = true),
            expense(7, 900_000, "2026-01-01T12:00").copy(cardName = "숨김 카드"),
            expense(8, 900_000, "2026-01-01T12:00").copy(originalSms = "BLOCKED purchase"),
            expense(9, 900_000, "2026-01-01T12:00").copy(transactionType = "TRANSFER", transferDirection = "DEPOSIT"),
            expense(10, 900_000, "2026-01-01T12:00").copy(isFixed = true),
            expense(11, 900_000, "2025-12-24T12:00").copy(isFixed = true),
            expense(12, 2_000, "2025-12-28T12:00").copy(category = "식비 > 점심")
        )
        val excluded = setOf("숨김 카드")
        val keywords = setOf("blocked")
        val visible = CardVisibilityFilter.filterVisibleExpenses(rows, excluded).filter {
            it.isIncludedInExpenseStats() && keywords.none { keyword -> it.originalSms.lowercase().contains(keyword) }
        }
        val comparison = comparison(visible, "2025-12-25", "2026-01-24", "2026-01-02T15:00")
        val increase = requireNotNull(comparison.largestCategoryIncrease)
        val request = WeeklyEvidenceRequest.from(comparison, increase.category)
        val result = WeeklyEvidenceFilter.filter(rows, request, excluded, keywords)

        assertEquals("식비", increase.category)
        assertEquals(listOf(3L, 2L), result.recent.map { it.id })
        assertEquals(listOf(1L), result.previous.map { it.id })
        assertEquals(increase.recentAmount, result.recentAmount)
        assertEquals(increase.previousAmount, result.previousAmount)
        assertEquals(epoch("2025-12-20T00:00"), request.queryStartMillis)
        assertEquals(epoch("2026-01-02T15:00"), request.queryEndMillis)
        assertEquals(LocalDate.parse("2025-12-27"), request.recentStart)

        val all = WeeklyEvidenceFilter.filter(rows, request.copy(category = null), excluded, keywords)
        assertEquals(comparison.recent.amount, all.recentAmount)
        assertEquals(comparison.previous.amount, all.previousAmount)
    }

    @Test
    fun pastAccountingPeriodKeepsActualLastFourteenDatesAndFullFinalDay() {
        val rows = listOf(
            expense(1, 2_000, "2026-08-17T23:59:59.999"),
            expense(2, 4_000, "2026-08-24T23:59:59.999"),
            expense(3, 900_000, "2026-08-25T00:00")
        )
        val comparison = comparison(rows, "2026-07-25", "2026-08-24", "2026-09-09T12:00")
        val request = WeeklyEvidenceRequest.from(comparison, "식비", initiallyRecent = false)
        val result = WeeklyEvidenceFilter.filter(rows, request, emptySet(), emptySet())
        assertEquals(LocalDate.parse("2026-08-11"), request.previousStart)
        assertEquals(LocalDate.parse("2026-08-18"), request.recentStart)
        assertEquals(LocalDate.parse("2026-08-24"), request.recentEndInclusive)
        assertEquals(epoch("2026-08-25T00:00") - 1, request.queryEndMillis)
        assertEquals(4_000L, result.recentAmount)
        assertEquals(2_000L, result.previousAmount)
        assertEquals(false, request.initiallyRecent)
    }

    @Test
    fun editedOrDeletedRecordsRecalculateWithinTheSameCutoffAndKeepZeroPreviousWeek() {
        val initial = expense(1, 3_000, "2026-09-09T12:00")
        val comparison = comparison(listOf(initial), "2026-09-01", "2026-09-30", "2026-09-09T13:00")
        val request = WeeklyEvidenceRequest.from(comparison, "식비")
        val changed = WeeklyEvidenceFilter.filter(
            listOf(initial.copy(amount = 2_000), expense(2, 9_000, "2026-09-09T14:00")),
            request, emptySet(), emptySet()
        )
        assertEquals(2_000L, changed.recentAmount)
        assertEquals(0L, changed.previousAmount)
        assertTrue(changed.previous.isEmpty())
        assertEquals(0L, WeeklyEvidenceFilter.filter(emptyList(), request, emptySet(), emptySet()).recentAmount)
    }

    @Test
    fun totalsUseLongAndTransferWithdrawalsKeepExistingExpenseStatsPolicy() {
        val rows = listOf(
            expense(1, 2_000_000_000, "2026-09-09T12:00"),
            expense(2, 2_000_000_000, "2026-09-09T12:01").copy(transactionType = "TRANSFER", transferDirection = "WITHDRAWAL")
        )
        val comparison = comparison(rows, "2026-09-01", "2026-09-30", "2026-09-09T13:00")
        val result = WeeklyEvidenceFilter.filter(rows, WeeklyEvidenceRequest.from(comparison, null), emptySet(), emptySet())
        assertEquals(4_000_000_000L, result.recentAmount)
        assertEquals(listOf(2L, 1L), result.recentByDate.values.flatten().map { it.id })
    }

    private fun comparison(rows: List<ExpenseEntity>, start: String, end: String, now: String) =
        requireNotNull(SpendingBriefingCalculator.calculate(
            rows.map { BriefingExpense(it.amount.toLong(), it.category, it.dateTime, it.isFixed) },
            LocalDate.parse(start), LocalDate.parse(end), LocalDateTime.parse(now).atZone(zone).toInstant(), zone, null
        ).weeklyComparison)

    private fun epoch(value: String) = LocalDateTime.parse(value).atZone(zone).toInstant().toEpochMilli()
    private fun expense(id: Long, amount: Int, time: String) = ExpenseEntity(
        id = id, amount = amount, storeName = "fixture-$id", category = "식비", cardName = "보이는 카드",
        dateTime = epoch(time), originalSms = "", smsId = "weekly-fixture-$id"
    )
}
