package com.sanha.moneytalk.feature.transactionactions.model

import com.sanha.moneytalk.core.model.CategoryType

data class QuickTransaction(
    val target: TransactionTarget,
    val title: String,
    val amount: Int,
    val category: String,
    val categoryType: CategoryType,
    val isFixed: Boolean,
    /** 수입에는 통계 제외 필드가 없으므로 null이다. */
    val isExcludedFromStats: Boolean?
)
