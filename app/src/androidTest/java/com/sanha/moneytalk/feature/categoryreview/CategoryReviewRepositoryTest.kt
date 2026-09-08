package com.sanha.moneytalk.feature.categoryreview

import android.os.Build
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sanha.moneytalk.core.database.AppDatabase
import com.sanha.moneytalk.core.database.OwnedCardRepository
import com.sanha.moneytalk.core.database.SmsExclusionRepository
import com.sanha.moneytalk.core.database.dao.ExpenseDao
import com.sanha.moneytalk.core.database.entity.ExpenseEntity
import com.sanha.moneytalk.core.util.DataRefreshEvent
import com.sanha.moneytalk.feature.categoryreview.data.CategoryReviewRepository
import com.sanha.moneytalk.feature.categoryreview.ui.CategoryReviewUiState
import com.sanha.moneytalk.feature.categoryreview.ui.CategoryReviewViewModel
import com.sanha.moneytalk.feature.home.data.ExpenseRepository
import java.lang.reflect.Proxy
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CategoryReviewRepositoryTest {
    private lateinit var database: AppDatabase
    private lateinit var expenses: ExpenseRepository
    private lateinit var cards: OwnedCardRepository
    private lateinit var exclusions: SmsExclusionRepository
    private lateinit var repository: CategoryReviewRepository
    private lateinit var viewModel: CategoryReviewViewModel
    private val events = DataRefreshEvent()
    private val store = ViewModelStore()

    @Before
    fun setUp() {
        check(Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("sdk_gphone")) {
            "Category review fixtures must run on a disposable emulator"
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        expenses = ExpenseRepository(database.expenseDao())
        cards = OwnedCardRepository(database.ownedCardDao())
        exclusions = SmsExclusionRepository(database.smsExclusionKeywordDao())
        repository = CategoryReviewRepository(expenses, cards, exclusions, events)
    }

    @After
    fun tearDown() {
        if (::viewModel.isInitialized) {
            var job: Job? = null
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                job = viewModel.viewModelScope.coroutineContext[Job]
                store.clear()
            }
            runBlocking { withTimeout(5_000) { job?.join() } }
        }
        if (::database.isInitialized) database.close()
    }

    @Test
    fun editsAndSameStoreCategoryUpdatesRemoveOnlyResolvedItemsWithoutManualReload() = runBlocking(Dispatchers.IO) {
        expenses.insertAll(listOf(
            expense(1).copy(storeName = "같은 거래처"),
            expense(2).copy(storeName = "같은 거래처"),
            expense(3),
            expense(4).copy(category = "기타")
        ))
        startViewModel()
        assertEquals(setOf(1L, 2L, 3L), awaitCount(3).ids())
        assertEquals(3, repository.observeExpenses().first().size)

        expenses.updateCategoryByStoreName("같은 거래처", "식비")
        assertEquals(setOf(3L), awaitCount(1).ids())
        assertEquals(listOf(3L), repository.observeExpenses().first().map { it.id })

        expenses.update(expense(3).copy(category = "카페/간식"))
        assertTrue(awaitCount(0).days.isEmpty())
        assertEquals(4, expenses.getExpenseCount())
    }

    @Test
    fun visibilityChangesAndReturnRefreshKeepCountAndListInSync() = runBlocking(Dispatchers.IO) {
        expenses.insertAll(listOf(
            expense(1).copy(cardName = "숨긴 카드"),
            expense(2).copy(originalSms = "제외할 문자"),
            expense(3).copy(amount = 0, isExcludedFromStats = true)
        ))
        startViewModel()
        awaitCount(3)
        cards.addManualCard("숨긴 카드", isOwned = false)
        assertEquals(setOf(2L, 3L), awaitCount(2).ids())
        exclusions.addKeyword("제외할")
        events.emitSuspend(DataRefreshEvent.RefreshType.CATEGORY_UPDATED)
        assertEquals(setOf(3L), awaitCount(1).ids())
        assertEquals(1, repository.observeExpenses().first().size)

        exclusions.removeKeyword("제외할")
        withContext(Dispatchers.Main) { viewModel.refresh() }
        assertEquals(setOf(2L, 3L), awaitCount(2).ids())
    }

    @Test
    fun failedLoadHasExplicitErrorAndRetryCanRecover() = runBlocking(Dispatchers.IO) {
        val releaseFailure = CompletableDeferred<Unit>()
        val failOnce = AtomicBoolean(true)
        val dao = Proxy.newProxyInstance(
            ExpenseDao::class.java.classLoader,
            arrayOf(ExpenseDao::class.java)
        ) { _, method, args ->
            if (method.name == "getExpensesByCategory") {
                flow {
                    if (failOnce.getAndSet(false)) {
                        releaseFailure.await()
                        throw IllegalStateException("Synthetic read failure")
                    }
                    emitAll(database.expenseDao().getExpensesByCategory("미분류"))
                }
            } else {
                method.invoke(database.expenseDao(), *(args ?: emptyArray()))
            }
        } as ExpenseDao
        repository = CategoryReviewRepository(ExpenseRepository(dao), cards, exclusions, events)
        startViewModel()
        assertEquals(CategoryReviewUiState.Loading, viewModel.uiState.value)
        releaseFailure.complete(Unit)
        withTimeout(5_000) { viewModel.uiState.first { it is CategoryReviewUiState.Error } }
        expenses.insert(expense(1))
        withContext(Dispatchers.Main) { viewModel.refresh() }
        assertEquals(setOf(1L), awaitCount(1).ids())
    }

    private suspend fun startViewModel() = withContext(Dispatchers.Main) {
        viewModel = CategoryReviewViewModel(repository)
        store.put("review", viewModel)
    }

    private suspend fun awaitCount(count: Int): CategoryReviewUiState.Content = withTimeout(5_000) {
        viewModel.uiState.filterIsInstance<CategoryReviewUiState.Content>().first { it.count == count }
    }

    private fun CategoryReviewUiState.Content.ids(): Set<Long> = days.flatMap { it.expenses }.map { it.id }.toSet()

    private fun expense(id: Long) = ExpenseEntity(
        id = id,
        amount = 1_000,
        storeName = "합성 거래 $id",
        category = "미분류",
        cardName = "표시 카드",
        dateTime = 1_783_000_000_000L + id,
        originalSms = "합성 원문",
        smsId = "category-review-$id"
    )
}
