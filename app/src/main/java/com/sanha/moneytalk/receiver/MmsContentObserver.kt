package com.sanha.moneytalk.receiver

import android.content.ContentResolver
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.sanha.moneytalk.core.sms2.SmsFilter
import com.sanha.moneytalk.core.sms2.SmsInstantProcessor
import com.sanha.moneytalk.core.sms2.SmsReaderV2
import com.sanha.moneytalk.core.util.DataRefreshEvent
import com.sanha.moneytalk.core.util.MoneyTalkLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * MMS 수신 실시간 감지 ContentObserver
 *
 * content://mms URI를 감시하여 MMS 수신 시 즉시 파싱/저장/알림을 수행한다.
 * SMS는 [SmsReceiver] BroadcastReceiver로 처리하고,
 * MMS/RCS(채팅+)로 수신되는 금융 문자는 이 Observer가 담당한다.
 *
 * 주의:
 * - Android는 MMS part(body)를 비동기로 쓰므로 retry with backoff 필요
 * - ContentObserver는 단일 MMS에 대해 여러 번 onChange를 호출할 수 있어 dedup 필수
 * - MMS date 컬럼은 초 단위 (SMS는 밀리초)
 *
 * 등록 위치: [com.sanha.moneytalk.MoneyTalkApplication.onCreate]
 */
@Singleton
class MmsContentObserver @Inject constructor(
    private val smsReaderV2: SmsReaderV2,
    private val instantProcessor: SmsInstantProcessor,
    private val dataRefreshEvent: DataRefreshEvent
) : ContentObserver(Handler(Looper.getMainLooper())) {

    companion object {
        private val MMS_INBOX_URI = Uri.parse("content://mms/inbox")

        /** dedup 항목 유지 시간 (5분) */
        private const val DEDUP_TTL_MS = 5 * 60 * 1000L

        /** MMS body retry 대기 시간 (ms) */
        private val BODY_RETRY_DELAYS = longArrayOf(500L, 1500L, 3000L)
    }

    /** mmsId → 처리 시각. 중복 onChange 호출 방지 */
    private val processedMmsIds = ConcurrentHashMap<String, Long>()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var cachedContentResolver: ContentResolver? = null

    /** Application.onCreate()에서 호출하여 MMS 감시 시작 */
    fun register(contentResolver: ContentResolver) {
        cachedContentResolver = contentResolver
        contentResolver.registerContentObserver(
            Uri.parse("content://mms"),
            true,
            this
        )
        MoneyTalkLogger.i("[MmsObserver] content://mms 감시 등록")
    }

    override fun onChange(selfChange: Boolean, uri: Uri?) {
        super.onChange(selfChange, uri)
        scope.launch {
            try {
                processLatestMms()
            } catch (e: Exception) {
                MoneyTalkLogger.e("[MmsObserver] 처리 예외: ${e.message}")
            }
        }
    }

    private suspend fun processLatestMms() {
        val contentResolver = getContentResolver() ?: return

        // 최근 MMS 1건 조회
        val cursor = contentResolver.query(
            MMS_INBOX_URI,
            arrayOf("_id", "date"),
            null,
            null,
            "date DESC LIMIT 1"
        ) ?: return

        cursor.use {
            if (!it.moveToFirst()) return

            val idIndex = it.getColumnIndex("_id")
            val dateIndex = it.getColumnIndex("date")
            if (idIndex < 0 || dateIndex < 0) return

            val mmsId = it.getString(idIndex) ?: return
            val dateSec = it.getLong(dateIndex)
            val timestampMillis = dateSec * 1000L

            // Dedup 체크 + 만료 항목 정리
            cleanExpiredEntries()
            if (processedMmsIds.putIfAbsent(mmsId, System.currentTimeMillis()) != null) {
                return
            }

            MoneyTalkLogger.i("[MmsObserver] 새 MMS 감지: id=$mmsId")

            // MMS body 읽기 (retry with backoff — part 비동기 쓰기 대응)
            var body: String? = null
            for (delayMs in BODY_RETRY_DELAYS) {
                body = smsReaderV2.getMmsTextBody(contentResolver, mmsId)
                if (!body.isNullOrBlank()) break
                MoneyTalkLogger.i("[MmsObserver] body 미준비, ${delayMs}ms 후 재시도")
                delay(delayMs)
            }

            if (body.isNullOrBlank()) {
                MoneyTalkLogger.w("[MmsObserver] body 읽기 최종 실패: mmsId=$mmsId")
                processedMmsIds.remove(mmsId)
                return
            }

            // 발신번호 읽기
            val address = smsReaderV2.getMmsAddress(contentResolver, mmsId)
            if (address == "unknown" || address.isBlank()) {
                MoneyTalkLogger.w("[MmsObserver] 발신번호 읽기 실패: mmsId=$mmsId")
                processedMmsIds.remove(mmsId)
                return
            }

            // 개인번호 필터
            if (SmsFilter.shouldSkipBySender(address, body)) {
                MoneyTalkLogger.i("[MmsObserver] 개인번호 스킵: $address")
                return
            }

            MoneyTalkLogger.i("[MmsObserver] MMS 처리 시작: addr=$address, len=${body.length}")

            // SmsInstantProcessor로 즉시 처리
            var instantSuccess = false
            try {
                val result = instantProcessor.processAndSave(address, body, timestampMillis)
                when (result) {
                    is SmsInstantProcessor.Result.Expense -> {
                        instantSuccess = true
                        MoneyTalkLogger.i("[MmsObserver] 즉시 지출 저장: ${result.entity.storeName} ${result.entity.amount}원")
                    }
                    is SmsInstantProcessor.Result.Income -> {
                        instantSuccess = true
                        MoneyTalkLogger.i("[MmsObserver] 즉시 수입 저장: ${result.entity.amount}원")
                    }
                    is SmsInstantProcessor.Result.Skipped ->
                        MoneyTalkLogger.i("[MmsObserver] 비결제 또는 미매칭 → 전체 동기화 대기")
                    is SmsInstantProcessor.Result.Error ->
                        MoneyTalkLogger.w("[MmsObserver] 즉시 처리 실패: ${result.message}")
                }
            } catch (e: Exception) {
                MoneyTalkLogger.e("[MmsObserver] 즉시 처리 예외: ${e.message}")
            } finally {
                if (instantSuccess) {
                    dataRefreshEvent.emit(DataRefreshEvent.RefreshType.TRANSACTION_ADDED)
                } else {
                    dataRefreshEvent.emit(DataRefreshEvent.RefreshType.SMS_RECEIVED)
                }
            }
        }
    }

    /** 5분 경과 dedup 항목 제거 */
    private fun cleanExpiredEntries() {
        val now = System.currentTimeMillis()
        processedMmsIds.entries.removeIf { now - it.value > DEDUP_TTL_MS }
    }

    private fun getContentResolver(): ContentResolver? = cachedContentResolver
}
