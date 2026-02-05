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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

data class HistoryUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false, // Pull-to-Refresh 상태
    val expenses: List<ExpenseEntity> = emptyList(),
    val selectedCategory: String? = null,
    val selectedCardName: String? = null,
    val selectedYear: Int = DateUtils.getCurrentYear(),
    val selectedMonth: Int = DateUtils.getCurrentMonth(),
    val monthStartDay: Int = 1,
    val cardNames: List<String> = emptyList(),
    val monthlyTotal: Int = 0,
    val dailyTotals: Map<String, Int> = emptyMap(), // "yyyy-MM-dd" -> amount
    val errorMessage: String? = null
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val expenseRepository: ExpenseRepository,
    private val settingsDataStore: SettingsDataStore,
    private val categoryClassifierService: CategoryClassifierService
) : ViewModel() {

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    init {
        loadSettings()
        loadCardNames()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            settingsDataStore.monthStartDayFlow.collect { startDay ->
                _uiState.update { it.copy(monthStartDay = startDay) }
                loadExpenses()
            }
        }
    }

    private fun loadCardNames() {
        viewModelScope.launch {
            try {
                val cardNames = expenseRepository.getAllCardNames()
                _uiState.update { it.copy(cardNames = cardNames) }
            } catch (e: Exception) {
                // 카드 목록 로딩 실패 시 무시
            }
        }
    }

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
                val monthlyTotal = expenseRepository.getTotalExpenseByDateRange(startTime, endTime)
                val dailySums = expenseRepository.getDailyTotals(startTime, endTime)
                val dailyTotalsMap = dailySums.associate { it.date to it.total }
                _uiState.update { it.copy(monthlyTotal = monthlyTotal, dailyTotals = dailyTotalsMap) }
            } catch (e: Exception) {
                // 총액 로딩 실패 시 무시
            }

            // 필터링된 지출 내역 로드
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
                    _uiState.update {
                        it.copy(isLoading = false, expenses = expenses)
                    }
                }
        }
    }

    fun setMonth(year: Int, month: Int) {
        _uiState.update { it.copy(selectedYear = year, selectedMonth = month) }
        loadExpenses()
    }

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

    fun filterByCategory(category: String?) {
        _uiState.update { it.copy(selectedCategory = category) }
        loadExpenses()
    }

    fun filterByCardName(cardName: String?) {
        _uiState.update { it.copy(selectedCardName = cardName) }
        loadExpenses()
    }

    fun deleteExpense(expense: ExpenseEntity) {
        viewModelScope.launch {
            try {
                expenseRepository.delete(expense)
                // 삭제 후 새로고침
                loadExpenses()
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message) }
            }
        }
    }

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
                categoryClassifierService.updateExpenseCategory(expenseId, storeName, newCategory)
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
            val monthlyTotal = expenseRepository.getTotalExpenseByDateRange(startTime, endTime)
            val dailySums = expenseRepository.getDailyTotals(startTime, endTime)
            val dailyTotalsMap = dailySums.associate { it.date to it.total }
            val expenses = expenseRepository.getExpensesByDateRangeOnce(startTime, endTime)
                .let { list ->
                    // 필터 적용
                    list.filter { expense ->
                        (state.selectedCardName == null || expense.cardName == state.selectedCardName) &&
                        (state.selectedCategory == null || expense.category == state.selectedCategory)
                    }
                }

            _uiState.update {
                it.copy(
                    monthlyTotal = monthlyTotal,
                    dailyTotals = dailyTotalsMap,
                    expenses = expenses,
                    isLoading = false
                )
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(errorMessage = e.message, isLoading = false) }
        }
    }
}
