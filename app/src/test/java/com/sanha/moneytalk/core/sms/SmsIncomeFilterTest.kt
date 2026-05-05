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
    fun `smile card approval is classified as payment`() {
        val body = "스마일카드승인 하*현 491,770원 일시불 05/06 01:16 G마켓_스마일카드 누적527,270원"

        val (type, reason) = filter.classify(body)

        assertEquals(SmsType.PAYMENT, type)
        assertEquals("paymentKw", reason)
    }

    @Test
    fun `card bill notice is skipped`() {
        val body = "[Web발신]\n이번 달 카드대금 결제예정 금액은 120,000원입니다"

        val (type, _) = filter.classify(body)

        assertEquals(SmsType.SKIP, type)
    }

    @Test
    fun `card cancellation notice with original use date is skipped`() {
        val body = "[KB국민카드] 6310 하*현님 쿠팡(쿠페이)-쿠 01월28일 이용건 02월02일 취소완료(-15,990원)"

        val (type, reason) = filter.classify(body)

        assertEquals(SmsType.SKIP, type)
        assertEquals("cancellationNotice", reason)
    }
}
