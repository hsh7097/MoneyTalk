package com.sanha.moneytalk.feature.transactionedit.ui

import android.content.Context
import androidx.compose.runtime.Stable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sanha.moneytalk.R
import com.sanha.moneytalk.core.database.entity.ExpenseEntity
import com.sanha.moneytalk.core.database.entity.IncomeEntity
import com.sanha.moneytalk.core.ui.AppSnackbarBus
import com.sanha.moneytalk.core.util.DataRefreshEvent
import com.sanha.moneytalk.feature.home.data.ExpenseRepository
import com.sanha.moneytalk.feature.home.data.IncomeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

@Stable
data class TransactionEditUiState(
    val isNew: Boolean = true,
    val isIncome: Boolean = false,
    val isLoading: Boolean = true,
    val amount: String = "",
    val storeName: String = "",
    val category: String = "기타",
    val cardName: String = "",
    val incomeType: String = "",
    val source: String = "",
    val dateMillis: Long = System.currentTimeMillis(),
    val hour: Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
    val minute: Int = Calendar.getInstance().get(Calendar.MINUTE),
    val memo: String = "",
    val originalSms: String = "",
    val isSaved: Boolean = false,
    val isDeleted: Boolean = false
)

/**
 * 거래 편집/추가 ViewModel.
 *
 * SavedStateHandle로 Intent extra를 수신:
 * - extra_expense_id: 기존 지출 편집 시 ID, -1이면 새 거래
 * - extra_income_id: 기존 수입 편집 시 ID, -1이면 무시
 * - extra_initial_date: 새 거래 추가 시 기본 날짜 (Long)
 */
