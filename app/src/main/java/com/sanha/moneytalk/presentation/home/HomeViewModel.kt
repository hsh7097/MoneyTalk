package com.sanha.moneytalk.presentation.home

import android.content.ContentResolver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sanha.moneytalk.data.local.SettingsDataStore
import com.sanha.moneytalk.data.local.dao.CategorySum
import com.sanha.moneytalk.data.local.entity.ExpenseEntity
import com.sanha.moneytalk.data.repository.ClaudeRepository
import com.sanha.moneytalk.data.repository.ExpenseRepository
import com.sanha.moneytalk.data.repository.IncomeRepository
import com.sanha.moneytalk.domain.model.Category
import com.sanha.moneytalk.util.DateUtils
import com.sanha.moneytalk.util.SmsParser
import com.sanha.moneytalk.util.SmsReader
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val isLoading: Boolean = false,
    val monthlyIncome: Int = 0,
    val monthlyExpense: Int = 0,
    val remainingBudget: Int = 0,
    val categoryExpenses: List<CategorySum> = emptyList(),
    val recentExpenses: List<ExpenseEntity> = emptyList(),
    val aiInsight: String = "",
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

    private val monthStart = DateUtils.getMonthStartTimestamp()
    private val monthEnd = DateUtils.getMonthEndTimestamp()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            try {
                // 이번 달 지출 합계
                val totalExpense = expenseRepository.getTotalExpenseByDateRange(monthStart, monthEnd)

                // 이번 달 수입 합계
                val totalIncome = incomeRepository.getTotalIncomeByDateRange(monthStart, monthEnd)

                // 카테고리별 지출
                val categoryExpenses = expenseRepository.getExpenseSumByCategory(monthStart, monthEnd)

                // 최근 지출 20건
                val recentExpenses = expenseRepository.getRecentExpenses(20)

                _uiState.update {
                    it.copy(
                        isLoading = false,
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

    fun syncSmsMessages(contentResolver: ContentResolver, forceFullSync: Boolean = false) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true) }

            try {
                // 마지막 동기화 시간 가져오기
                val lastSyncTime = if (forceFullSync) 0L else settingsDataStore.getLastSyncTime()
                val currentTime = System.currentTimeMillis()

                // 마지막 동기화 이후의 SMS만 가져오기 (증분 동기화)
                val smsList = if (lastSyncTime > 0) {
                    smsReader.readCardSmsByDateRange(contentResolver, lastSyncTime, currentTime)
                } else {
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
        loadData()
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
