package com.sanha.moneytalk.core.firebase

import android.util.Log
import com.sanha.moneytalk.core.datastore.SettingsDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gemini API 키 제공자
 *
 * 서비스 티어(무료/프리미엄)와 서버 설정에 따라 올바른 API 키를 제공합니다.
 * 모든 Gemini 소비자(GeminiRepositoryImpl, GeminiCategoryRepositoryImpl,
 * GeminiSmsExtractor, SmsEmbeddingService)가 이 클래스를 통해 API 키를 얻습니다.
 *
 * ## 키 결정 로직
 * - FREE 티어 + 무료 허용: 사용자 직접 입력 키 (DataStore)
 * - FREE 티어 + 무료 차단: 빈 문자열 (서비스 불가)
 * - PREMIUM 티어: 서버 관리 키 (Firebase Realtime DB)
 * - 서버 점검 중: 빈 문자열 (서비스 불가)
 */
@Singleton
class GeminiApiKeyProvider @Inject constructor(
    private val premiumManager: PremiumManager,
    private val settingsDataStore: SettingsDataStore
) {
    companion object {
        private const val TAG = "GeminiApiKeyProvider"
    }

    /**
     * 현재 유효한 API 키를 즉시 반환
     */
    suspend fun getApiKey(): String {
        return premiumManager.getEffectiveApiKey()
    }

    /**
     * API 키가 유효한지 확인
     */
    suspend fun hasValidApiKey(): Boolean {
        return getApiKey().isNotBlank()
    }

    /**
     * API 키 변경을 실시간으로 관찰하는 Flow
     *
     * 서비스 티어 변경, 서버 설정 변경, 사용자 키 변경 모두 반영됩니다.
     */
    val apiKeyFlow: Flow<String> = combine(
        premiumManager.serviceTierFlow,
        premiumManager.premiumConfig,
        settingsDataStore.geminiApiKeyFlow
    ) { tier, config, userKey ->
        when {
            !config.serviceEnabled -> ""
            tier == ServiceTier.PREMIUM && config.geminiApiKey.isNotBlank() -> config.geminiApiKey
            tier == ServiceTier.FREE && config.freeTierEnabled -> userKey
            tier == ServiceTier.FREE && !config.freeTierEnabled -> ""
            else -> userKey
        }
    }

    /**
     * 현재 서비스 상태를 실시간으로 관찰하는 Flow
     */
    val serviceStatusFlow: Flow<ServiceStatus> = premiumManager.serviceStatusFlow

    /**
     * 사용자가 직접 입력한 API 키 저장 (무료 티어 전용)
     */
    suspend fun saveUserApiKey(apiKey: String) {
        settingsDataStore.saveGeminiApiKey(apiKey)
        Log.d(TAG, "사용자 API 키 저장됨")
    }
}
