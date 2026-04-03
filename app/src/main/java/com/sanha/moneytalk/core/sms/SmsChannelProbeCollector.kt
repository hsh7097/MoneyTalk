package com.sanha.moneytalk.core.sms

import com.sanha.moneytalk.BuildConfig
import com.sanha.moneytalk.core.database.dao.SmsChannelProbeLogDao
import com.sanha.moneytalk.core.database.entity.SmsChannelProbeLogEntity
import com.sanha.moneytalk.core.util.MoneyTalkLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 특정 금융 메시지가 SMS/MMS/RCS/실시간 수신 경로 중 어디에서 관측되는지 추적하는 진단 수집기.
 *
 * 적재 실패 후보를 제한적으로 수집하여 파이프라인 누락 분석에 활용한다.
 */
@Singleton
class SmsChannelProbeCollector @Inject constructor(
    private val smsChannelProbeLogDao: SmsChannelProbeLogDao
) {

    companion object {
        private val WOORI_APPROVAL_PATTERN = Regex("""우리\(\d+\)승인""")
        private val AMOUNT_PATTERN = Regex("""[\d,]+원""")
        private const val RETENTION_WINDOW_MS = 7L * 24 * 60 * 60 * 1000
        private val NON_FAILURE_STAGES = setOf(
            "received",
            "accepted",
            "instant_expense",
            "instant_income",
            "duplicate_skip"
        )
        private val TRANSACTION_HINTS = listOf(
            "승인", "결제", "출금", "입금", "이체", "송금", "사용", "이용",
            "취소", "일시불", "할부", "잔액", "누적", "체크카드", "자동결제"
        )
        private val FINANCE_HINTS = listOf(
            "카드", "은행", "우리", "신한", "국민", "KB", "하나", "NH", "농협",
            "삼성", "현대", "롯데", "BC", "씨티", "IBK", "카카오뱅크", "토스"
        )
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun collect(
        channel: String,
        stage: String,
        address: String,
        body: String,
        timestamp: Long,
        note: String = ""
    ) {
        if (!BuildConfig.DEBUG) return
        if (!shouldProbe(stage, address, body)) return

        val normalizedSender = SmsFilter.normalizeAddress(address)
        val normalizedBody = normalizeBody(body)
        MoneyTalkLogger.i(
            "[SmsChannelProbe] channel=$channel, stage=$stage, sender=$normalizedSender, " +
                "timestamp=$timestamp, note=$note, body=${normalizedBody.take(120)}"
        )

        scope.launch {
            runCatching {
                smsChannelProbeLogDao.insert(
                    SmsChannelProbeLogEntity(
                        normalizedSenderAddress = normalizedSender,
                        channel = channel,
                        stage = stage,
                        originBody = body,
                        maskedBody = maskBody(body),
                        normalizedBody = normalizedBody,
                        messageTimestamp = timestamp,
                        note = note
                    )
                )
                smsChannelProbeLogDao.deleteOlderThan(
                    minCreatedAt = System.currentTimeMillis() - RETENTION_WINDOW_MS
                )
            }.onFailure { e ->
                MoneyTalkLogger.w(
                    "sms_channel_probe room 저장 실패: ${e.javaClass.simpleName} ${e.message}"
                )
            }
        }
    }

    private fun shouldProbe(stage: String, address: String, body: String): Boolean {
        if (stage in NON_FAILURE_STAGES) return false

        val normalizedSender = SmsFilter.normalizeAddress(address)
        if (normalizedSender == "15889955") return true

        val normalizedBody = normalizeBody(body)
        val hasAmount = AMOUNT_PATTERN.containsMatchIn(normalizedBody)
        val hasTransactionHint = TRANSACTION_HINTS.any {
            normalizedBody.contains(it, ignoreCase = true)
        }
        val hasFinanceHint = FINANCE_HINTS.any {
            normalizedBody.contains(it, ignoreCase = true)
        }

        return normalizedBody.contains("우리카드 이용안내", ignoreCase = true) ||
            WOORI_APPROVAL_PATTERN.containsMatchIn(normalizedBody) ||
            (hasAmount && hasTransactionHint) ||
            (hasAmount && hasFinanceHint)
    }

    private fun normalizeBody(body: String): String =
        body.replace(Regex("""\s+"""), " ").trim()

    private fun maskBody(body: String): String {
        var masked = body.replace(Regex("""\d{2,}""")) { match ->
            "*".repeat(match.value.length.coerceAtMost(8))
        }
        masked = masked.replace(Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}"""), "***")
        return masked
    }
}
