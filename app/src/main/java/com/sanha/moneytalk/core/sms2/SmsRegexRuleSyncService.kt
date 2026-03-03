package com.sanha.moneytalk.core.sms2

import com.sanha.moneytalk.core.database.SmsRegexRuleRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * sender 기반 regex 룰 동기화 서비스
 *
 * 병합 순서:
 * 1) Asset seed
 * 2) RTDB overlay (동일 키 충돌 시 덮어씀)
 */
@Singleton
class SmsRegexRuleSyncService @Inject constructor(
    private val repository: SmsRegexRuleRepository,
    private val assetLoader: SmsRegexRuleAssetLoader,
    private val remoteLoader: SmsRegexRemoteRuleLoader
) {
    data class SyncSummary(
        val assetRuleCount: Int = 0,
        val remoteRuleCount: Int = 0
    )

    suspend fun syncRules(forceRemoteRefresh: Boolean = false): SyncSummary {
        val assetRules = assetLoader.loadRules()
        repository.upsertRules(assetRules)

        val remoteRules = remoteLoader.loadRules(forceRefresh = forceRemoteRefresh)
        repository.upsertRules(remoteRules)

        return SyncSummary(
            assetRuleCount = assetRules.size,
            remoteRuleCount = remoteRules.size
        )
    }
}
