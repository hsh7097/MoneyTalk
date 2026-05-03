package com.sanha.moneytalk.core.sms

import org.junit.Assert.assertEquals
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
