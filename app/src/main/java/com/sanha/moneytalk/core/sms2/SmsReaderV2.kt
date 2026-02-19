package com.sanha.moneytalk.core.sms2

import android.content.ContentResolver
import android.net.Uri
import android.provider.Telephony
import android.util.Log
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SMS/MMS/RCS 통합 읽기 (sms2 전용)
 *
 * 기기의 SMS/MMS/RCS 수신함에서 기간별 모든 메시지를 읽어 SmsInput으로 반환합니다.
 * 카드 결제/수입 필터링 없이 전체를 반환하여 sms2 파이프라인에서 분류하도록 합니다.
 *
 * SmsReader(V1)에서 HomeViewModel이 사용하는 readAllMessagesByDateRange() 흐름만 추출.
 * - SMS: content://sms/inbox (date 밀리초)
 * - MMS: content://mms/inbox (date 초 단위 → 밀리초 변환)
 * - RCS: content://im/chat (date 밀리초, 삼성 기기)
 */
@Singleton
class SmsReaderV2 @Inject constructor() {

    companion object {
        private const val TAG = "SmsReaderV2"

        // MMS URI
        private val MMS_INBOX_URI = Uri.parse("content://mms/inbox")
        private val MMS_PART_URI = Uri.parse("content://mms/part")

        // RCS (채팅+) URI — 삼성 메시지 앱에서 사용
        private val RCS_URI = Uri.parse("content://im/chat")
    }

    /**
     * SMS + MMS + RCS 통합: 특정 기간의 모든 메시지를 SmsInput으로 반환
     *
     * 010/070 개인번호는 금융 힌트 없으면 제외합니다.
     * 카드/수입 필터링은 하지 않고, sms2 파이프라인(SmsPreFilter → SmsIncomeFilter)에서 처리합니다.
     *
     * @param contentResolver ContentResolver
     * @param startDate 시작 시간 (밀리초)
     * @param endDate 종료 시간 (밀리초)
     * @return 모든 메시지의 SmsInput 리스트 (최신순 정렬, id 중복 제거)
     */
    fun readAllMessagesByDateRange(
        contentResolver: ContentResolver,
        startDate: Long,
        endDate: Long
    ): List<SmsInput> {
        val sdfRange = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.KOREA)
        Log.d(TAG, "readAllMessagesByDateRange: 요청 범위 ${sdfRange.format(java.util.Date(startDate))} ~ ${sdfRange.format(java.util.Date(endDate))}")

        val smsList = readAllSmsByDateRange(contentResolver, startDate, endDate)
        val mmsList = readAllMmsByDateRange(contentResolver, startDate, endDate)
        val rcsList = readAllRcsByDateRange(contentResolver, startDate, endDate)

        val combined = (smsList + mmsList + rcsList)
            .distinctBy { it.id }
            .sortedByDescending { it.date }

