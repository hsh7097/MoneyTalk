package com.sanha.moneytalk.receiver

import android.content.ContentResolver
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.sanha.moneytalk.core.sms.SmsChannelProbeCollector
import com.sanha.moneytalk.core.sms.SmsFilter
import com.sanha.moneytalk.core.sms.SmsInstantProcessor
import com.sanha.moneytalk.core.sms.SmsReaderV2
import com.sanha.moneytalk.core.util.DataRefreshEvent
import com.sanha.moneytalk.core.util.MoneyTalkLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 삼성 RCS(content://im/chat) 실시간 감지 ContentObserver.
 *
 * 일부 금융 메시지는 SMS/MMS가 아니라 RCS로 저장되어 BroadcastReceiver를 타지 않으므로,
 * provider 변경을 감지해 즉시 파싱/저장/거래 알림을 수행한다.
 */
@Singleton
class RcsContentObserver @Inject constructor(
    private val smsReaderV2: SmsReaderV2,
    private val instantProcessor: SmsInstantProcessor,
    private val dataRefreshEvent: DataRefreshEvent,
    private val channelProbeCollector: SmsChannelProbeCollector
) : ContentObserver(Handler(Looper.getMainLooper())) {

    companion object {
        private val RCS_URI = Uri.parse("content://im/chat")

        private const val DEDUP_TTL_MS = 5 * 60 * 1000L
        private const val RECENT_RCS_SCAN_LIMIT = 15
        private val BODY_RETRY_DELAYS = longArrayOf(300L, 1000L, 2500L)
    }

    private data class CandidateOutcome(
        val instantSuccess: Boolean = false,
        val shouldTriggerSync: Boolean = false
    )

    private val processedRcsIds = ConcurrentHashMap<String, Long>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val processMutex = Mutex()
    private var cachedContentResolver: ContentResolver? = null

    fun register(contentResolver: ContentResolver) {
        cachedContentResolver = contentResolver
        runCatching {
            contentResolver.registerContentObserver(
                RCS_URI,
                true,
                this
            )
            MoneyTalkLogger.i("[RcsObserver] content://im/chat 감시 등록")
        }.onFailure { e ->
            MoneyTalkLogger.w("[RcsObserver] RCS 감시 등록 실패: ${e.message}")
        }
    }

    override fun onChange(selfChange: Boolean, uri: Uri?) {
        super.onChange(selfChange, uri)
        scope.launch {
            if (!processMutex.tryLock()) return@launch
            try {
                processRecentRcs()
            } catch (e: Exception) {
                MoneyTalkLogger.e("[RcsObserver] 처리 예외: ${e.message}")
            } finally {
                processMutex.unlock()
            }
        }
    }

    private suspend fun processRecentRcs() {
        val contentResolver = cachedContentResolver ?: return
        val recentCandidates = mutableListOf<RcsCandidate>()

        val cursor = contentResolver.query(
            RCS_URI,
            arrayOf("_id", "address", "body", "date"),
            null,
            null,
            "date DESC LIMIT $RECENT_RCS_SCAN_LIMIT"
        ) ?: return

        cursor.use {
            val idIndex = it.getColumnIndex("_id")
            val addressIndex = it.getColumnIndex("address")
            val bodyIndex = it.getColumnIndex("body")
            val dateIndex = it.getColumnIndex("date")
            if (idIndex < 0 || addressIndex < 0 || bodyIndex < 0 || dateIndex < 0) return

            while (it.moveToNext()) {
                val rcsId = it.getString(idIndex) ?: continue
                val address = it.getString(addressIndex) ?: continue
                val rawBody = it.getString(bodyIndex) ?: continue
                val timestampMillis = it.getLong(dateIndex)
                recentCandidates += RcsCandidate(
                    rcsId = rcsId,
                    address = address,
                    rawBody = rawBody,
                    timestampMillis = timestampMillis
                )
            }
        }

        if (recentCandidates.isEmpty()) return

        var hasInstantSuccess = false
        var shouldTriggerSync = false

        for (candidate in recentCandidates.asReversed()) {
            val outcome = processRcsCandidate(contentResolver, candidate)
            hasInstantSuccess = hasInstantSuccess || outcome.instantSuccess
            shouldTriggerSync = shouldTriggerSync || outcome.shouldTriggerSync
        }

        if (hasInstantSuccess) {
            dataRefreshEvent.emitSuspend(DataRefreshEvent.RefreshType.TRANSACTION_ADDED)
        }
        if (shouldTriggerSync) {
            dataRefreshEvent.emitSuspend(DataRefreshEvent.RefreshType.SMS_RECEIVED)
        }
    }

    private suspend fun processRcsCandidate(
        contentResolver: ContentResolver,
        candidate: RcsCandidate
    ): CandidateOutcome {
        cleanExpiredEntries()
        if (processedRcsIds.putIfAbsent(candidate.rcsId, System.currentTimeMillis()) != null) {
            return CandidateOutcome()
        }

        var body = candidate.rawBody
        for (delayMs in BODY_RETRY_DELAYS) {
            val latestRawBody = getRcsRawBody(contentResolver, candidate.rcsId)
            val latestBody = smsReaderV2.extractRcsText(latestRawBody ?: body)
            if (latestBody.isNotBlank()) {
                body = latestBody
                break
            }
            delay(delayMs)
        }

        body = smsReaderV2.extractRcsText(body)
        if (body.isBlank()) {
            processedRcsIds.remove(candidate.rcsId)
            return CandidateOutcome()
        }

        if (SmsFilter.shouldSkipBySender(candidate.address, body)) {
            channelProbeCollector.collect(
                channel = "rcs_observer",
                stage = "sender_skipped",
                address = candidate.address,
                body = body,
                timestamp = candidate.timestampMillis
            )
            return CandidateOutcome()
        }

        channelProbeCollector.collect(
            channel = "rcs_observer",
            stage = "received",
            address = candidate.address,
            body = body,
            timestamp = candidate.timestampMillis
        )

        return try {
            when (val result = instantProcessor.processAndSave(
                candidate.address,
                body,
                candidate.timestampMillis
            )) {
                is SmsInstantProcessor.Result.Expense -> {
                    channelProbeCollector.collect(
                        channel = "rcs_observer",
                        stage = "instant_expense",
                        address = candidate.address,
                        body = body,
                        timestamp = candidate.timestampMillis
                    )
                    MoneyTalkLogger.i(
                        "[RcsObserver] 즉시 지출 저장: ${result.entity.storeName} ${result.entity.amount}원"
                    )
                    CandidateOutcome(instantSuccess = true)
                }

                is SmsInstantProcessor.Result.Income -> {
                    channelProbeCollector.collect(
                        channel = "rcs_observer",
                        stage = "instant_income",
                        address = candidate.address,
                        body = body,
                        timestamp = candidate.timestampMillis
                    )
                    MoneyTalkLogger.i("[RcsObserver] 즉시 수입 저장: ${result.entity.amount}원")
                    CandidateOutcome(instantSuccess = true)
                }

                is SmsInstantProcessor.Result.Skipped -> {
                    channelProbeCollector.collect(
                        channel = "rcs_observer",
                        stage = "instant_skipped",
                        address = candidate.address,
                        body = body,
                        timestamp = candidate.timestampMillis
                    )
                    CandidateOutcome(shouldTriggerSync = true)
                }

                is SmsInstantProcessor.Result.Error -> {
                    channelProbeCollector.collect(
                        channel = "rcs_observer",
                        stage = "instant_error",
                        address = candidate.address,
                        body = body,
                        timestamp = candidate.timestampMillis,
                        note = result.message
                    )
                    CandidateOutcome(shouldTriggerSync = true)
                }
            }
        } catch (e: Exception) {
            MoneyTalkLogger.e("[RcsObserver] 즉시 처리 예외: ${e.message}")
            CandidateOutcome(shouldTriggerSync = true)
        }
    }

    private fun getRcsRawBody(contentResolver: ContentResolver, rcsId: String): String? {
        return runCatching {
            contentResolver.query(
                RCS_URI,
                arrayOf("body"),
                "_id = ?",
                arrayOf(rcsId),
                null
            )?.use { cursor ->
                val bodyIndex = cursor.getColumnIndex("body")
                if (bodyIndex >= 0 && cursor.moveToFirst()) {
                    cursor.getString(bodyIndex)
                } else {
                    null
                }
            }
        }.getOrNull()
    }

    private fun cleanExpiredEntries() {
        val now = System.currentTimeMillis()
        processedRcsIds.entries.removeIf { now - it.value > DEDUP_TTL_MS }
    }

    private data class RcsCandidate(
        val rcsId: String,
        val address: String,
        val rawBody: String,
        val timestampMillis: Long
    )
}
