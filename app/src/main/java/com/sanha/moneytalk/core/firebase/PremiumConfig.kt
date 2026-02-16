package com.sanha.moneytalk.core.firebase

/**
 * Firebase Realtime Database에서 가져오는 프리미엄 설정
 *
 * DB 경로: /config
 * ```
 * {
 *   "gemini_api_key": "서버 관리 API 키",
 *   "free_tier_enabled": true,
 *   "service_enabled": true,
 *   "maintenance_message": ""
 * }
 * ```
 */
data class PremiumConfig(
    /** 서버에서 관리하는 Gemini API 키 (프리미엄 사용자용) */
    val geminiApiKey: String = "",
    /** 무료 티어(사용자 직접 키 입력) 허용 여부 — false면 무료 차단 */
    val freeTierEnabled: Boolean = true,
    /** 서비스 전체 활성화 여부 (점검 시 false) */
    val serviceEnabled: Boolean = true,
    /** 점검 시 표시할 메시지 */
    val maintenanceMessage: String = ""
)
