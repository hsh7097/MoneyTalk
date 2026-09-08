package com.sanha.moneytalk.feature.history.ui

import android.content.Context
import android.os.Build
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sanha.moneytalk.core.database.AppDatabase
import com.sanha.moneytalk.core.database.CustomCategoryRepository
import com.sanha.moneytalk.core.database.OwnedCardRepository
import com.sanha.moneytalk.core.database.SmsExclusionRepository
import com.sanha.moneytalk.core.database.dao.ExpenseDao
import com.sanha.moneytalk.core.database.entity.ExpenseEntity
import com.sanha.moneytalk.core.database.entity.IncomeEntity
import com.sanha.moneytalk.core.datastore.SettingsDataStore
import com.sanha.moneytalk.core.firebase.AnalyticsHelper
import com.sanha.moneytalk.core.model.Category
import com.sanha.moneytalk.core.model.CategoryProvider
import com.sanha.moneytalk.core.ui.AppSnackbarBus
import com.sanha.moneytalk.core.ui.component.MonthKey
import com.sanha.moneytalk.core.util.DataRefreshEvent
import com.sanha.moneytalk.feature.home.data.CategoryClassifierService
import com.sanha.moneytalk.feature.home.data.ExpenseRepository
import com.sanha.moneytalk.feature.home.data.IncomeRepository
import java.lang.reflect.Proxy
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HistorySearchViewModelTest {
    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var expenses: ExpenseRepository
    private lateinit var incomes: IncomeRepository
    private lateinit var exclusions: SmsExclusionRepository
    private lateinit var cards: OwnedCardRepository
    private lateinit var refreshEvent: DataRefreshEvent
    private lateinit var viewModel: HistoryViewModel
    private val store = ViewModelStore()

    @Before
    fun setUp() {
        check(Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("sdk_gphone")) {
            "History search fixtures must run on a disposable emulator"
        }
        context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        expenses = ExpenseRepository(database.expenseDao())
        incomes = IncomeRepository(database.incomeDao())
        exclusions = SmsExclusionRepository(database.smsExclusionKeywordDao())
        cards = OwnedCardRepository(database.ownedCardDao())
        refreshEvent = DataRefreshEvent()
    }

    @After
    fun tearDown() {
        if (::viewModel.isInitialized) {
            var scopeJob: Job? = null
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                scopeJob = viewModel.viewModelScope.coroutineContext[Job]
                store.clear()
            }
            runBlocking { withTimeout(5_000) { scopeJob?.join() } }
        }
        if (::database.isInitialized) database.close()
    }

    @Test
    fun incomeOnlySearchFindsDescriptionSourceCategoryAndMemoAcrossAllDates() = runBlocking<Unit>(Dispatchers.IO) {
        val row = income().copy(
            description = "설명검색", source = "출처검색", category = "사용자수입검색", memo = "메모검색",
            dateTime = 946_684_800_000L
        )
        incomes.insert(row)
        startViewModel()
        onMain { applyFilter(showExpenses = false, showTransfers = false) }
        for (query in listOf("설명검색", "출처검색", "사용자수입검색", "메모검색")) {
            val page = search(query)
            assertEquals(listOf(row), page.incomes)
            assertEquals(listOf(1L), page.transactionListItems.filterIsInstance<TransactionListItem.IncomeItem>().map { it.income.id })
            assertEquals(row.amount, page.monthlyIncomeTotal)
        }
    }

    @Test
    fun mixedSearchRetainsBothTablesWhenIdsOverlapAndSortsByExistingPolicy() = runBlocking<Unit>(Dispatchers.IO) {
        expenses.insert(expense())
        incomes.insert(income().copy(amount = 90_000))
        startViewModel()
        onMain { applyFilter(sortOrder = SortOrder.AMOUNT_DESC) }
        val page = search("검색")
        val transactions = page.transactionListItems.filterNot { it is TransactionListItem.Header }
        assertEquals(2, transactions.size)
        assertTrue(transactions.first() is TransactionListItem.IncomeItem)
        assertEquals(1L, transactions.filterIsInstance<TransactionListItem.ExpenseItem>().single().expense.id)
        assertEquals(1L, transactions.filterIsInstance<TransactionListItem.IncomeItem>().single().income.id)
    }

    @Test
    fun activeSearchKeepsQueryWhenIncomeCategoryFixedAndCardFiltersChange() = runBlocking<Unit>(Dispatchers.IO) {
        incomes.insert(income(1).copy(category = "급여", isRecurring = true))
        incomes.insert(income(2).copy(category = "급여", isRecurring = false))
        incomes.insert(income(3).copy(category = "사용자 수입", isRecurring = true))
        incomes.insert(income(4).copy(description = "일치하지 않는 설명", category = "급여", isRecurring = true))
        startViewModel()
        search("검색")
        onMain { applyFilter(incomeCategories = setOf("급여"), fixedFilter = FixedExpenseFilter.FIXED_ONLY) }
        var page = awaitSearchPage("검색")
        assertEquals(listOf(1L), page.incomes.map { it.id })
        onMain { applyFilter(cardNames = setOf("합성 카드")) }
        page = awaitSearchPage("검색")
        assertTrue(page.incomes.isEmpty())
        onMain { applyFilter(showIncomes = false) }
        assertTrue(awaitSearchPage("검색").incomes.isEmpty())
        onMain { applyFilter(showExpenses = false, showTransfers = false) }
        assertEquals(setOf(1L, 2L, 3L), awaitSearchPage("검색").incomes.map { it.id }.toSet())
    }

    @Test
    fun transactionSmsExclusionAndExpenseCardVisibilityMatchNormalListRules() = runBlocking<Unit>(Dispatchers.IO) {
        exclusions.addKeyword("제외원문")
        cards.addManualCard("숨긴 카드", isOwned = false)
        incomes.insert(income(1).copy(originalSms = "제외원문 검색"))
        incomes.insert(income(2))
        expenses.insert(expense(1).copy(cardName = "숨긴 카드"))
        expenses.insert(expense(2))
        expenses.insert(expense(3).copy(originalSms = "제외원문 검색"))
        startViewModel()
        val page = search("검색")
        assertEquals(listOf(2L), page.incomes.map { it.id })
        assertEquals(listOf(2L), page.expenses.map { it.id })
        assertEquals(listOf(2L), page.transactionListItems.filterIsInstance<TransactionListItem.ExpenseItem>().map { it.expense.id })
    }

    @Test
    fun transactionRefreshRerunsSearchInsteadOfReplacingItWithWholeMonth() = runBlocking<Unit>(Dispatchers.IO) {
        incomes.insert(income(1))
        incomes.insert(income(2).copy(description = "다른 거래"))
        startViewModel()
        search("검색")
        incomes.insert(income(3))
        refreshEvent.emit(DataRefreshEvent.RefreshType.TRANSACTION_ADDED)
        val page = withTimeout(5_000) {
            viewModel.uiState.first { it.searchQuery == "검색" && it.currentPage.incomes.map { row -> row.id }.toSet() == setOf(1L, 3L) }
        }.currentPage
        assertEquals(setOf(1L, 3L), page.incomes.map { it.id }.toSet())
    }

    @Test
    fun olderSearchFailureCannotClearNewIncomeSearchResults() = runBlocking<Unit>(Dispatchers.IO) {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        incomes.insert(income().copy(description = "새결과"))
        startViewModel(delayedSearchRepository(entered, release))
        val oldJob = startDelayedSearch()
        try {
            withTimeout(5_000) { entered.await() }
            assertEquals(listOf(1L), search("새결과").incomes.map { it.id })
        } finally {
            release.complete(Unit)
            withTimeout(5_000) { oldJob.join() }
        }
        assertNull(viewModel.uiState.value.errorMessage)
        assertEquals("새결과", viewModel.uiState.value.searchQuery)
        assertEquals(listOf(1L), viewModel.uiState.value.currentPage.incomes.map { it.id })
    }

    @Test
    fun clearingQueryCancelsOldSearchAndRestoresMonth() = runBlocking<Unit>(Dispatchers.IO) {
        verifySearchDismissal(exitMode = false)
    }

    @Test
    fun leavingSearchCancelsOldSearchAndRestoresMonth() = runBlocking<Unit>(Dispatchers.IO) {
        verifySearchDismissal(exitMode = true)
    }

    private suspend fun verifySearchDismissal(exitMode: Boolean) {
        incomes.insert(income())
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        startViewModel(delayedSearchRepository(entered, release))
        val oldJob = startDelayedSearch()
        try {
            withTimeout(5_000) { entered.await() }
            onMain { if (exitMode) viewModel.exitSearchMode() else viewModel.search("") }
            withTimeout(5_000) { viewModel.uiState.first { !it.currentPage.isLoading && it.searchQuery.isEmpty() } }
        } finally {
            release.complete(Unit)
            withTimeout(5_000) { oldJob.join() }
        }
        val state = viewModel.uiState.value
        assertEquals("", state.searchQuery)
        if (exitMode) assertFalse(state.isSearchMode)
        assertNull(state.errorMessage)
        assertEquals(listOf(1L), state.currentPage.incomes.map { it.id })
    }

    private fun delayedSearchRepository(entered: CompletableDeferred<Unit>, release: CompletableDeferred<Unit>): ExpenseRepository {
        val delegate = database.expenseDao()
        val dao = object : ExpenseDao by delegate {
            override suspend fun searchExpenses(query: String): List<ExpenseEntity> {
                if (query == "이전검색") return withContext(NonCancellable) {
                    entered.complete(Unit)
                    release.await()
                    throw IllegalStateException("late search fixture")
                }
                return delegate.searchExpenses(query)
            }
        }
        return ExpenseRepository(dao)
    }

    private suspend fun startDelayedSearch(): Job = withContext(Dispatchers.Main.immediate) {
        val parent = requireNotNull(viewModel.viewModelScope.coroutineContext[Job])
        val previousJobs = parent.children.toSet()
        viewModel.enterSearchMode()
        viewModel.search("이전검색")
        parent.children.single { it !in previousJobs }
    }

    private suspend fun startViewModel(expenseRepository: ExpenseRepository = expenses) {
        val classifier = Proxy.newProxyInstance(
            CategoryClassifierService::class.java.classLoader, arrayOf(CategoryClassifierService::class.java)
        ) { _, method, _ -> throw AssertionError("Search must not classify: ${method.name}") } as CategoryClassifierService
        onMain {
            viewModel = HistoryViewModel(
                expenseRepository, incomes, SettingsDataStore(context), classifier,
                CategoryProvider(CustomCategoryRepository(database.customCategoryDao())), refreshEvent,
                AppSnackbarBus(), exclusions, cards, context, AnalyticsHelper(null)
            )
            store.put("history-search", viewModel)
        }
        withTimeout(5_000) { viewModel.uiState.first { !it.currentPage.isLoading } }
    }

    private fun applyFilter(
        sortOrder: SortOrder = SortOrder.DATE_DESC,
        showExpenses: Boolean = true,
        showIncomes: Boolean = true,
        showTransfers: Boolean = true,
        incomeCategories: Set<String> = emptySet(),
        cardNames: Set<String> = emptySet(),
        fixedFilter: FixedExpenseFilter = FixedExpenseFilter.ALL
    ) = viewModel.applyFilter(
        sortOrder, showExpenses, showIncomes, showTransfers, emptySet(), incomeCategories,
        emptySet(), cardNames, fixedFilter
    )

    private suspend fun search(query: String): HistoryPageData {
        onMain { viewModel.enterSearchMode(); viewModel.search(query) }
        return awaitSearchPage(query)
    }

    private suspend fun awaitSearchPage(query: String): HistoryPageData = withTimeout(5_000) {
        viewModel.uiState.first { it.searchQuery == query && !it.currentPage.isLoading }
    }.currentPage

    private val HistoryUiState.currentPage: HistoryPageData
        get() = pageCache[MonthKey(selectedYear, selectedMonth)] ?: HistoryPageData()

    private suspend fun onMain(action: () -> Unit) = withContext(Dispatchers.Main.immediate) { action() }

    private fun expense(id: Long = 1) = ExpenseEntity(
        id = id, amount = 12_000, storeName = "검색 지출", category = Category.FOOD.displayName,
        cardName = "합성 카드", dateTime = System.currentTimeMillis(), originalSms = "원문", smsId = "search-$id"
    )

    private fun income(id: Long = 1) = IncomeEntity(
        id = id, amount = 50_000, type = "입금", description = "검색 수입", isRecurring = false,
        dateTime = System.currentTimeMillis(), category = Category.INCOME_SALARY.displayName
    )
}
