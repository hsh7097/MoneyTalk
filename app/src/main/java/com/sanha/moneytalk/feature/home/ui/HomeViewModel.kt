package com.sanha.moneytalk.feature.home.ui

import android.content.ContentResolver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sanha.moneytalk.core.datastore.SettingsDataStore
import com.sanha.moneytalk.core.database.dao.CategorySum
import com.sanha.moneytalk.core.database.entity.ExpenseEntity
import com.sanha.moneytalk.feature.chat.data.ClaudeRepository
import com.sanha.moneytalk.feature.home.data.ExpenseRepository
import com.sanha.moneytalk.feature.home.data.IncomeRepository
import com.sanha.moneytalk.core.util.DateUtils
import com.sanha.moneytalk.core.util.SmsParser
import com.sanha.moneytalk.core.util.SmsReader
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val isLoading: Boolean = false,
    val selectedYear: Int = DateUtils.getCurrentYear(),
    val selectedMonth: Int = DateUtils.getCurrentMonth(),
    val monthStartDay: Int = 1,
    val monthlyIncome: Int = 0,
    val monthlyExpense: Int = 0,
    val remainingBudget: Int = 0,
    val categoryExpenses: List<CategorySum> = emptyList(),
    val recentExpenses: List<ExpenseEntity> = emptyList(),
    val periodLabel: String = "",
    val errorMessage: String? = null,
    val isSyncing: Boolean = false
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val expenseRepository: ExpenseRepository,
    private val incomeRepository: IncomeRepository,
    private val claudeRepository: ClaudeRepository,
    private val smsReader: SmsReader,
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            val monthStartDay = settingsDataStore.getMonthStartDay()

            // 현재 커스텀 월 기간 계산
            val (startTs, _) = DateUtils.getCurrentCustomMonthPeriod(monthStartDay)
            val calendar = java.util.Calendar.getInstance().apply { timeInMillis = startTs }
            val year = calendar.get(java.util.Calendar.YEAR)
            val month = calendar.get(java.util.Calendar.MONTH) + 1

            _uiState.update {
                it.copy(
                    monthStartDay = monthStartDay,
                    selectedYear = year,
                    selectedMonth = month
                )
            }
            loadData()
        }
    }

    private fun loadData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            try {
                val state = _uiState.value
                val (monthStart, monthEnd) = DateUtils.getCustomMonthPeriod(
                    state.selectedYear,
                    state.selectedMonth,
                    state.monthStartDay
                )

                // 기간 레이블 생성
                val periodLabel = DateUtils.formatCustomMonthPeriod(
                    state.selectedYear,
                    state.selectedMonth,
                    state.monthStartDay
                )

                // 이번 기간 지출 합계
                val totalExpense = expenseRepository.getTotalExpenseByDateRange(monthStart, monthEnd)

                // 이번 기간 수입 합계
                val totalIncome = incomeRepository.getTotalIncomeByDateRange(monthStart, monthEnd)

                // 카테고리별 지출
                val categoryExpenses = expenseRepository.getExpenseSumByCategory(monthStart, monthEnd)

                // 최근 지출 20건
                val recentExpenses = expenseRepository.getRecentExpenses(20)

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        periodLabel = periodLabel,
                        monthlyIncome = totalIncome,
                        monthlyExpense = totalExpense,
                        remainingBudget = totalIncome - totalExpense,
                        categoryExpenses = categoryExpenses,
                        recentExpenses = recentExpenses
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message
                    )
                }
            }
        }
    }

    fun previousMonth() {
        val state = _uiState.value
        var newYear = state.selectedYear
        var newMonth = state.selectedMonth - 1
        if (newMonth < 1) {
            newMonth = 12
            newYear -= 1
        }
        _uiState.update { it.copy(selectedYear = newYear, selectedMonth = newMonth) }
        loadData()
    }

    fun nextMonth() {
        val state = _uiState.value
        var newYear = state.selectedYear
        var newMonth = state.selectedMonth + 1
        if (newMonth > 12) {
            newMonth = 1
            newYear += 1
        }
        _uiState.update { it.copy(selectedYear = newYear, selectedMonth = newMonth) }
        loadData()
    }

    fun syncSmsMessages(contentResolver: ContentResolver, forceFullSync: Boolean = false) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true) }

            try {
                // 마지막 동기화 시간 가져오기
                val lastSyncTime = if (forceFullSync) 0L else settingsDataStore.getLastSyncTime()
                val currentTime = System.currentTimeMillis()

                android.util.Log.e("sanha", "=== syncSmsMessages 시작 ===")
                android.util.Log.e("sanha", "forceFullSync: $forceFullSync, lastSyncTime: $lastSyncTime")
                android.util.Log.e("sanha", "lastSyncTime 날짜: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.KOREA).format(java.util.Date(lastSyncTime))}")

                // 마지막 동기화 이후의 SMS만 가져오기 (증분 동기화)
                val smsList = if (lastSyncTime > 0) {
                    android.util.Log.e("sanha", "증분 동기화 모드")
                    smsReader.readCardSmsByDateRange(contentResolver, lastSyncTime, currentTime)
                } else {
                    android.util.Log.e("sanha", "전체 동기화 모드")
                    smsReader.readAllCardSms(contentResolver)
                }

                var newCount = 0

                for (sms in smsList) {
                    // 이미 처리된 문자인지 확인
                    if (expenseRepository.existsBySmsId(sms.id)) {
                        continue
                    }

                    // 로컬 정규식으로 파싱 (API 호출 없음)
                    val analysis = SmsParser.parseSms(sms.body, sms.date)

                    // 금액이 0보다 큰 경우에만 저장
                    if (analysis.amount > 0) {
                        val expense = ExpenseEntity(
                            amount = analysis.amount,
                            storeName = analysis.storeName,
                            category = analysis.category,
                            cardName = analysis.cardName,
                            dateTime = DateUtils.parseDateTime(analysis.dateTime),
                            originalSms = sms.body,
                            smsId = sms.id
                        )
                        expenseRepository.insert(expense)
                        newCount++
                    }
                }

                // 마지막 동기화 시간 저장
                settingsDataStore.saveLastSyncTime(currentTime)

                // 데이터 새로고침
                loadData()

                _uiState.update {
                    it.copy(
                        isSyncing = false,
                        errorMessage = if (newCount > 0) "${newCount}건의 새 지출이 추가되었습니다" else "새로운 지출이 없습니다"
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSyncing = false,
                        errorMessage = "동기화 실패: ${e.message}"
                    )
                }
            }
        }
    }

    fun setApiKey(key: String) {
        viewModelScope.launch {
            claudeRepository.setApiKey(key)
        }
    }

    fun hasApiKey(callback: (Boolean) -> Unit) {
        viewModelScope.launch {
            callback(claudeRepository.hasApiKey())
        }
    }

    fun refresh() {
        loadSettings()
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
