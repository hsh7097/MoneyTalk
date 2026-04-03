package com.sanha.moneytalk.receiver

import android.app.Notification
import android.service.notification.StatusBarNotification
import java.util.Calendar

/**
 * 메시지 앱 알림에서 금융 거래 후보 텍스트를 추출한다.
 *
 * 현재는 삼성/구글 메시지 계열만 지원하며,
 * 알림 자체를 바로 저장하기보다 최근 SMS/MMS/RCS provider row를 찾기 위한 힌트로 사용한다.
 */
object NotificationContentParser {

    private const val MAX_BODY_LENGTH = 130
    private const val MAX_NOTIFICATION_AGE_MS = 30L * 24 * 60 * 60 * 1000
    private const val MIN_CANDIDATE_LENGTH = 8

    private val timePattern = Regex("""\d{1,2}:\d{2}""")
    private val datePattern = Regex("""\d{1,2}[/.-]\d{1,2}""")
    private val amountPattern = Regex("""[\d,]+원""")
    private val transactionHints = listOf(
        "입금", "출금", "결제", "승인", "취소", "송금", "이체", "잔액", "일시불", "할부", "이용"
    )

    private val supportedPackagePrefixes = listOf(
        "com.samsung.android.messaging",
        "com.google.android.apps.messaging",
        "com.android.messaging",
        "com.android.mms",
        "com.lge.message",
        "com.skt.prod.dialer"
    )

    @Volatile
    var selfPackageName: String = ""

    data class ParsedNotification(
        val packageName: String,
        val title: String,
        val text: String,
        val bigText: String,
        val body: String,
        val candidateBodies: Set<String>,
        val timestamp: Long
    )

    fun isSupportedPackage(packageName: String): Boolean =
        supportedPackagePrefixes.any { packageName.startsWith(it) }

