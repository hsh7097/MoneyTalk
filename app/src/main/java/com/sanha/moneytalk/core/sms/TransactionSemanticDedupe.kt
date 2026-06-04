package com.sanha.moneytalk.core.sms

import com.sanha.moneytalk.core.database.entity.ExpenseEntity
import kotlin.math.abs

/**
 * SMS와 앱 알림처럼 smsId가 달라지는 소스 간 동일 거래를 비교한다.
 *
 * 카드 suffix는 본문에 노출되는 카드번호 끝자리 또는 마스킹 번호 조각이다.
 * 양쪽 본문에서 모두 추출되면 반드시 같아야 중복으로 본다.
 */
object TransactionSemanticDedupe {

    const val CROSS_SOURCE_WINDOW_MS: Long = 60 * 1000L
    private const val APP_ADDRESS_PREFIX = "app:"
    private val cardSuffixPatterns = listOf(
        Regex("""(?<!\d)(\d{3,4})[-\s]?\*{2,}[-\s]?(\d{2,4})(?!\d)"""),
        Regex("""(?<!\d)(\d{3,4})[-\s]?[xX]{2,}[-\s]?(\d{2,4})(?!\d)"""),
        Regex("""(?:카드|CARD|체크|신용|우리카드|국민카드|신한카드|삼성카드|현대카드|롯데카드|하나카드|농협카드|NH카드|BC카드|비씨카드)[\s\])(:：\-]{0,8}(\d{3,4})(?![\d,])""", RegexOption.IGNORE_CASE)
    )

    fun isAppGenerated(entity: ExpenseEntity): Boolean {
        return entity.senderAddress.startsWith(APP_ADDRESS_PREFIX)
    }

    fun isPotentialCrossSourceDuplicate(
        candidate: ExpenseEntity,
        existing: ExpenseEntity
    ): Boolean {
        if (candidate.smsId == existing.smsId) return false
        if (candidate.id != 0L && candidate.id == existing.id) return false
        if (isAppGenerated(candidate) == isAppGenerated(existing)) return false
        if (candidate.amount != existing.amount) return false

        val timeDiff = abs(candidate.dateTime - existing.dateTime)
        if (timeDiff > CROSS_SOURCE_WINDOW_MS) return false

        if (!hasSameCardSuffixWhenPresent(candidate, existing)) return false

        return hasSameMeaningfulCardName(candidate, existing) &&
            hasSameMeaningfulStoreName(candidate, existing)
    }

    fun findPotentialCrossSourceDuplicate(
        candidate: ExpenseEntity,
        existingExpenses: List<ExpenseEntity>
    ): ExpenseEntity? {
        return existingExpenses.firstOrNull { existing ->
            isPotentialCrossSourceDuplicate(candidate, existing)
        }
    }

    fun extractCardSuffix(body: String): String? {
        for (pattern in cardSuffixPatterns) {
            val match = pattern.find(body) ?: continue
            val suffix = match.groupValues
                .drop(1)
                .map { it.filter(Char::isDigit) }
                .lastOrNull { it.length in 3..4 }
                ?: continue
            return suffix
        }
        return null
    }

    private fun hasSameCardSuffixWhenPresent(
        candidate: ExpenseEntity,
        existing: ExpenseEntity
    ): Boolean {
        val candidateSuffix = extractCardSuffix(candidate.originalSms)
        val existingSuffix = extractCardSuffix(existing.originalSms)
        if (candidateSuffix == null || existingSuffix == null) return true
        return candidateSuffix == existingSuffix
    }

    private fun hasSameMeaningfulCardName(
        candidate: ExpenseEntity,
        existing: ExpenseEntity
    ): Boolean {
        val candidateCard = normalizeToken(candidate.cardName)
        val existingCard = normalizeToken(existing.cardName)
        return candidateCard.length >= 2 && candidateCard == existingCard
    }

    private fun hasSameMeaningfulStoreName(
        candidate: ExpenseEntity,
        existing: ExpenseEntity
    ): Boolean {
        val candidateStore = normalizeStoreToken(candidate)
        val existingStore = normalizeStoreToken(existing)
        return candidateStore.isNotBlank() && candidateStore == existingStore
    }

    private fun normalizeStoreToken(entity: ExpenseEntity): String {
        val parsedStoreName = if (
            isAppGenerated(entity) &&
            isLikelyAppLabelStore(entity.storeName)
        ) {
            AppNotificationTransactionParser.parseExpense(
                body = entity.originalSms,
                appLabel = entity.storeName,
                packageName = entity.senderAddress.removePrefix(APP_ADDRESS_PREFIX)
            )?.storeName
        } else {
            null
        }
        return normalizeToken(parsedStoreName?.takeIf { it.isNotBlank() } ?: entity.storeName)
    }

    private fun isLikelyAppLabelStore(storeName: String): Boolean {
        val normalized = normalizeToken(storeName)
        return appLabelStoreKeywords.any { normalized.contains(it) }
    }

    private fun normalizeToken(value: String): String {
        return value.lowercase()
            .replace(Regex("""[\s\p{Punct}]"""), "")
    }

    private val appLabelStoreKeywords = listOf(
        "카드",
        "은행",
        "뱅크"
    )
}
