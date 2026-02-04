package com.sanha.moneytalk.util

object SmsParser {

    // 카드사 키워드 목록
    private val cardKeywords = listOf(
        "KB국민", "국민카드", "KB카드",
        "신한", "신한카드",
        "삼성", "삼성카드",
        "현대", "현대카드",
        "롯데", "롯데카드",
        "하나", "하나카드",
        "우리", "우리카드",
        "NH", "농협", "NH카드",
        "BC", "BC카드",
        "씨티", "시티", "Citi",
        "카카오뱅크", "카카오페이",
        "토스", "토스뱅크",
        "케이뱅크"
    )

    // 결제 관련 키워드
    private val paymentKeywords = listOf(
        "결제", "승인", "사용", "출금", "이용"
    )

    // 제외할 키워드 (광고, 안내 등)
    private val excludeKeywords = listOf(
        "광고", "안내", "홍보", "이벤트", "혜택", "포인트 적립",
        "한도", "실적", "명세서", "청구", "결제일"
    )

    /**
     * 카드 결제 문자인지 확인
     */
    fun isCardPaymentSms(message: String): Boolean {
        // 제외 키워드가 있으면 false
        if (excludeKeywords.any { message.contains(it) }) {
            return false
        }

        // 카드사 키워드가 있고, 결제 관련 키워드가 있으면 true
        val hasCardKeyword = cardKeywords.any { message.contains(it) }
        val hasPaymentKeyword = paymentKeywords.any { message.contains(it) }

        // 금액 패턴 확인 (숫자+원 또는 숫자+,+원)
        val amountPattern = Regex("""[\d,]+원""")
        val hasAmount = amountPattern.containsMatchIn(message)

        return hasCardKeyword && hasPaymentKeyword && hasAmount
    }

    /**
     * 문자에서 카드사 추출
     */
    fun extractCardName(message: String): String {
        for (keyword in cardKeywords) {
            if (message.contains(keyword)) {
                return keyword
            }
        }
        return "기타"
    }

    /**
     * 문자에서 금액 추출 (간단 파싱)
     */
    fun extractAmount(message: String): Int? {
        val amountPattern = Regex("""([\d,]+)원""")
        val match = amountPattern.find(message)
        return match?.groupValues?.get(1)?.replace(",", "")?.toIntOrNull()
    }

    /**
     * SMS ID 생성 (중복 방지용)
     */
    fun generateSmsId(address: String, body: String, date: Long): String {
        return "${address}_${date}_${body.hashCode()}"
    }
}
