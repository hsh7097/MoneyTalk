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
 *   "maintenance_message": "",
 *   "reward_ad_enabled": false,
 *   "reward_ad_chat_count": 5,
 *   "min_version_code": 1,
 *   "min_version_name": "1.0.0",
 *   "force_update_message": ""
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
    val maintenanceMessage: String = "",
    /** 리워드 광고 활성화 여부 (true면 채팅 시 광고 시청 필요) */
    val rewardAdEnabled: Boolean = false,
    /** 리워드 광고 1회 시청 시 충전되는 채팅 횟수 */
    val rewardAdChatCount: Int = 5,
    /** 최소 요구 versionCode — 미만이면 강제 업데이트 */
    val minVersionCode: Int = 1,
    /** 최소 요구 versionName (표시용) */
    val minVersionName: String = "1.0.0",
    /** 강제 업데이트 시 표시할 커스텀 메시지 (빈 문자열이면 기본 메시지 사용) */
    val forceUpdateMessage: String = ""
)
