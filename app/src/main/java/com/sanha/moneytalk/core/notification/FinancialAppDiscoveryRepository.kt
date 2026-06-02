package com.sanha.moneytalk.core.notification

import com.sanha.moneytalk.core.database.dao.FinancialAppCandidateDao
import com.sanha.moneytalk.core.database.entity.FinancialAppCandidateEntity
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FinancialAppDiscoveryRepository @Inject constructor(
    private val candidateDao: FinancialAppCandidateDao,
    private val remoteRepository: RemoteFinancialAppSource,
    private val llmAnalyzer: FinancialAppCandidateAnalyzer
) {
    companion object {
        private const val REMOTE_CACHE_TTL_MS = 24L * 60 * 60 * 1000
        private const val REMOTE_FAILURE_CACHE_TTL_MS = 10L * 60 * 1000
    }

    private val refreshMutex = Mutex()
    private val inFlightPackages = ConcurrentHashMap.newKeySet<String>()
    private var lastRemoteRefreshAt: Long = 0L

    suspend fun refreshRemoteAppsIfNeeded(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastRemoteRefreshAt < REMOTE_CACHE_TTL_MS) return

        refreshMutex.withLock {
            val lockedNow = System.currentTimeMillis()
            if (!force && lockedNow - lastRemoteRefreshAt < REMOTE_CACHE_TTL_MS) return@withLock

            val remoteApps = remoteRepository.loadApprovedApps()
            if (remoteApps == null) {
                lastRemoteRefreshAt = lockedNow - REMOTE_CACHE_TTL_MS + REMOTE_FAILURE_CACHE_TTL_MS
                return@withLock
            }

            val approvedApps = remoteApps
                .filter { it.enabled && it.packageName.isNotBlank() }
            val approvedPackageNames = approvedApps.map { it.packageName }.toSet()
            approvedApps.forEach { app ->
                cacheSupportedApp(app, lockedNow)
            }
            revokeStaleRemoteSupportedApps(approvedPackageNames, lockedNow)
            lastRemoteRefreshAt = lockedNow
        }
    }

    suspend fun isSupportedFinancialApp(packageName: String): Boolean {
        if (FinancialAppPackageRegistry.isSupportedAppNotificationPackage(packageName)) {
            return true
        }
        val cached = candidateDao.findByPackageName(packageName) ?: return false
        return cached.status == FinancialAppCandidateEntity.STATUS_SUPPORTED
    }

    suspend fun handleUnknownFinancialCandidate(
        packageName: String,
        displayName: String
    ) {
        if (FinancialAppPackageRegistry.isSupportedAppNotificationPackage(packageName)) return

        val existing = candidateDao.findByPackageName(packageName)
        if (existing != null) {
            candidateDao.touch(
                packageName = packageName,
                displayName = displayName,
                lastSeenAt = System.currentTimeMillis()
            )
            return
        }

        if (!inFlightPackages.add(packageName)) return
        try {
            val analysis = llmAnalyzer.analyze(packageName, displayName) ?: return
            if (analysis.shouldReport) {
                reportAndCacheCandidate(analysis)
            } else {
                cacheRejectedCandidate(analysis)
            }
        } finally {
            inFlightPackages.remove(packageName)
        }
    }

    private suspend fun cacheSupportedApp(
        app: RemoteFinancialApp,
        now: Long
    ) {
        val existing = candidateDao.findByPackageName(app.packageName)
        candidateDao.upsert(
            FinancialAppCandidateEntity(
                packageName = app.packageName,
                displayName = app.displayName,
                status = FinancialAppCandidateEntity.STATUS_SUPPORTED,
                appType = app.appType,
                parserProfile = app.parserProfile,
                confidence = 1f,
                reason = "RTDB 승인 금융앱",
                source = FinancialAppCandidateEntity.SOURCE_RTDB,
                reportedAt = existing?.reportedAt,
                lastSeenAt = existing?.lastSeenAt ?: now,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now
            )
        )
    }

    private suspend fun reportAndCacheCandidate(analysis: FinancialAppAnalysis) {
        if (!remoteRepository.canReportCandidates()) {
            cacheReportDisabledCandidate(analysis)
            return
        }

        val reported = remoteRepository.reportCandidate(analysis)
        if (!reported) return

        val now = System.currentTimeMillis()
        candidateDao.upsert(
            FinancialAppCandidateEntity(
                packageName = analysis.packageName,
                displayName = analysis.displayName,
                status = FinancialAppCandidateEntity.STATUS_REPORTED,
                appType = analysis.appType,
                parserProfile = analysis.parserProfile,
                confidence = analysis.confidence,
                reason = analysis.reason,
                source = FinancialAppCandidateEntity.SOURCE_LLM,
                reportedAt = now,
                lastSeenAt = now,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    private suspend fun cacheReportDisabledCandidate(analysis: FinancialAppAnalysis) {
        val now = System.currentTimeMillis()
        candidateDao.upsert(
            FinancialAppCandidateEntity(
                packageName = analysis.packageName,
                displayName = analysis.displayName,
                status = FinancialAppCandidateEntity.STATUS_REPORT_DISABLED,
                appType = analysis.appType,
                parserProfile = analysis.parserProfile,
                confidence = analysis.confidence,
                reason = "후보 리포트 비활성화",
                source = FinancialAppCandidateEntity.SOURCE_LLM,
                lastSeenAt = now,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    private suspend fun cacheRejectedCandidate(analysis: FinancialAppAnalysis) {
        val now = System.currentTimeMillis()
        candidateDao.upsert(
            FinancialAppCandidateEntity(
                packageName = analysis.packageName,
                displayName = analysis.displayName,
                status = FinancialAppCandidateEntity.STATUS_REJECTED,
                appType = analysis.appType,
                parserProfile = analysis.parserProfile,
                confidence = analysis.confidence,
                reason = analysis.reason,
                source = FinancialAppCandidateEntity.SOURCE_LLM,
                lastSeenAt = now,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    private suspend fun revokeStaleRemoteSupportedApps(
        approvedPackageNames: Set<String>,
        now: Long
    ) {
        val remoteSupportedApps = candidateDao.getBySourceAndStatus(
            source = FinancialAppCandidateEntity.SOURCE_RTDB,
            status = FinancialAppCandidateEntity.STATUS_SUPPORTED
        )
        remoteSupportedApps
            .filterNot { it.packageName in approvedPackageNames }
            .forEach { stale ->
                candidateDao.upsert(
                    stale.copy(
                        status = FinancialAppCandidateEntity.STATUS_REJECTED,
                        reason = "RTDB 승인 철회",
                        updatedAt = now
                    )
                )
            }
    }
}
