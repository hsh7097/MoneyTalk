package com.sanha.moneytalk.core.sync

import com.sanha.moneytalk.core.database.SyncCoverageRepository
import com.sanha.moneytalk.core.database.SyncCoverageTrigger
import com.sanha.moneytalk.core.util.MoneyTalkLogger
import javax.inject.Inject
import javax.inject.Singleton

data class SyncCoverageRecordCounts(
    val expenseCount: Int,
    val incomeCount: Int,
    val reconciledExpenseCount: Int,
    val reconciledIncomeCount: Int
)

/**
 * 성공한 동기화 구간을 coverage 테이블에 기록한다.
 */
@Singleton
class SyncCoverageRecorder @Inject constructor(
    private val syncCoverageRepository: SyncCoverageRepository
) {

    suspend fun recordSuccessfulRange(
        range: Pair<Long, Long>,
        trigger: SyncCoverageTrigger,
        counts: SyncCoverageRecordCounts
    ) {
        try {
            val recordEndMillis = minOf(range.second, System.currentTimeMillis())
            if (recordEndMillis < range.first) return

            syncCoverageRepository.recordCoverage(
                startMillis = range.first,
                endMillis = recordEndMillis,
                trigger = trigger,
                expenseCount = counts.expenseCount,
                incomeCount = counts.incomeCount,
                reconciledExpenseCount = counts.reconciledExpenseCount,
                reconciledIncomeCount = counts.reconciledIncomeCount
            )
        } catch (e: Exception) {
            MoneyTalkLogger.w("동기화 구간 저장 실패: ${e.message}")
        }
    }
}
