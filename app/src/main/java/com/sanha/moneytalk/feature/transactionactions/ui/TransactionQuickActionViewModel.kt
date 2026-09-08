package com.sanha.moneytalk.feature.transactionactions.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sanha.moneytalk.R
import com.sanha.moneytalk.feature.transactionactions.data.TransactionQuickActionService
import com.sanha.moneytalk.feature.transactionactions.model.TransactionQuickUpdateResult
import com.sanha.moneytalk.feature.transactionactions.model.TransactionTarget
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class TransactionQuickActionViewModel @Inject constructor(
    private val service: TransactionQuickActionService
) : ViewModel() {
    private val _uiState = MutableStateFlow(TransactionQuickActionUiState())
    val uiState = _uiState.asStateFlow()
    private val messages = Channel<Int>(Channel.BUFFERED)
    val completedMessages = messages.receiveAsFlow()
    private var loadJob: Job? = null
    private var loadGeneration = 0L

    fun open(target: TransactionTarget) {
        if (_uiState.value.isSaving) return
        loadJob?.cancel()
        val generation = ++loadGeneration
        _uiState.value = TransactionQuickActionUiState(target = target, isLoading = true)
        loadJob = viewModelScope.launch {
            try {
                val transaction = service.load(target)
                if (generation != loadGeneration) return@launch
                if (transaction == null) {
                    messages.send(R.string.quick_transaction_missing)
                    _uiState.value = TransactionQuickActionUiState()
                    return@launch
                }
                val categories = service.categories(transaction.categoryType)
                if (generation != loadGeneration) return@launch
                _uiState.value = TransactionQuickActionUiState(
                    target = target, transaction = transaction, categories = categories,
                    category = transaction.category, isFixed = transaction.isFixed,
                    isExcludedFromStats = transaction.isExcludedFromStats
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (generation != loadGeneration) return@launch
                _uiState.update { it.copy(isLoading = false, error = R.string.quick_transaction_load_failed) }
            }
        }
    }

    fun dismiss() {
        if (_uiState.value.isSaving) return
        loadGeneration++
        loadJob?.cancel()
        _uiState.value = TransactionQuickActionUiState()
    }

    fun showCategories(show: Boolean) = edit { it.copy(showCategories = show) }
    fun selectCategory(category: String) = edit { it.copy(category = category, showCategories = false) }
    fun setFixed(fixed: Boolean) = edit { it.copy(isFixed = fixed) }
    fun setStatsExcluded(excluded: Boolean) = edit { it.copy(isExcludedFromStats = excluded) }
    fun confirmDelete(confirm: Boolean) = edit { it.copy(confirmDelete = confirm) }

    private fun edit(transform: (TransactionQuickActionUiState) -> TransactionQuickActionUiState) {
        if (!_uiState.value.isSaving) _uiState.update { transform(it).copy(error = null) }
    }

    fun save() {
        val state = _uiState.value
        val target = state.target ?: return
        if (state.isLoading || state.isSaving || !state.hasChanges) return
        mutate {
            when (service.update(target, state.patch)) {
                is TransactionQuickUpdateResult.Updated -> complete(R.string.quick_transaction_saved)
                TransactionQuickUpdateResult.Missing -> complete(R.string.quick_transaction_missing)
                TransactionQuickUpdateResult.InvalidCategory -> fail(R.string.quick_transaction_invalid_category)
                TransactionQuickUpdateResult.UnsupportedField -> fail(R.string.quick_transaction_save_failed)
            }
        }
    }

    fun delete() {
        val state = _uiState.value
        val target = state.target ?: return
        if (state.isLoading || state.isSaving || !state.confirmDelete) return
        mutate {
            complete(if (service.delete(target)) R.string.quick_transaction_deleted else R.string.quick_transaction_missing)
        }
    }

    private fun mutate(action: suspend () -> Unit) {
        _uiState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            try {
                action()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                fail(R.string.quick_transaction_save_failed)
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    private suspend fun complete(message: Int) {
        _uiState.value = TransactionQuickActionUiState()
        messages.send(message)
    }

    private fun fail(message: Int) {
        _uiState.update { it.copy(error = message, confirmDelete = false) }
    }
}
