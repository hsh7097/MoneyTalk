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
    fun `completed card bill debit notification remains payment`() {
        val body = "[롯데카드] 홍길동님, 4월 결제대금 120,000원 중 100,000원 04/24 출금되었습니다."

        val type = AppNotificationTypeClassifier.classify(body)

        assertEquals(SmsType.PAYMENT, type)
    }

    @Test
    fun `aggregate transaction summary notifications are skipped`() {
        val samples = listOf(
            "교통카드 후불교통 4월 이용내역 18건 45,000원 안내",
            "KSNET 마이장부 신용카드승인 12건 340,000원 매출접수 집계",
            "매출접수 8건 125,000원 교통카드 기준 집계"
        )

        samples.forEach { body ->
            assertEquals("aggregate summary type: $body", SmsType.SKIP, AppNotificationTypeClassifier.classify(body))
        }
    }

    @Test
    fun `zero approval and incomplete deposit notifications are skipped`() {
        val samples = listOf(
            "가맹점 0원 승인 고객 롯데법인1234 누적250,000원",
            "[롯데홈쇼핑] 49,000원/ 농협 123456-12-123456"
        )

        samples.forEach { body ->
            assertEquals(SmsType.SKIP, AppNotificationTypeClassifier.classify(body))
        }
    }

    @Test
    fun `cancel notification is income`() {
        val body = "출금취소 5,000원"

        val type = AppNotificationTypeClassifier.classify(body)

        assertEquals(SmsType.INCOME, type)
    }

    @Test
    fun `spaced payment cancel notification is income`() {
        val body = "14,500원 결제 취소 KB국민체크 | 구글페이먼트코리아(일시불)"

        val type = AppNotificationTypeClassifier.classify(body)

        assertEquals(SmsType.INCOME, type)
    }

    @Test
    fun `standalone card cancel notification is income`() {
        val body = "현대카드 MX Black 취소 하*현 27,000원 일시불 06/28 11:51 주식회사위대"

        val type = AppNotificationTypeClassifier.classify(body)

        assertEquals(SmsType.INCOME, type)
    }

    @Test
    fun `cashback deposit result notice is skipped`() {
        val body = """
            프렌즈 체크카드 캐시백 입금결과 안내
            05월 프렌즈 체크카드(6383) 결제금액에 대한 캐시백 2,248원이 계좌로 입금되었습니다.
            - 기본 캐시백 2,248원
            - 프로모션 캐시백 0원
        """.trimIndent()

        val type = AppNotificationTypeClassifier.classify(body)

        assertEquals(SmsType.SKIP, type)
    }

    @Test
    fun `actual cashback deposit notification remains income`() {
        val body = """
            입금 2,248원
            프렌즈 체크카드 캐시백 → 입출금통장(2193)
            잔액 190,392원
        """.trimIndent()

        val type = AppNotificationTypeClassifier.classify(body)

        assertEquals(SmsType.INCOME, type)
    }
}
