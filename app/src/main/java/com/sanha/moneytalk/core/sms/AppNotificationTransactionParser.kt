package com.sanha.moneytalk.core.sms

/**
 * 금융 앱 알림 본문에서 지출 필드를 추출하는 경량 파서.
 *
 * SMS provider row가 없는 앱 알림은 sender 기반 regex 룰을 학습하기 전까지
 * Fast Path가 실패할 수 있어, 알림 본문 구조를 휴리스틱으로 1회 보정한다.
 */
object AppNotificationTransactionParser {

    data class ExpenseCandidate(
        val amount: Int,
        val storeName: String,
        val category: String,
        val cardName: String
    )

    private val amountPattern = Regex("""([\d,]+)\s*원""")
    private val whitespacePattern = Regex("""\s+""")
    private val dateOrTimePattern = Regex(
        """(?:\d{1,2}[/.-]\d{1,2}|\d{1,2}:\d{2}|오전|오후)"""
    )
    private val cardNumberPattern = Regex("""\d+\*+\d+|\*{2,}|\d{4}[-\s]?\d{2,}""")
    private val accountTargetPattern = Regex(
        """(?:입출금통장|통장|계좌)\([^)]*\)\s*(?:→|->|>)\s*(.+)$"""
    )

    private val balanceKeywords = listOf("잔액", "누적", "잔고", "보유")
    private val transactionKeywords = listOf(
        "결제", "승인", "출금", "사용", "이용", "일시불", "할부", "체크카드", "카드"
    )
    private val appNameKeywords = listOf("카카오뱅크", "토스")
    private val invalidStoreKeywords = listOf(
        "알림", "입금", "취소", "잔액", "누적", "잔고", "보유"
    )

    fun parseExpense(
        body: String,
        appLabel: String,
        packageName: String
    ): ExpenseCandidate? {
        val amountMatch = findTransactionAmount(body) ?: return null
        val amount = amountMatch.value
            .replace(",", "")
            .replace("원", "")
            .trim()
            .toIntOrNull()
            ?: return null
        if (amount <= 0) return null

        val storeName = extractStoreName(body, amountMatch.range, appLabel)
            ?: buildFallbackStoreName(appLabel, packageName)
        val category = SmsParser.inferCategory(storeName, body).takeUnless { it == "미분류" }
            ?: "기타"
        val cardName = appLabel.takeIf { it.isNotBlank() }
            ?: packageName.substringAfterLast('.')

        return ExpenseCandidate(
            amount = amount,
            storeName = storeName,
            category = category,
            cardName = cardName
        )
    }

    private fun findTransactionAmount(body: String): MatchResult? {
        val matches = amountPattern.findAll(body).toList()
        if (matches.isEmpty()) return null

        return matches.firstOrNull { match ->
            val line = body.lineSequence()
                .firstOrNull { line -> line.contains(match.value) }
                .orEmpty()
            balanceKeywords.none { line.contains(it) }
        } ?: matches.first()
    }

    private fun extractStoreName(
        body: String,
        amountRange: IntRange,
        appLabel: String
    ): String? {
        extractStoreFromAccountTargetLine(body, appLabel)?.let { return it }
        extractStoreFromAmountLine(body, amountRange, appLabel)?.let { return it }

        val lines = body.lines().map(::normalizeText).filter { it.isNotBlank() }
        val amountLineIndex = lines.indexOfFirst { amountPattern.containsMatchIn(it) }

        val nearbyLines = when {
            amountLineIndex >= 0 -> listOfNotNull(
                lines.getOrNull(amountLineIndex - 2),
                lines.getOrNull(amountLineIndex - 1),
                lines.getOrNull(amountLineIndex + 1),
                lines.getOrNull(amountLineIndex + 2)
            )
            else -> lines
        }

        return nearbyLines.firstNotNullOfOrNull { sanitizeStoreCandidate(it, appLabel) }
            ?: lines.firstNotNullOfOrNull { sanitizeStoreCandidate(it, appLabel) }
    }

    private fun extractStoreFromAccountTargetLine(
        body: String,
        appLabel: String
    ): String? {
        return body.lineSequence()
            .mapNotNull { line ->
                val match = accountTargetPattern.find(normalizeText(line)) ?: return@mapNotNull null
                val target = trimAccountTarget(match.groupValues.getOrNull(1).orEmpty())
                sanitizeStoreCandidate(
                    raw = target,
                    appLabel = appLabel,
                    allowShort = true,
                    preserveCardWord = true
                )
            }
            .firstOrNull()
    }

    private fun extractStoreFromAmountLine(
        body: String,
        amountRange: IntRange,
        appLabel: String
    ): String? {
        val lineStart = body.lastIndexOf('\n', amountRange.first).let { if (it < 0) 0 else it + 1 }
        val lineEnd = body.indexOf('\n', amountRange.last).let { if (it < 0) body.length else it }
        val line = body.substring(lineStart, lineEnd)
        val amountText = body.substring(amountRange)

        val beforeAmount = line.substringBefore(amountText)
        sanitizeStoreCandidate(beforeAmount, appLabel)?.let { return it }

        val afterAmount = line.substringAfter(amountText, missingDelimiterValue = "")
        return sanitizeStoreCandidate(afterAmount, appLabel)
    }

    private fun sanitizeStoreCandidate(
        raw: String,
        appLabel: String,
        allowShort: Boolean = false,
        preserveCardWord: Boolean = false
    ): String? {
        var candidate = normalizeText(raw)
        if (candidate.isBlank()) return null

        candidate = amountPattern.replace(candidate, " ")
        transactionKeywords.forEach { keyword ->
            if (preserveCardWord && keyword == "카드") return@forEach
            candidate = candidate.replace(keyword, " ")
        }
        candidate = candidate.replace(appLabel, " ")
        appNameKeywords.forEach { keyword ->
            candidate = candidate.replace(keyword, " ")
        }
        candidate = candidate
            .replace("에서", " ")
            .replace("완료", " ")
            .replace("되었습니다", " ")
            .replace("했습니다", " ")
            .trim(' ', '-', ':', '|')
        candidate = normalizeText(candidate)

        val minLength = if (allowShort) 1 else 2
        if (candidate.length !in minLength..40) return null
        if (candidate == appLabel) return null
        if (dateOrTimePattern.containsMatchIn(candidate)) return null
        if (cardNumberPattern.containsMatchIn(candidate)) return null
        if (candidate.all { it.isDigit() || it.isWhitespace() || it in ",.-:/" }) return null
        if (invalidStoreKeywords.any { candidate.contains(it) }) return null

        return candidate
    }

    private fun trimAccountTarget(raw: String): String {
        var target = raw
        balanceKeywords.forEach { keyword ->
            target = target.substringBefore(keyword)
        }
        target = amountPattern.replace(target, " ")
        return normalizeText(target).trim(' ', '-', ':', '|')
    }

    private fun buildFallbackStoreName(appLabel: String, packageName: String): String {
        return appLabel.takeIf { it.isNotBlank() }
            ?: packageName.substringAfterLast('.').takeIf { it.isNotBlank() }
            ?: "앱결제"
    }

    private fun normalizeText(text: String): String =
        whitespacePattern.replace(text, " ").trim()
}
