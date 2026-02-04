package com.sanha.moneytalk.presentation.home

import android.content.ContentResolver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    private val smsReader: SmsReader
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

                // 최근 지출 5건
                val recentExpenses = expenseRepository.getRecentExpenses(5)

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

    fun syncSmsMessages(contentResolver: ContentResolver) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true) }

            try {
                val smsList = smsReader.readAllCardSms(contentResolver)
                var newCount = 0

                for (sms in smsList) {
                    // 이미 처리된 문자인지 확인
                    if (expenseRepository.existsBySmsId(sms.id)) {
                        continue
                    }

                    // Claude로 분석
                    val result = claudeRepository.analyzeSms(sms.body)

                    result.onSuccess { analysis ->
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

                // 데이터 새로고침
                loadData()

                _uiState.update {
                    it.copy(
                        isSyncing = false,
                        errorMessage = if (newCount > 0) "${newCount}건의 새 지출이 추가되었습니다" else null
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
        claudeRepository.setApiKey(key)
    }

    fun hasApiKey(): Boolean = claudeRepository.hasApiKey()

    fun refresh() {
        loadData()
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
