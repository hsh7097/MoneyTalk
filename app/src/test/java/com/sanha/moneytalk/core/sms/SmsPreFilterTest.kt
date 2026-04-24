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
}
