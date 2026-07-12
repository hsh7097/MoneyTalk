package com.sanha.moneytalk.core.firebase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Provides remotely managed AI service state and model names without exposing API keys. */
@Singleton
class GeminiConfigProvider @Inject constructor(
    private val premiumManager: PremiumManager
) {
    suspend fun isServiceAvailable(): Boolean {
        return premiumManager.isAiServiceEnabled()
    }

    val modelConfig: GeminiModelConfig
        get() = premiumManager.premiumConfig.value.modelConfig

    val modelConfigFlow: Flow<GeminiModelConfig> =
        premiumManager.premiumConfig.map { it.modelConfig }

    val serviceStatusFlow: Flow<ServiceStatus> = premiumManager.serviceStatusFlow
}
