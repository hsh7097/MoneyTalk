package com.sanha.moneytalk.core.database

import com.sanha.moneytalk.core.database.dao.SyncCoverageDao
import com.sanha.moneytalk.core.database.entity.SyncCoverageEntity
import kotlinx.coroutines.flow.Flow
import java.util.Calendar
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
    private data class CoverageRange(
        val startMillis: Long,
        val endMillis: Long
    )

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
        return getCoverageStatusForRanges(
            startMillis = startMillis,
            endMillis = endMillis,
            ranges = coverages.map { coverage ->
                CoverageRange(
                    startMillis = coverage.startMillis,
                    endMillis = coverage.endMillis
                )
            }
        )
    }

    /**
     * 화면 CTA 노출 판단용 날짜 단위 커버리지.
     *
     * 실제 동기화 기록은 ms 단위로 보존하되, "오늘까지 가져왔는지"는 날짜 단위로 판단한다.
     */
    fun getDateCoverageStatus(
        startMillis: Long,
        endMillis: Long,
        coverages: List<SyncCoverageEntity>
    ): SyncCoverageStatus {
        if (endMillis < startMillis) return SyncCoverageStatus.NONE

        return getCoverageStatusForRanges(
            startMillis = startOfDayMillis(startMillis),
            endMillis = endOfDayMillis(endMillis),
            ranges = coverages
                .filter { coverage -> coverage.endMillis >= coverage.startMillis }
                .map { coverage ->
                    CoverageRange(
                        startMillis = startOfDayMillis(coverage.startMillis),
                        endMillis = endOfDayMillis(coverage.endMillis)
                    )
                }
        )
    }

    private fun getCoverageStatusForRanges(
        startMillis: Long,
        endMillis: Long,
        ranges: List<CoverageRange>
    ): SyncCoverageStatus {
        if (endMillis < startMillis) return SyncCoverageStatus.NONE

        val overlappingRanges = ranges
            .asSequence()
            .filter { coverage ->
                coverage.endMillis >= coverage.startMillis &&
                    coverage.endMillis >= startMillis &&
                    coverage.startMillis <= endMillis
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

    private fun startOfDayMillis(timestamp: Long): Long {
        return Calendar.getInstance().apply {
            timeInMillis = timestamp
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun endOfDayMillis(timestamp: Long): Long {
        return Calendar.getInstance().apply {
            timeInMillis = timestamp
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis
    }
}
