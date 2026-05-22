package com.sanha.moneytalk.core.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class SmsIncomeParserTest {

    @Test
    fun `1월에 수신한 12월 환불 SMS는 전년도 날짜로 저장한다`() {
        val timestamp = timestamp(2026, 1, 2, 0, 5)
        val body = listOf(
            "[KB]12/31 23:50",
            "출금취소",
            "10,000원"
        ).joinToString("\n")

        val dateTime = SmsIncomeParser.extractDateTime(body, timestamp)

        assertEquals("2025-12-31 23:50", dateTime)
    }

    @Test
    fun `1월에 수신한 1월 환불 SMS는 같은 연도 날짜로 저장한다`() {
        val timestamp = timestamp(2026, 1, 5, 10, 30)
        val body = listOf(
            "[KB]01/05 10:29",
            "승인취소",
            "10,000원"
        ).joinToString("\n")

        val dateTime = SmsIncomeParser.extractDateTime(body, timestamp)

        assertEquals("2026-01-05 10:29", dateTime)
    }

    @Test
    fun `취소완료 SMS는 원 이용일이 아니라 취소완료일로 저장한다`() {
        val timestamp = timestamp(2026, 2, 2, 9, 3)
        val body = "[KB국민카드] 6310 하*현님 쿠팡(쿠페이)-쿠 01월28일 이용건 02월02일 취소완료(-15,990원)"

        val dateTime = SmsIncomeParser.extractDateTime(body, timestamp)

        assertEquals("2026-02-02 09:03", dateTime)
    }

    @Test
    fun `공백이 포함된 결제 취소 SMS는 환불 유형으로 파싱한다`() {
        val body = "14,500원 결제 취소 KB국민체크 | 구글페이먼트코리아(일시불)"

        val type = SmsIncomeParser.extractIncomeType(body)

        assertEquals("환불", type)
    }

    @Test
    fun `카드 취소 단독 문구는 환불 유형으로 파싱한다`() {
        val body = "[Web발신]\n현대카드 MX Black 취소 하*현\n27,000원 일시불\n06/28 11:51\n주식회사위대"

        val type = SmsIncomeParser.extractIncomeType(body)

        assertEquals("환불", type)
    }

    @Test
    fun `카카오뱅크 입금 알림은 금액이 아니라 송금인을 출처로 파싱한다`() {
        val body = "입금 100,000원\n하상현 → 입출금통장(9103)\n잔액 4,514,631원"

        val source = SmsIncomeParser.extractIncomeSource(body)

        assertEquals("하상현", source)
    }

    @Test
    fun `카카오뱅크 입금 알림은 금액 유형 출처 시간을 모두 파싱한다`() {
        val timestamp = timestamp(2026, 5, 22, 9, 35)
        val body = "입금 100,000원\n하상현 → 입출금통장(9103)\n잔액 4,514,631원"

        assertEquals(100000, SmsIncomeParser.extractIncomeAmount(body))
        assertEquals("입금", SmsIncomeParser.extractIncomeType(body))
        assertEquals("하상현", SmsIncomeParser.extractIncomeSource(body))
        assertEquals("2026-05-22 09:35", SmsIncomeParser.extractDateTime(body, timestamp))
    }

    @Test
    fun `한 줄 카카오뱅크 입금 알림도 송금인을 출처로 파싱한다`() {
        val body = "입금 100,000원 하상현 → 입출금통장(9103) 잔액 4,514,631원"

        val source = SmsIncomeParser.extractIncomeSource(body)

        assertEquals("하상현", source)
    }

    @Test
    fun `입금 뒤 금액은 송금인으로 사용하지 않는다`() {
        val body = "카카오톡 입금 50,000원 출금계좌 카카오뱅크"

        val source = SmsIncomeParser.extractIncomeSource(body)

        assertEquals("", source)
    }

    @Test
    fun `금액과 앱명은 저장된 수입 출처 보정 대상이다`() {
        assertTrue(SmsIncomeParser.isInvalidIncomeSource("100"))
        assertTrue(SmsIncomeParser.isInvalidIncomeSource("카카오톡"))
        assertTrue(SmsIncomeParser.isInvalidIncomeSource("50,000원"))
        assertFalse(SmsIncomeParser.isInvalidIncomeSource("하상현"))
    }

    private fun timestamp(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int
    ): Long {
        return Calendar.getInstance().apply {
            clear()
            set(year, month - 1, day, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
}
