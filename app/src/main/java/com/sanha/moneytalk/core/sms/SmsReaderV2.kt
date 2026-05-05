package com.sanha.moneytalk.core.sms

import com.sanha.moneytalk.core.util.MoneyTalkLogger

import android.content.ContentResolver
import android.net.Uri
import android.provider.Telephony
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.sanha.moneytalk.core.database.SmsBlockedSenderRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SMS/MMS/RCS 통합 읽기 (sms 전용)
 *
 * 기기의 SMS/MMS/RCS 수신함에서 기간별 모든 메시지를 읽어 SmsInput으로 반환합니다.
 * 카드 결제/수입 필터링 없이 전체를 반환하여 sms 파이프라인에서 분류하도록 합니다.
 *
 * MainViewModel의 동기화 경로에서 SmsSyncMessageReader를 통해 호출됩니다.
 * - SMS: content://sms/inbox (date 밀리초)
 * - MMS: content://mms/inbox (date 초 단위 → 밀리초 변환)
 * - RCS: content://im/chat (date 밀리초, 삼성 기기)
 */
@Singleton
class SmsReaderV2 @Inject constructor(
    private val smsBlockedSenderRepository: SmsBlockedSenderRepository,
    private val channelProbeCollector: SmsChannelProbeCollector
) {

    companion object {

        // MMS URI
        private val MMS_INBOX_URI: Uri by lazy { Uri.parse("content://mms/inbox") }
        private val MMS_PART_URI: Uri by lazy { Uri.parse("content://mms/part") }

        // RCS (채팅+) URI — 삼성 메시지 앱에서 사용
        private val RCS_URI: Uri by lazy { Uri.parse("content://im/chat") }

        /** RCS JSON에서 텍스트를 찾을 키 목록 */
        private val TEXT_KEYS = listOf("text", "description", "body", "title", "content", "message", "msg")

    }

    data class SmsReadResult(
        val messages: List<SmsInput>,
        val smsProviderReadSucceeded: Boolean
    )

    private data class ChannelReadResult(
        val messages: List<SmsInput>,
        val readSucceeded: Boolean
    )

    private data class MmsHeader(
        val id: String,
        val dateMs: Long
    )

    /**
     * SMS + MMS + RCS 통합: 특정 기간의 모든 메시지를 SmsInput으로 반환
     *
     * 010/070 개인번호는 금융 힌트 없으면 제외합니다.
     * 카드/수입 필터링은 하지 않고, sms 파이프라인(SmsPreFilter → SmsIncomeFilter)에서 처리합니다.
     *
     * @param contentResolver ContentResolver
     * @param startDate 시작 시간 (밀리초)
     * @param endDate 종료 시간 (밀리초)
     * @return 모든 메시지의 SmsInput 리스트와 SMS 기본 provider 읽기 성공 여부
     */
    suspend fun readAllMessagesByDateRange(
        contentResolver: ContentResolver,
        startDate: Long,
        endDate: Long
    ): SmsReadResult = coroutineScope {
        val blockedAddressSet = smsBlockedSenderRepository.getBlockedAddressSet()
        val smsDeferred = async {
            readAllSmsByDateRange(contentResolver, startDate, endDate, blockedAddressSet)
        }
        val mmsDeferred = async {
            readAllMmsByDateRange(contentResolver, startDate, endDate, blockedAddressSet)
        }
        val rcsDeferred = async {
            readAllRcsByDateRange(contentResolver, startDate, endDate, blockedAddressSet)
        }

        val smsResult = smsDeferred.await()
        val mmsList = mmsDeferred.await()
        val rcsList = rcsDeferred.await()

        MoneyTalkLogger.i("[SmsReaderV2] 채널별 읽기 결과: SMS=${smsResult.messages.size}, MMS=${mmsList.size}, RCS=${rcsList.size}")

        SmsReadResult(
            messages = (smsResult.messages + mmsList + rcsList)
                .distinctBy { it.id }
                .sortedByDescending { it.date },
            smsProviderReadSucceeded = smsResult.readSucceeded
        )
    }

    // ========== SMS 읽기 ==========

    private fun readAllSmsByDateRange(
        contentResolver: ContentResolver,
        startDate: Long,
        endDate: Long,
        blockedAddressSet: Set<String>
    ): ChannelReadResult {
        val result = mutableListOf<SmsInput>()
        var senderSkipCount = 0
        var blockedSenderSkipCount = 0
        var totalCursorCount = 0

        val cursor = try {
            contentResolver.query(
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
        } catch (e: Exception) {
            MoneyTalkLogger.e("[SmsReaderV2][SMS] provider 조회 실패: ${e.message}")
            return ChannelReadResult(emptyList(), readSucceeded = false)
        }

        if (cursor == null) {
            MoneyTalkLogger.e("[SmsReaderV2][SMS] provider cursor null")
            return ChannelReadResult(emptyList(), readSucceeded = false)
        }

        cursor.use {
            val idIndex = it.getColumnIndex(Telephony.Sms._ID)
            val addressIndex = it.getColumnIndex(Telephony.Sms.ADDRESS)
            val bodyIndex = it.getColumnIndex(Telephony.Sms.BODY)
            val dateIndex = it.getColumnIndex(Telephony.Sms.DATE)
            if (idIndex < 0 || addressIndex < 0 || bodyIndex < 0 || dateIndex < 0) {
                MoneyTalkLogger.e("[SmsReaderV2][SMS] 필수 컬럼 누락")
                return ChannelReadResult(emptyList(), readSucceeded = false)
            }

            while (it.moveToNext()) {
                totalCursorCount++
                it.getString(idIndex) ?: continue
                val address = it.getString(addressIndex) ?: continue
                val body = it.getString(bodyIndex) ?: continue
                val date = it.getLong(dateIndex)

                if (shouldSkipBlockedSender(address, blockedAddressSet)) {
                    channelProbeCollector.collect(
                        channel = "sms_inbox",
                        stage = "blocked_sender",
                        address = address,
                        body = body,
                        timestamp = date
                    )
                    blockedSenderSkipCount++
                    continue
                }

                if (SmsFilter.shouldSkipBySender(address, body)) {
                    channelProbeCollector.collect(
                        channel = "sms_inbox",
                        stage = "sender_skipped",
                        address = address,
                        body = body,
                        timestamp = date
                    )
                    senderSkipCount++
                    continue
                }

                channelProbeCollector.collect(
                    channel = "sms_inbox",
                    stage = "accepted",
                    address = address,
                    body = body,
                    timestamp = date
                )

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

        MoneyTalkLogger.i("[SmsReaderV2][SMS] 총 ${totalCursorCount}건 조회, ${result.size}건 통과, sender스킵=${senderSkipCount}, blocked스킵=${blockedSenderSkipCount}")
        return ChannelReadResult(result, readSucceeded = true)
    }

    // ========== MMS 읽기 ==========

    private fun readAllMmsByDateRange(
        contentResolver: ContentResolver,
        startDate: Long,
        endDate: Long,
        blockedAddressSet: Set<String>
    ): List<SmsInput> {
        val result = mutableListOf<SmsInput>()
        var senderSkipCount = 0
        var blockedSenderSkipCount = 0
        var totalCursorCount = 0
        val startSec = startDate / 1000
        val endSec = endDate / 1000
        val headers = mutableListOf<MmsHeader>()

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
                    totalCursorCount++
                    val mmsId = it.getString(idIndex) ?: continue
                    val dateSec = it.getLong(dateIndex)
                    headers.add(MmsHeader(id = mmsId, dateMs = dateSec * 1000))
                }
            }

            if (headers.isNotEmpty()) {
                val addressesById = loadMmsAddresses(contentResolver, headers.map { it.id })
                val acceptedHeaders = mutableListOf<Pair<MmsHeader, String>>()

                for (header in headers) {
                    val address = addressesById[header.id] ?: getMmsAddress(contentResolver, header.id)

                    if (shouldSkipBlockedSender(address, blockedAddressSet)) {
                        channelProbeCollector.collect(
                            channel = "mms_inbox",
                            stage = "blocked_sender",
                            address = address,
                            body = "",
                            timestamp = header.dateMs
                        )
                        blockedSenderSkipCount++
                        continue
                    }

                    if (SmsFilter.shouldSkipBySender(address, "")) {
                        channelProbeCollector.collect(
                            channel = "mms_inbox",
                            stage = "sender_skipped",
                            address = address,
                            body = "",
                            timestamp = header.dateMs
                        )
                        senderSkipCount++
                        continue
                    }

                    acceptedHeaders.add(header to address)
                }

                val bodiesById = loadMmsTextBodies(
                    contentResolver = contentResolver,
                    mmsIds = acceptedHeaders.map { it.first.id }
                )

                for ((header, address) in acceptedHeaders) {
                    val body = bodiesById[header.id]
                        ?: getMmsTextBody(contentResolver, header.id)
                        ?: continue

                    channelProbeCollector.collect(
                        channel = "mms_inbox",
                        stage = "accepted",
                        address = address,
                        body = body,
                        timestamp = header.dateMs
                    )

                    result.add(
                        SmsInput(
                            id = generateSmsId(address, body, header.dateMs),
                            body = body,
                            address = address,
                            date = header.dateMs
                        )
                    )
                }
            }
        } catch (e: Exception) {
            MoneyTalkLogger.e("MMS 읽기 실패: ${e.message}")
        }

        MoneyTalkLogger.i("[SmsReaderV2][MMS] 총 ${totalCursorCount}건 조회, ${result.size}건 통과, sender스킵=${senderSkipCount}, blocked스킵=${blockedSenderSkipCount}")
        return result
    }

    private fun loadMmsAddresses(
        contentResolver: ContentResolver,
        mmsIds: List<String>
    ): Map<String, String> {
        if (mmsIds.isEmpty()) return emptyMap()

        val result = mutableMapOf<String, String>()
        for (chunk in mmsIds.chunked(400)) {
            val selection = buildInSelection("msg_id", chunk.size)
            try {
                val cursor = contentResolver.query(
                    Uri.parse("content://mms/addr"),
                    arrayOf("msg_id", "address", "type"),
                    selection,
                    chunk.toTypedArray(),
                    null
                )
                cursor?.use {
                    val msgIdIndex = it.getColumnIndex("msg_id")
                    val addressIndex = it.getColumnIndex("address")
                    val typeIndex = it.getColumnIndex("type")
                    if (msgIdIndex < 0 || addressIndex < 0 || typeIndex < 0) return@use

                    while (it.moveToNext()) {
                        val msgId = it.getString(msgIdIndex) ?: continue
                        val address = it.getString(addressIndex)
                        if (address.isNullOrBlank() || address == "insert-address-token") continue

                        val type = it.getInt(typeIndex)
                        if (!result.containsKey(msgId) || type == 137) {
                            result[msgId] = address
                        }
                    }
                }
            } catch (e: Exception) {
                MoneyTalkLogger.w("MMS 주소 bulk 읽기 실패: ${e.message}")
                return result
            }
        }

        return result
    }

    private fun loadMmsTextBodies(
        contentResolver: ContentResolver,
        mmsIds: List<String>
    ): Map<String, String> {
        if (mmsIds.isEmpty()) return emptyMap()

        val bodyPartsById = linkedMapOf<String, StringBuilder>()
        for (chunk in mmsIds.chunked(400)) {
            val selection = buildInSelection("mid", chunk.size)
            try {
                val cursor = contentResolver.query(
                    MMS_PART_URI,
                    arrayOf("_id", "mid", "ct", "text"),
                    selection,
                    chunk.toTypedArray(),
                    null
                )
                cursor?.use {
                    val partIdIndex = it.getColumnIndex("_id")
                    val midIndex = it.getColumnIndex("mid")
                    val contentTypeIndex = it.getColumnIndex("ct")
                    val textIndex = it.getColumnIndex("text")
                    if (partIdIndex < 0 || midIndex < 0 || contentTypeIndex < 0 || textIndex < 0) {
                        return@use
                    }

                    while (it.moveToNext()) {
                        val contentType = it.getString(contentTypeIndex) ?: continue
                        if (contentType != "text/plain") continue

                        val mid = it.getString(midIndex) ?: continue
                        val text = it.getString(textIndex)
                        val body = if (!text.isNullOrBlank()) {
                            text
                        } else {
                            val partId = it.getString(partIdIndex) ?: continue
                            readMmsPartText(contentResolver, partId).orEmpty()
                        }
                        if (body.isNotBlank()) {
                            bodyPartsById.getOrPut(mid) { StringBuilder() }.append(body)
                        }
                    }
                }
            } catch (e: Exception) {
                MoneyTalkLogger.w("MMS 본문 bulk 읽기 실패: ${e.message}")
                return bodyPartsById.mapValues { it.value.toString() }
            }
        }

        return bodyPartsById.mapValues { it.value.toString() }
    }

    private fun buildInSelection(columnName: String, size: Int): String {
        val placeholders = List(size) { "?" }.joinToString(",")
        return "$columnName IN ($placeholders)"
    }

    /**
     * MMS 메시지의 텍스트 본문 읽기
     */
    internal fun getMmsTextBody(contentResolver: ContentResolver, mmsId: String): String? {
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
                            readMmsPartText(contentResolver, partId)?.let { body ->
                                sb.append(body)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            MoneyTalkLogger.w("MMS 본문 읽기 실패 (mmsId=$mmsId): ${e.message}")
        }
        return sb.toString().takeIf { it.isNotBlank() }
    }

    private fun readMmsPartText(contentResolver: ContentResolver, partId: String): String? {
        return try {
            val partUri = Uri.parse("content://mms/part/$partId")
            contentResolver.openInputStream(partUri)?.use { inputStream ->
                val reader = BufferedReader(InputStreamReader(inputStream, "UTF-8"))
                reader.readText()
            }
        } catch (e: Exception) {
            MoneyTalkLogger.w("MMS part 읽기 실패 (partId=$partId): ${e.message}")
            null
        }
    }

    /**
     * MMS 발신 번호 읽기
     */
    internal fun getMmsAddress(contentResolver: ContentResolver, mmsId: String): String {
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
            MoneyTalkLogger.w("MMS 주소 읽기 실패 (mmsId=$mmsId): ${e.message}")
        }
        return "unknown"
    }

    // ========== RCS (채팅+) 읽기 ==========

    private fun readAllRcsByDateRange(
        contentResolver: ContentResolver,
        startDate: Long,
        endDate: Long,
        blockedAddressSet: Set<String>
    ): List<SmsInput> {
        val result = mutableListOf<SmsInput>()
        var senderSkipCount = 0
        var blockedSenderSkipCount = 0
        var totalCursorCount = 0

        try {
            val cursor = contentResolver.query(
                RCS_URI,
                arrayOf("_id", "address", "body", "date"),
                "date >= ? AND date <= ?",
                arrayOf(startDate.toString(), endDate.toString()),
                "date DESC"
            )
            if (cursor == null) {
                MoneyTalkLogger.w("[SmsReaderV2][RCS] provider cursor null")
                return result
            }

            cursor.use {
                val idIndex = it.getColumnIndex("_id")
                val addressIndex = it.getColumnIndex("address")
                val bodyIndex = it.getColumnIndex("body")
                val dateIndex = it.getColumnIndex("date")
                if (idIndex < 0 || addressIndex < 0 || bodyIndex < 0 || dateIndex < 0) {
                    MoneyTalkLogger.w("[SmsReaderV2][RCS] 필수 컬럼 누락")
                    return result
                }

                while (it.moveToNext()) {
                    totalCursorCount++
                    it.getString(idIndex) ?: continue
                    val address = it.getString(addressIndex) ?: continue
                    val rawBody = it.getString(bodyIndex) ?: continue
                    val date = it.getLong(dateIndex)
                    val body = extractRcsText(rawBody)

                    if (body.isBlank()) continue

                    if (shouldSkipBlockedSender(address, blockedAddressSet)) {
                        channelProbeCollector.collect(
                            channel = "rcs_im_chat",
                            stage = "blocked_sender",
                            address = address,
                            body = body,
                            timestamp = date
                        )
                        blockedSenderSkipCount++
                        continue
                    }

                    if (SmsFilter.shouldSkipBySender(address, body)) {
                        channelProbeCollector.collect(
                            channel = "rcs_im_chat",
                            stage = "sender_skipped",
                            address = address,
                            body = body,
                            timestamp = date
                        )
                        senderSkipCount++
                        continue
                    }

                    channelProbeCollector.collect(
                        channel = "rcs_im_chat",
                        stage = "accepted",
                        address = address,
                        body = body,
                        timestamp = date
                    )

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
            MoneyTalkLogger.e("RCS 읽기 실패: ${e.message}")
        }

        MoneyTalkLogger.i("[SmsReaderV2][RCS] 총 ${totalCursorCount}건 조회, ${result.size}건 통과, sender스킵=${senderSkipCount}, blocked스킵=${blockedSenderSkipCount}")
        return result
    }

    /** 수신거부 발신번호 여부 확인 */
    private fun shouldSkipBlockedSender(address: String, blockedAddressSet: Set<String>): Boolean {
        if (blockedAddressSet.isEmpty()) return false
        val normalized = SmsFilter.normalizeAddress(address)
        return normalized in blockedAddressSet
    }

    /**
     * RCS 메시지 body에서 실제 텍스트 추출
     *
     * RCS(채팅+) 메시지는 body가 JSON 형태로 저장됨.
     * "text" 필드의 값만 추출하여 반환, JSON이 아닌 경우 그대로 반환.
     */
    internal fun extractRcsText(rawBody: String): String {
        if (rawBody.isBlank()) return rawBody

        val trimmed = rawBody.trim()
        if (!trimmed.startsWith("{")) return rawBody

        return try {
            val json = JsonParser.parseString(trimmed).asJsonObject

            // 1. 최상위에서 text 키 우선 탐색 (JSON 값은 스킵)
            for (key in TEXT_KEYS) {
                val value = json.get(key).asStringOrBlank()
                if (value.isNotBlank() && !value.startsWith("{")) {
                    return value
                }
            }

            // 2. nested JSON object 재귀 탐색 (generalPurposeCard 등)
            val nested = extractTextFromNestedRcs(json)
            if (nested.isNotBlank()) return nested

            // 3. layout 기반 추출
            val layoutJson = json.get("layout").asJsonObjectFromRcsValue()
            if (layoutJson != null) {
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
     * nested RCS JSON에서 텍스트 재귀 추출
     *
     * generalPurposeCard 등 nested 구조에서 description/text/title 필드를 찾는다.
     * TEXT_KEYS 값이 JSON object인 경우(예: content가 object)도 재귀 처리.
     * 최대 깊이 5로 제한하여 무한 재귀 방지.
     */
    private fun extractTextFromNestedRcs(json: JsonObject, depth: Int = 0): String {
        if (depth > 5) return ""

        // 현재 레벨에서 텍스트 필드 탐색 (plain string 우선, JSONObject는 재귀)
        for (key in TEXT_KEYS) {
            val raw = json.get(key) ?: continue
            when {
                // plain string → 바로 반환
                raw.isJsonPrimitive -> {
                    val value = raw.asStringOrBlank()
                    if (value.isNotBlank() && !value.startsWith("{")) return value
                    val nested = raw.asJsonObjectFromRcsValue()
                    if (nested != null) {
                        val extracted = extractTextFromNestedRcs(nested, depth + 1)
                        if (extracted.isNotBlank()) return extracted
                    }
                }
                // JSON object → 재귀 추출
                raw.isJsonObject -> {
                    val extracted = extractTextFromNestedRcs(raw.asJsonObject, depth + 1)
                    if (extracted.isNotBlank()) return extracted
                }
            }
        }

        // 하위 JSONObject 재귀 탐색
        for ((_, child) in json.entrySet()) {
            if (child.isJsonObject) {
                val extracted = extractTextFromNestedRcs(child.asJsonObject, depth + 1)
                if (extracted.isNotBlank()) return extracted
            }
        }

        return ""
    }

    /**
     * RCS layout JSON에서 모든 TextView의 text 값을 재귀적으로 추출
     */
    private fun extractTextsFromLayout(json: JsonObject, texts: MutableList<String>) {
        val widget = json.get("widget").asStringOrBlank()
        if (widget == "TextView") {
            val text = json.get("text").asStringOrBlank()
            if (text.isNotBlank()) {
                texts.add(text)
            }
        }

        val children = json.get("children")
        if (children != null && children.isJsonArray) {
            for (child in children.asJsonArray) {
                if (child.isJsonObject) extractTextsFromLayout(child.asJsonObject, texts)
            }
        }
    }

    private fun JsonElement?.asStringOrBlank(): String {
        if (this == null || isJsonNull || !isJsonPrimitive) return ""
        return runCatching { asString }.getOrDefault("")
    }

    private fun JsonElement?.asJsonObjectFromRcsValue(): JsonObject? {
        if (this == null || isJsonNull) return null
        if (isJsonObject) return asJsonObject

        val value = asStringOrBlank().trim()
        if (!value.startsWith("{")) return null
        return runCatching { JsonParser.parseString(value).asJsonObject }.getOrNull()
    }

    // ========== 유틸리티 ==========

    /**
     * SMS 고유 ID 생성
     * 중복 저장 방지를 위해 발신번호 + 시간 + 본문 해시로 구성
     */
    private fun generateSmsId(address: String, body: String, date: Long): String {
        return "${SmsFilter.normalizeAddress(address)}_${date}_${body.hashCode()}"
    }
}
