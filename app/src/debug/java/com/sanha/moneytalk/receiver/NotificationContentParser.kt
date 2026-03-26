package com.sanha.moneytalk.receiver

import android.app.Notification
import android.service.notification.StatusBarNotification
import com.sanha.moneytalk.core.util.MoneyTalkLogger
import java.util.Calendar

/**
 * 앱 알림에서 텍스트를 추출하는 파서 (debug 전용).
 *
 * 앱 알림에서 텍스트를 추출한다.
 * 메시지 앱 여부에 따른 중복/허용 판단은 NotificationTransactionService가 담당한다.
 * 결제/비결제 판별은 기존 SMS 파이프라인(SmsPreFilter, SmsIncomeFilter)이 담당하므로,
 * 여기서는 본문 조합과 금융 앱 라벨 보강만 수행한다.
 *
 * address는 패키지명에서 자동 생성 (예: "NOTI_kakaobank")하여
 * SMS의 발신번호와 동일한 역할을 한다.
 */
object NotificationContentParser {

    private const val MAX_PIPELINE_BODY_LENGTH = 130
    private const val MAX_NOTIFICATION_AGE_MS = 30L * 24 * 60 * 60 * 1000
    private val TIME_PATTERN = Regex("""\d{1,2}:\d{2}""")

    private data class FinanceAppSpec(
        val packagePrefix: String,
        val label: String,
        val bodyHints: Set<String>,
        val canonicalAddress: String
    )

    private val messageAppPackagePrefixes = listOf(
        "com.samsung.android.messaging",
        "com.skt.prod.dialer",
        "com.android.mms",
        "com.lge.message",
        "com.google.android.apps.messaging",
        "com.android.messaging"
    )

    private val financeAppSpecs = listOf(
        FinanceAppSpec(
            packagePrefix = "com.kakaobank",
            label = "카카오뱅크",
            bodyHints = setOf("카카오뱅크", "카뱅", "kakaobank"),
            canonicalAddress = "NOTI_kakaobank"
        ),
        FinanceAppSpec(
            packagePrefix = "viva.republica.toss",
            label = "토스",
            bodyHints = setOf("토스", "toss", "토스뱅크"),
            canonicalAddress = "NOTI_toss"
        ),
        FinanceAppSpec(
            packagePrefix = "com.kbstar",
            label = "KB",
            bodyHints = setOf("KB", "국민", "국민은행", "KB국민"),
            canonicalAddress = "NOTI_kbstar"
        ),
        FinanceAppSpec(
            packagePrefix = "com.shinhan",
            label = "신한",
            bodyHints = setOf("신한", "신한카드", "신한은행", "SOL", "쏠"),
            canonicalAddress = "NOTI_shinhan"
        ),
        FinanceAppSpec(
            packagePrefix = "com.wooribank",
            label = "우리",
            bodyHints = setOf("우리", "우리카드", "우리은행"),
            canonicalAddress = "NOTI_woori"
        ),
        FinanceAppSpec(
            packagePrefix = "com.hanabank",
            label = "하나",
            bodyHints = setOf("하나", "하나카드", "하나은행"),
            canonicalAddress = "NOTI_hana"
        ),
        FinanceAppSpec(
            packagePrefix = "com.nh.smart",
            label = "NH",
            bodyHints = setOf("NH", "농협", "NH농협", "농협카드"),
            canonicalAddress = "NOTI_nh"
        ),
        FinanceAppSpec(
            packagePrefix = "com.samsungcard",
            label = "삼성카드",
            bodyHints = setOf("삼성", "삼성카드"),
            canonicalAddress = "NOTI_samsungcard"
        ),
        FinanceAppSpec(
            packagePrefix = "com.hyundaicard",
            label = "현대카드",
            bodyHints = setOf("현대", "현대카드"),
            canonicalAddress = "NOTI_hyundaicard"
        ),
        FinanceAppSpec(
            packagePrefix = "com.lottecard",
            label = "롯데카드",
            bodyHints = setOf("롯데", "롯데카드"),
            canonicalAddress = "NOTI_lottecard"
        )
    )

