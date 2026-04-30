package com.sanha.moneytalk.core.sync

import com.sanha.moneytalk.core.database.SyncCoverageRepository
import com.sanha.moneytalk.core.database.SyncCoverageStatus
import com.sanha.moneytalk.core.database.entity.SyncCoverageEntity
import com.sanha.moneytalk.core.util.DateUtils
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 월별 CTA 노출에 필요한 coverage 상태를 계산한다.
 */
@Singleton
class SyncCoveragePagePolicy @Inject constructor(
    private val syncCoverageRepository: SyncCoverageRepository
) {

    fun isMonthSynced(
        year: Int,
        month: Int,
        monthStartDay: Int,
        isLegacyFullSyncUnlocked: Boolean,
        syncedMonths: Set<String>,
        coverages: List<SyncCoverageEntity>
    ): Boolean {
        if (isLegacyFullSyncUnlocked) return true
        if (getPageCoverageStatus(year, month, monthStartDay, coverages) == SyncCoverageStatus.FULL) {
            return true
        }
        return yearMonthKey(year, month) in syncedMonths
    }

    fun isPagePartiallyCovered(
        year: Int,
        month: Int,
        monthStartDay: Int,
        isLegacyFullSyncUnlocked: Boolean,
        syncedMonths: Set<String>,
        coverages: List<SyncCoverageEntity>
    ): Boolean {
        if (isLegacyFullSyncUnlocked) return false
        if (yearMonthKey(year, month) in syncedMonths) return false

        return getPageCoverageStatus(year, month, monthStartDay, coverages) == SyncCoverageStatus.PARTIAL
    }

    fun getPageCoverageStatus(
        year: Int,
        month: Int,
        monthStartDay: Int,
        coverages: List<SyncCoverageEntity>
    ): SyncCoverageStatus {
        val (startMillis, rawEndMillis) = DateUtils.getCustomMonthPeriod(year, month, monthStartDay)
        val (effectiveYear, effectiveMonth) = DateUtils.getEffectiveCurrentMonth(monthStartDay)
        val endMillis = if (year == effectiveYear && month == effectiveMonth) {
            minOf(rawEndMillis, System.currentTimeMillis())
        } else {
            rawEndMillis
        }

        return syncCoverageRepository.getDateCoverageStatus(
            startMillis = startMillis,
            endMillis = endMillis,
            coverages = coverages
        )
    }

    private fun yearMonthKey(year: Int, month: Int): String {
        return String.format("%04d-%02d", year, month)
    }
}
