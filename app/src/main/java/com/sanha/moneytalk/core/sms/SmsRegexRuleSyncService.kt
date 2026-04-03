package com.sanha.moneytalk.core.sms

import com.sanha.moneytalk.core.database.SmsRegexRuleRepository
import java.util.concurrent.atomic.AtomicBoolean
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
    companion object {
        /** 원격 룰 upsert 최소 주기 */
        private const val REMOTE_SYNC_MIN_INTERVAL_MS = 10L * 60L * 1000L
    }

    data class SyncSummary(
        val assetRuleCount: Int = 0,
        val remoteRuleCount: Int = 0
    )

    private val assetSeeded = AtomicBoolean(false)

    @Volatile
    private var lastRemoteSyncAt: Long = 0L

    suspend fun syncRules(forceRemoteRefresh: Boolean = false): SyncSummary {
        val now = System.currentTimeMillis()
        val assetRules = if (assetSeeded.compareAndSet(false, true)) {
            assetLoader.loadRules().also { repository.upsertRules(it) }
        } else {
            emptyList()
        }

        val shouldSyncRemote = forceRemoteRefresh ||
            (now - lastRemoteSyncAt) >= REMOTE_SYNC_MIN_INTERVAL_MS
        val remoteRules = if (shouldSyncRemote) {
            remoteLoader.loadRules(forceRefresh = forceRemoteRefresh).also {
                repository.upsertRules(it)
                lastRemoteSyncAt = now
            }
        } else {
            emptyList()
        }

        return SyncSummary(
            assetRuleCount = assetRules.size,
            remoteRuleCount = remoteRules.size
        )
    }
}
