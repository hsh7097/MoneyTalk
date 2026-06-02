package com.sanha.moneytalk.core.util

import com.sanha.moneytalk.core.database.entity.ExpenseEntity

/**
 * 제외 카드 정책.
 *
 * 거래 데이터는 보존하되 사용자 노출/집계 단계에서만 제외한다.
 */
object CardVisibilityFilter {

    fun isExcluded(cardName: String, excludedCardNames: Set<String>): Boolean {
        if (excludedCardNames.isEmpty()) return false
        return matchesCardName(cardName, excludedCardNames)
    }

    fun isSelected(cardName: String, selectedCardNames: Set<String>): Boolean {
        if (selectedCardNames.isEmpty()) return true
        return matchesCardName(cardName, selectedCardNames)
    }

    fun filterVisibleExpenses(
        expenses: List<ExpenseEntity>,
        excludedCardNames: Set<String>
    ): List<ExpenseEntity> {
        if (excludedCardNames.isEmpty()) return expenses
        return expenses.filterNot { expense -> isExcluded(expense.cardName, excludedCardNames) }
    }

    fun filterSelectedExpenses(
        expenses: List<ExpenseEntity>,
        selectedCardNames: Set<String>
    ): List<ExpenseEntity> {
        if (selectedCardNames.isEmpty()) return expenses
        return expenses.filter { expense -> isSelected(expense.cardName, selectedCardNames) }
    }

    private fun matchesCardName(cardName: String, targetCardNames: Set<String>): Boolean {
        val normalizedCardName = CardNameNormalizer.normalize(cardName)
        return targetCardNames.any { target ->
            cardName == target || normalizedCardName == CardNameNormalizer.normalize(target)
        }
    }
}
