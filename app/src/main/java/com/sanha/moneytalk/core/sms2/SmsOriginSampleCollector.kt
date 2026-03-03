package com.sanha.moneytalk.core.sms2

import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.sanha.moneytalk.core.util.MoneyTalkLogger
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * sms_origin 표본 수집기
 *
 * 정책:
 * - 성공(outcome=success): sender+type당 fingerprint 단위 upsert + count/lastSeen 누적
 * - 실패(outcome=fail): fingerprint 단위 upsert + count/lastSeen 누적
 * - 동일 fingerprint는 새 row를 만들지 않고 count/lastSeenAt만 갱신
 */
@Singleton
class SmsOriginSampleCollector @Inject constructor(
    private val database: FirebaseDatabase?
) {
    companion object {
        private const val SMS_ORIGIN_PATH = "sms_origin"
        private const val SUCCESS_CACHE_MAX = 500
    }

    data class SuccessSample(
        val normalizedSenderAddress: String,
        val type: String,
        val template: String,
        val originBody: String,
        val maskedBody: String,
        val parseSource: String,
        val cardName: String,
        val groupMemberCount: Int,
        val matchedRuleKey: String = ""
    )

    data class FailureSample(
        val normalizedSenderAddress: String,
        val type: String,
        val originBody: String,
        val parseSource: String,
        val failStage: String,
        val failReason: String,
        val matchedRuleKey: String = ""
    )

    private val successFingerprintsByBucket = ConcurrentHashMap<String, LinkedHashSet<String>>()

    fun resetSession() {
        successFingerprintsByBucket.clear()
    }

    fun collectSuccess(sample: SuccessSample) {
        val db = database ?: return
        if (sample.normalizedSenderAddress.isBlank()) return
        if (sample.type.isBlank()) return

        val fingerprint = sha256Hex(
            buildString {
                append(sample.normalizedSenderAddress)
                append('|')
                append(sample.type)
                append('|')
                append(sample.template.trim())
            }
        )
        val bucketKey = "${sample.normalizedSenderAddress}|${sample.type.lowercase(Locale.ROOT)}"

        val isNewInSession = synchronized(successFingerprintsByBucket) {
            val bucket = successFingerprintsByBucket.getOrPut(bucketKey) { linkedSetOf() }
            val alreadyExists = bucket.contains(fingerprint)
            bucket.add(fingerprint)
            trimSuccessCacheIfNeeded()
            !alreadyExists
        }

        val now = System.currentTimeMillis()
        val ref = db.getReference(SMS_ORIGIN_PATH)
            .child(sample.normalizedSenderAddress)
            .child(sample.type.lowercase(Locale.ROOT))
            .child(fingerprint)

        val payload = mutableMapOf<String, Any>(
            "schemaVersion" to 2,
            "fingerprint" to fingerprint,
            "outcome" to "success",
            "normalizedSenderAddress" to sample.normalizedSenderAddress,
            "type" to sample.type.lowercase(Locale.ROOT),
            "template" to sample.template,
            "originBody" to sample.originBody,
            "maskedBody" to sample.maskedBody,
            "parseSource" to sample.parseSource,
            "cardName" to sample.cardName.ifBlank { "UNKNOWN" },
            "groupMemberCount" to sample.groupMemberCount,
            "count" to ServerValue.increment(1),
            "lastSeenAt" to ServerValue.TIMESTAMP,
            "updatedAt" to ServerValue.TIMESTAMP
        )
        if (isNewInSession) {
            payload["createdAt"] = now
        }
        if (sample.matchedRuleKey.isNotBlank()) payload["matchedRuleKey"] = sample.matchedRuleKey
        appendRuleShapeFields(
            payload = payload,
            normalizedType = normalizeRuleType(sample.type),
            parseSource = sample.parseSource,
            groupMemberCount = sample.groupMemberCount,
            template = sample.template,
            cardName = sample.cardName,
            status = "ACTIVE"
        )

        ref.updateChildren(payload).addOnFailureListener { e ->
            MoneyTalkLogger.w(
                "sms_origin success 표본 업로드 실패: ${e.javaClass.simpleName} ${e.message}"
            )
        }

    }

    fun collectFailure(sample: FailureSample) {
        val db = database ?: return
        if (sample.normalizedSenderAddress.isBlank()) return

        val normalizedType = sample.type.ifBlank { "expense" }.lowercase(Locale.ROOT)
        val failureTemplate = normalizeFailureTemplate(sample.originBody)
        val fingerprint = sha256Hex(
            buildString {
                append(sample.normalizedSenderAddress)
                append('|')
                append(normalizedType)
                append('|')
                append(sample.failStage.lowercase(Locale.ROOT))
                append('|')
                append(sample.failReason.lowercase(Locale.ROOT))
                append('|')
                append(sample.matchedRuleKey.trim())
                append('|')
                append(failureTemplate)
            }
        )
        val ref = db.getReference(SMS_ORIGIN_PATH)
            .child(sample.normalizedSenderAddress)
            .child(normalizedType)
            .child(fingerprint)

        val payload = mutableMapOf<String, Any>(
            "schemaVersion" to 2,
            "fingerprint" to fingerprint,
            "outcome" to "fail",
            "normalizedSenderAddress" to sample.normalizedSenderAddress,
            "type" to normalizedType,
            "originBody" to sample.originBody,
            "maskedBody" to maskBody(sample.originBody),
            "parseSource" to sample.parseSource,
            "failStage" to sample.failStage,
            "failReason" to sample.failReason,
            "failureTemplate" to failureTemplate,
            "count" to ServerValue.increment(1),
            "lastSeenAt" to ServerValue.TIMESTAMP,
            "updatedAt" to ServerValue.TIMESTAMP
        )
        if (sample.matchedRuleKey.isNotBlank()) {
            payload["matchedRuleKey"] = sample.matchedRuleKey
        }
        appendRuleShapeFields(
            payload = payload,
            normalizedType = normalizedType,
            parseSource = sample.parseSource,
            groupMemberCount = 1,
            template = failureTemplate,
            status = "INACTIVE"
        )

        ref.updateChildren(payload)
            .addOnFailureListener { e ->
                MoneyTalkLogger.w(
                    "sms_origin failure 표본 업로드 실패: ${e.javaClass.simpleName} ${e.message}"
                )
            }
    }

    private fun trimSuccessCacheIfNeeded() {
        if (successFingerprintsByBucket.size <= SUCCESS_CACHE_MAX) return
        val iterator = successFingerprintsByBucket.keys.iterator()
        if (iterator.hasNext()) {
            val oldestBucket = iterator.next()
            successFingerprintsByBucket.remove(oldestBucket)
        }
    }

    private fun normalizeRuleType(type: String): String {
        return when (type.lowercase(Locale.ROOT)) {
            "expense", "cancel", "overseas", "income", "payment", "debit" -> type.lowercase(Locale.ROOT)
            else -> "expense"
        }
    }

    private fun deriveRulePriority(parseSource: String, groupMemberCount: Int): Int {
        val base = when (parseSource.lowercase(Locale.ROOT)) {
            "llm_regex" -> 900
            "template_regex" -> 860
            "remote_rule" -> 840
            else -> 820
        }
        val bonus = (groupMemberCount.coerceAtMost(20)) * 3
        return (base + bonus).coerceIn(700, 980)
    }

    private fun appendRuleShapeFields(
        payload: MutableMap<String, Any>,
        normalizedType: String,
        parseSource: String,
        groupMemberCount: Int,
        template: String,
        cardName: String = "",
        status: String
    ) {
        val bodyRegex = normalizeRegexForStorage(buildBodyRegexFromTemplate(template, cardName))
        val amountGroup = if (containsNamedGroup(bodyRegex, "amount")) "amount" else ""
        val storeGroup = if (containsNamedGroup(bodyRegex, "store")) "store" else ""
        val cardGroup = if (containsNamedGroup(bodyRegex, "card")) "card" else ""
        val dateGroup = if (containsNamedGroup(bodyRegex, "date")) "date" else ""

        payload["type"] = normalizedType
        payload["priority"] = deriveRulePriority(parseSource, groupMemberCount)
        payload["status"] = status
        payload["source"] = "sms_origin"
        payload["version"] = 1
        payload["amountGroup"] = amountGroup
        payload["storeGroup"] = storeGroup
        payload["cardGroup"] = cardGroup
        payload["dateGroup"] = dateGroup
        if (!bodyRegex.isNullOrBlank()) {
            payload["bodyRegex"] = bodyRegex

            // sms_rules 호환 ruleKey 생성
            val senderAddress = (payload["normalizedSenderAddress"] as? String).orEmpty()
            val ruleKey = sha256Hex(
                buildString {
                    append(senderAddress); append('|')
                    append(normalizedType); append('|')
                    append(bodyRegex); append('|')
                    append(amountGroup); append('|')
                    append(storeGroup); append('|')
                    append(cardGroup); append('|')
                    append(dateGroup); append('|')
                    append(1) // version
                }
            ).take(24)
            payload["ruleKey"] = ruleKey
        }
    }

    private fun normalizeRegexForStorage(rawPattern: String?): String {
        val trimmed = rawPattern?.trim().orEmpty()
        if (trimmed.isBlank()) return ""

        val deEscaped = decodeOverEscapedRegex(trimmed)
        if (deEscaped != trimmed && runCatching { Regex(deEscaped) }.isSuccess) {
            return deEscaped
        }

        return if (runCatching { Regex(trimmed) }.isSuccess) {
            trimmed
        } else {
            ""
        }
    }

    private fun decodeOverEscapedRegex(pattern: String): String {
        var normalized = pattern
        val replacements = listOf(
            """\\d""" to """\d""",
            """\\D""" to """\D""",
            """\\s""" to """\s""",
            """\\S""" to """\S""",
            """\\w""" to """\w""",
            """\\W""" to """\W""",
            """\\n""" to """\n""",
            """\\t""" to """\t""",
            """\\r""" to """\r""",
            """\\(""" to """\(""",
            """\\)""" to """\)""",
            """\\[""" to """\[""",
            """\\]""" to """\]""",
            """\\{""" to """\{""",
            """\\}""" to """\}""",
            """\\*""" to """\*""",
            """\\+""" to """\+""",
            """\\?""" to """\?""",
            """\\|""" to """\|"""
        )
        replacements.forEach { (from, to) ->
            normalized = normalized.replace(from, to)
        }
        return normalized
    }

    private fun containsNamedGroup(bodyRegex: String?, groupName: String): Boolean {
        if (bodyRegex.isNullOrBlank()) return false
        return bodyRegex.contains("(?<$groupName>")
    }

    private fun buildBodyRegexFromTemplate(template: String, cardName: String = ""): String? {
        var normalizedTemplate = template
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .trim()
        if (normalizedTemplate.isBlank()) return null
        if (!normalizedTemplate.contains("{AMOUNT}")) {
            return null
        }

        // cardName literal → {CARD_NAME} placeholder 치환 (첫 번째 출현만)
        if (cardName.isNotBlank() && normalizedTemplate.contains(cardName)) {
            val idx = normalizedTemplate.indexOf(cardName)
            normalizedTemplate = normalizedTemplate.substring(0, idx) +
                "{CARD_NAME}" +
                normalizedTemplate.substring(idx + cardName.length)
        }

        val enrichedTemplate = injectStorePlaceholderIfNeeded(normalizedTemplate)
        val placeholderPattern = Regex("""\{[A-Z_]+\}""")
        val builder = StringBuilder("(?s)")
        val usedNamedGroups = mutableSetOf<String>()
        var cursor = 0

        for (match in placeholderPattern.findAll(enrichedTemplate)) {
            val start = match.range.first
            if (start > cursor) {
                builder.append(escapeLiteralForRegex(enrichedTemplate.substring(cursor, start)))
            }
            builder.append(placeholderToRegex(match.value, usedNamedGroups))
            cursor = match.range.last + 1
        }
        if (cursor < enrichedTemplate.length) {
            builder.append(escapeLiteralForRegex(enrichedTemplate.substring(cursor)))
        }

        var bodyRegex = builder.toString()
        bodyRegex = bodyRegex.replace("""\[Web발신\]""", """(?:\[Web발신\])?""")
        return if (runCatching { Regex(bodyRegex) }.isSuccess) bodyRegex else null
    }

    private fun injectStorePlaceholderIfNeeded(template: String): String {
        if (template.contains("{STORE}")) return template

        // 패턴 1: placeholder 뒤 같은 줄에 literal + 키워드
        // 예: {CARD_NUM} 가맹점 출금 → {CARD_NUM} {STORE} 출금
        val withFlowKeyword = Regex(
            """(\{CARD_NUM\}|\{CARD_NO\}|\{TIME\}|\{DATE\})\s+([^\{\}\n]{2,40}?)\s+(출금|입금|승인|취소|결제)"""
        )
        withFlowKeyword.find(template)?.let { match ->
            val replacement = "${match.groupValues[1]} {STORE} ${match.groupValues[3]}"
            return template.replaceRange(match.range, replacement)
        }

        // 패턴 2: {CARD_NUM} 다음 줄에 literal store + 그 다음 줄에 키워드
        // 예: {CARD_NUM}\nKB카드출금\n출금 → {CARD_NUM}\n{STORE}\n출금
        val afterCardNumLine = Regex(
            """(\{CARD_NUM\}|\{CARD_NO\})\s*\n([^\{\}\n]{2,40})\n(출금|입금|승인|취소|결제)"""
        )
        afterCardNumLine.find(template)?.let { match ->
            val replacement = "${match.groupValues[1]}\n{STORE}\n${match.groupValues[3]}"
            return template.replaceRange(match.range, replacement)
        }

        // 패턴 3: {DATE} {TIME} 다음 줄에 literal store + 그 다음 줄에 잔액/누적
        // 예: {DATE} {TIME}\n가맹점\n잔액 → {DATE} {TIME}\n{STORE}\n잔액
        val afterDateLine = Regex(
            """(\{DATE\}(?:\s+\{TIME\})?)\s*\n([^\{\}\n]{2,40})\n(.*(?:잔액|누적|합계))"""
        )
        afterDateLine.find(template)?.let { match ->
            val replacement = "${match.groupValues[1]}\n{STORE}\n${match.groupValues[3]}"
            return template.replaceRange(match.range, replacement)
        }

        // 패턴 4: 끝에 trailing store
        val trailingStore = Regex("""(\{DATE\}(?:\s+\{TIME\})?)\s+([^\{\}\n]{2,40})$""")
        trailingStore.find(template)?.let { match ->
            val replacement = "${match.groupValues[1]} {STORE}"
            return template.replaceRange(match.range, replacement)
        }

        return template
    }

    private fun escapeLiteralForRegex(value: String): String {
        val out = StringBuilder()
        value.forEach { ch ->
            when (ch) {
                '\n' -> out.append("""\s*\n\s*""")
                '\r' -> Unit
                ' ', '\t' -> out.append("""\s*""")
                else -> {
                    if ("\\.^$|?*+()[]{}".contains(ch)) {
                        out.append('\\')
                    }
                    out.append(ch)
                }
            }
        }
        return out.toString()
    }

    private fun placeholderToRegex(token: String, usedNamedGroups: MutableSet<String>): String {
        return when (token) {
            "{AMOUNT}" -> namedOrRaw("amount", """[\d,]+""", usedNamedGroups)
            "{STORE}" -> namedOrRaw("store", """.+?""", usedNamedGroups)
            "{DATE}" -> namedOrRaw("date", """\d{1,2}[/.-]\d{1,2}""", usedNamedGroups)
            "{TIME}" -> """\d{1,2}:\d{2}"""
            "{CARD_NAME}" -> namedOrRaw("card", """\S+""", usedNamedGroups)
            "{CARD_NUM}", "{CARD_NO}" -> """\d+\*+\d+"""
            "{USER_NAME}" -> """\S+"""
            "{BALANCE}" -> """[\d,]+"""
            "{N}" -> """\d+"""
            else -> """.+?"""
        }
    }

    private fun namedOrRaw(
        groupName: String,
        rawPattern: String,
        usedNamedGroups: MutableSet<String>
    ): String {
        return if (usedNamedGroups.add(groupName)) {
            "(?<$groupName>$rawPattern)"
        } else {
            rawPattern
        }
    }

    private fun normalizeFailureTemplate(body: String): String {
        return body
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .replace(Regex("""\d{1,3}(,\d{3})+"""), "{AMOUNT}")
            .replace(Regex("""\d{2}/\d{2}"""), "{DATE}")
            .replace(Regex("""\d{2}:\d{2}"""), "{TIME}")
            .replace(Regex("""\d+\*+\d+"""), "{CARD_NO}")
            .replace(Regex("""\d+"""), "{N}")
            .trim()
    }

    private fun maskBody(body: String): String {
        return body
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .replace(Regex("""\d"""), "*")
    }

    private fun sha256Hex(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString(separator = "") { byte ->
            String.format(Locale.ROOT, "%02x", byte)
        }
    }
}
