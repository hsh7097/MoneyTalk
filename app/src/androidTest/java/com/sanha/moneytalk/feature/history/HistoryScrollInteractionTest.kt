package com.sanha.moneytalk.feature.history

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sanha.moneytalk.R
import com.sanha.moneytalk.core.database.entity.ExpenseEntity
import com.sanha.moneytalk.core.model.Category
import com.sanha.moneytalk.core.theme.MoneyTalkTheme
import com.sanha.moneytalk.core.theme.ThemeMode
import com.sanha.moneytalk.core.util.DateUtils
import com.sanha.moneytalk.feature.history.ui.BillingCycleCalendarView
import com.sanha.moneytalk.feature.history.ui.FilterTabRow
import com.sanha.moneytalk.feature.history.ui.HistoryScrollLayout
import com.sanha.moneytalk.feature.history.ui.HistoryTitleBar
import com.sanha.moneytalk.feature.history.ui.PeriodSummaryCard
import com.sanha.moneytalk.feature.history.ui.SearchBar
import com.sanha.moneytalk.feature.history.ui.TransactionListItem
import com.sanha.moneytalk.feature.history.ui.TransactionListView
import com.sanha.moneytalk.feature.history.ui.ViewMode
import java.io.File
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 실제 목록·달력과 같은 중첩 스크롤 경계만 연결한다. DB, 광고, 사용자 설정은 사용하지 않는다. */
@RunWith(AndroidJUnit4::class)
class HistoryScrollInteractionTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val effectiveMonth = DateUtils.getEffectiveCurrentMonth(1)
    private var month by mutableStateOf(YearMonth.of(effectiveMonth.first, effectiveMonth.second))
    private var mode by mutableStateOf(ViewMode.LIST)
    private var searching by mutableStateOf(false)
    private var categories by mutableStateOf(setOf(Category.FOOD.displayName))

    @Test fun listScrollCollapsesSummaryWhileControlsStayAndPullDownRestoresSummary() {
        showFixture()
        val expandedHeight = headerHeight()
        assertTrue(expandedHeight > 0f)
        capture("history-scroll-expanded.png")

        collapseHeader()
        val pinnedTop = bounds("history_pinned_controls").top
        assertEquals(bounds("history-scroll-fixture").top, pinnedTop, 1f)
        assertControlsOnOneLine()
        capture("history-scroll-collapsed.png")

        body().performTouchInput { swipeUp() }
        assertEquals(pinnedTop, bounds("history_pinned_controls").top, 1f)
        compose.onNode(
            SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollToIndex) and
                hasAnyAncestor(hasTestTag("history-scroll-body"))
        ).performScrollToIndex(0)
        body().performTouchInput { swipeDown() }
        compose.waitForIdle()
        assertEquals(expandedHeight, headerHeight(), 1f)
        compose.onNodeWithContentDescription(text(R.string.common_search)).assertIsDisplayed()
        compose.onNodeWithContentDescription(text(R.string.common_add)).assertIsDisplayed()
    }

    @Test fun calendarSwitchKeepsControlsAndMonthOrSearchTransitionRestoresHeader() {
        showFixture()
        val expandedHeight = headerHeight()
        collapseHeader()
        val pinnedTop = bounds("history_pinned_controls").top
        compose.onNodeWithText(text(R.string.history_view_calendar)).performClick()
        compose.runOnIdle { assertEquals(ViewMode.CALENDAR, mode) }
        assertEquals(pinnedTop, bounds("history_pinned_controls").top, 1f)
        body().performTouchInput { swipeDown() }
        compose.waitForIdle()
        assertEquals(expandedHeight, headerHeight(), 1f)

        collapseHeader()
        compose.runOnIdle { month = month.minusMonths(1) }
        compose.waitForIdle()
        assertTrue(headerHeight() > 0f)
        compose.onNodeWithText(text(R.string.finance_history_month, month.year, month.monthValue))
            .assertIsDisplayed()

        compose.onNodeWithContentDescription(text(R.string.common_search)).performClick()
        val searchHeight = headerHeight()
        compose.onNodeWithText(text(R.string.common_filter)).assertDoesNotExist()
        body().performTouchInput { swipeUp() }
        assertEquals(searchHeight, headerHeight(), 1f)
        compose.onNodeWithContentDescription(text(R.string.common_back)).performClick()
        assertTrue(headerHeight() > searchHeight)
        compose.onNodeWithText(text(R.string.history_view_list)).assertIsDisplayed()
    }

    @Test fun narrowLargeTextKeepsModesFilterAndSeparateResetReachableOnOneLine() {
        showFixture(fontScale = 1.5f, dark = true)
        collapseHeader()
        compose.onNodeWithText(text(R.string.history_view_calendar)).performClick()
        compose.runOnIdle { assertEquals(ViewMode.CALENDAR, mode) }
        compose.onNodeWithText(text(R.string.history_view_list)).performClick()
        compose.runOnIdle { assertEquals(ViewMode.LIST, mode) }
        compose.onNodeWithTag("history_filter_action").performScrollTo().assertIsDisplayed()
        assertControlsOnOneLine()
        capture("history-scroll-large-dark.png")
        compose.onNodeWithContentDescription(text(R.string.history_filter_reset))
            .assertIsDisplayed().performClick()
        compose.runOnIdle { assertTrue(categories.isEmpty()) }
        compose.onNodeWithTag("history_filter_action").assertIsDisplayed()
    }

    private fun showFixture(fontScale: Float = 1f, dark: Boolean = false) {
        val entries = listOf(TransactionListItem.Header("테스트 내역", expenseTotal = 120_000)) +
            (1..24).map { index ->
                TransactionListItem.ExpenseItem(
                    ExpenseEntity(
                        id = index.toLong(), amount = 5_000,
                        storeName = "테스트 식당 $index", category = Category.FOOD.displayName,
                        cardName = "테스트카드", dateTime = System.currentTimeMillis(),
                        originalSms = "", smsId = "history-scroll-fixture-$index"
                    )
                )
            }
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                MoneyTalkTheme(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                        HistoryScrollLayout(
                            resetKey = month to searching,
                            scrollEnabled = !searching,
                            modifier = Modifier.width(320.dp).fillMaxHeight()
                                .background(MaterialTheme.colorScheme.background)
                                .testTag("history-scroll-fixture"),
                            header = {
                                if (searching) {
                                    SearchBar(query = "", onQueryChange = {}, onClose = { searching = false })
                                } else {
                                    Column {
                                        HistoryTitleBar(onSearchClick = { searching = true }, onAddClick = {})
                                        PeriodSummaryCard(
                                            year = month.year, month = month.monthValue, monthStartDay = 1,
                                            totalExpense = 5_544_692, totalIncome = 6_000_000,
                                            onPreviousMonth = { month = month.minusMonths(1) },
                                            onNextMonth = { month = month.plusMonths(1) }
                                        )
                                    }
                                }
                            },
                            controls = {
                                if (!searching) FilterTabRow(
                                    currentMode = mode, onModeChange = { mode = it },
                                    selectedExpenseCategories = categories,
                                    onResetFilter = { categories = emptySet() }
                                )
                            }
                        ) { restoreHeader ->
                            Box(Modifier.fillMaxSize().testTag("history-scroll-body")) {
                                if (mode == ViewMode.CALENDAR && !searching) {
                                    BillingCycleCalendarView(
                                        year = month.year, month = month.monthValue, monthStartDay = 1,
                                        dailyTotals = emptyMap(), onDateClick = {}
                                    )
                                } else {
                                    TransactionListView(
                                        items = entries, isLoading = false, isMonthSynced = true,
                                        isAdEnabled = false, onScrollToTop = restoreHeader
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun collapseHeader() {
        repeat(2) { body().performTouchInput { swipeUp() } }
        compose.waitForIdle()
        assertEquals(0f, headerHeight(), 1f)
    }

    private fun assertControlsOnOneLine() {
        val controls = bounds("history_pinned_controls")
        val filter = bounds("history_filter_action")
        val reset = compose.onNodeWithContentDescription(text(R.string.history_filter_reset))
            .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val list = compose.onNodeWithText(text(R.string.history_view_list))
            .fetchSemanticsNode().boundsInRoot
        val calendar = compose.onNodeWithText(text(R.string.history_view_calendar))
            .fetchSemanticsNode().boundsInRoot
        listOf(filter, reset, list, calendar).forEach {
            assertTrue(it.center.y in controls.top..controls.bottom)
            assertEquals(filter.center.y, it.center.y, 2f)
        }
    }

    private fun headerHeight() = bounds("history_collapsing_header").height
    private fun bounds(tag: String) = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
    private fun body() = compose.onNodeWithTag("history-scroll-body")
    private fun text(id: Int, vararg args: Any) = context.getString(id, *args)
    private fun capture(name: String) {
        val bitmap = compose.onNodeWithTag("history-scroll-fixture").captureToImage().asAndroidBitmap()
        File(context.getExternalFilesDir(null), name).outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
