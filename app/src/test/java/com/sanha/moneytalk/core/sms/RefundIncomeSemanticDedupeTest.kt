package com.sanha.moneytalk.core.sms

import com.sanha.moneytalk.core.database.entity.IncomeEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RefundIncomeSemanticDedupeTest {

    private val baseTime = 1_764_000_000_000L

    @Test
    fun `google play cancel notice and bank deposit are duplicate refund incomes`() {
        val deposit = income(
            source = "구글플레이",
            description = "구글플레이에서 입금",
            originalSms = """
                [Web발신]
                [KB]05/21 14:49
                801302**775
                구글플레이
                입금
                14,500
                잔액4,410,118
            """.trimIndent()
        )
        val cancelNotice = income(
            id = 2L,
            smsId = "income2",
            type = "환불",
            description = "환불",
            originalSms = "14,500원 결제 취소 KB국민체크 | 구글페이먼트코리아(일시불)"
        )

        val result = RefundIncomeSemanticDedupe.isPotentialDuplicate(
            candidate = deposit,
            existing = cancelNotice
        )

        assertTrue(result)
        assertTrue(RefundIncomeSemanticDedupe.shouldPreferCandidate(deposit, cancelNotice))
    }

    @Test
    fun `cancel notice and unrelated deposit are not duplicate even with same amount`() {
        val deposit = income(
            source = "급여",
            description = "급여에서 입금",
            originalSms = "급여 입금 14,500원"
        )
        val cancelNotice = income(
            id = 2L,
            smsId = "income2",
            type = "환불",
            description = "환불",
            originalSms = "14,500원 결제 취소 KB국민체크 | 구글페이먼트코리아(일시불)"
        )

        val result = RefundIncomeSemanticDedupe.isPotentialDuplicate(
            candidate = deposit,
            existing = cancelNotice
        )

        assertFalse(result)
    }

    @Test
    fun `two normal deposits are not refund duplicates`() {
        val first = income(source = "구글플레이", originalSms = "구글플레이 입금 14,500원")
        val second = income(
            id = 2L,
            source = "구글플레이",
            originalSms = "구글플레이 입금 14,500원",
            smsId = "income2"
        )

        val result = RefundIncomeSemanticDedupe.isPotentialDuplicate(first, second)

        assertFalse(result)
    }

    private fun income(
        id: Long = 1L,
        smsId: String = "income1",
        amount: Int = 14_500,
        type: String = "입금",
        source: String = "",
        description: String = type,
        originalSms: String,
        dateTime: Long = baseTime
    ): IncomeEntity {
        return IncomeEntity(
            id = id,
            smsId = smsId,
            amount = amount,
            type = type,
            source = source,
            description = description,
            isRecurring = false,
            dateTime = dateTime,
            originalSms = originalSms,
            senderAddress = "15889955",
            category = "미분류"
        )
    }
}
