package com.sanha.moneytalk.feature.categoryreview

import com.sanha.moneytalk.core.database.entity.ExpenseEntity
import com.sanha.moneytalk.feature.categoryreview.data.CategoryReviewFilter
import com.sanha.moneytalk.feature.categoryreview.ui.CategoryReviewUiState
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryReviewFilterTest {
    @Test
    fun onlyUnclassifiedExpensesAreReviewTargets() {
        val rows = listOf(
            expense(1),
            expense(2).copy(category = "기타"),
            expense(3).copy(category = "식비"),
            expense(4).copy(category = "사용자 카테고리")
        )
        assertEquals(listOf(1L), filter(rows).map { it.id })
    }

    @Test
    fun zeroNegativeAndStatsExcludedRecordsRemainReviewable() {
        val rows = listOf(
            expense(1).copy(amount = 0),
            expense(2).copy(amount = -10_000),
            expense(3).copy(isExcludedFromStats = true),
            expense(4).copy(transactionType = "TRANSFER", transferDirection = "WITHDRAWAL")
        )
        assertEquals(setOf(1L, 2L, 3L, 4L), filter(rows).map { it.id }.toSet())
    }

    @Test
    fun excludedCardsAndSmsKeywordsDoNotAppearOrCount() {
        val rows = listOf(
            expense(1),
            expense(2).copy(cardName = "숨긴 카드"),
            expense(3).copy(originalSms = "TEST 차단 원문"),
            expense(4).copy(storeName = "TEST 이름은 원문 제외 대상 아님")
        )
        val visible = CategoryReviewFilter.filter(rows, setOf("test"), setOf("숨긴 카드"))
        assertEquals(setOf(1L, 4L), visible.map { it.id }.toSet())
        assertEquals(visible.size, CategoryReviewUiState.Content.from(visible).count)
    }

    @Test
    fun newestTransactionsAreFirstWithStableOrderForEqualTimes() {
        val rows = listOf(
            expense(1).copy(dateTime = 300),
            expense(2).copy(dateTime = 100),
            expense(3).copy(dateTime = 300)
        )
        assertEquals(listOf(3L, 1L, 2L), filter(rows).map { it.id })
    }

    @Test
    fun groupedDateUsesLocalDateAndRetainsEveryVisibleRecord() {
        val rows = listOf(
            expense(1).copy(dateTime = Instant.parse("2026-09-08T15:00:00Z").toEpochMilli()),
            expense(2).copy(dateTime = Instant.parse("2026-09-08T14:59:00Z").toEpochMilli())
        )
        val content = CategoryReviewUiState.Content.from(filter(rows), ZoneId.of("Asia/Seoul"))
        assertEquals(listOf("2026-09-09", "2026-09-08"), content.days.map { it.date.toString() })
        assertEquals(2, content.count)
        assertEquals(0, CategoryReviewUiState.Content.from(emptyList()).count)
    }

    private fun filter(rows: List<ExpenseEntity>): List<ExpenseEntity> =
        CategoryReviewFilter.filter(rows, emptySet(), emptySet())

    private fun expense(id: Long) = ExpenseEntity(
        id = id,
        amount = 1_000,
        storeName = "합성 거래 $id",
        category = "미분류",
        cardName = "표시 카드",
        dateTime = id,
        originalSms = "합성 원문",
        smsId = "category-review-$id"
    )
}
