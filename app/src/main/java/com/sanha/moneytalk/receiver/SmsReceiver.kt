package com.sanha.moneytalk.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.sanha.moneytalk.core.sms2.SmsInstantProcessor
import com.sanha.moneytalk.core.util.DataRefreshEvent
import com.sanha.moneytalk.core.util.MoneyTalkLogger
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 실시간 SMS 수신 BroadcastReceiver
 *
 * SMS 수신 시 두 가지 경로로 처리:
 * 1. **즉시 처리**: [SmsInstantProcessor]로 로컬 파싱 → DB 저장 → 알림 표시 (~100ms)
 * 2. **전체 동기화**: [DataRefreshEvent.SMS_RECEIVED] 발행 → MainViewModel이 증분 동기화 수행
 *    (MMS/RCS 보완, Gemini 분류 보완)
 *
 * goAsync()를 사용하여 BroadcastReceiver의 10초 제한 내에서 코루틴 작업을 완료한다.
 */
@AndroidEntryPoint
class SmsReceiver : BroadcastReceiver() {

    @Inject
    lateinit var dataRefreshEvent: DataRefreshEvent

    @Inject
    lateinit var instantProcessor: SmsInstantProcessor

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        // Intent에서 SMS 추출
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) {
            MoneyTalkLogger.i("[SmsReceiver] SMS 추출 실패 → 전체 동기화만 트리거")
            dataRefreshEvent.emit(DataRefreshEvent.RefreshType.SMS_RECEIVED)
            return
        }

        // 멀티파트 SMS 합치기
        val address = messages[0].displayOriginatingAddress
        if (address.isNullOrBlank()) {
            dataRefreshEvent.emit(DataRefreshEvent.RefreshType.SMS_RECEIVED)
            return
        }
        val body = messages.joinToString("") { it.displayMessageBody ?: "" }
        val timestamp = messages[0].timestampMillis

        if (body.isBlank()) {
            dataRefreshEvent.emit(DataRefreshEvent.RefreshType.SMS_RECEIVED)
            return
        }

        MoneyTalkLogger.i("[SmsReceiver] SMS 수신: addr=$address, len=${body.length}")

        // goAsync()로 코루틴 작업 범위 확장 (최대 10초)
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            var instantSuccess = false
            try {
                val result = instantProcessor.processAndSave(address, body, timestamp)
                when (result) {
                    is SmsInstantProcessor.Result.Expense -> {
                        instantSuccess = true
                        MoneyTalkLogger.i("[SmsReceiver] 즉시 지출 저장: ${result.entity.storeName} ${result.entity.amount}원")
                    }
                    is SmsInstantProcessor.Result.Income -> {
                        instantSuccess = true
                        MoneyTalkLogger.i("[SmsReceiver] 즉시 수입 저장: ${result.entity.amount}원")
                    }
                    is SmsInstantProcessor.Result.Skipped ->
                        MoneyTalkLogger.i("[SmsReceiver] 비결제 또는 미매칭 → 전체 동기화 대기")
                    is SmsInstantProcessor.Result.Error ->
                        MoneyTalkLogger.w("[SmsReceiver] 즉시 처리 실패: ${result.message}")
                }
            } catch (e: Exception) {
                MoneyTalkLogger.e("[SmsReceiver] 즉시 처리 예외: ${e.message}")
            } finally {
                if (instantSuccess) {
                    // 즉시 처리 성공 → UI 갱신만 (전체 동기화는 앱 진입 시 silent로)
                    dataRefreshEvent.emit(DataRefreshEvent.RefreshType.TRANSACTION_ADDED)
                } else {
                    // 미처리 → 전체 동기화 트리거
                    dataRefreshEvent.emit(DataRefreshEvent.RefreshType.SMS_RECEIVED)
                }
                pendingResult.finish()
            }
        }
    }
}
