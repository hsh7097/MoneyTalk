package com.sanha.moneytalk.core.sms

import org.junit.Assert.assertEquals
import org.junit.Test

class AppNotificationTypeClassifierTest {

    @Test
    fun `deposit notification with withdrawal account text is income`() {
        val body = "카카오톡 입금 50,000원 출금계좌 카카오뱅크"

        val type = AppNotificationTypeClassifier.classify(body)

        assertEquals(SmsType.INCOME, type)
    }

    @Test
    fun `payment notification remains payment`() {
        val body = "스타벅스 5,000원 결제"

        val type = AppNotificationTypeClassifier.classify(body)

        assertEquals(SmsType.PAYMENT, type)
    }

    @Test
    fun `cancel notification is income`() {
        val body = "출금취소 5,000원"

        val type = AppNotificationTypeClassifier.classify(body)

        assertEquals(SmsType.INCOME, type)
    }
}
