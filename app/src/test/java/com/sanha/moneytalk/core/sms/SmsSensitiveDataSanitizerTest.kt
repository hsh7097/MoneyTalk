package com.sanha.moneytalk.core.sms

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsSensitiveDataSanitizerTest {

    @Test
    fun `direct identifiers are masked while transaction fields remain`() {
        val body = listOf(
            "[롯데카드] 홍길동님",
            "고객명",
            "김철수",
            "카드 1234****5678",
            "계좌 123456-12-123456",
            "04/24 13:45 스타벅스 12,300원 승인"
        ).joinToString("\n")

        val sanitized = SmsSensitiveDataSanitizer.sanitizeForExternalProcessing(body)

        assertFalse(sanitized.contains("홍길동"))
        assertFalse(sanitized.contains("김철수"))
        assertFalse(sanitized.contains("1234****5678"))
        assertFalse(sanitized.contains("123456-12-123456"))
        assertTrue(sanitized.contains("{USER_NAME}"))
        assertTrue(sanitized.contains("{ACCOUNT_OR_CARD}"))
        assertTrue(sanitized.contains("04/24 13:45"))
        assertTrue(sanitized.contains("스타벅스"))
        assertTrue(sanitized.contains("12,300원"))
    }

    @Test
    fun `masked names and long card numbers are removed`() {
        val body = "홍*동 회원님 1234567812345678 카드로 39,050원 결제"

        val sanitized = SmsSensitiveDataSanitizer.sanitizeForExternalProcessing(body)

        assertFalse(sanitized.contains("홍*동"))
        assertFalse(sanitized.contains("1234567812345678"))
        assertTrue(sanitized.contains("39,050원"))
    }

    @Test
    fun `calendar dates are not mistaken for account numbers`() {
        val body = "2026-07-11 04/24 13:45 1,000원 승인"

        val sanitized = SmsSensitiveDataSanitizer.sanitizeForExternalProcessing(body)

        assertTrue(sanitized.contains("2026-07-11"))
        assertTrue(sanitized.contains("04/24 13:45"))
        assertTrue(sanitized.contains("1,000원"))
    }

    @Test
    fun `same line names and space separated phone numbers are removed`() {
        val body = "성명: 박영희\n연락처 010 1234 5678\n편의점 8,900원 승인"

        val sanitized = SmsSensitiveDataSanitizer.sanitizeForExternalProcessing(body)

        assertFalse(sanitized.contains("박영희"))
        assertFalse(sanitized.contains("010 1234 5678"))
        assertTrue(sanitized.contains("편의점"))
        assertTrue(sanitized.contains("8,900원"))
    }
}
