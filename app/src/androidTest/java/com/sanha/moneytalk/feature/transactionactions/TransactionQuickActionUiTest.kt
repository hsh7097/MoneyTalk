package com.sanha.moneytalk.feature.transactionactions

import android.content.Intent
import android.os.Build
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.room.withTransaction
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sanha.moneytalk.R
import com.sanha.moneytalk.core.database.AppDatabase
import com.sanha.moneytalk.core.database.entity.BudgetEntity
import com.sanha.moneytalk.core.database.entity.ExpenseEntity
import com.sanha.moneytalk.core.database.entity.IncomeEntity
import com.sanha.moneytalk.core.model.Category
import com.sanha.moneytalk.core.notification.NotificationTestDependencies
import com.sanha.moneytalk.feature.transactionedit.ui.TransactionEditActivity
import com.sanha.moneytalk.feature.transactionedit.ui.TransactionEditArgs
import com.sanha.moneytalk.feature.transactionlist.ui.TransactionDetailListActivity
import dagger.hilt.android.EntryPointAccessors
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 실제 날짜별 목록 → 길게 누르기 → 수정 진입/삭제를 검증한다. 개인 기기에는 실행하지 않는다. */
@RunWith(AndroidJUnit4::class)
class TransactionQuickActionUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefix = "overnight-qa-ui-${UUID.randomUUID()}-"
    private val zone = ZoneId.systemDefault()
    private val testDate = LocalDate.of(2010, 5, 17)
    private lateinit var database: AppDatabase
    private val expenseIds = mutableListOf<Long>()
    private val incomeIds = mutableListOf<Long>()
    private var keepManualFixture = false

    @Before fun setUp() {
        check(Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("sdk_gphone")) {
            "Quick action fixtures must run on a disposable emulator"
        }
        database = EntryPointAccessors.fromApplication(context, NotificationTestDependencies::class.java).database()
    }

    @After fun tearDown() = runBlocking {
        if (!::database.isInitialized || keepManualFixture) return@runBlocking
        // 이 실행이 만든 ID와 문자 식별자가 모두 일치할 때만 삭제한다. 기존 설정/예산은 건드리지 않는다.
        expenseIds.forEach { id ->
            if (database.expenseDao().getExpenseById(id)?.smsId?.startsWith(prefix) == true) {
                database.expenseDao().deleteById(id)
            }
        }
        incomeIds.forEach { id ->
            if (database.incomeDao().getIncomeById(id)?.smsId?.startsWith(prefix) == true) {
                database.incomeDao().deleteById(id)
            }
        }
    }

    @Test fun expenseMenuClosesWithoutChangesRoutesToEditAndDeletesOnlyAfterConfirmation() {
        val title = "${prefix}expense"
        val target = runBlocking { seedExpense(title) }
        val other = runBlocking { seedExpense("${prefix}untouched") }
        val income = runBlocking { seedIncome("${prefix}income", id = target) }
        val original = runBlocking { requireNotNull(database.expenseDao().getExpenseById(target)) }
        val untouched = runBlocking { database.expenseDao().getExpenseById(other) }
        val untouchedIncome = runBlocking { database.incomeDao().getIncomeById(income) }

        launchDate().use {
            openQuickActions(title)
            compose.onNodeWithText(context.getString(R.string.detail_category)).assertDoesNotExist()
            compose.onNodeWithText(context.getString(R.string.quick_transaction_fixed_expense)).assertDoesNotExist()
            compose.onNodeWithText(context.getString(R.string.quick_transaction_exclude_stats)).assertDoesNotExist()
            compose.onNodeWithText(context.getString(R.string.common_save)).assertDoesNotExist()
            compose.onNodeWithContentDescription(context.getString(R.string.common_close))
                .performClick()
            waitForDialogClosed()
            assertEquals(original, runBlocking { database.expenseDao().getExpenseById(target) })

            openQuickActions(title)
            assertEditRouteAndReturn(expenseId = target)
            waitForDialogClosed()
            assertEquals(original, runBlocking { database.expenseDao().getExpenseById(target) })
            assertEquals(untouched, runBlocking { database.expenseDao().getExpenseById(other) })
            assertEquals(untouchedIncome, runBlocking { database.incomeDao().getIncomeById(income) })

            openQuickActions(title)
            clickMenu(R.string.common_delete)
            waitForText(context.getString(R.string.quick_transaction_delete_title))
            clickDeleteDialog(R.string.common_cancel)
            assertEquals(original, runBlocking { database.expenseDao().getExpenseById(target) })

            clickMenu(R.string.common_delete)
            waitForText(context.getString(R.string.quick_transaction_delete_title))
            clickDeleteDialog(R.string.common_delete)
            waitForDatabase { database.expenseDao().getExpenseById(target) == null }
            waitForDialogClosed()
            compose.waitUntil(10_000) { compose.onAllNodesWithText(title).fetchSemanticsNodes().isEmpty() }
            assertNull(runBlocking { database.expenseDao().getExpenseById(target) })
            assertEquals(untouched, runBlocking { database.expenseDao().getExpenseById(other) })
            assertEquals(untouchedIncome, runBlocking { database.incomeDao().getIncomeById(income) })
        }
    }

    @Test fun incomeMenuRoutesAndDeletesIncomeWithoutChangingExpenseWithSameNumericId() {
        val expense = runBlocking { seedExpense("${prefix}expense") }
        val title = "${prefix}income"
        val income = runBlocking { seedIncome(title, id = expense) }
        val original = runBlocking { requireNotNull(database.incomeDao().getIncomeById(income)) }
        val unchangedExpense = runBlocking { database.expenseDao().getExpenseById(expense) }

        launchDate().use {
            openQuickActions(title)
            assertEditRouteAndReturn(incomeId = income)
            waitForDialogClosed()
            assertEquals(original, runBlocking { database.incomeDao().getIncomeById(income) })
            assertEquals(unchangedExpense, runBlocking { database.expenseDao().getExpenseById(expense) })

            openQuickActions(title)
            clickMenu(R.string.common_delete)
            waitForText(context.getString(R.string.quick_transaction_delete_title))
            clickDeleteDialog(R.string.common_cancel)
            assertEquals(original, runBlocking { database.incomeDao().getIncomeById(income) })

            clickMenu(R.string.common_delete)
            waitForText(context.getString(R.string.quick_transaction_delete_title))
            clickDeleteDialog(R.string.common_delete)
            waitForDatabase { database.incomeDao().getIncomeById(income) == null }
            waitForDialogClosed()
            compose.waitUntil(10_000) { compose.onAllNodesWithText(title).fetchSemanticsNodes().isEmpty() }
            assertNull(runBlocking { database.incomeDao().getIncomeById(income) })
            assertEquals(unchangedExpense, runBlocking { database.expenseDao().getExpenseById(expense) })
        }
    }

    /** -e overnight_fixture true일 때만 수동 UI QA용 기록과 전체 예산을 에뮬레이터에 남긴다. */
    @Test fun seedManualOvernightFixture() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("overnight_fixture") == "true")
        val manualPrefix = "overnight-qa-manual-"
        val today = LocalDate.now(zone)
        database.withTransaction {
            // 재실행도 이 메서드가 만든 합성 자료만 교체한다.
            database.expenseDao().getAllExpensesOnce().filter { it.smsId.startsWith(manualPrefix) }
                .forEach { database.expenseDao().deleteById(it.id) }
            database.incomeDao().getAllIncomesOnce().filter { it.smsId?.startsWith(manualPrefix) == true }
                .forEach { database.incomeDao().deleteById(it.id) }

            val expected = today.plusDays(5)
            val latestMonth = YearMonth.from(expected).minusMonths(1)
            repeat(3) { offset ->
                val month = latestMonth.minusMonths(offset.toLong())
                insertManualExpense(
                    manualPrefix, "subscription-$offset", "overnight-qa 구독", 14_500,
                    month.atDay(minOf(expected.dayOfMonth, month.lengthOfMonth())),
                    Category.SUBSCRIPTION.displayName, fixed = true
                )
            }
            insertManualExpense(manualPrefix, "week-current-1", "overnight-qa 식사", 32_000, today.minusDays(1))
            insertManualExpense(manualPrefix, "week-current-2", "overnight-qa 식사", 22_000, today.minusDays(3))
            insertManualExpense(manualPrefix, "week-previous-1", "overnight-qa 식사", 8_000, today.minusDays(8))
            insertManualExpense(manualPrefix, "week-previous-2", "overnight-qa 식사", 4_000, today.minusDays(10))
            val maxExpenseId = database.expenseDao().getAllExpensesOnce().maxOfOrNull { it.id } ?: 0L
            val maxIncomeId = database.incomeDao().getAllIncomesOnce().maxOfOrNull { it.id } ?: 0L
            val sharedId = maxOf(System.currentTimeMillis(), maxExpenseId, maxIncomeId) + 1
            insertManualExpense(
                manualPrefix, "unclassified", "overnight-qa 미분류", 8_500, today,
                Category.UNCLASSIFIED.displayName, id = sharedId
            )
            database.incomeDao().insert(
                IncomeEntity(
                    id = sharedId, smsId = "${manualPrefix}income", amount = 2_600_000,
                    type = "급여", source = "overnight-qa 급여", description = "overnight-qa 급여",
                    isRecurring = false, dateTime = today.atStartOfDay(zone).toInstant().toEpochMilli(),
                    originalSms = "${manualPrefix}income", category = Category.INCOME_SALARY.displayName
                )
            )
            database.budgetDao().insert(BudgetEntity(category = "전체", monthlyLimit = 1_000_000, yearMonth = "default"))
        }
        keepManualFixture = true
    }

    private fun launchDate(): ActivityScenario<TransactionDetailListActivity> = ActivityScenario.launch(
        // TransactionDetailListActivity.open의 날짜 전달 계약을 그대로 사용한다.
        Intent(context, TransactionDetailListActivity::class.java).putExtra("extra_date", testDate.toString())
    )

    private fun openQuickActions(title: String) {
        waitForText(title)
        compose.onNodeWithText(title).performScrollTo().performTouchInput { longClick() }
        waitForText(context.getString(R.string.quick_transaction_details))
        compose.onNode(
            isDialog() and hasAnyDescendant(hasText(title))
        ).assertExists()
    }

    private fun clickMenu(resource: Int) {
        val text = context.getString(resource)
        waitForText(text)
        compose.onNode(hasText(text) and hasAnyAncestor(isDialog())).performClick()
    }

    private fun assertEditRouteAndReturn(expenseId: Long = -1L, incomeId: Long = -1L) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val monitor = instrumentation.addMonitor(TransactionEditActivity::class.java.name, null, false)
        var activity: TransactionEditActivity? = null
        try {
            clickMenu(R.string.quick_transaction_details)
            val launched = requireNotNull(instrumentation.waitForMonitorWithTimeout(monitor, 10_000)) as TransactionEditActivity
            activity = launched
            assertEquals(expenseId, launched.intent.getLongExtra(TransactionEditArgs.EXPENSE_ID, -1L))
            assertEquals(incomeId, launched.intent.getLongExtra(TransactionEditArgs.INCOME_ID, -1L))
        } finally {
            instrumentation.removeMonitor(monitor)
            activity?.let { launched -> instrumentation.runOnMainSync { launched.finish() } }
            instrumentation.waitForIdleSync()
        }
    }

    private fun clickDeleteDialog(resource: Int) {
        val dialog = isDialog() and hasAnyDescendant(hasText(context.getString(R.string.quick_transaction_delete_title)))
        compose.onNode(hasText(context.getString(resource)) and hasAnyAncestor(dialog)).performClick()
    }

    private fun waitForText(text: String) {
        compose.waitUntil(10_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun waitForDialogClosed() {
        compose.waitUntil(10_000) {
            compose.onAllNodes(isDialog()).fetchSemanticsNodes().isEmpty()
        }
    }

    private fun waitForDatabase(condition: suspend () -> Boolean) {
        compose.waitUntil(10_000) { runBlocking { condition() } }
    }

    private suspend fun seedExpense(title: String): Long {
        // 두 테이블에 같은 ID를 안전하게 만들 수 있도록 양쪽에서 비어 있는 ID를 선택한다.
        val maxExpenseId = database.expenseDao().getAllExpensesOnce().maxOfOrNull { it.id } ?: 0L
        val maxIncomeId = database.incomeDao().getAllIncomesOnce().maxOfOrNull { it.id } ?: 0L
        val id = maxOf(System.currentTimeMillis(), maxExpenseId, maxIncomeId) + 1
        check(database.expenseDao().getExpenseById(id) == null && database.incomeDao().getIncomeById(id) == null)
        return database.expenseDao().insert(
            ExpenseEntity(
                id = id, amount = 12_340, storeName = title, category = Category.FOOD.displayName,
                cardName = "${prefix}card", dateTime = testDate.atTime(12, 0).atZone(zone).toInstant().toEpochMilli(),
                originalSms = prefix, smsId = "$prefix${UUID.randomUUID()}"
            )
        ).also(expenseIds::add)
    }

    private suspend fun seedIncome(title: String, id: Long): Long {
        check(database.incomeDao().getIncomeById(id) == null)
        return database.incomeDao().insert(
            IncomeEntity(
                id = id, smsId = "$prefix${UUID.randomUUID()}", amount = 56_780, type = "급여",
                source = title, description = title, isRecurring = false,
                dateTime = testDate.atTime(11, 0).atZone(zone).toInstant().toEpochMilli(),
                originalSms = prefix, category = Category.INCOME_SALARY.displayName
            )
        ).also(incomeIds::add)
    }

    private suspend fun insertManualExpense(
        manualPrefix: String,
        key: String,
        store: String,
        amount: Int,
        date: LocalDate,
        category: String = Category.FOOD.displayName,
        fixed: Boolean = false,
        id: Long = 0L
    ) {
        database.expenseDao().insert(
            ExpenseEntity(
                id = id, smsId = "$manualPrefix$key", amount = amount, storeName = store, category = category,
                cardName = "overnight-qa 카드", dateTime = date.atStartOfDay(zone).toInstant().toEpochMilli(),
                originalSms = "$manualPrefix$key", isFixed = fixed
            )
        )
    }
}
