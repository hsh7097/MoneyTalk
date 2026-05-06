package com.sanha.moneytalk.feature.settings.ui

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.sanha.moneytalk.R
import com.sanha.moneytalk.core.database.AppDatabase
import com.sanha.moneytalk.core.database.dao.BudgetDao
import com.sanha.moneytalk.core.database.dao.ChatDao
import com.sanha.moneytalk.core.database.SyncCoverageRepository
import com.sanha.moneytalk.core.datastore.SettingsDataStore
import com.sanha.moneytalk.core.firebase.AnalyticsEvent
import com.sanha.moneytalk.core.firebase.AnalyticsHelper
import com.sanha.moneytalk.core.notification.NotificationAccessHelper
import com.sanha.moneytalk.core.theme.ThemeMode
import com.sanha.moneytalk.core.ui.AppSnackbarBus
import com.sanha.moneytalk.core.ui.ClassificationState
import com.sanha.moneytalk.core.util.BackupData
import com.sanha.moneytalk.core.util.DataBackupManager
import com.sanha.moneytalk.core.sms.DeletedSmsTracker
import com.sanha.moneytalk.core.util.DataRefreshEvent
import com.sanha.moneytalk.core.util.DriveBackupFile
import com.sanha.moneytalk.core.util.ExportFilter
import com.sanha.moneytalk.core.util.ExportFormat
import com.sanha.moneytalk.core.util.GoogleDriveHelper
import com.sanha.moneytalk.feature.home.data.CategoryClassifierService
import com.sanha.moneytalk.feature.home.data.CategoryRepository
import com.sanha.moneytalk.feature.home.data.ExpenseRepository
import com.sanha.moneytalk.feature.home.data.IncomeRepository
import com.sanha.moneytalk.receiver.NotificationTransactionService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Settings 화면의 모든 사용자 인터랙션을 Intent로 정의 */
sealed interface SettingsIntent {
    // 다이얼로그 열기
    data object ShowApiKeyDialog : SettingsIntent
    data object ShowMonthStartDayDialog : SettingsIntent
    data object ShowDeleteConfirmDialog : SettingsIntent
    data object ShowExportDialog : SettingsIntent
    data object ShowGoogleDriveDialog : SettingsIntent
    data object ShowAppInfoDialog : SettingsIntent
    data object ShowPrivacyDialog : SettingsIntent
    data object ShowThemeDialog : SettingsIntent
    data object ShowMonthlyBudgetDialog : SettingsIntent
    data object ShowBudgetBottomSheet : SettingsIntent

    // 다이얼로그 닫기
    data object DismissDialog : SettingsIntent

    // 액션
    data class SaveApiKey(val key: String) : SettingsIntent
    data class SaveMonthStartDay(val day: Int) : SettingsIntent
    data class SaveMonthlyBudget(val amount: Int) : SettingsIntent
    data class SaveBudgets(
        val totalBudget: Int?,
        val categoryBudgets: Map<String, Int>
    ) : SettingsIntent
    data class SaveThemeMode(val mode: ThemeMode) : SettingsIntent
    data object ClassifyUnclassified : SettingsIntent
    data object DeleteAllData : SettingsIntent
    data object DeleteDuplicates : SettingsIntent
    data object DebugFullSyncAllMessages : SettingsIntent
    data object DebugSyncTodayMessages : SettingsIntent
    data object OpenRestoreFilePicker : SettingsIntent
    data class SetPendingRestoreUri(val uri: Uri) : SettingsIntent
    data object ConfirmRestore : SettingsIntent
    data class ToggleNotification(val enabled: Boolean) : SettingsIntent
}

/** 다이얼로그 종류 (하나의 필드로 관리) */
enum class SettingsDialog {
    API_KEY,
    MONTH_START_DAY,
    DELETE_CONFIRM,
    RESTORE_CONFIRM,
    EXPORT,
    GOOGLE_DRIVE,
    APP_INFO,
    PRIVACY,
    THEME,
    MONTHLY_BUDGET,
    BUDGET_BOTTOM_SHEET
}

