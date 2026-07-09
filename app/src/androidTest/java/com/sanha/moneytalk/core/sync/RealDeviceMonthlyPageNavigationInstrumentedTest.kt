package com.sanha.moneytalk.core.sync

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sanha.moneytalk.MainActivity
import com.sanha.moneytalk.R
import com.sanha.moneytalk.core.datastore.SettingsDataStore
import com.sanha.moneytalk.core.util.DateUtils
import com.sanha.moneytalk.core.util.MoneyTalkLogger
import kotlinx.coroutines.runBlocking
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
    val composeRule = createEmptyComposeRule()

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private var monthStartDay: Int = 1

    @Test
    fun navigateHomeAndHistoryMonthlyPagesInTenDifferentOrders() {
        assumeSupportedRealDevice()
        prepareStableAppState()

        ActivityScenario.launch(MainActivity::class.java).use {
            dismissBlockingDialogs()

            val months = monthsFrom2025JanuaryToCurrent()
            val orders = buildOrders(months)
            check(orders.size == 10)

            tapBottomNavigation(
                label = context.getString(R.string.nav_home)
            )
            var current = currentYearMonth()
            waitForHomeMonth(current)
            val homeVisitCount = visitOrders(
                navigation = MonthNavigation(
                    screenName = "home",
                    previousContentDescription = context.getString(R.string.home_previous_month),
                    nextContentDescription = context.getString(R.string.home_next_month),
                    waitForMonth = ::waitForHomeMonth
                ),
                orders = orders,
                current = current
            )

            tapBottomNavigation(
                label = context.getString(R.string.nav_history)
            )
            current = currentYearMonth()
            waitForHistoryMonth(current)
            val historyVisitCount = visitOrders(
                navigation = MonthNavigation(
                    screenName = "history",
                    previousContentDescription = context.getString(R.string.home_previous_month),
                    nextContentDescription = context.getString(R.string.home_next_month),
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
    }

    private fun assumeSupportedRealDevice() {
        assumeTrue(
            "SM-F966N 실기기 화면 좌표 기반 검증에서만 실행",
            Build.MODEL == "SM-F966N"
        )
    }

    private fun prepareStableAppState() = runBlocking {
        grantRuntimePermissions()
        val settingsDataStore = SettingsDataStore(context)
        settingsDataStore.setOnboardingCompleted(true)
        settingsDataStore.setScreenOnboardingSeen("home")
        settingsDataStore.setScreenOnboardingSeen("history")
        settingsDataStore.setScreenOnboardingSeen("history_filter")
        monthStartDay = settingsDataStore.getMonthStartDay()
    }

    private fun grantRuntimePermissions() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val permissions = buildList {
            add(Manifest.permission.READ_SMS)
            add(Manifest.permission.RECEIVE_SMS)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        permissions.forEach { permission ->
            try {
                instrumentation.uiAutomation.grantRuntimePermission(context.packageName, permission)
            } catch (exception: SecurityException) {
                MoneyTalkLogger.w(
                    "RealDevicePageNav[grantRuntimePermissions] : grantRuntimePermission failed: $permission",
                    exception
                )
            }
        }
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
            MoneyTalkLogger.i(
                "RealDevicePageNav[visitOrders] : " +
                    "${navigation.screenName} order=${orderIndex + 1} visited=${order.size}"
            )
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
        val contentDescription = if (isMovingForward) {
            navigation.nextContentDescription
        } else {
            navigation.previousContentDescription
        }
        repeat(abs(diff)) {
            val expectedMonth = if (isMovingForward) cursor.next() else cursor.previous()
            clickContentDescription(contentDescription)
            composeRule.waitForIdle()
            dismissBlockingDialogs()
            navigation.waitForMonth(expectedMonth)
            cursor = expectedMonth
        }
        return cursor
    }

    private fun waitForHomeMonth(month: YearMonth) {
        val title = DateUtils.formatCustomYearMonth(month.year, month.month, monthStartDay)
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText(title, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    private fun waitForHistoryMonth(month: YearMonth) {
        val (start, end) = DateUtils.getCustomMonthPeriod(month.year, month.month, monthStartDay)
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

    private fun tapBottomNavigation(label: String) {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText(label, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        dismissBlockingDialogs()
        composeRule.onAllNodesWithText(label, useUnmergedTree = true)
            .onFirst()
            .performClick()
        composeRule.waitForIdle()
        dismissBlockingDialogs()
    }

    private fun dismissBlockingDialogs() {
        val dismissTexts = listOf(
            context.getString(R.string.coach_mark_skip),
            context.getString(R.string.coach_mark_finish),
            context.getString(R.string.classify_dialog_later),
            context.getString(R.string.sync_dialog_dismiss),
            context.getString(R.string.full_sync_ad_later),
            context.getString(R.string.common_confirm),
            context.getString(R.string.common_close)
        )

        repeat(MAX_DIALOG_DISMISS_ATTEMPTS) {
            var clicked = false
            dismissTexts.forEach { text ->
                clicked = clickTextIfPresent(text) || clicked
            }
            if (!clicked) return
            composeRule.waitForIdle()
        }
    }

    private fun clickTextIfPresent(text: String): Boolean {
        val nodes = composeRule.onAllNodesWithText(text, useUnmergedTree = true)
            .fetchSemanticsNodes()
        if (nodes.isEmpty()) {
            return false
        }
        composeRule.onAllNodesWithText(text, useUnmergedTree = true)
            .onFirst()
            .performClick()
        composeRule.waitForIdle()
        return true
    }

    private fun clickContentDescription(contentDescription: String) {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithContentDescription(contentDescription, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        val visibleCenter = composeRule.onAllNodesWithContentDescription(contentDescription, useUnmergedTree = true)
            .fetchSemanticsNodes()
            .map { it.boundsInRoot }
            .firstOrNull { bounds ->
                bounds.left >= 0f &&
                    bounds.top >= 0f &&
                    bounds.width > 0f &&
                    bounds.height > 0f
            }
            ?.center

        if (visibleCenter != null) {
            tapAt(visibleCenter)
            return
        }

        composeRule.onAllNodesWithContentDescription(contentDescription, useUnmergedTree = true)
            .onFirst()
            .performClick()
    }

    private fun tapAt(point: Offset) {
        composeRule.onAllNodes(isRoot(), useUnmergedTree = true)
            .onFirst()
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
        MoneyTalkLogger.i("RealDevicePageNav[writeReport] : ${report.replace("\n", " | ")}")
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
        val (year, month) = DateUtils.getEffectiveCurrentMonth(monthStartDay)
        return YearMonth(
            year = year,
            month = month
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
        val previousContentDescription: String,
        val nextContentDescription: String,
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
        private const val REPORT_FILE = "real_device_monthly_page_navigation_report.txt"
        private const val MAX_DIALOG_DISMISS_ATTEMPTS = 5
    }
}
