package com.sanha.moneytalk.core.firebase

import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gemini API 키 및 모델 설정 제공자
 *
 * PremiumManager의 Firebase RTDB 설정을 기반으로 API 키와 모델명을 제공합니다.
 * 모든 Gemini 소비자(GeminiRepositoryImpl, GeminiCategoryRepositoryImpl,
 * GeminiSmsExtractor, SmsEmbeddingService)가 이 클래스를 통해 API 키와 모델 설정을 얻습니다.
 *
 * ## 키 결정 로직
 * - RTDB 키 풀(gemini_api_keys)에서 라운드로빈 선택
 * - 키 풀이 비어있으면 gemini_api_key 단일 키 fallback
 * - 서비스 비활성화 시 빈 문자열
 *
 * ## 모델 설정
 * - RTDB /config/models/ 에서 역할별 모델명을 읽어옴
 * - RTDB에 값이 없으면 GeminiModelConfig 기본값 사용
 */
@Singleton
class GeminiApiKeyProvider @Inject constructor(
    private val premiumManager: PremiumManager
) {
    companion object {
        private const val TAG = "GeminiApiKeyProvider"
    }

    /**
     * 현재 유효한 API 키를 라운드로빈으로 반환
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
     * 키 풀 또는 단일 키의 변경을 감지합니다.
     * Flow에서는 첫 번째 키만 emit하여 변경 감지용으로 사용합니다.
     */
    val apiKeyFlow: Flow<String> = premiumManager.premiumConfig.map { config ->
        if (!config.serviceEnabled) return@map ""
        val keys = config.geminiApiKeys.ifEmpty {
            if (config.geminiApiKey.isNotBlank()) listOf(config.geminiApiKey) else emptyList()
        }
        keys.firstOrNull() ?: ""
    }

    /**
     * 현재 모델 설정 (즉시 조회)
     */
    val modelConfig: GeminiModelConfig
        get() = premiumManager.premiumConfig.value.modelConfig

    /**
     * 모델 설정 변경을 실시간으로 관찰하는 Flow
     */
    val modelConfigFlow: Flow<GeminiModelConfig> =
        premiumManager.premiumConfig.map { it.modelConfig }

    /**
     * 현재 서비스 상태를 실시간으로 관찰하는 Flow
     */
    val serviceStatusFlow: Flow<ServiceStatus> = premiumManager.serviceStatusFlow
}
