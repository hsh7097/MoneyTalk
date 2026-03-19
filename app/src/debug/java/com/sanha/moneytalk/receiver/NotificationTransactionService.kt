package com.sanha.moneytalk.receiver

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.sanha.moneytalk.core.sms2.SmsInput
import com.sanha.moneytalk.core.sms2.SmsInstantProcessor
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
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * 앱 알림에서 거래를 감지하는 서비스 (debug 전용).
 *
 * 모든 앱의 알림을 수신하여 텍스트를 SMS와 동일하게 [SmsInstantProcessor]에 전달한다.
 * 결제/비결제 판별은 기존 SMS 파이프라인(SmsPreFilter 등)이 처리하므로,
 * 화이트리스트 없이 모든 알림을 파이프라인에 태운다.
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
    }

    companion object {
        /** 동일 알림 중복 처리 방지 TTL (5분) */
        private const val DEDUP_TTL_MS = 5 * 60 * 1000L
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

        MoneyTalkLogger.i(
            "[NotiService] 알림 수신: pkg=${sbn.packageName}, " +
                "title=${parsed.title.take(30)}, body=${parsed.body.take(50)}"
        )

        scope.launch {
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
                    MoneyTalkLogger.i("[NotiService] regex 미매칭 → 배치 파이프라인 대기 큐 등록")
                    // 알림은 SMS inbox에 없으므로 대기 큐에 넣어 배치 동기화 시 합류
                    val smsInput = SmsInput(
                        id = "${parsed.address}_${parsed.timestamp}_${parsed.body.hashCode()}",
                        body = parsed.body,
                        address = parsed.address,
                        date = parsed.timestamp
                    )
                    SmsInstantProcessor.addPendingNotification(smsInput)
                    dataRefreshEvent.emitSuspend(
                        DataRefreshEvent.RefreshType.SMS_RECEIVED
                    )
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
}
