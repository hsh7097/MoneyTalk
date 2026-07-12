package com.sanha.moneytalk.core.sms

import com.sanha.moneytalk.core.util.StatsExclusionClassifier

/**
 * 금액과 금융 키워드가 들어있지만 실제 거래가 아니라 결과/안내 문구인 알림을 거른다.
 *
 * 실제 입금/결제 알림은 보존해야 하므로 "니다" 같은 종결어만으로는 차단하지 않고,
 * 안내성 제목/문맥과 정산성 키워드가 함께 있는 경우만 고신뢰 비거래로 판단한다.
 */
object SmsNonTransactionNoticeFilter {

    private val aggregateSummaryPatterns = listOf(
        Regex(
            pattern = """(?=.*(?:교통카드|교통[-\s]?버스|후불하이패스))(?=.*(?:\d+|\{N\})\s*건).*""",
            options = setOf(RegexOption.DOT_MATCHES_ALL)
        ),
        Regex(
            pattern = """(?=.*KSNET)(?=.*마이장부)(?=.*(?:신용카드승인|매출접수))(?=.*(?:\d+|\{N\})\s*건).*""",
            options = setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        ),
        Regex(
            pattern = """(?=.*매출접수)(?=.*(?:\d+|\{N\})\s*건)(?=.*(?:교통|하이패스|기준|집계)).*""",
            options = setOf(RegexOption.DOT_MATCHES_ALL)
        )
    )

    private val noticePatterns = listOf(
        Regex(
            pattern = """(?<![\d,])0\s*원\s*(?:승인|결제|사용)"""
        ),
        Regex(
            pattern = """(?=.*\[[^\]\n]*(?:쇼핑|홈쇼핑)[^\]\n]*\])(?=.*[\d,]+원\s*/\s*[가-힣A-Za-z]+\s+\d{2,6}(?:-\d{2,6}){1,3})(?!.*(?:승인|출금|결제완료|입금완료|사용)).*""",
            options = setOf(RegexOption.DOT_MATCHES_ALL)
        ),
        Regex(
            pattern = """(?=.*(?:무료체험|가입\s*시|전원\s*지급|상품권\s*\d+만원\s*도착))(?=.*(?:신청|가입|혜택|지급|무료)).*""",
            options = setOf(RegexOption.DOT_MATCHES_ALL)
        ),
        Regex(
            pattern = """(?=.*(?:입금\s*결과\s*안내|입금결과\s*안내|결과\s*안내))(?=.*(?:습니다|니다|입니다)).*""",
            options = setOf(RegexOption.DOT_MATCHES_ALL)
        ),
        Regex(
            pattern = """(?=.*(?:캐시백|캐쉬백))(?=.*(?:결제금액|기본\s*캐시백|프로모션\s*캐시백|입금\s*결과|입금결과))(?=.*(?:입금되었습니다|지급되었습니다|처리되었습니다|완료되었습니다|입니다)).*""",
            options = setOf(RegexOption.DOT_MATCHES_ALL)
        ),
        Regex(
            pattern = """(?=.*(?:안내|알림))(?=.*(?:습니다|니다|입니다))(?=.*(?:결제예정|출금예정|납입|납부|청구|명세서|이용대금|이용금액|카드대금|대출|이자|수수료)).*""",
            options = setOf(RegexOption.DOT_MATCHES_ALL)
        )
    )

    fun isNonTransactionNotice(body: String): Boolean {
        if (StatsExclusionClassifier.isCardBillDebitText(body, requireWonAmount = true)) {
            return false
        }
        return aggregateSummaryPatterns.any { it.containsMatchIn(body) } ||
            noticePatterns.any { it.containsMatchIn(body) }
    }
}
