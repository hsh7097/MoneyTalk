package com.sanha.moneytalk.feature.settings.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sanha.moneytalk.core.datastore.SettingsDataStore
import com.sanha.moneytalk.core.util.BackupData
import com.sanha.moneytalk.core.util.DataBackupManager
import com.sanha.moneytalk.feature.chat.data.ClaudeRepository
import com.sanha.moneytalk.feature.home.data.ExpenseRepository
import com.sanha.moneytalk.feature.home.data.IncomeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val apiKey: String = "",
    val hasApiKey: Boolean = false,
    val monthlyIncome: Int = 0,
    val monthStartDay: Int = 1,
    val isLoading: Boolean = false,
    val message: String? = null,
    val backupJson: String? = null // 백업 데이터 준비 완료 시
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val claudeRepository: ClaudeRepository,
    private val expenseRepository: ExpenseRepository,
    private val incomeRepository: IncomeRepository
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

        viewModelScope.launch {
            // 월 시작일 로드
            settingsDataStore.monthStartDayFlow.collect { day ->
                _uiState.update { it.copy(monthStartDay = day) }
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

    fun saveMonthStartDay(day: Int) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                settingsDataStore.saveMonthStartDay(day)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        monthStartDay = day,
                        message = "월 시작일이 ${day}일로 설정되었습니다"
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

    /**
     * 데이터 백업 준비 (JSON 생성)
     */
    fun prepareBackup() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val expenses = expenseRepository.getAllExpensesOnce()
                val incomes = incomeRepository.getAllIncomesOnce()
                val state = _uiState.value

                val backupJson = DataBackupManager.createBackupJson(
                    expenses = expenses,
                    incomes = incomes,
                    monthlyIncome = state.monthlyIncome,
                    monthStartDay = state.monthStartDay
                )

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        backupJson = backupJson
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        message = "백업 준비 실패: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * 백업 파일로 내보내기
     */
    fun exportBackup(context: Context, uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val backupJson = _uiState.value.backupJson
                    ?: throw Exception("백업 데이터가 준비되지 않았습니다")

                DataBackupManager.exportToUri(context, uri, backupJson)
                    .onSuccess {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                backupJson = null,
                                message = "백업이 완료되었습니다"
                            )
                        }
                    }
                    .onFailure { e ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                message = "백업 실패: ${e.message}"
                            )
                        }
                    }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        message = "백업 실패: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * 백업 파일에서 복원
     */
    fun importBackup(context: Context, uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                DataBackupManager.importFromUri(context, uri)
                    .onSuccess { backupData ->
                        restoreData(backupData)
                    }
                    .onFailure { e ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                message = "복원 실패: ${e.message}"
                            )
                        }
                    }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        message = "복원 실패: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * 백업 데이터 복원 실행
     */
    private suspend fun restoreData(backupData: BackupData) {
        try {
            // 설정 복원
            settingsDataStore.saveMonthlyIncome(backupData.settings.monthlyIncome)
            settingsDataStore.saveMonthStartDay(backupData.settings.monthStartDay)

            // 지출 데이터 복원
            val expenses = DataBackupManager.convertToExpenseEntities(backupData.expenses)
            if (expenses.isNotEmpty()) {
                expenseRepository.insertAll(expenses)
            }

            // 수입 데이터 복원
            val incomes = DataBackupManager.convertToIncomeEntities(backupData.incomes)
            if (incomes.isNotEmpty()) {
                incomeRepository.insertAll(incomes)
            }

            val expenseCount = expenses.size
            val incomeCount = incomes.size

            _uiState.update {
                it.copy(
                    isLoading = false,
                    monthlyIncome = backupData.settings.monthlyIncome,
                    monthStartDay = backupData.settings.monthStartDay,
                    message = "복원 완료: 지출 ${expenseCount}건, 수입 ${incomeCount}건"
                )
            }
        } catch (e: Exception) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    message = "복원 실패: ${e.message}"
                )
            }
        }
    }

    /**
     * 모든 데이터 삭제
     */
    fun deleteAllData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                // 지출 데이터 삭제
                expenseRepository.deleteAll()
                // 수입 데이터 삭제
                incomeRepository.deleteAll()
                // 설정 초기화
                settingsDataStore.saveMonthlyIncome(0)
                settingsDataStore.saveMonthStartDay(1)

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        monthlyIncome = 0,
                        monthStartDay = 1,
                        message = "모든 데이터가 삭제되었습니다"
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        message = "삭제 실패: ${e.message}"
                    )
                }
            }
        }
    }

    fun clearBackupJson() {
        _uiState.update { it.copy(backupJson = null) }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }
}
