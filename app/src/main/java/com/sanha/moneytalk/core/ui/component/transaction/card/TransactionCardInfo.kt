package com.sanha.moneytalk.core.ui.component.transaction.card

import com.sanha.moneytalk.core.database.entity.ExpenseEntity
import com.sanha.moneytalk.core.database.entity.IncomeEntity
import com.sanha.moneytalk.core.model.Category
import java.text.SimpleDateFormat
import java.util.*

/**
 * 거래 카드 UI에 필요한 데이터를 정의하는 Interface.
 * Composable은 Entity를 직접 참조하지 않고 이 Interface로 렌더링한다.
 */
interface TransactionCardInfo {
    /** 가게명 또는 수입 설명 */
    val title: String
    /** "카테고리 | 카드명" 또는 "유형 | 시간" */
    val subtitle: String
    /** 거래 금액 (원 단위) */
    val amount: Int
    /** true=수입, false=지출 (금액 색상 분기용) */
    val isIncome: Boolean
    /** 카테고리 (지출 아이콘용, 수입이면 null) */
    val category: Category?
        get() = null
    /** 이모지 아이콘 (수입용, category가 null일 때 사용) */
    val iconEmoji: String?
        get() = null
}

/** ExpenseEntity → TransactionCardInfo 변환 */
class ExpenseTransactionCardInfo(
    private val expense: ExpenseEntity
) : TransactionCardInfo {
    override val title: String = expense.storeName
    override val subtitle: String = "${expense.category} | ${expense.cardName}"
    override val amount: Int = expense.amount
    override val isIncome: Boolean = false
    override val category: Category = Category.fromDisplayName(expense.category)
}

/** IncomeEntity → TransactionCardInfo 변환 */
class IncomeTransactionCardInfo(
    private val income: IncomeEntity
) : TransactionCardInfo {
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.KOREA)

    override val title: String = income.description.ifBlank { income.type }
    override val subtitle: String = "${income.type} | ${timeFormat.format(Date(income.dateTime))}"
    override val amount: Int = income.amount
    override val isIncome: Boolean = true
    override val iconEmoji: String = "\uD83D\uDCB0"
}
