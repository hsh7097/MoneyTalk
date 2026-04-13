package com.sanha.moneytalk.core.database

import com.sanha.moneytalk.core.database.dao.SyncCoverageDao
import com.sanha.moneytalk.core.database.entity.SyncCoverageEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

enum class SyncCoverageStatus {
    NONE,
    PARTIAL,
    FULL
}

enum class SyncCoverageTrigger {
    AUTO_INITIAL,
    APP_RESUME_INCREMENTAL,
    SMS_RECEIVED_INCREMENTAL,
    MANUAL_INCREMENTAL,
    MANUAL_MONTH_UNLOCK,
    MANUAL_MONTH_SYNC,
    DEBUG_FULL_SYNC,
    DEBUG_RECENT_SYNC,
    PENDING_SILENT_INCREMENTAL
}

/**
 * 실제 성공한 동기화 구간을 저장/판정하는 리포지토리.
 */
@Singleton
class SyncCoverageRepository @Inject constructor(
    private val syncCoverageDao: SyncCoverageDao
) {

    val coverageFlow: Flow<List<SyncCoverageEntity>> = syncCoverageDao.observeAll()

    suspend fun recordCoverage(
        startMillis: Long,
        endMillis: Long,
        trigger: SyncCoverageTrigger,
        expenseCount: Int,
        incomeCount: Int,
        reconciledExpenseCount: Int,
        reconciledIncomeCount: Int
    ) {
        if (endMillis < startMillis) return

        syncCoverageDao.insert(
            SyncCoverageEntity(
                startMillis = startMillis,
                endMillis = endMillis,
                trigger = trigger.name,
                expenseCount = expenseCount,
                incomeCount = incomeCount,
                reconciledExpenseCount = reconciledExpenseCount,
                reconciledIncomeCount = reconciledIncomeCount
            )
        )
    }

    suspend fun clearAll() {
        syncCoverageDao.deleteAll()
    }

    fun getCoverageStatus(
        startMillis: Long,
        endMillis: Long,
        coverages: List<SyncCoverageEntity>
    ): SyncCoverageStatus {
        if (endMillis < startMillis) return SyncCoverageStatus.NONE

        val overlappingRanges = coverages
            .asSequence()
            .filter { coverage ->
                coverage.endMillis >= startMillis && coverage.startMillis <= endMillis
            }
            .sortedBy { it.startMillis }
            .toList()

        if (overlappingRanges.isEmpty()) return SyncCoverageStatus.NONE

        var cursor = startMillis
        for (coverage in overlappingRanges) {
            if (coverage.endMillis < cursor) continue
            if (coverage.startMillis > cursor) {
                return SyncCoverageStatus.PARTIAL
            }

            cursor = if (coverage.endMillis == Long.MAX_VALUE) {
                Long.MAX_VALUE
            } else {
                maxOf(cursor, coverage.endMillis + 1L)
            }

            if (cursor > endMillis) {
                return SyncCoverageStatus.FULL
            }
        }

        return SyncCoverageStatus.PARTIAL
    }
}
