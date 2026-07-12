package com.sanha.moneytalk.core.firebase

/**
 * 서비스 등급
 *
 * - FREE: 무료 정책과 AI 크레딧/광고 정책을 적용
 * - PREMIUM: 프리미엄 정책을 적용
 *
 * 두 등급 모두 Gemini 호출은 Firebase AI Logic과 App Check를 사용한다.
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
