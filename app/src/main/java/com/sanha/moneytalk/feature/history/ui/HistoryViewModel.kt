package com.sanha.moneytalk.feature.history.ui

import android.content.Context
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sanha.moneytalk.R
import com.sanha.moneytalk.core.database.entity.ExpenseEntity
import com.sanha.moneytalk.core.database.entity.IncomeEntity
import com.sanha.moneytalk.core.datastore.SettingsDataStore
import com.sanha.moneytalk.core.firebase.AnalyticsEvent
import com.sanha.moneytalk.core.firebase.AnalyticsHelper
import com.sanha.moneytalk.core.model.Category
import com.sanha.moneytalk.core.ui.AppSnackbarBus
import com.sanha.moneytalk.core.ui.component.transaction.card.ExpenseTransactionCardInfo
import com.sanha.moneytalk.core.ui.component.transaction.card.IncomeTransactionCardInfo
import com.sanha.moneytalk.core.ui.component.transaction.card.TransactionCardInfo
import com.sanha.moneytalk.core.ui.component.transaction.header.TransactionGroupHeaderInfo
import com.sanha.moneytalk.core.ui.component.MonthKey
import com.sanha.moneytalk.core.ui.component.MonthPagerUtils
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Date
import javax.inject.Inject

/**
 * 정렬 방식
 */
enum class SortOrder {
    DATE_DESC,      // 최신순 (기본값)
    AMOUNT_DESC,    // 금액 높은순
    STORE_FREQ      // 사용처별 (많이 사용한 곳 순)
}

/**
 * History 화면의 모든 사용자 인터랙션을 표현하는 Intent
 */
sealed interface HistoryIntent {
    // 아이템 클릭
    data class SelectExpense(val expense: ExpenseEntity) : HistoryIntent
    data class SelectIncome(val income: IncomeEntity) : HistoryIntent
    data object DismissDialog : HistoryIntent

    // 지출 액션
    data class DeleteExpense(val expense: ExpenseEntity) : HistoryIntent
    data class ChangeCategory(val storeName: String, val newCategory: String) :
        HistoryIntent

    data class UpdateExpenseMemo(val expenseId: Long, val memo: String?) : HistoryIntent

    // 수입 액션
    data class DeleteIncome(val income: IncomeEntity) : HistoryIntent
    data class UpdateIncomeMemo(val incomeId: Long, val memo: String?) : HistoryIntent
}

/**
 * LazyColumn에 바로 렌더링할 수 있는 플랫 리스트 아이템
 *
 * Composable은 Info.toComposeData() → UiModel로 변환 후 순수 렌더링만 수행.
 * Entity 참조는 Intent 전달용으로만 보관.
 */
sealed interface TransactionListItem {
    /** 그룹 헤더 - TransactionGroupHeaderInfo 구현 */
    data class Header(
        override val title: String,
        override val expenseTotal: Int = 0,
        override val incomeTotal: Int = 0
    ) : TransactionListItem, TransactionGroupHeaderInfo

    /** 지출 아이템 - TransactionCardInfo 포함 */
    data class ExpenseItem(
        val expense: ExpenseEntity,
        val cardInfo: TransactionCardInfo = ExpenseTransactionCardInfo(expense)
    ) : TransactionListItem

    /** 수입 아이템 - TransactionCardInfo 포함 */
    data class IncomeItem(
        val income: IncomeEntity,
        val cardInfo: TransactionCardInfo = IncomeTransactionCardInfo(income)
    ) : TransactionListItem
}

/**
 * 내역 화면의 페이지별(월별) 데이터.
 * HorizontalPager의 각 페이지가 독립적으로 렌더링할 수 있도록 월별 데이터를 캡슐화.
 */
@Stable
data class HistoryPageData(
    val isLoading: Boolean = true,
    val expenses: List<ExpenseEntity> = emptyList(),
    val incomes: List<IncomeEntity> = emptyList(),
    val monthlyTotal: Int = 0,
    val dailyTotals: Map<String, Int> = emptyMap(),
    val monthlyIncomeTotal: Int = 0,
    val transactionListItems: List<TransactionListItem> = emptyList()
)

