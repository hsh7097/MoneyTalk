package com.sanha.moneytalk.core.sms

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class SmsTransactionDateResolverTest {

    @Test
    fun `1월 수신 12월 거래 날짜는 전년도 기준으로 해석한다`() {
        val smsTimestamp = timestamp(2026, 1, 2, 0, 5)

        val dateTime = SmsTransactionDateResolver.extractDateTime(
            message = "[KB]12/31 23:50\n출금취소\n10,000원",
            smsTimestamp = smsTimestamp
        )

        assertEquals("2025-12-31 23:50", dateTime)
    }

    @Test
    fun `캡처 그룹 날짜도 같은 연도 보정 규칙을 사용한다`() {
        val smsTimestamp = timestamp(2026, 1, 2, 0, 5)

        val dateTime = SmsTransactionDateResolver.normalizeCapturedDateTime(
            raw = "12/31 23:50",
            smsTimestamp = smsTimestamp
        )

        assertEquals("2025-12-31 23:50", dateTime)
    }

    @Test
    fun `본문 앞의 무효 날짜 후보는 건너뛰고 뒤의 정상 날짜를 사용한다`() {
        val smsTimestamp = timestamp(2026, 1, 2, 0, 5)

        val dateTime = SmsTransactionDateResolver.extractDateTime(
            message = "[카드 12-99]\n12/31 23:50\n출금취소\n10,000원",
            smsTimestamp = smsTimestamp
        )

        assertEquals("2025-12-31 23:50", dateTime)
    }

    @Test
    fun `캡처 문자열 앞의 무효 날짜시간 후보는 건너뛰고 뒤의 정상 날짜시간을 사용한다`() {
        val smsTimestamp = timestamp(2026, 1, 2, 0, 5)

        val dateTime = SmsTransactionDateResolver.normalizeCapturedDateTime(
            raw = "99/99 99:99 12/31 23:50",
            smsTimestamp = smsTimestamp
        )

        assertEquals("2025-12-31 23:50", dateTime)
    }

    @Test
    fun `본문의 전체 날짜시간은 SMS 수신 연도와 무관하게 우선 사용한다`() {
        val smsTimestamp = timestamp(2026, 1, 2, 0, 5)

        val dateTime = SmsTransactionDateResolver.extractDateTime(
            message = "[KB]\n2025-02-01 23:50\n출금취소\n10,000원",
            smsTimestamp = smsTimestamp
        )

        assertEquals("2025-02-01 23:50", dateTime)
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
