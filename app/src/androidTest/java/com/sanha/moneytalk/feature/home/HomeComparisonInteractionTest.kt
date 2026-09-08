package com.sanha.moneytalk.feature.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sanha.moneytalk.R
import com.sanha.moneytalk.core.theme.MoneyTalkTheme
import com.sanha.moneytalk.core.theme.ThemeMode
import com.sanha.moneytalk.core.util.DateUtils
import com.sanha.moneytalk.feature.home.ui.HomePageContent
import com.sanha.moneytalk.feature.home.ui.HomePageData
import com.sanha.moneytalk.feature.home.ui.theme.HomeTheme
import java.text.NumberFormat
import java.time.YearMonth
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 월 요약과 누적 비교의 서로 다른 집계 범위를 검증한다. DB와 수집 상태는 변경하지 않는다. */
@RunWith(AndroidJUnit4::class)
class HomeComparisonInteractionTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val currentPeriod = DateUtils.getEffectiveCurrentMonth(1)
    private val currentMonth = YearMonth.of(currentPeriod.first, currentPeriod.second)
    private val overviewTag = "home-monthly-overview"

    @Test
    fun futureDatedRecordsRemainInMonthlyTotalButNotTheSamePointComparison() {
        showHome(mutableStateOf(fixture()))

        overviewAmount(520_000).assertIsDisplayed()
        scrollTo(hasText(won(20_000)) and outsideOverview()).assertIsDisplayed()
        scrollTo(text(R.string.finance_comparison_current_less, "10,000")).assertIsDisplayed()
        scrollTo(legendMatcher(currentMonth)).assertIsDisplayed()
        scrollTo(legendMatcher(currentMonth.minusMonths(1))).assertIsDisplayed()
    }

    @Test
    fun collectionStatusControlsNeutralNoticeAndPreviousMonthLegendWithoutTreatingMissingAsZero() {
        val state = mutableStateOf(
            fixture().copy(
                previousMonthSynced = false,
                pageData = currentPage().copy(lastMonthDailyCumulative = listOf(0L, 0L, 0L, 0L))
            )
        )
        showHome(state)

        scrollTo(text(R.string.finance_comparison_previous_incomplete)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.finance_comparison_current_more, "20,000")).assertDoesNotExist()
        scrollTo(legendMatcher(currentMonth)).assertIsDisplayed()
        compose.onNode(legendMatcher(currentMonth.minusMonths(1))).assertDoesNotExist()

        compose.runOnIdle {
            state.value = state.value.copy(previousMonthSynced = true, currentPartiallyCovered = true)
        }
        scrollTo(text(R.string.finance_comparison_current_incomplete)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.finance_comparison_previous_incomplete)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.finance_comparison_current_more, "20,000")).assertDoesNotExist()
        scrollTo(legendMatcher(currentMonth.minusMonths(1))).assertIsDisplayed()
        scrollTo(hasText(won(20_000)) and outsideOverview()).assertIsDisplayed()

        compose.runOnIdle { state.value = state.value.copy(currentPartiallyCovered = false) }
        compose.onNodeWithText(text(R.string.finance_comparison_current_incomplete)).assertDoesNotExist()
        scrollTo(text(R.string.finance_comparison_current_more, "20,000")).assertIsDisplayed()
        scrollTo(legendMatcher(currentMonth.minusMonths(1))).assertIsDisplayed()
    }

    @Test
    fun completedPastJanuaryUsesWholeMonthComparisonAndPreviousDecemberYear() {
        val pastJanuary = YearMonth.of(currentMonth.year - 1, 1)
        val state = fixture().copy(
            year = pastJanuary.year,
            month = pastJanuary.monthValue,
            pageData = HomePageData(
                isLoading = false,
                monthlyExpense = 90_000,
                dailyCumulativeExpenses = listOf(0L, 30_000L, 90_000L),
                lastMonthDailyCumulative = listOf(0L, 5_000L, 20_000L, 50_000L),
                todayDayIndex = -1,
                daysInMonth = pastJanuary.lengthOfMonth()
            )
        )
        showHome(mutableStateOf(state))

        overviewAmount(90_000).assertIsDisplayed()
        scrollTo(hasText(won(90_000)) and outsideOverview()).assertIsDisplayed()
        scrollTo(text(R.string.finance_comparison_past_more, "40,000")).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.finance_comparison_current_more, "40,000")).assertDoesNotExist()
        scrollTo(legendMatcher(pastJanuary)).assertIsDisplayed()
        scrollTo(legendMatcher(pastJanuary.minusMonths(1))).assertIsDisplayed()
        scrollTo(text(R.string.home_expense_top4)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.home_today_transactions)).assertDoesNotExist()
    }

    @Test
    fun longExpenseAmountsRemainCompleteOnOneLineAtLargeFontScale() {
        val amounts = listOf(5_544_692, 999_999_999)
        val state = mutableStateOf(fixture())
        showHome(state, fontScale = 1.5f, width = 320.dp)

        amounts.forEach { amount ->
            compose.runOnIdle {
                state.value = state.value.copy(
                    pageData = currentPage().copy(
                        monthlyExpense = amount,
                        dailyCumulativeExpenses = listOf(0L, amount.toLong()),
                        todayDayIndex = 1
                    )
                )
            }
            val amountText = won(amount)
            val amountNode = overviewAmount(amount).assertIsDisplayed()
            val layouts = mutableListOf<TextLayoutResult>()
            amountNode.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action ->
                assertTrue("Amount exposes its text layout", action(layouts))
            }
            assertEquals(1, layouts.size)
            val layout = layouts.single()
            assertEquals(amountText, layout.layoutInput.text.text)
            assertEquals("Amount and currency unit stay on one line", 1, layout.lineCount)
            assertFalse("$amountText must fit the available width", layout.didOverflowWidth)
            assertFalse("$amountText must fit the available height", layout.didOverflowHeight)
            assertFalse("$amountText must not be ellipsized", layout.isLineEllipsized(0))
            assertEquals(
                "Every digit and the currency unit are laid out",
                amountText.length,
                layout.getLineEnd(0, visibleEnd = true)
            )
        }
    }

    private fun showHome(
        state: MutableState<HomeFixture>,
        fontScale: Float = 1f,
        width: Dp = 400.dp
    ) {
        compose.setContent {
            val density = LocalDensity.current
            val scope = rememberCoroutineScope()
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                MoneyTalkTheme(themeMode = ThemeMode.LIGHT) {
                    HomeTheme {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                            Box(Modifier.width(width).fillMaxHeight()) {
                                val fixture = state.value
                                HomePageContent(
                                    pageData = fixture.pageData,
                                    year = fixture.year,
                                    month = fixture.month,
                                    monthStartDay = 1,
                                    isMonthSynced = true,
                                    isPartiallyCovered = fixture.currentPartiallyCovered,
                                    isPreviousMonthSynced = fixture.previousMonthSynced,
                                    hasSmsPermission = true,
                                    selectedCategory = null,
                                    isSyncing = false,
                                    isAdEnabled = false,
                                    onPreviousMonth = {},
                                    onNextMonth = {},
                                    onIncrementalSync = {},
                                    onFullSync = {},
                                    onCategorySelected = {},
                                    onExpenseSelected = {},
                                    onIncomeSelected = {},
                                    isCurrentPage = true,
                                    coroutineScope = scope
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun fixture() = HomeFixture(
        pageData = currentPage(),
        year = currentMonth.year,
        month = currentMonth.monthValue
    )

    private fun currentPage() = HomePageData(
        isLoading = false,
        monthlyExpense = 520_000,
        dailyCumulativeExpenses = listOf(0L, 10_000L, 20_000L, 520_000L),
        lastMonthDailyCumulative = listOf(0L, 15_000L, 30_000L, 60_000L),
        todayDayIndex = 2,
        daysInMonth = currentMonth.lengthOfMonth()
    )

    private fun overviewAmount(amount: Int) = compose.onNode(
        hasText(won(amount)) and hasAnyAncestor(hasTestTag(overviewTag))
    )

    private fun outsideOverview() = !hasAnyAncestor(hasTestTag(overviewTag))

    private fun legendMatcher(month: YearMonth) =
        hasText(text(R.string.finance_comparison_month_label, month.monthValue)) and
            outsideOverview()

    private fun scrollTo(label: String) = scrollTo(hasText(label))

    private fun scrollTo(matcher: SemanticsMatcher) = compose.onNode(matcher).also {
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollToIndex))
            .performScrollToNode(matcher)
    }

    private fun won(amount: Int) = text(
        R.string.common_won,
        NumberFormat.getNumberInstance(Locale.KOREA).format(amount)
    )

    private fun text(id: Int, vararg args: Any) = context.getString(id, *args)

    private data class HomeFixture(
        val pageData: HomePageData,
        val year: Int,
        val month: Int,
        val previousMonthSynced: Boolean = true,
        val currentPartiallyCovered: Boolean = false
    )
}