@Stable
data class SettingsUiState(
    val apiKey: String = "",
    val hasApiKey: Boolean = false,
    val monthStartDay: Int = 1,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val isLoading: Boolean = false,
    val backupContent: String? = null,
    val exportFormat: ExportFormat = ExportFormat.JSON,
    val exportFilter: ExportFilter = ExportFilter(),
    // 카드/카테고리 목록 (필터용)
    val availableCards: List<String> = emptyList(),
    val availableCategories: List<String> = emptyList(),
    // 구글 드라이브 관련
    val isGoogleSignedIn: Boolean = false,
    val googleAccountName: String? = null,
    val driveBackupFiles: List<DriveBackupFile> = emptyList(),
    // 카테고리 분류 관련
    val unclassifiedCount: Int = 0,
    val isClassifying: Boolean = false,
    val classifyProgress: String = "",
    val classifyProgressCurrent: Int = 0,
    val classifyProgressTotal: Int = 0,
    // 내 카드 관리
    val ownedCards: List<com.sanha.moneytalk.core.database.entity.OwnedCardEntity> = emptyList(),
    // 백그라운드 분류 진행 중 (HomeViewModel에서 진행 중인 경우)
    val isBackgroundClassifying: Boolean = false,
    // 다이얼로그 상태 (null이면 닫힘)
    val activeDialog: SettingsDialog? = null,
    // 복원 대기 URI
    val pendingRestoreUri: Uri? = null,
    // 복원 파일 선택 트리거
    val triggerRestoreFilePicker: Boolean = false,
    // 월 예산
    val monthlyBudget: Int? = null,
    // 카테고리별 예산 (category displayName → monthlyLimit)
    val categoryBudgets: Map<String, Int> = emptyMap(),
    // 거래 알림 설정
    val notificationEnabled: Boolean = false,
    // 알림 접근 권한 상태
    val notificationAccessEnabled: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsDataStore: SettingsDataStore,
    private val expenseRepository: ExpenseRepository,
    private val incomeRepository: IncomeRepository,
    private val googleDriveHelper: GoogleDriveHelper,
    private val categoryClassifierService: CategoryClassifierService,
    private val categoryRepository: CategoryRepository,
    private val appDatabase: AppDatabase,
    private val chatDao: ChatDao,
    private val budgetDao: BudgetDao,
    private val syncCoverageRepository: SyncCoverageRepository,
    private val dataRefreshEvent: DataRefreshEvent,
    private val ownedCardRepository: com.sanha.moneytalk.core.database.OwnedCardRepository,
    private val snackbarBus: AppSnackbarBus,
    private val classificationState: ClassificationState,
    private val analyticsHelper: AnalyticsHelper
) : ViewModel() {

    companion object {
    }

    private fun message(resId: Int, vararg args: Any): String {
        return context.getString(resId, *args)
    }

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
        loadFilterOptions()
        loadUnclassifiedCount()
        loadOwnedCards()
        observeClassificationState()
        loadThemeMode()
        loadMonthlyBudget()
        loadNotificationEnabled()
    }

    // ========== Intent 처리 ==========

    /** 모든 사용자 인터랙션을 Intent로 처리 */
    fun onIntent(intent: SettingsIntent) {
        when (intent) {
            is SettingsIntent.ShowApiKeyDialog -> showDialog(SettingsDialog.API_KEY)
            is SettingsIntent.ShowMonthStartDayDialog -> showDialog(SettingsDialog.MONTH_START_DAY)
            is SettingsIntent.ShowDeleteConfirmDialog -> showDialog(SettingsDialog.DELETE_CONFIRM)
            is SettingsIntent.ShowExportDialog -> showDialog(SettingsDialog.EXPORT)
            is SettingsIntent.ShowGoogleDriveDialog -> showDialog(SettingsDialog.GOOGLE_DRIVE)
            is SettingsIntent.ShowAppInfoDialog -> showDialog(SettingsDialog.APP_INFO)
            is SettingsIntent.ShowPrivacyDialog -> showDialog(SettingsDialog.PRIVACY)
            is SettingsIntent.ShowThemeDialog -> showDialog(SettingsDialog.THEME)
            is SettingsIntent.ShowMonthlyBudgetDialog -> showDialog(SettingsDialog.MONTHLY_BUDGET)
            is SettingsIntent.ShowBudgetBottomSheet -> showDialog(SettingsDialog.BUDGET_BOTTOM_SHEET)
            is SettingsIntent.DismissDialog -> dismissDialog()

            is SettingsIntent.SaveApiKey -> {
                dismissDialog()
                saveApiKey(intent.key)
            }

            is SettingsIntent.SaveMonthStartDay -> {
                dismissDialog()
                saveMonthStartDay(intent.day)
            }

            is SettingsIntent.SaveThemeMode -> {
                dismissDialog()
                saveThemeMode(intent.mode)
            }

            is SettingsIntent.SaveMonthlyBudget -> {
                dismissDialog()
                saveMonthlyBudget(intent.amount)
            }

            is SettingsIntent.SaveBudgets -> {
                dismissDialog()
                saveBudgets(intent.totalBudget, intent.categoryBudgets)
            }

            is SettingsIntent.ClassifyUnclassified -> classifyUnclassifiedExpenses()
            is SettingsIntent.DeleteAllData -> {
                dismissDialog()
                deleteAllData()
            }

            is SettingsIntent.DeleteDuplicates -> deleteDuplicates()
            is SettingsIntent.DebugFullSyncAllMessages -> requestDebugFullSyncAllMessages()
            is SettingsIntent.DebugSyncTodayMessages -> requestDebugSyncTodayMessages()
            is SettingsIntent.OpenRestoreFilePicker -> {
                _uiState.update { it.copy(triggerRestoreFilePicker = true) }
            }

            is SettingsIntent.SetPendingRestoreUri -> {
                _uiState.update {
                    it.copy(
                        pendingRestoreUri = intent.uri,
                        activeDialog = SettingsDialog.RESTORE_CONFIRM
                    )
                }
            }

            is SettingsIntent.ConfirmRestore -> {
                // Context가 필요한 복원은 Composable에서 직접 호출
                dismissDialog()
            }

            is SettingsIntent.ToggleNotification -> saveNotificationEnabled(intent.enabled)
        }
    }

    private fun showDialog(dialog: SettingsDialog) {
        _uiState.update { it.copy(activeDialog = dialog) }
    }

    private fun dismissDialog() {
        _uiState.update { it.copy(activeDialog = null, pendingRestoreUri = null) }
    }

    /** 파일 선택 트리거 소비 (Composable에서 호출) */
    fun consumeRestoreFilePickerTrigger() {
        _uiState.update { it.copy(triggerRestoreFilePicker = false) }
    }


    // ========== 알림 설정 ==========

    private fun loadNotificationEnabled() {
        viewModelScope.launch {
            settingsDataStore.notificationEnabledFlow.collect { enabled ->
                _uiState.update { it.copy(notificationEnabled = enabled) }
            }
        }
    }

    private fun saveNotificationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.saveNotificationEnabled(enabled)
        }
    }

    fun refreshNotificationAccess(context: Context) {
        val enabled = NotificationAccessHelper.isNotificationListenerEnabled(
            context = context,
            listenerServiceClass = NotificationTransactionService::class.java
        )
        _uiState.update { it.copy(notificationAccessEnabled = enabled) }
    }

    private fun loadThemeMode() {
        viewModelScope.launch {
            settingsDataStore.themeModeFlow.collect { modeStr ->
                val mode = try {
                    ThemeMode.valueOf(modeStr)
                } catch (_: Exception) {
                    ThemeMode.SYSTEM
                }
                _uiState.update { it.copy(themeMode = mode) }
            }
        }
    }

    fun saveThemeMode(mode: ThemeMode) {
        analyticsHelper.logClick(AnalyticsEvent.SCREEN_SETTINGS, AnalyticsEvent.CLICK_THEME_CHANGE)
        viewModelScope.launch {
            settingsDataStore.saveThemeMode(mode.name)
        }
    }

    /** 백그라운드 분류 상태 감지 (HomeViewModel에서 진행 중인 경우 버튼 비활성화) */
    private fun observeClassificationState() {
        viewModelScope.launch {
            classificationState.isRunning.collect { running ->
                _uiState.update { it.copy(isBackgroundClassifying = running) }
                // 분류 완료 시 미분류 건수 새로고침
                if (!running) {
                    loadUnclassifiedCount()
                }
            }
        }
    }

    private fun loadSettings() {
        loadApiKeyState()

        viewModelScope.launch {
            // 월 시작일 로드
            settingsDataStore.monthStartDayFlow.collect { day ->
                _uiState.update { it.copy(monthStartDay = day) }
            }
        }
    }

    private fun loadApiKeyState() {
        viewModelScope.launch {
            refreshApiKeyState()
        }
    }

    private suspend fun refreshApiKeyState(): Boolean {
        val hasKey = withContext(Dispatchers.IO) {
            categoryClassifierService.hasGeminiApiKey()
        }
        _uiState.update { it.copy(hasApiKey = hasKey) }
        return hasKey
    }

    private fun loadFilterOptions() {
        viewModelScope.launch {
            try {
                val (cards, categories) = withContext(Dispatchers.IO) {
                    val c = expenseRepository.getAllCardNames()
                    val cat = expenseRepository.getAllCategories()
                    Pair(c, cat)
                }

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

    @Deprecated("API 키는 Firebase RTDB에서 관리됩니다")
    fun saveApiKey(key: String) {
        // RTDB 기반 키 관리로 전환 — 로컬 키 저장 제거
    }

    fun saveMonthStartDay(day: Int) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                withContext(Dispatchers.IO) { settingsDataStore.saveMonthStartDay(day) }
                snackbarBus.show("월 시작일이 ${day}일로 설정되었습니다")
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        monthStartDay = day
                    )
                }
            } catch (e: Exception) {
                snackbarBus.show("저장 실패: ${e.message}")
                _uiState.update {
                    it.copy(
                        isLoading = false
                    )
                }
            }
        }
    }

    // ========== 월 예산 관리 ==========

    /** 전체 + 카테고리별 예산 로드 (모든 월 공통 "default") */
    private fun loadMonthlyBudget() {
        viewModelScope.launch {
            try {
                val budgets = withContext(Dispatchers.IO) {
                    // 기존 월별 yearMonth("2026-02" 등)를 "default"로 마이그레이션
                    budgetDao.migrateToDefault()
                    budgetDao.getBudgetsByMonthOnce("default")
                }
                val totalBudget = budgets
                    .find { it.category == "전체" }
                    ?.monthlyLimit
                val categoryBudgets = budgets
                    .filter { it.category != "전체" }
                    .associate { it.category to it.monthlyLimit }
                _uiState.update {
                    it.copy(
                        monthlyBudget = totalBudget,
                        categoryBudgets = categoryBudgets
                    )
                }
            } catch (_: Exception) {
                // 무시
            }
        }
    }

    /** 월 예산 저장 (전체 예산으로 단일 "전체" 카테고리에 저장) — 모든 월 공통 */
    private fun saveMonthlyBudget(amount: Int) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    if (amount > 0) {
                        budgetDao.deleteAllByMonth("default")
                        budgetDao.insert(
                            com.sanha.moneytalk.core.database.entity.BudgetEntity(
                                category = "전체",
                                monthlyLimit = amount,
                                yearMonth = "default"
                            )
                        )
                    } else {
                        budgetDao.deleteAllByMonth("default")
                    }
                }
                _uiState.update {
                    it.copy(
                        monthlyBudget = if (amount > 0) amount else null,
                        categoryBudgets = emptyMap()
                    )
                }
                snackbarBus.show(
                    if (amount > 0) "월 예산이 설정되었습니다" else "월 예산이 해제되었습니다"
                )
                dataRefreshEvent.emit(DataRefreshEvent.RefreshType.CATEGORY_UPDATED)
            } catch (e: Exception) {
                snackbarBus.show("저장 실패: ${e.message}")
            }
        }
    }

    /** 전체 + 카테고리별 예산 일괄 저장 (BudgetBottomSheet에서 호출, 모든 월 공통) */
    private fun saveBudgets(totalBudget: Int?, categoryBudgets: Map<String, Int>) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    budgetDao.deleteAllByMonth("default")

                    // 전체 예산 저장
                    if (totalBudget != null && totalBudget > 0) {
                        budgetDao.insert(
                            com.sanha.moneytalk.core.database.entity.BudgetEntity(
                                category = "전체",
                                monthlyLimit = totalBudget,
                                yearMonth = "default"
                            )
                        )
                    }

                    // 카테고리별 예산 저장
                    categoryBudgets.forEach { (category, amount) ->
                        if (amount > 0) {
                            budgetDao.insert(
                                com.sanha.moneytalk.core.database.entity.BudgetEntity(
                                    category = category,
                                    monthlyLimit = amount,
                                    yearMonth = "default"
                                )
                            )
                        }
                    }
                }

                _uiState.update {
                    it.copy(
                        monthlyBudget = totalBudget?.takeIf { b -> b > 0 },
                        categoryBudgets = categoryBudgets.filterValues { v -> v > 0 }
                    )
                }
                snackbarBus.show("예산이 저장되었습니다")
                dataRefreshEvent.emit(DataRefreshEvent.RefreshType.CATEGORY_UPDATED)
            } catch (e: Exception) {
                snackbarBus.show("저장 실패: ${e.message}")
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
        analyticsHelper.logClick(AnalyticsEvent.SCREEN_SETTINGS, AnalyticsEvent.CLICK_BACKUP)
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, backupContent = null) }
            try {
                val state = _uiState.value
                val content = withContext(Dispatchers.IO) {
                    var expenses = expenseRepository.getAllExpensesOnce()
                    var incomes = incomeRepository.getAllIncomesOnce()
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

                    val savedMonthlyIncome = settingsDataStore.getMonthlyIncome()
                    when (state.exportFormat) {
                        ExportFormat.JSON -> DataBackupManager.createBackupJson(
                            expenses = expenses,
                            incomes = incomes,
                            monthlyIncome = savedMonthlyIncome,
                            monthStartDay = state.monthStartDay
                        )

                        ExportFormat.CSV -> DataBackupManager.createCombinedCsv(expenses, incomes)
                    }
                }

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        backupContent = content
                    )
                }
            } catch (e: Exception) {
                snackbarBus.show("백업 준비 실패: ${e.message}")
                _uiState.update {
                    it.copy(
                        isLoading = false
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

                val result = withContext(Dispatchers.IO) {
                    DataBackupManager.exportToUri(context, uri, content)
                }
                result.onSuccess {
                    snackbarBus.show("백업이 완료되었습니다")
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            backupContent = null
                        )
                    }
                }.onFailure { e ->
                    snackbarBus.show("백업 실패: ${e.message}")
                    _uiState.update {
                        it.copy(
                            isLoading = false
                        )
                    }
                }
            } catch (e: Exception) {
                snackbarBus.show("백업 실패: ${e.message}")
                _uiState.update {
                    it.copy(
                        isLoading = false
                    )
                }
            }
        }
    }

    /**
     * 백업 파일에서 복원
     */
    fun importBackup(context: Context, uri: Uri) {
        analyticsHelper.logClick(AnalyticsEvent.SCREEN_SETTINGS, AnalyticsEvent.CLICK_RESTORE)
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val result = withContext(Dispatchers.IO) {
                    DataBackupManager.importFromUri(context, uri)
                }
                result.onSuccess { backupData ->
                    restoreData(backupData)
                }.onFailure { e ->
                    snackbarBus.show("복원 실패: ${e.message}")
                    _uiState.update {
                        it.copy(
                            isLoading = false
                        )
                    }
                }
            } catch (e: Exception) {
                snackbarBus.show("복원 실패: ${e.message}")
                _uiState.update {
                    it.copy(
                        isLoading = false
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
            val (expenseCount, incomeCount) = withContext(Dispatchers.IO) {
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

                Pair(expenses.size, incomes.size)
            }

            _uiState.update {
                it.copy(
                    isLoading = false,
                    monthStartDay = backupData.settings.monthStartDay
                )
            }
            snackbarBus.show("복원 완료: 지출 ${expenseCount}건, 수입 ${incomeCount}건")
        } catch (e: Exception) {
            snackbarBus.show("복원 실패: ${e.message}")
            _uiState.update {
                it.copy(
                    isLoading = false
                )
            }
        }
    }

    /**
     * 모든 데이터 삭제 (전체 초기화)
     *
     * 삭제 대상:
     * - 지출/수입 데이터
     * - 채팅 기록 (세션 포함)
     * - 카테고리 매핑 정보
     * - 예산 설정
     * - 앱 설정
     *
     * 보존 대상 (벡터 학습 데이터):
     * - SmsPatternEntity: SMS 임베딩 패턴 (누적될수록 분류 정확도 향상)
     * - StoreEmbeddingEntity: 가게명 임베딩 벡터 (카테고리 분류 캐시)
     */
    fun deleteAllData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                // 진행 중인 백그라운드 분류 작업 즉시 취소
                classificationState.cancelIfRunning()

                withContext(Dispatchers.IO) {
                    // 선택적 테이블 삭제 (벡터 데이터 보존)
                    // SmsPatternEntity, StoreEmbeddingEntity는 학습 데이터이므로 유지
                    expenseRepository.deleteAll()
                    incomeRepository.deleteAll()
                    chatDao.deleteAll()          // chat_history 삭제
                    chatDao.deleteAllSessions()  // chat_sessions 삭제
                    budgetDao.deleteAll()
                    categoryRepository.deleteAllMappings()
                    ownedCardRepository.deleteAll()

                    // 설정 초기화
                    settingsDataStore.saveMonthlyIncome(0)
                    settingsDataStore.saveMonthStartDay(1)
                    // 마지막 동기화 시간 초기화 (다음 동기화 시 전체 동기화 되도록)
                    settingsDataStore.saveLastSyncTime(0L)
                    settingsDataStore.saveLastRcsProviderScanTime(0L)
                    // 실제 동기화 구간 기록도 함께 제거
                    syncCoverageRepository.clearAll()
                    // 광고 시청 기록 초기화 (월별 전체 동기화 다시 가능하도록)
                    settingsDataStore.clearSyncedMonths()
                }

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        monthStartDay = 1,
                        unclassifiedCount = 0,
                    )
                }
                snackbarBus.show("모든 데이터가 삭제되었습니다 (학습 데이터는 보존됨)")

                // 전체 삭제 시 삭제 추적 목록도 초기화 (새 동기화에서 재수집 가능하도록)
                DeletedSmsTracker.clear()

                // 다른 ViewModel에게 데이터 삭제 이벤트 전달
                dataRefreshEvent.emit(DataRefreshEvent.RefreshType.ALL_DATA_DELETED)
            } catch (e: Exception) {
                snackbarBus.show("삭제 실패: ${e.message}")
                _uiState.update {
                    it.copy(
                        isLoading = false
                    )
                }
            }
        }
    }

    // ========== 구글 드라이브 관련 ==========

    /**
     * 구글 로그인 상태 확인 (앱 시작 시)
     * 이전에 로그인했던 계정이 있으면 Drive 서비스도 함께 초기화
     */
    fun checkGoogleSignIn(context: Context) {
        val account = googleDriveHelper.getSignedInAccount(context)
        val isSignedIn = account != null

        // 이전 세션에서 로그인된 계정이 있으면 Drive 서비스 재초기화
        if (isSignedIn && account != null) {
            googleDriveHelper.initializeDriveService(context, account)
        }

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
     * 구글 드라이브 열기 시도
     * 1) driveService가 이미 초기화되어 있으면 바로 다이얼로그 열기
     * 2) 아니면 silentSignIn 시도 → 성공하면 다이얼로그 열기
     * 3) silentSignIn 실패 → interactive 로그인 필요 (콜백에서 Intent 반환)
     *
     * @return 로그인 Intent (interactive 로그인이 필요한 경우), null이면 로그인 성공/바로 열기 가능
     */
    suspend fun tryOpenGoogleDrive(context: Context): android.content.Intent? {
        // 1) Drive 서비스가 이미 준비되어 있으면 바로 성공
        if (googleDriveHelper.isDriveServiceReady()) {
            _uiState.update { it.copy(isGoogleSignedIn = true) }
            return null
        }

        // 2) Silent sign-in 시도
        val account = googleDriveHelper.trySilentSignIn(context)
        if (account != null) {
            _uiState.update {
                it.copy(
                    isGoogleSignedIn = true,
                    googleAccountName = account.email
                )
            }
            return null
        }

        // 3) Interactive 로그인 필요
        return googleDriveHelper.getSignInIntent(context)
    }

    /**
     * 구글 로그아웃
     */
    fun signOutGoogle(context: Context) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { googleDriveHelper.signOut(context) }
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

                val result = withContext(Dispatchers.IO) {
                    googleDriveHelper.uploadFile(fileName, content, format)
                }
                result.onSuccess { _ ->
                    snackbarBus.show("구글 드라이브에 업로드되었습니다")
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            backupContent = null
                        )
                    }
                    // 목록 새로고침
                    loadDriveBackupFiles()
                }.onFailure { e ->
                    snackbarBus.show("업로드 실패: ${e.message}")
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            backupContent = null
                        )
                    }
                }
            } catch (e: Exception) {
                snackbarBus.show("업로드 실패: ${e.message}")
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        backupContent = null
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
            val result = withContext(Dispatchers.IO) {
                googleDriveHelper.listBackupFiles()
            }
            result.onSuccess { files ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        driveBackupFiles = files
                    )
                }
            }.onFailure { e ->
                snackbarBus.show("목록 로드 실패: ${e.message}")
                _uiState.update {
                    it.copy(
                        isLoading = false
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
            val result = withContext(Dispatchers.IO) {
                googleDriveHelper.downloadFile(fileId)
            }
            result.onSuccess { content ->
                try {
                    val backupData = withContext(Dispatchers.IO) {
                        com.google.gson.Gson().fromJson(content, BackupData::class.java)
                    }
                    restoreData(backupData)
                } catch (e: Exception) {
                    snackbarBus.show("복원 실패: 잘못된 파일 형식")
                    _uiState.update {
                        it.copy(
                            isLoading = false
                        )
                    }
                }
            }.onFailure { e ->
                snackbarBus.show("다운로드 실패: ${e.message}")
                _uiState.update {
                    it.copy(
                        isLoading = false
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
            val result = withContext(Dispatchers.IO) {
                googleDriveHelper.deleteFile(fileId)
            }
            result.onSuccess {
                snackbarBus.show("삭제되었습니다")
                _uiState.update {
                    it.copy(
                        isLoading = false
                    )
                }
                loadDriveBackupFiles()
            }.onFailure { e ->
                snackbarBus.show("삭제 실패: ${e.message}")
                _uiState.update {
                    it.copy(
                        isLoading = false
                    )
                }
            }
        }
    }

    // ========== 내 카드 관리 ==========

    /**
     * 보유 카드 목록 로드 (Flow 구독)
     */
    private fun loadOwnedCards() {
        viewModelScope.launch {
            ownedCardRepository.getAllCards().collect { cards ->
                _uiState.update { it.copy(ownedCards = cards) }
            }
        }
    }

    /**
     * 카드 소유 여부 변경
     */
    fun updateCardOwnership(cardName: String, isOwned: Boolean) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    ownedCardRepository.updateOwnership(cardName, isOwned)
                }
                dataRefreshEvent.emit(DataRefreshEvent.RefreshType.OWNED_CARD_UPDATED)
            } catch (e: Exception) {
                snackbarBus.show("카드 설정 실패: ${e.message}")
            }
        }
    }

    fun clearBackupContent() {
        _uiState.update { it.copy(backupContent = null) }
    }

    /**
     * 구글 로그인 Intent 가져오기
     * SettingsScreen에서 새 GoogleDriveHelper 인스턴스를 생성하지 않고
     * ViewModel에 주입된 싱글톤을 사용하도록
     */
    fun getSignInIntent(context: Context): android.content.Intent {
        return googleDriveHelper.getSignInIntent(context)
    }

    // ========== 카테고리 분류 관련 ==========

    /**
     * 화면 진입 시 데이터 새로고침
     * 다른 탭(홈)에서 SMS 동기화 후 설정 탭으로 돌아올 때 최신 데이터 반영
     */
    fun refresh() {
        loadApiKeyState()
        loadUnclassifiedCount()
        loadFilterOptions()
    }

    /**
     * 미분류 항목 수 조회
     */
    private fun loadUnclassifiedCount() {
        viewModelScope.launch {
            try {
                val count = withContext(Dispatchers.IO) {
                    categoryClassifierService.getUnclassifiedCount()
                }
                _uiState.update { it.copy(unclassifiedCount = count) }
            } catch (e: Exception) {
                // 무시
            }
        }
    }

    /**
     * 미분류 항목 Gemini로 자동 분류
     */
    fun classifyUnclassifiedExpenses() {
        viewModelScope.launch {
            if (_uiState.value.isClassifying || _uiState.value.isBackgroundClassifying) {
                snackbarBus.show(message(R.string.settings_classify_already_running))
                return@launch
            }

            val hasApiKey = refreshApiKeyState()
            if (!hasApiKey) {
                snackbarBus.show(message(R.string.settings_classify_no_api_key))
                return@launch
            }

            val initialCount = withContext(Dispatchers.IO) {
                categoryClassifierService.getUnclassifiedCount()
            }
            _uiState.update { it.copy(unclassifiedCount = initialCount) }
            if (initialCount == 0) {
                snackbarBus.show(message(R.string.settings_classify_nothing_to_process))
                return@launch
            }

            _uiState.update {
                it.copy(
                    isClassifying = true,
                    classifyProgress = message(R.string.settings_classify_preparing),
                    classifyProgressCurrent = 0,
                    classifyProgressTotal = initialCount
                )
            }
            try {
                val count = withContext(Dispatchers.IO) {
                    categoryClassifierService.classifyUnclassifiedExpenses(onStepProgress = { step, current, total ->
                        _uiState.update {
                            val progressText = if (total > 0) {
                                message(R.string.settings_classify_step_progress, step, current, total)
                            } else {
                                step
                            }
                            it.copy(
                                classifyProgress = progressText,
                                classifyProgressCurrent = current,
                                classifyProgressTotal = if (total > 0) total else it.classifyProgressTotal
                            )
                        }
                    })
                }
                loadUnclassifiedCount()
                snackbarBus.show(
                    if (count > 0) {
                        message(R.string.settings_classify_success, count)
                    } else {
                        message(R.string.settings_classify_no_result)
                    }
                )
                _uiState.update {
                    it.copy(
                        isClassifying = false,
                        classifyProgress = "",
                        classifyProgressCurrent = 0,
                        classifyProgressTotal = 0
                    )
                }
            } catch (e: Exception) {
                snackbarBus.show(
                    message(
                        R.string.settings_classify_failed,
                        e.message ?: message(R.string.common_unknown_error)
                    )
                )
                _uiState.update {
                    it.copy(
                        isClassifying = false,
                        classifyProgress = "",
                        classifyProgressCurrent = 0,
                        classifyProgressTotal = 0
                    )
                }
            }
        }
    }

    /**
     * Gemini API 키 존재 여부 확인
     */
    fun hasGeminiApiKey(): Boolean {
        return _uiState.value.hasApiKey
    }

    /**
     * 중복 데이터 삭제
     */
    fun deleteDuplicates() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val deletedCount = withContext(Dispatchers.IO) {
                    expenseRepository.deleteDuplicates()
                }
                snackbarBus.show(
                    if (deletedCount > 0) {
                        message(R.string.settings_duplicate_deleted, deletedCount)
                    } else {
                        message(R.string.settings_duplicate_empty)
                    }
                )
                _uiState.update {
                    it.copy(
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                snackbarBus.show(
                    message(
                        R.string.settings_duplicate_failed,
                        e.message ?: message(R.string.common_unknown_error)
                    )
                )
                _uiState.update {
                    it.copy(
                        isLoading = false
                    )
                }
            }
        }
    }

    private fun requestDebugFullSyncAllMessages() {
        dataRefreshEvent.emit(DataRefreshEvent.RefreshType.DEBUG_FULL_SYNC_ALL_MESSAGES)
    }

    private fun requestDebugSyncTodayMessages() {
        dataRefreshEvent.emit(DataRefreshEvent.RefreshType.DEBUG_SYNC_TODAY_MESSAGES)
    }

    // ===== 화면별 온보딩 =====

    fun hasSeenScreenOnboardingFlow(screenId: String) =
        settingsDataStore.hasSeenScreenOnboardingFlow(screenId)

    fun markScreenOnboardingSeen(screenId: String) {
        viewModelScope.launch {
            settingsDataStore.setScreenOnboardingSeen(screenId)
        }
    }

    /** 모든 화면 온보딩 가이드 초기화 (설정 > 가이드 초기화) */
    fun resetAllScreenOnboardings() {
        viewModelScope.launch {
            settingsDataStore.resetAllScreenOnboardings()
            snackbarBus.show(message(R.string.settings_reset_guide_done))
        }
    }
}
