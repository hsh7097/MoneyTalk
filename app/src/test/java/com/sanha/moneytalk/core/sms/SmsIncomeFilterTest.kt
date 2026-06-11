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

    @Test
    fun `kakaotalk deposit with withdrawal account text is classified as income`() {
        val body = "카카오톡 입금 50,000원 출금계좌 카카오뱅크"

        val (type, reason) = filter.classify(body)

        assertEquals(SmsType.INCOME, type)
        assertEquals("incomeKw[입금]", reason)
    }

    @Test
    fun `spaced payment cancel is classified as income`() {
        val body = "14,500원 결제 취소 KB국민체크 | 구글페이먼트코리아(일시불)"

        val (type, reason) = filter.classify(body)

        assertEquals(SmsType.INCOME, type)
        assertEquals("cancel", reason)
    }

    @Test
    fun `standalone card cancel is classified as income`() {
        val body = "[Web발신]\n현대카드 MX Black 취소 하*현\n27,000원 일시불\n06/28 11:51\n주식회사위대"

        val (type, reason) = filter.classify(body)

        assertEquals(SmsType.INCOME, type)
        assertEquals("cancel", reason)
    }

    @Test
    fun `cashback deposit result notice is skipped`() {
        val body = """
            프렌즈 체크카드 캐시백 입금결과 안내
            05월 프렌즈 체크카드(6383) 결제금액에 대한 캐시백 2,248원이 계좌로 입금되었습니다.
            - 기본 캐시백 2,248원
            - 프로모션 캐시백 0원
        """.trimIndent()

        val (type, reason) = filter.classify(body)

        assertEquals(SmsType.SKIP, type)
        assertEquals("nonTransactionNotice", reason)
    }

    @Test
    fun `actual cashback deposit notification is classified as income`() {
        val body = """
            입금 2,248원
            프렌즈 체크카드 캐시백 → 입출금통장(2193)
            잔액 190,392원
        """.trimIndent()

        val (type, reason) = filter.classify(body)

        assertEquals(SmsType.INCOME, type)
        assertEquals("incomeKw[입금]", reason)
    }

    @Test
    fun `polite bank deposit is classified as income`() {
        val body = "카카오뱅크 홍길동님으로부터 50,000원 입금되었습니다"

        val (type, reason) = filter.classify(body)

        assertEquals(SmsType.INCOME, type)
        assertEquals("incomeKw[입금]", reason)
    }
}
