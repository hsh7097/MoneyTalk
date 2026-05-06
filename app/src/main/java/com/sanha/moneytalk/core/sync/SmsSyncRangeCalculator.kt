package com.sanha.moneytalk.core.sync

import com.sanha.moneytalk.core.database.dao.ExpenseDao
import com.sanha.moneytalk.core.database.dao.IncomeDao
import com.sanha.moneytalk.core.datastore.SettingsDataStore
import com.sanha.moneytalk.core.util.DateUtils
import com.sanha.moneytalk.core.util.MoneyTalkLogger
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SMS 동기화에 사용할 시간 범위를 계산한다.
 */
@Singleton
class SmsSyncRangeCalculator @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val expenseDao: ExpenseDao,
    private val incomeDao: IncomeDao
) {

    companion object {
        private const val DEFAULT_SYNC_PERIOD_MILLIS = 60L * 24 * 60 * 60 * 1000
        private const val OVERLAP_MARGIN_MILLIS = 5L * 60 * 1000
    }

    suspend fun calculateIncrementalRange(monthStartDay: Int): Pair<Long, Long> {
        val savedSyncTime = settingsDataStore.getLastSyncTime()
        val now = System.currentTimeMillis()
        val effectiveSyncTime = resolveEffectiveSyncTime(savedSyncTime)
        val minStartTime = now - DEFAULT_SYNC_PERIOD_MILLIS - customMonthExtraMillis(monthStartDay)

        val startTime = if (effectiveSyncTime > 0) {
            maxOf(effectiveSyncTime - OVERLAP_MARGIN_MILLIS, minStartTime)
        } else {
            initialSyncStartTime(monthStartDay)
        }

        return startTime to now
    }

    fun calculateMonthRange(year: Int, month: Int, monthStartDay: Int): Pair<Long, Long> {
        return DateUtils.getCustomMonthPeriod(year, month, monthStartDay)
    }

    fun calculateDefaultProviderCatchUpStart(endTime: Long, monthStartDay: Int): Long {
        return (endTime - DEFAULT_SYNC_PERIOD_MILLIS - customMonthExtraMillis(monthStartDay))
            .coerceAtLeast(0L)
    }

    private suspend fun resolveEffectiveSyncTime(savedSyncTime: Long): Long {
        val dbCount = expenseDao.getExpenseCount() + incomeDao.getIncomeCount()
        if (savedSyncTime > 0 && dbCount == 0) {
            MoneyTalkLogger.w("Auto Backup 감지: savedSyncTime 있으나 DB 비어있음 → 리셋")
            settingsDataStore.saveLastSyncTime(0L)
            settingsDataStore.saveLastRcsProviderScanTime(0L)
            return 0L
        }
        return savedSyncTime
    }

    private fun customMonthExtraMillis(monthStartDay: Int): Long {
        return if (monthStartDay > 1) {
            (monthStartDay - 1).toLong() * 24 * 60 * 60 * 1000
        } else {
            0L
        }
    }

    private fun initialSyncStartTime(monthStartDay: Int): Long {
        return Calendar.getInstance().apply {
            if (monthStartDay > 1) {
                add(Calendar.MONTH, -2)
                set(
                    Calendar.DAY_OF_MONTH,
                    monthStartDay.coerceAtMost(getActualMaximum(Calendar.DAY_OF_MONTH))
                )
            } else {
                add(Calendar.MONTH, -1)
                set(Calendar.DAY_OF_MONTH, 1)
            }
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
}
