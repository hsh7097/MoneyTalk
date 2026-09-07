package com.sanha.moneytalk.feature.transactionlist.ui

import com.sanha.moneytalk.core.database.entity.ExpenseEntity
import com.sanha.moneytalk.core.database.entity.IncomeEntity
import com.sanha.moneytalk.core.util.CardVisibilityFilter
import com.sanha.moneytalk.feature.history.ui.FixedExpenseFilter
import com.sanha.moneytalk.feature.history.ui.SortOrder

sealed interface TransactionDetailListItem {
    data class Expense(val expense: ExpenseEntity) : TransactionDetailListItem
    data class Income(val income: IncomeEntity) : TransactionDetailListItem
}

internal object TransactionDetailListFilters {
    fun buildItems(
        expenses: List<ExpenseEntity>,
        incomes: List<IncomeEntity>,
        filter: TransactionDetailFilter?,
        exclusionKeywords: Set<String>,
        excludedCardNames: Set<String>
    ): List<TransactionDetailListItem> {
        val selection = filter ?: TransactionDetailFilter()
        val keywords = if (filter != null) exclusionKeywords else emptySet()
        val filteredExpenses = expenses.filter { expense ->
            val categories = if (expense.transactionType == "TRANSFER") {
                if (!selection.showTransfers) return@filter false
                selection.transferCategories
            } else {
                if (!selection.showExpenses) return@filter false
                selection.expenseCategories
            }
            (categories.isEmpty() || expense.category in categories) &&
                CardVisibilityFilter.isSelected(expense.cardName, selection.cardNames) &&
                !CardVisibilityFilter.isExcluded(expense.cardName, excludedCardNames) &&
                matchesFixed(expense.isFixed, selection.fixedExpenseFilter) &&
                matchesKeywords(expense.originalSms, keywords)
        }
        val filteredIncomes = if (!selection.showIncomes || selection.cardNames.isNotEmpty()) {
            emptyList()
        } else {
            incomes.filter { income ->
                (selection.incomeCategories.isEmpty() || income.category in selection.incomeCategories) &&
                    matchesFixed(income.isRecurring, selection.fixedExpenseFilter) &&
                    matchesKeywords(income.originalSms, keywords)
            }
        }

        // 날짜만 전달하던 기존 진입은 수입 우선, 각 유형의 최신순을 유지한다.
        if (filter == null) {
            return filteredIncomes.sortedByDescending { it.dateTime }.map { TransactionDetailListItem.Income(it) } +
                filteredExpenses.sortedByDescending { it.dateTime }.map { TransactionDetailListItem.Expense(it) }
        }

        // History의 수입만 보기 모드는 정렬 선택과 무관하게 날짜순을 사용한다.
        if (!selection.showExpenses && !selection.showTransfers && selection.showIncomes) {
            return filteredIncomes.sortedByDescending { it.dateTime }.map { TransactionDetailListItem.Income(it) }
        }

        val items: List<TransactionDetailListItem> = filteredExpenses.map { TransactionDetailListItem.Expense(it) } +
            filteredIncomes.map { TransactionDetailListItem.Income(it) }
        return when (selection.sortOrder) {
            SortOrder.DATE_DESC -> items.sortedByDescending { it.dateTime() }
            SortOrder.AMOUNT_DESC -> items.sortedByDescending {
                when (it) {
                    is TransactionDetailListItem.Expense -> it.expense.amount
                    is TransactionDetailListItem.Income -> it.income.amount
                }
            }
            SortOrder.STORE_FREQ -> {
                val expenseGroups = filteredExpenses.groupBy { it.storeName }
                    .values.sortedByDescending { it.size }
                val incomeGroups = filteredIncomes.groupBy { it.source.ifBlank { it.type } }
                    .values.sortedByDescending { it.size }
                expenseGroups.flatMap { group ->
                    group.sortedByDescending { it.dateTime }.map { TransactionDetailListItem.Expense(it) }
                } + incomeGroups.flatMap { group ->
                    group.sortedByDescending { it.dateTime }.map { TransactionDetailListItem.Income(it) }
                }
            }
        }
    }

    private fun matchesFixed(isFixed: Boolean, filter: FixedExpenseFilter): Boolean = when (filter) {
        FixedExpenseFilter.ALL -> true
        FixedExpenseFilter.FIXED_ONLY -> isFixed
        FixedExpenseFilter.EXCLUDE_FIXED -> !isFixed
    }

    private fun matchesKeywords(originalSms: String?, keywords: Set<String>): Boolean {
        if (originalSms == null || keywords.isEmpty()) return true
        val smsLower = originalSms.lowercase()
        return keywords.none { smsLower.contains(it) }
    }

    private fun TransactionDetailListItem.dateTime(): Long = when (this) {
        is TransactionDetailListItem.Expense -> expense.dateTime
        is TransactionDetailListItem.Income -> income.dateTime
    }
}
