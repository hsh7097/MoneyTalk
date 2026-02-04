package com.sanha.moneytalk.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.sanha.moneytalk.core.util.SmsParser

/**
 * 실시간 SMS 수신 BroadcastReceiver
 */
class SmsReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SmsReceiver"
        const val ACTION_SMS_RECEIVED = "com.sanha.moneytalk.SMS_RECEIVED"
        const val EXTRA_SMS_BODY = "sms_body"
        const val EXTRA_SMS_ADDRESS = "sms_address"
        const val EXTRA_SMS_DATE = "sms_date"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            return
        }

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)

        for (sms in messages) {
            val body = sms.messageBody ?: continue
            val address = sms.originatingAddress ?: "Unknown"
            val date = sms.timestampMillis

            Log.d(TAG, "SMS Received - From: $address, Body: $body")

            // 카드 결제 문자인지 확인
            if (SmsParser.isCardPaymentSms(body)) {
                Log.d(TAG, "Card payment SMS detected!")

                // 앱 내부로 브로드캐스트 전송
                val smsIntent = Intent(ACTION_SMS_RECEIVED).apply {
                    setPackage(context.packageName)
                    putExtra(EXTRA_SMS_BODY, body)
                    putExtra(EXTRA_SMS_ADDRESS, address)
                    putExtra(EXTRA_SMS_DATE, date)
                }
                context.sendBroadcast(smsIntent)
            }
        }
    }
}