    fun parse(sbn: StatusBarNotification): ParsedNotification? {
        if (sbn.packageName == selfPackageName) return null
        if (!isSupportedPackage(sbn.packageName)) return null

        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
        val titleBig = extras.getCharSequence(Notification.EXTRA_TITLE_BIG)?.toString().orEmpty()
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString().orEmpty()
        val summaryText = extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)?.toString().orEmpty()
        val conversationTitle = extras
            .getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)
            ?.toString()
            .orEmpty()
        val textLines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            ?.mapNotNull { it?.toString() }
            .orEmpty()

        val segments = mergeSegments(
            listOf(
                title,
                titleBig,
                conversationTitle,
                text,
                bigText,
                summaryText,
                subText
            ) + textLines
        )
        if (segments.isEmpty()) return null

        val compactMultiline = buildCompactCandidate(segments, separator = "\n")
        val compactSingleLine = normalizeWhitespace(compactMultiline.replace('\n', ' '))
        val candidates = buildList {
            add(compactMultiline)
            add(compactSingleLine)
            add(segments.joinToString("\n"))
            add(segments.joinToString(" "))
            addAll(segments)
        }
            .map(::normalizeBodyCandidate)
            .filter { it.isNotBlank() }
            .distinct()

        val boundedCandidates = candidates.filter { it.length <= MAX_BODY_LENGTH }
        val pool = if (boundedCandidates.isNotEmpty()) {
            boundedCandidates
        } else {
            listOf(trimCandidate(compactMultiline), trimCandidate(compactSingleLine))
                .filter { it.isNotBlank() }
        }

        val body = pool.maxByOrNull(::scoreBodyCandidate) ?: return null
        val candidateBodies = candidates
            .map { normalizeWhitespace(it.replace('\n', ' ')) }
            .filter { it.length >= MIN_CANDIDATE_LENGTH }
            .toSet()
            .ifEmpty { setOf(normalizeWhitespace(body.replace('\n', ' '))) }

        return ParsedNotification(
            packageName = sbn.packageName,
            title = title,
            text = text,
            bigText = bigText,
            body = body,
            candidateBodies = candidateBodies,
            timestamp = resolveTimestamp(sbn, body)
        )
    }

    fun looksLikeFinancialMessage(parsed: ParsedNotification): Boolean {
        val merged = normalizeWhitespace(
            listOf(parsed.title, parsed.text, parsed.bigText, parsed.body)
                .filter { it.isNotBlank() }
                .joinToString(" ")
        )
        if (!amountPattern.containsMatchIn(merged)) return false
        return transactionHints.any { merged.contains(it, ignoreCase = true) }
    }

    private fun mergeSegments(rawSegments: List<String>): List<String> {
        val merged = mutableListOf<String>()
        for (segment in rawSegments.map(::normalizeSegment).filter { it.isNotBlank() }) {
            val existingIndex = merged.indexOfFirst {
                it == segment || it.contains(segment) || segment.contains(it)
            }
            when {
                existingIndex < 0 -> merged += segment
                segment.length > merged[existingIndex].length -> merged[existingIndex] = segment
            }
        }
        return merged
    }

    private fun normalizeSegment(text: String): String =
        text.lines()
            .map(::normalizeWhitespace)
            .filter { it.isNotBlank() }
            .joinToString("\n")

    private fun normalizeBodyCandidate(body: String): String =
        body.lines()
            .map(::normalizeWhitespace)
            .filter { it.isNotBlank() }
            .joinToString("\n")

    private fun buildCompactCandidate(
        segments: List<String>,
        separator: String
    ): String {
        val selected = LinkedHashSet<Int>()

        fun trySelect(index: Int) {
            val nextIndexes = (selected + index).sorted()
            val next = nextIndexes.map { segments[it] }.joinToString(separator)
            if (next.length <= MAX_BODY_LENGTH) {
                selected += index
            }
        }

        segments.forEachIndexed { index, segment ->
            if (hasAmount(segment) || hasTransactionHint(segment) || hasDateOrTime(segment)) {
                trySelect(index)
            }
        }
        segments.indices.forEach(::trySelect)

        if (selected.isNotEmpty()) {
            return selected.map { segments[it] }.joinToString(separator)
        }
        return trimCandidate(segments.first())
    }

    private fun scoreBodyCandidate(body: String): Int {
        val flatBody = body.replace('\n', ' ')
        var score = 0
        if (amountPattern.containsMatchIn(flatBody)) score += 40
        if (transactionHints.any { flatBody.contains(it, ignoreCase = true) }) score += 30
        if (datePattern.containsMatchIn(flatBody)) score += 10
        if (timePattern.containsMatchIn(flatBody)) score += 15
        if (flatBody.length in 15..MAX_BODY_LENGTH) score += 10
        if (flatBody.length > MAX_BODY_LENGTH) score -= (flatBody.length - MAX_BODY_LENGTH)
        return score
    }

    private fun resolveTimestamp(
        sbn: StatusBarNotification,
        body: String
    ): Long {
        val now = System.currentTimeMillis()
        val candidates = listOf(sbn.postTime, sbn.notification.`when`)
            .filter { timestamp ->
                timestamp in 1..now && (now - timestamp) <= MAX_NOTIFICATION_AGE_MS
            }

        val preferred = candidates.maxOrNull() ?: now
        if (!timePattern.containsMatchIn(body) && isSuspiciousStartOfDay(preferred, now)) {
            return now
        }
        return preferred
    }

    private fun isSuspiciousStartOfDay(timestamp: Long, now: Long): Boolean {
        val tsCal = Calendar.getInstance().apply { timeInMillis = timestamp }
        val nowCal = Calendar.getInstance().apply { timeInMillis = now }
        return tsCal.get(Calendar.YEAR) == nowCal.get(Calendar.YEAR) &&
            tsCal.get(Calendar.DAY_OF_YEAR) == nowCal.get(Calendar.DAY_OF_YEAR) &&
            tsCal.get(Calendar.HOUR_OF_DAY) == 0 &&
            tsCal.get(Calendar.MINUTE) == 0
    }

    private fun hasAmount(text: String): Boolean = amountPattern.containsMatchIn(text)

    private fun hasTransactionHint(text: String): Boolean =
        transactionHints.any { text.contains(it, ignoreCase = true) }

    private fun hasDateOrTime(text: String): Boolean =
        datePattern.containsMatchIn(text) || timePattern.containsMatchIn(text)

    private fun normalizeWhitespace(text: String): String =
        text.replace(Regex("""\s+"""), " ").trim()

    private fun trimCandidate(body: String): String {
        if (body.length <= MAX_BODY_LENGTH) return body
        return body.take(MAX_BODY_LENGTH).trimEnd()
    }
}
