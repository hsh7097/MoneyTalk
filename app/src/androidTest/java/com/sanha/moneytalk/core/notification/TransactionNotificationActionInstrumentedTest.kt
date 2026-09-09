package com.sanha.moneytalk.core.notification

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.service.notification.StatusBarNotification
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sanha.moneytalk.R
import com.sanha.moneytalk.core.database.AppDatabase
import com.sanha.moneytalk.core.database.dao.ExpenseIngestionResult
import com.sanha.moneytalk.core.database.entity.ExpenseEntity
import com.sanha.moneytalk.core.database.entity.IncomeEntity
import com.sanha.moneytalk.core.sms.DeletedSmsTracker
import com.sanha.moneytalk.feature.transactionactions.model.TransactionTarget
import dagger.hilt.android.EntryPointAccessors
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** 실제 앱 DB/Hilt와 알림 삭제·통계 제외 Broadcast PendingIntent 연동. 개인 기기에서는 실행하지 않는다. */
@RunWith(AndroidJUnit4::class)
class TransactionNotificationActionInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private lateinit var database: AppDatabase
    private lateinit var notifications: SmsNotificationManager
    private lateinit var notificationManager: NotificationManager
    private val expenseIds = mutableListOf<Long>()
    private val incomeIds = mutableListOf<Long>()
    private val ownedNotifications = mutableSetOf<Pair<String, Int>>()
    private val ownedPendingIntents = mutableSetOf<PendingIntent>()
    private val trackerPreferences = "notification_action_test_${UUID.randomUUID()}"
    private var trackerIsolated = false
    private var manualFixtureCreated = false

    @Before
    fun setUp() {
        check(Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("sdk_gphone")) {
            "Notification action fixtures must run on a disposable emulator"
        }
        database = EntryPointAccessors.fromApplication(
            context, NotificationTestDependencies::class.java
        ).database()
        if (Build.VERSION.SDK_INT >= 33) {
            instrumentation.uiAutomation.grantRuntimePermission(
                context.packageName, Manifest.permission.POST_NOTIFICATIONS
            )
        }
        notificationManager = requireNotNull(context.getSystemService(NotificationManager::class.java))
        notifications = SmsNotificationManager(context)
        notifications.createNotificationChannel()
        // 앱의 기존 삭제 기록은 그대로 두고 이번 테스트의 tombstone만 별도 저장소에 쓴다.
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
            if (::database.isInitialized && !manualFixtureCreated) {
                notificationManager.activeNotifications
                    .filter { (it.tag to it.id) in ownedNotifications }
                    .forEach { active ->
                        active.notification.contentIntent?.let { ownedPendingIntents += it }
                        active.notification.actions?.forEach { action ->
                            action.actionIntent?.let { ownedPendingIntents += it }
                        }
                    }
                ownedNotifications.forEach { (tag, id) -> notificationManager.cancel(tag, id) }
                ownedPendingIntents.forEach(PendingIntent::cancel)
                expenseIds.forEach { database.expenseDao().deleteById(it) }
                incomeIds.forEach { database.incomeDao().deleteById(it) }
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
    fun expenseDeletePreservesSameLongIdIncomeAndOtherRowsAndNotifications() = runBlocking<Unit>(Dispatchers.IO) {
        assertDeletesOnlySelectedType(deleteIncome = false)
    }

    @Test
    fun incomeDeletePreservesSameLongIdExpenseAndOtherRowsAndNotifications() = runBlocking<Unit>(Dispatchers.IO) {
        assertDeletesOnlySelectedType(deleteIncome = true)
    }

    @Test
    fun expenseExclusionPreservesLatestFieldsAndOtherTargetsAndNeverTogglesBack() = runBlocking<Unit>(Dispatchers.IO) {
        val id = unusedSharedId()
        assertTrue(id > Int.MAX_VALUE)
        val expense = expense("Exclude target", id)
        val income = income("Exclude same ID income", id)
        val otherExpense = expense(expense.storeName)
        val otherIncome = income("Exclude untouched income")
        show(expense)
        show(income)
        show(otherExpense)
        show(otherIncome)
        val tag = "expense:$id"
        val selected = awaitNotification(tag).notification
        val pending = excludeAction(selected.actions)
        ownedNotifications += tag to 41
        notificationManager.notify(tag, 41, selected)
        val expectedNotifications = awaitOwnedNotificationKeys() - (tag to 0)
        // 알림이 생성된 뒤 편집된 내용도 제외 상태 외에는 그대로 보존해야 한다.
        val latest = expense.copy(
            amount = 24680, category = "교통", memo = "Updated after notification",
            isFixed = true, senderAddress = "QA-EXCLUDE", createdAt = 123L
        )
        database.expenseDao().update(latest)
        val expected = latest.copy(isExcludedFromStats = true)
        val totalBefore = requireNotNull(database.expenseDao()
            .getTotalExpenseByDateRange(latest.dateTime, latest.dateTime))

        // 제외 액션에 삭제 액션/다른 ID를 fill-in해도 원래 지출의 제외만 수행한다.
        sendAndWait(pending, deleteIntent("expense", otherExpense.id))
        awaitAbsent(tag)
        assertEquals(expected, database.expenseDao().getExpenseById(id))
        assertEquals(income, database.incomeDao().getIncomeById(id))
        assertEquals(otherExpense, database.expenseDao().getExpenseById(otherExpense.id))
        assertEquals(otherIncome, database.incomeDao().getIncomeById(otherIncome.id))
        assertEquals(expectedNotifications, activeOwnedNotificationKeys())
        assertEquals(totalBefore - latest.amount, database.expenseDao()
            .getTotalExpenseByDateRange(latest.dateTime, latest.dateTime) ?: 0)
        listOf(expense.smsId, requireNotNull(income.smsId), otherExpense.smsId, requireNotNull(otherIncome.smsId))
            .forEach { assertFalse(DeletedSmsTracker.isDeleted(it)) }

        show(expected)
        awaitNotification(tag)
        sendAndWait(pending)
        awaitAbsent(tag)
        assertEquals(expected, database.expenseDao().getExpenseById(id))
        assertEquals(income, database.incomeDao().getIncomeById(id))
        assertEquals(otherExpense, database.expenseDao().getExpenseById(otherExpense.id))
        assertEquals(otherIncome, database.incomeDao().getIncomeById(otherIncome.id))
        assertEquals(expectedNotifications, activeOwnedNotificationKeys())
        assertEquals(totalBefore - latest.amount, database.expenseDao()
            .getTotalExpenseByDateRange(latest.dateTime, latest.dateTime) ?: 0)
        listOf(expense.smsId, requireNotNull(income.smsId), otherExpense.smsId, requireNotNull(otherIncome.smsId))
            .forEach { assertFalse(DeletedSmsTracker.isDeleted(it)) }
    }

    @Test
    fun missingExpenseExclusionOnlyDismissesItsStaleNotification() = runBlocking<Unit>(Dispatchers.IO) {
        val id = unusedSharedId()
        val expense = expense("Missing exclude expense", id)
        val income = income("Missing exclude same ID income", id)
        val otherExpense = expense("Missing exclude untouched")
        show(expense)
        show(income)
        show(otherExpense)
        val tag = "expense:$id"
        val selected = awaitNotification(tag).notification
        val pending = excludeAction(selected.actions)
        ownedNotifications += tag to 41
        notificationManager.notify(tag, 41, selected)
        val expectedNotifications = awaitOwnedNotificationKeys() - (tag to 0)

        database.expenseDao().deleteById(id)
        sendAndWait(pending)
        awaitAbsent(tag)
        assertNull(database.expenseDao().getExpenseById(id))
        assertEquals(income, database.incomeDao().getIncomeById(id))
        assertEquals(otherExpense, database.expenseDao().getExpenseById(otherExpense.id))
        assertEquals(expectedNotifications, activeOwnedNotificationKeys())
        listOf(expense.smsId, requireNotNull(income.smsId), otherExpense.smsId)
            .forEach { assertFalse(DeletedSmsTracker.isDeleted(it)) }
    }

    @Test
    fun notificationActionsRespectTransactionTypeAndKeepOriginalEditContentIntents() = runBlocking<Unit>(Dispatchers.IO) {
        val id = unusedSharedId()
        val expense = expense("Delete action expense", id)
        val income = income("Delete action income", id)
        show(expense)
        show(income)
        val expenseNotification = awaitNotification("expense:$id").notification
        val incomeNotification = awaitNotification("income:$id").notification
        val expenseActions = requireNotNull(expenseNotification.actions)
        val incomeActions = requireNotNull(incomeNotification.actions)
        assertEquals(listOf(context.getString(R.string.transaction_edit_exclude_from_stats), context.getString(R.string.common_delete)),
            expenseActions.map { it.title.toString() })
        assertEquals(listOf(context.getString(R.string.common_delete)), incomeActions.map { it.title.toString() })
        val expenseDelete = deleteAction(expenseActions)
        val incomeDelete = deleteAction(incomeActions)
        val expenseExclude = excludeAction(expenseActions)

        assertNotNull(expenseNotification.contentIntent)
        assertNotNull(incomeNotification.contentIntent)
        assertNull(expenseNotification.deleteIntent)
        assertNull(incomeNotification.deleteIntent)
        assertEquals(TransactionNotificationIntents.expense(context, id), expenseNotification.contentIntent)
        assertEquals(TransactionNotificationIntents.income(context, id), incomeNotification.contentIntent)
        assertEquals(TransactionNotificationIntents.deleteExpense(context, id), expenseDelete)
        assertEquals(TransactionNotificationIntents.deleteIncome(context, id), incomeDelete)
        assertEquals(TransactionNotificationIntents.excludeExpense(context, id), expenseExclude)
        assertNotEquals(expenseExclude, expenseDelete)
        assertNotEquals(expenseExclude, expenseNotification.contentIntent)
        assertNotEquals(expenseDelete, incomeDelete)
        assertNotEquals(expenseNotification.contentIntent, expenseDelete)
        assertNotEquals(incomeNotification.contentIntent, incomeDelete)
        assertTrue(expenseNotification.contentIntent.isActivity)
        assertTrue(incomeNotification.contentIntent.isActivity)
        assertEquals(expense, database.expenseDao().getExpenseById(id))
        assertEquals(income, database.incomeDao().getIncomeById(id))
    }

    @Test
    fun missingTargetsOnlyDismissTheirStaleNotificationsWithoutMarkingOtherSms() = runBlocking<Unit>(Dispatchers.IO) {
        val id = unusedSharedId()
        val expense = expense("Stale expense", id)
        val income = income("Stale income", id)
        val otherExpense = expense("Stale untouched expense")
        val otherIncome = income("Stale untouched income")
        show(expense)
        show(income)
        show(otherExpense)
        show(otherIncome)
        val expensePending = deleteAction(awaitNotification("expense:$id").notification.actions)
        val incomePending = deleteAction(awaitNotification("income:$id").notification.actions)
        val before = awaitOwnedNotificationKeys()

        database.expenseDao().deleteById(id)
        sendAndWait(expensePending)
        awaitAbsent("expense:$id")
        assertEquals(income, database.incomeDao().getIncomeById(id))
        assertEquals(before - ("expense:$id" to 0), activeOwnedNotificationKeys())
        assertFalse(DeletedSmsTracker.isDeleted(expense.smsId))
        assertFalse(DeletedSmsTracker.isDeleted(requireNotNull(income.smsId)))

        database.incomeDao().deleteById(id)
        sendAndWait(incomePending)
        awaitAbsent("income:$id")
        assertEquals(before - setOf("expense:$id" to 0, "income:$id" to 0), activeOwnedNotificationKeys())
        assertEquals(otherExpense, database.expenseDao().getExpenseById(otherExpense.id))
        assertEquals(otherIncome, database.incomeDao().getIncomeById(otherIncome.id))
        assertFalse(DeletedSmsTracker.isDeleted(requireNotNull(income.smsId)))
        assertFalse(DeletedSmsTracker.isDeleted(otherExpense.smsId))
        assertFalse(DeletedSmsTracker.isDeleted(requireNotNull(otherIncome.smsId)))
    }

    @Test
    @Suppress("DEPRECATION")
    fun actionReceiverIsEnabledAndNotExportedOrImplicitlyExposed() {
        val component = ComponentName(context, TransactionNotificationActionReceiver::class.java)
        val receiver = context.packageManager.getReceiverInfo(component, 0)
        assertTrue(receiver.enabled)
        assertFalse(receiver.exported)
        assertEquals(context.applicationInfo.uid, receiver.applicationInfo.uid)
        listOf(DELETE_ACTION, EXCLUDE_ACTION).forEach { action ->
            val implicit = Intent(action).setPackage(context.packageName)
            assertTrue(context.packageManager.queryBroadcastReceivers(implicit, 0)
                .none { it.activityInfo.name == component.className })
        }
    }

    @Test
    fun deleteTargetKeepsTypedLongIdsAndRejectsMalformedIntents() {
        val id = (1L shl 33) + 17
        assertEquals(TransactionTarget.Expense(id), TransactionNotificationIntents.deleteTarget(deleteIntent("expense", id)))
        assertEquals(TransactionTarget.Income(id), TransactionNotificationIntents.deleteTarget(deleteIntent("income", id)))
        assertEquals(TransactionTarget.Expense(Long.MAX_VALUE),
            TransactionNotificationIntents.deleteTarget(deleteIntent("expense", Long.MAX_VALUE)))
        assertNull(TransactionNotificationIntents.deleteTarget(Intent()))
        assertNull(TransactionNotificationIntents.deleteTarget(Intent(DELETE_ACTION)))
        assertNull(TransactionNotificationIntents.deleteTarget(deleteIntent("expense", id).setAction("wrong.action")))
        assertNull(TransactionNotificationIntents.deleteTarget(deleteIntent("expense", id).setAction(null)))
        listOf(
            "https://transaction/expense/$id/delete",
            "moneytalk://other/expense/$id/delete",
            "moneytalk://transaction/transfer/$id/delete",
            "moneytalk://transaction/expense/$id",
            "moneytalk://transaction/expense/$id/edit",
            "moneytalk://transaction/expense/$id/delete/extra",
            "moneytalk://transaction/expense//delete",
            "moneytalk://transaction/expense/0/delete",
            "moneytalk://transaction/income/-1/delete",
            "moneytalk://transaction/expense/not-a-number/delete",
            "moneytalk://transaction/income/9223372036854775808/delete"
        ).forEach { uri ->
            assertNull(uri, TransactionNotificationIntents.deleteTarget(Intent(DELETE_ACTION, Uri.parse(uri))))
        }
        listOf(0L, -1L, Long.MIN_VALUE).forEach { invalidId ->
            assertTrue(runCatching { TransactionNotificationIntents.deleteExpense(context, invalidId) }
                .exceptionOrNull() is IllegalArgumentException)
            assertTrue(runCatching { TransactionNotificationIntents.deleteIncome(context, invalidId) }
                .exceptionOrNull() is IllegalArgumentException)
        }
    }

    @Test
    fun excludeTargetOnlyAcceptsExpenseLongIdsAndRejectsDeleteOrMalformedIntents() {
        val id = (1L shl 33) + 17
        assertEquals(TransactionTarget.Expense(id), TransactionNotificationIntents.excludeTarget(excludeIntent(id)))
        assertEquals(TransactionTarget.Expense(Long.MAX_VALUE),
            TransactionNotificationIntents.excludeTarget(excludeIntent(Long.MAX_VALUE)))
        assertNull(TransactionNotificationIntents.excludeTarget(Intent()))
        assertNull(TransactionNotificationIntents.excludeTarget(Intent(EXCLUDE_ACTION)))
        assertNull(TransactionNotificationIntents.excludeTarget(excludeIntent(id).setAction("wrong.action")))
        assertNull(TransactionNotificationIntents.excludeTarget(excludeIntent(id).setAction(null)))
        assertNull(TransactionNotificationIntents.excludeTarget(deleteIntent("expense", id)))
        assertNull(TransactionNotificationIntents.deleteTarget(excludeIntent(id)))
        listOf(
            "https://transaction/expense/$id/exclude-from-stats",
            "moneytalk://other/expense/$id/exclude-from-stats",
            "moneytalk://transaction/income/$id/exclude-from-stats",
            "moneytalk://transaction/transfer/$id/exclude-from-stats",
            "moneytalk://transaction/expense/$id",
            "moneytalk://transaction/expense/$id/delete",
            "moneytalk://transaction/expense/$id/exclude-from-stats/extra",
            "moneytalk://transaction/expense//exclude-from-stats",
            "moneytalk://transaction/expense/0/exclude-from-stats",
            "moneytalk://transaction/expense/-1/exclude-from-stats",
            "moneytalk://transaction/expense/not-a-number/exclude-from-stats",
            "moneytalk://transaction/expense/9223372036854775808/exclude-from-stats"
        ).forEach { uri ->
            assertNull(uri, TransactionNotificationIntents.excludeTarget(Intent(EXCLUDE_ACTION, Uri.parse(uri))))
        }
        listOf(0L, -1L, Long.MIN_VALUE).forEach { invalidId ->
            assertTrue(runCatching { TransactionNotificationIntents.excludeExpense(context, invalidId) }
                .exceptionOrNull() is IllegalArgumentException)
        }
    }

    /** -e notification_action_fixture true + 이 메서드를 지정한 경우에만 shade 확인용 합성 거래를 남긴다. */
    @Test
    fun manualFixtureLeavesExpenseAndIncomeActionsForShadeReview() = runBlocking<Unit>(Dispatchers.IO) {
        assumeTrue(InstrumentationRegistry.getArguments().getString("notification_action_fixture") == "true")
        val id = unusedSharedId()
        val expense = expense("통계 제외 확인 지출", id)
        val income = income("알림 삭제 확인 수입", id)
        val deleteExpense = expense("알림 삭제 확인 지출")
        show(expense)
        show(income)
        show(deleteExpense)
        excludeAction(awaitNotification("expense:$id").notification.actions)
        deleteAction(awaitNotification("expense:${deleteExpense.id}").notification.actions)
        deleteAction(awaitNotification("income:$id").notification.actions)
        manualFixtureCreated = true
        instrumentation.sendStatus(0, Bundle().apply {
            putString("stream", "\nNotification action fixture: expense:$id (${expense.storeName}), " +
                "expense:${deleteExpense.id} (${deleteExpense.storeName}), income:$id (${income.source})\n")
        })
    }

    private suspend fun assertDeletesOnlySelectedType(deleteIncome: Boolean) {
        val id = unusedSharedId()
        assertTrue(id > Int.MAX_VALUE)
        val expense = expense("Same ID expense", id)
        val income = income("Same ID income", id)
        val otherExpense = expense("Untouched expense")
        val otherIncome = income("Untouched income")
        show(expense)
        show(income)
        show(otherExpense)
        show(otherIncome)
        val type = if (deleteIncome) "income" else "expense"
        val tag = "$type:$id"
        val selected = awaitNotification(tag).notification
        val pending = deleteAction(selected.actions)
        // 같은 tag여도 id=0 이외의 알림을 함께 취소하지 않는다.
        ownedNotifications += tag to 41
        notificationManager.notify(tag, 41, selected)
        val before = awaitOwnedNotificationKeys()
        val expected = before - (tag to 0)
        // Immutable 원래 대상은 send 쪽의 fill-in URI로 다른 거래로 바꿀 수 없다.
        val fillIn = deleteIntent(if (deleteIncome) "expense" else "income", id)
        sendAndWait(pending, fillIn)
        awaitAbsent(tag)
        assertEquals(expected, activeOwnedNotificationKeys())
        assertEquals(otherExpense, database.expenseDao().getExpenseById(otherExpense.id))
        assertEquals(otherIncome, database.incomeDao().getIncomeById(otherIncome.id))
        if (deleteIncome) {
            assertNull(database.incomeDao().getIncomeById(id))
            assertEquals(expense, database.expenseDao().getExpenseById(id))
        } else {
            assertNull(database.expenseDao().getExpenseById(id))
            assertEquals(income, database.incomeDao().getIncomeById(id))
        }
        assertEquals(!deleteIncome, DeletedSmsTracker.isDeleted(expense.smsId))
        assertEquals(deleteIncome, DeletedSmsTracker.isDeleted(requireNotNull(income.smsId)))
        assertFalse(DeletedSmsTracker.isDeleted(otherExpense.smsId))
        assertFalse(DeletedSmsTracker.isDeleted(requireNotNull(otherIncome.smsId)))

        // 이미 처리한 PendingIntent를 다시 보내도 재삽입·다른 알림 취소가 없어야 한다.
        if (deleteIncome) show(income) else show(expense)
        awaitNotification(tag)
        sendAndWait(pending)
        awaitAbsent(tag)
        assertEquals(expected, activeOwnedNotificationKeys())
        assertEquals(otherExpense, database.expenseDao().getExpenseById(otherExpense.id))
        assertEquals(otherIncome, database.incomeDao().getIncomeById(otherIncome.id))
        assertEquals(if (deleteIncome) expense else null, database.expenseDao().getExpenseById(id))
        assertEquals(if (deleteIncome) null else income, database.incomeDao().getIncomeById(id))
        assertEquals(!deleteIncome, DeletedSmsTracker.isDeleted(expense.smsId))
        assertEquals(deleteIncome, DeletedSmsTracker.isDeleted(requireNotNull(income.smsId)))
    }

    private fun deleteAction(actions: Array<android.app.Notification.Action>?): PendingIntent {
        val action = requireNotNull(actions).single { it.title.toString() == context.getString(R.string.common_delete) }
        if (Build.VERSION.SDK_INT >= 28) {
            assertEquals(android.app.Notification.Action.SEMANTIC_ACTION_DELETE, action.semanticAction)
        }
        return checkedActionPendingIntent(action)
    }

    private fun excludeAction(actions: Array<android.app.Notification.Action>?): PendingIntent {
        val action = requireNotNull(actions).single {
            it.title.toString() == context.getString(R.string.transaction_edit_exclude_from_stats)
        }
        return checkedActionPendingIntent(action)
    }

    private fun checkedActionPendingIntent(action: android.app.Notification.Action): PendingIntent {
        if (Build.VERSION.SDK_INT >= 31) assertTrue(action.isAuthenticationRequired)
        val pending = requireNotNull(action.actionIntent)
        assertEquals(context.packageName, pending.creatorPackage)
        if (Build.VERSION.SDK_INT >= 26) assertTrue(pending.isBroadcast)
        if (Build.VERSION.SDK_INT >= 31) assertTrue(pending.isImmutable)
        ownedPendingIntents += pending
        return pending
    }

    private fun sendAndWait(pending: PendingIntent, fillIn: Intent? = null) {
        val finished = CountDownLatch(1)
        pending.send(context, 0, fillIn, { _, _, _, _, _ -> finished.countDown() }, Handler(Looper.getMainLooper()))
        assertTrue("Notification action broadcast did not finish", finished.await(10, TimeUnit.SECONDS))
    }

    private suspend fun awaitNotification(tag: String): StatusBarNotification = withTimeout(10_000) {
        var found = notificationManager.activeNotifications.firstOrNull { it.tag == tag && it.id == 0 }
        while (found == null) {
            delay(25)
            found = notificationManager.activeNotifications.firstOrNull { it.tag == tag && it.id == 0 }
        }
        found.notification.contentIntent?.let { ownedPendingIntents += it }
        found
    }

    private suspend fun awaitAbsent(tag: String) = withTimeout(10_000) {
        while (notificationManager.activeNotifications.any { it.tag == tag && it.id == 0 }) delay(25)
    }

    private suspend fun awaitOwnedNotificationKeys(): Set<Pair<String, Int>> = withTimeout(10_000) {
        while (!activeOwnedNotificationKeys().containsAll(ownedNotifications)) delay(25)
        activeOwnedNotificationKeys()
    }

    private fun activeOwnedNotificationKeys(): Set<Pair<String, Int>> = notificationManager.activeNotifications
        .mapNotNull { notification -> notification.tag?.let { it to notification.id } }
        .filter { it in ownedNotifications }.toSet()

    private fun show(expense: ExpenseEntity) {
        ownedNotifications += "expense:${expense.id}" to 0
        notifications.showExpenseNotification(expense.id, expense.amount, expense.storeName)
    }

    private fun show(income: IncomeEntity) {
        ownedNotifications += "income:${income.id}" to 0
        notifications.showIncomeNotification(income.id, income.amount, income.source, income.type)
    }

    private suspend fun unusedSharedId(): Long {
        var id = maxOf(System.currentTimeMillis(), 1L shl 33)
        while (database.expenseDao().getExpenseById(id) != null || database.incomeDao().getIncomeById(id) != null) id++
        return id
    }

    private suspend fun expense(title: String, id: Long = 0): ExpenseEntity {
        val smsId = "notification-action-qa-${UUID.randomUUID()}"
        val row = ExpenseEntity(
            id = id, amount = 12340, storeName = title, category = "식비", cardName = "QA카드",
            dateTime = System.currentTimeMillis(), originalSms = "QA synthetic $smsId", smsId = smsId
        )
        val result = database.expenseDao().insertIngested(row) as ExpenseIngestionResult.Inserted
        expenseIds += result.expenseId
        return row.copy(id = result.expenseId)
    }

    private suspend fun income(title: String, id: Long = 0): IncomeEntity {
        val row = IncomeEntity(
            id = id, amount = 56780, type = "입금", source = title, description = title, isRecurring = false,
            dateTime = System.currentTimeMillis(), smsId = "notification-action-qa-${UUID.randomUUID()}"
        )
        val savedId = database.incomeDao().insert(row)
        incomeIds += savedId
        return row.copy(id = savedId)
    }

    private fun deleteIntent(type: String, id: Long) = Intent(
        DELETE_ACTION, Uri.parse("moneytalk://transaction/$type/$id/delete")
    )

    private fun excludeIntent(id: Long) = Intent(
        EXCLUDE_ACTION, Uri.parse("moneytalk://transaction/expense/$id/exclude-from-stats")
    )

    private companion object {
        const val DELETE_ACTION = "com.sanha.moneytalk.action.DELETE_TRANSACTION_NOTIFICATION"
        const val EXCLUDE_ACTION = "com.sanha.moneytalk.action.EXCLUDE_TRANSACTION_NOTIFICATION_FROM_STATS"
    }
}
