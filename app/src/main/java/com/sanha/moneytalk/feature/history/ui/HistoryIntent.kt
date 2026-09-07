package com.sanha.moneytalk.feature.history.ui

import com.sanha.moneytalk.core.database.entity.ExpenseEntity
import com.sanha.moneytalk.core.database.entity.IncomeEntity

/**
 * History 화면의 모든 사용자 인터랙션을 표현하는 Intent
 */
sealed interface HistoryIntent {
    // 아이템 클릭
    data class SelectExpense(val expense: ExpenseEntity) : HistoryIntent
    data class SelectIncome(val income: IncomeEntity) : HistoryIntent
    data object DismissDialog : HistoryIntent

    // 지출 액션
    data class DeleteExpense(val expense: ExpenseEntity) : HistoryIntent
    data class ChangeCategory(val storeName: String, val newCategory: String) :
        HistoryIntent

    data class UpdateExpenseMemo(val expenseId: Long, val memo: String?) : HistoryIntent

    // 수입 액션
    data class DeleteIncome(val income: IncomeEntity) : HistoryIntent
    data class UpdateIncomeMemo(val incomeId: Long, val memo: String?) : HistoryIntent
}
