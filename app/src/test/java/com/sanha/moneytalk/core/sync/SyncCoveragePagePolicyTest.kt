package com.sanha.moneytalk.core.sync

import com.sanha.moneytalk.core.database.SyncCoverageRepository
import com.sanha.moneytalk.core.database.SyncCoverageStatus
import com.sanha.moneytalk.core.database.SyncCoverageTrigger
import com.sanha.moneytalk.core.database.dao.SyncCoverageDao
import com.sanha.moneytalk.core.database.entity.SyncCoverageEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class SyncCoveragePagePolicyTest {

    private val repository = SyncCoverageRepository(FakeSyncCoverageDao())
    private val policy = SyncCoveragePagePolicy(repository)

    @Test
    fun `레거시 전체 동기화 사용자는 월 동기화 완료로 본다`() {
        val isSynced = policy.isMonthSynced(
            year = 2026,
            month = 1,
            monthStartDay = 1,
            isLegacyFullSyncUnlocked = true,
            syncedMonths = emptySet(),
            coverages = emptyList()
        )

        assertTrue(isSynced)
    }

    @Test
    fun `syncedMonths fallback 월은 부분 커버 CTA 대상이 아니다`() {
        val isPartiallyCovered = policy.isPagePartiallyCovered(
            year = 2026,
            month = 1,
            monthStartDay = 1,
            isLegacyFullSyncUnlocked = false,
            syncedMonths = setOf("2026-01"),
            coverages = emptyList()
        )

        assertFalse(isPartiallyCovered)
    }

    @Test
    fun `coverage가 월 일부만 덮으면 부분 커버로 본다`() {
        val status = policy.getPageCoverageStatus(
            year = 2026,
            month = 1,
            monthStartDay = 1,
            coverages = listOf(
                coverage(
                    startMillis = timestamp(2026, 1, 15),
                    endMillis = timestamp(2026, 1, 31, hour = 23, minute = 59)
                )
            )
        )

        assertEquals(SyncCoverageStatus.PARTIAL, status)
    }

    private fun coverage(
        startMillis: Long,
        endMillis: Long
    ): SyncCoverageEntity {
        return SyncCoverageEntity(
            startMillis = startMillis,
            endMillis = endMillis,
            trigger = SyncCoverageTrigger.MANUAL_MONTH_SYNC.name,
            expenseCount = 0,
            incomeCount = 0
        )
    }

    private fun timestamp(
        year: Int,
        month: Int,
        day: Int,
        hour: Int = 0,
        minute: Int = 0
    ): Long {
        return Calendar.getInstance().apply {
            clear()
            set(year, month - 1, day, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private class FakeSyncCoverageDao : SyncCoverageDao {
        override suspend fun insert(entity: SyncCoverageEntity) = Unit

        override fun observeAll(): Flow<List<SyncCoverageEntity>> {
            return flowOf(emptyList())
        }

        override suspend fun deleteAll() = Unit
    }
}
