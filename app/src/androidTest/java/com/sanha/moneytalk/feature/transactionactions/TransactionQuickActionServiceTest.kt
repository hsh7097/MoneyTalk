package com.sanha.moneytalk.feature.transactionactions

import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sanha.moneytalk.core.database.AppDatabase
import com.sanha.moneytalk.core.database.CustomCategoryRepository
import com.sanha.moneytalk.core.database.dao.ExpenseDao
import com.sanha.moneytalk.core.database.entity.ExpenseEntity
import com.sanha.moneytalk.core.database.entity.IncomeEntity
import com.sanha.moneytalk.core.model.Category
import com.sanha.moneytalk.core.model.CategoryProvider
import com.sanha.moneytalk.core.model.CategoryType
import com.sanha.moneytalk.core.sms.DeletedSmsTracker
import com.sanha.moneytalk.core.util.DataRefreshEvent
import com.sanha.moneytalk.feature.home.data.ExpenseRepository
import com.sanha.moneytalk.feature.home.data.IncomeRepository
import com.sanha.moneytalk.feature.transactionactions.data.TransactionQuickActionService
import com.sanha.moneytalk.feature.transactionactions.model.TransactionQuickPatch
import com.sanha.moneytalk.feature.transactionactions.model.TransactionQuickUpdateResult
import com.sanha.moneytalk.feature.transactionactions.model.TransactionTarget
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import java.util.Calendar
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TransactionQuickActionServiceTest {
    private lateinit var database: AppDatabase
    private lateinit var expenses: ExpenseRepository
    private lateinit var incomes: IncomeRepository
    private lateinit var customCategories: CustomCategoryRepository
    private lateinit var categoryProvider: CategoryProvider
    private lateinit var refreshEvent: DataRefreshEvent
    private lateinit var service: TransactionQuickActionService
    private lateinit var context: Context
    private var trackerInitialized = false
    private val preferenceName = "quick_action_test_${UUID.randomUUID()}"
    private val transactionDate = Calendar.getInstance().apply {
        clear()
        set(2026, Calendar.AUGUST, 28, 13, 27)
    }.timeInMillis

    @Before
    fun setUp() {
        check(Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("sdk_gphone")) {
            "Quick action fixtures must run on a disposable emulator"
        }
        context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        expenses = ExpenseRepository(database.expenseDao())
        incomes = IncomeRepository(database.incomeDao())
        customCategories = CustomCategoryRepository(database.customCategoryDao())
        categoryProvider = CategoryProvider(customCategories)
        refreshEvent = DataRefreshEvent()
        service = createService()
        val trackerContext = object : ContextWrapper(context) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int) =
                super.getSharedPreferences(preferenceName, mode)
        }
        DeletedSmsTracker.init(trackerContext)
        trackerInitialized = true
        DeletedSmsTracker.clear()
    }

    @After
    fun tearDown() {
        if (::database.isInitialized) database.close()
        if (trackerInitialized) {
            DeletedSmsTracker.clear()
            DeletedSmsTracker.init(context)
            context.deleteSharedPreferences(preferenceName)
        }
    }

    @Test
    fun sameNumericIdsRemainDifferentExpenseAndIncomeTargets() = runBlocking<Unit>(Dispatchers.IO) {
        expenses.insert(expense())
        incomes.insert(income())
        val expense = requireNotNull(service.load(TransactionTarget.Expense(1)))
        val income = requireNotNull(service.load(TransactionTarget.Income(1)))
        assertNotEquals(expense.target, income.target)
        assertEquals("상점", expense.title)
        assertEquals(CategoryType.EXPENSE, expense.categoryType)
        assertEquals(false, expense.isExcludedFromStats)
        assertEquals("급여 설명", income.title)
        assertEquals(CategoryType.INCOME, income.categoryType)
        assertNull(income.isExcludedFromStats)
    }

    @Test
    fun expensePatchUsesLatestRowAndPreservesMetadataAndOtherTransactions() = runBlocking<Unit>(Dispatchers.IO) {
        val original = expense()
        val sameStore = expense(id = 2)
        val otherTable = income()
        expenses.insert(original)
        expenses.insert(sameStore)
        incomes.insert(otherTable)
        service.load(TransactionTarget.Expense(1))
        val latest = original.copy(amount = 99_999, memo = "다른 화면에서 수정", cardName = "새 카드", createdAt = 123L)
        expenses.update(latest)

        val result = service.update(
            TransactionTarget.Expense(1),
            TransactionQuickPatch(category = Category.CAFE_SNACK.displayName, isFixed = true, isExcludedFromStats = true)
        ) as TransactionQuickUpdateResult.Updated

        assertTrue(result.changed)
        assertEquals(99_999, result.transaction.amount)
        assertEquals(
            latest.copy(category = Category.CAFE_SNACK.displayName, isFixed = true, isExcludedFromStats = true),
            expenses.getExpenseById(1)
        )
        assertEquals(sameStore, expenses.getExpenseById(2))
        assertEquals(otherTable, incomes.getIncomeById(1))
        assertTrue(database.categoryMappingDao().getAllMappingsOnce().isEmpty())
        assertTrue(database.storeRuleDao().getAllOnce().isEmpty())
    }

    @Test
    fun omittedFieldsRetainChangesMadeAfterSheetLoaded() = runBlocking<Unit>(Dispatchers.IO) {
        val original = expense()
        expenses.insert(original)
        service.load(TransactionTarget.Expense(1))
        val latest = original.copy(category = Category.CAFE_SNACK.displayName, isExcludedFromStats = true)
        expenses.update(latest)
        service.update(TransactionTarget.Expense(1), TransactionQuickPatch(isFixed = true))
        assertEquals(latest.copy(isFixed = true), expenses.getExpenseById(1))
    }

    @Test
    fun incomeCategoryPatchPreservesMetadataAndConfiguredRecurringDay() = runBlocking<Unit>(Dispatchers.IO) {
        customCategories.add("사용자 수입", "S", CategoryType.INCOME)
        val original = income().copy(isRecurring = true, recurringDay = 10)
        incomes.insert(original)
        service.load(TransactionTarget.Income(1))
        val latest = original.copy(amount = 222_222, description = "수정된 설명", memo = "새 메모", senderAddress = "12345")
        incomes.update(latest)

        service.update(TransactionTarget.Income(1), TransactionQuickPatch(category = "사용자 수입"))
        assertEquals(latest.copy(category = "사용자 수입"), incomes.getIncomeById(1))
        assertTrue(database.categoryMappingDao().getAllMappingsOnce().isEmpty())
    }

    @Test
    fun fixedIncomeUsesTransactionDayAndClearsItWhenDisabled() = runBlocking<Unit>(Dispatchers.IO) {
        incomes.insert(income())
        service.update(TransactionTarget.Income(1), TransactionQuickPatch(isFixed = true))
        assertEquals(28, incomes.getIncomeById(1)?.recurringDay)
        assertEquals(true, incomes.getIncomeById(1)?.isRecurring)
        service.update(TransactionTarget.Income(1), TransactionQuickPatch(isFixed = false))
        assertNull(incomes.getIncomeById(1)?.recurringDay)
        assertEquals(false, incomes.getIncomeById(1)?.isRecurring)
    }

    @Test
    fun enablingFixedIncomePreservesValidConfiguredDayAndRepairsInvalidDay() = runBlocking<Unit>(Dispatchers.IO) {
        incomes.insert(income().copy(recurringDay = 12))
        service.update(TransactionTarget.Income(1), TransactionQuickPatch(isFixed = true))
        assertEquals(12, incomes.getIncomeById(1)?.recurringDay)
        incomes.insert(income(id = 2).copy(recurringDay = 40))
        service.update(TransactionTarget.Income(2), TransactionQuickPatch(isFixed = true))
        assertEquals(28, incomes.getIncomeById(2)?.recurringDay)
    }

    @Test
    fun categoriesIncludeOnlyTheirTypeAndDeletedCustomSelectionIsRejected() = runBlocking<Unit>(Dispatchers.IO) {
        val customId = customCategories.add("개인 지출", "E", CategoryType.EXPENSE)
        customCategories.add("개인 수입", "I", CategoryType.INCOME)
        customCategories.add("개인 이체", "T", CategoryType.TRANSFER)
        for (type in CategoryType.entries) {
            val entries = service.categories(type)
            assertTrue(entries.isNotEmpty())
            assertTrue(entries.all { it.categoryType == type })
            assertEquals(1, entries.count { it.isCustom })
        }
        val row = expense()
        expenses.insert(row)
        assertTrue(
            service.update(TransactionTarget.Expense(1), TransactionQuickPatch(category = "개인 지출"))
                is TransactionQuickUpdateResult.Updated
        )
        expenses.update(row)
        customCategories.delete(customId)
        val result = service.update(TransactionTarget.Expense(1), TransactionQuickPatch(category = "개인 지출", isFixed = true))
        assertEquals(TransactionQuickUpdateResult.InvalidCategory, result)
        assertEquals(row, expenses.getExpenseById(1))
    }

    @Test
    fun staleExpenseCategoryCannotBeAppliedAfterTransactionBecomesTransfer() = runBlocking<Unit>(Dispatchers.IO) {
        val original = expense()
        expenses.insert(original)
        service.load(TransactionTarget.Expense(1))
        val transferred = original.copy(transactionType = "TRANSFER", transferDirection = "DEPOSIT", category = Category.TRANSFER_GENERAL.displayName)
        expenses.update(transferred)
        assertEquals(CategoryType.TRANSFER, service.load(TransactionTarget.Expense(1))?.categoryType)
        assertEquals(
            TransactionQuickUpdateResult.InvalidCategory,
            service.update(TransactionTarget.Expense(1), TransactionQuickPatch(category = Category.CAFE_SNACK.displayName, isFixed = true))
        )
        assertEquals(transferred, expenses.getExpenseById(1))
        service.update(TransactionTarget.Expense(1), TransactionQuickPatch(category = Category.TRANSFER_SAVINGS.displayName))
        assertEquals(transferred.copy(category = Category.TRANSFER_SAVINGS.displayName), expenses.getExpenseById(1))
    }

    @Test
    fun incomeRejectsUnsupportedStatsFieldWithoutPartiallySaving() = runBlocking<Unit>(Dispatchers.IO) {
        val original = income()
        incomes.insert(original)
        assertEquals(
            TransactionQuickUpdateResult.UnsupportedField,
            service.update(TransactionTarget.Income(1), TransactionQuickPatch(isFixed = true, isExcludedFromStats = false))
        )
        assertEquals(original, incomes.getIncomeById(1))
    }

    @Test
    fun staleAndInvalidIdsNeverResurrectDeletedRows() = runBlocking<Unit>(Dispatchers.IO) {
        expenses.insert(expense())
        incomes.insert(income())
        service.load(TransactionTarget.Expense(1))
        service.load(TransactionTarget.Income(1))
        expenses.deleteById(1)
        incomes.deleteById(1)
        for (target in listOf(TransactionTarget.Expense(1), TransactionTarget.Income(1), TransactionTarget.Expense(0), TransactionTarget.Income(-1))) {
            assertNull(service.load(target))
            assertEquals(TransactionQuickUpdateResult.Missing, service.update(target, TransactionQuickPatch(isFixed = true)))
            assertFalse(service.delete(target))
        }
        assertEquals(0, expenses.getExpenseCount())
        assertEquals(0, incomes.getIncomeCount())
    }

    @Test
    fun concurrentDuplicateUpdatesAndDeletesEmitOnlyOncePerChange() = runBlocking<Unit>(Dispatchers.IO) {
        expenses.insert(expense())
        val events = mutableListOf<DataRefreshEvent.RefreshType>()
        val collector = launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
            refreshEvent.refreshEvent.collect { events += it }
        }
        try {
            val updates = List(2) {
                async { service.update(TransactionTarget.Expense(1), TransactionQuickPatch(isFixed = true)) }
            }.awaitAll().map { it as TransactionQuickUpdateResult.Updated }
            assertEquals(1, updates.count { it.changed })
            assertEquals(listOf(DataRefreshEvent.RefreshType.TRANSACTION_ADDED), events)
            val deletes = List(2) { async { service.delete(TransactionTarget.Expense(1)) } }.awaitAll()
            assertEquals(1, deletes.count { it })
            assertEquals(2, events.size)
        } finally {
            collector.cancelAndJoin()
        }
    }

    @Test
    fun deleteUsesLatestSmsIdentityAndLeavesOtherTableUntouched() = runBlocking<Unit>(Dispatchers.IO) {
        val original = expense()
        val otherTable = income()
        expenses.insert(original)
        incomes.insert(otherTable)
        service.load(TransactionTarget.Expense(1))
        val latestSmsId = "updated-${UUID.randomUUID()}"
        expenses.update(original.copy(smsId = latestSmsId))

        assertTrue(service.delete(TransactionTarget.Expense(1)))
        assertNull(expenses.getExpenseById(1))
        assertTrue(DeletedSmsTracker.isDeleted(latestSmsId))
        assertFalse(DeletedSmsTracker.isDeleted(original.smsId))
        assertEquals(otherTable, incomes.getIncomeById(1))
        assertFalse(DeletedSmsTracker.isDeleted(requireNotNull(otherTable.smsId)))
        assertTrue(service.delete(TransactionTarget.Income(1)))
        assertTrue(DeletedSmsTracker.isDeleted(requireNotNull(otherTable.smsId)))
    }

    @Test
    fun cancellationDuringMultiFieldUpdateRollsBackAndPropagates() = runBlocking<Unit>(Dispatchers.IO) {
        verifyFailedUpdate(CancellationException("cancel fixture"))
    }

    @Test
    fun databaseFailureDuringMultiFieldUpdateRollsBackAndPropagates() = runBlocking<Unit>(Dispatchers.IO) {
        verifyFailedUpdate(IllegalStateException("write failure fixture"))
    }

    @Test
    fun failedDeleteLeavesRowAndDoesNotMarkSmsDeleted() = runBlocking<Unit>(Dispatchers.IO) {
        val original = expense()
        expenses.insert(original)
        val failure = IllegalStateException("delete failure fixture")
        val failingService = createService(repositoryBeforeCall("deleteById") { throw failure })
        assertEquals(failure, runCatching { failingService.delete(TransactionTarget.Expense(1)) }.exceptionOrNull())
        assertEquals(original, expenses.getExpenseById(1))
        assertFalse(DeletedSmsTracker.isDeleted(original.smsId))
    }

    @Test
    fun cancellationAfterDeleteStartsStillRecordsTombstoneAndPropagates() = runBlocking<Unit>(Dispatchers.IO) {
        val original = expense()
        expenses.insert(original)
        lateinit var deletion: Deferred<Boolean>
        val cancellingService = createService(repositoryBeforeCall("deleteById") { deletion.cancel() })
        deletion = async(start = CoroutineStart.LAZY) { cancellingService.delete(TransactionTarget.Expense(1)) }
        deletion.start()
        val error = runCatching { deletion.await() }.exceptionOrNull()
        deletion.join()
        assertTrue(error is CancellationException)
        assertNull(expenses.getExpenseById(1))
        assertTrue(DeletedSmsTracker.isDeleted(original.smsId))
    }

    private suspend fun verifyFailedUpdate(failure: Exception) {
        val original = expense()
        expenses.insert(original)
        val failingService = createService(repositoryBeforeCall("updateFixedById") { throw failure })
        val error = runCatching {
            failingService.update(TransactionTarget.Expense(1), TransactionQuickPatch(category = Category.CAFE_SNACK.displayName, isFixed = true))
        }.exceptionOrNull()
        assertEquals(failure, error)
        assertEquals(original, expenses.getExpenseById(1))
    }

    private fun repositoryBeforeCall(methodName: String, beforeCall: () -> Unit): ExpenseRepository {
        val delegate = database.expenseDao()
        val dao = Proxy.newProxyInstance(ExpenseDao::class.java.classLoader, arrayOf(ExpenseDao::class.java)) { _, method, arguments ->
            if (method.name == methodName) beforeCall()
            try {
                method.invoke(delegate, *(arguments ?: emptyArray()))
            } catch (exception: InvocationTargetException) {
                throw exception.targetException
            }
        } as ExpenseDao
        return ExpenseRepository(dao)
    }

    private fun createService(expenseRepository: ExpenseRepository = expenses) = TransactionQuickActionService(
        database, expenseRepository, incomes, categoryProvider, customCategories, refreshEvent
    )

    private fun expense(id: Long = 1) = ExpenseEntity(
        id = id,
        amount = 12_300,
        storeName = "상점",
        category = Category.FOOD.displayName,
        cardName = "카드 1234",
        dateTime = transactionDate,
        originalSms = "합성 결제 원문",
        smsId = "expense-$id-${UUID.randomUUID()}",
        senderAddress = "15881234",
        memo = "메모",
        createdAt = 99L
    )

    private fun income(id: Long = 1) = IncomeEntity(
        id = id,
        smsId = "income-$id-${UUID.randomUUID()}",
        amount = 55_000,
        type = "입금",
        source = "회사",
        description = "급여 설명",
        isRecurring = false,
        dateTime = transactionDate,
        originalSms = "합성 입금 원문",
        senderAddress = "15885678",
        memo = "수입 메모",
        category = Category.INCOME_SALARY.displayName,
        createdAt = 88L
    )
}
