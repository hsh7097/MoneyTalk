package com.sanha.moneytalk.core.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class AppNotificationTransactionParserTest {

    @Test
    fun `multiline app notification extracts store and amount`() {
        val body = """
            카카오뱅크
            체크카드 결제
            스타벅스
            5,000원
            잔액 120,000원
        """.trimIndent()

        val result = AppNotificationTransactionParser.parseExpense(
            body = body,
            appLabel = "카카오뱅크",
            packageName = "com.kakaobank.channel"
        )

        assertNotNull(result)
        assertEquals(5_000, result?.amount)
        assertEquals("스타벅스", result?.storeName)
        assertEquals("카페/간식", result?.category)
        assertEquals("카카오뱅크", result?.cardName)
    }

    @Test
    fun `single line app notification extracts store before amount`() {
        val body = "스타벅스에서 5,000원 결제"

        val result = AppNotificationTransactionParser.parseExpense(
            body = body,
            appLabel = "카카오뱅크",
            packageName = "com.kakaobank.channel"
        )

        assertNotNull(result)
        assertEquals(5_000, result?.amount)
        assertEquals("스타벅스", result?.storeName)
    }

    @Test
    fun `single line with app label extracts merchant token`() {
        val body = "카카오뱅크 체크카드 결제 스타벅스 5,000원"

        val result = AppNotificationTransactionParser.parseExpense(
            body = body,
            appLabel = "카카오뱅크",
            packageName = "com.kakaobank.channel"
        )

        assertNotNull(result)
        assertEquals(5_000, result?.amount)
        assertEquals("스타벅스", result?.storeName)
    }

    @Test
    fun `balance amount is not selected as transaction amount`() {
        val body = """
            카카오뱅크
            스타벅스 5,000원 결제
            잔액 120,000원
        """.trimIndent()

        val result = AppNotificationTransactionParser.parseExpense(
            body = body,
            appLabel = "카카오뱅크",
            packageName = "com.kakaobank.channel"
        )

        assertNotNull(result)
        assertEquals(5_000, result?.amount)
        assertEquals("스타벅스", result?.storeName)
    }

    @Test
    fun `kakaobank withdrawal extracts account target as store`() {
        val body = """
            출금 30,000원
            입출금통장(2193) → 탄
            잔액 795,430원
        """.trimIndent()

        val result = AppNotificationTransactionParser.parseExpense(
            body = body,
            appLabel = "카카오뱅크",
            packageName = "com.kakaobank.channel"
        )

        assertNotNull(result)
        assertEquals(30_000, result?.amount)
        assertEquals("탄", result?.storeName)
    }

    @Test
    fun `kakaobank withdrawal preserves card target as store`() {
        val body = """
            출금 1,450,770원
            입출금통장(2193) → 하상현현대카드
            잔액 825,430원
        """.trimIndent()

        val result = AppNotificationTransactionParser.parseExpense(
            body = body,
            appLabel = "카카오뱅크",
            packageName = "com.kakaobank.channel"
        )

        assertNotNull(result)
        assertEquals(1_450_770, result?.amount)
        assertEquals("하상현현대카드", result?.storeName)
    }

    @Test
    fun `single line kakaobank withdrawal trims balance after account target`() {
        val body = "출금 1,450,770원 입출금통장(2193) → 하상현현대카드 잔액 825,430원"

        val result = AppNotificationTransactionParser.parseExpense(
            body = body,
            appLabel = "카카오뱅크",
            packageName = "com.kakaobank.channel"
        )

        assertNotNull(result)
        assertEquals(1_450_770, result?.amount)
        assertEquals("하상현현대카드", result?.storeName)
    }
}
