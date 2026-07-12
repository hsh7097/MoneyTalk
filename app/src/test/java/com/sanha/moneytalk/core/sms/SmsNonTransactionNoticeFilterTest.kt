package com.sanha.moneytalk.core.sms

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsNonTransactionNoticeFilterTest {

    @Test
    fun `aggregate summaries are non transactions`() {
        val samples = listOf(
            "교통카드 후불교통 4월 이용내역 18건 45,000원 안내",
            "교통-버스 이번 달 {N}건 {AMOUNT}원 집계",
            "KSNET 마이장부 신용카드승인 12건 340,000원 매출접수 집계",
            "매출접수 8건 125,000원 하이패스 기준 집계"
        )

        samples.forEach { body ->
            assertTrue("aggregate summary must be non-transactional: $body", SmsNonTransactionNoticeFilter.isNonTransactionNotice(body))
        }
    }

    @Test
    fun `completed card bill debit is a transaction`() {
        val body = "[롯데카드] 홍길동님, 4월 결제대금 120,000원 중 100,000원 04/24 출금되었습니다."

        assertFalse(SmsNonTransactionNoticeFilter.isNonTransactionNotice(body))
    }

    @Test
    fun `scheduled card bill notice is not a transaction`() {
        val samples = listOf(
            "[롯데카드] 4월 결제대금 120,000원 출금예정 안내입니다.",
            "[롯데카드] 4월 이용금액 120,000원 출금예정 안내입니다."
        )

        samples.forEach { body ->
            assertTrue(SmsNonTransactionNoticeFilter.isNonTransactionNotice(body))
        }
    }

    @Test
    fun `free trial gift promotion is not a transaction`() {
        val body = "[상품권 5만원 도착] 서비스 신청 시 상품권 전원 지급, 0원 무료체험 가입 혜택"

        assertTrue(SmsNonTransactionNoticeFilter.isNonTransactionNotice(body))
    }

    @Test
    fun `zero won approval is not a transaction`() {
        val body = "가맹점\n0원 승인\n고객 롯데법인1234\n일시불 05/09 19:58\n누적250,000원"

        assertTrue(SmsNonTransactionNoticeFilter.isNonTransactionNotice(body))
    }

    @Test
    fun `shopping deposit instruction without completion is not a transaction`() {
        val body = "[Web발신]\n[롯데홈쇼핑] 49,000원/ 농협 123456-12-123456"

        assertTrue(SmsNonTransactionNoticeFilter.isNonTransactionNotice(body))
    }

    @Test
    fun `nonzero approvals and completed shopping debit remain transactions`() {
        val samples = listOf(
            "가맹점 10원 승인 05/09 19:58",
            "가맹점 1,000원 승인 05/09 19:58",
            "[롯데홈쇼핑] 49,000원/ 농협 123456-12-123456 출금 완료"
        )

        samples.forEach { body ->
            assertFalse(SmsNonTransactionNoticeFilter.isNonTransactionNotice(body))
        }
    }
}
