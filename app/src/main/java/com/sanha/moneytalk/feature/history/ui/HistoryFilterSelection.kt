package com.sanha.moneytalk.feature.history.ui

import androidx.annotation.StringRes
import com.sanha.moneytalk.R

internal enum class CategorySheetType(@StringRes val titleResId: Int) {
    EXPENSE(R.string.history_filter_expense_category_title),
    INCOME(R.string.history_filter_income_category_title),
    TRANSFER(R.string.history_filter_transfer_category_title)
}

internal enum class FilterTransactionType(@StringRes val labelResId: Int) {
    ALL(R.string.common_all),
    EXPENSE(R.string.home_expense),
    INCOME(R.string.home_income),
    TRANSFER(R.string.transaction_type_transfer)
}

/** 필터 시트의 미적용 선택 값. 적용 전까지 HistoryViewModel 상태를 바꾸지 않는다. */
internal data class HistoryFilterSelection(
    val sortOrder: SortOrder = SortOrder.DATE_DESC,
    val showExpenses: Boolean = true,
    val showIncomes: Boolean = true,
    val showTransfers: Boolean = true,
    val expenseCategories: Set<String> = emptySet(),
    val incomeCategories: Set<String> = emptySet(),
    val transferCategories: Set<String> = emptySet(),
    val cardNames: Set<String> = emptySet(),
    val fixedExpenseFilter: FixedExpenseFilter = FixedExpenseFilter.ALL,
    private val hasAutoCollapsed: Boolean = !(showExpenses && showIncomes && showTransfers)
) {
    val selectedTypes: List<FilterTransactionType>
        get() = buildList {
            if (showExpenses) add(FilterTransactionType.EXPENSE)
            if (showIncomes) add(FilterTransactionType.INCOME)
            if (showTransfers) add(FilterTransactionType.TRANSFER)
        }

    val isDefault: Boolean
        get() = sortOrder == SortOrder.DATE_DESC && showExpenses && showIncomes && showTransfers &&
            expenseCategories.isEmpty() && incomeCategories.isEmpty() && transferCategories.isEmpty() &&
            cardNames.isEmpty() && fixedExpenseFilter == FixedExpenseFilter.ALL

    fun selectType(type: FilterTransactionType): HistoryFilterSelection {
        if (type == FilterTransactionType.ALL || selectedTypes == listOf(type)) {
            return copy(
                showExpenses = true, showIncomes = true, showTransfers = true,
                expenseCategories = emptySet(), incomeCategories = emptySet(), transferCategories = emptySet(),
                hasAutoCollapsed = false
            )
        }
        return copy(
            showExpenses = type == FilterTransactionType.EXPENSE,
            showIncomes = type == FilterTransactionType.INCOME,
            showTransfers = type == FilterTransactionType.TRANSFER,
            expenseCategories = if (type == FilterTransactionType.EXPENSE) expenseCategories else emptySet(),
            incomeCategories = if (type == FilterTransactionType.INCOME) incomeCategories else emptySet(),
            transferCategories = if (type == FilterTransactionType.TRANSFER) transferCategories else emptySet(),
            hasAutoCollapsed = true
        )
    }

    fun selectCategories(type: CategorySheetType, categories: Set<String>): HistoryFilterSelection {
        val updated = when (type) {
            CategorySheetType.EXPENSE -> copy(expenseCategories = categories, showExpenses = true)
            CategorySheetType.INCOME -> copy(incomeCategories = categories, showIncomes = true)
            CategorySheetType.TRANSFER -> copy(transferCategories = categories, showTransfers = true)
        }
        // 기본 전체 상태에서 처음 범위를 좁히는 경우에만 다른 거래 유형을 해제한다.
        if (!hasAutoCollapsed && categories.isNotEmpty() &&
            updated.showExpenses && updated.showIncomes && updated.showTransfers
        ) {
            return updated.copy(
                showExpenses = type == CategorySheetType.EXPENSE,
                showIncomes = type == CategorySheetType.INCOME,
                showTransfers = type == CategorySheetType.TRANSFER,
                hasAutoCollapsed = true
            )
        }
        return updated
    }
}
