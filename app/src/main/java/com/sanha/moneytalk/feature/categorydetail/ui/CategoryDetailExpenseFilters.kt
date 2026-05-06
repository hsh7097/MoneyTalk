package com.sanha.moneytalk.feature.categorydetail.ui

import com.sanha.moneytalk.core.database.entity.ExpenseEntity
import com.sanha.moneytalk.core.database.entity.isIncludedInExpenseStats

internal object CategoryDetailExpenseFilters {

    fun filterDisplayExpenses(
        expenses: List<ExpenseEntity>,
        exclusionKeywords: Set<String>
    ): List<ExpenseEntity> {
        return expenses.filter { expense ->
            if (exclusionKeywords.isEmpty()) {
                true
            } else {
                val smsLower = expense.originalSms.lowercase()
                exclusionKeywords.none { keyword -> smsLower.contains(keyword) }
            }
        }
    }

    fun filterStatsExpenses(
        expenses: List<ExpenseEntity>,
        exclusionKeywords: Set<String>
    ): List<ExpenseEntity> {
        return filterStatsExpenses(filterDisplayExpenses(expenses, exclusionKeywords))
    }

    fun filterStatsExpenses(expenses: List<ExpenseEntity>): List<ExpenseEntity> {
        return expenses.filter { expense -> expense.isIncludedInExpenseStats() }
    }
}
