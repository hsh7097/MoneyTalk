package com.sanha.moneytalk.receiver

import android.app.Notification
import android.service.notification.StatusBarNotification

/**
 * 앱 알림에서 텍스트를 추출하는 파서 (debug 전용).
 *
 * 화이트리스트 없이 모든 앱의 알림을 수신한다.
 * 결제/비결제 판별은 기존 SMS 파이프라인(SmsPreFilter, SmsIncomeFilter)이 담당하므로,
 * 여기서는 텍스트 추출만 수행한다.
 *
 * address는 패키지명에서 자동 생성 (예: "NOTI_kakaobank")하여
 * SMS의 발신번호와 동일한 역할을 한다.
 */
object NotificationContentParser {

    /** 자기 자신의 패키지 (피드백 루프 방지용, 서비스에서 설정) */
    var selfPackageName: String = ""

    /** 알림에서 추출한 텍스트 (SMS의 address/body/timestamp에 대응) */
    data class ParsedNotification(
        val address: String,
        val title: String,
        val body: String,
        val timestamp: Long
    )

    /**
     * StatusBarNotification → ParsedNotification 변환.
     *
     * 자기 자신의 알림이거나 텍스트가 비어있으면 null 반환.
     * 그 외 모든 앱의 알림을 처리한다.
     */
    fun parse(sbn: StatusBarNotification): ParsedNotification? {
        // 자기 자신의 알림은 무시 (피드백 루프 방지)
        if (sbn.packageName == selfPackageName) return null

        val address = buildAddress(sbn.packageName)

        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""

        // text > bigText > title 순으로 사용.
        // SMS 파이프라인의 길이 제한(130자)에 맞추기 위해 간결한 text를 우선 사용.
        // bigText는 상세 정보(잔액, 시각 등)를 포함하여 130자를 초과할 수 있어
        // SmsPreFilter에서 버려질 위험이 있다.
        val body = when {
            text.isNotBlank() -> text
            bigText.isNotBlank() -> bigText
            title.isNotBlank() -> title
            else -> return null
        }

        return ParsedNotification(
            address = address,
            title = title,
            body = body.trim(),
            timestamp = sbn.postTime
        )
    }

    /**
     * 패키지명에서 알림 sender address 생성.
     *
     * "com." 접두사를 제거하고 "."을 "_"로 변환하여 유니크 키를 만든다.
     *
     * 예시:
     * - "com.kakaobank.channel" → "NOTI_kakaobank_channel"
     * - "com.kbstar.kbbank" → "NOTI_kbstar_kbbank"
     * - "viva.republica.toss" → "NOTI_viva_republica_toss"
     */
    private fun buildAddress(packageName: String): String {
        val stripped = packageName.removePrefix("com.")
        return "NOTI_${stripped.replace('.', '_')}"
    }
}
