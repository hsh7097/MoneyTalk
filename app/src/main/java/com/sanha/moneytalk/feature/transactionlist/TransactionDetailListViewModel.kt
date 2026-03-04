package com.sanha.moneytalk.feature.transactionlist

import androidx.compose.runtime.Stable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sanha.moneytalk.core.database.entity.ExpenseEntity
import com.sanha.moneytalk.core.database.entity.IncomeEntity
import com.sanha.moneytalk.core.util.DataRefreshEvent
import com.sanha.moneytalk.feature.home.data.ExpenseRepository
import com.sanha.moneytalk.feature.home.data.IncomeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject

private const val EXTRA_DATE = "extra_date"

@Stable
data class TransactionDetailListUiState(
    val isLoading: Boolean = true,
    val dateString: String = "",
    val monthStr: String = "",
    val dayNum: Int = 0,
    val expenses: List<ExpenseEntity> = emptyList(),
    val incomes: List<IncomeEntity> = emptyList()
)

/**
 * 날짜별 거래 목록 ViewModel.
 *
 * 특정 날짜의 지출+수입 목록을 조회.
 * DataRefreshEvent 구독으로 다른 화면에서의 변경을 반영.
 */
@HiltViewModel
class TransactionDetailListViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val expenseRepository: ExpenseRepository,
    private val incomeRepository: IncomeRepository,
    private val dataRefreshEvent: DataRefreshEvent
) : ViewModel() {

    private val dateString: String =
        savedStateHandle[EXTRA_DATE] ?: ""

    private val _uiState = MutableStateFlow(TransactionDetailListUiState())
    val uiState: StateFlow<TransactionDetailListUiState> = _uiState.asStateFlow()

    init {
        parseDateAndLoad()
        observeRefreshEvents()
    }

    private fun parseDateAndLoad() {
        val parts = dateString.split("-")
        val monthStr = (parts.getOrNull(1)?.toIntOrNull() ?: 0).toString()
        val dayNum = parts.getOrNull(2)?.toIntOrNull() ?: 0

        _uiState.update {
            it.copy(dateString = dateString, monthStr = monthStr, dayNum = dayNum)
        }

        loadTransactions()
    }

    private fun loadTransactions() {
        viewModelScope.launch {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA)
            val date = try {
                dateFormat.parse(dateString)
            } catch (_: Exception) {
                null
            }

            if (date == null) {
                _uiState.update { it.copy(isLoading = false) }
                return@launch
            }

            val cal = Calendar.getInstance().apply { time = date }
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            val startTime = cal.timeInMillis

            cal.set(Calendar.HOUR_OF_DAY, 23)
            cal.set(Calendar.MINUTE, 59)
            cal.set(Calendar.SECOND, 59)
            cal.set(Calendar.MILLISECOND, 999)
            val endTime = cal.timeInMillis

            val expenses = expenseRepository.getExpensesByDateRangeOnce(startTime, endTime)
                .sortedByDescending { it.dateTime }
            val incomes = incomeRepository.getIncomesByDateRangeOnce(startTime, endTime)
                .sortedByDescending { it.dateTime }

            _uiState.update {
                it.copy(
                    isLoading = false,
                    expenses = expenses,
                    incomes = incomes
                )
            }
        }
    }

    private fun observeRefreshEvents() {
        viewModelScope.launch {
            dataRefreshEvent.refreshEvent.collect {
                loadTransactions()
            }
        }
    }
}
