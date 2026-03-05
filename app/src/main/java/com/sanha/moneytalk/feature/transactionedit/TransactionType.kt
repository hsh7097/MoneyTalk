package com.sanha.moneytalk.feature.transactionedit

import androidx.annotation.StringRes
import com.sanha.moneytalk.R

/**
 * 거래 유형 분류.
 *
 * 지출(EXPENSE), 수입(INCOME), 이체(TRANSFER).
 * 이체는 ExpenseEntity에 category=TRANSFER로 저장.
 */
enum class TransactionType(@StringRes val labelResId: Int) {
    EXPENSE(R.string.transaction_type_expense),
    INCOME(R.string.transaction_type_income),
    TRANSFER(R.string.transaction_type_transfer)
}
