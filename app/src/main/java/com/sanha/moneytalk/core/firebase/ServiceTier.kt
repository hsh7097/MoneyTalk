package com.sanha.moneytalk.core.firebase

/**
 * 서비스 등급
 *
 * - FREE: 사용자가 직접 입력한 Gemini API 키를 사용
 * - PREMIUM: Firebase에서 관리하는 서버 API 키를 사용 (광고/유료 결제)
 */
enum class ServiceTier {
    FREE,
    PREMIUM;

    companion object {
        fun fromString(value: String): ServiceTier {
            return when (value.uppercase()) {
                "PREMIUM" -> PREMIUM
                else -> FREE
            }
        }
    }
}