@HiltViewModel
class TransactionEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val expenseRepository: ExpenseRepository,
    private val incomeRepository: IncomeRepository,
    private val dataRefreshEvent: DataRefreshEvent,
    private val snackbarBus: AppSnackbarBus,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val expenseId: Long = savedStateHandle[TransactionEditActivity.EXTRA_EXPENSE_ID] ?: -1L
    private val incomeId: Long = savedStateHandle[TransactionEditActivity.EXTRA_INCOME_ID] ?: -1L
    private val initialDate: Long = savedStateHandle[TransactionEditActivity.EXTRA_INITIAL_DATE]
        ?: System.currentTimeMillis()

    private val _uiState = MutableStateFlow(TransactionEditUiState())
    val uiState: StateFlow<TransactionEditUiState> = _uiState.asStateFlow()

    /** 원본 entity (수정 시 smsId 등 보존용) */
    private var originalExpenseEntity: ExpenseEntity? = null
    private var originalIncomeEntity: IncomeEntity? = null

    init {
        when {
            incomeId > 0 -> loadIncome(incomeId)
            expenseId > 0 -> loadExpense(expenseId)
            else -> initNewExpense()
        }
    }

    private fun loadExpense(id: Long) {
        viewModelScope.launch {
            val expense = expenseRepository.getExpenseById(id)
            if (expense != null) {
                originalExpenseEntity = expense
                val cal = Calendar.getInstance().apply { timeInMillis = expense.dateTime }
                _uiState.update {
                    it.copy(
                        isNew = false,
                        isIncome = false,
                        isLoading = false,
                        amount = expense.amount.toString(),
                        storeName = expense.storeName,
                        category = expense.category,
                        cardName = expense.cardName,
                        dateMillis = expense.dateTime,
                        hour = cal.get(Calendar.HOUR_OF_DAY),
                        minute = cal.get(Calendar.MINUTE),
                        memo = expense.memo ?: "",
                        originalSms = expense.originalSms
                    )
                }
            } else {
                initNewExpense()
            }
        }
    }

    private fun loadIncome(id: Long) {
        viewModelScope.launch {
            val income = incomeRepository.getIncomeById(id)
            if (income != null) {
                originalIncomeEntity = income
                val cal = Calendar.getInstance().apply { timeInMillis = income.dateTime }
                _uiState.update {
                    it.copy(
                        isNew = false,
                        isIncome = true,
                        isLoading = false,
                        amount = income.amount.toString(),
                        storeName = income.description,
                        incomeType = income.type,
                        source = income.source,
                        dateMillis = income.dateTime,
                        hour = cal.get(Calendar.HOUR_OF_DAY),
                        minute = cal.get(Calendar.MINUTE),
                        memo = income.memo ?: "",
                        originalSms = income.originalSms ?: ""
                    )
                }
            } else {
                initNewExpense()
            }
        }
    }

    private fun initNewExpense() {
        val cal = Calendar.getInstance().apply { timeInMillis = initialDate }
        _uiState.update {
            it.copy(
                isNew = true,
                isLoading = false,
                dateMillis = initialDate,
                hour = cal.get(Calendar.HOUR_OF_DAY),
                minute = cal.get(Calendar.MINUTE)
            )
        }
    }

    fun updateAmount(value: String) {
        _uiState.update { it.copy(amount = value) }
    }

    fun updateStoreName(value: String) {
        _uiState.update { it.copy(storeName = value) }
    }

    fun updateCategory(value: String) {
        _uiState.update { it.copy(category = value) }
    }

    fun updateCardName(value: String) {
        _uiState.update { it.copy(cardName = value) }
    }

    fun updateIncomeType(value: String) {
        _uiState.update { it.copy(incomeType = value) }
    }

    fun updateSource(value: String) {
        _uiState.update { it.copy(source = value) }
    }

    fun updateDate(millis: Long) {
        _uiState.update { it.copy(dateMillis = millis) }
    }

    fun updateTime(hour: Int, minute: Int) {
        _uiState.update { it.copy(hour = hour, minute = minute) }
    }

    fun updateMemo(value: String) {
        _uiState.update { it.copy(memo = value) }
    }

    fun save() {
        val state = _uiState.value
        if (state.isIncome) {
            saveIncome(state)
        } else {
            saveExpense(state)
        }
    }

    private fun saveExpense(state: TransactionEditUiState) {
        val amount = state.amount.replace(",", "").toIntOrNull()
        if (amount == null || amount <= 0 || state.storeName.isBlank()) {
            snackbarBus.show(context.getString(R.string.transaction_edit_input_required))
            return
        }

        val dateTime = buildDateTime(state.dateMillis, state.hour, state.minute)

        viewModelScope.launch {
            try {
                if (state.isNew) {
                    val entity = ExpenseEntity(
                        amount = amount,
                        storeName = state.storeName.trim(),
                        category = state.category,
                        cardName = state.cardName.trim(),
                        dateTime = dateTime,
                        originalSms = "",
                        smsId = "manual_${System.currentTimeMillis()}",
                        memo = state.memo.ifBlank { null }
                    )
                    expenseRepository.insert(entity)
                } else {
                    val orig = originalExpenseEntity ?: return@launch
                    val updated = orig.copy(
                        amount = amount,
                        storeName = state.storeName.trim(),
                        category = state.category,
                        cardName = state.cardName.trim(),
                        dateTime = dateTime,
                        memo = state.memo.ifBlank { null }
                    )
                    expenseRepository.update(updated)
                }
                dataRefreshEvent.emit(DataRefreshEvent.RefreshType.TRANSACTION_ADDED)
                snackbarBus.show(context.getString(R.string.transaction_edit_saved))
                _uiState.update { it.copy(isSaved = true) }
            } catch (e: Exception) {
                snackbarBus.show(context.getString(R.string.transaction_edit_save_failed))
            }
        }
    }

    private fun saveIncome(state: TransactionEditUiState) {
        val amount = state.amount.replace(",", "").toIntOrNull()
        if (amount == null || amount <= 0) {
            snackbarBus.show(context.getString(R.string.transaction_edit_income_input_required))
            return
        }

        val dateTime = buildDateTime(state.dateMillis, state.hour, state.minute)

        viewModelScope.launch {
            try {
                val orig = originalIncomeEntity ?: return@launch
                val updated = orig.copy(
                    amount = amount,
                    type = state.incomeType.trim().ifBlank { orig.type },
                    source = state.source.trim(),
                    description = state.storeName.trim(),
                    dateTime = dateTime,
                    memo = state.memo.ifBlank { null }
                )
                incomeRepository.update(updated)
                dataRefreshEvent.emit(DataRefreshEvent.RefreshType.TRANSACTION_ADDED)
                snackbarBus.show(context.getString(R.string.transaction_edit_saved))
                _uiState.update { it.copy(isSaved = true) }
            } catch (e: Exception) {
                snackbarBus.show(context.getString(R.string.transaction_edit_save_failed))
            }
        }
    }

    fun delete() {
        val state = _uiState.value
        viewModelScope.launch {
            try {
                if (state.isIncome) {
                    if (incomeId <= 0) return@launch
                    incomeRepository.deleteById(incomeId)
                } else {
                    if (expenseId <= 0) return@launch
                    expenseRepository.deleteById(expenseId)
                }
                dataRefreshEvent.emit(DataRefreshEvent.RefreshType.TRANSACTION_ADDED)
                snackbarBus.show(context.getString(R.string.transaction_edit_deleted))
                _uiState.update { it.copy(isDeleted = true) }
            } catch (e: Exception) {
                snackbarBus.show(context.getString(R.string.transaction_edit_delete_failed))
            }
        }
    }

    private fun buildDateTime(dateMillis: Long, hour: Int, minute: Int): Long {
        return Calendar.getInstance().apply {
            timeInMillis = dateMillis
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
}
