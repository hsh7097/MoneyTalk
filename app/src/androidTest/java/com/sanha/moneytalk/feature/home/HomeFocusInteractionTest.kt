package com.sanha.moneytalk.feature.home

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sanha.moneytalk.R
import com.sanha.moneytalk.core.database.dao.CategorySum
import com.sanha.moneytalk.core.database.entity.ExpenseEntity
import com.sanha.moneytalk.core.database.entity.IncomeEntity
import com.sanha.moneytalk.core.model.Category
import com.sanha.moneytalk.core.theme.MoneyTalkTheme
import com.sanha.moneytalk.core.theme.ThemeMode
import com.sanha.moneytalk.core.util.DateUtils
import com.sanha.moneytalk.feature.home.briefing.BriefingSpendingWindow
import com.sanha.moneytalk.feature.home.briefing.BriefingWeeklyComparison
import com.sanha.moneytalk.feature.home.briefing.SpendingBriefing
import com.sanha.moneytalk.feature.home.recurring.RecurringExpenseForecast
import com.sanha.moneytalk.feature.home.recurring.RecurringExpenseForecastItem
import com.sanha.moneytalk.feature.home.ui.HomePageContent
import com.sanha.moneytalk.feature.home.ui.HomePageData
import com.sanha.moneytalk.feature.home.ui.theme.HomeTheme
import com.sanha.moneytalk.feature.transactionactions.model.TransactionTarget
import java.text.NumberFormat
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 홈의 섹션 접근과 거래 동작을 검증한다. DB/Hilt/네트워크를 사용하지 않는다. */
@RunWith(AndroidJUnit4::class)
class HomeFocusInteractionTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val currentPeriod = DateUtils.getEffectiveCurrentMonth(1)
    private val numberFormat = NumberFormat.getNumberInstance(Locale.KOREA)

    @Test fun monthlyOverviewAndEveryHomeSectionRemainReachable() {
        showHome(mutableStateOf(fixture(pageData = richPage())))

        compose.onNode(
            hasText(won(62_500)) and hasAnyAncestor(hasTestTag("home-monthly-overview"))
        ).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.home_income_badge, "2,600,000")).assertIsDisplayed()
        scrollTo(text(R.string.home_cumulative_spending)).assertIsDisplayed()
        scrollTo(text(R.string.home_briefing_recent_week)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.home_briefing_budget_title)).assertDoesNotExist()
        scrollTo(text(R.string.home_recurring_title)).assertIsDisplayed()
        scrollTo(text(R.string.home_expense_top4)).assertIsDisplayed()
        scrollTo(text(R.string.home_today_transactions)).assertIsDisplayed()
    }

    @Test fun everyTodayRowPreservesExpenseAndIncomeIdsForClicksAndLongClicks() {
        val page = richPage(withToday = true)
        val expensesClicked = mutableListOf<Long>()
        val incomesClicked = mutableListOf<Long>()
        val longClicks = mutableListOf<TransactionTarget>()
        showHome(
            mutableStateOf(fixture(page)),
            onExpense = { expensesClicked.add(it.id) },
            onIncome = { incomesClicked.add(it.id) },
            onLongClick = { longClicks.add(it) }
        )

        val screenshot = compose.onRoot().captureToImage().asAndroidBitmap()
        try {
            File(requireNotNull(context.getExternalFilesDir(null)), "home-restored-overview.png")
                .outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally {
            screenshot.recycle()
        }

        scrollTo("home-fixture newest expense").assertIsDisplayed()
        scrollTo("home-fixture income").assertIsDisplayed()
        scrollTo("home-fixture middle expense").assertIsDisplayed()
        scrollTo("home-fixture oldest expense").assertIsDisplayed()

        tap("home-fixture newest expense")
        tap("home-fixture income")
        longPress("home-fixture newest expense")
        longPress("home-fixture income")
        tap("home-fixture oldest expense")

        compose.runOnIdle {
            assertEquals(listOf(41L, 43L), expensesClicked)
            assertEquals(listOf(41L), incomesClicked)
            assertEquals(
                listOf(TransactionTarget.Expense(41L), TransactionTarget.Income(41L)),
                longClicks
            )
        }
    }

    @Test fun billingPeriodChangesKeepTodayRowsOnlyInTheCurrentMonth() {
        val state = mutableStateOf(fixture(richPage(withToday = true)))
        showHome(state)

        scrollTo("home-fixture oldest expense").assertIsDisplayed()
        val shiftedPeriod = DateUtils.getEffectiveCurrentMonth(2)
        compose.runOnIdle {
            state.value = state.value.copy(
                year = shiftedPeriod.first,
                month = shiftedPeriod.second,
                monthStartDay = 2
            )
        }
        scrollTo("home-fixture oldest expense").assertIsDisplayed()

        val past = YearMonth.of(shiftedPeriod.first, shiftedPeriod.second).minusMonths(1)
        compose.runOnIdle {
            state.value = state.value.copy(year = past.year, month = past.monthValue)
        }
        scrollTo(text(R.string.home_expense_top4)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.home_today_transactions)).assertDoesNotExist()
        compose.onNodeWithText("home-fixture newest expense").assertDoesNotExist()
        compose.onNodeWithText(text(R.string.home_recurring_title)).assertDoesNotExist()

        compose.runOnIdle {
            state.value = state.value.copy(year = shiftedPeriod.first, month = shiftedPeriod.second)
        }
        scrollTo("home-fixture oldest expense").assertIsDisplayed()
    }

    @Test fun briefingShowsWeeklyComparisonWithoutBudgetAndHidesWhenComparisonIsMissing() {
        val page = richPage()
        val briefing = requireNotNull(page.spendingBriefing)
        val state = mutableStateOf(fixture(page))
        showHome(state)

        listOf(0L, -12_500L, null).forEach { remaining ->
            compose.runOnIdle {
                state.value = state.value.copy(pageData = page.copy(
                    monthlyBudget = if (remaining == null) null else page.monthlyBudget,
                    spendingBriefing = briefing.copy(
                        monthlyBudget = if (remaining == null) null else briefing.monthlyBudget,
                        budgetRemaining = remaining
                    )
                ))
            }
            scrollTo(text(R.string.home_briefing_recent_week)).assertIsDisplayed()
            scrollTo(text(R.string.home_briefing_amount, "25,000")).assertIsDisplayed()
            scrollTo(text(R.string.home_briefing_week_increased, "15,000")).assertIsDisplayed()
            listOf(
                text(R.string.home_briefing_budget_title),
                text(R.string.home_briefing_budget_remaining, "0"),
                text(R.string.home_briefing_budget_over, "12,500"),
                text(R.string.home_briefing_budget_missing_settings)
            ).forEach { label -> compose.onNodeWithText(label).assertDoesNotExist() }
        }

        compose.runOnIdle {
            state.value = state.value.copy(pageData = page.copy(
                spendingBriefing = briefing.copy(weeklyComparison = null)
            ))
        }
        compose.onNodeWithText(text(R.string.home_briefing_title)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.home_briefing_recent_week)).assertDoesNotExist()
        scrollTo(text(R.string.home_recurring_title)).assertIsDisplayed()
    }

    private fun scrollTo(label: String) = compose.onNodeWithText(label).also {
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollToIndex))
            .performScrollToNode(hasText(label))
    }

    private fun tap(label: String) {
        scrollTo(label)
        compose.onNode(hasText(label) and hasClickAction()).performClick()
    }

    private fun longPress(label: String) {
        scrollTo(label)
        compose.onNode(hasText(label) and hasClickAction()).performTouchInput { longClick() }
    }

    private fun showHome(
        state: MutableState<HomeFixture>,
        onExpense: (ExpenseEntity) -> Unit = {},
        onIncome: (IncomeEntity) -> Unit = {},
        onLongClick: (TransactionTarget) -> Unit = {}
    ) {
        compose.setContent {
            val scope = rememberCoroutineScope()
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1f)) {
                MoneyTalkTheme(themeMode = ThemeMode.LIGHT) {
                    HomeTheme {
                        Box(
                            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            Box(Modifier.width(400.dp).fillMaxHeight()) {
                                val fixture = state.value
                                HomePageContent(
                                    pageData = fixture.pageData,
                                    year = fixture.year,
                                    month = fixture.month,
                                    monthStartDay = fixture.monthStartDay,
                                    isMonthSynced = true,
                                    isPartiallyCovered = false,
                                    isPreviousMonthSynced = fixture.isPreviousMonthSynced,
                                    hasSmsPermission = true,
                                    selectedCategory = null,
                                    isSyncing = false,
                                    isAdEnabled = false,
                                    onPreviousMonth = {},
                                    onNextMonth = {},
                                    onIncrementalSync = {},
                                    onFullSync = {},
                                    onCategorySelected = {},
                                    onExpenseSelected = onExpense,
                                    onIncomeSelected = onIncome,
                                    isCurrentPage = fixture.isCurrentPage,
                                    coroutineScope = scope,
                                    onTransactionLongClick = onLongClick
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun fixture(pageData: HomePageData) = HomeFixture(
        pageData = pageData,
        year = currentPeriod.first,
        month = currentPeriod.second
    )

    private fun richPage(withToday: Boolean = false): HomePageData {
        val today = LocalDate.now()
        val now = System.currentTimeMillis()
        val expenses = if (withToday) listOf(
            expense(41, "home-fixture newest expense", now),
            expense(42, "home-fixture middle expense", now - 120_000),
            expense(43, "home-fixture oldest expense", now - 180_000)
        ) else emptyList()
        val incomes = if (withToday) listOf(
            IncomeEntity(
                id = 41L,
                amount = 9_000,
                type = "입금",
                source = "home-fixture income",
                description = "home-fixture income",
                isRecurring = false,
                dateTime = now - 60_000
            )
        ) else emptyList()
        return HomePageData(
            isLoading = false,
            monthlyExpense = 62_500,
            monthlyIncome = 2_600_000,
            monthlyBudget = 1_000_000,
            categoryExpenses = listOf(CategorySum(Category.FOOD.displayName, 62_500)),
            todayExpenses = expenses,
            todayIncomes = incomes,
            todayExpense = expenses.sumOf { it.amount },
            todayExpenseCount = expenses.size,
            dailyCumulativeExpenses = listOf(0L) + (1..today.lengthOfMonth()).map {
                (62_500L * it / today.dayOfMonth).coerceAtMost(62_500L)
            },
            lastMonthDailyCumulative = listOf(0L) + (1..today.minusMonths(1).lengthOfMonth()).map {
                40_000L * it / today.dayOfMonth
            },
            daysInMonth = today.lengthOfMonth(),
            todayDayIndex = today.dayOfMonth,
            spendingBriefing = SpendingBriefing(
                periodStart = today.withDayOfMonth(1),
                periodEndInclusive = today.withDayOfMonth(today.lengthOfMonth()),
                isCurrentPeriod = true,
                recordedExpense = 62_500L,
                monthlyBudget = 1_000_000L,
                budgetRemaining = 937_500L,
                remainingDays = 5,
                dailyReference = 187_500L,
                weeklyComparison = BriefingWeeklyComparison(
                    recent = BriefingSpendingWindow(today.minusDays(6), today, 25_000L, 3),
                    previous = BriefingSpendingWindow(today.minusDays(13), today.minusDays(7), 10_000L, 2),
                    largestCategoryIncrease = null
                )
            ),
            recurringForecast = RecurringExpenseForecast(
                asOfDate = today,
                untilDate = today.plusDays(7),
                items = listOf(
                    RecurringExpenseForecastItem(
                        sourceExpenseId = 91L,
                        storeName = "home-fixture subscription",
                        cardName = "fixture card",
                        category = Category.FOOD.displayName,
                        expectedDate = today.plusDays(1),
                        expectedAmount = 14_500L,
                        lastPaymentDate = today.minusMonths(1),
                        observedMonthCount = 3
                    )
                )
            )
        )
    }

    private fun expense(id: Long, name: String, timestamp: Long) = ExpenseEntity(
        id = id,
        amount = 1_000,
        storeName = name,
        category = Category.FOOD.displayName,
        cardName = "fixture card",
        dateTime = timestamp,
        originalSms = "",
        smsId = "home-focus-$id"
    )

    private fun won(amount: Int) = text(R.string.common_won, numberFormat.format(amount))
    private fun text(id: Int, vararg args: Any) = context.getString(id, *args)

    private data class HomeFixture(
        val pageData: HomePageData,
        val year: Int,
        val month: Int,
        val monthStartDay: Int = 1,
        val isCurrentPage: Boolean = true,
        val isPreviousMonthSynced: Boolean = true
    )
}
