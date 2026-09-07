package com.sanha.moneytalk.feature.history.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sanha.moneytalk.core.database.OwnedCardRepository
import com.sanha.moneytalk.core.database.entity.ExpenseEntity
import com.sanha.moneytalk.core.database.entity.IncomeEntity
import com.sanha.moneytalk.core.database.entity.isIncludedInExpenseStats
import com.sanha.moneytalk.core.datastore.SettingsDataStore
import com.sanha.moneytalk.core.firebase.AnalyticsEvent
import com.sanha.moneytalk.core.firebase.AnalyticsHelper
import com.sanha.moneytalk.core.model.Category
import com.sanha.moneytalk.core.model.CategoryProvider
import com.sanha.moneytalk.core.ui.AppSnackbarBus
import com.sanha.moneytalk.core.ui.component.MonthKey
import com.sanha.moneytalk.core.ui.component.MonthPagerUtils
import com.sanha.moneytalk.core.sms.DeletedSmsTracker
import com.sanha.moneytalk.core.util.CardVisibilityFilter
import com.sanha.moneytalk.core.util.DataRefreshEvent
import com.sanha.moneytalk.core.util.DateUtils
import com.sanha.moneytalk.feature.home.data.CategoryClassifierService
import com.sanha.moneytalk.feature.home.data.ExpenseRepository
import com.sanha.moneytalk.feature.home.data.IncomeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * 내역 화면 ViewModel
 *
 * 월별 지출 내역을 조회하고 필터링하는 기능을 제공합니다.
 * 카테고리별, 카드별 필터와 월 이동 기능을 지원합니다.
 *
 * 주요 기능:
 * - 월별 지출 내역 조회
 * - 카테고리/카드별 필터링
 * - 지출 항목 삭제
 * - 카테고리 수동 변경
 * - Pull-to-Refresh 지원
 */
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val expenseRepository: ExpenseRepository,
    private val incomeRepository: IncomeRepository,
    private val settingsDataStore: SettingsDataStore,
    private val categoryClassifierService: CategoryClassifierService,
    private val categoryProvider: CategoryProvider,
    private val dataRefreshEvent: DataRefreshEvent,
    private val snackbarBus: AppSnackbarBus,
    private val smsExclusionRepository: com.sanha.moneytalk.core.database.SmsExclusionRepository,
    private val ownedCardRepository: OwnedCardRepository,
    @ApplicationContext private val context: Context,
    private val analyticsHelper: AnalyticsHelper
) : ViewModel() {

    private val transactionListMapper = HistoryTransactionListMapper(context)

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    /** 페이지별 로드 Job 관리 (월별 독립 취소) */
    private val pageLoadJobs = mutableMapOf<MonthKey, Job>()

    /** 페이지 캐시 최대 허용 범위 (현재 월 ± 이 값) */
    private companion object {
        const val PAGE_CACHE_RANGE = 2
    }

    init {
        loadSettings()
        loadFilterCategories()
        observeDataRefreshEvents()
    }

    // ========== 페이지 캐시 관리 ==========

    /** 특정 월의 페이지 캐시 업데이트 */
    private fun updatePageCache(key: MonthKey, data: HistoryPageData) {
        _uiState.update { state ->
            state.copy(pageCache = state.pageCache + (key to data))
        }
    }

    /** 현재 월 ± PAGE_CACHE_RANGE 밖의 캐시 정리 */
    private fun evictDistantCache(year: Int, month: Int) {
        val currentTotal = year * 12 + month
        _uiState.update { state ->
            val filtered = state.pageCache.filter { (key, _) ->
                val keyTotal = key.year * 12 + key.month
                kotlin.math.abs(keyTotal - currentTotal) <= PAGE_CACHE_RANGE
            }
            state.copy(pageCache = filtered)
        }
    }

    /** 전체 페이지 캐시 클리어 */
    private fun clearAllPageCache() {
        pageLoadJobs.values.forEach { it.cancel() }
        pageLoadJobs.clear()
        _uiState.update { it.copy(pageCache = emptyMap()) }
    }

    /**
     * 현재 + 인접 페이지 데이터 새로고침 (캐시를 비우지 않고 덮어쓰기).
     * 백그라운드 동기화 후 목록 전체가 비었다가 다시 나타나는 깜빡임을 줄인다.
     */
    private fun refreshCurrentPages() {
        val state = _uiState.value
        val year = state.selectedYear
        val month = state.selectedMonth

        loadPageData(year, month, forceReload = true)
        val (prevY, prevM) = MonthPagerUtils.adjacentMonth(year, month, -1)
        loadPageData(prevY, prevM, forceReload = true)
        val (nextY, nextM) = MonthPagerUtils.adjacentMonth(year, month, +1)
        if (!MonthPagerUtils.isFutureYearMonth(nextY, nextM, state.monthStartDay)) {
            loadPageData(nextY, nextM, forceReload = true)
        }
    }

    /** 현재 + 인접 월 데이터 로드 (공통 진입점) */
    private fun loadCurrentAndAdjacentPages() {
        val state = _uiState.value
        val year = state.selectedYear
        val month = state.selectedMonth

        loadPageData(year, month)
        val (prevY, prevM) = MonthPagerUtils.adjacentMonth(year, month, -1)
        loadPageData(prevY, prevM)
        val (nextY, nextM) = MonthPagerUtils.adjacentMonth(year, month, +1)
        if (!MonthPagerUtils.isFutureYearMonth(nextY, nextM, state.monthStartDay)) {
            loadPageData(nextY, nextM)
        }
        evictDistantCache(year, month)
    }

    // ========== 전역 이벤트 처리 ==========

    private fun loadFilterCategories() {
        viewModelScope.launch {
            val expenseCategories = withContext(Dispatchers.IO) {
                categoryProvider.getExpenseEntries()
            }
            val incomeCategories = withContext(Dispatchers.IO) {
                categoryProvider.getIncomeEntries()
            }
            val transferCategories = withContext(Dispatchers.IO) {
                categoryProvider.getTransferEntries()
            }
            _uiState.update {
                it.copy(
                    expenseCategories = expenseCategories,
                    incomeCategories = incomeCategories,
                    transferCategories = transferCategories
                )
            }
        }
    }

    /** 내 카드 변경 등 전역 이벤트 감지 */
    private fun observeDataRefreshEvents() {
        viewModelScope.launch {
            dataRefreshEvent.refreshEvent.collect { event ->
                when (event) {
                    DataRefreshEvent.RefreshType.OWNED_CARD_UPDATED,
                    DataRefreshEvent.RefreshType.CATEGORY_UPDATED -> {
                        // 전체 월 데이터에 영향 → 비가시 캐시 제거 + 현재 페이지 갱신
                        loadFilterCategories()
                        clearAllPageCache()
                        loadCurrentAndAdjacentPages()
                    }

                    DataRefreshEvent.RefreshType.ALL_DATA_DELETED -> {
                        clearAllPageCache()
                        loadCurrentAndAdjacentPages()
                    }

                    DataRefreshEvent.RefreshType.TRANSACTION_ADDED -> {
                        refreshCurrentPages()
                    }

                    DataRefreshEvent.RefreshType.SMS_RECEIVED -> {
                        // MainViewModel이 증분 동기화 처리 → TRANSACTION_ADDED로 갱신됨
                    }

                    DataRefreshEvent.RefreshType.DEBUG_FULL_SYNC_ALL_MESSAGES -> {
                        // MainViewModel이 디버그 전체 동기화 수행 후 TRANSACTION_ADDED로 갱신됨
                    }

                    DataRefreshEvent.RefreshType.DEBUG_SYNC_TODAY_MESSAGES -> {
                        // MainViewModel이 오늘 문자 동기화 수행 후 TRANSACTION_ADDED로 갱신됨
                    }
                }
            }
        }
    }

    /**
     * 설정 로드 및 월 시작일 변경 감지
     * 월 시작일이 변경되면 자동으로 지출 내역을 다시 로드합니다.
     */
    private fun loadSettings() {
        var isFirstEmit = true
        viewModelScope.launch {
            settingsDataStore.monthStartDayFlow
                .distinctUntilChanged()
                .collect { startDay ->
                    if (isFirstEmit) {
                        // 최초: 커스텀 시작일 기준 실효 월로 selectedYear/selectedMonth 설정
                        val (year, month) = DateUtils.getEffectiveCurrentMonth(startDay)
                        _uiState.update {
                            it.copy(
                                monthStartDay = startDay,
                                selectedYear = year,
                                selectedMonth = month
                            )
                        }
                        isFirstEmit = false
                    } else {
                        _uiState.update { it.copy(monthStartDay = startDay) }
                    }
                    clearAllPageCache()
                    loadCurrentAndAdjacentPages()
                }
        }
    }

    // ========== 페이지별 데이터 로드 ==========

    /**
     * 특정 월의 페이지 데이터 로드
     * 해당 월의 지출, 수입, 일별 합계, 가공된 리스트를 조회하여 pageCache에 저장.
     * @param year 대상 연도
     * @param month 대상 월
     */
    private fun loadPageData(
        year: Int,
        month: Int,
        forceReload: Boolean = false
    ) {
        val key = MonthKey(year, month)
        if (!forceReload) {
            // 이미 로드 완료된 캐시가 있으면 스킵 (스와이프 시 불필요한 재로드 방지)
            val existing = _uiState.value.pageCache[key]
            if (existing != null && !existing.isLoading) return
        }

        pageLoadJobs[key]?.cancel()
        pageLoadJobs[key] = viewModelScope.launch {
            // forceReload가 아니고 캐시에 없을 때만 로딩 상태로 초기화
            if (!forceReload && _uiState.value.pageCache[key] == null) {
                updatePageCache(key, HistoryPageData(isLoading = true))
            }

            val state = _uiState.value
            val (startTime, endTime) = DateUtils.getCustomMonthPeriod(
                year, month, state.monthStartDay
            )

            // 제외 키워드 로드 (필터링용)
            val exclusionKeywords = withContext(Dispatchers.IO) {
                smsExclusionRepository.getAllKeywordStrings()
            }
            val excludedCardNames = withContext(Dispatchers.IO) {
                ownedCardRepository.getExcludedCardNames()
            }

            // 수입 로드 (1회성)
            val allIncomes = withContext(Dispatchers.IO) {
                incomeRepository.getIncomesByDateRangeOnce(startTime, endTime)
            }

            val keywordFilteredIncomes = if (exclusionKeywords.isEmpty()) {
                allIncomes
            } else {
                allIncomes.filter { income ->
                    val smsLower = income.originalSms?.lowercase()
                    smsLower == null || exclusionKeywords.none { kw -> smsLower.contains(kw) }
                }
            }

            val incomeCategoriesForFilter = state.selectedIncomeCategories.takeIf { it.isNotEmpty() }
            val typeCategoryFilteredIncomes = if (!state.showIncomes || state.selectedCardNames.isNotEmpty()) {
                emptyList()
            } else {
                keywordFilteredIncomes.filter { income ->
                    incomeCategoriesForFilter?.contains(income.category) ?: true
                }
            }
            val filteredIncomes = typeCategoryFilteredIncomes.filterIncomesByFixed(state.fixedExpenseFilter)
            val sortedIncomes = filteredIncomes.sortedByDescending { inc -> inc.dateTime }
            val incomeTotal = sortedIncomes.sumOf { it.amount }

            // 날짜별 수입 합계 (달력 셀 표시용)
            val incomeDateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.KOREA)
            val dailyIncomeMap = sortedIncomes
                .groupBy { incomeDateFormat.format(java.util.Date(it.dateTime)) }
                .mapValues { (_, incomes) -> incomes.sumOf { it.amount } }

            // 수입 데이터를 먼저 캐시에 반영
            val currentData = _uiState.value.pageCache[key] ?: HistoryPageData()
            updatePageCache(key, currentData.copy(
                incomes = sortedIncomes,
                monthlyIncomeTotal = incomeTotal,
                dailyIncomeTotals = dailyIncomeMap
            ))

            val expenseFlow = expenseRepository.getExpensesByDateRange(startTime, endTime)

            // 지출 내역 Flow로 실시간 감지 (Room DB 변경 시 자동 업데이트)
            expenseFlow
                .catch {
                    val cached = _uiState.value.pageCache[key] ?: HistoryPageData()
                    updatePageCache(key, cached.copy(isLoading = false))
                }
                .collect { allExpenses ->
                    val keywordFilteredExpenses = if (exclusionKeywords.isEmpty()) {
                        allExpenses
                    } else {
                        allExpenses.filter { expense ->
                            val smsLower = expense.originalSms.lowercase()
                            exclusionKeywords.none { kw -> smsLower.contains(kw) }
                        }
                    }

                    val currentState = _uiState.value
                    val expenseCategoriesForFilter = resolveExpenseFilterCategories(currentState)
                    val transferCategoriesForFilter =
                        currentState.selectedTransferCategories.takeIf { it.isNotEmpty() }
                    val selectedCardNamesForFilter =
                        currentState.selectedCardNames.takeIf { it.isNotEmpty() }
                    val visibleExpenses = CardVisibilityFilter.filterVisibleExpenses(
                        keywordFilteredExpenses,
                        excludedCardNames
                    )
                    updateAvailableCardNamesIfCurrent(key, visibleExpenses)
                    val cardFilteredExpenses = selectedCardNamesForFilter?.let { cardNames ->
                        CardVisibilityFilter.filterSelectedExpenses(visibleExpenses, cardNames)
                    } ?: visibleExpenses

                    val typeCategoryFilteredExpenses = cardFilteredExpenses.filter { expense ->
                        val isTransfer = expense.transactionType == "TRANSFER"
                        if (isTransfer) {
                            if (!currentState.showTransfers) return@filter false
                            transferCategoriesForFilter?.contains(expense.category) ?: true
                        } else {
                            if (!currentState.showExpenses) return@filter false
                            expenseCategoriesForFilter?.contains(expense.category) ?: true
                        }
                    }
                    val filteredExpenses =
                        typeCategoryFilteredExpenses.filterExpensesByFixed(currentState.fixedExpenseFilter)
                    val statsExpenses = filteredExpenses.filter { it.isIncludedInExpenseStats() }
                    val sortedExpenses = transactionListMapper.sortExpenses(filteredExpenses, currentState.sortOrder)
                    val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.KOREA)
                    val dailyTotalsMap = statsExpenses
                        .groupBy { dateFormat.format(java.util.Date(it.dateTime)) }
                        .mapValues { (_, expenses) -> expenses.sumOf { it.amount } }
                    val incomesForList = _uiState.value.pageCache[key]?.incomes ?: emptyList()

                    val cached = _uiState.value.pageCache[key] ?: HistoryPageData()
                    updatePageCache(key, cached.copy(
                        isLoading = false,
                        expenses = sortedExpenses,
                        monthlyTotal = statsExpenses.sumOf { it.amount },
                        dailyTotals = dailyTotalsMap,
                        transactionListItems = transactionListMapper.build(
                            sortedExpenses, incomesForList, currentState.sortOrder,
                            currentState.showExpenses, currentState.showIncomes, currentState.showTransfers,
                            currentState.fixedExpenseFilter
                        )
                    ))
                }
        }
    }

    private fun resolveExpenseFilterCategories(state: HistoryUiState): Set<String>? {
        if (state.selectedExpenseCategories.isNotEmpty()) {
            return state.selectedExpenseCategories
        }
        return state.selectedCategory?.let { resolveExactExpenseCategorySet(it) }
    }

    private fun resolveExactExpenseCategorySet(categoryName: String): Set<String> {
        val category = Category.fromDisplayName(categoryName)
        if (category == Category.ETC && categoryName != Category.ETC.displayName) {
            return setOf(categoryName)
        }
        return setOf(category.displayName)
    }

    private fun updateAvailableCardNamesIfCurrent(
        key: MonthKey,
        expenses: List<ExpenseEntity>
    ) {
        val state = _uiState.value
        if (key != MonthKey(state.selectedYear, state.selectedMonth)) return

        val cardNames = expenses
            .map { it.cardName }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
        if (state.availableCardNames == cardNames) return
        _uiState.update { it.copy(availableCardNames = cardNames) }
    }

    /** 정렬 순서 변경 */
    fun setSortOrder(sortOrder: SortOrder) {
        _uiState.update { it.copy(sortOrder = sortOrder) }
        // 캐시 내 모든 페이지의 transactionListItems 재빌드
        rebuildAllPageListItems()
    }

    /** 특정 년/월로 이동 (HorizontalPager에서 호출) */
    fun setMonth(year: Int, month: Int) {
        val state = _uiState.value
        if (state.selectedYear == year && state.selectedMonth == month) return
        _uiState.update { it.copy(selectedYear = year, selectedMonth = month) }
        loadCurrentAndAdjacentPages()
    }

    /** 이전 월로 이동 */
    fun previousMonth() {
        val state = _uiState.value
        var newYear = state.selectedYear
        var newMonth = state.selectedMonth - 1
        if (newMonth < 1) {
            newMonth = 12
            newYear -= 1
        }
        setMonth(newYear, newMonth)
    }

    /** 다음 월로 이동 (현재 실효 월 이후로는 이동 불가) */
    fun nextMonth() {
        val state = _uiState.value
        val (effectiveYear, effectiveMonth) = DateUtils.getEffectiveCurrentMonth(state.monthStartDay)
        if (state.selectedYear >= effectiveYear && state.selectedMonth >= effectiveMonth) return

        var newYear = state.selectedYear
        var newMonth = state.selectedMonth + 1
        if (newMonth > 12) {
            newMonth = 1
            newYear += 1
        }
        setMonth(newYear, newMonth)
    }

    /** 카테고리 필터 적용 (null이면 전체) */
    fun filterByCategory(category: String?) {
        analyticsHelper.logClick(AnalyticsEvent.SCREEN_HISTORY, AnalyticsEvent.CLICK_CATEGORY_FILTER)
        val selectedExpenseCategories = category?.let { resolveExactExpenseCategorySet(it) } ?: emptySet()
        _uiState.update {
            it.copy(
                selectedCategory = category,
                selectedExpenseCategories = selectedExpenseCategories,
                selectedIncomeCategories = emptySet(),
                selectedTransferCategories = emptySet(),
                selectedCardNames = emptySet(),
                showExpenses = true,
                showIncomes = true,
                showTransfers = true
            )
        }
        clearAllPageCache()
        loadCurrentAndAdjacentPages()
    }

    /** 지출 항목 삭제 */
    fun deleteExpense(expense: ExpenseEntity) {
        viewModelScope.launch {
            try {
                DeletedSmsTracker.markDeleted(expense.smsId)
                withContext(Dispatchers.IO) { expenseRepository.delete(expense) }
                clearAllPageCache()
                loadCurrentAndAdjacentPages()
                dataRefreshEvent.emit(DataRefreshEvent.RefreshType.TRANSACTION_ADDED)
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message) }
            }
        }
    }

    /** 수입 항목 삭제 */
    fun deleteIncome(income: IncomeEntity) {
        viewModelScope.launch {
            try {
                income.smsId?.let { DeletedSmsTracker.markDeleted(it) }
                withContext(Dispatchers.IO) { incomeRepository.delete(income) }
                snackbarBus.show("수입이 삭제되었습니다")
                clearAllPageCache()
                loadCurrentAndAdjacentPages()
                dataRefreshEvent.emit(DataRefreshEvent.RefreshType.TRANSACTION_ADDED)
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "수입 삭제 실패: ${e.message}") }
            }
        }
    }

    /** 에러 메시지 초기화 */
    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    /**
     * 특정 지출의 카테고리 변경
     * 동일 가게명의 모든 지출을 일괄 변경 + 벡터 학습 + 유사 가게 전파
     */
    fun updateExpenseCategory(storeName: String, newCategory: String) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    categoryClassifierService.updateCategoryForAllSameStore(
                        storeName,
                        newCategory
                    )
                }
                clearAllPageCache()
                loadCurrentAndAdjacentPages()
                dataRefreshEvent.emit(DataRefreshEvent.RefreshType.CATEGORY_UPDATED)
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "카테고리 변경 실패: ${e.message}") }
            }
        }
    }

    /**
     * Pull-to-Refresh 및 외부에서 호출 가능한 데이터 새로고침
     * 다른 화면에서 DB 변경 시에도 호출됨
     */
    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            refreshCurrentPages()
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }

    // ========== 검색 기능 ==========

    /** 검색 모드 진입 */
    fun enterSearchMode() {
        _uiState.update { it.copy(isSearchMode = true) }
    }

    /** 검색 모드 종료 */
    fun exitSearchMode() {
        _uiState.update { it.copy(isSearchMode = false, searchQuery = "") }
        clearAllPageCache()
        loadCurrentAndAdjacentPages()
    }

    /** 검색어 변경 및 검색 실행 */
    fun search(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        if (query.isBlank()) {
            clearAllPageCache()
            loadCurrentAndAdjacentPages()
        } else {
            searchExpenses(query)
        }
    }

    /** 지출 내역 검색 (검색 결과는 현재 월의 pageCache에 저장) */
    private fun searchExpenses(query: String) {
        val state = _uiState.value
        val key = MonthKey(state.selectedYear, state.selectedMonth)
        pageLoadJobs[key]?.cancel()
        pageLoadJobs[key] = viewModelScope.launch {
            updatePageCache(key, HistoryPageData(isLoading = true))

            try {
                val results = withContext(Dispatchers.IO) {
                    expenseRepository.searchExpenses(query)
                }
                val currentState = _uiState.value
                val excludedCardNames = withContext(Dispatchers.IO) {
                    ownedCardRepository.getExcludedCardNames()
                }
                val visibleResults = CardVisibilityFilter.filterVisibleExpenses(
                    results,
                    excludedCardNames
                )
                val cardFilteredResults = CardVisibilityFilter.filterSelectedExpenses(
                    visibleResults,
                    currentState.selectedCardNames
                )
                val sortedResults = transactionListMapper.sortExpenses(cardFilteredResults, currentState.sortOrder)
                val filteredResults = cardFilteredResults.filterExpensesByFixed(currentState.fixedExpenseFilter)
                updatePageCache(key, HistoryPageData(
                    isLoading = false,
                    expenses = sortedResults,
                    monthlyTotal = filteredResults
                        .filter { it.isIncludedInExpenseStats() }
                        .sumOf { e -> e.amount },
                    transactionListItems = transactionListMapper.build(
                        sortedResults, emptyList(), currentState.sortOrder,
                        currentState.showExpenses, currentState.showIncomes, currentState.showTransfers,
                        currentState.fixedExpenseFilter
                    )
                ))
            } catch (e: Exception) {
                updatePageCache(key, HistoryPageData(isLoading = false))
                _uiState.update { it.copy(errorMessage = e.message) }
            }
        }
    }

    // ========== 수동 지출 추가 기능 ==========

    /**
     * 수동으로 지출 추가
     * SMS가 아닌 현금 결제 등을 직접 입력할 때 사용
     */
    fun addManualExpense(
        amount: Int,
        storeName: String,
        category: String,
        cardName: String = "현금",
        dateTime: Long = System.currentTimeMillis()
    ) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val expense = ExpenseEntity(
                        amount = amount,
                        storeName = storeName,
                        cardName = cardName,
                        dateTime = dateTime,
                        category = category,
                        originalSms = "수동 입력",
                        smsId = "manual_${System.currentTimeMillis()}"
                    )
                    expenseRepository.insert(expense)
                }
                snackbarBus.show("지출이 추가되었습니다")
                clearAllPageCache()
                loadCurrentAndAdjacentPages()
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "지출 추가 실패: ${e.message}") }
            }
        }
    }

    /** 지출 메모 업데이트 */
    fun updateExpenseMemo(expenseId: Long, memo: String?) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    expenseRepository.updateMemo(expenseId, memo?.ifBlank { null })
                }
                snackbarBus.show("메모가 저장되었습니다")
                clearAllPageCache()
                loadCurrentAndAdjacentPages()
                dataRefreshEvent.emit(DataRefreshEvent.RefreshType.TRANSACTION_ADDED)
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "메모 저장 실패: ${e.message}") }
            }
        }
    }

    /** 수입 메모 업데이트 */
    fun updateIncomeMemo(incomeId: Long, memo: String?) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    incomeRepository.updateMemo(incomeId, memo?.ifBlank { null })
                }
                snackbarBus.show("메모가 저장되었습니다")
                clearAllPageCache()
                loadCurrentAndAdjacentPages()
                dataRefreshEvent.emit(DataRefreshEvent.RefreshType.TRANSACTION_ADDED)
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "메모 저장 실패: ${e.message}") }
            }
        }
    }

    /**
     * BottomSheet에서 필터 적용
     * 정렬/거래유형/카테고리를 한 번에 반영
     */
    fun applyFilter(
        sortOrder: SortOrder,
        showExpenses: Boolean,
        showIncomes: Boolean,
        category: String?,
        fixedExpenseFilter: FixedExpenseFilter = FixedExpenseFilter.ALL
    ) {
        val selectedExpenseCategories = category?.let { resolveExactExpenseCategorySet(it) } ?: emptySet()
        _uiState.update {
            it.copy(
                sortOrder = sortOrder,
                showExpenses = showExpenses,
                showIncomes = showIncomes,
                showTransfers = true,
                selectedCategory = category,
                selectedExpenseCategories = selectedExpenseCategories,
                selectedIncomeCategories = emptySet(),
                selectedTransferCategories = emptySet(),
                selectedCardNames = emptySet(),
                fixedExpenseFilter = fixedExpenseFilter
            )
        }
        clearAllPageCache()
        loadCurrentAndAdjacentPages()
    }

    fun applyFilter(
        sortOrder: SortOrder,
        showExpenses: Boolean,
        showIncomes: Boolean,
        showTransfers: Boolean,
        expenseCategories: Set<String>,
        incomeCategories: Set<String>,
        transferCategories: Set<String>,
        cardNames: Set<String>,
        fixedExpenseFilter: FixedExpenseFilter = FixedExpenseFilter.ALL
    ) {
        _uiState.update {
            it.copy(
                sortOrder = sortOrder,
                showExpenses = showExpenses,
                showIncomes = showIncomes,
                showTransfers = showTransfers,
                selectedCategory = null,
                selectedExpenseCategories = expenseCategories,
                selectedIncomeCategories = incomeCategories,
                selectedTransferCategories = transferCategories,
                selectedCardNames = cardNames,
                fixedExpenseFilter = fixedExpenseFilter
            )
        }
        clearAllPageCache()
        loadCurrentAndAdjacentPages()
    }

    /** 필터/검색 상태를 초기값으로 리셋 (탭 재클릭 시 호출) */
    fun resetFilters() {
        val state = _uiState.value
        val needsReload = state.selectedCategory != null ||
                state.selectedExpenseCategories.isNotEmpty() ||
                state.selectedIncomeCategories.isNotEmpty() ||
                state.selectedTransferCategories.isNotEmpty() ||
                state.selectedCardNames.isNotEmpty() ||
                state.isSearchMode ||
                state.searchQuery.isNotEmpty() ||
                state.sortOrder != SortOrder.DATE_DESC ||
                !state.showExpenses ||
                !state.showIncomes ||
                !state.showTransfers ||
                state.fixedExpenseFilter != FixedExpenseFilter.ALL
        if (!needsReload) return

        _uiState.update {
            it.copy(
                selectedCategory = null,
                selectedExpenseCategories = emptySet(),
                selectedIncomeCategories = emptySet(),
                selectedTransferCategories = emptySet(),
                selectedCardNames = emptySet(),
                isSearchMode = false,
                searchQuery = "",
                sortOrder = SortOrder.DATE_DESC,
                showExpenses = true,
                showIncomes = true,
                showTransfers = true,
                fixedExpenseFilter = FixedExpenseFilter.ALL
            )
        }
        clearAllPageCache()
        loadCurrentAndAdjacentPages()
    }

    // ========== Intent 처리 ==========

    /** 모든 사용자 인터랙션을 Intent로 처리 */
    fun onIntent(intent: HistoryIntent) {
        when (intent) {
            is HistoryIntent.SelectExpense -> {
                _uiState.update { it.copy(selectedExpense = intent.expense) }
            }

            is HistoryIntent.SelectIncome -> {
                _uiState.update { it.copy(selectedIncome = intent.income) }
            }

            is HistoryIntent.DismissDialog -> {
                _uiState.update { it.copy(selectedExpense = null, selectedIncome = null) }
            }

            is HistoryIntent.DeleteExpense -> {
                _uiState.update { it.copy(selectedExpense = null) }
                deleteExpense(intent.expense)
            }

            is HistoryIntent.ChangeCategory -> {
                _uiState.update { it.copy(selectedExpense = null) }
                updateExpenseCategory(intent.storeName, intent.newCategory)
            }

            is HistoryIntent.UpdateExpenseMemo -> {
                _uiState.update { it.copy(selectedExpense = null) }
                updateExpenseMemo(intent.expenseId, intent.memo)
            }

            is HistoryIntent.DeleteIncome -> {
                _uiState.update { it.copy(selectedIncome = null) }
                deleteIncome(intent.income)
            }

            is HistoryIntent.UpdateIncomeMemo -> {
                _uiState.update { it.copy(selectedIncome = null) }
                updateIncomeMemo(intent.incomeId, intent.memo)
            }
        }
    }

    // ========== 리스트 데이터 가공 ==========

    /** 캐시 내 모든 페이지의 transactionListItems 재빌드 (정렬/필터 변경 시) */
    private fun rebuildAllPageListItems() {
        val state = _uiState.value
        val updatedCache = state.pageCache.mapValues { (_, pageData) ->
            val incomesForList = pageData.incomes
            val sortedExpenses = transactionListMapper.sortExpenses(pageData.expenses, state.sortOrder)
            pageData.copy(
                expenses = sortedExpenses,
                transactionListItems = transactionListMapper.build(
                    sortedExpenses, incomesForList, state.sortOrder,
                    state.showExpenses, state.showIncomes, state.showTransfers, state.fixedExpenseFilter
                )
            )
        }
        _uiState.update { it.copy(pageCache = updatedCache) }
    }

    // ===== 화면별 온보딩 =====

    fun hasSeenScreenOnboardingFlow(screenId: String) =
        settingsDataStore.hasSeenScreenOnboardingFlow(screenId)

    fun markScreenOnboardingSeen(screenId: String) {
        viewModelScope.launch {
            settingsDataStore.setScreenOnboardingSeen(screenId)
        }
    }
}
