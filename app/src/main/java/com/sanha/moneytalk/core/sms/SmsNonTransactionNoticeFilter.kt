package com.sanha.moneytalk.core.sms

/**
 * 금액과 금융 키워드가 들어있지만 실제 거래가 아니라 결과/안내 문구인 알림을 거른다.
 *
 * 실제 입금/결제 알림은 보존해야 하므로 "니다" 같은 종결어만으로는 차단하지 않고,
 * 안내성 제목/문맥과 정산성 키워드가 함께 있는 경우만 고신뢰 비거래로 판단한다.
 */
object SmsNonTransactionNoticeFilter {

    private val noticePatterns = listOf(
        Regex(
            pattern = """(?=.*(?:입금\s*결과\s*안내|입금결과\s*안내|결과\s*안내))(?=.*(?:습니다|니다|입니다)).*""",
            options = setOf(RegexOption.DOT_MATCHES_ALL)
        ),
        Regex(
            pattern = """(?=.*(?:캐시백|캐쉬백))(?=.*(?:결제금액|기본\s*캐시백|프로모션\s*캐시백|입금\s*결과|입금결과))(?=.*(?:입금되었습니다|지급되었습니다|처리되었습니다|완료되었습니다|입니다)).*""",
            options = setOf(RegexOption.DOT_MATCHES_ALL)
        ),
        Regex(
            pattern = """(?=.*(?:안내|알림))(?=.*(?:습니다|니다|입니다))(?=.*(?:결제예정|출금예정|납입|납부|청구|명세서|이용대금|카드대금|대출|이자|수수료)).*""",
            options = setOf(RegexOption.DOT_MATCHES_ALL)
        )
    )

    fun isNonTransactionNotice(body: String): Boolean {
        return noticePatterns.any { it.containsMatchIn(body) }
    }
}
