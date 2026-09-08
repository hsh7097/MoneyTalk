package com.sanha.moneytalk.feature.categoryreview.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sanha.moneytalk.feature.categoryreview.data.CategoryReviewRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CategoryReviewViewModel @Inject constructor(
    private val repository: CategoryReviewRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow<CategoryReviewUiState>(CategoryReviewUiState.Loading)
    val uiState: StateFlow<CategoryReviewUiState> = _uiState.asStateFlow()
    private var observation: Job? = null

    init {
        refresh()
    }

    /** 편집에서 돌아오거나 재시도할 때 노출 설정까지 다시 읽는다. */
    fun refresh() {
        observation?.cancel()
        if (_uiState.value !is CategoryReviewUiState.Content) {
            _uiState.value = CategoryReviewUiState.Loading
        }
        observation = viewModelScope.launch {
            try {
                repository.observeExpenses().collect { expenses ->
                    _uiState.value = CategoryReviewUiState.Content.from(expenses)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _uiState.value = CategoryReviewUiState.Error
            }
        }
    }
}
