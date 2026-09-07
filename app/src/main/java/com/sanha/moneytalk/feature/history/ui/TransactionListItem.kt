package com.sanha.moneytalk.feature.history.ui

import com.sanha.moneytalk.core.database.entity.ExpenseEntity
import com.sanha.moneytalk.core.database.entity.IncomeEntity
import com.sanha.moneytalk.core.ui.component.transaction.card.ExpenseTransactionCardInfo
import com.sanha.moneytalk.core.ui.component.transaction.card.IncomeTransactionCardInfo
import com.sanha.moneytalk.core.ui.component.transaction.card.TransactionCardInfo
import com.sanha.moneytalk.core.ui.component.transaction.header.TransactionGroupHeaderInfo

/**
 * LazyColumn에 바로 렌더링할 수 있는 플랫 리스트 아이템
 *
 * Composable은 cardInfo/header 계약을 직접 읽어 렌더링한다.
 * Entity 참조는 Intent 전달용으로만 보관.
 */
sealed interface TransactionListItem {
    /** 그룹 헤더 - TransactionGroupHeaderInfo 구현 */
    data class Header(
        override val title: String,
        override val expenseTotal: Int = 0,
        override val incomeTotal: Int = 0
    ) : TransactionListItem, TransactionGroupHeaderInfo

    /** 지출 아이템 - TransactionCardInfo 포함 */
    data class ExpenseItem(
        val expense: ExpenseEntity,
        val cardInfo: TransactionCardInfo = ExpenseTransactionCardInfo(expense)
    ) : TransactionListItem

    /** 수입 아이템 - TransactionCardInfo 포함 */
    data class IncomeItem(
        val income: IncomeEntity,
        val cardInfo: TransactionCardInfo = IncomeTransactionCardInfo(income)
    ) : TransactionListItem
}