    private val amountPattern = Regex("""[\d,]+원""")
    private val datePattern = Regex("""\d{1,2}[/.-]\d{1,2}""")
    private val transactionHints = listOf(
        "입금", "출금", "결제", "승인", "취소", "송금", "이체", "잔액",
        "자동결제", "후불교통"
    )

    /** 자기 자신의 패키지 (피드백 루프 방지용, 서비스에서 설정) */
    @Volatile
    var selfPackageName: String = ""

    /** 알림에서 추출한 텍스트 (SMS의 address/body/timestamp에 대응) */
    data class ParsedNotification(
        val address: String,
        val title: String,
        val text: String,
        val bigText: String,
        val body: String,
        val timestamp: Long,
        val receivedAt: Long,
        val postedAt: Long,
        val notificationWhen: Long
    )

    /**
     * StatusBarNotification → ParsedNotification 변환.
     *
     * 자기 자신의 알림이거나 텍스트가 비어있으면 null 반환.
     * 금융 앱은 패키지명을 기반으로 본문에 카드/은행 힌트를 보강한다.
     */
    fun parse(sbn: StatusBarNotification): ParsedNotification? {
        // 자기 자신의 알림은 무시 (피드백 루프 방지)
        if (sbn.packageName == selfPackageName) return null

        val receivedAt = System.currentTimeMillis()
        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""
        val titleBig = extras.getCharSequence(Notification.EXTRA_TITLE_BIG)?.toString() ?: ""
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString() ?: ""
        val summaryText = extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)?.toString() ?: ""
        val conversationTitle = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString() ?: ""
        val textLines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            ?.mapNotNull { it?.toString() }
            .orEmpty()
        val address = buildAddress(sbn.packageName)

        MoneyTalkLogger.i(
            "[NotiParser] raw pkg=${sbn.packageName}, address=$address, " +
                "title=${shortenForLog(title)}, text=${shortenForLog(text)}, " +
                "bigText=${shortenForLog(bigText)}, titleBig=${shortenForLog(titleBig)}, " +
                "subText=${shortenForLog(subText)}, summary=${shortenForLog(summaryText)}, " +
                "conversation=${shortenForLog(conversationTitle)}, " +
                "lines=${textLines.joinToString(" | ") { shortenForLog(it) }}, " +
                "postTime=${sbn.postTime}, when=${sbn.notification.`when`}, receivedAt=$receivedAt"
        )

        val body = selectBestBody(
            packageName = sbn.packageName,
            title = title,
            text = text,
            bigText = bigText,
            titleBig = titleBig,
            subText = subText,
            summaryText = summaryText,
            conversationTitle = conversationTitle,
            textLines = textLines
        ) ?: return null

        val parsed = ParsedNotification(
            address = address,
            title = title,
            text = text,
            bigText = bigText,
            body = body,
            timestamp = resolveTimestamp(sbn, body),
            receivedAt = receivedAt,
            postedAt = sbn.postTime,
            notificationWhen = sbn.notification.`when`
        )

        MoneyTalkLogger.i(
            "[NotiParser] parsed pkg=${sbn.packageName}, address=${parsed.address}, " +
                "body=${shortenForLog(parsed.body, 180)}, savedTimestamp=${parsed.timestamp}, " +
                "postTime=${parsed.postedAt}, when=${parsed.notificationWhen}"
        )

