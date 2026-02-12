package com.sanha.moneytalk.feature.history.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sanha.moneytalk.R
import com.sanha.moneytalk.core.database.entity.ExpenseEntity
import com.sanha.moneytalk.core.database.entity.IncomeEntity
import com.sanha.moneytalk.core.datastore.SettingsDataStore
import com.sanha.moneytalk.core.model.Category
import com.sanha.moneytalk.core.ui.AppSnackbarBus
import com.sanha.moneytalk.core.ui.component.transaction.card.ExpenseTransactionCardInfo
import com.sanha.moneytalk.core.ui.component.transaction.card.IncomeTransactionCardInfo
import com.sanha.moneytalk.core.ui.component.transaction.card.TransactionCardInfo
import com.sanha.moneytalk.core.ui.component.transaction.header.TransactionGroupHeaderInfo
import com.sanha.moneytalk.core.util.DataRefreshEvent
import com.sanha.moneytalk.feature.home.data.CategoryClassifierService
import com.sanha.moneytalk.feature.home.data.ExpenseRepository
import com.sanha.moneytalk.feature.home.data.IncomeRepository
import com.sanha.moneytalk.core.util.DateUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*
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
    data class ChangeCategory(val expenseId: Long, val storeName: String, val newCategory: String) : HistoryIntent
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
 * 내역 화면 UI 상태
 *
 * @property isLoading 데이터 로딩 중 여부
 * @property isRefreshing Pull-to-Refresh 진행 중 여부
 * @property expenses 필터링된 지출 내역 목록
 * @property selectedCategory 선택된 카테고리 필터 (null이면 전체)
 * @property selectedYear 선택된 연도
 * @property selectedMonth 선택된 월
 * @property monthStartDay 월 시작일 (1~28, 사용자 설정)
 * @property monthlyTotal 해당 월 총 지출
 * @property dailyTotals 일별 지출 합계 (캘린더 표시용, "yyyy-MM-dd" -> 금액)
 * @property errorMessage 에러 메시지 (null이면 에러 없음)
 * @property searchQuery 검색어
 * @property isSearchMode 검색 모드 여부
 * @property sortOrder 정렬 순서
 * @property showExpenses 지출 표시 여부 (BottomSheet 필터)
 * @property showIncomes 수입 표시 여부 (BottomSheet 필터)
 */
