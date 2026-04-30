package com.sanha.moneytalk.core.util

import com.sanha.moneytalk.core.database.entity.ExpenseEntity
import com.sanha.moneytalk.core.model.Category

/**
 * 기록은 보존하되 소비 통계에서 제외해야 하는 거래를 판별한다.
 *
 * 카드대금 납부는 이미 카드 승인 시점에 소비로 잡힌 금액을 상환하는 거래라서,
 * 신규 SMS 저장 시 기본적으로 통계 제외 처리한다.
 */
object StatsExclusionClassifier {

    private val cardBillKeywords = listOf(
        "카드대금",
        "카드 대금",
        "결제대금",
        "결제 대금",
        "이용대금",
        "이용 대금",
        "청구금액",
        "신용카드대금",
        "카드결제",
        "카드 결제"
    ).map { it.lowercase() }

    private val settlementKeywords = listOf(
        "출금",
        "자동이체",
        "납부",
        "결제완료",
        "인출"
    ).map { it.lowercase() }

    private val cardUsageKeywords = listOf(
        "승인",
        "일시불",
        "할부",
        "가맹점",
        "체크카드출금"
    ).map { it.lowercase() }

    private val noticeKeywords = listOf(
        "예정",
        "명세서",
        "청구서",
        "납부안내",
        "결제일"
    ).map { it.lowercase() }

    private val amountWithWonPattern = Regex("""[\d,]+원""")

    fun isCardBillDebitText(text: String, requireWonAmount: Boolean = false): Boolean {
        val normalized = text.lowercase()
        val hasCardBill = cardBillKeywords.any { normalized.contains(it) }
        val hasSettlement = settlementKeywords.any { normalized.contains(it) }
        val hasCardUsage = cardUsageKeywords.any { normalized.contains(it) }
        val isNotice = noticeKeywords.any { normalized.contains(it) }
        val hasAmount = !requireWonAmount || amountWithWonPattern.containsMatchIn(text)
        return hasCardBill && hasSettlement && !hasCardUsage && !isNotice && hasAmount
    }

    fun shouldExcludeExpense(expense: ExpenseEntity): Boolean {
        if (expense.category == Category.TRANSFER_CARD.displayName) {
            return true
        }

        val text = listOf(
            expense.originalSms,
            expense.storeName,
            expense.category,
            expense.cardName
        ).joinToString(separator = " ").lowercase()

        return isCardBillDebitText(text)
    }
}
