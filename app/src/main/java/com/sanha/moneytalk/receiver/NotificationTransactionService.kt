package com.sanha.moneytalk.receiver

import android.net.Uri
import android.provider.Telephony
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.sanha.moneytalk.core.sms2.SmsInstantProcessor
import com.sanha.moneytalk.core.sms2.SmsReaderV2
import com.sanha.moneytalk.core.util.DataRefreshEvent
import com.sanha.moneytalk.core.util.MoneyTalkLogger
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/**
 * 메시지 앱 알림을 트리거로 사용해 최근 SMS/MMS/RCS provider row를 즉시 처리한다.
 *
 * SMS는 기존 BroadcastReceiver가 주 경로이고,
 * 이 서비스는 앱 프로세스가 죽어 있을 때 놓치던 RCS/비즈메시지 실시간 처리를 보완한다.
 */
class NotificationTransactionService : NotificationListenerService() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ServiceEntryPoint {
        fun instantProcessor(): SmsInstantProcessor
        fun dataRefreshEvent(): DataRefreshEvent
        fun smsReaderV2(): SmsReaderV2
    }

    companion object {
        private val SMS_URI = Uri.parse("content://sms/inbox")
        private val MMS_URI = Uri.parse("content://mms/inbox")
        private val RCS_URI = Uri.parse("content://im/chat")

        private const val DEDUP_TTL_MS = 5 * 60 * 1000L
        private const val LOOKBACK_MS = 2 * 60 * 1000L
        private const val RECENT_MESSAGE_LOOKUP_LIMIT = 20
        private val PROVIDER_RECHECK_DELAYS_MS = longArrayOf(500L, 1_500L, 3_000L)
    }

    private data class ProviderMessage(
        val address: String,
        val body: String,
        val timestamp: Long,
        val channel: String
    )

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val processedNotifications = ConcurrentHashMap<String, Long>()

    private val entryPoint: ServiceEntryPoint by lazy {
        EntryPointAccessors.fromApplication(
            applicationContext,
            ServiceEntryPoint::class.java
        )
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        NotificationContentParser.selfPackageName = applicationContext.packageName
        MoneyTalkLogger.i("[NotiService] 알림 리스너 연결")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return

        scope.launch {
            try {
                val parsed = NotificationContentParser.parse(sbn) ?: return@launch
                if (!NotificationContentParser.looksLikeFinancialMessage(parsed)) return@launch

                val dedupKey = "${sbn.key}_${parsed.body.hashCode()}"
                cleanExpiredEntries()
                if (processedNotifications.putIfAbsent(dedupKey, System.currentTimeMillis()) != null) {
                    return@launch
                }

                val providerMessage = awaitRecentProviderMessage(parsed)
                if (providerMessage == null) {
                    MoneyTalkLogger.w(
                        "[NotiService] 최근 provider row 미발견: " +
                            "pkg=${parsed.packageName}, body=${parsed.body.take(80)}"
                    )
                    return@launch
                }

                processProviderMessage(providerMessage)
            } catch (e: Exception) {
                MoneyTalkLogger.e("[NotiService] 알림 처리 예외: ${e.message}")
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // no-op
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun awaitRecentProviderMessage(
        parsed: NotificationContentParser.ParsedNotification
    ): ProviderMessage? {
        findRecentProviderMessage(parsed)?.let { return it }

        for (delayMs in PROVIDER_RECHECK_DELAYS_MS) {
            delay(delayMs)
            findRecentProviderMessage(parsed)?.let { return it }
        }

        return null
    }

    private fun findRecentProviderMessage(
        parsed: NotificationContentParser.ParsedNotification
    ): ProviderMessage? {
        val start = parsed.timestamp - LOOKBACK_MS
        val end = parsed.timestamp + LOOKBACK_MS
        return listOfNotNull(
            findRecentSms(parsed.candidateBodies, start, end),
            findRecentMms(parsed.candidateBodies, start, end),
            findRecentRcs(parsed.candidateBodies, start, end)
        ).minByOrNull { message ->
            abs(message.timestamp - parsed.timestamp)
        }
    }

    private fun findRecentSms(
        candidateBodies: Set<String>,
        start: Long,
        end: Long
    ): ProviderMessage? {
        return try {
            applicationContext.contentResolver.query(
                SMS_URI,
                arrayOf(
                    Telephony.Sms.ADDRESS,
                    Telephony.Sms.BODY,
                    Telephony.Sms.DATE
                ),
                "${Telephony.Sms.DATE} >= ? AND ${Telephony.Sms.DATE} <= ?",
                arrayOf(start.toString(), end.toString()),
                "${Telephony.Sms.DATE} DESC LIMIT $RECENT_MESSAGE_LOOKUP_LIMIT"
            )?.use { cursor ->
                val addressIndex = cursor.getColumnIndex(Telephony.Sms.ADDRESS)
                val bodyIndex = cursor.getColumnIndex(Telephony.Sms.BODY)
                val dateIndex = cursor.getColumnIndex(Telephony.Sms.DATE)

                while (cursor.moveToNext()) {
                    val address = cursor.getString(addressIndex) ?: continue
                    val body = cursor.getString(bodyIndex) ?: continue
                    val timestamp = cursor.getLong(dateIndex)
                    if (matchesCandidateBody(body, candidateBodies)) {
                        return ProviderMessage(
                            address = address,
                            body = body,
                            timestamp = timestamp,
                            channel = "sms_inbox"
                        )
                    }
                }
                null
            }
        } catch (e: Exception) {
            MoneyTalkLogger.w("[NotiService] SMS provider 조회 실패: ${e.message}")
            null
        }
    }

    private fun findRecentMms(
        candidateBodies: Set<String>,
        start: Long,
        end: Long
    ): ProviderMessage? {
        val startSec = start / 1000L
        val endSec = end / 1000L
        return try {
            val smsReaderV2 = entryPoint.smsReaderV2()
            applicationContext.contentResolver.query(
                MMS_URI,
                arrayOf("_id", "date"),
                "date >= ? AND date <= ?",
                arrayOf(startSec.toString(), endSec.toString()),
                "date DESC LIMIT $RECENT_MESSAGE_LOOKUP_LIMIT"
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndex("_id")
                val dateIndex = cursor.getColumnIndex("date")

                while (cursor.moveToNext()) {
                    val mmsId = cursor.getString(idIndex) ?: continue
                    val timestamp = cursor.getLong(dateIndex) * 1000L
                    val body = smsReaderV2.getMmsTextBody(applicationContext.contentResolver, mmsId)
                        ?: continue
                    if (!matchesCandidateBody(body, candidateBodies)) continue

                    val address = smsReaderV2.getMmsAddress(applicationContext.contentResolver, mmsId)
                    return ProviderMessage(
                        address = address,
                        body = body,
                        timestamp = timestamp,
                        channel = "mms_inbox"
                    )
                }
                null
            }
        } catch (e: Exception) {
            MoneyTalkLogger.w("[NotiService] MMS provider 조회 실패: ${e.message}")
            null
        }
    }

    private fun findRecentRcs(
        candidateBodies: Set<String>,
        start: Long,
        end: Long
    ): ProviderMessage? {
        return try {
            val smsReaderV2 = entryPoint.smsReaderV2()
            applicationContext.contentResolver.query(
                RCS_URI,
                arrayOf("address", "body", "date"),
                "date >= ? AND date <= ?",
                arrayOf(start.toString(), end.toString()),
                "date DESC LIMIT $RECENT_MESSAGE_LOOKUP_LIMIT"
            )?.use { cursor ->
                val addressIndex = cursor.getColumnIndex("address")
                val bodyIndex = cursor.getColumnIndex("body")
                val dateIndex = cursor.getColumnIndex("date")

                while (cursor.moveToNext()) {
                    val address = cursor.getString(addressIndex) ?: continue
                    val rawBody = cursor.getString(bodyIndex) ?: continue
                    val body = smsReaderV2.extractRcsText(rawBody)
                    val timestamp = cursor.getLong(dateIndex)
                    if (matchesCandidateBody(body, candidateBodies)) {
                        return ProviderMessage(
                            address = address,
                            body = body,
                            timestamp = timestamp,
                            channel = "rcs_im_chat"
                        )
                    }
                }
                null
            }
        } catch (e: Exception) {
            MoneyTalkLogger.w("[NotiService] RCS provider 조회 실패: ${e.message}")
            null
        }
    }

    private suspend fun processProviderMessage(message: ProviderMessage) {
        val instantProcessor = entryPoint.instantProcessor()
        val dataRefreshEvent = entryPoint.dataRefreshEvent()

        try {
            when (val result = instantProcessor.processAndSave(
                address = message.address,
                body = message.body,
                timestampMillis = message.timestamp
            )) {
                is SmsInstantProcessor.Result.Expense -> {
                    MoneyTalkLogger.i(
                        "[NotiService] ${message.channel} 즉시 지출 저장: " +
                            "${result.entity.storeName} ${result.entity.amount}원"
                    )
                    dataRefreshEvent.emitSuspend(DataRefreshEvent.RefreshType.TRANSACTION_ADDED)
                }

                is SmsInstantProcessor.Result.Income -> {
                    MoneyTalkLogger.i(
                        "[NotiService] ${message.channel} 즉시 수입 저장: ${result.entity.amount}원"
                    )
                    dataRefreshEvent.emitSuspend(DataRefreshEvent.RefreshType.TRANSACTION_ADDED)
                }

                is SmsInstantProcessor.Result.Skipped -> {
                    if (message.channel == "rcs_im_chat") {
                        MoneyTalkLogger.i(
                            "[NotiService] ${message.channel} 즉시 처리 스킵 → 배치 동기화 대기"
                        )
                        dataRefreshEvent.emitSuspend(DataRefreshEvent.RefreshType.SMS_RECEIVED)
                    } else {
                        MoneyTalkLogger.i(
                            "[NotiService] ${message.channel} 즉시 처리 스킵"
                        )
                    }
                }

                is SmsInstantProcessor.Result.Error -> {
                    MoneyTalkLogger.w(
                        "[NotiService] ${message.channel} 즉시 처리 실패: ${result.message}"
                    )
                    if (message.channel == "rcs_im_chat") {
                        dataRefreshEvent.emitSuspend(DataRefreshEvent.RefreshType.SMS_RECEIVED)
                    }
                }
            }
        } catch (e: Exception) {
            MoneyTalkLogger.e("[NotiService] provider 메시지 처리 예외: ${e.message}")
            if (message.channel == "rcs_im_chat") {
                dataRefreshEvent.emitSuspend(DataRefreshEvent.RefreshType.SMS_RECEIVED)
            }
        }
    }

    private fun matchesCandidateBody(
        body: String,
        candidateBodies: Set<String>
    ): Boolean {
        val normalizedBody = normalizeWhitespace(body)
        if (normalizedBody.length < 8) return false

        return candidateBodies.any { candidate ->
            normalizedBody == candidate ||
                normalizedBody.contains(candidate) ||
                candidate.contains(normalizedBody)
        }
    }

    private fun normalizeWhitespace(text: String): String =
        text.replace(Regex("""\s+"""), " ").trim()

    private fun cleanExpiredEntries() {
        val now = System.currentTimeMillis()
        processedNotifications.entries.removeIf { now - it.value > DEDUP_TTL_MS }
    }
}
