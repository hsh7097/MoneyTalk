package com.sanha.moneytalk.feature.transactionedit.ui

import com.sanha.moneytalk.feature.transactionedit.ui.model.TransactionType

/** 저장 여부 판단에는 화면 표시 상태가 아닌 실제 저장되는 입력만 비교한다. */
internal data class TransactionEditSnapshot(
    val transactionType: TransactionType,
    val amount: String,
    val storeName: String,
    val category: String,
    val cardName: String,
    val incomeType: String,
    val source: String,
    val dateMillis: Long,
    val hour: Int,
    val minute: Int,
    val memo: String,
    val isFixed: Boolean,
    val isExcludedFromStats: Boolean,
    val applyStatsExcludeToAll: Boolean,
    val applyCategoryToAll: Boolean,
    val applyFixedToAll: Boolean,
    val ruleKeyword: String,
    val transferDirection: String?
)

internal fun TransactionEditUiState.toEditSnapshot(): TransactionEditSnapshot {
    return TransactionEditSnapshot(
        transactionType = transactionType,
        amount = amount,
        storeName = storeName,
        category = category,
        cardName = cardName,
        incomeType = incomeType,
        source = source,
        dateMillis = dateMillis,
        hour = hour,
        minute = minute,
        memo = memo,
        isFixed = isFixed,
        isExcludedFromStats = isExcludedFromStats,
        applyStatsExcludeToAll = applyStatsExcludeToAll,
        applyCategoryToAll = applyCategoryToAll,
        applyFixedToAll = applyFixedToAll,
        ruleKeyword = ruleKeyword,
        transferDirection = transferDirection?.dbValue
    )
}
