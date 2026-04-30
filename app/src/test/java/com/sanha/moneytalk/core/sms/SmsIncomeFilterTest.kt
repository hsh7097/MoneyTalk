package com.sanha.moneytalk.core.sms

import org.junit.Assert.assertEquals
import org.junit.Test

class SmsIncomeFilterTest {

    private val filter = SmsIncomeFilter()

    @Test
    fun `card bill debit is classified as payment`() {
        val body = "[Web발신]\n우리카드결제 120,000원 출금 완료 잔액 500,000원"

        val (type, reason) = filter.classify(body)

        assertEquals(SmsType.PAYMENT, type)
        assertEquals("cardBillDebit", reason)
    }

    @Test
    fun `card bill notice is skipped`() {
        val body = "[Web발신]\n이번 달 카드대금 결제예정 금액은 120,000원입니다"

        val (type, _) = filter.classify(body)

        assertEquals(SmsType.SKIP, type)
    }
}
