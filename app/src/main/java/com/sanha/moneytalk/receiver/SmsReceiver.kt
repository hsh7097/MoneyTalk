package com.sanha.moneytalk.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.sanha.moneytalk.core.service.SmsProcessingService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 실시간 SMS 수신 BroadcastReceiver
 *
 * SMS 수신 즉시 SmsProcessingService를 통해 DB에 저장합니다.
 * goAsync() + CoroutineScope로 비동기 작업을 안전하게 수행합니다.
 *
 * 금융 SMS 필터링은 SmsProcessingService 내부에서 처리하므로
 * Receiver는 모든 SMS를 Service에 전달합니다.
 */
@AndroidEntryPoint
class SmsReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SmsReceiver"
    }

    @Inject
    lateinit var smsProcessingService: SmsProcessingService

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            return
        }

        val pendingResult = goAsync()

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)

                for (sms in messages) {
                    val body = sms.messageBody ?: continue
                    val address = sms.originatingAddress ?: "Unknown"
                    val date = sms.timestampMillis

                    try {
                        val result = smsProcessingService.processIncomingSms(body, address, date)
                        if (result.type == SmsProcessingService.ProcessResult.ResultType.EXPENSE_SAVED ||
                            result.type == SmsProcessingService.ProcessResult.ResultType.INCOME_SAVED
                        ) {
                            Log.d(TAG, "SMS 실시간 저장: ${result.type} (${result.storeName} ${result.amount}원)")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "개별 SMS 처리 실패 (계속): ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "SMS 처리 중 오류: ${e.message}", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
