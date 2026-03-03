package com.sanha.moneytalk.core.sms2

import com.sanha.moneytalk.core.database.SmsRegexRuleRepository
import com.sanha.moneytalk.core.database.entity.SmsRegexRuleEntity
import com.sanha.moneytalk.core.model.SmsAnalysisResult
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * sender 기반 regex 룰 매칭기
 *
 * 결제 후보 SMS를 발신번호 기준 ACTIVE 룰과 매칭하여 빠르게 파싱합니다.
 * 매칭 실패건은 기존 임베딩/LLM 파이프라인으로 폴백합니다.
 */
@Singleton
class SmsRegexRuleMatcher @Inject constructor(
    private val ruleRepository: SmsRegexRuleRepository
) {
    data class RuleMatchResult(
        val matched: List<SmsParseResult>,
        val unmatched: List<SmsInput>
    )

    companion object {
        private val NON_DIGIT_PATTERN = Regex("""[^\d]""")
        private val DATE_PATTERN = Regex("""(\d{1,2})[/.-](\d{1,2})""")
        private val TIME_PATTERN = Regex("""(\d{1,2}):(\d{2})""")
        private val BANK_TAG_PATTERN = Regex(
            """\[(KB|신한카드|신한|우리|하나|삼성카드|삼성|현대카드|현대|롯데카드|롯데|IBK|NH|농협|BC|카카오뱅크|토스뱅크|토스|SC|씨티|수협|대구|부산|광주|전북|경남|제주)\]"""
        )
        private val STORE_NUMBER_ONLY_PATTERN = Regex("""^[\d,.:/\-\s]+$""")

        private val ELIGIBLE_TYPES = setOf(
            "expense",
            "cancel",
            "overseas",
            "payment",
            "debit"
        )
    }

    private val regexCache = ConcurrentHashMap<String, Regex>()

    suspend fun matchPaymentCandidates(inputs: List<SmsInput>): RuleMatchResult {
        if (inputs.isEmpty()) return RuleMatchResult(emptyList(), emptyList())

        val rulesBySender = mutableMapOf<String, List<SmsRegexRuleEntity>>()
        val matched = mutableListOf<SmsParseResult>()
        val unmatched = mutableListOf<SmsInput>()

        for (input in inputs) {
            val sender = SmsFilter.normalizeAddress(input.address)
            val senderRules = rulesBySender.getOrPut(sender) {
                ruleRepository.getActiveRulesBySender(sender)
                    .filter { isEligibleType(it.type) }
            }

            if (senderRules.isEmpty()) {
                unmatched.add(input)
                continue
            }

            val parsed = matchOne(input, senderRules)
            if (parsed != null) {
                matched.add(parsed)
            } else {
                unmatched.add(input)
            }
        }

        return RuleMatchResult(matched = matched, unmatched = unmatched)
    }

    private fun matchOne(input: SmsInput, rules: List<SmsRegexRuleEntity>): SmsParseResult? {
        val normalizedBody = normalizeBody(input.body)

        for (rule in rules) {
            val regex = compileRegex(rule.bodyRegex) ?: continue
            val match = regex.find(normalizedBody) ?: continue

            val amount = extractAmount(match, rule.amountGroup) ?: continue
            if (amount <= 0) continue

            val storeName = extractGroupValue(match, rule.storeGroup)
                ?.trim()
                ?.takeIf(::isValidStoreCandidate)
                ?: continue

            val cardName = extractGroupValue(match, rule.cardGroup)
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: extractBankTagFromBody(normalizedBody)
                ?: "기타"

            val dateTime = extractDateTime(match, normalizedBody, input.date, rule.dateGroup)

            return SmsParseResult(
                input = input,
                analysis = SmsAnalysisResult(
                    amount = amount,
                    storeName = storeName,
                    category = "미분류",
                    dateTime = dateTime,
                    cardName = cardName
                ),
                tier = 1,
                confidence = 1.0f
            )
        }

        return null
    }

    private fun isEligibleType(type: String): Boolean {
        if (type.isBlank()) return true
        return type.lowercase(Locale.ROOT) in ELIGIBLE_TYPES
    }

    private fun compileRegex(pattern: String): Regex? {
        if (pattern.isBlank()) return null
        regexCache[pattern]?.let { return it }
        return try {
            Regex(pattern).also { regexCache[pattern] = it }
        } catch (_: Exception) {
            null
        }
    }

    private fun extractAmount(match: kotlin.text.MatchResult, groupKey: String): Int? {
        val raw = extractGroupValue(match, groupKey) ?: return null
        val normalized = raw.replace(NON_DIGIT_PATTERN, "")
        return normalized.toIntOrNull()
    }

    private fun extractGroupValue(match: kotlin.text.MatchResult, groupKey: String): String? {
        val key = groupKey.trim()
        if (key.isBlank()) return null

        val value = key.toIntOrNull()?.let { index ->
            match.groupValues.getOrNull(index)
        } ?: runCatching {
            match.groups[key]?.value
        }.getOrNull()

        return value?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun extractDateTime(
        match: kotlin.text.MatchResult,
        body: String,
        timestamp: Long,
        dateGroup: String
    ): String {
        val fromGroup = extractGroupValue(match, dateGroup)
        if (!fromGroup.isNullOrBlank()) {
            val normalized = normalizeDateTime(fromGroup, timestamp)
            if (!normalized.isNullOrBlank()) {
                return normalized
            }
        }

        val calendar = Calendar.getInstance().apply { timeInMillis = timestamp }
        val dateMatch = DATE_PATTERN.find(body)
        if (dateMatch != null) {
            val month = dateMatch.groupValues[1].toIntOrNull() ?: (calendar.get(Calendar.MONTH) + 1)
            val day = dateMatch.groupValues[2].toIntOrNull() ?: calendar.get(Calendar.DAY_OF_MONTH)
            calendar.set(Calendar.MONTH, month - 1)
            calendar.set(Calendar.DAY_OF_MONTH, day)
        }

        val timeMatch = TIME_PATTERN.find(body)
        if (timeMatch != null) {
            val hour = timeMatch.groupValues[1].toIntOrNull() ?: calendar.get(Calendar.HOUR_OF_DAY)
            val minute = timeMatch.groupValues[2].toIntOrNull() ?: calendar.get(Calendar.MINUTE)
            calendar.set(Calendar.HOUR_OF_DAY, hour)
            calendar.set(Calendar.MINUTE, minute)
        }

        return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.KOREA).format(calendar.time)
    }

    private fun normalizeDateTime(raw: String, timestamp: Long): String? {
        val trimmed = raw.trim()
        if (trimmed.matches(Regex("""\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2}"""))) {
            return trimmed.replace(Regex("""\s+"""), " ")
        }

        val now = Calendar.getInstance().apply { timeInMillis = timestamp }
        val mdHm = Regex("""(\d{1,2})[/.-](\d{1,2})\s+(\d{1,2}):(\d{2})""").find(trimmed)
        if (mdHm != null) {
            now.set(Calendar.MONTH, (mdHm.groupValues[1].toIntOrNull() ?: 1) - 1)
            now.set(Calendar.DAY_OF_MONTH, mdHm.groupValues[2].toIntOrNull() ?: 1)
            now.set(Calendar.HOUR_OF_DAY, mdHm.groupValues[3].toIntOrNull() ?: 0)
            now.set(Calendar.MINUTE, mdHm.groupValues[4].toIntOrNull() ?: 0)
            return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.KOREA).format(now.time)
        }

        val hm = Regex("""(\d{1,2}):(\d{2})""").find(trimmed)
        if (hm != null) {
            now.set(Calendar.HOUR_OF_DAY, hm.groupValues[1].toIntOrNull() ?: 0)
            now.set(Calendar.MINUTE, hm.groupValues[2].toIntOrNull() ?: 0)
            return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.KOREA).format(now.time)
        }

        return null
    }

    private fun normalizeBody(body: String): String {
        return body
            .replace("\r\n", "\n")
            .replace('\r', '\n')
    }

    private fun extractBankTagFromBody(body: String): String? {
        val match = BANK_TAG_PATTERN.find(body) ?: return null
        return match.groupValues.getOrNull(1)?.takeIf { it.isNotBlank() }
    }

    private fun isValidStoreCandidate(value: String): Boolean {
        if (value.isBlank()) return false
        if (value.length > 30) return false
        if (value.contains("{")) return false
        if (STORE_NUMBER_ONLY_PATTERN.matches(value)) return false
        return true
    }
}
