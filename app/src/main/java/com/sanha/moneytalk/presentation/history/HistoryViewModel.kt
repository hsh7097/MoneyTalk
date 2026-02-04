package com.sanha.moneytalk.presentation.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sanha.moneytalk.data.local.entity.ExpenseEntity
import com.sanha.moneytalk.data.repository.ExpenseRepository
import com.sanha.moneytalk.util.DateUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HistoryUiState(
    val isLoading: Boolean = false,
    val expenses: List<ExpenseEntity> = emptyList(),
    val selectedCategory: String? = null,
    val errorMessage: String? = null
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val expenseRepository: ExpenseRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    init {
        loadAllExpenses()
    }

    private fun loadAllExpenses() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            expenseRepository.getAllExpenses()
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

    fun filterByCategory(category: String?) {
        viewModelScope.launch {
            _uiState.update { it.copy(selectedCategory = category, isLoading = true) }

            if (category == null) {
                loadAllExpenses()
            } else {
                expenseRepository.getExpensesByCategory(category)
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
    }

    fun deleteExpense(expense: ExpenseEntity) {
        viewModelScope.launch {
            try {
                expenseRepository.delete(expense)
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message) }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
