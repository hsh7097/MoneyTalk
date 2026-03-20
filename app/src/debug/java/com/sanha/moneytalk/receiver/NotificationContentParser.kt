package com.sanha.moneytalk.receiver

import android.app.Notification
import android.service.notification.StatusBarNotification

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

    private data class FinanceAppSpec(
        val packagePrefix: String,
        val label: String,
        val bodyHints: Set<String>
    )

    private val alwaysIgnoredMessageAppPackagePrefixes = listOf(
        "com.samsung.android.messaging",
        "com.skt.prod.dialer",
        "com.android.mms",
        "com.lge.message"
    )

    private val mirrorCheckedMessageAppPackagePrefixes = listOf(
        "com.google.android.apps.messaging",
        "com.android.messaging"
    )

    private val financeAppSpecs = listOf(
        FinanceAppSpec(
            packagePrefix = "com.kakaobank",
            label = "카카오뱅크",
            bodyHints = setOf("카카오뱅크", "카뱅", "kakaobank")
        ),
        FinanceAppSpec(
            packagePrefix = "viva.republica.toss",
            label = "토스",
            bodyHints = setOf("토스", "toss", "토스뱅크")
        ),
        FinanceAppSpec(
            packagePrefix = "com.kbstar",
            label = "KB",
            bodyHints = setOf("KB", "국민", "국민은행", "KB국민")
        ),
        FinanceAppSpec(
            packagePrefix = "com.shinhan",
            label = "신한",
            bodyHints = setOf("신한", "신한카드", "신한은행", "SOL", "쏠")
        ),
        FinanceAppSpec(
            packagePrefix = "com.wooribank",
            label = "우리",
            bodyHints = setOf("우리", "우리카드", "우리은행")
        ),
        FinanceAppSpec(
            packagePrefix = "com.hanabank",
            label = "하나",
            bodyHints = setOf("하나", "하나카드", "하나은행")
        ),
        FinanceAppSpec(
            packagePrefix = "com.nh.smart",
            label = "NH",
            bodyHints = setOf("NH", "농협", "NH농협", "농협카드")
        ),
        FinanceAppSpec(
            packagePrefix = "com.samsungcard",
            label = "삼성카드",
            bodyHints = setOf("삼성", "삼성카드")
        ),
        FinanceAppSpec(
            packagePrefix = "com.hyundaicard",
            label = "현대카드",
            bodyHints = setOf("현대", "현대카드")
        ),
        FinanceAppSpec(
            packagePrefix = "com.lottecard",
            label = "롯데카드",
            bodyHints = setOf("롯데", "롯데카드")
        )
    )

    private val amountPattern = Regex("""[\d,]+원""")
    private val datePattern = Regex("""\d{1,2}[/.-]\d{1,2}""")
    private val transactionHints = listOf(
        "입금", "출금", "결제", "승인", "취소", "송금", "이체", "잔액",
        "자동결제", "후불교통"
    )

    /** 자기 자신의 패키지 (피드백 루프 방지용, 서비스에서 설정) */
    var selfPackageName: String = ""

    /** 알림에서 추출한 텍스트 (SMS의 address/body/timestamp에 대응) */
    data class ParsedNotification(
        val address: String,
        val title: String,
        val text: String,
        val bigText: String,
        val body: String,
        val timestamp: Long
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

        val address = buildAddress(sbn.packageName)

        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""
        val body = selectBestBody(
            packageName = sbn.packageName,
            title = title,
            text = text,
            bigText = bigText
        ) ?: return null

        return ParsedNotification(
            address = address,
            title = title,
            text = text,
            bigText = bigText,
            body = body,
            timestamp = sbn.postTime
        )
    }

    fun shouldAlwaysIgnoreMessageApp(packageName: String): Boolean =
        alwaysIgnoredMessageAppPackagePrefixes.any { packageName.startsWith(it) }

    fun isMirrorCheckedMessageApp(packageName: String): Boolean =
        mirrorCheckedMessageAppPackagePrefixes.any { packageName.startsWith(it) }

    private fun selectBestBody(
        packageName: String,
        title: String,
        text: String,
        bigText: String
    ): String? {
        val parts = listOf(title, text, bigText)
            .map(::normalizeWhitespace)
            .filter { it.isNotBlank() }

        if (parts.isEmpty()) return null

        val candidates = buildList {
            if (parts.isNotEmpty()) add(parts.joinToString(" "))
            if (title.isNotBlank() && text.isNotBlank()) {
                add(normalizeWhitespace("$title $text"))
            }
            if (title.isNotBlank() && bigText.isNotBlank()) {
                add(normalizeWhitespace("$title $bigText"))
            }
            addAll(parts)
        }.distinct()

        val boundedCandidates = candidates.filter { it.length <= MAX_PIPELINE_BODY_LENGTH }
        val pool = if (boundedCandidates.isNotEmpty()) boundedCandidates else candidates

        val best = pool.maxByOrNull(::scoreBodyCandidate) ?: return null
        return addFinanceLabelIfNeeded(packageName, best)
    }

    private fun scoreBodyCandidate(body: String): Int {
        var score = 0
        if (amountPattern.containsMatchIn(body)) score += 40
        if (transactionHints.any { body.contains(it, ignoreCase = true) }) score += 30
        if (containsFinanceInstitutionHint(body)) score += 20
        if (datePattern.containsMatchIn(body)) score += 10
        if (body.length in 15..MAX_PIPELINE_BODY_LENGTH) score += 10
        if (body.length > MAX_PIPELINE_BODY_LENGTH) score -= (body.length - MAX_PIPELINE_BODY_LENGTH)
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

    private fun containsFinanceInstitutionHint(body: String): Boolean =
        financeAppSpecs.any { spec ->
            spec.bodyHints.any { hint -> body.contains(hint, ignoreCase = true) }
        }

    /**
     * 패키지명에서 알림 sender address 생성.
     *
     * "com." 접두사를 제거하고 "."을 "_"로 변환하여 유니크 키를 만든다.
     *
     * 예시:
     * - "com.kakaobank.channel" → "NOTI_kakaobank_channel"
     * - "com.kbstar.kbbank" → "NOTI_kbstar_kbbank"
     * - "viva.republica.toss" → "NOTI_viva_republica_toss"
     */
    private fun buildAddress(packageName: String): String {
        val stripped = packageName.removePrefix("com.")
        return "NOTI_${stripped.replace('.', '_')}"
    }
}
