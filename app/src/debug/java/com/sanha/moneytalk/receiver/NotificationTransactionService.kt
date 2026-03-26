package com.sanha.moneytalk.receiver

import android.net.Uri
import android.provider.Telephony
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.sanha.moneytalk.core.datastore.SettingsDataStore
import com.sanha.moneytalk.core.notification.NotificationAppCatalog
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
import java.lang.ref.WeakReference
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
        fun settingsDataStore(): SettingsDataStore
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

        private var instanceRef: WeakReference<NotificationTransactionService>? = null

        /**
         * 앱 포그라운드 진입 시 호출.
         * 선택된 금융/메시지 앱의 알림을 상태바에서 제거한다.
         */
        fun dismissFinancialNotifications() {
            val service = instanceRef?.get() ?: return
            service.scope.launch {
                try {
                    val selectedPackages = service.getSelectedPackages()
                    val activeNotifications = service.activeNotifications ?: return@launch
                    var dismissed = 0
                    for (sbn in activeNotifications) {
                        if (sbn.packageName in selectedPackages) {
                            service.cancelNotification(sbn.key)
                            dismissed++
                        }
                    }
                    if (dismissed > 0) {
                        MoneyTalkLogger.i("[NotiService] 포그라운드 진입: 금융 알림 ${dismissed}건 해제")
                    }
                } catch (e: Exception) {
                    MoneyTalkLogger.w("[NotiService] 알림 해제 실패: ${e.message}")
                }
            }
        }
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val defaultSelectedPackages: Set<String> by lazy {
        NotificationAppCatalog.getDefaultSelectedPackages(
            NotificationAppCatalog.getInstalledLaunchableApps(applicationContext)
        )
    }

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
        instanceRef = WeakReference(this)
        // 자기 자신의 패키지명 설정 (피드백 루프 방지)
        NotificationContentParser.selfPackageName = applicationContext.packageName
        MoneyTalkLogger.i("[NotiService] 알림 리스너 연결됨")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return

        scope.launch {
            try {
                MoneyTalkLogger.i(
                    "[NotiService] onNotificationPosted 진입: pkg=${sbn.packageName}, key=${sbn.key}"
                )

                if (!shouldProcessNotificationPackage(sbn.packageName)) {
                    return@launch
                }

                // 알림 텍스트 추출 (자기 자신은 NotificationContentParser에서 제외)
                val parsed = NotificationContentParser.parse(sbn)
                if (parsed == null) {
                    MoneyTalkLogger.i("[NotiService] 파싱 실패 (null 반환): pkg=${sbn.packageName}")
                    return@launch
                }

                // 5분 TTL dedup
                val dedupKey = "${sbn.key}_${parsed.body.hashCode()}"
                cleanExpiredEntries()
                if (processedNotifications.putIfAbsent(dedupKey, System.currentTimeMillis()) != null) {
                    MoneyTalkLogger.i(
                        "[NotiService] TTL dedup 스킵: pkg=${sbn.packageName}, key=${sbn.key}, " +
                            "body=${parsed.body.take(80)}"
                    )
                    return@launch
                }

                if (shouldSkipMirroredMessageAppNotification(sbn.packageName, parsed)) {
                    MoneyTalkLogger.i(
                        "[NotiService] 메시지 앱 미러 알림 스킵: " +
                            "pkg=${sbn.packageName}, body=${parsed.body.take(50)}"
                    )
                    return@launch
                }

                MoneyTalkLogger.i(
                    "[NotiService] 알림 수신: pkg=${sbn.packageName}, " +
                        "title=${parsed.title.take(30)}, body=${parsed.body.take(80)}, " +
                        "savedTimestamp=${parsed.timestamp}, postTime=${parsed.postedAt}, when=${parsed.notificationWhen}"
                )
                processNotification(parsed)
            } catch (e: Exception) {
                MoneyTalkLogger.e(
                    "[NotiService] onNotificationPosted 예외: pkg=${sbn.packageName}, " +
                        "error=${e.javaClass.simpleName}: ${e.message}"
                )
            }
        }
    }

    private suspend fun getSelectedPackages(): Set<String> {
        val settingsDataStore = entryPoint.settingsDataStore()
        return settingsDataStore.ensureNotificationSelectedAppsInitialized(
            defaultSelectedPackages
        )
    }

    private suspend fun shouldProcessNotificationPackage(packageName: String): Boolean {
        val selectedPackages = getSelectedPackages()
        val allowed = packageName in selectedPackages
        if (!allowed) {
            MoneyTalkLogger.i(
                "[NotiService] 미선택 앱 알림 스킵: pkg=$packageName, " +
                    "selectedCount=${selectedPackages.size}"
            )
        }
        return allowed
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
            MoneyTalkLogger.i(
                "[NotiService] process 시작: address=${parsed.address}, " +
                    "body=${parsed.body.take(100)}, timestamp=${parsed.timestamp}"
            )
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
        instanceRef = null
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
            MoneyTalkLogger.i("[NotiService] 일반 앱 알림 허용: pkg=$packageName")
            return false
        }

        if (!looksLikeFinancialMessage(parsed)) {
            MoneyTalkLogger.i(
                "[NotiService] 메시지 앱 비금융 판정 스킵: pkg=$packageName, body=${parsed.body.take(80)}"
            )
            return true
        }

        val candidateBodies = buildCandidateBodies(parsed)
        if (candidateBodies.isEmpty()) {
            MoneyTalkLogger.i("[NotiService] 메시지 앱 candidate body 없음 → 허용: pkg=$packageName")
            return false
        }

        MoneyTalkLogger.i(
            "[NotiService] 메시지 앱 미러 검사 시작: pkg=$packageName, " +
                "timestamp=${parsed.timestamp}, candidates=${candidateBodies.joinToString(" || ") { it.take(80) }}"
        )

        if (hasRecentProviderMirror(candidateBodies, parsed.timestamp)) {
            MoneyTalkLogger.i("[NotiService] 즉시 provider mirror 발견 → 스킵: pkg=$packageName")
            return true
        }

        // 구글 메시지 계열은 알림 콜백이 SMS/MMS provider 반영보다 먼저 올 수 있다.
        // 짧게 기다리며 재확인해서 일반 SMS/MMS 미러를 최대한 걸러내고,
        // 끝까지 provider에 없을 때만 RCS/알림 전용 메시지로 본다.
        for (delayMs in MESSAGE_MIRROR_RECHECK_DELAYS_MS) {
            delay(delayMs)
            if (hasRecentProviderMirror(candidateBodies, parsed.timestamp)) {
                MoneyTalkLogger.i(
                    "[NotiService] 지연 재확인 mirror 발견 → 스킵: pkg=$packageName, delay=${delayMs}ms"
                )
                return true
            }
            MoneyTalkLogger.i(
                "[NotiService] 지연 재확인 mirror 없음: pkg=$packageName, delay=${delayMs}ms"
            )
        }

        MoneyTalkLogger.i("[NotiService] provider mirror 없음 → 알림 처리 허용: pkg=$packageName")
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
                candidate.contains(normalizedBody)
        }
    }

    private fun normalizeWhitespace(text: String): String =
        text.replace(Regex("""\s+"""), " ").trim()
}
