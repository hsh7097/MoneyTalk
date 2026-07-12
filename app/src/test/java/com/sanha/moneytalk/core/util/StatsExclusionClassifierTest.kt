package com.sanha.moneytalk.core.util

import com.sanha.moneytalk.core.database.entity.ExpenseEntity
import com.sanha.moneytalk.core.model.Category
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StatsExclusionClassifierTest {

    @Test
    fun cardBillDebit_isExcludedFromStats() {
        val expense = baseExpense(
            storeName = "신한카드",
            category = Category.TRANSFER_CARD.displayName,
            originalSms = "[신한] 카드대금 120,000원 자동이체 출금"
        )

        assertTrue(StatsExclusionClassifier.shouldExcludeExpense(expense))
    }

    @Test
    fun cardBillKeywordDebit_isExcludedFromStats() {
        val expense = baseExpense(
            storeName = "우리카드",
            category = Category.ETC.displayName,
            originalSms = "[우리은행] 우리카드결제 120,000원 출금"
        )

        assertTrue(StatsExclusionClassifier.shouldExcludeExpense(expense))
    }

    @Test
    fun completedStructuredCardBillDebit_isExcludedFromStats() {
        val expense = baseExpense(
            storeName = "롯데",
            category = Category.ETC.displayName,
            originalSms = "[롯데카드] 홍길동님, 4월 결제대금 120,000원 중 100,000원 04/24 출금되었습니다."
        )

        assertTrue(StatsExclusionClassifier.shouldExcludeExpense(expense))
    }

    @Test
    fun completedUsageAmountDebit_isExcludedFromStats() {
        val expense = baseExpense(
            storeName = "롯데법인",
            category = Category.ETC.displayName,
            originalSms = "이용금액이 기업은행에서 출금됐어요. 출금액: 100,000원"
        )

        assertTrue(StatsExclusionClassifier.shouldExcludeExpense(expense))
    }

    @Test
    fun scheduledCardBillNotice_isNotCompletedDebit() {
        val body = "[롯데카드] 4월 결제대금 120,000원 출금예정 안내입니다."

        assertFalse(StatsExclusionClassifier.isCardBillDebitText(body, requireWonAmount = true))
    }

    @Test
    fun cardApproval_isNotExcludedFromStats() {
        val expense = baseExpense(
            storeName = "스타벅스",
            category = Category.CAFE_SNACK.displayName,
            originalSms = "[신한] 스타벅스 5,000원 일시불 승인"
        )

        assertFalse(StatsExclusionClassifier.shouldExcludeExpense(expense))
    }

    private fun baseExpense(
        storeName: String,
        category: String,
        originalSms: String
    ): ExpenseEntity {
        return ExpenseEntity(
            amount = 1_000,
            storeName = storeName,
            category = category,
            cardName = "신한카드",
            dateTime = 0L,
            originalSms = originalSms,
            smsId = "test_sms"
        )
    }
}