/**
 * 내역 화면 UI 상태
 *
 * 월별 데이터는 [pageCache]에서 관리하며, 글로벌 상태만 직접 보유.
 * HorizontalPager의 각 페이지는 pageCache[MonthKey]에서 자기 월의 데이터를 읽어 렌더링.
 *
 * @property pageCache 월별 페이지 데이터 캐시 (최대 3~5개)
 * @property selectedCategory 선택된 카테고리 필터 (null이면 전체)
 * @property selectedYear 선택된 연도
 * @property selectedMonth 선택된 월
 * @property monthStartDay 월 시작일 (1~28, 사용자 설정)
 * @property errorMessage 에러 메시지 (null이면 에러 없음)
 * @property searchQuery 검색어
 * @property isSearchMode 검색 모드 여부
 * @property sortOrder 정렬 순서
 * @property showExpenses 지출 표시 여부 (BottomSheet 필터)
 * @property showIncomes 수입 표시 여부 (BottomSheet 필터)
 */
@Stable
data class HistoryUiState(
    val pageCache: Map<MonthKey, HistoryPageData> = emptyMap(),
    val isRefreshing: Boolean = false,
    val selectedCategory: String? = null,
    val selectedYear: Int = DateUtils.getCurrentYear(),
    val selectedMonth: Int = DateUtils.getCurrentMonth(),
    val monthStartDay: Int = 1,
    val errorMessage: String? = null,
    val searchQuery: String = "",
    val isSearchMode: Boolean = false,
    val sortOrder: SortOrder = SortOrder.DATE_DESC,
    val showExpenses: Boolean = true,
    val showIncomes: Boolean = true,
    // 다이얼로그 상태 (Composable에서 remember 대신 ViewModel에서 관리)
    val selectedExpense: ExpenseEntity? = null,
    val selectedIncome: IncomeEntity? = null,
    // 월별 동기화 해제 여부
    val syncedMonths: Set<String> = emptySet(),
    val showFullSyncAdDialog: Boolean = false
) {
    /** 현재 선택 월의 페이지 데이터 (하위 호환용) */
    private val currentPageData: HistoryPageData
        get() = pageCache[MonthKey(selectedYear, selectedMonth)] ?: HistoryPageData()

    /** 필터 적용된 지출 총합 */
    val filteredExpenseTotal: Int
        get() = if (showExpenses) currentPageData.expenses.sumOf { it.amount } else 0

    /** 필터 적용된 수입 총합 (카테고리 필터 시 수입 숨김) */
    val filteredIncomeTotal: Int
        get() = if (showIncomes && selectedCategory == null) currentPageData.incomes.sumOf { it.amount } else 0
}

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
    private val dataRefreshEvent: DataRefreshEvent,
    private val snackbarBus: AppSnackbarBus,
    private val smsExclusionRepository: com.sanha.moneytalk.core.database.SmsExclusionRepository,
    @ApplicationContext private val context: Context,
    private val analyticsHelper: AnalyticsHelper,
    val rewardAdManager: com.sanha.moneytalk.core.ad.RewardAdManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    /** 페이지별 로드 Job 관리 (월별 독립 취소) */
    private val pageLoadJobs = mutableMapOf<MonthKey, Job>()

    /** 페이지 캐시 최대 허용 범위 (현재 월 ± 이 값) */
    private companion object {
        const val PAGE_CACHE_RANGE = 2

        /** 초기 동기화 제한 기간 (2개월, 밀리초) — HomeViewModel과 동일값 */
        const val DEFAULT_SYNC_PERIOD_MILLIS = 60L * 24 * 60 * 60 * 1000
    }

    init {
        loadSettings()
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

    /** 현재 + 인접 월 데이터 로드 (공통 진입점) */
    private fun loadCurrentAndAdjacentPages() {
        val state = _uiState.value
        val year = state.selectedYear
        val month = state.selectedMonth

        loadPageData(year, month)
        val (prevY, prevM) = MonthPagerUtils.adjacentMonth(year, month, -1)
        loadPageData(prevY, prevM)
        val (nextY, nextM) = MonthPagerUtils.adjacentMonth(year, month, +1)
        if (!MonthPagerUtils.isFutureYearMonth(nextY, nextM)) {
            loadPageData(nextY, nextM)
        }
        evictDistantCache(year, month)
    }

    // ========== 전역 이벤트 처리 ==========

    /** 내 카드 변경 등 전역 이벤트 감지 */
    private fun observeDataRefreshEvents() {
        viewModelScope.launch {
            dataRefreshEvent.refreshEvent.collect { event ->
                when (event) {
                    DataRefreshEvent.RefreshType.OWNED_CARD_UPDATED,
                    DataRefreshEvent.RefreshType.CATEGORY_UPDATED -> {
                        clearAllPageCache()
                        loadCurrentAndAdjacentPages()
                    }

                    DataRefreshEvent.RefreshType.ALL_DATA_DELETED -> {
                        clearAllPageCache()
                        loadCurrentAndAdjacentPages()
                    }

                    DataRefreshEvent.RefreshType.TRANSACTION_ADDED -> {
                        clearAllPageCache()
                        loadCurrentAndAdjacentPages()
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
        viewModelScope.launch {
            settingsDataStore.monthStartDayFlow.collect { startDay ->
                _uiState.update { it.copy(monthStartDay = startDay) }
                clearAllPageCache()
                loadCurrentAndAdjacentPages()
            }
        }
        viewModelScope.launch {
            settingsDataStore.syncedMonthsFlow.collect { months ->
                _uiState.update { it.copy(syncedMonths = months) }
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
    private fun loadPageData(year: Int, month: Int) {
        val key = MonthKey(year, month)
        // 이미 로드 완료된 캐시가 있으면 스킵 (스와이프 시 불필요한 재로드 방지)
        val existing = _uiState.value.pageCache[key]
        if (existing != null && !existing.isLoading) return

        pageLoadJobs[key]?.cancel()
        pageLoadJobs[key] = viewModelScope.launch {
            // 캐시에 없으면 로딩 상태로 초기화
            if (_uiState.value.pageCache[key] == null) {
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

            // 카테고리 필터
            val categoryFilter = state.selectedCategory
            val categoriesForFilter = categoryFilter?.let {
                val cat = Category.fromDisplayName(it)
                cat.displayNamesIncludingSub
            }

            // 수입 로드 (1회성)
            val allIncomes = withContext(Dispatchers.IO) {
                incomeRepository.getIncomesByDateRangeOnce(startTime, endTime)
            }
            val filteredIncomes = if (exclusionKeywords.isEmpty()) {
                allIncomes
            } else {
                allIncomes.filter { income ->
                    val smsLower = income.originalSms?.lowercase()
                    smsLower == null || exclusionKeywords.none { kw -> smsLower.contains(kw) }
                }
            }
            val sortedIncomes = filteredIncomes.sortedByDescending { inc -> inc.dateTime }
            val incomeTotal = filteredIncomes.sumOf { it.amount }

            // 수입 데이터를 먼저 캐시에 반영
            val currentData = _uiState.value.pageCache[key] ?: HistoryPageData()
            updatePageCache(key, currentData.copy(
                incomes = sortedIncomes,
                monthlyIncomeTotal = incomeTotal
            ))

            val expenseFlow = if (categoriesForFilter != null) {
                expenseRepository.getExpensesFilteredByCategories(
                    cardName = null,
                    categories = categoriesForFilter,
                    startTime = startTime,
                    endTime = endTime
                )
            } else {
                expenseRepository.getExpensesFiltered(
                    cardName = null,
                    category = null,
                    startTime = startTime,
                    endTime = endTime
                )
            }

            // 월별/일별 총액 계산 (제외 키워드 + 카테고리 필터 적용)
            try {
                expenseRepository.getExpensesByDateRange(startTime, endTime)
                    .first()
                    .let { allExpensesForTotal ->
                        val filteredForTotal = if (exclusionKeywords.isEmpty()) {
                            allExpensesForTotal
                        } else {
                            allExpensesForTotal.filter { expense ->
                                val smsLower = expense.originalSms?.lowercase()
                                smsLower == null || exclusionKeywords.none { kw ->
                                    smsLower.contains(kw)
                                }
                            }
                        }
                        val categoryFiltered = if (categoriesForFilter != null) {
                            filteredForTotal.filter { it.category in categoriesForFilter }
                        } else {
                            filteredForTotal
                        }
                        val monthlyTotal = categoryFiltered.sumOf { it.amount }
                        val dateFormat =
                            java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.KOREA)
                        val dailyTotalsMap = categoryFiltered
                            .groupBy { dateFormat.format(java.util.Date(it.dateTime)) }
                            .mapValues { (_, expenses) -> expenses.sumOf { it.amount } }
                        val cached = _uiState.value.pageCache[key] ?: HistoryPageData()
                        updatePageCache(key, cached.copy(
                            monthlyTotal = monthlyTotal,
                            dailyTotals = dailyTotalsMap,
                            monthlyIncomeTotal = incomeTotal
                        ))
                    }
            } catch (e: Exception) {
                // 총액 로딩 실패 시 무시
            }

            // 지출 내역 Flow로 실시간 감지 (Room DB 변경 시 자동 업데이트)
            expenseFlow
                .catch { e ->
                    val cached = _uiState.value.pageCache[key] ?: HistoryPageData()
                    updatePageCache(key, cached.copy(isLoading = false))
                }
                .collect { allExpenses ->
                    val expenses = if (exclusionKeywords.isEmpty()) {
                        allExpenses
                    } else {
                        allExpenses.filter { expense ->
                            val smsLower = expense.originalSms?.lowercase()
                            smsLower == null || exclusionKeywords.none { kw -> smsLower.contains(kw) }
                        }
                    }
                    val currentState = _uiState.value
                    val sortedExpenses = sortExpenses(expenses, currentState.sortOrder)
                    // 수입: 카테고리 필터 활성 시 제외
                    val incomesForList = if (currentState.selectedCategory != null) emptyList()
                    else (_uiState.value.pageCache[key]?.incomes ?: emptyList())

                    val cached = _uiState.value.pageCache[key] ?: HistoryPageData()
                    updatePageCache(key, cached.copy(
                        isLoading = false,
                        expenses = sortedExpenses,
                        transactionListItems = buildTransactionListItems(
                            sortedExpenses, incomesForList, currentState.sortOrder,
                            currentState.showExpenses, currentState.showIncomes
                        )
                    ))
                }
        }
    }

    /** 정렬 방식에 따라 지출 내역 정렬 */
    private fun sortExpenses(
        expenses: List<ExpenseEntity>,
        sortOrder: SortOrder
    ): List<ExpenseEntity> {
        return when (sortOrder) {
            SortOrder.DATE_DESC -> expenses.sortedByDescending { it.dateTime }
            SortOrder.AMOUNT_DESC -> expenses.sortedByDescending { it.amount }
            SortOrder.STORE_FREQ -> {
                // 가게별 사용 빈도 계산
                val storeFrequency = expenses.groupingBy { it.storeName }.eachCount()
                // 빈도 높은 순 정렬, 같은 가게 내에서는 최신순
                expenses.sortedWith(
                    compareByDescending<ExpenseEntity> { storeFrequency[it.storeName] ?: 0 }
                        .thenByDescending { it.dateTime }
                )
            }
        }
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

    /** 다음 월로 이동 (현재 월 이후로는 이동 불가) */
    fun nextMonth() {
        val state = _uiState.value
        val currentYear = DateUtils.getCurrentYear()
        val currentMonth = DateUtils.getCurrentMonth()
        if (state.selectedYear >= currentYear && state.selectedMonth >= currentMonth) return

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
        _uiState.update { it.copy(selectedCategory = category) }
        clearAllPageCache()
        loadCurrentAndAdjacentPages()
    }

    /** 지출 항목 삭제 */
    fun deleteExpense(expense: ExpenseEntity) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { expenseRepository.delete(expense) }
                clearAllPageCache()
                loadCurrentAndAdjacentPages()
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message) }
            }
        }
    }

    /** 수입 항목 삭제 */
    fun deleteIncome(income: IncomeEntity) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { incomeRepository.delete(income) }
                snackbarBus.show("수입이 삭제되었습니다")
                clearAllPageCache()
                loadCurrentAndAdjacentPages()
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
            clearAllPageCache()
            loadCurrentAndAdjacentPages()
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
                val sortedResults = sortExpenses(results, currentState.sortOrder)
                updatePageCache(key, HistoryPageData(
                    isLoading = false,
                    expenses = sortedResults,
                    monthlyTotal = results.sumOf { e -> e.amount },
                    transactionListItems = buildTransactionListItems(
                        sortedResults, emptyList(), currentState.sortOrder,
                        currentState.showExpenses, currentState.showIncomes
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
        category: String?
    ) {
        _uiState.update {
            it.copy(
                sortOrder = sortOrder,
                showExpenses = showExpenses,
                showIncomes = showIncomes,
                selectedCategory = category
            )
        }
        clearAllPageCache()
        loadCurrentAndAdjacentPages()
    }

    /** 필터/검색 상태를 초기값으로 리셋 (탭 재클릭 시 호출) */
    fun resetFilters() {
        val state = _uiState.value
        val needsReload = state.selectedCategory != null ||
                state.isSearchMode ||
                state.searchQuery.isNotEmpty() ||
                state.sortOrder != SortOrder.DATE_DESC ||
                !state.showExpenses ||
                !state.showIncomes
        if (!needsReload) return

        _uiState.update {
            it.copy(
                selectedCategory = null,
                isSearchMode = false,
                searchQuery = "",
                sortOrder = SortOrder.DATE_DESC,
                showExpenses = true,
                showIncomes = true
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
            val incomesForList = if (state.selectedCategory != null) emptyList() else pageData.incomes
            val sortedExpenses = sortExpenses(pageData.expenses, state.sortOrder)
            pageData.copy(
                expenses = sortedExpenses,
                transactionListItems = buildTransactionListItems(
                    sortedExpenses, incomesForList, state.sortOrder,
                    state.showExpenses, state.showIncomes
                )
            )
        }
        _uiState.update { it.copy(pageCache = updatedCache) }
    }

    /**
     * 지출+수입 데이터를 LazyColumn에 바로 렌더링 가능한 플랫 리스트로 가공
     * showExpenses/showIncomes 필터에 따라 표시할 항목을 결정
     */
    private fun buildTransactionListItems(
        expenses: List<ExpenseEntity>,
        incomes: List<IncomeEntity>,
        sortOrder: SortOrder,
        showExpenses: Boolean,
        showIncomes: Boolean
    ): List<TransactionListItem> {
        val filteredExpenses = if (showExpenses) expenses else emptyList()
        val filteredIncomes = if (showIncomes) incomes else emptyList()

        // 둘 다 해제된 경우 빈 리스트
        if (!showExpenses && !showIncomes) {
            return emptyList()
        }

        // 수입만 보기 모드: 날짜별 그룹핑
        if (!showExpenses && showIncomes) {
            return buildIncomeDayGroups(filteredIncomes)
        }

        return when (sortOrder) {
            SortOrder.DATE_DESC -> buildDateDescItems(filteredExpenses, filteredIncomes)
            SortOrder.AMOUNT_DESC -> buildAmountDescItems(filteredExpenses, filteredIncomes)
            SortOrder.STORE_FREQ -> buildStoreFreqItems(filteredExpenses, filteredIncomes)
        }
    }

    /** DATE_DESC: 날짜별 그룹핑 (지출 + 수입 통합) */
    private fun buildDateDescItems(
        expenses: List<ExpenseEntity>,
        incomes: List<IncomeEntity>
    ): List<TransactionListItem> {
        val items = mutableListOf<TransactionListItem>()

        val groupedExpenses = expenses.groupBy { it.dateTime.toDateKey() }
        val groupedIncomes = incomes.groupBy { it.dateTime.toDateKey() }
        val allDates = (groupedExpenses.keys + groupedIncomes.keys)
            .toSortedSet(compareByDescending { it })

        allDates.forEach { date ->
            val dayExpenses = groupedExpenses[date] ?: emptyList()
            val dayIncomes = groupedIncomes[date] ?: emptyList()
            val dailyExpenseTotal = dayExpenses.sumOf { it.amount }
            val dailyIncomeTotal = dayIncomes.sumOf { it.amount }

            val calendar = Calendar.getInstance().apply { time = date }
            val dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH)
            val dayOfWeekStr =
                context.getString(getDayOfWeekResId(calendar.get(Calendar.DAY_OF_WEEK)))
            val title = context.getString(R.string.history_day_header, dayOfMonth, dayOfWeekStr)

            items.add(
                TransactionListItem.Header(
                    title = title,
                    expenseTotal = dailyExpenseTotal,
                    incomeTotal = dailyIncomeTotal
                )
            )
            // 수입+지출을 시간 최신순으로 통합 정렬
            val merged = dayExpenses.map { it.dateTime to TransactionListItem.ExpenseItem(it) } +
                    dayIncomes.map { it.dateTime to TransactionListItem.IncomeItem(it) }
            merged.sortedByDescending { it.first }
                .forEach { items.add(it.second) }
        }

        return items
    }

    /** AMOUNT_DESC: 금액 높은순 플랫 리스트 (지출 + 수입 통합) */
    private fun buildAmountDescItems(
        expenses: List<ExpenseEntity>,
        incomes: List<IncomeEntity>
    ): List<TransactionListItem> {
        val items = mutableListOf<TransactionListItem>()
        val totalCount = expenses.size + incomes.size
        items.add(
            TransactionListItem.Header(
                title = "${context.getString(R.string.history_sort_amount)} (${
                    context.getString(R.string.history_count_with_unit, totalCount)
                })",
                expenseTotal = expenses.sumOf { it.amount },
                incomeTotal = incomes.sumOf { it.amount }
            )
        )
        // 지출+수입 금액 높은순 통합 정렬
        val merged = expenses.map { it.amount to TransactionListItem.ExpenseItem(it) } +
                incomes.map { it.amount to TransactionListItem.IncomeItem(it) }
        merged.sortedByDescending { it.first }
            .forEach { items.add(it.second) }
        return items
    }

    /** STORE_FREQ: 사용처별 그룹핑 (지출 + 수입 출처별 통합) */
    private fun buildStoreFreqItems(
        expenses: List<ExpenseEntity>,
        incomes: List<IncomeEntity>
    ): List<TransactionListItem> {
        val items = mutableListOf<TransactionListItem>()

        // 지출: 사용처별 그룹핑
        val storeGroups = expenses.groupBy { it.storeName }
            .entries
            .sortedByDescending { it.value.size }

        storeGroups.forEach { (storeName, storeExpenses) ->
            val storeTotal = storeExpenses.sumOf { it.amount }
            items.add(
                TransactionListItem.Header(
                    title = "$storeName (${
                        context.getString(R.string.history_visit_with_unit, storeExpenses.size)
                    })",
                    expenseTotal = storeTotal
                )
            )
            storeExpenses.sortedByDescending { it.dateTime }
                .forEach { items.add(TransactionListItem.ExpenseItem(it)) }
        }

        // 수입: 출처별 그룹핑 (지출 그룹 뒤에 추가)
        if (incomes.isNotEmpty()) {
            val sourceGroups = incomes.groupBy { it.source.ifBlank { it.type } }
                .entries
                .sortedByDescending { it.value.size }

            sourceGroups.forEach { (source, sourceIncomes) ->
                val sourceTotal = sourceIncomes.sumOf { it.amount }
                items.add(
                    TransactionListItem.Header(
                        title = "$source (${
                            context.getString(R.string.history_count_with_unit, sourceIncomes.size)
                        })",
                        incomeTotal = sourceTotal
                    )
                )
                sourceIncomes.sortedByDescending { it.dateTime }
                    .forEach { items.add(TransactionListItem.IncomeItem(it)) }
            }
        }

        return items
    }

    /** 수입 전용: 날짜별 그룹핑 */
    private fun buildIncomeDayGroups(incomes: List<IncomeEntity>): List<TransactionListItem> {
        val items = mutableListOf<TransactionListItem>()

        val groupedIncomes = incomes.groupBy { it.dateTime.toDateKey() }
            .toSortedMap(compareByDescending { it })

        groupedIncomes.forEach { (date, dayIncomes) ->
            val dailyTotal = dayIncomes.sumOf { it.amount }
            val calendar = Calendar.getInstance().apply { time = date }
            val dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH)
            val dayOfWeekStr =
                context.getString(getDayOfWeekResId(calendar.get(Calendar.DAY_OF_WEEK)))
            val title = context.getString(R.string.history_day_header, dayOfMonth, dayOfWeekStr)

            items.add(
                TransactionListItem.Header(
                    title = title,
                    incomeTotal = dailyTotal
                )
            )
            dayIncomes.forEach { items.add(TransactionListItem.IncomeItem(it)) }
        }

        return items
    }

    /** timestamp → 날짜 키 (시분초 제거) */
    private fun Long.toDateKey(): Date {
        return try {
            val calendar = Calendar.getInstance().apply {
                timeInMillis = this@toDateKey
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            calendar.time
        } catch (e: Exception) {
            Date()
        }
    }

    /** Calendar.DAY_OF_WEEK → R.string.day_* 리소스 ID */
    private fun getDayOfWeekResId(dayOfWeek: Int): Int {
        return when (dayOfWeek) {
            Calendar.SUNDAY -> R.string.day_sunday
            Calendar.MONDAY -> R.string.day_monday
            Calendar.TUESDAY -> R.string.day_tuesday
            Calendar.WEDNESDAY -> R.string.day_wednesday
            Calendar.THURSDAY -> R.string.day_thursday
            Calendar.FRIDAY -> R.string.day_friday
            Calendar.SATURDAY -> R.string.day_saturday
            else -> R.string.day_sunday
        }
    }

    // ========== 전체 동기화 해제 (광고) ==========

    /**
     * 해당 페이지의 커스텀 월이 동기화 범위에 부분만 포함되는지 판단
     *
     * 해당 월이 이미 동기화(광고 시청) 되었으면 → false (완전 커버)
     * 미동기화 시 → 커스텀 월 시작이 (현재 - DEFAULT_SYNC_PERIOD_MILLIS) 이전이면 부분 커버
     */
    fun isPagePartiallyCovered(year: Int, month: Int): Boolean {
        val yearMonth = String.format("%04d-%02d", year, month)
        if (yearMonth in _uiState.value.syncedMonths) return false
        val (customMonthStart, _) = DateUtils.getCustomMonthPeriod(
            year, month, _uiState.value.monthStartDay
        )
        val syncCoverageStart = System.currentTimeMillis() - DEFAULT_SYNC_PERIOD_MILLIS
        return customMonthStart < syncCoverageStart
    }

    /** 해당 월이 이미 동기화(광고 시청) 되었는지 확인 */
    fun isMonthSynced(year: Int, month: Int): Boolean {
        val yearMonth = String.format("%04d-%02d", year, month)
        return yearMonth in _uiState.value.syncedMonths
    }

    /** 전체 동기화 광고 프리로드 */
    fun preloadFullSyncAd() {
        rewardAdManager.preloadAd()
    }

    /** 전체 동기화 광고 다이얼로그 표시 */
    fun showFullSyncAdDialog() {
        rewardAdManager.preloadAd()
        _uiState.update { it.copy(showFullSyncAdDialog = true) }
    }

    /** 전체 동기화 광고 다이얼로그 닫기 */
    fun dismissFullSyncAdDialog() {
        _uiState.update { it.copy(showFullSyncAdDialog = false) }
    }

    /** 월별 동기화 해제 (광고 시청 완료 후 호출) — HomeViewModel에 월별 sync 요청 */
    fun unlockFullSync() {
        viewModelScope.launch {
            val state = _uiState.value
            val yearMonth = String.format("%04d-%02d", state.selectedYear, state.selectedMonth)
            settingsDataStore.addSyncedMonth(yearMonth)
            _uiState.update { it.copy(showFullSyncAdDialog = false) }
            val isCurrentMonth = state.selectedYear == DateUtils.getCurrentYear() &&
                    state.selectedMonth == DateUtils.getCurrentMonth()
            val monthLabel = if (isCurrentMonth) "이번달" else "${state.selectedMonth}월"
            snackbarBus.show(context.getString(R.string.full_sync_unlocked_message, monthLabel))

            // HomeViewModel에 해당 달 SMS 동기화 요청
            dataRefreshEvent.requestMonthSync(state.selectedYear, state.selectedMonth)
        }
    }
}
