package com.sanha.moneytalk.core.sms

import com.sanha.moneytalk.core.database.entity.ExpenseEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransactionSemanticDedupeTest {

    private val baseTime = 1_764_000_000_000L

    @Test
    fun `same amount card name store name and one minute window across app and sms is duplicate`() {
        val sms = expense(
            senderAddress = "15889955",
            body = "[우리카드] 1234 하*현 12,000원 승인 05/21 스타벅스",
            smsId = "sms"
        )
        val app = expense(
            senderAddress = "app:com.wooricard.smartapp",
            body = "우리카드 1234 승인\n스타벅스\n12,000원",
            dateTime = baseTime + 60_000L,
            smsId = "app"
        )

        val result = TransactionSemanticDedupe.isPotentialCrossSourceDuplicate(app, sms)

        assertTrue(result)
    }

    @Test
    fun `different card name is not duplicate`() {
        val sms = expense(
            senderAddress = "15889955",
            body = "[우리카드] 1234 하*현 12,000원 승인 05/21 스타벅스",
            smsId = "sms"
        )
        val app = expense(
            senderAddress = "app:com.wooricard.smartapp",
            body = "신한카드 승인\n스타벅스\n12,000원",
            dateTime = baseTime + 60_000L,
            smsId = "app",
            cardName = "신한카드"
        )

        val result = TransactionSemanticDedupe.isPotentialCrossSourceDuplicate(app, sms)

        assertFalse(result)
    }

    @Test
    fun `same app source is not cross source duplicate`() {
        val first = expense(
            senderAddress = "app:com.wooricard.smartapp",
            body = "우리카드 1234 승인\n스타벅스\n12,000원",
            smsId = "app1"
        )
        val second = expense(
            senderAddress = "app:com.wooricard.smartapp",
            body = "우리카드 1234 승인\n스타벅스\n12,000원",
            dateTime = baseTime + 60_000L,
            smsId = "app2"
        )

        val result = TransactionSemanticDedupe.isPotentialCrossSourceDuplicate(first, second)

        assertFalse(result)
    }

    @Test
    fun `different store name is not duplicate`() {
        val sms = expense(
            senderAddress = "15889955",
            body = "[우리카드] 스타벅스 12,000원 승인",
            smsId = "sms"
        )
        val app = expense(
            senderAddress = "app:com.wooricard.smartapp",
            body = "이디야 12,000원 결제",
            dateTime = baseTime + 60_000L,
            smsId = "app",
            storeName = "이디야"
        )

        val result = TransactionSemanticDedupe.isPotentialCrossSourceDuplicate(app, sms)

        assertFalse(result)
    }

    @Test
    fun `same one character store name across app and sms is duplicate`() {
        val sms = expense(
            amount = 11_000,
            storeName = "탄",
            cardName = "우리",
            senderAddress = "15889955",
            body = """
                ● 우리카드 이용안내
                우리(1690)승인
                하*현님
                11,000원 일시불
                06/04 12:35
                탄
                누적508,800원
            """.trimIndent(),
            smsId = "sms"
        )
        val app = expense(
            amount = 11_000,
            storeName = "탄",
            cardName = "우리",
            senderAddress = "app:com.wooricard.smartapp",
            body = """
                승인내역
                [일시불.승인(1690)]06/04 12:35
                11,000원 / 누적:508,800원
                탄
            """.trimIndent(),
            dateTime = baseTime + 22_000L,
            smsId = "app"
        )

        val result = TransactionSemanticDedupe.isPotentialCrossSourceDuplicate(app, sms)

        assertTrue(result)
    }

    @Test
    fun `legacy woori app fallback store is reparsed before duplicate comparison`() {
        val sms = expense(
            amount = 11_000,
            storeName = "탄",
            cardName = "우리",
            senderAddress = "15889955",
            body = """
                ● 우리카드 이용안내
                우리(1690)승인
                하*현님
                11,000원 일시불
                06/04 12:35
                탄
                누적508,800원
            """.trimIndent(),
            smsId = "sms"
        )
        val legacyApp = expense(
            amount = 11_000,
            storeName = "우리카드",
            cardName = "우리",
            senderAddress = "app:com.wooricard.smartapp",
            body = """
                승인내역
                [일시불.승인(1690)]06/04 12:35
                11,000원 / 누적:508,800원
                탄
            """.trimIndent(),
            dateTime = baseTime + 22_000L,
            smsId = "app"
        )

        val result = TransactionSemanticDedupe.isPotentialCrossSourceDuplicate(legacyApp, sms)

        assertTrue(result)
    }

    @Test
    fun `different amount is not duplicate`() {
        val sms = expense(
            senderAddress = "15889955",
            body = "[우리카드] 1234 하*현 12,000원 승인 05/21 스타벅스",
            smsId = "sms"
        )
        val app = expense(
            senderAddress = "app:com.wooricard.smartapp",
            body = "우리카드 1234 승인\n스타벅스\n13,000원",
            dateTime = baseTime + 60_000L,
            smsId = "app",
            amount = 13_000
        )

        val result = TransactionSemanticDedupe.isPotentialCrossSourceDuplicate(app, sms)

        assertFalse(result)
    }

    @Test
    fun `same amount card name and store name outside one minute window is not duplicate`() {
        val sms = expense(
            senderAddress = "15889955",
            body = "[우리카드] 1234 하*현 12,000원 승인 05/21 스타벅스",
            smsId = "sms"
        )
        val app = expense(
            senderAddress = "app:com.wooricard.smartapp",
            body = "우리카드 1234 승인\n스타벅스\n12,000원",
            dateTime = baseTime + TransactionSemanticDedupe.CROSS_SOURCE_WINDOW_MS + 1L,
            smsId = "app"
        )

        val result = TransactionSemanticDedupe.isPotentialCrossSourceDuplicate(app, sms)

        assertFalse(result)
    }

    @Test
    fun `app notification then sms is loaded once and sms replaces app record`() {
        val stored = mutableListOf<ExpenseEntity>()
        val app = expense(
            id = 1L,
            senderAddress = "app:com.wooricard.smartapp",
            body = "우리카드 1234 승인\n스타벅스\n12,000원",
            smsId = "app"
        )
        val sms = expense(
            id = 2L,
            senderAddress = "15889955",
            body = "[우리카드] 1234 하*현 12,000원 승인 05/21 스타벅스",
            dateTime = baseTime + 60_000L,
            smsId = "sms"
        )

        saveWithCrossSourceDedupe(stored, app)
        saveWithCrossSourceDedupe(stored, sms)

        assertEquals(1, stored.size)
        assertEquals("sms", stored.single().smsId)
        assertFalse(TransactionSemanticDedupe.isAppGenerated(stored.single()))
    }

    @Test
    fun `sms then app notification is loaded once and app record is skipped`() {
        val stored = mutableListOf<ExpenseEntity>()
        val sms = expense(
            id = 1L,
            senderAddress = "15889955",
            body = "[우리카드] 1234 하*현 12,000원 승인 05/21 스타벅스",
            smsId = "sms"
        )
        val app = expense(
            id = 2L,
            senderAddress = "app:com.wooricard.smartapp",
            body = "우리카드 1234 승인\n스타벅스\n12,000원",
            dateTime = baseTime + 60_000L,
            smsId = "app"
        )

        saveWithCrossSourceDedupe(stored, sms)
        saveWithCrossSourceDedupe(stored, app)

        assertEquals(1, stored.size)
        assertEquals("sms", stored.single().smsId)
        assertFalse(TransactionSemanticDedupe.isAppGenerated(stored.single()))
    }

    @Test
    fun `same card name store name amount with same card suffix is loaded once`() {
        val stored = mutableListOf<ExpenseEntity>()
        val sms = expense(
            id = 1L,
            senderAddress = "15889955",
            body = "[우리카드] 1234 하*현 12,000원 승인 05/21 스타벅스",
            smsId = "sms"
        )
        val app = expense(
            id = 2L,
            senderAddress = "app:com.wooricard.smartapp",
            body = "우리카드 1234 승인\n스타벅스\n12,000원",
            dateTime = baseTime + 60_000L,
            smsId = "app"
        )

        saveWithCrossSourceDedupe(stored, sms)
        saveWithCrossSourceDedupe(stored, app)

        assertEquals(1, stored.size)
        assertEquals("sms", stored.single().smsId)
    }

    @Test
    fun `same card name store name amount with different card suffix keeps both records`() {
        val stored = mutableListOf<ExpenseEntity>()
        val sms = expense(
            id = 1L,
            senderAddress = "15889955",
            body = "[우리카드] 1234 하*현 12,000원 승인 05/21 스타벅스",
            smsId = "sms"
        )
        val app = expense(
            id = 2L,
            senderAddress = "app:com.wooricard.smartapp",
            body = "우리카드 5678 승인\n스타벅스\n12,000원",
            dateTime = baseTime + 60_000L,
            smsId = "app"
        )

        saveWithCrossSourceDedupe(stored, sms)
        saveWithCrossSourceDedupe(stored, app)

        assertEquals(2, stored.size)
    }

    @Test
    fun `same card name store name amount without card suffix falls back to base conditions`() {
        val stored = mutableListOf<ExpenseEntity>()
        val sms = expense(
            id = 1L,
            senderAddress = "15889955",
            body = "[우리카드] 스타벅스 12,000원 승인",
            smsId = "sms"
        )
        val app = expense(
            id = 2L,
            senderAddress = "app:com.wooricard.smartapp",
            body = "우리카드 승인\n스타벅스\n12,000원",
            dateTime = baseTime + 60_000L,
            smsId = "app"
        )

        saveWithCrossSourceDedupe(stored, sms)
        saveWithCrossSourceDedupe(stored, app)

        assertEquals(1, stored.size)
        assertEquals("sms", stored.single().smsId)
    }

    @Test
    fun `card suffix extracts visible trailing card digits`() {
        assertEquals("1234", TransactionSemanticDedupe.extractCardSuffix("우리카드 1234 승인"))
        assertEquals("5678", TransactionSemanticDedupe.extractCardSuffix("1234-****-5678 승인"))
    }

    private fun saveWithCrossSourceDedupe(
        stored: MutableList<ExpenseEntity>,
        candidate: ExpenseEntity
    ) {
        val duplicate = TransactionSemanticDedupe.findPotentialCrossSourceDuplicate(
            candidate = candidate,
            existingExpenses = stored
        )
        when {
            duplicate == null -> stored += candidate
            TransactionSemanticDedupe.isAppGenerated(duplicate) &&
                !TransactionSemanticDedupe.isAppGenerated(candidate) -> {
                stored.removeAll { it.id == duplicate.id }
                stored += candidate
            }
        }
    }

    private fun expense(
        id: Long = 0L,
        amount: Int = 12_000,
        storeName: String = "스타벅스",
        cardName: String = "우리카드",
        senderAddress: String,
        body: String,
        smsId: String,
        dateTime: Long = baseTime
    ): ExpenseEntity {
        return ExpenseEntity(
            id = id,
            amount = amount,
            storeName = storeName,
            category = "카페/간식",
            cardName = cardName,
            dateTime = dateTime,
            originalSms = body,
            smsId = smsId,
            senderAddress = senderAddress
        )
    }
}
