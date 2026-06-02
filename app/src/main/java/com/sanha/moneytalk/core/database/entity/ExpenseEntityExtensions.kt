package com.sanha.moneytalk.core.database.entity

import com.sanha.moneytalk.core.model.TransferDirection

/** 고정 거래 적용 가능 여부 (일반 지출 또는 이체) */
fun ExpenseEntity.supportsFixedExpense(): Boolean {
    return transactionType == "EXPENSE" ||
        transactionType == "TRANSFER"
}

/** 이체 입금 여부 (수입 방향으로 표시/집계되는 ExpenseEntity) */
fun ExpenseEntity.isTransferDeposit(): Boolean {
    return transactionType == "TRANSFER" &&
        transferDirection == TransferDirection.DEPOSIT.dbValue
}

/** 지출 통계 포함 여부 */
fun ExpenseEntity.isIncludedInExpenseStats(): Boolean {
    return !isExcludedFromStats && !isTransferDeposit()
}

/** 이체 입금이 수입 통계에 포함될 수 있는지 여부 */
fun ExpenseEntity.isIncludedInTransferIncomeStats(): Boolean {
    return !isExcludedFromStats && isTransferDeposit()
}
