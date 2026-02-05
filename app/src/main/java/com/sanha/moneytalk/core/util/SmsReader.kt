package com.sanha.moneytalk.core.util

import android.content.ContentResolver
import android.net.Uri
import android.provider.Telephony
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class SmsMessage(
    val id: String,
    val address: String,
    val body: String,
    val date: Long
)

@Singleton
class SmsReader @Inject constructor() {

    /**
     * 저장된 모든 카드 결제 문자 읽기
     */
    fun readAllCardSms(contentResolver: ContentResolver): List<SmsMessage> {
        val smsList = mutableListOf<SmsMessage>()

        val cursor = contentResolver.query(
            Uri.parse("content://sms/inbox"),
            arrayOf(
                Telephony.Sms._ID,
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE
            ),
            null,
            null,
            "${Telephony.Sms.DATE} DESC"
        )

        cursor?.use {
            val idIndex = it.getColumnIndex(Telephony.Sms._ID)
            val addressIndex = it.getColumnIndex(Telephony.Sms.ADDRESS)
            val bodyIndex = it.getColumnIndex(Telephony.Sms.BODY)
            val dateIndex = it.getColumnIndex(Telephony.Sms.DATE)

            while (it.moveToNext()) {
                val id = it.getString(idIndex) ?: continue
                val address = it.getString(addressIndex) ?: continue
                val body = it.getString(bodyIndex) ?: continue
                val date = it.getLong(dateIndex)

                // 카드 결제 문자만 필터링
                if (SmsParser.isCardPaymentSms(body)) {
                    smsList.add(
                        SmsMessage(
                            id = SmsParser.generateSmsId(address, body, date),
                            address = address,
                            body = body,
                            date = date
                        )
                    )
                }
            }
        }

        return smsList
    }

    /**
     * 특정 기간 동안의 카드 결제 문자 읽기
     */
    fun readCardSmsByDateRange(
        contentResolver: ContentResolver,
        startDate: Long,
        endDate: Long
    ): List<SmsMessage> {
        val smsList = mutableListOf<SmsMessage>()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.KOREA)

        Log.e("sanha", "=== readCardSmsByDateRange 시작 ===")
        Log.e("sanha", "조회 범위: ${dateFormat.format(Date(startDate))} ~ ${dateFormat.format(Date(endDate))}")

        val cursor = contentResolver.query(
            Uri.parse("content://sms/inbox"),
            arrayOf(
                Telephony.Sms._ID,
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE
            ),
            "${Telephony.Sms.DATE} BETWEEN ? AND ?",
            arrayOf(startDate.toString(), endDate.toString()),
            "${Telephony.Sms.DATE} DESC"
        )

        var totalCount = 0
        var cardCount = 0

        cursor?.use {
            val idIndex = it.getColumnIndex(Telephony.Sms._ID)
            val addressIndex = it.getColumnIndex(Telephony.Sms.ADDRESS)
            val bodyIndex = it.getColumnIndex(Telephony.Sms.BODY)
            val dateIndex = it.getColumnIndex(Telephony.Sms.DATE)

            while (it.moveToNext()) {
                totalCount++
                val id = it.getString(idIndex) ?: continue
                val address = it.getString(addressIndex) ?: continue
                val body = it.getString(bodyIndex) ?: continue
                val date = it.getLong(dateIndex)

                Log.e("sanha", "SMS[$totalCount] 날짜: ${dateFormat.format(Date(date))}, 발신: $address")

                if (SmsParser.isCardPaymentSms(body)) {
                    cardCount++
                    smsList.add(
                        SmsMessage(
                            id = SmsParser.generateSmsId(address, body, date),
                            address = address,
                            body = body,
                            date = date
                        )
                    )
                }
            }
        }

        Log.e("sanha", "=== 조회 결과: 전체 $totalCount 건, 카드결제 $cardCount 건 ===")

        return smsList
    }
}
