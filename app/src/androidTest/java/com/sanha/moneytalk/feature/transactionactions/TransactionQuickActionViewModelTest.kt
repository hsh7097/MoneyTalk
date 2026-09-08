package com.sanha.moneytalk.feature.transactionactions

import android.os.Build
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sanha.moneytalk.R
import com.sanha.moneytalk.core.database.AppDatabase
import com.sanha.moneytalk.core.database.CustomCategoryRepository
import com.sanha.moneytalk.core.database.dao.ExpenseDao
import com.sanha.moneytalk.core.database.entity.ExpenseEntity
import com.sanha.moneytalk.core.database.entity.IncomeEntity
import com.sanha.moneytalk.core.model.Category
import com.sanha.moneytalk.core.model.CategoryProvider
import com.sanha.moneytalk.core.util.DataRefreshEvent
import com.sanha.moneytalk.feature.home.data.ExpenseRepository
import com.sanha.moneytalk.feature.home.data.IncomeRepository
import com.sanha.moneytalk.feature.transactionactions.data.TransactionQuickActionService
import com.sanha.moneytalk.feature.transactionactions.model.TransactionTarget
import com.sanha.moneytalk.feature.transactionactions.ui.TransactionQuickActionUiState
import com.sanha.moneytalk.feature.transactionactions.ui.TransactionQuickActionViewModel
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TransactionQuickActionViewModelTest {
    private lateinit var database: AppDatabase
    private lateinit var expenses: ExpenseRepository
    private lateinit var incomes: IncomeRepository
    private lateinit var viewModel: TransactionQuickActionViewModel
    private val store = ViewModelStore()

    @Before
    fun setUp() {
        check(Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("sdk_gphone")) {
            "Quick action ViewModel fixtures must run on a disposable emulator"
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        expenses = ExpenseRepository(database.expenseDao())
        incomes = IncomeRepository(database.incomeDao())
        installViewModel()
    }

    @After
    fun tearDown() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync { store.clear() }
        if (::database.isInitialized) database.close()
    }

    @Test
    fun repeatedSaveAndNavigationDuringSaveApplyOnlyOriginalPatchOnce() = runBlocking<Unit>(Dispatchers.IO) {
        val original = expense()
        expenses.insert(original)
        expenses.insert(expense(2))
        openAndAwait(TransactionTarget.Expense(1))
        val latest = original.copy(amount = 77_777, category = Category.CAFE_SNACK.displayName, memo = "다른 화면 변경")
        expenses.update(latest)
        onMain {
            viewModel.setFixed(true)
            viewModel.save()
            viewModel.save()
            viewModel.dismiss()
            viewModel.open(TransactionTarget.Expense(2))
            assertTrue(viewModel.uiState.value.isSaving)
            assertEquals(TransactionTarget.Expense(1), viewModel.uiState.value.target)
        }
        assertEquals(R.string.quick_transaction_saved, awaitMessage())
        awaitState { it.target == null && !it.isSaving }
        assertEquals(latest.copy(isFixed = true), expenses.getExpenseById(1))
        assertFalse(requireNotNull(expenses.getExpenseById(2)).isFixed)
        assertNoMessage()
    }

    @Test
    fun unchangedSaveDoesNotWriteOrEmitCompletion() = runBlocking<Unit>(Dispatchers.IO) {
        val original = expense()
        expenses.insert(original)
        openAndAwait(TransactionTarget.Expense(1))
        onMain { viewModel.save() }
        assertEquals(original, expenses.getExpenseById(1))
        assertEquals(TransactionTarget.Expense(1), viewModel.uiState.value.target)
        assertNoMessage()
    }

    @Test
    fun invalidCategoryPreservesEditableSheetAndRetryCompletes() = runBlocking<Unit>(Dispatchers.IO) {
        expenses.insert(expense())
        openAndAwait(TransactionTarget.Expense(1))
        onMain {
            viewModel.selectCategory("삭제된 사용자 카테고리")
            viewModel.save()
        }
        val failed = awaitState { it.error == R.string.quick_transaction_invalid_category && !it.isSaving }
        assertEquals(TransactionTarget.Expense(1), failed.target)
        assertTrue(failed.hasChanges)
        assertEquals(Category.FOOD.displayName, expenses.getExpenseById(1)?.category)
        assertNoMessage()
        onMain {
            viewModel.selectCategory(Category.CAFE_SNACK.displayName)
            viewModel.save()
        }
        assertEquals(R.string.quick_transaction_saved, awaitMessage())
        assertEquals(Category.CAFE_SNACK.displayName, expenses.getExpenseById(1)?.category)
    }

    @Test
    fun missingOnOpenAndMissingDuringSaveCloseWithDistinctMessage() = runBlocking<Unit>(Dispatchers.IO) {
        onMain { viewModel.open(TransactionTarget.Income(42)) }
        assertEquals(R.string.quick_transaction_missing, awaitMessage())
        awaitState { it.target == null }
        expenses.insert(expense())
        openAndAwait(TransactionTarget.Expense(1))
        expenses.deleteById(1)
        onMain {
            viewModel.setFixed(true)
            viewModel.save()
        }
        assertEquals(R.string.quick_transaction_missing, awaitMessage())
        assertNull(expenses.getExpenseById(1))
        assertEquals(TransactionQuickActionUiState(), awaitState { it.target == null && !it.isSaving })
    }

    @Test
    fun deleteRequiresConfirmationAndDuplicateClickCompletesOnce() = runBlocking<Unit>(Dispatchers.IO) {
        incomes.insert(income())
        openAndAwait(TransactionTarget.Income(1))
        onMain { viewModel.delete() }
        assertEquals(1, incomes.getIncomeCount())
        assertNoMessage()
        onMain {
            viewModel.confirmDelete(true)
            viewModel.delete()
            viewModel.delete()
            assertTrue(viewModel.uiState.value.isSaving)
        }
        assertEquals(R.string.quick_transaction_deleted, awaitMessage())
        assertEquals(0, incomes.getIncomeCount())
        awaitState { it.target == null && !it.isSaving }
        assertNoMessage()
    }

    @Test
    fun loadFailureShowsRetryAndRetryReadsActualRow() = runBlocking<Unit>(Dispatchers.IO) {
        expenses.insert(expense())
        var shouldFail = true
        val delegate = database.expenseDao()
        val dao = object : ExpenseDao by delegate {
            override suspend fun getExpenseById(id: Long): ExpenseEntity? {
                if (shouldFail) throw IllegalStateException("read fixture")
                return delegate.getExpenseById(id)
            }
        }
        installViewModel(ExpenseRepository(dao))
        onMain { viewModel.open(TransactionTarget.Expense(1)) }
        val state = awaitState { it.error == R.string.quick_transaction_load_failed }
        assertFalse(state.isLoading)
        assertNull(state.transaction)
        shouldFail = false
        openAndAwait(TransactionTarget.Expense(1))
        assertNull(viewModel.uiState.value.error)
        assertNoMessage()
    }

    @Test
    fun saveFailureLeavesSheetOpenAndClearsBusyState() = runBlocking<Unit>(Dispatchers.IO) {
        val original = expense()
        expenses.insert(original)
        val delegate = database.expenseDao()
        val dao = object : ExpenseDao by delegate {
            override suspend fun updateFixedById(expenseId: Long, isFixed: Boolean): Int {
                throw IllegalStateException("write fixture")
            }
        }
        installViewModel(ExpenseRepository(dao))
        openAndAwait(TransactionTarget.Expense(1))
        onMain {
            viewModel.setFixed(true)
            viewModel.save()
        }
        val failed = awaitState { it.error == R.string.quick_transaction_save_failed && !it.isSaving }
        assertEquals(TransactionTarget.Expense(1), failed.target)
        assertTrue(failed.hasChanges)
        assertEquals(original, expenses.getExpenseById(1))
        assertNoMessage()
    }

    @Test
    fun lateFailureFromCancelledLoadCannotOverwriteDifferentTransaction() = runBlocking<Unit>(Dispatchers.IO) {
        verifySupersededLoad(nextTarget = TransactionTarget.Expense(2))
    }

    @Test
    fun lateFailureFromCancelledLoadCannotOverwriteSameTargetRetry() = runBlocking<Unit>(Dispatchers.IO) {
        verifySupersededLoad(nextTarget = TransactionTarget.Expense(1))
    }

    @Test
    fun dismissDuringLoadLeavesCleanClosedStateEvenIfDriverFailsLate() = runBlocking<Unit>(Dispatchers.IO) {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val delegate = database.expenseDao()
        val dao = object : ExpenseDao by delegate {
            override suspend fun getExpenseById(id: Long): ExpenseEntity? = withContext(NonCancellable) {
                entered.complete(Unit)
                release.await()
                throw IllegalStateException("late read fixture")
            }
        }
        installViewModel(ExpenseRepository(dao))
        onMain { viewModel.open(TransactionTarget.Expense(1)) }
        val job = loadJob()
        try {
            withTimeout(5_000) { entered.await() }
            onMain { viewModel.dismiss() }
        } finally {
            release.complete(Unit)
            withTimeout(5_000) { job.join() }
        }
        assertEquals(TransactionQuickActionUiState(), viewModel.uiState.value)
        assertNoMessage()
    }

    private suspend fun verifySupersededLoad(nextTarget: TransactionTarget) {
        expenses.insert(expense())
        expenses.insert(expense(2))
        val firstEntered = CompletableDeferred<Unit>()
        val secondEntered = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val releaseSecond = CompletableDeferred<Unit>()
        val calls = AtomicInteger()
        val delegate = database.expenseDao()
        val dao = object : ExpenseDao by delegate {
            override suspend fun getExpenseById(id: Long): ExpenseEntity? {
                if (calls.incrementAndGet() == 1) return withContext(NonCancellable) {
                    firstEntered.complete(Unit)
                    releaseFirst.await()
                    throw IllegalStateException("old read fixture")
                }
                secondEntered.complete(Unit)
                releaseSecond.await()
                return delegate.getExpenseById(id)
            }
        }
        installViewModel(ExpenseRepository(dao))
        onMain { viewModel.open(TransactionTarget.Expense(1)) }
        val oldJob = loadJob()
        try {
            withTimeout(5_000) { firstEntered.await() }
            onMain { viewModel.open(nextTarget) }
            withTimeout(5_000) { secondEntered.await() }
            releaseFirst.complete(Unit)
            withTimeout(5_000) { oldJob.join() }
            val waiting = viewModel.uiState.value
            assertEquals(nextTarget, waiting.target)
            assertTrue(waiting.isLoading)
            assertNull(waiting.error)
        } finally {
            releaseFirst.complete(Unit)
            releaseSecond.complete(Unit)
            val pendingJobs = withContext(Dispatchers.Main.immediate) {
                requireNotNull(viewModel.viewModelScope.coroutineContext[Job]).children.toList()
            }
            withTimeout(5_000) { pendingJobs.forEach { it.join() } }
        }
        awaitState { it.target == nextTarget && it.transaction != null && !it.isLoading }
        assertNoMessage()
    }

    private fun installViewModel(expenseRepository: ExpenseRepository = expenses) {
        val categories = CustomCategoryRepository(database.customCategoryDao())
        val service = TransactionQuickActionService(
            database, expenseRepository, incomes, CategoryProvider(categories), categories, DataRefreshEvent()
        )
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            store.clear()
            viewModel = TransactionQuickActionViewModel(service)
            store.put("quick-action", viewModel)
        }
    }

    private suspend fun openAndAwait(target: TransactionTarget) {
        onMain { viewModel.open(target) }
        awaitState { it.target == target && it.transaction != null && !it.isLoading }
    }

    private suspend fun loadJob(): Job = withContext(Dispatchers.Main.immediate) {
        requireNotNull(viewModel.viewModelScope.coroutineContext[Job]).children.single()
    }

    private suspend fun onMain(action: () -> Unit) = withContext(Dispatchers.Main.immediate) { action() }

    private suspend fun awaitState(predicate: (TransactionQuickActionUiState) -> Boolean): TransactionQuickActionUiState =
        withTimeout(5_000) { viewModel.uiState.first(predicate) }

    private suspend fun awaitMessage(): Int = withTimeout(5_000) { viewModel.completedMessages.first() }

    private suspend fun assertNoMessage() {
        assertNull(withTimeoutOrNull(100) { viewModel.completedMessages.first() })
    }

    private fun expense(id: Long = 1) = ExpenseEntity(
        id = id, amount = 12_000, storeName = "거래 $id", category = Category.FOOD.displayName,
        cardName = "합성 카드", dateTime = 1_783_332_000_000L, originalSms = "합성 문자", smsId = "vm-fixture-$id"
    )

    private fun income() = IncomeEntity(
        id = 1, amount = 33_000, type = "입금", description = "합성 수입", isRecurring = false,
        dateTime = 1_783_332_000_000L, category = Category.INCOME_SALARY.displayName
    )
}
