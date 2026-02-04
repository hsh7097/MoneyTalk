package com.sanha.moneytalk.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sanha.moneytalk.data.local.SettingsDataStore
import com.sanha.moneytalk.data.repository.ClaudeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val apiKey: String = "",
    val hasApiKey: Boolean = false,
    val monthlyIncome: Int = 0,
    val isLoading: Boolean = false,
    val message: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val claudeRepository: ClaudeRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            // API 키 로드
            settingsDataStore.apiKeyFlow.collect { key ->
                _uiState.update {
                    it.copy(
                        apiKey = maskApiKey(key),
                        hasApiKey = key.isNotBlank()
                    )
                }
            }
        }

        viewModelScope.launch {
            // 월 수입 로드
            settingsDataStore.monthlyIncomeFlow.collect { income ->
                _uiState.update { it.copy(monthlyIncome = income) }
            }
        }
    }

    // API 키 마스킹 (보안)
    private fun maskApiKey(key: String): String {
        return if (key.length > 20) {
            "${key.take(10)}...${key.takeLast(4)}"
        } else if (key.isNotBlank()) {
            "${key.take(5)}..."
        } else {
            ""
        }
    }

    fun saveApiKey(key: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                claudeRepository.setApiKey(key)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        hasApiKey = true,
                        apiKey = maskApiKey(key),
                        message = "API 키가 저장되었습니다"
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        message = "저장 실패: ${e.message}"
                    )
                }
            }
        }
    }

    fun saveMonthlyIncome(income: Int) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                settingsDataStore.saveMonthlyIncome(income)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        monthlyIncome = income,
                        message = "월 수입이 저장되었습니다"
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        message = "저장 실패: ${e.message}"
                    )
                }
            }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }
}
