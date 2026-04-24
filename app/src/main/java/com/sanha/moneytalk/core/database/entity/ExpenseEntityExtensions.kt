package com.sanha.moneytalk.core.database.entity

/** 고정 거래 적용 가능 여부 (일반 지출 또는 이체) */
fun ExpenseEntity.supportsFixedExpense(): Boolean {
    return transactionType == "EXPENSE" ||
        transactionType == "TRANSFER"
}
