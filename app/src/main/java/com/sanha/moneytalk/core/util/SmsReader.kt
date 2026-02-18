package com.sanha.moneytalk.core.util

import android.content.ContentResolver
import android.net.Uri
import android.provider.Telephony
import android.util.Log
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
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
 * SMS/MMS/RCS 읽기 유틸리티
 *
 * 기기의 SMS/MMS/RCS 수신함에서 카드 결제 문자를 읽어옵니다.
 * ContentResolver를 통해 SMS/MMS/RCS 데이터베이스에 접근합니다.
 * 한국 카드사(신한, 삼성 등)는 MMS 또는 RCS(채팅+)로 알림을 보내는 경우가 많아
 * SMS뿐 아니라 MMS, RCS도 함께 읽습니다.
 *
 * - SMS: content://sms/inbox (date 밀리초)
 * - MMS: content://mms/inbox (date 초 단위 → 밀리초 변환)
 * - RCS: content://im/chat (date 밀리초, 삼성 기기)
 *
 * 주의: SMS 권한(READ_SMS)이 필요합니다.
 */
@Singleton
class SmsReader @Inject constructor() {

    companion object {
        private const val TAG = "SmsReader"

        // MMS URI
        private val MMS_URI = Uri.parse("content://mms")
        private val MMS_INBOX_URI = Uri.parse("content://mms/inbox")
        private val MMS_PART_URI = Uri.parse("content://mms/part")

        // RCS (채팅+) URI — 삼성 메시지 앱에서 사용
        private val RCS_URI = Uri.parse("content://im/chat")
    }

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
                it.getString(idIndex) ?: continue  // null이면 skip
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
        Log.e(
            "sanha",
            "조회 범위: ${
                SimpleDateFormat(
                    "yyyy-MM-dd HH:mm:ss",
                    Locale.KOREA
                ).format(Date(startDate))
            } ~ ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.KOREA).format(Date(endDate))}"
        )
        Log.e("sanha", "startDate(ms): $startDate, endDate(ms): $endDate")

        val cursor = contentResolver.query(
            Uri.parse("content://sms/inbox"),
            arrayOf(
                Telephony.Sms._ID,
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE
            ),
            "${Telephony.Sms.DATE} >= ? AND ${Telephony.Sms.DATE} <= ?",
            arrayOf(startDate.toString(), endDate.toString()),
            "${Telephony.Sms.DATE} DESC"
        )

        var totalCount = 0
        var cardCount = 0
        // 은행/발신자별로 그룹화
        val smsByAddress =
            mutableMapOf<String, MutableList<Pair<String, String>>>() // address -> [(날짜, 내용미리보기)]

        cursor?.use {
            val idIndex = it.getColumnIndex(Telephony.Sms._ID)
            val addressIndex = it.getColumnIndex(Telephony.Sms.ADDRESS)
            val bodyIndex = it.getColumnIndex(Telephony.Sms.BODY)
            val dateIndex = it.getColumnIndex(Telephony.Sms.DATE)

            while (it.moveToNext()) {
                totalCount++
                it.getString(idIndex) ?: continue  // null이면 skip
                val address = it.getString(addressIndex) ?: continue
                val body = it.getString(bodyIndex) ?: continue
                val date = it.getLong(dateIndex)

                // 발신자별로 그룹화 (날짜, 본문 앞 30자)
                val dateStr = dateFormat.format(Date(date))
                val preview = if (body.length > 30) body.take(30)
                    .replace("\n", " ") + "..." else body.replace("\n", " ")
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

        // 진단용: 날짜 범위 조회 결과가 0건이면 최근 SMS 5건 확인
        if (totalCount == 0) {
            Log.e("sanha", "=== 진단: 날짜 범위 결과 0건, 최근 SMS 확인 ===")
            val diagCursor = contentResolver.query(
                Uri.parse("content://sms/inbox"),
                arrayOf(Telephony.Sms.DATE, Telephony.Sms.ADDRESS, Telephony.Sms.BODY),
                null,
                null,
                "${Telephony.Sms.DATE} DESC LIMIT 5"
            )
            diagCursor?.use { dc ->
                val dDateIdx = dc.getColumnIndex(Telephony.Sms.DATE)
                val dAddrIdx = dc.getColumnIndex(Telephony.Sms.ADDRESS)
                val dBodyIdx = dc.getColumnIndex(Telephony.Sms.BODY)
                var idx = 0
                while (dc.moveToNext()) {
                    val d = dc.getLong(dDateIdx)
                    val addr = dc.getString(dAddrIdx) ?: "?"
                    val body = dc.getString(dBodyIdx) ?: ""
                    val preview = if (body.length > 40) body.take(40)
                        .replace("\n", " ") + "..." else body.replace("\n", " ")
                    Log.e(
                        "sanha",
                        "  최근SMS[${idx}]: date=$d (${
                            SimpleDateFormat(
                                "yyyy-MM-dd HH:mm:ss",
                                Locale.KOREA
                            ).format(Date(d))
                        }) addr=$addr body=$preview"
                    )
                    idx++
                }
                if (idx == 0) Log.e("sanha", "  SMS 수신함이 완전히 비어있음")
            }
        }

        return smsList
    }

    /**
     * 특정 기간의 모든 SMS 읽기 (필터링 없이)
     *
     * 하이브리드 분류기에서 사용: 정규식으로 걸러지지 않은 SMS도
     * 벡터/LLM 분류를 위해 전달합니다.
     *
     * @param contentResolver ContentResolver
     * @param startDate 시작 시간 (밀리초)
     * @param endDate 종료 시간 (밀리초)
     * @return 모든 SMS 목록 (최신순 정렬)
     */
    fun readAllSmsByDateRange(
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
            "${Telephony.Sms.DATE} >= ? AND ${Telephony.Sms.DATE} <= ?",
            arrayOf(startDate.toString(), endDate.toString()),
            "${Telephony.Sms.DATE} DESC"
        )

        cursor?.use {
            val idIndex = it.getColumnIndex(Telephony.Sms._ID)
            val addressIndex = it.getColumnIndex(Telephony.Sms.ADDRESS)
            val bodyIndex = it.getColumnIndex(Telephony.Sms.BODY)
            val dateIndex = it.getColumnIndex(Telephony.Sms.DATE)

            while (it.moveToNext()) {
                it.getString(idIndex) ?: continue  // null이면 skip
                val address = it.getString(addressIndex) ?: continue
                val body = it.getString(bodyIndex) ?: continue
                val date = it.getLong(dateIndex)

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

        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.KOREA)
        Log.e("sanha", "SmsReader[readAllSmsByDateRange] : ${smsList.size}건, 범위: ${sdf.format(java.util.Date(startDate))} ~ ${sdf.format(java.util.Date(endDate))}")
        if (smsList.isNotEmpty()) {
            val oldest = smsList.minByOrNull { it.date }
            val newest = smsList.maxByOrNull { it.date }
            Log.e("sanha", "SmsReader[readAllSmsByDateRange] : 가장 오래된 SMS=${oldest?.date?.let { sdf.format(java.util.Date(it)) }}, 최신 SMS=${newest?.date?.let { sdf.format(java.util.Date(it)) }}")
        }
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
                it.getString(idIndex) ?: continue  // null이면 skip
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
            "${Telephony.Sms.DATE} >= ? AND ${Telephony.Sms.DATE} <= ?",
            arrayOf(startDate.toString(), endDate.toString()),
            "${Telephony.Sms.DATE} DESC"
        )

        cursor?.use {
            val idIndex = it.getColumnIndex(Telephony.Sms._ID)
            val addressIndex = it.getColumnIndex(Telephony.Sms.ADDRESS)
            val bodyIndex = it.getColumnIndex(Telephony.Sms.BODY)
            val dateIndex = it.getColumnIndex(Telephony.Sms.DATE)

            while (it.moveToNext()) {
                it.getString(idIndex) ?: continue  // null이면 skip
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

        Log.d(TAG, "기간별 수입 SMS 읽기 완료: ${smsList.size}건 ($startDate ~ $endDate)")
        return smsList
    }

    // ========== MMS 읽기 기능 ==========

    /**
     * MMS 메시지의 텍스트 본문 읽기
     * MMS는 content://mms/part에 파트별로 저장되어 있음
     *
     * @param contentResolver ContentResolver
     * @param mmsId MMS 메시지 ID
     * @return 텍스트 본문 (없으면 null)
     */
    private fun getMmsTextBody(contentResolver: ContentResolver, mmsId: String): String? {
        val sb = StringBuilder()
        try {
            val cursor = contentResolver.query(
                MMS_PART_URI,
                arrayOf("_id", "ct", "text"),
                "mid = ?",
                arrayOf(mmsId),
                null
            )
            cursor?.use {
                val ctIndex = it.getColumnIndex("ct")
                val textIndex = it.getColumnIndex("text")
                val idIndex = it.getColumnIndex("_id")

                while (it.moveToNext()) {
                    val contentType = it.getString(ctIndex) ?: continue

                    if (contentType == "text/plain") {
                        // text 컬럼에서 직접 읽기 시도
                        val text = it.getString(textIndex)
                        if (!text.isNullOrBlank()) {
                            sb.append(text)
                        } else {
                            // text 컬럼이 비어있으면 _data에서 InputStream으로 읽기
                            val partId = it.getString(idIndex) ?: continue
                            try {
                                val partUri = Uri.parse("content://mms/part/$partId")
                                contentResolver.openInputStream(partUri)?.use { inputStream ->
                                    val reader =
                                        BufferedReader(InputStreamReader(inputStream, "UTF-8"))
                                    sb.append(reader.readText())
                                }
                            } catch (e: Exception) {
                                Log.w(TAG, "MMS part 읽기 실패 (partId=$partId): ${e.message}")
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "MMS 본문 읽기 실패 (mmsId=$mmsId): ${e.message}")
        }
        return sb.toString().takeIf { it.isNotBlank() }
    }

    /**
     * MMS 발신 번호 읽기
     * MMS는 content://mms/{id}/addr에 주소가 별도 저장됨
     * type=137(PduHeaders.FROM)이 발신자
     *
     * @param contentResolver ContentResolver
     * @param mmsId MMS 메시지 ID
     * @return 발신 번호 (없으면 "unknown")
     */
    private fun getMmsAddress(contentResolver: ContentResolver, mmsId: String): String {
        try {
            val addrUri = Uri.parse("content://mms/$mmsId/addr")
            val cursor = contentResolver.query(
                addrUri,
                arrayOf("address", "type"),
                null,
                null,
                null
            )
            cursor?.use {
                val addressIndex = it.getColumnIndex("address")
                val typeIndex = it.getColumnIndex("type")

                while (it.moveToNext()) {
                    val type = it.getInt(typeIndex)
                    // type 137 = FROM (PduHeaders.FROM)
                    if (type == 137) {
                        val address = it.getString(addressIndex)
                        if (!address.isNullOrBlank() && address != "insert-address-token") {
                            return address
                        }
                    }
                }
                // FROM이 없으면 첫 번째 주소 반환
                if (it.moveToFirst()) {
                    val address = it.getString(addressIndex)
                    if (!address.isNullOrBlank() && address != "insert-address-token") {
                        return address
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "MMS 주소 읽기 실패 (mmsId=$mmsId): ${e.message}")
        }
        return "unknown"
    }

    /**
     * 모든 MMS에서 카드 결제 문자 읽기 (전체 동기화용)
     * SMS와 동일한 SmsMessage 형태로 반환하여 기존 파이프라인에 바로 통합 가능
     */
    fun readAllCardMms(contentResolver: ContentResolver): List<SmsMessage> {
        val mmsList = mutableListOf<SmsMessage>()

        try {
            val cursor = contentResolver.query(
                MMS_INBOX_URI,
                arrayOf("_id", "date", "msg_box"),
                "msg_box = 1", // 1 = 수신함
                null,
                "date DESC"
            )

            var totalCount = 0
            var cardCount = 0

            cursor?.use {
                val idIndex = it.getColumnIndex("_id")
                val dateIndex = it.getColumnIndex("date")

                while (it.moveToNext()) {
                    totalCount++
                    val mmsId = it.getString(idIndex) ?: continue
                    // MMS date는 초 단위! → 밀리초로 변환
                    val dateSec = it.getLong(dateIndex)
                    val dateMs = dateSec * 1000

                    val body = getMmsTextBody(contentResolver, mmsId) ?: continue
                    val address = getMmsAddress(contentResolver, mmsId)

                    if (SmsParser.isCardPaymentSms(body)) {
                        cardCount++
                        mmsList.add(
                            SmsMessage(
                                id = SmsParser.generateSmsId(address, body, dateMs),
                                address = address,
                                body = body,
                                date = dateMs
                            )
                        )
                    }
                }
            }

            Log.d(TAG, "전체 MMS 카드결제 읽기: 전체 $totalCount 건, 카드결제 $cardCount 건")
        } catch (e: Exception) {
            Log.e(TAG, "MMS 전체 읽기 실패: ${e.message}")
        }

        return mmsList
    }

    /**
     * 특정 기간의 MMS에서 카드 결제 문자 읽기 (증분 동기화용)
     * MMS date는 초 단위이므로 밀리초 파라미터를 초로 변환하여 조회
     */
    fun readCardMmsByDateRange(
        contentResolver: ContentResolver,
        startDate: Long,
        endDate: Long
    ): List<SmsMessage> {
        val mmsList = mutableListOf<SmsMessage>()
        val startSec = startDate / 1000
        val endSec = endDate / 1000

        Log.e(TAG, "=== readCardMmsByDateRange 시작 ===")
        Log.e(
            TAG,
            "MMS 조회 범위: ${
                SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.KOREA).format(
                    Date(startDate)
                )
            } ~ ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.KOREA).format(Date(endDate))}"
        )

        try {
            val cursor = contentResolver.query(
                MMS_INBOX_URI,
                arrayOf("_id", "date", "msg_box"),
                "date >= ? AND date <= ?",
                arrayOf(startSec.toString(), endSec.toString()),
                "date DESC"
            )

            var totalCount = 0
            var cardCount = 0

            cursor?.use {
                val idIndex = it.getColumnIndex("_id")
                val dateIndex = it.getColumnIndex("date")

                while (it.moveToNext()) {
                    totalCount++
                    val mmsId = it.getString(idIndex) ?: continue
                    val dateSec = it.getLong(dateIndex)
                    val dateMs = dateSec * 1000

                    val body = getMmsTextBody(contentResolver, mmsId) ?: continue
                    val address = getMmsAddress(contentResolver, mmsId)

                    val preview = if (body.length > 30) body.take(30)
                        .replace("\n", " ") + "..." else body.replace("\n", " ")
                    Log.d(TAG, "  MMS[$mmsId]: addr=$address, body=$preview")

                    if (SmsParser.isCardPaymentSms(body)) {
                        cardCount++
                        mmsList.add(
                            SmsMessage(
                                id = SmsParser.generateSmsId(address, body, dateMs),
                                address = address,
                                body = body,
                                date = dateMs
                            )
                        )
                    }
                }
            }

            Log.e(TAG, "=== MMS 조회 결과: 전체 $totalCount 건, 카드결제 $cardCount 건 ===")

            // 진단용: MMS 0건이면 최근 MMS 5건 확인
            if (totalCount == 0) {
                Log.e(TAG, "=== 진단: MMS 날짜 범위 결과 0건, 최근 MMS 확인 ===")
                val diagCursor = contentResolver.query(
                    MMS_INBOX_URI,
                    arrayOf("_id", "date"),
                    null,
                    null,
                    "date DESC"
                )
                diagCursor?.use { dc ->
                    val dIdIdx = dc.getColumnIndex("_id")
                    val dDateIdx = dc.getColumnIndex("date")
                    var idx = 0
                    while (dc.moveToNext() && idx < 5) {
                        val id = dc.getString(dIdIdx) ?: continue
                        val d = dc.getLong(dDateIdx)
                        val dMs = d * 1000
                        val body = getMmsTextBody(contentResolver, id) ?: "(본문없음)"
                        val preview = if (body.length > 40) body.take(40)
                            .replace("\n", " ") + "..." else body.replace("\n", " ")
                        Log.e(
                            TAG,
                            "  최근MMS[$idx]: id=$id date=$d (${
                                SimpleDateFormat(
                                    "yyyy-MM-dd HH:mm:ss",
                                    Locale.KOREA
                                ).format(Date(dMs))
                            }) body=$preview"
                        )
                        idx++
                    }
                    if (idx == 0) Log.e(TAG, "  MMS 수신함이 비어있음")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "MMS 기간별 읽기 실패: ${e.message}")
        }

        return mmsList
    }

    /**
     * 특정 기간의 모든 MMS 읽기 (필터링 없이, 하이브리드 분류기용)
     */
    fun readAllMmsByDateRange(
        contentResolver: ContentResolver,
        startDate: Long,
        endDate: Long
    ): List<SmsMessage> {
        val mmsList = mutableListOf<SmsMessage>()
        val startSec = startDate / 1000
        val endSec = endDate / 1000

        try {
            val cursor = contentResolver.query(
                MMS_INBOX_URI,
                arrayOf("_id", "date", "msg_box"),
                "date >= ? AND date <= ?",
                arrayOf(startSec.toString(), endSec.toString()),
                "date DESC"
            )

            cursor?.use {
                val idIndex = it.getColumnIndex("_id")
                val dateIndex = it.getColumnIndex("date")

                while (it.moveToNext()) {
                    val mmsId = it.getString(idIndex) ?: continue
                    val dateSec = it.getLong(dateIndex)
                    val dateMs = dateSec * 1000

                    val body = getMmsTextBody(contentResolver, mmsId) ?: continue
                    val address = getMmsAddress(contentResolver, mmsId)

                    mmsList.add(
                        SmsMessage(
                            id = SmsParser.generateSmsId(address, body, dateMs),
                            address = address,
                            body = body,
                            date = dateMs
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "MMS 전체 읽기(기간별) 실패: ${e.message}")
        }

        Log.d(TAG, "전체 MMS 읽기 완료: ${mmsList.size}건 ($startDate ~ $endDate)")
        return mmsList
    }

    /**
     * 모든 수입(입금) MMS 읽기
     */
    fun readAllIncomeMms(contentResolver: ContentResolver): List<SmsMessage> {
        val mmsList = mutableListOf<SmsMessage>()

        try {
            val cursor = contentResolver.query(
                MMS_INBOX_URI,
                arrayOf("_id", "date", "msg_box"),
                "msg_box = 1",
                null,
                "date DESC"
            )

            cursor?.use {
                val idIndex = it.getColumnIndex("_id")
                val dateIndex = it.getColumnIndex("date")

                while (it.moveToNext()) {
                    val mmsId = it.getString(idIndex) ?: continue
                    val dateSec = it.getLong(dateIndex)
                    val dateMs = dateSec * 1000

                    val body = getMmsTextBody(contentResolver, mmsId) ?: continue
                    val address = getMmsAddress(contentResolver, mmsId)

                    if (SmsParser.isIncomeSms(body)) {
                        mmsList.add(
                            SmsMessage(
                                id = SmsParser.generateSmsId(address, body, dateMs),
                                address = address,
                                body = body,
                                date = dateMs
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "MMS 수입 전체 읽기 실패: ${e.message}")
        }

        Log.d(TAG, "수입 MMS 읽기 완료: ${mmsList.size}건")
        return mmsList
    }

    /**
     * 특정 기간의 수입(입금) MMS 읽기
     */
    fun readIncomeMmsByDateRange(
        contentResolver: ContentResolver,
        startDate: Long,
        endDate: Long
    ): List<SmsMessage> {
        val mmsList = mutableListOf<SmsMessage>()
        val startSec = startDate / 1000
        val endSec = endDate / 1000

        try {
            val cursor = contentResolver.query(
                MMS_INBOX_URI,
                arrayOf("_id", "date", "msg_box"),
                "date >= ? AND date <= ?",
                arrayOf(startSec.toString(), endSec.toString()),
                "date DESC"
            )

            cursor?.use {
                val idIndex = it.getColumnIndex("_id")
                val dateIndex = it.getColumnIndex("date")

                while (it.moveToNext()) {
                    val mmsId = it.getString(idIndex) ?: continue
                    val dateSec = it.getLong(dateIndex)
                    val dateMs = dateSec * 1000

                    val body = getMmsTextBody(contentResolver, mmsId) ?: continue
                    val address = getMmsAddress(contentResolver, mmsId)

                    if (SmsParser.isIncomeSms(body)) {
                        mmsList.add(
                            SmsMessage(
                                id = SmsParser.generateSmsId(address, body, dateMs),
                                address = address,
                                body = body,
                                date = dateMs
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "MMS 수입 기간별 읽기 실패: ${e.message}")
        }

        Log.d(TAG, "기간별 수입 MMS 읽기 완료: ${mmsList.size}건 ($startDate ~ $endDate)")
        return mmsList
    }

    // ========== SMS + MMS + RCS 통합 읽기 ==========

    /**
     * SMS + MMS + RCS 통합: 모든 카드 결제 문자 읽기
     */
    fun readAllCardMessages(contentResolver: ContentResolver): List<SmsMessage> {
        val smsList = readAllCardSms(contentResolver)
        val mmsList = readAllCardMms(contentResolver)
        val rcsList = readAllCardRcs(contentResolver)
        val combined =
            (smsList + mmsList + rcsList).distinctBy { it.id }.sortedByDescending { it.date }
        Log.d(
            TAG,
            "통합 카드결제 읽기: SMS ${smsList.size}건 + MMS ${mmsList.size}건 + RCS ${rcsList.size}건 = 총 ${combined.size}건"
        )
        return combined
    }

    /**
     * SMS + MMS + RCS 통합: 특정 기간의 카드 결제 문자 읽기
     */
    fun readCardMessagesByDateRange(
        contentResolver: ContentResolver,
        startDate: Long,
        endDate: Long
    ): List<SmsMessage> {
        val smsList = readCardSmsByDateRange(contentResolver, startDate, endDate)
        val mmsList = readCardMmsByDateRange(contentResolver, startDate, endDate)
        val rcsList = readCardRcsByDateRange(contentResolver, startDate, endDate)
        val combined =
            (smsList + mmsList + rcsList).distinctBy { it.id }.sortedByDescending { it.date }
        Log.d(
            TAG,
            "통합 기간별 카드결제 읽기: SMS ${smsList.size}건 + MMS ${mmsList.size}건 + RCS ${rcsList.size}건 = 총 ${combined.size}건"
        )
        return combined
    }

    /**
     * SMS + MMS + RCS 통합: 특정 기간의 모든 메시지 읽기 (하이브리드 분류기용)
     */
    fun readAllMessagesByDateRange(
        contentResolver: ContentResolver,
        startDate: Long,
        endDate: Long
    ): List<SmsMessage> {
        val sdfRange = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.KOREA)
        Log.e("sanha", "SmsReader[readAllMessagesByDateRange] : 요청 범위 ${sdfRange.format(java.util.Date(startDate))} ~ ${sdfRange.format(java.util.Date(endDate))}")
        val smsList = readAllSmsByDateRange(contentResolver, startDate, endDate)
        val mmsList = readAllMmsByDateRange(contentResolver, startDate, endDate)
        val rcsList = readAllRcsByDateRange(contentResolver, startDate, endDate)
        val combined =
            (smsList + mmsList + rcsList).distinctBy { it.id }.sortedByDescending { it.date }
        Log.e(
            "sanha",
            "SmsReader[readAllMessagesByDateRange] : SMS ${smsList.size}건 + MMS ${mmsList.size}건 + RCS ${rcsList.size}건 = 총 ${combined.size}건"
        )
        if (combined.isNotEmpty()) {
            val oldest = combined.minByOrNull { it.date }
            val newest = combined.maxByOrNull { it.date }
            Log.e("sanha", "SmsReader[readAllMessagesByDateRange] : 가장 오래된=${oldest?.date?.let { sdfRange.format(java.util.Date(it)) }}, 최신=${newest?.date?.let { sdfRange.format(java.util.Date(it)) }}")
        }
        return combined
    }

    /**
     * SMS + MMS + RCS 통합: 모든 수입 문자 읽기
     */
    fun readAllIncomeMessages(contentResolver: ContentResolver): List<SmsMessage> {
        val smsList = readAllIncomeSms(contentResolver)
        val mmsList = readAllIncomeMms(contentResolver)
        val rcsList = readAllIncomeRcs(contentResolver)
        val combined =
            (smsList + mmsList + rcsList).distinctBy { it.id }.sortedByDescending { it.date }
        Log.d(
            TAG,
            "통합 수입 읽기: SMS ${smsList.size}건 + MMS ${mmsList.size}건 + RCS ${rcsList.size}건 = 총 ${combined.size}건"
        )
        return combined
    }

    /**
     * SMS + MMS + RCS 통합: 특정 기간의 수입 문자 읽기
     */
    fun readIncomeMessagesByDateRange(
        contentResolver: ContentResolver,
        startDate: Long,
        endDate: Long
    ): List<SmsMessage> {
        val smsList = readIncomeSmsByDateRange(contentResolver, startDate, endDate)
        val mmsList = readIncomeMmsByDateRange(contentResolver, startDate, endDate)
        val rcsList = readIncomeRcsByDateRange(contentResolver, startDate, endDate)
        val combined =
            (smsList + mmsList + rcsList).distinctBy { it.id }.sortedByDescending { it.date }
        Log.d(
            TAG,
            "통합 기간별 수입 읽기: SMS ${smsList.size}건 + MMS ${mmsList.size}건 + RCS ${rcsList.size}건 = 총 ${combined.size}건"
        )
        return combined
    }

    // ========== RCS (채팅+) 읽기 기능 ==========
    // 삼성 기기에서 신한카드 등 카드사가 RCS로 알림을 보내는 경우
    // content://im/chat에 저장됨 (date는 밀리초 단위)
    // RCS body는 JSON 형식으로 저장됨 → "text" 필드에서 실제 메시지 추출 필요

    /**
     * RCS 메시지 body에서 실제 텍스트 추출
     *
     * RCS(채팅+) 메시지는 body가 JSON 형태로 저장됨:
     * {"messageHeader":"[Web발신]","verifiedIndicator":"1",
     *  "messageHeaderExtension":"확인된 발신자","text":"실제 메시지 내용..."}
     *
     * 이 함수에서 "text" 필드의 값만 추출하여 반환합니다.
     * JSON이 아닌 일반 텍스트인 경우 그대로 반환합니다.
     *
     * @param rawBody RCS body 원본 (JSON 또는 평문)
     * @return 추출된 텍스트 (실패 시 원본 반환)
     */
    private fun extractRcsText(rawBody: String): String {
        if (rawBody.isBlank()) return rawBody

        val trimmed = rawBody.trim()
        if (!trimmed.startsWith("{")) return rawBody

        return try {
            val json = JSONObject(trimmed)

            // 1) 직접 text/body 필드 시도
            for (key in listOf("text", "body", "message", "msg", "content")) {
                val value = json.optString(key, "")
                if (value.isNotBlank()) {
                    Log.d(TAG, "RCS JSON→텍스트 추출 (key=$key, ${value.length}자)")
                    return value
                }
            }

            // 2) layout 필드에서 TextView의 text 값들을 재귀 추출
            val layoutStr = json.optString("layout", "")
            if (layoutStr.isNotBlank() && layoutStr.startsWith("{")) {
                val layoutJson = JSONObject(layoutStr)
                val texts = mutableListOf<String>()
                extractTextsFromLayout(layoutJson, texts)
                if (texts.isNotEmpty()) {
                    val result = texts.joinToString("\n")
                    Log.d(TAG, "RCS layout→텍스트 추출 성공 (${texts.size}개 항목, ${result.length}자)")
                    return result
                }
            }

            Log.w(TAG, "RCS JSON에서 텍스트 추출 실패, 원본 사용")
            rawBody
        } catch (e: Exception) {
            Log.d(TAG, "RCS body JSON 파싱 실패: ${e.message}")
            rawBody
        }
    }

    /**
     * RCS layout JSON에서 모든 TextView의 text 값을 재귀적으로 추출
     */
    private fun extractTextsFromLayout(json: JSONObject, texts: MutableList<String>) {
        val widget = json.optString("widget", "")
        if (widget == "TextView") {
            val text = json.optString("text", "")
            if (text.isNotBlank()) {
                texts.add(text)
            }
        }

        val children = json.optJSONArray("children")
        if (children != null) {
            for (i in 0 until children.length()) {
                val child = children.optJSONObject(i)
                if (child != null) {
                    extractTextsFromLayout(child, texts)
                }
            }
        }
    }

    /**
     * RCS provider 사용 가능 여부 확인
     */
    private fun isRcsAvailable(contentResolver: ContentResolver): Boolean {
        return try {
            val cursor = contentResolver.query(
                RCS_URI,
                arrayOf("_id"),
                null,
                null,
                "date DESC LIMIT 1"
            )
            val available = cursor != null && cursor.count > 0
            cursor?.close()
            available
        } catch (e: Exception) {
            false
        }
    }

    /**
     * RCS에서 모든 카드 결제 메시지 읽기
     */
    fun readAllCardRcs(contentResolver: ContentResolver): List<SmsMessage> {
        val rcsList = mutableListOf<SmsMessage>()
        if (!isRcsAvailable(contentResolver)) return rcsList

        try {
            val cursor = contentResolver.query(
                RCS_URI,
                arrayOf("_id", "address", "body", "date"),
                null,
                null,
                "date DESC"
            )

            var totalCount = 0
            var cardCount = 0

            cursor?.use {
                val idIndex = it.getColumnIndex("_id")
                val addressIndex = it.getColumnIndex("address")
                val bodyIndex = it.getColumnIndex("body")
                val dateIndex = it.getColumnIndex("date")

                while (it.moveToNext()) {
                    totalCount++
                    it.getString(idIndex) ?: continue  // null이면 skip
                    val address = it.getString(addressIndex) ?: continue
                    val rawBody = it.getString(bodyIndex) ?: continue
                    val date = it.getLong(dateIndex)
                    val body = extractRcsText(rawBody)

                    if (body.isBlank()) continue

                    if (SmsParser.isCardPaymentSms(body)) {
                        cardCount++
                        rcsList.add(
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

            Log.d(TAG, "전체 RCS 카드결제 읽기: 전체 $totalCount 건, 카드결제 $cardCount 건")
        } catch (e: Exception) {
            Log.e(TAG, "RCS 전체 읽기 실패: ${e.message}")
        }

        return rcsList
    }

    /**
     * RCS에서 특정 기간의 카드 결제 메시지 읽기
     * RCS date는 밀리초 단위 (SMS와 동일)
     */
    fun readCardRcsByDateRange(
        contentResolver: ContentResolver,
        startDate: Long,
        endDate: Long
    ): List<SmsMessage> {
        val rcsList = mutableListOf<SmsMessage>()
        if (!isRcsAvailable(contentResolver)) return rcsList

        Log.e(TAG, "=== readCardRcsByDateRange 시작 ===")
        Log.e(
            TAG,
            "RCS 조회 범위: ${
                SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.KOREA).format(
                    Date(startDate)
                )
            } ~ ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.KOREA).format(Date(endDate))}"
        )

        try {
            val cursor = contentResolver.query(
                RCS_URI,
                arrayOf("_id", "address", "body", "date"),
                "date >= ? AND date <= ?",
                arrayOf(startDate.toString(), endDate.toString()),
                "date DESC"
            )

            var totalCount = 0
            var cardCount = 0

            cursor?.use {
                val idIndex = it.getColumnIndex("_id")
                val addressIndex = it.getColumnIndex("address")
                val bodyIndex = it.getColumnIndex("body")
                val dateIndex = it.getColumnIndex("date")

                while (it.moveToNext()) {
                    totalCount++
                    val id = it.getString(idIndex) ?: continue
                    val address = it.getString(addressIndex) ?: continue
                    val rawBody = it.getString(bodyIndex) ?: continue
                    val date = it.getLong(dateIndex)
                    val body = extractRcsText(rawBody)

                    if (body.isBlank()) continue

                    val preview = if (body.length > 30) body.take(30)
                        .replace("\n", " ") + "..." else body.replace("\n", " ")
                    Log.d(TAG, "  RCS[$id]: addr=$address, body=$preview")

                    if (SmsParser.isCardPaymentSms(body)) {
                        cardCount++
                        rcsList.add(
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

            Log.e(TAG, "=== RCS 조회 결과: 전체 $totalCount 건, 카드결제 $cardCount 건 ===")
        } catch (e: Exception) {
            Log.e(TAG, "RCS 기간별 읽기 실패: ${e.message}")
        }

        return rcsList
    }

    /**
     * RCS에서 특정 기간의 모든 메시지 읽기 (하이브리드 분류기용)
     */
    fun readAllRcsByDateRange(
        contentResolver: ContentResolver,
        startDate: Long,
        endDate: Long
    ): List<SmsMessage> {
        val rcsList = mutableListOf<SmsMessage>()
        if (!isRcsAvailable(contentResolver)) return rcsList

        try {
            val cursor = contentResolver.query(
                RCS_URI,
                arrayOf("_id", "address", "body", "date"),
                "date >= ? AND date <= ?",
                arrayOf(startDate.toString(), endDate.toString()),
                "date DESC"
            )

            cursor?.use {
                val idIndex = it.getColumnIndex("_id")
                val addressIndex = it.getColumnIndex("address")
                val bodyIndex = it.getColumnIndex("body")
                val dateIndex = it.getColumnIndex("date")

                while (it.moveToNext()) {
                    it.getString(idIndex) ?: continue  // null이면 skip
                    val address = it.getString(addressIndex) ?: continue
                    val rawBody = it.getString(bodyIndex) ?: continue
                    val date = it.getLong(dateIndex)
                    val body = extractRcsText(rawBody)

                    if (body.isBlank()) continue

                    rcsList.add(
                        SmsMessage(
                            id = SmsParser.generateSmsId(address, body, date),
                            address = address,
                            body = body,
                            date = date
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "RCS 전체 읽기(기간별) 실패: ${e.message}")
        }

        Log.d(TAG, "전체 RCS 읽기 완료: ${rcsList.size}건 ($startDate ~ $endDate)")
        return rcsList
    }

    /**
     * RCS에서 모든 수입 메시지 읽기
     */
    fun readAllIncomeRcs(contentResolver: ContentResolver): List<SmsMessage> {
        val rcsList = mutableListOf<SmsMessage>()
        if (!isRcsAvailable(contentResolver)) return rcsList

        try {
            val cursor = contentResolver.query(
                RCS_URI,
                arrayOf("_id", "address", "body", "date"),
                null,
                null,
                "date DESC"
            )

            cursor?.use {
                val idIndex = it.getColumnIndex("_id")
                val addressIndex = it.getColumnIndex("address")
                val bodyIndex = it.getColumnIndex("body")
                val dateIndex = it.getColumnIndex("date")

                while (it.moveToNext()) {
                    it.getString(idIndex) ?: continue  // null이면 skip
                    val address = it.getString(addressIndex) ?: continue
                    val rawBody = it.getString(bodyIndex) ?: continue
                    val date = it.getLong(dateIndex)
                    val body = extractRcsText(rawBody)

                    if (body.isBlank()) continue

                    if (SmsParser.isIncomeSms(body)) {
                        rcsList.add(
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
        } catch (e: Exception) {
            Log.e(TAG, "RCS 수입 전체 읽기 실패: ${e.message}")
        }

        Log.d(TAG, "수입 RCS 읽기 완료: ${rcsList.size}건")
        return rcsList
    }

    /**
     * RCS에서 특정 기간의 수입 메시지 읽기
     */
    fun readIncomeRcsByDateRange(
        contentResolver: ContentResolver,
        startDate: Long,
        endDate: Long
    ): List<SmsMessage> {
        val rcsList = mutableListOf<SmsMessage>()
        if (!isRcsAvailable(contentResolver)) return rcsList

        try {
            val cursor = contentResolver.query(
                RCS_URI,
                arrayOf("_id", "address", "body", "date"),
                "date >= ? AND date <= ?",
                arrayOf(startDate.toString(), endDate.toString()),
                "date DESC"
            )

            cursor?.use {
                val idIndex = it.getColumnIndex("_id")
                val addressIndex = it.getColumnIndex("address")
                val bodyIndex = it.getColumnIndex("body")
                val dateIndex = it.getColumnIndex("date")

                while (it.moveToNext()) {
                    it.getString(idIndex) ?: continue  // null이면 skip
                    val address = it.getString(addressIndex) ?: continue
                    val rawBody = it.getString(bodyIndex) ?: continue
                    val date = it.getLong(dateIndex)
                    val body = extractRcsText(rawBody)

                    if (body.isBlank()) continue

                    if (SmsParser.isIncomeSms(body)) {
                        rcsList.add(
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
        } catch (e: Exception) {
            Log.e(TAG, "RCS 수입 기간별 읽기 실패: ${e.message}")
        }

        Log.d(TAG, "기간별 수입 RCS 읽기 완료: ${rcsList.size}건 ($startDate ~ $endDate)")
        return rcsList
    }

    // ========== 진단용: 모든 메시지 provider 탐색 ==========

    /**
     * 진단용: 기기의 모든 메시지 저장소를 탐색하여 최근 메시지 위치 확인
     * SMS/MMS에서 찾을 수 없는 메시지가 어디에 저장되는지 확인하기 위함
     * (RCS, Samsung RCS, content://mms-sms 등)
     */
    fun diagnoseAllMessageProviders(contentResolver: ContentResolver) {
        Log.e(TAG, "========== 메시지 provider 진단 시작 ==========")

        // 1. content://mms-sms (통합 provider, 컬럼이 다를 수 있음)
        try {
            Log.e(TAG, "--- [1] content://mms-sms/conversations ---")
            val cursor = contentResolver.query(
                Uri.parse("content://mms-sms/complete-conversations"),
                null, // 모든 컬럼
                null,
                null,
                "date DESC"
            )
            cursor?.use {
                val cols = it.columnNames.joinToString(", ")
                Log.e(TAG, "  컬럼: $cols")
                Log.e(TAG, "  총 행: ${it.count}")
                var idx = 0
                while (it.moveToNext() && idx < 10) {
                    val dateColIdx = it.getColumnIndex("date")
                    val bodyColIdx = it.getColumnIndex("body")
                    val addrColIdx = it.getColumnIndex("address")
                    val typeColIdx = it.getColumnIndex("transport_type")
                    val ctTypeIdx = it.getColumnIndex("ct_t")

                    val date = if (dateColIdx >= 0) it.getLong(dateColIdx) else -1
                    val body = if (bodyColIdx >= 0) (it.getString(bodyColIdx)
                        ?: "(null)") else "(no body col)"
                    val addr = if (addrColIdx >= 0) (it.getString(addrColIdx)
                        ?: "(null)") else "(no addr col)"
                    val transType = if (typeColIdx >= 0) (it.getString(typeColIdx) ?: "?") else "?"
                    val ctType = if (ctTypeIdx >= 0) (it.getString(ctTypeIdx) ?: "?") else "?"

                    // date를 밀리초/초 양쪽으로 표시
                    val dateMs = if (date < 10000000000L) date * 1000 else date
                    val dateStr =
                        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.KOREA).format(Date(dateMs))
                    val preview = if (body.length > 50) body.take(50)
                        .replace("\n", " ") + "..." else body.replace("\n", " ")

                    Log.e(
                        TAG,
                        "  [$idx] date=$date ($dateStr) addr=$addr type=$transType ct=$ctType body=$preview"
                    )
                    idx++
                }
                if (idx == 0) Log.e(TAG, "  (비어있음)")
            } ?: Log.e(TAG, "  cursor가 null (provider 없음)")
        } catch (e: Exception) {
            Log.e(TAG, "  실패: ${e.message}")
        }

        // 2. content://sms 전체 (inbox 아닌 전체)
        try {
            Log.e(TAG, "--- [2] content://sms (전체, inbox 외 포함) ---")
            val cursor = contentResolver.query(
                Uri.parse("content://sms"),
                arrayOf("_id", "date", "address", "body", "type"),
                null,
                null,
                "date DESC"
            )
            cursor?.use {
                Log.e(TAG, "  총 행: ${it.count}")
                var idx = 0
                while (it.moveToNext() && idx < 5) {
                    val dateIdx = it.getColumnIndex("date")
                    val addressIdx = it.getColumnIndex("address")
                    val bodyIdx = it.getColumnIndex("body")
                    val typeIdx = it.getColumnIndex("type")
                    if (dateIdx < 0 || addressIdx < 0 || bodyIdx < 0 || typeIdx < 0) {
                        Log.e(TAG, "  필수 컬럼 누락: date/address/body/type")
                        break
                    }
                    val d = it.getLong(dateIdx)
                    val addr = it.getString(addressIdx) ?: "?"
                    val body = it.getString(bodyIdx) ?: ""
                    val type = it.getInt(typeIdx)
                    val preview = if (body.length > 40) body.take(40)
                        .replace("\n", " ") + "..." else body.replace("\n", " ")
                    Log.e(
                        TAG,
                        "  [$idx] type=$type date=${
                            SimpleDateFormat(
                                "yyyy-MM-dd HH:mm:ss",
                                Locale.KOREA
                            ).format(Date(d))
                        } addr=$addr body=$preview"
                    )
                    idx++
                }
            } ?: Log.e(TAG, "  cursor가 null")
        } catch (e: Exception) {
            Log.e(TAG, "  실패: ${e.message}")
        }

        // 3. content://mms 전체 (inbox 뿐 아니라)
        try {
            Log.e(TAG, "--- [3] content://mms (전체) ---")
            val cursor = contentResolver.query(
                Uri.parse("content://mms"),
                arrayOf("_id", "date", "msg_box", "sub"),
                null,
                null,
                "date DESC"
            )
            cursor?.use {
                Log.e(TAG, "  총 행: ${it.count}")
                var idx = 0
                while (it.moveToNext() && idx < 5) {
                    val idIdx = it.getColumnIndex("_id")
                    val dateIdx = it.getColumnIndex("date")
                    val msgBoxIdx = it.getColumnIndex("msg_box")
                    val subIdx = it.getColumnIndex("sub")
                    if (idIdx < 0 || dateIdx < 0 || msgBoxIdx < 0 || subIdx < 0) {
                        Log.e(TAG, "  필수 컬럼 누락: _id/date/msg_box/sub")
                        break
                    }
                    val id = it.getString(idIdx)
                    val d = it.getLong(dateIdx)
                    val msgBox = it.getInt(msgBoxIdx)
                    val sub = it.getString(subIdx) ?: "(null)"
                    val dMs = d * 1000
                    val body = getMmsTextBody(contentResolver, id) ?: "(본문없음)"
                    val preview = if (body.length > 40) body.take(40)
                        .replace("\n", " ") + "..." else body.replace("\n", " ")
                    Log.e(
                        TAG,
                        "  [$idx] id=$id msgBox=$msgBox date=${
                            SimpleDateFormat(
                                "yyyy-MM-dd HH:mm:ss",
                                Locale.KOREA
                            ).format(Date(dMs))
                        } sub=$sub body=$preview"
                    )
                    idx++
                }
            } ?: Log.e(TAG, "  cursor가 null")
        } catch (e: Exception) {
            Log.e(TAG, "  실패: ${e.message}")
        }

        // 4. Samsung RCS / IM provider 시도
        val rcsUris = listOf(
            "content://im/chat",
            "content://com.samsung.rcs.im/chat",
            "content://com.samsung.rcs.im/message",
            "content://rcs/message",
            "content://com.google.android.apps.messaging/conversations"
        )
        for (uri in rcsUris) {
            try {
                Log.e(TAG, "--- [RCS] $uri ---")
                val cursor = contentResolver.query(
                    Uri.parse(uri),
                    null,
                    null,
                    null,
                    null
                )
                cursor?.use {
                    val cols = it.columnNames.joinToString(", ")
                    Log.e(TAG, "  존재함! 컬럼: $cols")
                    Log.e(TAG, "  총 행: ${it.count}")
                    var idx = 0
                    while (it.moveToNext() && idx < 3) {
                        val sb = StringBuilder("  [$idx] ")
                        for (c in 0 until minOf(it.columnCount, 6)) {
                            try {
                                sb.append(
                                    "${it.getColumnName(c)}=${
                                        it.getString(c)?.take(30) ?: "null"
                                    } | "
                                )
                            } catch (_: Exception) {
                            }
                        }
                        Log.e(TAG, sb.toString())
                        idx++
                    }
                } ?: Log.e(TAG, "  cursor가 null (없는 provider)")
            } catch (e: Exception) {
                Log.e(TAG, "  접근 불가: ${e.message?.take(80)}")
            }
        }

        Log.e(TAG, "========== 메시지 provider 진단 완료 ==========")
    }
}