data class HistoryUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val expenses: List<ExpenseEntity> = emptyList(),
    val selectedCategory: String? = null,
    val selectedYear: Int = DateUtils.getCurrentYear(),
    val selectedMonth: Int = DateUtils.getCurrentMonth(),
    val monthStartDay: Int = 1,
    val monthlyTotal: Int = 0,
    val dailyTotals: Map<String, Int> = emptyMap(),
    val errorMessage: String? = null,
    val searchQuery: String = "",
    val isSearchMode: Boolean = false,
    val sortOrder: SortOrder = SortOrder.DATE_DESC,
    val showExpenses: Boolean = true,
    val showIncomes: Boolean = true,
    val incomes: List<IncomeEntity> = emptyList(),
    val monthlyIncomeTotal: Int = 0,
    // 가공된 리스트 데이터 (TransactionListView용)
    val transactionListItems: List<TransactionListItem> = emptyList(),
    // 다이얼로그 상태 (Composable에서 remember 대신 ViewModel에서 관리)
    val selectedExpense: ExpenseEntity? = null,
    val selectedIncome: IncomeEntity? = null
) {
    /** 필터 적용된 지출 총합 (필터 활성 시 expenses 합계, 비활성 시 월 전체) */
    val filteredExpenseTotal: Int
        get() = if (showExpenses) expenses.sumOf { it.amount } else 0

    /** 필터 적용된 수입 총합 (필터 활성 시 incomes 합계, 비활성 시 0) */
    val filteredIncomeTotal: Int
        get() = if (showIncomes) {
            if (selectedCategory != null) 0 else incomes.sumOf { it.amount }
        } else 0
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
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    /** 현재 실행 중인 데이터 로드 작업 (취소 가능) */
    private var loadJob: Job? = null

    init {
        loadSettings()
        observeDataRefreshEvents()
    }

    /** 내 카드 변경 등 전역 이벤트 감지 */
    private fun observeDataRefreshEvents() {
        viewModelScope.launch {
            dataRefreshEvent.refreshEvent.collect { event ->
                when (event) {
                    DataRefreshEvent.RefreshType.OWNED_CARD_UPDATED,
                    DataRefreshEvent.RefreshType.CATEGORY_UPDATED -> {
                        loadExpenses()
                    }
                    DataRefreshEvent.RefreshType.ALL_DATA_DELETED -> {
                        _uiState.update {
                            it.copy(
                                monthlyTotal = 0,
                                expenses = emptyList(),
                                dailyTotals = emptyMap(),
                                incomes = emptyList(),
                                monthlyIncomeTotal = 0
                            )
                        }
                        loadExpenses()
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
                loadExpenses()
            }
        }
    }

    /**
     * 지출 내역 로드
     * 선택된 월과 필터 조건에 맞는 지출 내역을 조회합니다.
     * 기존 로드 작업이 있으면 취소하고 새로 시작합니다.
     */
    private fun loadExpenses() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            val state = _uiState.value
            val (startTime, endTime) = DateUtils.getCustomMonthPeriod(
                state.selectedYear,
                state.selectedMonth,
                state.monthStartDay
            )

            // 제외 키워드 로드 (필터링용)
            val exclusionKeywords = withContext(Dispatchers.IO) {
                smsExclusionRepository.getAllKeywordStrings()
            }

            // 수입 항상 로드 (목록 모드에서도 수입 표시)
            loadIncomes(exclusionKeywords)

            // 필터링된 지출 내역 로드 (Flow는 Room이 자동으로 IO에서 실행)
            // 대 카테고리 선택 시 소 카테고리도 포함 (예: "식비" → "식비" + "배달")
            val categoryFilter = state.selectedCategory
            val categoriesForFilter = categoryFilter?.let {
                val cat = Category.fromDisplayName(it)
                cat.displayNamesIncludingSub  // 대 카테고리 + 소 카테고리 displayName 목록
            }

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

            // 월별/일별 총액은 제외 키워드 필터 적용 후 계산 (필터링 안된 전체 데이터 기준으로 별도 로드)
            // 카드/카테고리 필터 없이 전체 데이터 기준으로 총액 계산
            try {
                expenseRepository.getExpensesByDateRange(startTime, endTime)
                    .first()
                    .let { allExpensesForTotal ->
                        val filteredForTotal = if (exclusionKeywords.isEmpty()) {
                            allExpensesForTotal
                        } else {
                            allExpensesForTotal.filter { expense ->
                                val smsLower = expense.originalSms?.lowercase()
                                smsLower == null || exclusionKeywords.none { kw -> smsLower.contains(kw) }
                            }
                        }
                        val monthlyTotal = filteredForTotal.sumOf { it.amount }
                        // 일별 총액도 필터링된 데이터 기준으로 계산
                        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.KOREA)
                        val dailyTotalsMap = filteredForTotal
                            .groupBy { dateFormat.format(java.util.Date(it.dateTime)) }
                            .mapValues { (_, expenses) -> expenses.sumOf { it.amount } }
                        val incomeTotal = incomeRepository.getTotalIncomeByDateRange(startTime, endTime)
                        _uiState.update {
                            it.copy(
                                monthlyTotal = monthlyTotal,
                                dailyTotals = dailyTotalsMap,
                                monthlyIncomeTotal = incomeTotal
                            )
                        }
                    }
            } catch (e: Exception) {
                // 총액 로딩 실패 시 무시
            }

            expenseFlow
                .catch { e ->
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = e.message)
                    }
                }
                .collect { allExpenses ->
                    // 제외 키워드 필터 적용
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
                    // 카테고리 필터 활성화 시 수입 항목 제외 (수입은 카테고리가 없으므로)
                    val filteredIncomes = if (currentState.selectedCategory != null) emptyList() else currentState.incomes
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            expenses = sortedExpenses,
                            transactionListItems = buildTransactionListItems(
                                sortedExpenses, filteredIncomes, currentState.sortOrder,
                                currentState.showExpenses, currentState.showIncomes
                            )
                        )
                    }
                }
        }
    }

    /** 정렬 방식에 따라 지출 내역 정렬 */
    private fun sortExpenses(expenses: List<ExpenseEntity>, sortOrder: SortOrder): List<ExpenseEntity> {
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
        val currentExpenses = _uiState.value.expenses
        val sortedExpenses = sortExpenses(currentExpenses, sortOrder)
        _uiState.update { it.copy(sortOrder = sortOrder, expenses = sortedExpenses) }
        updateTransactionListItems()
    }

    /** 특정 년/월로 이동 */
    fun setMonth(year: Int, month: Int) {
        _uiState.update { it.copy(selectedYear = year, selectedMonth = month) }
        loadExpenses()
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

    /** 다음 월로 이동 */
    fun nextMonth() {
        val state = _uiState.value
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
        _uiState.update { it.copy(selectedCategory = category) }
        loadExpenses()
    }

    /** 지출 항목 삭제 */
    fun deleteExpense(expense: ExpenseEntity) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { expenseRepository.delete(expense) }
                loadExpenses()
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
                loadExpenses() // 수입도 함께 다시 로드됨
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
     * Room 매핑도 함께 업데이트하여 동일 가게명에 대해 학습
     */
    fun updateExpenseCategory(expenseId: Long, storeName: String, newCategory: String) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    categoryClassifierService.updateExpenseCategory(expenseId, storeName, newCategory)
                }
                loadExpenses() // 화면 새로고침
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
            loadExpensesSync()
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
        loadExpenses()
    }

    /** 검색어 변경 및 검색 실행 */
    fun search(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        if (query.isBlank()) {
            loadExpenses()
        } else {
            searchExpenses(query)
        }
    }

    /** 지출 내역 검색 */
    private fun searchExpenses(query: String) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            try {
                val results = withContext(Dispatchers.IO) {
                    expenseRepository.searchExpenses(query)
                }
                val currentState = _uiState.value
                val sortedResults = sortExpenses(results, currentState.sortOrder)
                val filteredIncomes = if (currentState.selectedCategory != null) emptyList() else currentState.incomes
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        expenses = sortedResults,
                        monthlyTotal = results.sumOf { e -> e.amount },
                        transactionListItems = buildTransactionListItems(
                            sortedResults, filteredIncomes, currentState.sortOrder,
                            currentState.showExpenses, currentState.showIncomes
                        )
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = e.message)
                }
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
                loadExpenses()
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
                loadExpenses()
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
                loadExpenses()
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
        loadExpenses()
    }

    /** 수입 내역 로드 */
    private fun loadIncomes(exclusionKeywords: Set<String> = emptySet()) {
        viewModelScope.launch {
            val state = _uiState.value
            val (startTime, endTime) = DateUtils.getCustomMonthPeriod(
                state.selectedYear,
                state.selectedMonth,
                state.monthStartDay
            )

            try {
                incomeRepository.getIncomesByDateRange(startTime, endTime)
                    .collect { allIncomes ->
                        // 제외 키워드 필터 적용
                        val incomes = if (exclusionKeywords.isEmpty()) {
                            allIncomes
                        } else {
                            allIncomes.filter { income ->
                                val smsLower = income.originalSms?.lowercase()
                                smsLower == null || exclusionKeywords.none { kw -> smsLower.contains(kw) }
                            }
                        }
                        val total = incomes.sumOf { it.amount }
                        val sortedIncomes = incomes.sortedByDescending { inc -> inc.dateTime }
                        _uiState.update {
                            it.copy(
                                incomes = sortedIncomes,
                                monthlyIncomeTotal = total
                            )
                        }
                        // 수입 갱신 후 transactionListItems도 재빌드
                        updateTransactionListItems()
                    }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "수입 로드 실패: ${e.message}") }
            }
        }
    }

    /**
     * 지출 내역 동기 로드 (refresh에서 사용)
     */
    private suspend fun loadExpensesSync() {
        val state = _uiState.value
        val (startTime, endTime) = DateUtils.getCustomMonthPeriod(
            state.selectedYear,
            state.selectedMonth,
            state.monthStartDay
        )

        try {
            val result = withContext(Dispatchers.IO) {
                // 제외 키워드 로드
                val exclusionKeywords = smsExclusionRepository.getAllKeywordStrings()
                // 전체 데이터 로드 후 제외 키워드 필터 적용하여 총액 계산
                val allExpenses = expenseRepository.getExpensesByDateRangeOnce(startTime, endTime)
                val filteredForTotal = if (exclusionKeywords.isEmpty()) {
                    allExpenses
                } else {
                    allExpenses.filter { expense ->
                        val smsLower = expense.originalSms?.lowercase()
                        smsLower == null || exclusionKeywords.none { kw -> smsLower.contains(kw) }
                    }
                }
                val total = filteredForTotal.sumOf { it.amount }
                // 일별 총액도 필터링된 데이터 기준
                val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.KOREA)
                val map = filteredForTotal
                    .groupBy { dateFormat.format(java.util.Date(it.dateTime)) }
                    .mapValues { (_, expenses) -> expenses.sumOf { it.amount } }
                // 대 카테고리 선택 시 소 카테고리도 포함
                val syncCategoryFilter = state.selectedCategory?.let { catName ->
                    Category.fromDisplayName(catName).displayNamesIncludingSub
                }
                val expenses = filteredForTotal
                    .filter { expense ->
                        syncCategoryFilter == null || expense.category in syncCategoryFilter
                    }
                // 정렬 적용
                val sorted = sortExpenses(expenses, state.sortOrder)
                Triple(total, map, sorted)
            }

            val state2 = _uiState.value
            // 카테고리 필터 활성화 시 수입 항목 제외
            val filteredIncomes = if (state2.selectedCategory != null) emptyList() else state2.incomes
            _uiState.update {
                it.copy(
                    monthlyTotal = result.first,
                    dailyTotals = result.second,
                    expenses = result.third,
                    isLoading = false,
                    transactionListItems = buildTransactionListItems(
                        result.third, filteredIncomes, state2.sortOrder,
                        state2.showExpenses, state2.showIncomes
                    )
                )
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(errorMessage = e.message, isLoading = false) }
        }
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
                updateExpenseCategory(intent.expenseId, intent.storeName, intent.newCategory)
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

    /** transactionListItems 갱신 */
    private fun updateTransactionListItems() {
        val state = _uiState.value
        // 카테고리 필터 활성화 시 수입 항목 제외
        val filteredIncomes = if (state.selectedCategory != null) emptyList() else state.incomes
        val items = buildTransactionListItems(
            state.expenses, filteredIncomes, state.sortOrder,
            state.showExpenses, state.showIncomes
        )
        _uiState.update { it.copy(transactionListItems = items) }
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

        // 수입만 보기 모드: 날짜별 그룹핑
        if (!showExpenses && showIncomes) {
            return buildIncomeDayGroups(filteredIncomes)
        }

        return when (sortOrder) {
            SortOrder.DATE_DESC -> buildDateDescItems(filteredExpenses, filteredIncomes)
            SortOrder.AMOUNT_DESC -> buildAmountDescItems(filteredExpenses)
            SortOrder.STORE_FREQ -> buildStoreFreqItems(filteredExpenses)
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
            val dayOfWeekStr = context.getString(getDayOfWeekResId(calendar.get(Calendar.DAY_OF_WEEK)))
            val title = context.getString(R.string.history_day_header, dayOfMonth, dayOfWeekStr)

            items.add(TransactionListItem.Header(
                title = title,
                expenseTotal = dailyExpenseTotal,
                incomeTotal = dailyIncomeTotal
            ))
            // 수입+지출을 시간 최신순으로 통합 정렬
            val merged = dayExpenses.map { it.dateTime to TransactionListItem.ExpenseItem(it) } +
                dayIncomes.map { it.dateTime to TransactionListItem.IncomeItem(it) }
            merged.sortedByDescending { it.first }
                .forEach { items.add(it.second) }
        }

        return items
    }

    /** AMOUNT_DESC: 금액 높은순 플랫 리스트 */
    private fun buildAmountDescItems(expenses: List<ExpenseEntity>): List<TransactionListItem> {
        val items = mutableListOf<TransactionListItem>()
        items.add(TransactionListItem.Header(
            title = "${context.getString(R.string.history_sort_amount)} (${expenses.size}${context.getString(R.string.history_count_suffix)})",
            expenseTotal = expenses.sumOf { it.amount }
        ))
        expenses.forEach { items.add(TransactionListItem.ExpenseItem(it)) }
        return items
    }

    /** STORE_FREQ: 사용처별 그룹핑 */
    private fun buildStoreFreqItems(expenses: List<ExpenseEntity>): List<TransactionListItem> {
        val items = mutableListOf<TransactionListItem>()
        val storeGroups = expenses.groupBy { it.storeName }
            .entries
            .sortedByDescending { it.value.size }

        storeGroups.forEach { (storeName, storeExpenses) ->
            val storeTotal = storeExpenses.sumOf { it.amount }
            items.add(TransactionListItem.Header(
                title = "$storeName (${storeExpenses.size}${context.getString(R.string.history_visit_suffix)})",
                expenseTotal = storeTotal
            ))
            storeExpenses.sortedByDescending { it.dateTime }
                .forEach { items.add(TransactionListItem.ExpenseItem(it)) }
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
            val dayOfWeekStr = context.getString(getDayOfWeekResId(calendar.get(Calendar.DAY_OF_WEEK)))
            val title = context.getString(R.string.history_day_header, dayOfMonth, dayOfWeekStr)

            items.add(TransactionListItem.Header(
                title = title,
                incomeTotal = dailyTotal
            ))
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
}
