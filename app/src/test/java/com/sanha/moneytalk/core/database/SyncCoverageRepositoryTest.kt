package com.sanha.moneytalk.core.database

import com.sanha.moneytalk.core.database.dao.SyncCoverageDao
import com.sanha.moneytalk.core.database.entity.SyncCoverageEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class SyncCoverageRepositoryTest {

    private val repository = SyncCoverageRepository(FakeSyncCoverageDao())

    @Test
    fun `날짜 단위 판정은 당일 일부 시각도 커버로 본다`() {
        val targetStart = timestamp(2026, 4, 1)
        val targetEnd = timestamp(
            year = 2026,
            month = 4,
            day = 24,
            hour = 23,
            minute = 59,
            second = 59,
            millisecond = 999
        )
        val coverages = listOf(
            coverage(
                startMillis = timestamp(2026, 4, 1),
                endMillis = timestamp(2026, 4, 24, hour = 10)
            )
        )

        val status = repository.getDateCoverageStatus(targetStart, targetEnd, coverages)

        assertEquals(SyncCoverageStatus.FULL, status)
    }

    @Test
    fun `시간 단위 판정은 당일 남은 시각을 누락으로 본다`() {
        val targetStart = timestamp(2026, 4, 1)
        val targetEnd = timestamp(
            year = 2026,
            month = 4,
            day = 24,
            hour = 23,
            minute = 59,
            second = 59,
            millisecond = 999
        )
        val coverages = listOf(
            coverage(
                startMillis = timestamp(2026, 4, 1),
                endMillis = timestamp(2026, 4, 24, hour = 10)
            )
        )

        val status = repository.getCoverageStatus(targetStart, targetEnd, coverages)

        assertEquals(SyncCoverageStatus.PARTIAL, status)
    }

    @Test
    fun `날짜 단위 판정은 시작 날짜가 비어 있으면 부분 커버로 본다`() {
        val targetStart = timestamp(2026, 4, 1)
        val targetEnd = timestamp(
            year = 2026,
            month = 4,
            day = 24,
            hour = 23,
            minute = 59,
            second = 59,
            millisecond = 999
        )
        val coverages = listOf(
            coverage(
                startMillis = timestamp(2026, 4, 2),
                endMillis = timestamp(2026, 4, 24, hour = 10)
            )
        )

        val status = repository.getDateCoverageStatus(targetStart, targetEnd, coverages)

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
        minute: Int = 0,
        second: Int = 0,
        millisecond: Int = 0
    ): Long {
        return Calendar.getInstance().apply {
            clear()
            set(year, month - 1, day, hour, minute, second)
            set(Calendar.MILLISECOND, millisecond)
        }.timeInMillis
    }

    private class FakeSyncCoverageDao : SyncCoverageDao {
        private val coverages = mutableListOf<SyncCoverageEntity>()

        override suspend fun insert(entity: SyncCoverageEntity) {
            coverages += entity
        }

        override fun observeAll(): Flow<List<SyncCoverageEntity>> {
            return flowOf(coverages)
        }

        override suspend fun deleteAll() {
            coverages.clear()
        }
    }
}
