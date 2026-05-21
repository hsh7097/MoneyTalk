package com.sanha.moneytalk.core.sms

/**
 * 금융 앱 알림 본문을 지출/수입 후보로 분류한다.
 *
 * 입금 알림에는 "출금계좌"처럼 출금 키워드가 함께 들어오는 경우가 있어
 * 취소/입금 계열을 일반 결제 힌트보다 먼저 판단한다.
 */
object AppNotificationTypeClassifier {

    private val paymentHints = listOf(
        "결제", "승인", "출금", "사용", "이용"
    )

    private val incomeHints = listOf(
        "입금", "이체입금", "송금받", "받았", "받으셨"
    )

    private val cancelHintPattern = Regex(
        """(?:출금|승인|결제|사용|이용)\s*취소|취소\s*(?:승인|완료|처리|환불)?|환불"""
    )

    fun classify(body: String): SmsType {
        if (cancelHintPattern.containsMatchIn(body)) {
            return SmsType.INCOME
        }
        if (incomeHints.any { body.contains(it, ignoreCase = true) }) {
            return SmsType.INCOME
        }
        if (paymentHints.any { body.contains(it, ignoreCase = true) }) {
            return SmsType.PAYMENT
        }
        return SmsType.SKIP
    }
}
