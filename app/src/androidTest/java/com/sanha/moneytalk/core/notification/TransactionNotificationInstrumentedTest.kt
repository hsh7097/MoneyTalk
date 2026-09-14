package com.sanha.moneytalk.core.notification

import android.Manifest
import android.app.Activity
import android.app.ActivityOptions
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.os.Build
import android.service.notification.StatusBarNotification
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.sanha.moneytalk.MainActivity
import com.sanha.moneytalk.R
import com.sanha.moneytalk.core.database.AppDatabase
import com.sanha.moneytalk.core.database.dao.ExpenseIngestionResult
import com.sanha.moneytalk.core.database.entity.ExpenseEntity
import com.sanha.moneytalk.core.database.entity.IncomeEntity
import com.sanha.moneytalk.feature.transactionedit.ui.TransactionEditActivity
import com.sanha.moneytalk.feature.transactionedit.ui.TransactionEditArgs
import com.sanha.moneytalk.feature.transactionedit.ui.TransactionEditViewModel
import com.sanha.moneytalk.feature.transactionedit.ui.model.TransactionType
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** 실제 앱 DB/Activity/PendingIntent 연동. 개인 기기에서는 실행하지 않는다. */
@RunWith(AndroidJUnit4::class)
class TransactionNotificationInstrumentedTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private lateinit var database: AppDatabase
    private lateinit var notifications: SmsNotificationManager
    private val expenses = mutableListOf<Long>()
    private val incomes = mutableListOf<Long>()

    @Before fun setUp() = runBlocking<Unit> {
        check(Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("sdk_gphone")) {
            "Notification fixtures must run on a disposable emulator"
        }
        val dependencies = EntryPointAccessors.fromApplication(context, NotificationTestDependencies::class.java)
        database = dependencies.database()
        dependencies.settings().setScreenOnboardingSeen("transaction_edit")
        dependencies.settings().setScreenOnboardingSeen("home")
        if (Build.VERSION.SDK_INT >= 33) {
            instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        notifications = SmsNotificationManager(context)
        notifications.createNotificationChannel()
        notifications.clearTransactionNotifications()
        awaitTransactionNotifications(emptyMap())
    }

    @After fun tearDown() = runBlocking<Unit> {
        if (!::database.isInitialized) return@runBlocking
        // 명시한 수동 QA 모드에서만 가짜 거래/알림을 남겨 실제 알림 창 탭을 검증한다.
        if (InstrumentationRegistry.getArguments().getString("notification_fixture") == "true") return@runBlocking
        instrumentation.runOnMainSync {
            val monitor = ActivityLifecycleMonitorRegistry.getInstance()
            Stage.values().flatMap { monitor.getActivitiesInStage(it).toList() }
                .distinct().filterNot { it.isFinishing }.forEach(Activity::finish)
        }
        notifications.clearTransactionNotifications()
        try {
            awaitTransactionNotifications(emptyMap())
        } finally {
            expenses.forEach { database.expenseDao().deleteById(it) }
            incomes.forEach { database.incomeDao().deleteById(it) }
        }
    }

    @Test fun sameNumericExpenseAndIncomeIdsKeepSeparatePendingIntents() = runBlocking {
        val id = maxOf(System.currentTimeMillis(), 1L shl 33)
        val expense = expense("Notification QA expense", id = id)
        val income = income("Notification QA income", id = id)
        notifications.showExpenseNotification(expense, 12340, "Notification QA expense")
        notifications.showIncomeNotification(income, 56780, "Notification QA income", "입금")
        val active = awaitTransactionNotifications(mapOf(
            "expense:$expense" to "Notification QA expense",
            "income:$income" to "Notification QA income 입금"
        ))
        val expenseNotification = active.single { it.tag == "expense:$expense" }
        val incomeNotification = active.single { it.tag == "income:$income" }
        assertNotEquals(expenseNotification.notification.contentIntent, incomeNotification.notification.contentIntent)
        // 새 manager 인스턴스에서도 같은 거래만 교체하고, 정리는 tag가 있는 알림까지 포함한다.
        SmsNotificationManager(context).showExpenseNotification(expense, 12340, "Notification QA expense updated")
        assertEquals(2, awaitTransactionNotifications(mapOf(
            "expense:$expense" to "Notification QA expense updated",
            "income:$income" to "Notification QA income 입금"
        )).size)
    }

    @Test fun olderExpenseNotificationStillOpensItsOwnPersistedRow() = runBlocking {
        val first = expense("Notification QA first")
        val second = expense("Notification QA second")
        notifications.showExpenseNotification(first, 12340, "Notification QA first")
        notifications.showExpenseNotification(second, 12340, "Notification QA second")
        val pending = awaitTransactionNotifications(mapOf(
            "expense:$first" to "Notification QA first",
            "expense:$second" to "Notification QA second"
        ))
            .single { it.tag == "expense:$first" }.notification.contentIntent
        val activity = open(pending)
        assertEquals(first, activity.intent.getLongExtra(TransactionEditArgs.EXPENSE_ID, -1))
        val model = loadedModel(activity)
        assertEquals("Notification QA first", model.uiState.value.storeName)
        assertEquals("12340", model.uiState.value.amount)
        assertFalse(activity.isTaskRoot)
        assertEquals(MainActivity::class.java.name, activity.parentActivityIntent?.component?.className)
    }

    @Test fun warmNavigationChangesExpenseToIncomeWithoutReusingPreviousViewModel() = runBlocking {
        val expense = expense("Notification QA expense")
        val income = income("Notification QA income")
        val first = open(TransactionNotificationIntents.expense(context, expense))
        val firstModel = loadedModel(first)
        assertEquals(TransactionType.EXPENSE, firstModel.uiState.value.transactionType)
        val second = open(TransactionNotificationIntents.income(context, income))
        val secondModel = loadedModel(second)
        assertNotSame(firstModel, secondModel)
        assertEquals(TransactionType.INCOME, secondModel.uiState.value.transactionType)
        assertEquals("Notification QA income", secondModel.uiState.value.source)
        assertEquals(income, second.intent.getLongExtra(TransactionEditArgs.INCOME_ID, -1))
    }

    @Test fun deletedNotificationTargetShowsMissingMessageInsteadOfCreatingTransaction() = runBlocking {
        val id = expense("Notification QA deleted")
        val pending = TransactionNotificationIntents.expense(context, id)
        database.expenseDao().deleteById(id)
        val model = loadedModel(open(pending))
        assertFalse(model.uiState.value.isNew)
        assertEquals(R.string.transaction_edit_not_found, model.uiState.value.loadErrorResId)
        instrumentation.runOnMainSync { model.save() }
        assertFalse(model.uiState.value.isSaved)
        assertNull(database.expenseDao().getExpenseById(id))
    }

    @Test fun notificationBackReturnsToHome() = runBlocking {
        val id = expense("Notification QA back")
        val activity = open(TransactionNotificationIntents.expense(context, id))
        loadedModel(activity)
        instrumentation.runOnMainSync { activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitUntil(10_000) {
            var resumedHome = false
            instrumentation.runOnMainSync {
                resumedHome = ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED).any { it is MainActivity }
            }
            resumedHome
        }
    }

    @Test fun savingFromNotificationUpdatesOnlyItsTarget() = runBlocking {
        val target = expense("Notification QA edit target")
        val other = expense("Notification QA untouched")
        val model = loadedModel(open(TransactionNotificationIntents.expense(context, target)))
        instrumentation.runOnMainSync {
            model.updateAmount("24680")
            model.updateMemo("notification edit")
            model.save()
        }
        compose.waitUntil(10_000) { model.uiState.value.isSaved }
        val saved = requireNotNull(database.expenseDao().getExpenseById(target))
        assertEquals(24680, saved.amount)
        assertEquals("notification edit", saved.memo)
        assertEquals(12340, database.expenseDao().getExpenseById(other)?.amount)
    }

    @Test fun transferNotificationUsesExpenseRowAndTransferEditor() = runBlocking {
        val id = expense("Notification QA transfer")
        val row = requireNotNull(database.expenseDao().getExpenseById(id))
        database.expenseDao().update(row.copy(transactionType = "TRANSFER", transferDirection = "WITHDRAWAL"))
        val model = loadedModel(open(TransactionNotificationIntents.expense(context, id)))
        assertEquals(TransactionType.TRANSFER, model.uiState.value.transactionType)
        assertEquals("Notification QA transfer", model.uiState.value.storeName)
    }

    @Test fun existingScreenLaunchRetainsEditContract() = runBlocking {
        val id = expense("Notification QA existing entry")
        ActivityScenario.launch<TransactionEditActivity>(TransactionEditActivity.createIntent(context, expenseId = id)).use { scenario ->
            lateinit var activity: TransactionEditActivity
            scenario.onActivity { activity = it }
            assertEquals("Notification QA existing entry", loadedModel(activity).uiState.value.storeName)
            instrumentation.runOnMainSync { loadedModelOnMain(activity).updateRuleKeyword("changed") }
            scenario.recreate()
            scenario.onActivity { activity = it }
            val model = loadedModel(activity)
            assertEquals("changed", model.uiState.value.ruleKeyword)
            assertTrue(model.hasPendingChanges(model.uiState.value))
        }
    }

    private fun awaitTransactionNotifications(expectedTitles: Map<String, String>): List<StatusBarNotification> {
        var active = emptyList<StatusBarNotification>()
        compose.waitUntil(10_000) {
            active = context.getSystemService(NotificationManager::class.java).activeNotifications
                .filter { it.notification.channelId == SmsNotificationManager.CHANNEL_ID }
            active.size == expectedTitles.size && expectedTitles.all { (tag, title) ->
                active.any {
                    it.tag == tag && it.notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() == title
                }
            }
        }
        return active
    }

    private fun open(pending: PendingIntent): TransactionEditActivity {
        val monitor = instrumentation.addMonitor(TransactionEditActivity::class.java.name, null, false)
        try {
            val options = ActivityOptions.makeBasic()
            if (Build.VERSION.SDK_INT >= 34) {
                options.setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
            }
            pending.send(context, 0, null, null, null, null, options.toBundle())
            return requireNotNull(instrumentation.waitForMonitorWithTimeout(monitor, 10_000)) as TransactionEditActivity
        } finally {
            instrumentation.removeMonitor(monitor)
        }
    }

    private fun loadedModel(activity: TransactionEditActivity): TransactionEditViewModel {
        lateinit var model: TransactionEditViewModel
        instrumentation.runOnMainSync { model = loadedModelOnMain(activity) }
        compose.waitUntil(10_000) { !model.uiState.value.isLoading }
        return model
    }

    private fun loadedModelOnMain(activity: TransactionEditActivity) =
        ViewModelProvider(activity)[TransactionEditViewModel::class.java]

    private suspend fun expense(store: String, id: Long = 0): Long {
        val result = database.expenseDao().insertIngested(ExpenseEntity(
            id = id, amount = 12340, storeName = store, category = "식비", cardName = "QA카드",
            dateTime = System.currentTimeMillis(), originalSms = "QA synthetic transaction",
            smsId = "notification-qa-${UUID.randomUUID()}"
        ))
        return (result as ExpenseIngestionResult.Inserted).expenseId.also { expenses += it }
    }

    private suspend fun income(source: String, id: Long = 0): Long = database.incomeDao().insert(IncomeEntity(
        id = id, amount = 56780, type = "입금", source = source, description = source, isRecurring = false,
        dateTime = System.currentTimeMillis(), smsId = "notification-qa-${UUID.randomUUID()}"
    )).also { incomes += it }
}
