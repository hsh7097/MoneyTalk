package com.sanha.moneytalk.core.sync

import com.sanha.moneytalk.core.database.SyncCoverageRepository
import com.sanha.moneytalk.core.database.SyncCoverageStatus
import com.sanha.moneytalk.core.database.SyncCoverageTrigger
import com.sanha.moneytalk.core.database.dao.SyncCoverageDao
import com.sanha.moneytalk.core.database.entity.SyncCoverageEntity
import com.sanha.moneytalk.core.sms.SmsIncomeFilter
import com.sanha.moneytalk.core.sms.SmsIncomeParser
import com.sanha.moneytalk.core.sms.SmsInput
import com.sanha.moneytalk.core.sms.SmsTransactionDateResolver
import com.sanha.moneytalk.core.sms.SmsType
import com.sanha.moneytalk.core.util.DateUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.random.Random

class MonthlySmsSyncOrderRegressionTest {

    private val incomeFilter = SmsIncomeFilter()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.KOREA)

    @Test
    fun `2025년 1월부터 현재월까지 월별 동기화 순서가 달라도 데이터가 손실되지 않는다`() {
        val months = monthsFrom2025JanuaryToCurrent()
        val fixtures = buildFixtures(months)
        val expectedIds = fixtures.map { it.sms.id }.toSet()
        val expectedTransactionMonthCounts = fixtures
            .groupingBy { yearMonthKey(it.expectedTransactionTime) }
            .eachCount()

        assertTrue("검증 월 목록은 최소 2025년 1월부터 현재월까지 포함해야 함", months.size >= 12)
        assertTrue("연말/연초 환불 fixture가 포함되어야 함", fixtures.any { it.id == "refund-2026-01-for-2025-12" })

        val orders = buildOrders(months)
        assertEquals("요청한 재귀 검증 횟수", 10, orders.size)
        assertEquals(
            "10번의 월별 읽기 순서는 모두 달라야 함",
            10,
            orders.map { order -> order.map { it.key } }.toSet().size
        )

        orders.forEachIndexed { index, orderedMonths ->
            val result = simulateMonthlySyncs(
                fixtures = fixtures,
                orderedMonths = orderedMonths
            )

            assertEquals(
                "round ${index + 1} - 저장된 SMS ID 집합",
                expectedIds,
                result.savedBySmsId.keys
            )
            assertEquals(
                "round ${index + 1} - 중복 저장 없음",
                expectedIds.size,
                result.savedBySmsId.size
            )
            assertEquals(
                "round ${index + 1} - 거래월별 저장 건수",
                expectedTransactionMonthCounts,
                result.savedBySmsId.values.groupingBy { yearMonthKey(it.transactionTime) }.eachCount()
            )

            fixtures.forEach { fixture ->
                val saved = result.savedBySmsId[fixture.sms.id]
                assertEquals(
                    "round ${index + 1} - ${fixture.id} 거래시각",
                    fixture.expectedTransactionTime,
                    saved?.transactionTime
                )
                assertEquals(
                    "round ${index + 1} - ${fixture.id} 타입",
                    fixture.expectedType,
                    saved?.type
                )
            }

            months.forEach { month ->
                assertEquals(
                    "round ${index + 1} - ${month.key} coverage",
                    SyncCoverageStatus.FULL,
                    result.coverageRepository.getDateCoverageStatus(
                        startMillis = month.range.first,
                        endMillis = minOf(month.range.second, System.currentTimeMillis()),
                        coverages = result.coverages
                    )
                )
                assertTrue(
                    "round ${index + 1} - ${month.key} 동기화 완료 판정",
                    result.coveragePolicy.isMonthSynced(
                        year = month.year,
                        month = month.month,
                        monthStartDay = 1,
                        isLegacyFullSyncUnlocked = false,
                        syncedMonths = emptySet(),
                        coverages = result.coverages
                    )
                )
                assertEquals(
                    "round ${index + 1} - ${month.key} 부분 CTA 미노출",
                    false,
                    result.coveragePolicy.isPagePartiallyCovered(
                        year = month.year,
                        month = month.month,
                        monthStartDay = 1,
                        isLegacyFullSyncUnlocked = false,
                        syncedMonths = emptySet(),
                        coverages = result.coverages
                    )
                )
            }
        }
    }

    private fun simulateMonthlySyncs(
        fixtures: List<SmsFixture>,
        orderedMonths: List<YearMonth>
    ): SyncSimulationResult {
        val savedBySmsId = linkedMapOf<String, SavedTransaction>()
        val coverageDao = FakeSyncCoverageDao()
        val coverageRepository = SyncCoverageRepository(coverageDao)

        orderedMonths.forEach { month ->
            val messages = fixtures
                .map { it.sms }
                .filter { it.date in month.range.first..month.range.second }
                .sortedByDescending { it.date }

            val newMessages = messages.filter { it.id !in savedBySmsId }
            newMessages.forEach { sms ->
                val saved = parseForSimulation(sms)
                if (saved != null) {
                    savedBySmsId[sms.id] = saved
                }
            }

            coverageDao.add(
                SyncCoverageEntity(
                    startMillis = month.range.first,
                    endMillis = minOf(month.range.second, System.currentTimeMillis()),
                    trigger = SyncCoverageTrigger.MANUAL_MONTH_SYNC.name,
                    expenseCount = newMessages.count { incomeFilter.classify(it.body).first == SmsType.PAYMENT },
                    incomeCount = newMessages.count { incomeFilter.classify(it.body).first == SmsType.INCOME }
                )
            )
        }

        return SyncSimulationResult(
            savedBySmsId = savedBySmsId,
            coverageRepository = coverageRepository,
            coveragePolicy = SyncCoveragePagePolicy(coverageRepository),
            coverages = coverageDao.snapshot()
        )
    }

    private fun parseForSimulation(sms: SmsInput): SavedTransaction? {
        val type = incomeFilter.classify(sms.body).first
        val dateTime = when (type) {
            SmsType.PAYMENT -> SmsTransactionDateResolver.extractDateTime(sms.body, sms.date)
            SmsType.INCOME -> SmsIncomeParser.extractDateTime(sms.body, sms.date)
            SmsType.SKIP -> return null
        }
        return SavedTransaction(
            smsId = sms.id,
            type = type,
            transactionTime = dateFormat.parse(dateTime)?.time ?: return null
        )
    }

    private fun buildFixtures(months: List<YearMonth>): List<SmsFixture> {
        val fixtures = mutableListOf<SmsFixture>()

        months.forEachIndexed { index, month ->
            val normalDay = minOf(15, month.lastDay)
            val normalTime = timestamp(month.year, month.month, normalDay, 10, 10)
            fixtures += SmsFixture(
                id = "payment-${month.key}",
                sms = SmsInput(
                    id = "payment-${month.key}",
                    address = "1588-0000",
                    date = normalTime,
                    body = "[KB]${month.month.twoDigits()}/${normalDay.twoDigits()} 10:10\n체크카드출금\n${(index + 1) * 1000}원\n테스트상점"
                ),
                expectedType = SmsType.PAYMENT,
                expectedTransactionTime = normalTime
            )

            val previousMonth = month.previous()
            val receivedTime = timestamp(month.year, month.month, 2, 0, 5)
            val transactionTime = timestamp(previousMonth.year, previousMonth.month, previousMonth.lastDay, 23, 50)
            fixtures += SmsFixture(
                id = "refund-${month.key}-for-${previousMonth.key}",
                sms = SmsInput(
                    id = "refund-${month.key}-for-${previousMonth.key}",
                    address = "1588-0000",
                    date = receivedTime,
                    body = "[KB]${previousMonth.month.twoDigits()}/${previousMonth.lastDay.twoDigits()} 23:50\n출금취소\n10,000원"
                ),
                expectedType = SmsType.INCOME,
                expectedTransactionTime = transactionTime
            )
        }

        return fixtures
    }

    private fun buildOrders(months: List<YearMonth>): List<List<YearMonth>> {
        return listOf(
            months,
            months.asReversed(),
            months.filterIndexed { index, _ -> index % 2 == 0 } + months.filterIndexed { index, _ -> index % 2 == 1 },
            months.filterIndexed { index, _ -> index % 2 == 1 } + months.filterIndexed { index, _ -> index % 2 == 0 },
            centerOut(months),
            outsideIn(months),
            months.chunked(3).asReversed().flatten(),
            months.shuffled(Random(7)),
            months.shuffled(Random(42)),
            months.shuffled(Random(20250430))
        )
    }

    private fun centerOut(months: List<YearMonth>): List<YearMonth> {
        val result = mutableListOf<YearMonth>()
        var left = (months.size - 1) / 2
        var right = left + 1

        while (left >= 0 || right < months.size) {
            if (left >= 0) result += months[left--]
            if (right < months.size) result += months[right++]
        }
        return result
    }

    private fun outsideIn(months: List<YearMonth>): List<YearMonth> {
        val result = mutableListOf<YearMonth>()
        var left = 0
        var right = months.lastIndex

        while (left <= right) {
            result += months[left++]
            if (left <= right) result += months[right--]
        }
        return result
    }

    private fun monthsFrom2025JanuaryToCurrent(): List<YearMonth> {
        val now = Calendar.getInstance()
        val current = YearMonth(
            year = now.get(Calendar.YEAR),
            month = now.get(Calendar.MONTH) + 1
        )
        val months = mutableListOf<YearMonth>()
        var cursor = YearMonth(2025, 1)
        while (cursor <= current) {
            months += cursor
            cursor = cursor.next()
        }
        return months
    }

    private fun timestamp(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int
    ): Long {
        return Calendar.getInstance().apply {
            clear()
            set(year, month - 1, day, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun yearMonthKey(timestamp: Long): String {
        return Calendar.getInstance().apply { timeInMillis = timestamp }.let {
            "${it.get(Calendar.YEAR)}-${(it.get(Calendar.MONTH) + 1).twoDigits()}"
        }
    }

    private fun Int.twoDigits(): String = toString().padStart(2, '0')

    private data class YearMonth(
        val year: Int,
        val month: Int
    ) : Comparable<YearMonth> {
        val key: String = "$year-${month.toString().padStart(2, '0')}"
        val lastDay: Int
            get() = Calendar.getInstance().apply {
                clear()
                set(year, month - 1, 1)
            }.getActualMaximum(Calendar.DAY_OF_MONTH)
        val range: Pair<Long, Long>
            get() = DateUtils.getCustomMonthPeriod(year, month, monthStartDay = 1)

        fun previous(): YearMonth {
            return if (month == 1) YearMonth(year - 1, 12) else YearMonth(year, month - 1)
        }

        fun next(): YearMonth {
            return if (month == 12) YearMonth(year + 1, 1) else YearMonth(year, month + 1)
        }

        override fun compareTo(other: YearMonth): Int {
            return compareValuesBy(this, other, YearMonth::year, YearMonth::month)
        }
    }

    private data class SmsFixture(
        val id: String,
        val sms: SmsInput,
        val expectedType: SmsType,
        val expectedTransactionTime: Long
    )

    private data class SavedTransaction(
        val smsId: String,
        val type: SmsType,
        val transactionTime: Long
    )

    private data class SyncSimulationResult(
        val savedBySmsId: Map<String, SavedTransaction>,
        val coverageRepository: SyncCoverageRepository,
        val coveragePolicy: SyncCoveragePagePolicy,
        val coverages: List<SyncCoverageEntity>
    )

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

        fun add(entity: SyncCoverageEntity) {
            coverages += entity
        }

        fun snapshot(): List<SyncCoverageEntity> {
            return coverages.toList()
        }
    }
}
