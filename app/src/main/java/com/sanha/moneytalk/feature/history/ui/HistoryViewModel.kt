package com.sanha.moneytalk.feature.history.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sanha.moneytalk.core.database.dao.DailySum
import com.sanha.moneytalk.core.database.entity.ExpenseEntity
import com.sanha.moneytalk.core.datastore.SettingsDataStore
import com.sanha.moneytalk.feature.home.data.CategoryClassifierService
import com.sanha.moneytalk.feature.home.data.ExpenseRepository
import com.sanha.moneytalk.core.util.DateUtils
import dagger.hilt.android.lifecycle.HiltViewModel
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
 * 내역 화면 UI 상태
 *
 * @property isLoading 데이터 로딩 중 여부
 * @property isRefreshing Pull-to-Refresh 진행 중 여부
 * @property expenses 필터링된 지출 내역 목록
 * @property selectedCategory 선택된 카테고리 필터 (null이면 전체)
 * @property selectedCardName 선택된 카드 필터 (null이면 전체)
 * @property selectedYear 선택된 연도
 * @property selectedMonth 선택된 월
 * @property monthStartDay 월 시작일 (1~28, 사용자 설정)
 * @property cardNames 필터 드롭다운용 카드명 목록
 * @property monthlyTotal 해당 월 총 지출
 * @property dailyTotals 일별 지출 합계 (캘린더 표시용, "yyyy-MM-dd" -> 금액)
 * @property errorMessage 에러 메시지 (null이면 에러 없음)
 * @property searchQuery 검색어
 * @property isSearchMode 검색 모드 여부
 * @property message 사용자 메시지 (토스트용)
 * @property sortOrder 정렬 순서
 */
data class HistoryUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val expenses: List<ExpenseEntity> = emptyList(),
    val selectedCategory: String? = null,
    val selectedCardName: String? = null,
    val selectedYear: Int = DateUtils.getCurrentYear(),
    val selectedMonth: Int = DateUtils.getCurrentMonth(),
    val monthStartDay: Int = 1,
    val cardNames: List<String> = emptyList(),
    val monthlyTotal: Int = 0,
    val dailyTotals: Map<String, Int> = emptyMap(),
    val errorMessage: String? = null,
    val searchQuery: String = "",
    val isSearchMode: Boolean = false,
    val message: String? = null,
    val sortOrder: SortOrder = SortOrder.DATE_DESC
)

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
    private val settingsDataStore: SettingsDataStore,
    private val categoryClassifierService: CategoryClassifierService
) : ViewModel() {

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    /** 현재 실행 중인 데이터 로드 작업 (취소 가능) */
    private var loadJob: Job? = null

    init {
        loadSettings()
        loadCardNames()
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

    /** 필터 드롭다운용 카드명 목록 로드 */
    private fun loadCardNames() {
        viewModelScope.launch {
            try {
                val cardNames = withContext(Dispatchers.IO) {
                    expenseRepository.getAllCardNames()
                }
                _uiState.update { it.copy(cardNames = cardNames) }
            } catch (e: Exception) {
                // 카드 목록 로딩 실패 시 무시
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

            // 월별 총액 및 일별 총액 로드
            try {
                val (monthlyTotal, dailyTotalsMap) = withContext(Dispatchers.IO) {
                    val total = expenseRepository.getTotalExpenseByDateRange(startTime, endTime)
                    val dailySums = expenseRepository.getDailyTotals(startTime, endTime)
                    val map = dailySums.associate { it.date to it.total }
                    Pair(total, map)
                }
                _uiState.update { it.copy(monthlyTotal = monthlyTotal, dailyTotals = dailyTotalsMap) }
            } catch (e: Exception) {
                // 총액 로딩 실패 시 무시
            }

            // 필터링된 지출 내역 로드 (Flow는 Room이 자동으로 IO에서 실행)
            expenseRepository.getExpensesFiltered(
                cardName = state.selectedCardName,
                category = state.selectedCategory,
                startTime = startTime,
                endTime = endTime
            )
                .catch { e ->
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = e.message)
                    }
                }
                .collect { expenses ->
                    val sortedExpenses = sortExpenses(expenses, _uiState.value.sortOrder)
                    _uiState.update {
                        it.copy(isLoading = false, expenses = sortedExpenses)
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

    /** 카드 필터 적용 (null이면 전체) */
    fun filterByCardName(cardName: String?) {
        _uiState.update { it.copy(selectedCardName = cardName) }
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
            loadCardNames()
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
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        expenses = results,
                        monthlyTotal = results.sumOf { e -> e.amount }
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
                _uiState.update { it.copy(message = "지출이 추가되었습니다") }
                loadExpenses()
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "지출 추가 실패: ${e.message}") }
            }
        }
    }

    /** 메시지 초기화 */
    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
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
            val (monthlyTotal, dailyTotalsMap, sortedExpenses) = withContext(Dispatchers.IO) {
                val total = expenseRepository.getTotalExpenseByDateRange(startTime, endTime)
                val dailySums = expenseRepository.getDailyTotals(startTime, endTime)
                val map = dailySums.associate { it.date to it.total }
                val expenses = expenseRepository.getExpensesByDateRangeOnce(startTime, endTime)
                    .let { list ->
                        // 필터 적용
                        list.filter { expense ->
                            (state.selectedCardName == null || expense.cardName == state.selectedCardName) &&
                            (state.selectedCategory == null || expense.category == state.selectedCategory)
                        }
                    }
                // 정렬 적용
                val sorted = sortExpenses(expenses, state.sortOrder)
                Triple(total, map, sorted)
            }

            _uiState.update {
                it.copy(
                    monthlyTotal = monthlyTotal,
                    dailyTotals = dailyTotalsMap,
                    expenses = sortedExpenses,
                    isLoading = false
                )
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(errorMessage = e.message, isLoading = false) }
        }
    }
}
