package com.sanha.moneytalk.feature.transactionedit

import android.app.PendingIntent
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sanha.moneytalk.R
import com.sanha.moneytalk.core.database.AppDatabase
import com.sanha.moneytalk.core.database.entity.ExpenseEntity
import com.sanha.moneytalk.core.database.entity.IncomeEntity
import com.sanha.moneytalk.core.database.entity.StoreRuleEntity
import com.sanha.moneytalk.core.model.TransferDirection
import com.sanha.moneytalk.core.notification.NotificationTestDependencies
import com.sanha.moneytalk.core.notification.TransactionNotificationIntents
import com.sanha.moneytalk.core.sms.DeletedSmsTracker
import com.sanha.moneytalk.feature.transactionedit.ui.TransactionEditActivity
import com.sanha.moneytalk.feature.transactionedit.ui.TransactionEditViewModel
import com.sanha.moneytalk.feature.transactionedit.ui.model.TransactionType
import dagger.hilt.android.EntryPointAccessors
import java.util.UUID
import java.util.Calendar
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 실제 편집 ViewModel을 열어 둔 채 알림 Broadcast를 처리한다. 개인 기기에서는 실행하지 않는다. */
@RunWith(AndroidJUnit4::class)
class TransactionEditNotificationRaceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private lateinit var database: AppDatabase
    private val scenarios = mutableListOf<ActivityScenario<TransactionEditActivity>>()
    private val expenseIds = mutableSetOf<Long>()
    private val incomeIds = mutableSetOf<Long>()
    private val smsIds = mutableSetOf<String>()
    private val ruleKeywords = mutableSetOf<String>()
    private val pendingIntents = mutableSetOf<PendingIntent>()
    private val trackerPreferences = "edit_notification_race_${UUID.randomUUID()}"
    private var trackerIsolated = false

    @Before
    fun setUp() = runBlocking<Unit>(Dispatchers.IO) {
        check(Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("sdk_gphone")) {
            "Editor notification fixtures must run on a disposable emulator"
        }
        val dependencies = EntryPointAccessors.fromApplication(context, NotificationTestDependencies::class.java)
        database = dependencies.database()
        dependencies.settings().setScreenOnboardingSeen("transaction_edit")
        DeletedSmsTracker.init(object : ContextWrapper(context) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int) =
                super.getSharedPreferences(trackerPreferences, mode)
        })
        trackerIsolated = true
    }

    @After
    fun tearDown() = runBlocking<Unit>(Dispatchers.IO) {
        try {
            scenarios.forEach { it.close() }
            pendingIntents.forEach(PendingIntent::cancel)
            if (::database.isInitialized) {
                // 유형 전환으로 만들어진 행도 이번 fixture의 SMS ID로만 정리한다.
                smsIds.forEach { smsId ->
                    database.expenseDao().getExpenseBySmsId(smsId)?.let { expenseIds += it.id }
                    database.incomeDao().getIncomeBySmsId(smsId)?.let { incomeIds += it.id }
                }
                expenseIds.forEach { database.expenseDao().deleteById(it) }
                incomeIds.forEach { database.incomeDao().deleteById(it) }
                ruleKeywords.forEach { keyword ->
                    database.storeRuleDao().getByKeyword(keyword)?.let { database.storeRuleDao().delete(it) }
                }
            }
        } finally {
            if (trackerIsolated) {
                DeletedSmsTracker.clear()
                DeletedSmsTracker.init(context)
                context.deleteSharedPreferences(trackerPreferences)
            }
        }
    }

    @Test
    fun memoSaveAfterNotificationExclusionKeepsLatestExpenseAndSameIdIncome() = runBlocking<Unit>(Dispatchers.IO) {
        val expense = expense(id = unusedSharedId())
        val income = income(id = expense.id)
        val model = open(expenseId = expense.id)
        val latest = expense.copy(
            amount = 24680, cardName = "Changed card", category = "교통", isFixed = true,
            dateTime = expense.dateTime + 86_423_456L, originalSms = "Updated canonical SMS",
            smsId = newSmsId(), senderAddress = "QA-LATEST", createdAt = 123L
        )
        database.expenseDao().update(latest)
        sendAndWait(TransactionNotificationIntents.excludeExpense(context, expense.id))
        assertTrue(requireNotNull(database.expenseDao().getExpenseById(expense.id)).isExcludedFromStats)

        save(model) { updateMemo("User memo") }

        assertEquals(latest.copy(memo = "User memo", isExcludedFromStats = true),
            database.expenseDao().getExpenseById(expense.id))
        assertEquals(income, database.incomeDao().getIncomeById(income.id))
    }

    @Test
    fun unchangedStoreRuleDoesNotUndoSingleNotificationExclusion() = runBlocking<Unit>(Dispatchers.IO) {
        val expense = expense()
        val sibling = expense(storeName = expense.storeName)
        ruleKeywords += expense.storeName
        database.storeRuleDao().upsert(StoreRuleEntity(
            keyword = expense.storeName, isExcludedFromStats = false
        ))
        val originalRule = requireNotNull(database.storeRuleDao().getByKeyword(expense.storeName))
        val model = open(expenseId = expense.id)
        assertTrue(model.uiState.value.applyStatsExcludeToAll)
        sendAndWait(TransactionNotificationIntents.excludeExpense(context, expense.id))

        save(model) { updateMemo("Only this memo changed") }

        assertEquals(expense.copy(memo = "Only this memo changed", isExcludedFromStats = true),
            database.expenseDao().getExpenseById(expense.id))
        assertEquals(sibling, database.expenseDao().getExpenseById(sibling.id))
        assertEquals(originalRule, database.storeRuleDao().getByKeyword(expense.storeName))

        // 이미 규칙(false)과 단건(true)이 다른 상태에서 다시 열어 메모만 바꿔도 전체 규칙으로 확대하지 않는다.
        val reopened = open(expenseId = expense.id)
        assertTrue(reopened.uiState.value.isExcludedFromStats)
        save(reopened) { updateMemo("Another memo only") }
        assertEquals(expense.copy(memo = "Another memo only", isExcludedFromStats = true),
            database.expenseDao().getExpenseById(expense.id))
        assertEquals(sibling, database.expenseDao().getExpenseById(sibling.id))
        assertEquals(originalRule, database.storeRuleDao().getByKeyword(expense.storeName))
    }

    @Test
    fun notificationDeleteBlocksExpenseSaveAndSameStoreRuleSideEffects() = runBlocking<Unit>(Dispatchers.IO) {
        val expense = expense()
        val sibling = expense(storeName = expense.storeName)
        ruleKeywords += expense.storeName
        val model = open(expenseId = expense.id)
        instrumentation.runOnMainSync {
            model.updateMemo("Must not save")
            model.updateStatsExcluded(true)
            model.updateApplyStatsExcludeToAll(true)
        }
        sendAndWait(TransactionNotificationIntents.deleteExpense(context, expense.id))

        saveExpectingMissing(model)

        assertNull(database.expenseDao().getExpenseById(expense.id))
        assertNull(database.incomeDao().getIncomeBySmsId(expense.smsId))
        assertEquals(sibling, database.expenseDao().getExpenseById(sibling.id))
        assertNull(database.storeRuleDao().getByKeyword(expense.storeName))
    }

    @Test
    fun notificationDeleteBlocksBothCrossTableConversions() = runBlocking<Unit>(Dispatchers.IO) {
        val expense = expense(id = unusedSharedId())
        val income = income(id = expense.id)
        val expenseModel = open(expenseId = expense.id)
        instrumentation.runOnMainSync { expenseModel.setTransactionType(TransactionType.INCOME) }
        sendAndWait(TransactionNotificationIntents.deleteExpense(context, expense.id))
        saveExpectingMissing(expenseModel)
        assertNull(database.expenseDao().getExpenseById(expense.id))
        assertNull(database.incomeDao().getIncomeBySmsId(expense.smsId))
        assertEquals(income, database.incomeDao().getIncomeById(income.id))

        val incomeModel = open(incomeId = income.id)
        instrumentation.runOnMainSync { incomeModel.setTransactionType(TransactionType.TRANSFER) }
        sendAndWait(TransactionNotificationIntents.deleteIncome(context, income.id))
        saveExpectingMissing(incomeModel)
        assertNull(database.incomeDao().getIncomeById(income.id))
        assertNull(database.expenseDao().getExpenseBySmsId(requireNotNull(income.smsId)))
    }

    @Test
    fun conversionsKeepLatestSourceAndUserSelectedTypeAndCategory() = runBlocking<Unit>(Dispatchers.IO) {
        val expense = expense()
        val expenseModel = open(expenseId = expense.id)
        val latestExpense = expense.copy(
            amount = 24680, dateTime = expense.dateTime + 123_456L,
            originalSms = "Latest expense SMS", smsId = newSmsId(), senderAddress = "QA-CONVERSION"
        )
        database.expenseDao().update(latestExpense)
        save(expenseModel) {
            setTransactionType(TransactionType.INCOME)
            updateCategory("급여")
            updateMemo("Converted to income")
        }
        val convertedIncome = requireNotNull(database.incomeDao().getIncomeBySmsId(latestExpense.smsId))
        incomeIds += convertedIncome.id
        assertNull(database.expenseDao().getExpenseById(expense.id))
        assertEquals(latestExpense.amount, convertedIncome.amount)
        assertEquals(latestExpense.dateTime, convertedIncome.dateTime)
        assertEquals(latestExpense.originalSms, convertedIncome.originalSms)
        assertEquals(latestExpense.senderAddress, convertedIncome.senderAddress)
        assertEquals("급여", convertedIncome.category)
        assertEquals("Converted to income", convertedIncome.memo)

        val incomeModel = open(incomeId = convertedIncome.id)
        val latestIncome = convertedIncome.copy(amount = 98760, dateTime = convertedIncome.dateTime + 86_400_789L)
        database.incomeDao().update(latestIncome)
        save(incomeModel) {
            setTransactionType(TransactionType.TRANSFER)
            updateTransferDirection(TransferDirection.DEPOSIT)
            updateCategory("이체")
            updateMemo("Converted to transfer")
        }
        val convertedExpense = requireNotNull(database.expenseDao().getExpenseBySmsId(latestExpense.smsId))
        expenseIds += convertedExpense.id
        assertNull(database.incomeDao().getIncomeById(convertedIncome.id))
        assertEquals(latestIncome.amount, convertedExpense.amount)
        assertEquals(latestIncome.dateTime, convertedExpense.dateTime)
        assertEquals(latestIncome.originalSms, convertedExpense.originalSms)
        assertEquals(latestIncome.senderAddress, convertedExpense.senderAddress)
        assertEquals("TRANSFER", convertedExpense.transactionType)
        assertEquals("DEPOSIT", convertedExpense.transferDirection)
        assertEquals("이체", convertedExpense.category)
        assertEquals("Converted to transfer", convertedExpense.memo)
    }

    @Test
    fun incomeMemoSavePreservesLatestFieldsAndExplicitExpenseInclusionStillWorks() = runBlocking<Unit>(Dispatchers.IO) {
        val income = income()
        val model = open(incomeId = income.id)
        val latestIncome = income.copy(
            amount = 98760, type = "환급", source = "Changed source", category = "상여금",
            isRecurring = true, recurringDay = 12, dateTime = income.dateTime + 86_423_456L,
            originalSms = "Latest income SMS", smsId = newSmsId(), senderAddress = "QA-INCOME", createdAt = 321L
        )
        database.incomeDao().update(latestIncome)
        save(model) { updateMemo("Income user memo") }
        assertEquals(latestIncome.copy(memo = "Income user memo"), database.incomeDao().getIncomeById(income.id))

        val expense = expense()
        database.expenseDao().update(expense.copy(isExcludedFromStats = true))
        val expenseModel = open(expenseId = expense.id)
        val latestExpense = expense.copy(amount = 43210, isExcludedFromStats = true)
        database.expenseDao().update(latestExpense)
        save(expenseModel) { updateStatsExcluded(false) }
        assertEquals(latestExpense.copy(isExcludedFromStats = false), database.expenseDao().getExpenseById(expense.id))
    }

    @Test
    fun editorDeleteUsesLatestSourceAndReportsAnAlreadyDeletedIncomeAsMissing() = runBlocking<Unit>(Dispatchers.IO) {
        val expense = expense(id = unusedSharedId())
        val income = income(id = expense.id)
        val model = open(expenseId = expense.id)
        val latest = expense.copy(smsId = newSmsId(), originalSms = "Replacement source")
        database.expenseDao().update(latest)
        instrumentation.runOnMainSync {
            // 아직 저장하지 않은 유형 전환은 삭제할 테이블을 바꾸지 않는다.
            model.setTransactionType(TransactionType.INCOME)
            model.delete()
        }
        withTimeout(10_000) {
            while (!model.uiState.value.isDeleted && model.uiState.value.loadErrorResId == null) delay(25)
        }
        assertTrue(model.uiState.value.isDeleted)
        assertNull(database.expenseDao().getExpenseById(expense.id))
        assertTrue(DeletedSmsTracker.isDeleted(latest.smsId))
        assertFalse(DeletedSmsTracker.isDeleted(expense.smsId))
        assertEquals(income, database.incomeDao().getIncomeById(income.id))

        val incomeModel = open(incomeId = income.id)
        database.incomeDao().deleteById(income.id)
        instrumentation.runOnMainSync { incomeModel.delete() }
        withTimeout(10_000) {
            while (incomeModel.uiState.value.loadErrorResId == null && !incomeModel.uiState.value.isDeleted) delay(25)
        }
        assertEquals(R.string.transaction_edit_not_found, incomeModel.uiState.value.loadErrorResId)
        assertFalse(incomeModel.uiState.value.isDeleted)
        assertFalse(DeletedSmsTracker.isDeleted(requireNotNull(income.smsId)))
    }

    @Test
    fun explicitTimeSelectionKeepsHourAndMinuteTogetherWithTheLatestUneditedDate() = runBlocking<Unit>(Dispatchers.IO) {
        val originalTime = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 20)
            set(Calendar.SECOND, 37)
            set(Calendar.MILLISECOND, 456)
        }.timeInMillis
        val latestCalendar = Calendar.getInstance().apply {
            timeInMillis = originalTime
            add(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 11)
            set(Calendar.MINUTE, 30)
        }
        val expense = expense().copy(dateTime = originalTime)
        database.expenseDao().update(expense)
        val expenseModel = open(expenseId = expense.id)
        database.expenseDao().update(expense.copy(dateTime = latestCalendar.timeInMillis))
        save(expenseModel) { updateTime(10, 20) }
        val expectedExpenseTime = (latestCalendar.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 10)
            set(Calendar.MINUTE, 20)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        assertEquals(expense.copy(dateTime = expectedExpenseTime), database.expenseDao().getExpenseById(expense.id))

        val income = income().copy(dateTime = originalTime)
        database.incomeDao().update(income)
        val incomeModel = open(incomeId = income.id)
        database.incomeDao().update(income.copy(dateTime = latestCalendar.timeInMillis))
        save(incomeModel) { updateTime(9, 40) }
        val expectedIncomeTime = (latestCalendar.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 40)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        assertEquals(income.copy(dateTime = expectedIncomeTime), database.incomeDao().getIncomeById(income.id))
    }

    @Test
    fun switchOffAppliesExistingFalseRuleAndSameStoreCheckboxCanExplicitlyRemoveIt() = runBlocking<Unit>(Dispatchers.IO) {
        val expense = expense().copy(isExcludedFromStats = true)
        val sibling = expense(storeName = expense.storeName).copy(isExcludedFromStats = true)
        database.expenseDao().update(expense)
        database.expenseDao().update(sibling)
        ruleKeywords += expense.storeName
        database.storeRuleDao().upsert(StoreRuleEntity(
            keyword = expense.storeName, isExcludedFromStats = false
        ))
        val model = open(expenseId = expense.id)
        assertTrue(model.uiState.value.applyStatsExcludeToAll)

        // 실제 스위치 행을 눌러 OFF한다. 적용 범위는 체크된 상태로 유지되어야 한다.
        compose.onNodeWithText(context.getString(R.string.transaction_edit_exclude_from_stats))
            .performScrollTo().performClick()
        compose.waitForIdle()
        assertFalse(model.uiState.value.isExcludedFromStats)
        assertTrue(model.uiState.value.applyStatsExcludeToAll)
        save(model) { }
        assertEquals(expense.copy(isExcludedFromStats = false), database.expenseDao().getExpenseById(expense.id))
        assertEquals(sibling.copy(isExcludedFromStats = false), database.expenseDao().getExpenseById(sibling.id))
        assertEquals(false, requireNotNull(database.storeRuleDao().getByKeyword(expense.storeName)).isExcludedFromStats)

        val reopened = open(expenseId = expense.id)
        assertTrue(reopened.uiState.value.applyStatsExcludeToAll)
        compose.onNode(
            hasText(context.getString(R.string.transaction_edit_same_store_apply_short)) and hasClickAction()
        ).performScrollTo().performClick()
        compose.waitForIdle()
        assertFalse(reopened.uiState.value.applyStatsExcludeToAll)
        save(reopened) { }
        assertNull(database.storeRuleDao().getByKeyword(expense.storeName))
    }

    private suspend fun open(expenseId: Long = -1L, incomeId: Long = -1L): TransactionEditViewModel {
        val scenario = ActivityScenario.launch<TransactionEditActivity>(
            TransactionEditActivity.createIntent(context, expenseId = expenseId, incomeId = incomeId)
        )
        scenarios += scenario
        lateinit var model: TransactionEditViewModel
        scenario.onActivity { model = ViewModelProvider(it)[TransactionEditViewModel::class.java] }
        withTimeout(10_000) { while (model.uiState.value.isLoading) delay(25) }
        compose.waitForIdle()
        assertNull(model.uiState.value.loadErrorResId)
        assertFalse(model.uiState.value.isNew)
        return model
    }

    private suspend fun save(model: TransactionEditViewModel, edit: TransactionEditViewModel.() -> Unit) {
        instrumentation.runOnMainSync { model.edit(); model.save() }
        withTimeout(10_000) {
            while (!model.uiState.value.isSaved && model.uiState.value.loadErrorResId == null) delay(25)
        }
        assertNull(model.uiState.value.loadErrorResId)
        assertTrue(model.uiState.value.isSaved)
    }

    private suspend fun saveExpectingMissing(model: TransactionEditViewModel) {
        instrumentation.runOnMainSync { model.save() }
        withTimeout(10_000) {
            while (model.uiState.value.loadErrorResId == null && !model.uiState.value.isSaved) delay(25)
        }
        assertEquals(R.string.transaction_edit_not_found, model.uiState.value.loadErrorResId)
        assertFalse(model.uiState.value.isSaved)
        // 오류 상태에서 다시 저장을 눌러도 생성 경로로 넘어가지 않는다.
        instrumentation.runOnMainSync { model.save() }
        assertFalse(model.uiState.value.isSaved)
    }

    private fun sendAndWait(pending: PendingIntent) {
        pendingIntents += pending
        val finished = CountDownLatch(1)
        pending.send(context, 0, null, { _, _, _, _, _ -> finished.countDown() }, Handler(Looper.getMainLooper()))
        assertTrue("Notification action did not finish", finished.await(10, TimeUnit.SECONDS))
    }

    private suspend fun unusedSharedId(): Long {
        var id = maxOf(System.currentTimeMillis(), 1L shl 33)
        while (database.expenseDao().getExpenseById(id) != null || database.incomeDao().getIncomeById(id) != null) id++
        return id
    }

    private suspend fun expense(id: Long = 0L, storeName: String = "Editor QA ${UUID.randomUUID()}"): ExpenseEntity {
        val row = ExpenseEntity(
            id = id, amount = 12340, storeName = storeName, category = "식비", cardName = "QA Card",
            dateTime = System.currentTimeMillis(), originalSms = "Synthetic expense", smsId = newSmsId()
        )
        val savedId = database.expenseDao().insert(row)
        expenseIds += savedId
        return row.copy(id = savedId)
    }

    private suspend fun income(id: Long = 0L): IncomeEntity {
        val row = IncomeEntity(
            id = id, amount = 56780, type = "입금", source = "QA Income", description = "Editor QA ${UUID.randomUUID()}",
            isRecurring = false, dateTime = System.currentTimeMillis(), smsId = newSmsId()
        )
        val savedId = database.incomeDao().insert(row)
        incomeIds += savedId
        return row.copy(id = savedId)
    }

    private fun newSmsId(): String = "edit-notification-qa-${UUID.randomUUID()}".also { smsIds += it }
}
