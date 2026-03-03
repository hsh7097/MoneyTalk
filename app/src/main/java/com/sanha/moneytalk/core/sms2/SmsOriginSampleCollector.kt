package com.sanha.moneytalk.core.sms2

import android.content.Context
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.sanha.moneytalk.core.util.MoneyTalkLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * sms_origin 표본 수집기
 *
 * 정책:
 * - 성공(outcome=success): sender+type당 고유 fingerprint 최대 3개만 유지
 * - 실패(outcome=fail): fingerprint 단위 upsert + count/lastSeen 누적
 * - 동일 fingerprint는 새 row를 만들지 않고 count/lastSeenAt만 갱신
 */
@Singleton
class SmsOriginSampleCollector @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val database: FirebaseDatabase?
) {
    companion object {
        private const val SMS_ORIGIN_PATH = "sms_origin"
        private const val MAX_SUCCESS_FINGERPRINTS_PER_BUCKET = 3
        private const val SUCCESS_CACHE_MAX = 500
        private const val FAILURE_UPLOAD_GUARD_PREFS = "sms_origin_failure_upload_guard"
        private const val FAILURE_FINGERPRINT_COOLDOWN_MS = 24L * 60L * 60L * 1000L
        private const val MAX_FAILURE_UPLOADS_PER_BUCKET_PER_DAY = 5
    }

    data class SuccessSample(
        val senderAddress: String,
        val normalizedSenderAddress: String,
        val type: String,
        val template: String,
        val originBody: String,
        val maskedBody: String,
        val parseSource: String,
        val cardName: String,
        val groupMemberCount: Int,
        val amountRegex: String = "",
        val storeRegex: String = "",
        val cardRegex: String = "",
        val dateRegex: String = "",
        val matchedRuleKey: String = ""
    )

    data class FailureSample(
        val senderAddress: String,
        val normalizedSenderAddress: String,
        val type: String,
        val originBody: String,
        val parseSource: String,
        val failStage: String,
        val failReason: String,
        val matchedRuleKey: String = ""
    )

    private val successFingerprintsByBucket = ConcurrentHashMap<String, LinkedHashSet<String>>()
    private val failureUploadLock = Any()
    private val failureUploadPrefs by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        appContext.getSharedPreferences(FAILURE_UPLOAD_GUARD_PREFS, Context.MODE_PRIVATE)
    }

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
                append('|')
                append(sample.amountRegex.trim())
                append('|')
                append(sample.storeRegex.trim())
                append('|')
                append(sample.cardRegex.trim())
                append('|')
                append(sample.dateRegex.trim())
            }
        )
        val sampleKey = fingerprint
        val bucketKey = "${sample.normalizedSenderAddress}|${sample.type.lowercase(Locale.ROOT)}"

        val isNewInSession = synchronized(successFingerprintsByBucket) {
            val bucket = successFingerprintsByBucket.getOrPut(bucketKey) { linkedSetOf() }
            val alreadyExists = bucket.contains(fingerprint)
            if (!alreadyExists && bucket.size >= MAX_SUCCESS_FINGERPRINTS_PER_BUCKET) {
                return
            }
            bucket.add(fingerprint)
            trimSuccessCacheIfNeeded()
            !alreadyExists
        }

        val now = System.currentTimeMillis()
        val ref = db.getReference(SMS_ORIGIN_PATH)
            .child(sample.normalizedSenderAddress)
            .child(sample.type.lowercase(Locale.ROOT))
            .child(sampleKey)

        val payload = mutableMapOf<String, Any>(
            "schemaVersion" to 2,
            "sampleKey" to sampleKey,
            "fingerprint" to fingerprint,
            "outcome" to "success",
            "senderAddress" to sample.senderAddress,
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
        if (sample.amountRegex.isNotBlank()) payload["amountRegex"] = sample.amountRegex
        if (sample.storeRegex.isNotBlank()) payload["storeRegex"] = sample.storeRegex
        if (sample.cardRegex.isNotBlank()) payload["cardRegex"] = sample.cardRegex
        if (sample.dateRegex.isNotBlank()) payload["dateRegex"] = sample.dateRegex
        if (sample.matchedRuleKey.isNotBlank()) payload["matchedRuleKey"] = sample.matchedRuleKey

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
        val sampleKey = fingerprint

        if (!shouldUploadFailure(
                normalizedSender = sample.normalizedSenderAddress,
                normalizedType = normalizedType,
                failStage = sample.failStage,
                failReason = sample.failReason,
                fingerprint = fingerprint
            )
        ) {
            return
        }

        val ref = db.getReference(SMS_ORIGIN_PATH)
            .child(sample.normalizedSenderAddress)
            .child(normalizedType)
            .child(sampleKey)

        val payload = mutableMapOf<String, Any>(
            "schemaVersion" to 2,
            "sampleKey" to sampleKey,
            "fingerprint" to fingerprint,
            "outcome" to "fail",
            "senderAddress" to sample.senderAddress,
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

        ref.updateChildren(payload).addOnFailureListener { e ->
            MoneyTalkLogger.w(
                "sms_origin failure 표본 업로드 실패: ${e.javaClass.simpleName} ${e.message}"
            )
        }
    }

    private fun shouldUploadFailure(
        normalizedSender: String,
        normalizedType: String,
        failStage: String,
        failReason: String,
        fingerprint: String
    ): Boolean {
        val now = System.currentTimeMillis()
        val dayBucket = buildString {
            val calendar = java.util.Calendar.getInstance().apply { timeInMillis = now }
            append(calendar.get(java.util.Calendar.YEAR))
            append(String.format(Locale.ROOT, "%02d", calendar.get(java.util.Calendar.MONTH) + 1))
            append(String.format(Locale.ROOT, "%02d", calendar.get(java.util.Calendar.DAY_OF_MONTH)))
        }
        val fingerprintKey = "fp:$fingerprint"
        val bucketHash = sha256Hex(
            buildString {
                append(normalizedSender)
                append('|')
                append(normalizedType)
                append('|')
                append(failStage.lowercase(Locale.ROOT))
                append('|')
                append(failReason.lowercase(Locale.ROOT))
                append('|')
                append(dayBucket)
            }
        ).take(24)
        val bucketKey = "bk:$bucketHash"

        synchronized(failureUploadLock) {
            val lastUploadedAt = failureUploadPrefs.getLong(fingerprintKey, 0L)
            if (lastUploadedAt > 0L && now - lastUploadedAt < FAILURE_FINGERPRINT_COOLDOWN_MS) {
                return false
            }

            val bucketCount = failureUploadPrefs.getInt(bucketKey, 0)
            if (bucketCount >= MAX_FAILURE_UPLOADS_PER_BUCKET_PER_DAY) {
                return false
            }

            failureUploadPrefs.edit()
                .putLong(fingerprintKey, now)
                .putInt(bucketKey, bucketCount + 1)
                .apply()
        }
        return true
    }

    private fun trimSuccessCacheIfNeeded() {
        if (successFingerprintsByBucket.size <= SUCCESS_CACHE_MAX) return
        val iterator = successFingerprintsByBucket.keys.iterator()
        if (iterator.hasNext()) {
            val oldestBucket = iterator.next()
            successFingerprintsByBucket.remove(oldestBucket)
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
