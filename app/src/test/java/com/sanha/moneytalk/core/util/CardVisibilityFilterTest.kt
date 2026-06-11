package com.sanha.moneytalk.core.util

import com.sanha.moneytalk.core.database.entity.ExpenseEntity
import com.sanha.moneytalk.core.model.Category
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CardVisibilityFilterTest {

    @Test
    fun filterVisibleExpenses_removesExcludedCardByNormalizedName() {
        val visible = baseExpense(id = 1, cardName = "현금")
        val hidden = baseExpense(id = 2, cardName = "현대카드")

        val result = CardVisibilityFilter.filterVisibleExpenses(
            listOf(visible, hidden),
            setOf("현대")
        )

        assertEquals(listOf(1L), result.map { it.id })
    }

    @Test
    fun filterVisibleExpenses_removesExcludedCardByRawTargetName() {
        val visible = baseExpense(id = 1, cardName = "현금")
        val hidden = baseExpense(id = 2, cardName = "현대")

        val result = CardVisibilityFilter.filterVisibleExpenses(
            listOf(visible, hidden),
            setOf("현대카드")
        )

        assertEquals(listOf(1L), result.map { it.id })
    }

    @Test
    fun filterSelectedExpenses_keepsSelectedCardByRawName() {
        val selected = baseExpense(id = 1, cardName = "신한카드")
        val other = baseExpense(id = 2, cardName = "현대카드")

        val result = CardVisibilityFilter.filterSelectedExpenses(
            listOf(selected, other),
            setOf("신한카드")
        )

        assertEquals(listOf(1L), result.map { it.id })
    }

    @Test
    fun shouldShowExpenseNotification_returnsFalseForExcludedCardByNormalizedName() {
        val result = CardVisibilityFilter.shouldShowExpenseNotification(
            cardName = "우리카드",
            excludedCardNames = setOf("우리")
        )

        assertFalse(result)
    }

    @Test
    fun shouldShowExpenseNotification_returnsTrueForVisibleCard() {
        val result = CardVisibilityFilter.shouldShowExpenseNotification(
            cardName = "신한카드",
            excludedCardNames = setOf("우리")
        )

        assertTrue(result)
    }

    private fun baseExpense(
        id: Long,
        cardName: String
    ): ExpenseEntity {
        return ExpenseEntity(
            id = id,
            amount = 1_000,
            storeName = "테스트상점",
            category = Category.ETC.displayName,
            cardName = cardName,
            dateTime = 0L,
            originalSms = "카드 승인 테스트",
            smsId = "test_sms_$id"
        )
    }
}
