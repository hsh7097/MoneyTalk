package com.sanha.moneytalk.core.sync

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sanha.moneytalk.MainActivity
import com.sanha.moneytalk.R
import com.sanha.moneytalk.core.util.DateUtils
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs
import kotlin.random.Random

@RunWith(AndroidJUnit4::class)
class RealDeviceMonthlyPageNavigationInstrumentedTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun navigateHomeAndHistoryMonthlyPagesInTenDifferentOrders() {
        assumeSupportedRealDevice()
        dismissBlockingDialogs()

        val months = monthsFrom2025JanuaryToCurrent()
        val orders = buildOrders(months)
        check(orders.size == 10)

        tapBottomNavigation(
            label = context.getString(R.string.nav_home),
            tapPoint = HOME_NAV_TAP
        )
        var current = currentYearMonth()
        waitForHomeMonth(current)
        val homeVisitCount = visitOrders(
            navigation = MonthNavigation(
                screenName = "home",
                previousTap = HOME_PREVIOUS_MONTH_TAP,
                nextTap = HOME_NEXT_MONTH_TAP,
                waitForMonth = ::waitForHomeMonth
            ),
            orders = orders,
            current = current
        )

        tapBottomNavigation(
            label = context.getString(R.string.nav_history),
            tapPoint = HISTORY_NAV_TAP
        )
        current = currentYearMonth()
        waitForHistoryMonth(current)
        val historyVisitCount = visitOrders(
            navigation = MonthNavigation(
                screenName = "history",
                previousTap = HISTORY_PREVIOUS_MONTH_TAP,
                nextTap = HISTORY_NEXT_MONTH_TAP,
                waitForMonth = ::waitForHistoryMonth
            ),
            orders = orders,
            current = current
        )

