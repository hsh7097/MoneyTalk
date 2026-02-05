package com.sanha.moneytalk.feature.settings.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.sanha.moneytalk.core.datastore.SettingsDataStore
import com.sanha.moneytalk.core.util.BackupData
import com.sanha.moneytalk.core.util.DataBackupManager
import com.sanha.moneytalk.core.util.DriveBackupFile
import com.sanha.moneytalk.core.util.ExportFilter
import com.sanha.moneytalk.core.util.ExportFormat
import com.sanha.moneytalk.core.util.GoogleDriveHelper
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
    val backupContent: String? = null,
    val exportFormat: ExportFormat = ExportFormat.JSON,
    val exportFilter: ExportFilter = ExportFilter(),
    // 카드/카테고리 목록 (필터용)
    val availableCards: List<String> = emptyList(),
    val availableCategories: List<String> = emptyList(),
    // 구글 드라이브 관련
    val isGoogleSignedIn: Boolean = false,
    val googleAccountName: String? = null,
    val driveBackupFiles: List<DriveBackupFile> = emptyList()
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val claudeRepository: ClaudeRepository,
    private val expenseRepository: ExpenseRepository,
    private val incomeRepository: IncomeRepository,
    private val googleDriveHelper: GoogleDriveHelper
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
        loadFilterOptions()
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

    private fun loadFilterOptions() {
        viewModelScope.launch {
            try {
                val cards = expenseRepository.getAllCardNames()
                val categories = expenseRepository.getAllCategories()

                _uiState.update {
                    it.copy(
                        availableCards = cards,
                        availableCategories = categories
                    )
                }
            } catch (e: Exception) {
                // 무시
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
     * 내보내기 필터 설정
     */
    fun setExportFilter(filter: ExportFilter) {
        _uiState.update { it.copy(exportFilter = filter) }
    }

    /**
     * 내보내기 형식 설정
     */
    fun setExportFormat(format: ExportFormat) {
        _uiState.update { it.copy(exportFormat = format) }
    }

    /**
     * 데이터 백업 준비 (필터 적용)
     */
    fun prepareBackup() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                var expenses = expenseRepository.getAllExpensesOnce()
                var incomes = incomeRepository.getAllIncomesOnce()
                val state = _uiState.value
                val filter = state.exportFilter

                // 필터 적용
                if (filter.includeExpenses) {
                    expenses = DataBackupManager.filterExpenses(expenses, filter)
                } else {
                    expenses = emptyList()
                }

                if (filter.includeIncomes) {
                    incomes = DataBackupManager.filterIncomes(incomes, filter)
                } else {
                    incomes = emptyList()
                }

                val content = when (state.exportFormat) {
                    ExportFormat.JSON -> DataBackupManager.createBackupJson(
                        expenses = expenses,
                        incomes = incomes,
                        monthlyIncome = state.monthlyIncome,
                        monthStartDay = state.monthStartDay
                    )
                    ExportFormat.CSV -> DataBackupManager.createCombinedCsv(expenses, incomes)
                }

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        backupContent = content
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
     * 백업 파일로 내보내기 (로컬)
     */
    fun exportBackup(context: Context, uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val content = _uiState.value.backupContent
                    ?: throw Exception("백업 데이터가 준비되지 않았습니다")

                DataBackupManager.exportToUri(context, uri, content)
                    .onSuccess {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                backupContent = null,
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

    // ========== 구글 드라이브 관련 ==========

    /**
     * 구글 로그인 상태 확인
     */
    fun checkGoogleSignIn(context: Context) {
        val isSignedIn = googleDriveHelper.isSignedIn(context)
        val account = googleDriveHelper.getSignedInAccount(context)

        _uiState.update {
            it.copy(
                isGoogleSignedIn = isSignedIn,
                googleAccountName = account?.email
            )
        }
    }

    /**
     * 구글 로그인 성공 처리
     */
    fun handleGoogleSignInResult(context: Context, account: GoogleSignInAccount) {
        googleDriveHelper.initializeDriveService(context, account)
        _uiState.update {
            it.copy(
                isGoogleSignedIn = true,
                googleAccountName = account.email
            )
        }
    }

    /**
     * 구글 로그아웃
     */
    fun signOutGoogle(context: Context) {
        viewModelScope.launch {
            googleDriveHelper.signOut(context)
            _uiState.update {
                it.copy(
                    isGoogleSignedIn = false,
                    googleAccountName = null,
                    driveBackupFiles = emptyList()
                )
            }
        }
    }

    /**
     * 구글 드라이브로 백업 내보내기
     */
    fun exportToGoogleDrive() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val content = _uiState.value.backupContent
                    ?: throw Exception("백업 데이터가 준비되지 않았습니다")

                val format = _uiState.value.exportFormat
                val fileName = DataBackupManager.generateBackupFileName(format)

                googleDriveHelper.uploadFile(fileName, content, format)
                    .onSuccess { link ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                backupContent = null,
                                message = "구글 드라이브에 업로드되었습니다"
                            )
                        }
                        // 목록 새로고침
                        loadDriveBackupFiles()
                    }
                    .onFailure { e ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                message = "업로드 실패: ${e.message}"
                            )
                        }
                    }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        message = "업로드 실패: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * 구글 드라이브 백업 파일 목록 로드
     */
    fun loadDriveBackupFiles() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            googleDriveHelper.listBackupFiles()
                .onSuccess { files ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            driveBackupFiles = files
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            message = "목록 로드 실패: ${e.message}"
                        )
                    }
                }
        }
    }

    /**
     * 구글 드라이브에서 복원
     */
    fun restoreFromGoogleDrive(fileId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            googleDriveHelper.downloadFile(fileId)
                .onSuccess { content ->
                    try {
                        val backupData = com.google.gson.Gson().fromJson(content, BackupData::class.java)
                        restoreData(backupData)
                    } catch (e: Exception) {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                message = "복원 실패: 잘못된 파일 형식"
                            )
                        }
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            message = "다운로드 실패: ${e.message}"
                        )
                    }
                }
        }
    }

    /**
     * 구글 드라이브 백업 파일 삭제
     */
    fun deleteDriveBackupFile(fileId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            googleDriveHelper.deleteFile(fileId)
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            message = "삭제되었습니다"
                        )
                    }
                    loadDriveBackupFiles()
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            message = "삭제 실패: ${e.message}"
                        )
                    }
                }
        }
    }

    fun clearBackupContent() {
        _uiState.update { it.copy(backupContent = null) }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }
}
