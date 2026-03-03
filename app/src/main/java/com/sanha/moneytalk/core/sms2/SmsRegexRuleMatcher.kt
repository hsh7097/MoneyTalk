package com.sanha.moneytalk.core.sms2

import com.sanha.moneytalk.core.database.dao.SmsPatternDao
import com.sanha.moneytalk.core.database.SmsRegexRuleRepository
import com.sanha.moneytalk.core.database.entity.SmsPatternEntity
import com.sanha.moneytalk.core.database.entity.SmsRegexRuleEntity
import com.sanha.moneytalk.core.model.SmsAnalysisResult
import com.sanha.moneytalk.core.util.MoneyTalkLogger
import java.text.SimpleDateFormat
import java.util.Calendar
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
    private val ruleRepository: SmsRegexRuleRepository,
    private val smsPatternDao: SmsPatternDao,
    private val templateEngine: SmsTemplateEngine,
    private val originSampleCollector: SmsOriginSampleCollector
) {
    data class RuleMatchResult(
        val matched: List<SmsParseResult>,
        val unmatched: List<SmsInput>,
        val failureReasonCounts: Map<String, Int> = emptyMap(),
        val fallbackSenderCounts: Map<String, Int> = emptyMap()
    )

    companion object {
        private val NON_DIGIT_PATTERN = Regex("""[^\d]""")
        private val DATE_PATTERN = Regex("""(\d{1,2})[/.-](\d{1,2})""")
        private val TIME_PATTERN = Regex("""(\d{1,2}):(\d{2})""")
        private val BANK_TAG_PATTERN = Regex(
            """\[(KB|신한카드|신한|우리|하나|삼성카드|삼성|현대카드|현대|롯데카드|롯데|IBK|NH|농협|BC|카카오뱅크|토스뱅크|토스|SC|씨티|수협|대구|부산|광주|전북|경남|제주)\]"""
        )
        private val STORE_NUMBER_ONLY_PATTERN = Regex("""^[\d,.:/\-\s]+$""")
        private val STORE_DATE_OR_TIME_PATTERN =
            Regex("""^(?:\d{1,2}[/.-]\d{1,2}(?:\s+\d{1,2}:\d{2})?|\d{1,2}:\d{2})$""")
        private val DEBIT_LINE_PATTERN = Regex("""(?:^|\n)출금(?:\n|$)""")
        private const val DEBIT_FALLBACK_STORE_NAME = "계좌출금"
        private const val MAX_ACTIVE_RULES_PER_TYPE = 5
        private const val RULE_FAIL_INACTIVE_THRESHOLD = 12
        private const val RULE_STALE_INACTIVE_DAYS = 120L
        private const val MAX_LOCAL_REGEX_PATTERNS_PER_SENDER = 20
        private const val MAX_STRUCTURAL_CANDIDATES = 12

        private val ELIGIBLE_TYPES = setOf(
            "expense",
            "cancel",
            "overseas",
            "payment",
            "debit"
        )

        private val ACCOUNT_LINE_PATTERN = Regex("""\d+\*+\d+""")
        private val STORE_AFTER_ACCOUNT_PATTERN = Regex(
            """\n\d+\*+\d+\n(.+?)\n(?:출금|입금|체크카드출금|스마트폰출금|공동CMS출|지로출금|FBS출금)"""
        )
        private val STORE_AFTER_TIME_PATTERN = Regex(
            """\d{1,2}[/.-]\d{1,2}\s+\d{1,2}:\d{2}\s+(.+?)(?:\s+누적|\s*$)"""
        )
        private val CASHBACK_PREFIX_PATTERN = Regex("""^\*?\d+원(?:캐쉬백|캐시백)\s*""")
        private val STORE_SUFFIX_TRIM_PATTERN = Regex("""\s*(?:누적.*|잔액.*)$""")
        private val STORE_INVALID_KEYWORDS = setOf(
            "출금",
            "입금",
            "승인",
            "누적",
            "잔액",
            "일시불",
            "할부",
            "통지수수료",
            "민생회복",
            "소비쿠폰",
            "승인거절"
        )
        private val TEMPLATE_STORE_SLOT_PATTERN = Regex(
            """(\{CARD_NUM\}|\{CARD_NO\}|\d+\*+\d+)\s*\n([^\n]{2,40})\s*\n(출금|입금|승인|취소|결제)"""
        )
    }

    private val regexCache = ConcurrentHashMap<String, Regex>()
    private val placeholderPattern = Regex("""\{[^}]+\}""")
    private val nonTemplateTextPattern = Regex("""[^\p{L}\p{IsHangul}#]""")

    private data class MatchAttemptResult(
        val parsed: SmsParseResult? = null,
        val failReason: String = "",
        val failStage: String = "fast_path_regex",
        val matchedRuleKey: String = "",
        val ruleType: String = ""
    )

    suspend fun matchPaymentCandidates(inputs: List<SmsInput>): RuleMatchResult {
        if (inputs.isEmpty()) return RuleMatchResult(emptyList(), emptyList())

        val rulesBySender = mutableMapOf<String, List<SmsRegexRuleEntity>>()
        val localRegexPatternsBySender = mutableMapOf<String, List<SmsPatternEntity>>()
        val matched = mutableListOf<SmsParseResult>()
        val unmatched = mutableListOf<SmsInput>()
        val failureReasonCounts = mutableMapOf<String, Int>()
        val fallbackSenderCounts = mutableMapOf<String, Int>()

        for (input in inputs) {
            val sender = SmsFilter.normalizeAddress(input.address)
            val senderRules = rulesBySender[sender] ?: loadSenderRules(sender).also {
                rulesBySender[sender] = it
            }
            val senderLocalPatterns =
                localRegexPatternsBySender[sender] ?: loadLocalRegexPatterns(sender).also {
                    localRegexPatternsBySender[sender] = it
                }

            val primaryAttempt = if (senderRules.isEmpty()) {
                MatchAttemptResult(
                    failReason = "no_active_rule",
                    failStage = "fast_path_rule_lookup",
                    ruleType = inferTypeFromBody(input.body)
                )
            } else {
                matchOne(input = input, sender = sender, rules = senderRules)
            }
            val localAttempt = if (primaryAttempt.parsed == null) {
                matchWithLocalRegexPatterns(input = input, patterns = senderLocalPatterns)
            } else {
                MatchAttemptResult()
            }

            val parsed = primaryAttempt.parsed ?: localAttempt.parsed
            if (parsed != null) {
                matched.add(parsed)
                continue
            }

            if (senderRules.isEmpty() && senderLocalPatterns.isEmpty()) {
                incrementCount(failureReasonCounts, "fast_path_rule_lookup:no_active_rule")
                incrementCount(fallbackSenderCounts, sender)
                collectFastPathFailure(
                    input = input,
                    normalizedSender = sender,
                    type = primaryAttempt.ruleType.ifBlank { "expense" },
                    failReason = "no_active_rule",
                    failStage = "fast_path_rule_lookup",
                    matchedRuleKey = ""
                )
                unmatched.add(input)
                continue
            }

            val finalAttempt =
                if (localAttempt.failReason.isNotBlank() && localAttempt.failReason != "local_no_pattern") {
                    localAttempt
                } else {
                    primaryAttempt
                }
            val failStage = finalAttempt.failStage.ifBlank { "fast_path_regex" }
            val failReason = finalAttempt.failReason.ifBlank { "no_regex_match" }
            incrementCount(failureReasonCounts, "$failStage:$failReason")
            incrementCount(fallbackSenderCounts, sender)
            collectFastPathFailure(
                input = input,
                normalizedSender = sender,
                type = finalAttempt.ruleType.ifBlank { inferTypeFromBody(input.body) },
                failReason = failReason,
                failStage = failStage,
                matchedRuleKey = finalAttempt.matchedRuleKey
            )
            unmatched.add(input)
        }

        return RuleMatchResult(
            matched = matched,
            unmatched = unmatched,
            failureReasonCounts = failureReasonCounts.toSortedCountMap(),
            fallbackSenderCounts = fallbackSenderCounts.toSortedCountMap()
        )
    }

    private suspend fun loadSenderRules(sender: String): List<SmsRegexRuleEntity> {
        if (sender.isBlank()) return emptyList()

        var rules = ruleRepository.getActiveRulesBySender(sender)
            .filter { isEligibleType(it.type) }
        if (rules.isEmpty()) return emptyList()

        val optimized = optimizeRulesForSender(sender, rules)
        if (optimized) {
            rules = ruleRepository.getActiveRulesBySender(sender)
                .filter { isEligibleType(it.type) }
        }
        return rules
    }

    private suspend fun optimizeRulesForSender(
        sender: String,
        rules: List<SmsRegexRuleEntity>
    ): Boolean {
        val now = System.currentTimeMillis()
        var changed = false

        for (rule in rules) {
            if (shouldDeactivateLowQuality(rule, now)) {
                ruleRepository.updateStatus(
                    senderAddress = sender,
                    type = rule.type,
                    ruleKey = rule.ruleKey,
                    status = "INACTIVE",
                    timestamp = now
                )
                changed = true
                continue
            }

            val tunedPriority = computeAdaptivePriority(rule, now)
            if (tunedPriority != rule.priority) {
                ruleRepository.updatePriority(
                    senderAddress = sender,
                    type = rule.type,
                    ruleKey = rule.ruleKey,
                    priority = tunedPriority,
                    timestamp = now
                )
                changed = true
            }
        }

        val baselineRules = if (changed) {
            ruleRepository.getActiveRulesBySender(sender).filter { isEligibleType(it.type) }
        } else {
            rules
        }

        for ((type, typeRules) in baselineRules.groupBy { it.type.lowercase(Locale.ROOT) }) {
            val overflowRules = typeRules
                .sortedWith(
                    compareByDescending<SmsRegexRuleEntity> { it.priority }
                        .thenByDescending { it.lastMatchedAt }
                        .thenByDescending { it.updatedAt }
                )
                .drop(MAX_ACTIVE_RULES_PER_TYPE)

            if (overflowRules.isEmpty()) continue
            overflowRules.forEach { overflow ->
                ruleRepository.updateStatus(
                    senderAddress = sender,
                    type = overflow.type,
                    ruleKey = overflow.ruleKey,
                    status = "INACTIVE",
                    timestamp = now
                )
            }
            MoneyTalkLogger.i(
                "[SmsRegexRuleMatcher] sender=$sender type=$type 활성 룰 상한 초과(${typeRules.size}) → ${overflowRules.size}건 INACTIVE"
            )
            changed = true
        }

        return changed
    }

    private fun shouldDeactivateLowQuality(rule: SmsRegexRuleEntity, now: Long): Boolean {
        if (rule.status != "ACTIVE") return false

        val noSuccessAndManyFailures =
            rule.matchCount == 0 && rule.failCount >= RULE_FAIL_INACTIVE_THRESHOLD
        if (noSuccessAndManyFailures) return true

        if (rule.lastMatchedAt <= 0L) return false
        val staleMs = RULE_STALE_INACTIVE_DAYS * 24L * 60L * 60L * 1000L
        val isStale = (now - rule.lastMatchedAt) >= staleMs
        val failureDominant = rule.failCount >= (rule.matchCount + 8)
        return isStale && failureDominant
    }

    private fun computeAdaptivePriority(rule: SmsRegexRuleEntity, now: Long): Int {
        val basePriority = maxOf(rule.priority, defaultPriorityByType(rule.type))
        val performanceScore = (rule.matchCount * 8) - (rule.failCount * 9)
        val recencyBonus = when {
            rule.lastMatchedAt <= 0L -> 0
            (now - rule.lastMatchedAt) <= 7L * 24L * 60L * 60L * 1000L -> 40
            (now - rule.lastMatchedAt) <= 30L * 24L * 60L * 60L * 1000L -> 20
            (now - rule.lastMatchedAt) <= 90L * 24L * 60L * 60L * 1000L -> 5
            else -> -20
        }
        return (basePriority + performanceScore + recencyBonus).coerceIn(0, 1000)
    }

    private fun defaultPriorityByType(type: String): Int {
        return when (type.lowercase(Locale.ROOT)) {
            "expense", "payment", "debit" -> 700
            "cancel" -> 650
            "overseas" -> 620
            else -> 600
        }
    }

    private suspend fun matchOne(
        input: SmsInput,
        sender: String,
        rules: List<SmsRegexRuleEntity>
    ): MatchAttemptResult {
        val normalizedBody = normalizeBody(input.body)
        var lastFailure = MatchAttemptResult(failReason = "no_regex_match")

        for (rule in rules) {
            val regex = compileRegex(rule.bodyRegex)
            if (regex == null) {
                markRuleFailure(sender, rule)
                lastFailure = MatchAttemptResult(
                    failReason = "invalid_regex",
                    failStage = "fast_path_regex_compile",
                    matchedRuleKey = rule.ruleKey,
                    ruleType = rule.type
                )
                continue
            }
            val match = regex.find(normalizedBody) ?: continue

            val amount = extractAmount(match, rule.amountGroup)
            if (amount == null || amount <= 0) {
                markRuleFailure(sender, rule)
                lastFailure = MatchAttemptResult(
                    failReason = "amount_extract_failed",
                    matchedRuleKey = rule.ruleKey,
                    ruleType = rule.type
                )
                continue
            }

            val storeName = extractGroupValue(match, rule.storeGroup)
                ?.trim()
                ?.let(::sanitizeStoreCandidate)
                ?.let { normalizeStoreCandidate(it, normalizedBody) }
                ?: extractStoreFallback(normalizedBody)
            if (storeName.isNullOrBlank()) {
                markRuleFailure(sender, rule)
                lastFailure = MatchAttemptResult(
                    failReason = "store_extract_failed",
                    matchedRuleKey = rule.ruleKey,
                    ruleType = rule.type
                )
                continue
            }

            val cardName = extractGroupValue(match, rule.cardGroup)
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: extractBankTagFromBody(normalizedBody)
                ?: "기타"

            val dateTime = extractDateTime(match, normalizedBody, input.date, rule.dateGroup)
            ruleRepository.incrementMatchCount(sender, rule.type, rule.ruleKey)

            return MatchAttemptResult(
                parsed = SmsParseResult(
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
            )
        }

        return lastFailure
    }

    private suspend fun markRuleFailure(
        sender: String,
        rule: SmsRegexRuleEntity
    ) {
        ruleRepository.incrementFailCount(
            senderAddress = sender,
            type = rule.type,
            ruleKey = rule.ruleKey,
            inactiveThreshold = RULE_FAIL_INACTIVE_THRESHOLD
        )
    }

    private suspend fun loadLocalRegexPatterns(sender: String): List<SmsPatternEntity> {
        if (sender.isBlank()) return emptyList()
        return smsPatternDao.getPatternsBySender(sender)
            .asSequence()
            .filter { it.isPayment && it.amountRegex.isNotBlank() && it.storeRegex.isNotBlank() }
            .sortedWith(
                compareByDescending<SmsPatternEntity> { it.matchCount }
                    .thenByDescending { it.lastMatchedAt }
                    .thenByDescending { it.createdAt }
            )
            .take(MAX_LOCAL_REGEX_PATTERNS_PER_SENDER)
            .toList()
    }

    private suspend fun matchWithLocalRegexPatterns(
        input: SmsInput,
        patterns: List<SmsPatternEntity>
    ): MatchAttemptResult {
        if (patterns.isEmpty()) {
            return MatchAttemptResult(
                failReason = "local_no_pattern",
                failStage = "fast_path_local_regex",
                ruleType = inferTypeFromBody(input.body)
            )
        }

        val normalizedBody = normalizeBody(input.body)
        val inputTemplate = normalizeTemplate(templateEngine.templateize(input.body))
        val templateMatchedPatterns = patterns.filter { pattern ->
            val patternTemplate = normalizeTemplate(pattern.smsTemplate)
            patternTemplate == inputTemplate
        }
        val looseMatchedPatterns = if (templateMatchedPatterns.isEmpty()) {
            patterns.filter { pattern ->
                val patternTemplate = normalizeTemplate(pattern.smsTemplate)
                isLooseTemplateMatch(inputTemplate, patternTemplate)
            }
        } else {
            emptyList()
        }
        val structuralMatchedPatterns = if (
            templateMatchedPatterns.isEmpty() && looseMatchedPatterns.isEmpty()
        ) {
            patterns.filter { pattern ->
                val patternTemplate = normalizeTemplate(pattern.smsTemplate)
                isStructuralTemplateMatch(
                    inputBody = normalizedBody,
                    inputTemplate = inputTemplate,
                    patternTemplate = patternTemplate
                )
            }.take(MAX_STRUCTURAL_CANDIDATES)
        } else {
            emptyList()
        }
        val candidatePatterns = if (templateMatchedPatterns.isNotEmpty()) {
            templateMatchedPatterns
        } else if (looseMatchedPatterns.isNotEmpty()) {
            looseMatchedPatterns
        } else if (structuralMatchedPatterns.isNotEmpty()) {
            structuralMatchedPatterns
        } else {
            emptyList()
        }
        if (candidatePatterns.isEmpty()) {
            return MatchAttemptResult(
                failReason = "local_template_mismatch",
                failStage = "fast_path_local_template",
                ruleType = inferTypeFromBody(input.body)
            )
        }
        var lastFailure = MatchAttemptResult(
            failReason = "local_no_regex_match",
            failStage = "fast_path_local_regex",
            ruleType = inferTypeFromBody(input.body)
        )

        for (pattern in candidatePatterns) {
            val amountPattern = compileRegex(pattern.amountRegex)
            val storePattern = compileRegex(pattern.storeRegex)
            if (amountPattern == null || storePattern == null) {
                lastFailure = MatchAttemptResult(
                    failReason = "local_regex_compile_failed",
                    failStage = "fast_path_local_regex_compile",
                    ruleType = inferTypeFromBody(input.body)
                )
                continue
            }

            val amount = extractGroup1(amountPattern, normalizedBody)
                ?.replace(NON_DIGIT_PATTERN, "")
                ?.toIntOrNull()
            if (amount == null || amount <= 0) {
                lastFailure = MatchAttemptResult(
                    failReason = "local_amount_extract_failed",
                    failStage = "fast_path_local_regex",
                    ruleType = inferTypeFromBody(input.body)
                )
                continue
            }

            val storeName = extractGroup1(storePattern, normalizedBody)
                ?.trim()
                ?.let(::sanitizeStoreCandidate)
                ?.let { normalizeStoreCandidate(it, normalizedBody) }
                ?: extractStoreFallback(normalizedBody)
            if (storeName.isNullOrBlank()) {
                lastFailure = MatchAttemptResult(
                    failReason = "local_store_extract_failed",
                    failStage = "fast_path_local_regex",
                    ruleType = inferTypeFromBody(input.body)
                )
                continue
            }

            val cardPattern = compileRegex(pattern.cardRegex)
            val cardName = extractGroup1(cardPattern, normalizedBody)
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: extractBankTagFromBody(normalizedBody)
                ?: pattern.parsedCardName.ifBlank { "기타" }

            smsPatternDao.incrementMatchCount(pattern.id)

            return MatchAttemptResult(
                parsed = SmsParseResult(
                    input = input,
                    analysis = SmsAnalysisResult(
                        amount = amount,
                        storeName = storeName,
                        category = "미분류",
                        dateTime = extractDateTimeFromBody(normalizedBody, input.date),
                        cardName = cardName
                    ),
                    tier = 1,
                    confidence = 0.97f
                ),
                ruleType = inferTypeFromBody(input.body)
            )
        }
        return lastFailure
    }

    private fun collectFastPathFailure(
        input: SmsInput,
        normalizedSender: String,
        type: String,
        failReason: String,
        failStage: String,
        matchedRuleKey: String
    ) {
        runCatching {
            originSampleCollector.collectFailure(
                SmsOriginSampleCollector.FailureSample(
                    senderAddress = input.address,
                    normalizedSenderAddress = normalizedSender,
                    type = type,
                    originBody = input.body,
                    parseSource = "fast_path",
                    failStage = failStage,
                    failReason = failReason,
                    matchedRuleKey = matchedRuleKey
                )
            )
        }.onFailure { e ->
            MoneyTalkLogger.w("Fast Path 실패 표본 수집 예외(무시): ${e.message}")
        }
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

    private fun normalizeTemplate(template: String): String {
        return template
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .trim()
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
        if (STORE_INVALID_KEYWORDS.any { value.contains(it) }) return false
        return true
    }

    private fun extractGroup1(regex: Regex?, text: String): String? {
        if (regex == null) return null
        val match = regex.find(text) ?: return null
        return match.groupValues.getOrNull(1)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    private fun extractDateTimeFromBody(body: String, timestamp: Long): String {
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

    private fun inferTypeFromBody(body: String): String {
        val normalized = body.lowercase(Locale.ROOT)
        return when {
            normalized.contains("취소") -> "cancel"
            normalized.contains("해외") ||
                normalized.contains("usd") ||
                normalized.contains("jpy") ||
                normalized.contains("eur") -> "overseas"
            normalized.contains("입금") || normalized.contains("이체입금") -> "income"
            else -> "expense"
        }
    }

    private fun isLooseTemplateMatch(inputTemplate: String, patternTemplate: String): Boolean {
        val inputKey = toLooseTemplateKey(inputTemplate)
        val patternKey = toLooseTemplateKey(patternTemplate)
        if (inputKey.isBlank() || patternKey.isBlank()) return false
        if (inputKey == patternKey) return true
        if (inputKey.contains(patternKey) || patternKey.contains(inputKey)) return true

        val prefixLength = commonPrefixLength(inputKey, patternKey)
        if (prefixLength < 8) return false
        val shorterLength = minOf(inputKey.length, patternKey.length)
        return (prefixLength * 100) / shorterLength >= 45
    }

    private fun isStructuralTemplateMatch(
        inputBody: String,
        inputTemplate: String,
        patternTemplate: String
    ): Boolean {
        val inputLines = inputBody.split('\n').count { it.isNotBlank() }
        val patternLines = patternTemplate.split('\n').count { it.isNotBlank() }
        val lineCompatible = kotlin.math.abs(inputLines - patternLines) <= 2 ||
            (inputLines >= 5 && patternLines >= 5)
        if (!lineCompatible) return false

        val inputType = inferTypeFromBody(inputTemplate)
        val patternType = inferTypeFromBody(patternTemplate)
        if (inputType != patternType) return false

        val debitLike = inputBody.contains("출금")
        val approvalLike = inputBody.contains("승인")
        val patternDebitLike = patternTemplate.contains("출금")
        val patternApprovalLike = patternTemplate.contains("승인")
        if (debitLike != patternDebitLike && approvalLike != patternApprovalLike) return false

        return true
    }

    private fun toLooseTemplateKey(template: String): String {
        val normalizedTemplate = template
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .replace(TEMPLATE_STORE_SLOT_PATTERN, "$1\n#\n$3")

        return normalizedTemplate
            .lowercase(Locale.ROOT)
            .replace("[web발신]", "")
            .replace("(광고)", "")
            .replace("광고", "")
            .replace(placeholderPattern, "#")
            .replace(nonTemplateTextPattern, "")
            .replace(Regex("""#+"""), "#")
            .trim('#')
    }

    private fun extractStoreFallback(body: String): String? {
        val byAccountRegex = STORE_AFTER_ACCOUNT_PATTERN.find(body)
            ?.groupValues
            ?.getOrNull(1)
            ?.let(::sanitizeStoreCandidate)
            ?.takeIf(::isValidStoreCandidate)
        if (!byAccountRegex.isNullOrBlank()) return byAccountRegex

        val byTimeRegex = STORE_AFTER_TIME_PATTERN.find(body)
            ?.groupValues
            ?.getOrNull(1)
            ?.let(::sanitizeStoreCandidate)
            ?.takeIf(::isValidStoreCandidate)
        if (!byTimeRegex.isNullOrBlank()) return byTimeRegex

        val lines = body.split('\n').map { it.trim() }.filter { it.isNotBlank() }
        val accountIndex = lines.indexOfFirst { ACCOUNT_LINE_PATTERN.containsMatchIn(it) }
        if (accountIndex >= 0 && accountIndex + 1 < lines.size) {
            val nextLineStore = sanitizeStoreCandidate(lines[accountIndex + 1])
            if (isValidStoreCandidate(nextLineStore)) return nextLineStore
        }

        return lines
            .map(::sanitizeStoreCandidate)
            .firstOrNull(::isValidStoreCandidate)
    }

    private fun sanitizeStoreCandidate(raw: String): String {
        return raw
            .replace(CASHBACK_PREFIX_PATTERN, "")
            .replace(STORE_SUFFIX_TRIM_PATTERN, "")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    private fun normalizeStoreCandidate(value: String, body: String): String? {
        val trimmed = value.trim()
        if (isValidStoreCandidate(trimmed)) return trimmed
        return normalizeDebitStoreCandidate(trimmed, body)
    }

    /**
     * 출금형 메시지에서 상호 추출이 애매한 경우(예: KB카드출금/해외승인대금출금)
     * Fast Path 탈락을 줄이기 위해 보수적으로 store를 보정합니다.
     */
    private fun normalizeDebitStoreCandidate(value: String, body: String): String? {
        if (value.isBlank()) return null
        if (!DEBIT_LINE_PATTERN.containsMatchIn(body)) return null
        if (value.contains("{")) return null
        if (STORE_DATE_OR_TIME_PATTERN.matches(value)) return null
        if (STORE_NUMBER_ONLY_PATTERN.matches(value)) return DEBIT_FALLBACK_STORE_NAME
        if (value.contains("입출통지")) return DEBIT_FALLBACK_STORE_NAME
        if (STORE_INVALID_KEYWORDS.any { value.contains(it) }) return DEBIT_FALLBACK_STORE_NAME
        return value
    }

    private fun commonPrefixLength(left: String, right: String): Int {
        val limit = minOf(left.length, right.length)
        var index = 0
        while (index < limit && left[index] == right[index]) {
            index++
        }
        return index
    }

    private fun incrementCount(map: MutableMap<String, Int>, key: String) {
        if (key.isBlank()) return
        map[key] = (map[key] ?: 0) + 1
    }

    private fun Map<String, Int>.toSortedCountMap(): Map<String, Int> {
        return entries
            .sortedByDescending { it.value }
            .associate { it.key to it.value }
    }
}
