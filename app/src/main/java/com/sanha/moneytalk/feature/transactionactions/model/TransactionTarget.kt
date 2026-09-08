package com.sanha.moneytalk.feature.transactionactions.model

/** 지출/이체와 수입 테이블에서 같은 숫자 ID를 서로 다른 거래로 취급한다. */
sealed interface TransactionTarget {
    val id: Long

    data class Expense(override val id: Long) : TransactionTarget
    data class Income(override val id: Long) : TransactionTarget
}
