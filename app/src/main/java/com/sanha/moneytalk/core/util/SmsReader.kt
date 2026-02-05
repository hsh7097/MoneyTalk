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

/**
 * SMS 메시지 데이터 클래스
 *
 * @property id 고유 ID (발신번호 + 시간 + 본문 해시로 생성)
 * @property address 발신 번호
 * @property body SMS 본문
 * @property date 수신 시간 (밀리초)
 */
data class SmsMessage(
    val id: String,
    val address: String,
    val body: String,
    val date: Long
)

/**
 * SMS 읽기 유틸리티
 *
 * 기기의 SMS 수신함에서 카드 결제 문자를 읽어옵니다.
 * ContentResolver를 통해 SMS 데이터베이스에 접근합니다.
 *
 * 주의: SMS 권한(READ_SMS)이 필요합니다.
 */
@Singleton
class SmsReader @Inject constructor() {

    /**
     * 모든 카드 결제 문자 읽기 (전체 동기화용)
     *
     * 기기에 저장된 모든 SMS 중 카드 결제 문자만 필터링하여 반환합니다.
     * 최초 동기화 또는 전체 재동기화 시 사용합니다.
     *
     * @param contentResolver ContentResolver
     * @return 카드 결제 문자 목록 (최신순 정렬)
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
     * 특정 기간의 카드 결제 문자 읽기 (증분 동기화용)
     *
     * 지정된 기간 내의 SMS 중 카드 결제 문자만 필터링하여 반환합니다.
     * 마지막 동기화 이후 새로 수신된 문자만 처리할 때 사용합니다.
     *
     * @param contentResolver ContentResolver
     * @param startDate 시작 시간 (밀리초)
     * @param endDate 종료 시간 (밀리초)
     * @return 카드 결제 문자 목록 (최신순 정렬)
     */
    fun readCardSmsByDateRange(
        contentResolver: ContentResolver,
        startDate: Long,
        endDate: Long
    ): List<SmsMessage> {
        val smsList = mutableListOf<SmsMessage>()
        val dateFormat = SimpleDateFormat("MM/dd HH:mm", Locale.KOREA)

        Log.e("sanha", "=== readCardSmsByDateRange 시작 ===")
        Log.e("sanha", "조회 범위: ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.KOREA).format(Date(startDate))} ~ ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.KOREA).format(Date(endDate))}")

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
        // 은행/발신자별로 그룹화
        val smsByAddress = mutableMapOf<String, MutableList<Pair<String, String>>>() // address -> [(날짜, 내용미리보기)]

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

                // 발신자별로 그룹화 (날짜, 본문 앞 30자)
                val dateStr = dateFormat.format(Date(date))
                val preview = if (body.length > 30) body.take(30).replace("\n", " ") + "..." else body.replace("\n", " ")
                smsByAddress.getOrPut(address) { mutableListOf() }.add(dateStr to preview)

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

        // 은행/발신자별 로그 출력
        Log.e("sanha", "=== 발신자(은행)별 SMS 목록 ===")
        smsByAddress.forEach { (address, messages) ->
            Log.e("sanha", "[$address] ${messages.size}건")
            messages.take(10).forEach { (date, preview) ->
                Log.e("sanha", "  $date | $preview")
            }
            if (messages.size > 10) {
                Log.e("sanha", "  ... 외 ${messages.size - 10}건")
            }
        }
        Log.e("sanha", "=== 조회 결과: 전체 $totalCount 건, 카드결제 $cardCount 건 ===")

        return smsList
    }

    /**
     * 모든 수입(입금) 문자 읽기
     *
     * 기기에 저장된 모든 SMS 중 입금 문자만 필터링하여 반환합니다.
     *
     * @param contentResolver ContentResolver
     * @return 수입 문자 목록 (최신순 정렬)
     */
    fun readAllIncomeSms(contentResolver: ContentResolver): List<SmsMessage> {
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

                // 수입 문자만 필터링
                if (SmsParser.isIncomeSms(body)) {
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

        Log.d("SmsReader", "수입 SMS 읽기 완료: ${smsList.size}건")
        return smsList
    }

    /**
     * 특정 기간의 수입(입금) 문자 읽기 (증분 동기화용)
     *
     * @param contentResolver ContentResolver
     * @param startDate 시작 시간 (밀리초)
     * @param endDate 종료 시간 (밀리초)
     * @return 수입 문자 목록 (최신순 정렬)
     */
    fun readIncomeSmsByDateRange(
        contentResolver: ContentResolver,
        startDate: Long,
        endDate: Long
    ): List<SmsMessage> {
        val smsList = mutableListOf<SmsMessage>()

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

                if (SmsParser.isIncomeSms(body)) {
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

        Log.d("SmsReader", "기간별 수입 SMS 읽기 완료: ${smsList.size}건 ($startDate ~ $endDate)")
        return smsList
    }
}