        Log.d(TAG, "readAllMessagesByDateRange: SMS ${smsList.size}건 + MMS ${mmsList.size}건 + RCS ${rcsList.size}건 = 총 ${combined.size}건")
        return combined
    }

    // ========== SMS 읽기 ==========

    private fun readAllSmsByDateRange(
        contentResolver: ContentResolver,
        startDate: Long,
        endDate: Long
    ): List<SmsInput> {
        val result = mutableListOf<SmsInput>()
        var senderSkipCount = 0

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
                it.getString(idIndex) ?: continue
                val address = it.getString(addressIndex) ?: continue
                val body = it.getString(bodyIndex) ?: continue
                val date = it.getLong(dateIndex)

                if (SmsFilter.shouldSkipBySender(address, body)) {
                    senderSkipCount++
                    continue
                }

                result.add(
                    SmsInput(
                        id = generateSmsId(address, body, date),
                        body = body,
                        address = address,
                        date = date
                    )
                )
            }
        }

        Log.d(TAG, "SMS 읽기: ${result.size}건 (010/070 제외: ${senderSkipCount}건)")
        return result
    }

    // ========== MMS 읽기 ==========

    private fun readAllMmsByDateRange(
        contentResolver: ContentResolver,
        startDate: Long,
        endDate: Long
    ): List<SmsInput> {
        val result = mutableListOf<SmsInput>()
        var senderSkipCount = 0
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

                    if (SmsFilter.shouldSkipBySender(address, body)) {
                        senderSkipCount++
                        continue
                    }

                    result.add(
                        SmsInput(
                            id = generateSmsId(address, body, dateMs),
                            body = body,
                            address = address,
                            date = dateMs
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "MMS 읽기 실패: ${e.message}")
        }

        Log.d(TAG, "MMS 읽기: ${result.size}건 (010/070 제외: ${senderSkipCount}건)")
        return result
    }

    /**
     * MMS 메시지의 텍스트 본문 읽기
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
                        val text = it.getString(textIndex)
                        if (!text.isNullOrBlank()) {
                            sb.append(text)
                        } else {
                            val partId = it.getString(idIndex) ?: continue
                            try {
                                val partUri = Uri.parse("content://mms/part/$partId")
                                contentResolver.openInputStream(partUri)?.use { inputStream ->
                                    val reader = BufferedReader(InputStreamReader(inputStream, "UTF-8"))
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
                    if (type == 137) {
                        val address = it.getString(addressIndex)
                        if (!address.isNullOrBlank() && address != "insert-address-token") {
                            return address
                        }
                    }
                }
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

    // ========== RCS (채팅+) 읽기 ==========

    private fun readAllRcsByDateRange(
        contentResolver: ContentResolver,
        startDate: Long,
        endDate: Long
    ): List<SmsInput> {
        val result = mutableListOf<SmsInput>()
        var senderSkipCount = 0
        if (!isRcsAvailable(contentResolver)) return result

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
                    it.getString(idIndex) ?: continue
                    val address = it.getString(addressIndex) ?: continue
                    val rawBody = it.getString(bodyIndex) ?: continue
                    val date = it.getLong(dateIndex)
                    val body = extractRcsText(rawBody)

                    if (body.isBlank()) continue

                    if (SmsFilter.shouldSkipBySender(address, body)) {
                        senderSkipCount++
                        continue
                    }

                    result.add(
                        SmsInput(
                            id = generateSmsId(address, body, date),
                            body = body,
                            address = address,
                            date = date
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "RCS 읽기 실패: ${e.message}")
        }

        Log.d(TAG, "RCS 읽기: ${result.size}건 (010/070 제외: ${senderSkipCount}건)")
        return result
    }

    /**
     * RCS 메시지 body에서 실제 텍스트 추출
     *
     * RCS(채팅+) 메시지는 body가 JSON 형태로 저장됨.
     * "text" 필드의 값만 추출하여 반환, JSON이 아닌 경우 그대로 반환.
     */
    private fun extractRcsText(rawBody: String): String {
        if (rawBody.isBlank()) return rawBody

        val trimmed = rawBody.trim()
        if (!trimmed.startsWith("{")) return rawBody

        return try {
            val json = JSONObject(trimmed)

            for (key in listOf("text", "body", "message", "msg", "content")) {
                val value = json.optString(key, "")
                if (value.isNotBlank()) {
                    return value
                }
            }

            val layoutStr = json.optString("layout", "")
            if (layoutStr.isNotBlank() && layoutStr.startsWith("{")) {
                val layoutJson = JSONObject(layoutStr)
                val texts = mutableListOf<String>()
                extractTextsFromLayout(layoutJson, texts)
                if (texts.isNotEmpty()) {
                    return texts.joinToString("\n")
                }
            }

            rawBody
        } catch (e: Exception) {
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

    // ========== 유틸리티 ==========

    /**
     * SMS 고유 ID 생성
     * 중복 저장 방지를 위해 발신번호 + 시간 + 본문 해시로 구성
     */
    private fun generateSmsId(address: String, body: String, date: Long): String {
        return "${address}_${date}_${body.hashCode()}"
    }
}
