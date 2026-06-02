package com.sanha.moneytalk.feature.categorydetail.ui

import com.sanha.moneytalk.core.database.entity.ExpenseEntity
import com.sanha.moneytalk.core.database.entity.isIncludedInExpenseStats
import com.sanha.moneytalk.core.util.CardVisibilityFilter

internal object CategoryDetailExpenseFilters {

    fun filterDisplayExpenses(
        expenses: List<ExpenseEntity>,
        exclusionKeywords: Set<String>,
        excludedCardNames: Set<String>
    ): List<ExpenseEntity> {
        val keywordFiltered = expenses.filter { expense ->
            if (exclusionKeywords.isEmpty()) {
                true
            } else {
                val smsLower = expense.originalSms.lowercase()
                exclusionKeywords.none { keyword -> smsLower.contains(keyword) }
            }
        }
        return CardVisibilityFilter.filterVisibleExpenses(keywordFiltered, excludedCardNames)
    }

    fun filterStatsExpenses(
        expenses: List<ExpenseEntity>,
        exclusionKeywords: Set<String>,
        excludedCardNames: Set<String>
    ): List<ExpenseEntity> {
        return filterStatsExpenses(
            filterDisplayExpenses(expenses, exclusionKeywords, excludedCardNames)
        )
    }

    fun filterStatsExpenses(expenses: List<ExpenseEntity>): List<ExpenseEntity> {
        return expenses.filter { expense -> expense.isIncludedInExpenseStats() }
    }
}