        return parsed
    }

    fun isMirrorCheckedMessageApp(packageName: String): Boolean =
        messageAppPackagePrefixes.any { packageName.startsWith(it) }

    private fun selectBestBody(
        packageName: String,
        title: String,
        text: String,
        bigText: String,
        titleBig: String,
        subText: String,
        summaryText: String,
        conversationTitle: String,
        textLines: List<String>
    ): String? {
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
        val compactSingleLine = compactMultiline.replace('\n', ' ').let(::normalizeWhitespace)

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

        val boundedCandidates = candidates.filter { it.length <= MAX_PIPELINE_BODY_LENGTH }
        val pool = if (boundedCandidates.isNotEmpty()) boundedCandidates else {
            listOf(
                trimCandidate(compactMultiline),
                trimCandidate(compactSingleLine)
            ).filter { it.isNotBlank() }
        }

        val best = pool.maxByOrNull(::scoreBodyCandidate) ?: return null
        return addFinanceLabelIfNeeded(packageName, best)
    }

    private fun scoreBodyCandidate(body: String): Int {
        val flatBody = body.replace('\n', ' ')
        var score = 0
        if (amountPattern.containsMatchIn(flatBody)) score += 40
        if (transactionHints.any { flatBody.contains(it, ignoreCase = true) }) score += 30
        if (containsFinanceInstitutionHint(flatBody)) score += 20
        if (datePattern.containsMatchIn(flatBody)) score += 10
        if (TIME_PATTERN.containsMatchIn(flatBody)) score += 15
        if (flatBody.length in 15..MAX_PIPELINE_BODY_LENGTH) score += 10
        if (flatBody.length > MAX_PIPELINE_BODY_LENGTH) score -= (flatBody.length - MAX_PIPELINE_BODY_LENGTH)
        return score
    }

    private fun addFinanceLabelIfNeeded(packageName: String, body: String): String {
        val appSpec = financeAppSpecs.firstOrNull { packageName.startsWith(it.packagePrefix) }
            ?: return body

        if (appSpec.bodyHints.any { body.contains(it, ignoreCase = true) }) {
            return body
        }

        return "[${appSpec.label}] $body"
    }

    private fun normalizeWhitespace(text: String): String =
        text.replace(Regex("""\s+"""), " ").trim()

    private fun normalizeSegment(text: String): String =
        text.lines()
            .map(::normalizeWhitespace)
            .filter { it.isNotBlank() }
            .joinToString("\n")

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

    private fun buildCompactCandidate(
        segments: List<String>,
        separator: String
    ): String {
        val selected = LinkedHashSet<Int>()

        fun trySelect(index: Int) {
            val nextIndexes = (selected + index).sorted()
            val next = nextIndexes.map { segments[it] }.joinToString(separator)
            if (next.length <= MAX_PIPELINE_BODY_LENGTH) {
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

    private fun normalizeBodyCandidate(body: String): String =
        body.lines()
            .map(::normalizeWhitespace)
            .filter { it.isNotBlank() }
            .joinToString("\n")

    private fun shortenForLog(text: String, maxLength: Int = 80): String {
        val normalized = text.replace("\n", "↵")
        return if (normalized.length <= maxLength) normalized else normalized.take(maxLength) + "..."
    }

    private fun trimCandidate(body: String): String {
        if (body.length <= MAX_PIPELINE_BODY_LENGTH) return body
        return body
            .take(MAX_PIPELINE_BODY_LENGTH)
            .trimEnd()
    }

    private fun hasAmount(text: String): Boolean = amountPattern.containsMatchIn(text)

    private fun hasTransactionHint(text: String): Boolean =
        transactionHints.any { text.contains(it, ignoreCase = true) }

    private fun hasDateOrTime(text: String): Boolean =
        datePattern.containsMatchIn(text) || TIME_PATTERN.containsMatchIn(text)

    private fun containsFinanceInstitutionHint(body: String): Boolean =
        financeAppSpecs.any { spec ->
            spec.bodyHints.any { hint -> body.contains(hint, ignoreCase = true) }
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
        if (!TIME_PATTERN.containsMatchIn(body) && isSuspiciousStartOfDay(preferred, now)) {
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

    /**
     * 패키지명에서 알림 sender address 생성.
     *
     * "com." 접두사를 제거하고 "."을 "_"로 변환하여 유니크 키를 만든다.
     *
     * 예시:
     * - "com.kakaobank.channel" → "NOTI_kakaobank"
     * - "com.kbstar.kbbank" → "NOTI_kbstar"
     * - "viva.republica.toss" → "NOTI_toss"
     */
    private fun buildAddress(packageName: String): String {
        financeAppSpecs.firstOrNull { packageName.startsWith(it.packagePrefix) }
            ?.let { return it.canonicalAddress }
        val stripped = packageName.removePrefix("com.")
        return "NOTI_${stripped.replace('.', '_')}"
    }
}
