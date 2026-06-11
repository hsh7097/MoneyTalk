package com.sanha.moneytalk.core.sms

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsPreFilterTest {

    private val preFilter = SmsPreFilter()

    @Test
    fun `tariff notices are treated as non payment`() {
        val samples = listOf(
            "[SKT MVNO] MMS/데이터 요율 안내\nMMS 220원 데이터 0.5KB당 0.25원 부가세 포함",
            "[KT안내] 국제/로밍 단가 안내\n음성 1분 120원, 데이터 1MB 550원",
            "[SKT MVNO] MMS(발신: 220원,수신:무료),데이터(0.25원/0.5KB)",
            "[KT안내] 한국으로 걸 때 1.98원/초, 받을 때 1.98원/초, 데이터 0.25원/0.5KB"
        )

        samples.forEach { body ->
            assertTrue("tariff notice should be filtered: $body", preFilter.isObviouslyNonPayment(body))
        }
    }

    @Test
    fun `payment samples with telecom store are not filtered by tariff rule`() {
        val body = listOf(
            "[Web발신]",
            "RE:신한 카드번호입력승인 홍길동님(1234) 04/24 13:45 18,700원 SK 세븐모바일"
        ).joinToString("\n")

        assertFalse(preFilter.isObviouslyNonPayment(body))
        assertFalse(preFilter.lacksPaymentRequirements(body))
    }

    @Test
    fun `card bill debit is kept for transaction record`() {
        val body = "[Web발신]\n우리카드결제 120,000원 출금 완료 잔액 500,000원"

        assertFalse(preFilter.isObviouslyNonPayment(body))
        assertFalse(preFilter.lacksPaymentRequirements(body))
    }

    @Test
    fun `standalone card cancel is kept for income classification`() {
        val body = "[Web발신]\n현대카드 MX Black 취소 하*현\n27,000원 일시불\n06/28 11:51\n주식회사위대"

        assertFalse(preFilter.isObviouslyNonPayment(body))
        assertFalse(preFilter.lacksPaymentRequirements(body))
    }

    @Test
    fun `card bill notice is filtered`() {
        val body = "[Web발신]\n이번 달 카드대금 결제예정 금액은 120,000원입니다"

        assertTrue(preFilter.isObviouslyNonPayment(body))
    }

    @Test
    fun `cashback deposit result notice is filtered even with income keyword`() {
        val body = """
            프렌즈 체크카드 캐시백 입금결과 안내
            05월 프렌즈 체크카드(6383) 결제금액에 대한 캐시백 2,248원이 계좌로 입금되었습니다.
            - 기본 캐시백 2,248원
            - 프로모션 캐시백 0원
        """.trimIndent()

        assertTrue(preFilter.isObviouslyNonPayment(body))
    }

    @Test
    fun `actual cashback deposit notification is not filtered`() {
        val body = """
            입금 2,248원
            프렌즈 체크카드 캐시백 → 입출금통장(2193)
            잔액 190,392원
        """.trimIndent()

        assertFalse(preFilter.isObviouslyNonPayment(body))
        assertFalse(preFilter.lacksPaymentRequirements(body))
    }

    @Test
    fun `polite card approval is not filtered only by sentence ending`() {
        val body = "[현대카드] 스타벅스 5,000원 이용하셨습니다"

        assertFalse(preFilter.isObviouslyNonPayment(body))
        assertFalse(preFilter.lacksPaymentRequirements(body))
    }
}
