package com.sanha.moneytalk.feature.transactionactions.ui

import androidx.annotation.StringRes
import com.sanha.moneytalk.core.model.CategoryInfo
import com.sanha.moneytalk.feature.transactionactions.model.QuickTransaction
import com.sanha.moneytalk.feature.transactionactions.model.TransactionQuickPatch
import com.sanha.moneytalk.feature.transactionactions.model.TransactionTarget

data class TransactionQuickActionUiState(
    val target: TransactionTarget? = null,
    val transaction: QuickTransaction? = null,
    val categories: List<CategoryInfo> = emptyList(),
    val category: String = "",
    val isFixed: Boolean = false,
    val isExcludedFromStats: Boolean? = null,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val showCategories: Boolean = false,
    val confirmDelete: Boolean = false,
    @param:StringRes val error: Int? = null
) {
    val patch: TransactionQuickPatch
        get() = TransactionQuickPatch(
            category = category.takeIf { it != transaction?.category },
            isFixed = isFixed.takeIf { it != transaction?.isFixed },
            isExcludedFromStats = isExcludedFromStats?.takeIf { it != transaction?.isExcludedFromStats }
        )

    val hasChanges: Boolean
        get() = transaction != null && patch != TransactionQuickPatch()
}
