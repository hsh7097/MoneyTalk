package com.sanha.moneytalk.core.ui.component.transaction.card

import com.sanha.moneytalk.core.database.entity.ExpenseEntity
import com.sanha.moneytalk.core.database.entity.IncomeEntity
import com.sanha.moneytalk.core.model.Category
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 거래 카드 UI에 필요한 데이터를 정의하는 Interface.
 * Composable은 Entity를 직접 참조하지 않고 이 Interface로 렌더링한다.
 */
interface TransactionCardInfo {
    /** 가게명 또는 수입 설명 */
    val title: String

    /** fallback subtitle ("카테고리 | 카드명" 등) — 개별 필드가 없을 때 사용 */
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

    /** 카테고리 태그 텍스트 (칩 표시용, 예: "식비") */
    val categoryTag: String?
        get() = null

    /** 시간 텍스트 (예: "18:30 PM") */
    val time: String?
        get() = null

    /** 카드명 (예: "신한카드") */
    val cardNameText: String?
        get() = null
}

/** ExpenseEntity → TransactionCardInfo 변환 */
class ExpenseTransactionCardInfo(
    private val expense: ExpenseEntity
) : TransactionCardInfo {
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.KOREA)
    private val amPmFormat = SimpleDateFormat("a", Locale.ENGLISH)

    override val title: String = expense.storeName
    override val subtitle: String = "${expense.category} | ${expense.cardName}"
    override val amount: Int = expense.amount
    override val isIncome: Boolean = false
    override val category: Category = Category.fromDisplayName(expense.category)
    override val categoryTag: String = expense.category
    override val time: String = "${timeFormat.format(Date(expense.dateTime))} ${
        amPmFormat.format(Date(expense.dateTime)).uppercase()
    }"
    override val cardNameText: String = expense.cardName
}

/** IncomeEntity → TransactionCardInfo 변환 */
class IncomeTransactionCardInfo(
    private val income: IncomeEntity
) : TransactionCardInfo {
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.KOREA)
    private val amPmFormat = SimpleDateFormat("a", Locale.ENGLISH)

    override val title: String = income.description.ifBlank { income.type }
    override val subtitle: String = "${income.type} | ${timeFormat.format(Date(income.dateTime))}"
    override val amount: Int = income.amount
    override val isIncome: Boolean = true
    override val iconEmoji: String = "\uD83D\uDCB0"
    override val categoryTag: String = income.type
    override val time: String = "${timeFormat.format(Date(income.dateTime))} ${
        amPmFormat.format(Date(income.dateTime)).uppercase()
    }"
}
