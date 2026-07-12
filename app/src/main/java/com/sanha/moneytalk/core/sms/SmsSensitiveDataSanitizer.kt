package com.sanha.moneytalk.core.sms

/**
 * 외부 AI 처리나 진단 표본에 불필요한 직접 식별정보를 제거한다.
 * 금액, 거래처, 날짜처럼 파싱에 필요한 금융 필드는 유지한다.
 */
object SmsSensitiveDataSanitizer {

    private const val USER_NAME_PLACEHOLDER = "{USER_NAME}"
    private const val ACCOUNT_OR_CARD_PLACEHOLDER = "{ACCOUNT_OR_CARD}"

    private val labeledNamePattern = Regex(
        pattern = """(?m)^(\s*(?:고객명|손님명|성명)\s*(?::|：)?\s*)([가-힣A-Za-z]{2,20})(?=\s*$)"""
    )
    private val maskedNamePattern = Regex(
        pattern = """(?<![가-힣A-Za-z])(?:[가-힣A-Za-z]{1,3}\s*[*＊]\s*[가-힣A-Za-z]{1,3})(?:회원님|님)?"""
    )
    private val honorificNamePattern = Regex(
        pattern = """(?<![가-힣A-Za-z])(?:[가-힣]{2,4}|[A-Za-z]{2,20})\s*(?:회원님|님)"""
    )
    private val maskedAccountOrCardPattern = Regex(
        pattern = """(?<!\d)\d{1,6}(?:[-\s]?[*＊]{1,8}[-\s]?\d{1,6})+[*＊]?(?!\d)"""
    )
    private val separatedNumberPattern = Regex(
        pattern = """(?<!\d)(?:\d{2,6}(?:-\d{2,6}){1,4}|\d{2,6}(?:[ \t]\d{2,6}){1,4})(?!\d)"""
    )
    private val longNumberPattern = Regex(
        pattern = """(?<![\d,])\d{9,}(?![\d,])(?!\s*원)"""
    )

    fun sanitizeForExternalProcessing(body: String): String {
        if (body.isBlank()) return body

        return body
            .replace(labeledNamePattern) { match ->
                match.groupValues[1] + USER_NAME_PLACEHOLDER
            }
            .replace(maskedNamePattern, USER_NAME_PLACEHOLDER)
            .replace(honorificNamePattern, USER_NAME_PLACEHOLDER)
            .replace(maskedAccountOrCardPattern, ACCOUNT_OR_CARD_PLACEHOLDER)
            .replace(separatedNumberPattern) { match ->
                val digitCount = match.value.count(Char::isDigit)
                if (digitCount >= 9) ACCOUNT_OR_CARD_PLACEHOLDER else match.value
            }
            .replace(longNumberPattern, ACCOUNT_OR_CARD_PLACEHOLDER)
    }
}
