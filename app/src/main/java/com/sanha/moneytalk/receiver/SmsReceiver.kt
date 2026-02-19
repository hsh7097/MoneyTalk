package com.sanha.moneytalk.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.sanha.moneytalk.core.service.SmsProcessingService
import com.sanha.moneytalk.core.util.SmsParser
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 실시간 SMS 수신 BroadcastReceiver
 *
 * SMS 수신 즉시 SmsProcessingService를 통해 DB에 저장하고 알림을 표시합니다.
 * goAsync() + CoroutineScope로 비동기 작업을 안전하게 수행합니다.
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

        try {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)

            // 금융 SMS 필터링
            data class FinancialSms(val body: String, val address: String, val date: Long)

            val financialSmsList = messages.mapNotNull { sms ->
                val body = sms.messageBody ?: return@mapNotNull null
                val address = sms.originatingAddress ?: "Unknown"
                val date = sms.timestampMillis

                if (SmsParser.isCardPaymentSms(body) || SmsParser.isIncomeSms(body)) {
                    Log.d(TAG, "금융 SMS 감지 - From: $address")
                    FinancialSms(body, address, date)
                } else {
                    null
                }
            }

            if (financialSmsList.isEmpty()) {
                pendingResult.finish()
                return
            }

            // 모든 금융 SMS를 비동기로 처리 후 finish
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    for (sms in financialSmsList) {
                        try {
                            val result = smsProcessingService.processIncomingSms(
                                sms.body, sms.address, sms.date
                            )
                            Log.d(TAG, "SMS 처리 완료: ${result.type}")
                        } catch (e: Exception) {
                            Log.e(TAG, "개별 SMS 처리 실패 (계속): ${e.message}")
                        }
                    }
                } finally {
                    pendingResult.finish()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "SMS 처리 중 오류: ${e.message}", e)
            pendingResult.finish()
        }
    }
}
