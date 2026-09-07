package com.sanha.moneytalk.feature.transactionlist.ui

import androidx.lifecycle.SavedStateHandle
import com.sanha.moneytalk.feature.history.ui.FixedExpenseFilter
import com.sanha.moneytalk.feature.history.ui.SortOrder

/** 달력에서 날짜 상세로 전달하는 현재 내역 필터. */
data class TransactionDetailFilter(
    val sortOrder: SortOrder = SortOrder.DATE_DESC,
    val showExpenses: Boolean = true,
    val showIncomes: Boolean = true,
    val showTransfers: Boolean = true,
    val expenseCategories: Set<String> = emptySet(),
    val incomeCategories: Set<String> = emptySet(),
    val transferCategories: Set<String> = emptySet(),
    val cardNames: Set<String> = emptySet(),
    val fixedExpenseFilter: FixedExpenseFilter = FixedExpenseFilter.ALL
) {
    companion object {
        const val EXTRA_FILTER_ENABLED = "extra_filter_enabled"
        const val EXTRA_SORT_ORDER = "extra_sort_order"
        const val EXTRA_SHOW_EXPENSES = "extra_show_expenses"
        const val EXTRA_SHOW_INCOMES = "extra_show_incomes"
        const val EXTRA_SHOW_TRANSFERS = "extra_show_transfers"
        const val EXTRA_EXPENSE_CATEGORIES = "extra_expense_categories"
        const val EXTRA_INCOME_CATEGORIES = "extra_income_categories"
        const val EXTRA_TRANSFER_CATEGORIES = "extra_transfer_categories"
        const val EXTRA_CARD_NAMES = "extra_card_names"
        const val EXTRA_FIXED_FILTER = "extra_fixed_filter"

        fun fromSavedStateHandle(state: SavedStateHandle): TransactionDetailFilter? {
            if (state.get<Boolean>(EXTRA_FILTER_ENABLED) != true) return null
            return TransactionDetailFilter(
                sortOrder = SortOrder.entries.firstOrNull {
                    it.name == state.get<String>(EXTRA_SORT_ORDER)
                } ?: SortOrder.DATE_DESC,
                showExpenses = state[EXTRA_SHOW_EXPENSES] ?: true,
                showIncomes = state[EXTRA_SHOW_INCOMES] ?: true,
                showTransfers = state[EXTRA_SHOW_TRANSFERS] ?: true,
                expenseCategories = state.get<ArrayList<String>>(EXTRA_EXPENSE_CATEGORIES).orEmpty().toSet(),
                incomeCategories = state.get<ArrayList<String>>(EXTRA_INCOME_CATEGORIES).orEmpty().toSet(),
                transferCategories = state.get<ArrayList<String>>(EXTRA_TRANSFER_CATEGORIES).orEmpty().toSet(),
                cardNames = state.get<ArrayList<String>>(EXTRA_CARD_NAMES).orEmpty().toSet(),
                fixedExpenseFilter = FixedExpenseFilter.entries.firstOrNull {
                    it.name == state.get<String>(EXTRA_FIXED_FILTER)
                } ?: FixedExpenseFilter.ALL
            )
        }
    }
}
