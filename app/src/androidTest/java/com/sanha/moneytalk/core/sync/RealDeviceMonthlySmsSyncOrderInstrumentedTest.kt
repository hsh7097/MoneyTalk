package com.sanha.moneytalk.core.sync

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sanha.moneytalk.core.database.SmsBlockedSenderRepository
import com.sanha.moneytalk.core.database.dao.SmsBlockedSenderDao
import com.sanha.moneytalk.core.database.dao.SmsChannelProbeLogDao
import com.sanha.moneytalk.core.database.entity.SmsBlockedSenderEntity
import com.sanha.moneytalk.core.database.entity.SmsChannelProbeLogEntity
import com.sanha.moneytalk.core.sms.SmsChannelProbeCollector
import com.sanha.moneytalk.core.sms.SmsIncomeFilter
import com.sanha.moneytalk.core.sms.SmsIncomeParser
import com.sanha.moneytalk.core.sms.SmsInput
import com.sanha.moneytalk.core.sms.SmsReaderV2
import com.sanha.moneytalk.core.sms.SmsTransactionDateResolver
import com.sanha.moneytalk.core.sms.SmsType
import com.sanha.moneytalk.core.util.DateUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.random.Random

@RunWith(AndroidJUnit4::class)
class RealDeviceMonthlySmsSyncOrderInstrumentedTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val reader = SmsReaderV2(
        smsBlockedSenderRepository = SmsBlockedSenderRepository(FakeBlockedSenderDao()),
        channelProbeCollector = SmsChannelProbeCollector(FakeProbeLogDao())
    )
    private val incomeFilter = SmsIncomeFilter()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.KOREA)

    @Test
    fun realDeviceMonthlyReadsKeepSameMessagesForTenDifferentOrders() = runBlocking(Dispatchers.IO) {
        assumeTrue(
            "READ_SMS 권한이 부여된 실기기에서만 실행",
            context.checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
        )

        val months = monthsFrom2025JanuaryToCurrent()
        val orders = buildOrders(months)
        assertEquals("요청한 재귀 검증 횟수", 10, orders.size)

        val baseline = readUnion(months)
        assumeTrue("실기기 SMS/MMS/RCS provider에서 2025-01 이후 메시지가 있어야 함", baseline.isNotEmpty())

        val baselineParsed = baseline.values.mapNotNull { parseForSummary(it) }
        val baselineIds = baseline.keys
        val refundSummaries = baselineParsed.filter { it.incomeType == "환불" }
        val yearBoundaryRefunds = refundSummaries.filter {
            it.receivedMonth == "2026-01" || it.transactionMonth == "2025-12"
        }

        assumeTrue(
            "실기기에서 2026-01 수신 또는 2025-12 거래 환불 SMS가 최소 1건 확인되어야 함",
            yearBoundaryRefunds.isNotEmpty()
        )

        val orderCounts = mutableListOf<Int>()
        orders.forEachIndexed { index, orderedMonths ->
            val actual = readUnion(orderedMonths)
            orderCounts += actual.size
            assertEquals(
                "round ${index + 1} - 월별 읽기 순서가 달라도 메시지 ID 집합은 동일해야 함",
                baselineIds,
                actual.keys
            )

            val parsed = actual.values.mapNotNull { parseForSummary(it) }
            assertEquals(
                "round ${index + 1} - 거래월별 분류 집계",
                baselineParsed.groupingBy { it.transactionMonth to it.type }.eachCount(),
                parsed.groupingBy { it.transactionMonth to it.type }.eachCount()
            )
        }

        writeReport(
            months = months,
            baselineTotal = baseline.size,
            financialParsed = baselineParsed.size,
            refundCount = refundSummaries.size,
            yearBoundaryRefundCount = yearBoundaryRefunds.size,
            orderCounts = orderCounts
        )
    }

    private suspend fun readUnion(months: List<YearMonth>): LinkedHashMap<String, SmsInput> {
        val result = linkedMapOf<String, SmsInput>()
        for (month in months) {
            val readResult = reader.readAllMessagesByDateRange(
                contentResolver = context.contentResolver,
                startDate = month.range.first,
                endDate = minOf(month.range.second, System.currentTimeMillis())
            )
            assertTrue("${month.key} SMS provider 읽기 성공", readResult.smsProviderReadSucceeded)
            for (message in readResult.messages) {
                result.putIfAbsent(message.id, message)
            }
        }
        return result
    }

    private fun parseForSummary(sms: SmsInput): ParsedSummary? {
        val type = incomeFilter.classify(sms.body).first
        if (type == SmsType.SKIP) return null

        val dateTime = when (type) {
            SmsType.PAYMENT -> SmsTransactionDateResolver.extractDateTime(sms.body, sms.date)
            SmsType.INCOME -> SmsIncomeParser.extractDateTime(sms.body, sms.date)
            SmsType.SKIP -> return null
        }
        val transactionTime = dateFormat.parse(dateTime)?.time ?: return null
        val incomeType = if (type == SmsType.INCOME) SmsIncomeParser.extractIncomeType(sms.body) else ""

        return ParsedSummary(
            type = type,
            incomeType = incomeType,
            receivedMonth = yearMonthKey(sms.date),
            transactionMonth = yearMonthKey(transactionTime)
        )
    }

    private fun writeReport(
        months: List<YearMonth>,
        baselineTotal: Int,
        financialParsed: Int,
        refundCount: Int,
        yearBoundaryRefundCount: Int,
        orderCounts: List<Int>
    ) {
        val report = buildString {
            appendLine("realDevice=true")
            appendLine("deviceMonths=${months.first().key}..${months.last().key}")
            appendLine("orderRuns=${orderCounts.size}")
            appendLine("baselineMessages=$baselineTotal")
            appendLine("financialParsed=$financialParsed")
            appendLine("refundCount=$refundCount")
            appendLine("yearBoundaryRefundCount=$yearBoundaryRefundCount")
            appendLine("orderCounts=${orderCounts.joinToString(",")}")
        }
        context.openFileOutput(REPORT_FILE, Context.MODE_PRIVATE).use { output ->
            output.write(report.toByteArray())
        }
        Log.i(TAG, report.replace("\n", " | "))
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

    private fun yearMonthKey(timestamp: Long): String {
        return Calendar.getInstance().apply { timeInMillis = timestamp }.let {
            "${it.get(Calendar.YEAR)}-${(it.get(Calendar.MONTH) + 1).toString().padStart(2, '0')}"
        }
    }

    private data class YearMonth(
        val year: Int,
        val month: Int
    ) : Comparable<YearMonth> {
        val key: String = "$year-${month.toString().padStart(2, '0')}"
        val range: Pair<Long, Long>
            get() = DateUtils.getCustomMonthPeriod(year, month, monthStartDay = 1)

        fun next(): YearMonth {
            return if (month == 12) YearMonth(year + 1, 1) else YearMonth(year, month + 1)
        }

        override fun compareTo(other: YearMonth): Int {
            return compareValuesBy(this, other, YearMonth::year, YearMonth::month)
        }
    }

    private data class ParsedSummary(
        val type: SmsType,
        val incomeType: String,
        val receivedMonth: String,
        val transactionMonth: String
    )

    private class FakeBlockedSenderDao : SmsBlockedSenderDao {
        override fun observeAll(): Flow<List<SmsBlockedSenderEntity>> = flowOf(emptyList())

        override suspend fun getAllAddresses(): List<String> = emptyList()

        override suspend fun insert(entity: SmsBlockedSenderEntity) = Unit

        override suspend fun deleteByAddress(address: String): Int = 0
    }

    private class FakeProbeLogDao : SmsChannelProbeLogDao {
        override suspend fun insert(log: SmsChannelProbeLogEntity) = Unit

        override suspend fun deleteOlderThan(minCreatedAt: Long): Int = 0
    }

    private companion object {
        private const val TAG = "RealDeviceMonthlySync"
        private const val REPORT_FILE = "real_device_monthly_sync_report.txt"
    }
}
