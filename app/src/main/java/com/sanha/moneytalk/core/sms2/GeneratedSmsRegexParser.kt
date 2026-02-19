package com.sanha.moneytalk.core.sms2

import android.util.Log
import com.sanha.moneytalk.core.database.entity.SmsPatternEntity
import com.sanha.moneytalk.core.model.SmsAnalysisResult
import java.util.concurrent.ConcurrentHashMap

/**
 * LLM이 생성한 정규식으로 SMS를 파싱하는 유틸리티.
 *
 * 정규식 파싱 실패 시 기존 SmsParser 파서로 폴백합니다.
 */
object GeneratedSmsRegexParser {

    private const val TAG = "GeneratedSmsRegexParser"

    /** 문자열 정규식 캐시 (재컴파일 비용 절감) */
    private val regexCache = ConcurrentHashMap<String, Regex>()

    /** 숫자 이외 문자 제거용 패턴 */
    private val NON_DIGIT_PATTERN = Regex("""[^\d]""")

    /** 정규식 가게명 후보 검증용 패턴/키워드 */
    private val STORE_NUMBER_ONLY_PATTERN = Regex("""^[\d,.:/\-\s]+$""")
    private val STORE_DATE_OR_TIME_PATTERN =
        Regex("""^(?:\d{1,2}[/.-]\d{1,2}(?:\s+\d{1,2}:\d{2})?|\d{1,2}:\d{2})$""")
    private val STORE_INVALID_KEYWORDS = listOf(
        "승인", "결제", "출금", "입금", "누적", "잔액", "일시불", "할부", "이용", "카드"
    )
    private val CARD_NUMBER_ONLY_PATTERN = Regex("""^[\d,.:/\-\s]+$""")
    private val CARD_INVALID_KEYWORDS = listOf(
        "web발신", "국외발신", "국제발신", "해외발신", "광고", "안내", "알림"
    )

    fun hasUsableRegex(pattern: SmsPatternEntity): Boolean {
        return pattern.amountRegex.isNotBlank() && pattern.storeRegex.isNotBlank()
    }

    fun parseWithPattern(
        smsBody: String,
        smsTimestamp: Long,
        pattern: SmsPatternEntity
    ): SmsAnalysisResult? {
        return parseWithRegex(
            smsBody = smsBody,
            smsTimestamp = smsTimestamp,
            amountRegex = pattern.amountRegex,
            storeRegex = pattern.storeRegex,
            cardRegex = pattern.cardRegex,
            fallbackAmount = pattern.parsedAmount,
            fallbackStoreName = pattern.parsedStoreName,
            fallbackCardName = pattern.parsedCardName,
            fallbackCategory = pattern.parsedCategory
        )
    }

    fun parseWithRegex(
        smsBody: String,
        smsTimestamp: Long,
        amountRegex: String,
        storeRegex: String,
        cardRegex: String = "",
        fallbackAmount: Int = 0,
        fallbackStoreName: String = "",
        fallbackCardName: String = "",
        fallbackCategory: String = ""
    ): SmsAnalysisResult? {
        val amountPattern = compileRegex(amountRegex) ?: return null
        val storePattern = compileRegex(storeRegex) ?: return null
        val cardPattern = compileRegex(cardRegex)

        val amountFromRegex = extractAmount(amountPattern, smsBody)
        val amount = amountFromRegex ?: SmsParser.extractAmount(smsBody) ?: fallbackAmount
        if (amount <= 0) return null

        val storeFromRegex = extractGroup1(storePattern, smsBody)
            ?.let(::sanitizeStoreName)
            ?.takeIf(::isValidStoreCandidate)
        val fallbackStore = sanitizeStoreName(fallbackStoreName)
        val parsedStore = storeFromRegex
            ?: SmsParser.extractStoreName(smsBody).takeIf { it != "결제" && it.length >= 2 }
            ?: fallbackStore.takeIf { it.isNotBlank() }
            ?: return null

        val cardFromRegex = extractGroup1(cardPattern, smsBody)
            ?.trim()
            ?.takeIf(::isValidCardCandidate)
            .orEmpty()
        val cardFromSmsParser = SmsParser.extractCardName(smsBody)
        val parsedCard = when {
            cardFromRegex.isNotBlank() -> cardFromRegex
            cardFromSmsParser != "기타" -> cardFromSmsParser
            fallbackCardName.isNotBlank() -> fallbackCardName
            else -> "기타"
        }

        val category = when {
            fallbackCategory.isNotBlank() &&
                fallbackStore.isNotBlank() &&
                parsedStore.equals(fallbackStore, ignoreCase = true) -> fallbackCategory
            else -> SmsParser.inferCategory(parsedStore, smsBody)
        }

        return SmsAnalysisResult(
            amount = amount,
            storeName = parsedStore,
            category = category,
            dateTime = SmsParser.extractDateTime(smsBody, smsTimestamp),
            cardName = parsedCard
        )
    }

    private fun compileRegex(pattern: String): Regex? {
        if (pattern.isBlank()) return null

        regexCache[pattern]?.let { return it }

        return try {
            val compiled = Regex(pattern)
            regexCache[pattern] = compiled
            compiled
        } catch (e: Exception) {
            Log.w(TAG, "정규식 컴파일 실패: ${pattern.take(80)} (${e.message})")
            null
        }
    }

    private fun extractAmount(regex: Regex, smsBody: String): Int? {
        val raw = extractGroup1(regex, smsBody) ?: return null
        val normalized = raw.replace(NON_DIGIT_PATTERN, "")
        return normalized.toIntOrNull()
    }

    private fun extractGroup1(regex: Regex?, smsBody: String): String? {
        if (regex == null) return null
        val match = regex.find(smsBody) ?: return null
        val value = match.groupValues.getOrNull(1)?.trim().orEmpty()
        return value.takeIf { it.isNotBlank() }
    }

    private fun sanitizeStoreName(value: String): String {
        return value.trim().take(20)
    }

    private fun isValidStoreCandidate(value: String): Boolean {
        val trimmed = value.trim()
        if (trimmed.length < 2 || trimmed.length > 30) return false
        if (trimmed.contains("{")) return false
        if (STORE_NUMBER_ONLY_PATTERN.matches(trimmed)) return false
        if (STORE_DATE_OR_TIME_PATTERN.matches(trimmed)) return false
        if (STORE_INVALID_KEYWORDS.any { keyword -> trimmed.contains(keyword, ignoreCase = true) }) {
            return false
        }
        return true
    }

    private fun isValidCardCandidate(value: String): Boolean {
        val trimmed = value.trim()
        if (trimmed.length < 2 || trimmed.length > 20) return false
        if (CARD_NUMBER_ONLY_PATTERN.matches(trimmed)) return false

        val lower = trimmed.lowercase()
        if (CARD_INVALID_KEYWORDS.any { keyword -> lower.contains(keyword) }) return false
        return true
    }
}
