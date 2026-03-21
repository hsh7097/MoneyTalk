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

/**
 * 앱 알림에서 거래를 감지하는 서비스 (debug 전용).
 *
 * 일반 금융 앱 알림은 바로 처리하고,
 * 메시지 앱 알림은 최근 SMS/MMS/RCS 미러 여부를 확인한 뒤에만 처리한다.
 * 결제/비결제 판별은 기존 SMS 파이프라인(SmsPreFilter 등)이 처리한다.
 *
 * - address = 패키지명 기반 자동 생성 (예: "NOTI_kakaobank")
 * - body = 알림 본문
 * - timestamp = 알림 수신 시각
 *
 * 기존 SMS 파이프라인(regex → 벡터/LLM)을 그대로 탄다:
 * - regex 매칭 성공 → 즉시 저장
 * - regex 미매칭 → 대기 큐에 보관 → 다음 배치 동기화 시 LLM 파이프라인 합류
 *
 * src/debug 소스셋에만 존재하여 release 빌드에 포함되지 않음.
 *
 * @see NotificationContentParser
 * @see SmsInstantProcessor
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
        /** RCS (채팅+) URI — 삼성 메시지 앱 등에서 사용 */
        private val RCS_URI = Uri.parse("content://im/chat")

        /** 동일 알림 중복 처리 방지 TTL (5분) */
        private const val DEDUP_TTL_MS = 5 * 60 * 1000L

        /** 메시지 앱 알림이 SMS/MMS 미러인지 확인할 최근 조회 범위 */
        private const val MESSAGE_PROVIDER_LOOKBACK_MS = 2 * 60 * 1000L

        /** 최근 메시지 본문 비교 개수 */
        private const val RECENT_MESSAGE_LOOKUP_LIMIT = 20

        /** 금융 알림으로 볼 최소 텍스트 길이 */
        private const val MIN_MESSAGE_BODY_LENGTH = 8

        /** 메시지 앱 미러 반영을 기다리며 재확인할 지연 구간 */
        private val MESSAGE_MIRROR_RECHECK_DELAYS_MS = longArrayOf(500L, 1_500L, 3_000L)
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /** dedupKey -> 처리 시각. 동일 알림의 중복 처리 방지 */
    private val processedNotifications = ConcurrentHashMap<String, Long>()

    private val entryPoint: ServiceEntryPoint by lazy {
        EntryPointAccessors.fromApplication(
            applicationContext,
            ServiceEntryPoint::class.java
        )
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        // 자기 자신의 패키지명 설정 (피드백 루프 방지)
        NotificationContentParser.selfPackageName = applicationContext.packageName
        MoneyTalkLogger.i("[NotiService] 알림 리스너 연결됨")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return

        // 알림 텍스트 추출 (자기 자신은 NotificationContentParser에서 제외)
        val parsed = NotificationContentParser.parse(sbn) ?: return

        // 5분 TTL dedup
        val dedupKey = "${sbn.key}_${parsed.body.hashCode()}"
        cleanExpiredEntries()
        if (processedNotifications.putIfAbsent(dedupKey, System.currentTimeMillis()) != null) {
            return
        }

        scope.launch {
            if (shouldSkipMirroredMessageAppNotification(sbn.packageName, parsed)) {
                MoneyTalkLogger.i(
                    "[NotiService] 메시지 앱 미러 알림 스킵: " +
                        "pkg=${sbn.packageName}, body=${parsed.body.take(50)}"
                )
                return@launch
            }

            MoneyTalkLogger.i(
                "[NotiService] 알림 수신: pkg=${sbn.packageName}, " +
                    "title=${parsed.title.take(30)}, body=${parsed.body.take(50)}"
            )
            processNotification(parsed)
        }
    }

    /**
     * 기존 SMS 파이프라인과 동일하게 처리.
     * SmsReceiver의 onReceive() 흐름을 그대로 따른다.
     */
    private suspend fun processNotification(
        parsed: NotificationContentParser.ParsedNotification
    ) {
        val instantProcessor = entryPoint.instantProcessor()
        val dataRefreshEvent = entryPoint.dataRefreshEvent()

        try {
            val result = instantProcessor.processAndSave(
                address = parsed.address,
                body = parsed.body,
                timestampMillis = parsed.timestamp
            )

            when (result) {
                is SmsInstantProcessor.Result.Expense -> {
                    MoneyTalkLogger.i(
                        "[NotiService] 즉시 지출 저장: " +
                            "${result.entity.storeName} ${result.entity.amount}원"
                    )
                    dataRefreshEvent.emitSuspend(
                        DataRefreshEvent.RefreshType.TRANSACTION_ADDED
                    )
                }
                is SmsInstantProcessor.Result.Income -> {
                    MoneyTalkLogger.i(
                        "[NotiService] 즉시 수입 저장: ${result.entity.amount}원"
                    )
                    dataRefreshEvent.emitSuspend(
                        DataRefreshEvent.RefreshType.TRANSACTION_ADDED
                    )
                }
                is SmsInstantProcessor.Result.Skipped -> {
                    // 비결제 알림 → preFilter/incomeFilter에서 걸림 → 무시
                    // 결제 의심 + regex 미매칭 → SmsInstantProcessor 내부에서 대기 큐에 자동 추가됨
                    MoneyTalkLogger.i("[NotiService] 비결제 또는 regex 미매칭 → 스킵")
                }
                is SmsInstantProcessor.Result.Error -> {
                    MoneyTalkLogger.w("[NotiService] 처리 실패: ${result.message}")
                }
            }
        } catch (e: Exception) {
            MoneyTalkLogger.e("[NotiService] 처리 예외: ${e.message}")
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // 필요 없음
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    /** 5분 경과 dedup 항목 제거 */
    private fun cleanExpiredEntries() {
        val now = System.currentTimeMillis()
        processedNotifications.entries.removeIf { now - it.value > DEDUP_TTL_MS }
    }

    private suspend fun shouldSkipMirroredMessageAppNotification(
        packageName: String,
        parsed: NotificationContentParser.ParsedNotification
    ): Boolean {
        if (!NotificationContentParser.isMirrorCheckedMessageApp(packageName)) {
            return false
        }

        if (!looksLikeFinancialMessage(parsed)) {
            return true
        }

        val candidateBodies = buildCandidateBodies(parsed)
        if (candidateBodies.isEmpty()) {
            return false
        }

        if (hasRecentProviderMirror(candidateBodies, parsed.timestamp)) {
            return true
        }

        // 구글 메시지 계열은 알림 콜백이 SMS/MMS provider 반영보다 먼저 올 수 있다.
        // 짧게 기다리며 재확인해서 일반 SMS/MMS 미러를 최대한 걸러내고,
        // 끝까지 provider에 없을 때만 RCS/알림 전용 메시지로 본다.
        for (delayMs in MESSAGE_MIRROR_RECHECK_DELAYS_MS) {
            delay(delayMs)
            if (hasRecentProviderMirror(candidateBodies, parsed.timestamp)) {
                return true
            }
        }

        return false
    }

    private fun looksLikeFinancialMessage(
        parsed: NotificationContentParser.ParsedNotification
    ): Boolean {
        val merged = normalizeWhitespace(
            listOf(parsed.title, parsed.text, parsed.bigText, parsed.body)
                .filter { it.isNotBlank() }
                .joinToString(" ")
        )
        if (!Regex("""[\d,]+원""").containsMatchIn(merged)) {
            return false
        }
        return listOf(
            "입금", "출금", "결제", "승인", "취소", "이체", "송금", "잔액", "사용", "이용"
        ).any { merged.contains(it, ignoreCase = true) }
    }

    private fun buildCandidateBodies(
        parsed: NotificationContentParser.ParsedNotification
    ): Set<String> {
        return listOf(parsed.text, parsed.bigText, parsed.body)
            .map(::normalizeWhitespace)
            .filter { it.length >= MIN_MESSAGE_BODY_LENGTH }
            .toSet()
    }

    private fun hasRecentSmsMirror(
        candidateBodies: Set<String>,
        timestamp: Long
    ): Boolean {
        val start = timestamp - MESSAGE_PROVIDER_LOOKBACK_MS
        val end = timestamp + MESSAGE_PROVIDER_LOOKBACK_MS
        return try {
            applicationContext.contentResolver.query(
                Uri.parse("content://sms/inbox"),
                arrayOf(Telephony.Sms.BODY),
                "${Telephony.Sms.DATE} >= ? AND ${Telephony.Sms.DATE} <= ?",
                arrayOf(start.toString(), end.toString()),
                "${Telephony.Sms.DATE} DESC LIMIT $RECENT_MESSAGE_LOOKUP_LIMIT"
            )?.use { cursor ->
                val bodyIndex = cursor.getColumnIndex(Telephony.Sms.BODY)
                while (cursor.moveToNext()) {
                    val body = cursor.getString(bodyIndex) ?: continue
                    if (matchesCandidateBody(body, candidateBodies)) {
                        return true
                    }
                }
                false
            } ?: false
        } catch (e: Exception) {
            MoneyTalkLogger.w("[NotiService] SMS 미러 확인 실패: ${e.message}")
            false
        }
    }

    private fun hasRecentMmsMirror(
        candidateBodies: Set<String>,
        timestamp: Long
    ): Boolean {
        val startSec = (timestamp - MESSAGE_PROVIDER_LOOKBACK_MS) / 1000L
        val endSec = (timestamp + MESSAGE_PROVIDER_LOOKBACK_MS) / 1000L
        return try {
            applicationContext.contentResolver.query(
                Uri.parse("content://mms/inbox"),
                arrayOf("_id"),
                "date >= ? AND date <= ?",
                arrayOf(startSec.toString(), endSec.toString()),
                "date DESC LIMIT $RECENT_MESSAGE_LOOKUP_LIMIT"
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndex("_id")
                val smsReaderV2 = entryPoint.smsReaderV2()
                while (cursor.moveToNext()) {
                    val mmsId = cursor.getString(idIndex) ?: continue
                    val body = smsReaderV2.getMmsTextBody(applicationContext.contentResolver, mmsId)
                        ?: continue
                    if (matchesCandidateBody(body, candidateBodies)) {
                        return true
                    }
                }
                false
            } ?: false
        } catch (e: Exception) {
            MoneyTalkLogger.w("[NotiService] MMS 미러 확인 실패: ${e.message}")
            false
        }
    }

    private fun hasRecentProviderMirror(
        candidateBodies: Set<String>,
        timestamp: Long
    ): Boolean {
        return hasRecentSmsMirror(candidateBodies, timestamp) ||
            hasRecentMmsMirror(candidateBodies, timestamp) ||
            hasRecentRcsMirror(candidateBodies, timestamp)
    }

    private fun hasRecentRcsMirror(
        candidateBodies: Set<String>,
        timestamp: Long
    ): Boolean {
        val start = timestamp - MESSAGE_PROVIDER_LOOKBACK_MS
        val end = timestamp + MESSAGE_PROVIDER_LOOKBACK_MS
        return try {
            val smsReaderV2 = entryPoint.smsReaderV2()
            applicationContext.contentResolver.query(
                RCS_URI,
                arrayOf("body"),
                "date >= ? AND date <= ?",
                arrayOf(start.toString(), end.toString()),
                "date DESC LIMIT $RECENT_MESSAGE_LOOKUP_LIMIT"
            )?.use { cursor ->
                val bodyIndex = cursor.getColumnIndex("body")
                while (cursor.moveToNext()) {
                    val rawBody = cursor.getString(bodyIndex) ?: continue
                    val body = smsReaderV2.extractRcsText(rawBody)
                    if (matchesCandidateBody(body, candidateBodies)) {
                        return true
                    }
                }
                false
            } ?: false
        } catch (e: Exception) {
            MoneyTalkLogger.w("[NotiService] RCS 미러 확인 실패: ${e.message}")
            false
        }
    }

    private fun matchesCandidateBody(
        body: String,
        candidateBodies: Set<String>
    ): Boolean {
        val normalizedBody = normalizeWhitespace(body)
        if (normalizedBody.length < MIN_MESSAGE_BODY_LENGTH) {
            return false
        }

        return candidateBodies.any { candidate ->
            normalizedBody == candidate ||
                normalizedBody.contains(candidate) ||
                // 역방향 매칭은 짧은 candidate가 긴 body에 포함되는 경우만 허용하면
                // false positive가 발생할 수 있으므로, body가 candidate에 포함될 때는
                // 길이 차이가 적을 때만 매칭 (SMS 본문 트리밍 수준의 차이만 허용)
                (candidate.contains(normalizedBody) &&
                    candidate.length - normalizedBody.length <= 20)
        }
    }

    private fun normalizeWhitespace(text: String): String =
        text.replace(Regex("""\s+"""), " ").trim()
}