        writeReport(
            months = months,
            orderRuns = orders.size,
            homeVisitCount = homeVisitCount,
            historyVisitCount = historyVisitCount
        )
    }

    private fun assumeSupportedRealDevice() {
        assumeTrue(
            "SM-F966N 실기기 화면 좌표 기반 검증에서만 실행",
            Build.MODEL == "SM-F966N"
        )
    }

    private fun visitOrders(
        navigation: MonthNavigation,
        orders: List<List<YearMonth>>,
        current: YearMonth
    ): Int {
        var currentMonth = current
        var visitCount = 0

        orders.forEachIndexed { orderIndex, order ->
            order.forEach { target ->
                currentMonth = navigateByMonthButtons(currentMonth, target, navigation)
                visitCount++
            }
            Log.i(TAG, "${navigation.screenName} order=${orderIndex + 1} visited=${order.size}")
        }

        return visitCount
    }

    private fun navigateByMonthButtons(
        current: YearMonth,
        target: YearMonth,
        navigation: MonthNavigation
    ): YearMonth {
        val diff = target.totalMonths - current.totalMonths
        if (diff == 0) {
            navigation.waitForMonth(target)
            return target
        }

        var cursor = current
        val isMovingForward = diff > 0
        val tapPoint = if (isMovingForward) navigation.nextTap else navigation.previousTap
        repeat(abs(diff)) {
            val expectedMonth = if (isMovingForward) cursor.next() else cursor.previous()
            tapAt(tapPoint)
            composeRule.waitForIdle()
            dismissBlockingDialogs()
            navigation.waitForMonth(expectedMonth)
            cursor = expectedMonth
        }
        return cursor
    }

    private fun waitForHomeMonth(month: YearMonth) {
        val title = DateUtils.formatCustomYearMonth(month.year, month.month, monthStartDay = 1)
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText(title, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    private fun waitForHistoryMonth(month: YearMonth) {
        val (start, end) = DateUtils.getCustomMonthPeriod(month.year, month.month, monthStartDay = 1)
        val startText = formatShortDate(start)
        val endText = formatShortDate(end)
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText(startText, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty() &&
                composeRule.onAllNodesWithText(endText, useUnmergedTree = true)
                    .fetchSemanticsNodes()
                    .isNotEmpty()
        }
    }

    private fun tapBottomNavigation(label: String, tapPoint: Offset) {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText(label, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        tapAt(tapPoint)
        composeRule.waitForIdle()
        dismissBlockingDialogs()
    }

    private fun dismissBlockingDialogs() {
        clickTextIfPresent(context.getString(R.string.coach_mark_skip))
        clickTextIfPresent(context.getString(R.string.classify_dialog_later))
        clickTextIfPresent(context.getString(R.string.common_confirm))
        clickTextIfPresent(context.getString(R.string.common_close))
    }

    private fun clickTextIfPresent(text: String) {
        val nodes = composeRule.onAllNodesWithText(text, useUnmergedTree = true)
            .fetchSemanticsNodes()
        if (nodes.isNotEmpty()) {
            composeRule.onAllNodesWithText(text, useUnmergedTree = true)
                .onFirst()
                .performClick()
            composeRule.waitForIdle()
        }
    }

    private fun tapAt(point: Offset) {
        composeRule.onRoot(useUnmergedTree = true)
            .performTouchInput {
                click(point)
            }
    }

    private fun writeReport(
        months: List<YearMonth>,
        orderRuns: Int,
        homeVisitCount: Int,
        historyVisitCount: Int
    ) {
        val report = buildString {
            appendLine("realDevice=true")
            appendLine("uiNavigation=true")
            appendLine("deviceMonths=${months.first().key}..${months.last().key}")
            appendLine("orderRuns=$orderRuns")
            appendLine("homeVisitCount=$homeVisitCount")
            appendLine("historyVisitCount=$historyVisitCount")
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
        val current = currentYearMonth()
        val months = mutableListOf<YearMonth>()
        var cursor = YearMonth(2025, 1)
        while (cursor <= current) {
            months += cursor
            cursor = cursor.next()
        }
        return months
    }

    private fun currentYearMonth(): YearMonth {
        val calendar = Calendar.getInstance()
        return YearMonth(
            year = calendar.get(Calendar.YEAR),
            month = calendar.get(Calendar.MONTH) + 1
        )
    }

    private fun formatShortDate(timestamp: Long): String {
        val calendar = Calendar.getInstance().apply { timeInMillis = timestamp }
        return String.format(
            Locale.KOREA,
            "%02d.%02d.%02d",
            calendar.get(Calendar.YEAR) % 100,
            calendar.get(Calendar.MONTH) + 1,
            calendar.get(Calendar.DAY_OF_MONTH)
        )
    }

    private data class MonthNavigation(
        val screenName: String,
        val previousTap: Offset,
        val nextTap: Offset,
        val waitForMonth: (YearMonth) -> Unit
    )

    private data class YearMonth(
        val year: Int,
        val month: Int
    ) : Comparable<YearMonth> {
        val key: String = "$year-${month.toString().padStart(2, '0')}"
        val totalMonths: Int = year * 12 + month

        fun next(): YearMonth {
            return if (month == 12) YearMonth(year + 1, 1) else YearMonth(year, month + 1)
        }

        fun previous(): YearMonth {
            return if (month == 1) YearMonth(year - 1, 12) else YearMonth(year, month - 1)
        }

        override fun compareTo(other: YearMonth): Int {
            return compareValuesBy(this, other, YearMonth::year, YearMonth::month)
        }
    }

    private companion object {
        private const val TAG = "RealDevicePageNav"
        private const val REPORT_FILE = "real_device_monthly_page_navigation_report.txt"

        private val HOME_NAV_TAP = Offset(128f, 2397f)
        private val HISTORY_NAV_TAP = Offset(403f, 2397f)

        private val HOME_PREVIOUS_MONTH_TAP = Offset(309f, 215f)
        private val HOME_NEXT_MONTH_TAP = Offset(772f, 215f)
        private val HISTORY_PREVIOUS_MONTH_TAP = Offset(79f, 338f)
        private val HISTORY_NEXT_MONTH_TAP = Offset(352f, 338f)
    }
}
