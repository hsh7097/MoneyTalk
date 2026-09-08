package com.sanha.moneytalk.feature.transactionactions.model

/** 화면을 열었을 때의 값과 비교해 사용자가 바꾼 필드만 전달한다. null은 미변경이다. */
data class TransactionQuickPatch(
    val category: String? = null,
    val isFixed: Boolean? = null,
    val isExcludedFromStats: Boolean? = null
)
