package com.sanha.moneytalk.core.sms

import org.junit.Assert.assertEquals
import org.junit.Test

class SmsParserDeliveryCategoryTest {

    @Test
    fun inferCategoryReturnsDeliveryForDeliveryApps() {
        assertEquals("배달", SmsParser.inferCategory("우아한형제들", "카드 승인"))
        assertEquals("배달", SmsParser.inferCategory("배달의민족", "11,000원 결제"))
        assertEquals("배달", SmsParser.inferCategory("쿠팡이츠", "18,500원 결제"))
        assertEquals("배달", SmsParser.inferCategory("요기요", "결제 완료"))
    }

    @Test
    fun inferCategoryKeepsNonDeliveryFoodAsFood() {
        assertEquals("식비", SmsParser.inferCategory("맥도날드", "카드 승인"))
        assertEquals("식비", SmsParser.inferCategory("GS25", "카드 승인"))
    }
}
