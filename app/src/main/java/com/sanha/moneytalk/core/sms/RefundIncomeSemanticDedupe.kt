package com.sanha.moneytalk.core.sms

import com.sanha.moneytalk.core.database.entity.IncomeEntity
import kotlin.math.abs

/**
 * 결제 취소 알림과 실제 입금 알림이 같은 환불을 각각 저장하는 것을 방지한다.
 */
object RefundIncomeSemanticDedupe {

    const val DEFAULT_WINDOW_MS: Long = 3L * 24 * 60 * 60 * 1000

    private val refundHintPattern = Regex(
        """(?:출금|승인|결제|사용|이용)\s*취소|취소\s*(?:승인|완료|처리|환불)?|환불"""
    )
    private val tokenSplitPattern = Regex("""[^\p{L}\p{N}]+""")
    private val ignoredTokenParts = listOf(
        "web발신", "입금", "출금", "결제", "취소", "승인", "환불",
        "잔액", "일시불", "할부", "카드", "체크", "국민", "신한",
        "우리", "삼성", "현대", "롯데", "하나", "농협", "비씨", "bc", "kb"
    )

    fun isPotentialDuplicate(
        candidate: IncomeEntity,
        existing: IncomeEntity,
        windowMs: Long = DEFAULT_WINDOW_MS
    ): Boolean {
        if (candidate.smsId != null && candidate.smsId == existing.smsId) return false
        if (candidate.id != 0L && candidate.id == existing.id) return false
        if (candidate.amount != existing.amount) return false
        if (abs(candidate.dateTime - existing.dateTime) > windowMs) return false

        val candidateRefund = isRefundLike(candidate)
        val existingRefund = isRefundLike(existing)
        if (!candidateRefund && !existingRefund) return false

        return areMerchantTokensRelated(candidate, existing)
    }

    fun shouldPreferCandidate(
        candidate: IncomeEntity,
        duplicate: IncomeEntity
    ): Boolean {
        return !isRefundNotice(candidate) && isRefundNotice(duplicate)
    }

    fun isRefundLike(entity: IncomeEntity): Boolean {
        return refundHintPattern.containsMatchIn(comparisonText(entity))
    }

    fun isRefundNotice(entity: IncomeEntity): Boolean {
        return refundHintPattern.containsMatchIn(entity.originalSms.orEmpty())
    }

    private fun areMerchantTokensRelated(
        candidate: IncomeEntity,
        existing: IncomeEntity
    ): Boolean {
        val candidateTokens = merchantTokens(candidate)
        val existingTokens = merchantTokens(existing)
        if (candidateTokens.isEmpty() || existingTokens.isEmpty()) return false

        return candidateTokens.any { candidateToken ->
            existingTokens.any { existingToken ->
                candidateToken == existingToken ||
                    candidateToken.contains(existingToken) ||
                    existingToken.contains(candidateToken) ||
                    commonPrefixLength(candidateToken, existingToken) >= 2
            }
        }
    }

    private fun merchantTokens(entity: IncomeEntity): Set<String> {
        return comparisonText(entity)
            .split(tokenSplitPattern)
            .map(::normalizeToken)
            .filter { token ->
                token.length >= 2 &&
                    token.any { it.isLetter() } &&
                    token.none { it.isDigit() } &&
                    ignoredTokenParts.none { ignored -> token.contains(ignored) }
            }
            .toSet()
    }

    private fun comparisonText(entity: IncomeEntity): String {
        return listOf(
            entity.source,
            entity.description,
            entity.originalSms.orEmpty()
        ).joinToString(" ")
    }

    private fun normalizeToken(value: String): String {
        return value.lowercase()
            .replace(Regex("""[\s\p{Punct}]"""), "")
    }

    private fun commonPrefixLength(first: String, second: String): Int {
        val limit = minOf(first.length, second.length)
        var count = 0
        while (count < limit && first[count] == second[count]) {
            count++
        }
        return count
    }
}
