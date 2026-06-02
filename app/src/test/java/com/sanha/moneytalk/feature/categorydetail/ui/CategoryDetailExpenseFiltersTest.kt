package com.sanha.moneytalk.feature.categorydetail.ui

import com.sanha.moneytalk.core.database.entity.ExpenseEntity
import com.sanha.moneytalk.core.model.Category
import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryDetailExpenseFiltersTest {

    @Test
    fun filterDisplayExpenses_keepsStatsExcludedExpense() {
        val included = baseExpense(id = 1, isExcludedFromStats = false)
        val statsExcluded = baseExpense(id = 2, isExcludedFromStats = true)

        val result = CategoryDetailExpenseFilters.filterDisplayExpenses(
            listOf(included, statsExcluded),
            emptySet(),
            emptySet()
        )

        assertEquals(listOf(1L, 2L), result.map { it.id })
    }

    @Test
    fun filterStatsExpenses_removesStatsExcludedExpense() {
        val included = baseExpense(id = 1, isExcludedFromStats = false)
        val statsExcluded = baseExpense(id = 2, isExcludedFromStats = true)

        val result = CategoryDetailExpenseFilters.filterStatsExpenses(
            listOf(included, statsExcluded),
            emptySet(),
            emptySet()
        )

        assertEquals(listOf(1L), result.map { it.id })
    }

    @Test
    fun filterDisplayExpenses_removesKeywordExcludedExpense() {
        val normal = baseExpense(id = 1, originalSms = "카드 승인 테스트")
        val keywordExcluded = baseExpense(id = 2, originalSms = "포인트 적립 테스트")

        val result = CategoryDetailExpenseFilters.filterDisplayExpenses(
            listOf(normal, keywordExcluded),
            setOf("포인트"),
            emptySet()
        )

        assertEquals(listOf(1L), result.map { it.id })
    }

    @Test
    fun filterDisplayExpenses_removesExcludedCardExpense() {
        val visible = baseExpense(id = 1, cardName = "신한")
        val hidden = baseExpense(id = 2, cardName = "현대")

        val result = CategoryDetailExpenseFilters.filterDisplayExpenses(
            listOf(visible, hidden),
            emptySet(),
            setOf("현대")
        )

        assertEquals(listOf(1L), result.map { it.id })
    }

    private fun baseExpense(
        id: Long,
        isExcludedFromStats: Boolean = false,
        originalSms: String = "카드 승인 테스트",
        cardName: String = "테스트카드"
    ): ExpenseEntity {
        return ExpenseEntity(
            id = id,
            amount = 1_000,
            storeName = "테스트상점",
            category = Category.ETC.displayName,
            cardName = cardName,
            dateTime = 0L,
            originalSms = originalSms,
            smsId = "test_sms_$id",
            isExcludedFromStats = isExcludedFromStats
        )
    }
}
