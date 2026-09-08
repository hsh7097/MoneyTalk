package com.sanha.moneytalk.feature.transactionactions.model

sealed interface TransactionQuickUpdateResult {
    data class Updated(
        val transaction: QuickTransaction,
        val changed: Boolean
    ) : TransactionQuickUpdateResult

    data object Missing : TransactionQuickUpdateResult
    data object InvalidCategory : TransactionQuickUpdateResult
    data object UnsupportedField : TransactionQuickUpdateResult
}
