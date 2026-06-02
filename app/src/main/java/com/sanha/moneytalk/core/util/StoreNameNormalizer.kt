package com.sanha.moneytalk.core.util

/**
 * 거래처명 비교용 정규화 유틸.
 *
 * 화면/DB에 저장되는 원본 거래처명은 유지하고, 동일 거래처 판정이나 규칙 매칭에서만
 * 내부 공백과 대소문자 차이를 제거한 키를 사용합니다.
 */
object StoreNameNormalizer {
    private const val MIN_TRUNCATED_PREFIX_LENGTH = 4
    private const val MAX_TRUNCATED_SUFFIX_LENGTH = 3

    fun normalizeForComparison(value: String): String {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return ""

        var hasWhitespace = false
        for (char in trimmed) {
            if (char.isWhitespace()) {
                hasWhitespace = true
                break
            }
        }

        val compact = if (hasWhitespace) {
            buildString(trimmed.length) {
                for (char in trimmed) {
                    if (!char.isWhitespace()) append(char)
                }
            }
        } else {
            trimmed
        }

        return compact.lowercase()
    }

    fun equalsForComparison(left: String, right: String): Boolean {
        return normalizeForComparison(left) == normalizeForComparison(right)
    }

    fun containsForComparison(text: String, keyword: String): Boolean {
        val normalizedKeyword = normalizeForComparison(keyword)
        if (normalizedKeyword.isEmpty()) return false

        return normalizeForComparison(text).contains(normalizedKeyword)
    }

    fun matchesStoreRule(text: String, keyword: String): Boolean {
        val normalizedText = normalizeForComparison(text)
        val normalizedKeyword = normalizeForComparison(keyword)
        if (normalizedText.isEmpty() || normalizedKeyword.isEmpty()) return false

        if (normalizedText.contains(normalizedKeyword)) return true

        val missingSuffixLength = normalizedKeyword.length - normalizedText.length
        return normalizedText.length >= MIN_TRUNCATED_PREFIX_LENGTH &&
            missingSuffixLength in 1..MAX_TRUNCATED_SUFFIX_LENGTH &&
            normalizedKeyword.startsWith(normalizedText)
    }
}
