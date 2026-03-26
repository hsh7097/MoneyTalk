package com.sanha.moneytalk.core.database.entity

import com.sanha.moneytalk.core.model.TransferDirection

/** 고정 지출 적용 가능 여부 (일반 지출 또는 출금 이체만 해당) */
fun ExpenseEntity.supportsFixedExpense(): Boolean {
    return transactionType == "EXPENSE" ||
        (transactionType == "TRANSFER" &&
            transferDirection == TransferDirection.WITHDRAWAL.dbValue)
}
