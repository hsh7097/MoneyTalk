package com.sanha.moneytalk.feature.weeklyevidence.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sanha.moneytalk.core.database.OwnedCardRepository
import com.sanha.moneytalk.core.database.SmsExclusionRepository
import com.sanha.moneytalk.core.util.DataRefreshEvent
import com.sanha.moneytalk.feature.home.data.ExpenseRepository
import com.sanha.moneytalk.feature.weeklyevidence.WeeklyEvidenceFilter
import com.sanha.moneytalk.feature.weeklyevidence.WeeklyEvidenceRecords
import com.sanha.moneytalk.feature.weeklyevidence.WeeklyEvidenceRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

enum class WeeklyEvidencePeriod { RECENT, PREVIOUS }

data class WeeklyEvidenceUiState(
    val request: WeeklyEvidenceRequest? = null,
    val records: WeeklyEvidenceRecords = WeeklyEvidenceRecords(),
    val selectedPeriod: WeeklyEvidencePeriod = WeeklyEvidencePeriod.RECENT,
    val isLoading: Boolean = true,
    val hasError: Boolean = false
)

@HiltViewModel
class WeeklyEvidenceViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val expenseRepository: ExpenseRepository,
    private val ownedCardRepository: OwnedCardRepository,
    private val smsExclusionRepository: SmsExclusionRepository,
    private val dataRefreshEvent: DataRefreshEvent
) : ViewModel() {
    private val request = runCatching {
        WeeklyEvidenceRequest(
            category = savedStateHandle.get<String>(WeeklyEvidenceActivity.CATEGORY),
            recentStart = LocalDate.parse(savedStateHandle.get<String>(WeeklyEvidenceActivity.RECENT_START)),
            recentEndInclusive = LocalDate.parse(savedStateHandle.get<String>(WeeklyEvidenceActivity.RECENT_END)),
            previousStart = LocalDate.parse(savedStateHandle.get<String>(WeeklyEvidenceActivity.PREVIOUS_START)),
            previousEndInclusive = LocalDate.parse(savedStateHandle.get<String>(WeeklyEvidenceActivity.PREVIOUS_END)),
            asOfMillis = requireNotNull(savedStateHandle.get<Long>(WeeklyEvidenceActivity.AS_OF)),
            timeZoneId = requireNotNull(savedStateHandle.get<String>(WeeklyEvidenceActivity.TIME_ZONE)),
            initiallyRecent = savedStateHandle.get<Boolean>(WeeklyEvidenceActivity.INITIALLY_RECENT) ?: true
        ).also { it.zoneId }
    }.getOrNull()
    private val _uiState = MutableStateFlow(WeeklyEvidenceUiState(
        request = request,
        selectedPeriod = if (request?.initiallyRecent != false) WeeklyEvidencePeriod.RECENT else WeeklyEvidencePeriod.PREVIOUS
    ))
    val uiState = _uiState.asStateFlow()
    private var loadJob: Job? = null

    init {
        reload()
        viewModelScope.launch { dataRefreshEvent.refreshEvent.collect { reload() } }
    }

    fun selectPeriod(period: WeeklyEvidencePeriod) {
        _uiState.update { it.copy(selectedPeriod = period) }
    }

    fun reload() {
        loadJob?.cancel()
        val request = request
        if (request == null) {
            _uiState.update { it.copy(isLoading = false, hasError = true) }
            return
        }
        loadJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, hasError = false) }
            try {
                val (excludedCards, keywords) = withContext(Dispatchers.IO) {
                    ownedCardRepository.getExcludedCardNames() to smsExclusionRepository.getAllKeywordStrings()
                }
                expenseRepository.getExpensesByDateRange(request.queryStartMillis, request.queryEndMillis)
                    .collect { expenses ->
                        val records = withContext(Dispatchers.Default) {
                            WeeklyEvidenceFilter.filter(expenses, request, excludedCards, keywords)
                        }
                        _uiState.update { it.copy(records = records, isLoading = false, hasError = false) }
                    }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _uiState.update { it.copy(isLoading = false, hasError = true) }
            }
        }
    }
}
