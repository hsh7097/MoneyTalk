package com.sanha.moneytalk.feature.transactionedit.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Stable
import com.sanha.moneytalk.core.model.Category
import com.sanha.moneytalk.core.model.CategoryInfo
import com.sanha.moneytalk.core.model.TransferDirection
import com.sanha.moneytalk.feature.transactionedit.ui.model.TransactionType
import java.util.Calendar

@Stable
data class TransactionEditUiState(
    val isNew: Boolean = true,
    val transactionType: TransactionType = TransactionType.EXPENSE,
    val isLoading: Boolean = true,
    @StringRes val loadErrorResId: Int? = null,
    val amount: String = "",
    val storeName: String = "",
    val category: String = Category.ETC.displayName,
    val cardName: String = "",
    val incomeType: String = "",
    val source: String = "",
    val dateMillis: Long = System.currentTimeMillis(),
    val hour: Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
    val minute: Int = Calendar.getInstance().get(Calendar.MINUTE),
    val memo: String = "",
    val originalSms: String = "",
    val isFixed: Boolean = false,
    val isExcludedFromStats: Boolean = false,
    val transferDirection: TransferDirection? = null,
    /** 카테고리 변경을 동일 거래처에 일괄 적용 */
    val applyCategoryToAll: Boolean = false,
    /** 고정 거래 변경을 동일 거래처에 일괄 적용 */
    val applyFixedToAll: Boolean = false,
    /** 통계 제외 변경을 동일 거래처에 일괄 적용 */
    val applyStatsExcludeToAll: Boolean = false,
    /** 거래처 규칙 매칭 키워드 (일괄 적용 시 사용) */
    val ruleKeyword: String = "",
    val categoryEntries: List<CategoryInfo> = Category.expenseEntries,
    val showCategoryPicker: Boolean = false,
    val showAddCategoryDialog: Boolean = false,
    val addCategoryEmoji: String = "\uD83D\uDCE6",
    val addCategoryName: String = "",
    @StringRes val addCategoryErrorResId: Int? = null,
    val isSaved: Boolean = false,
    val isDeleted: Boolean = false
) {
    /** 하위 호환용 계산 프로퍼티 */
    val isIncome: Boolean get() = transactionType == TransactionType.INCOME
    val isTransfer: Boolean get() = transactionType == TransactionType.TRANSFER
}
