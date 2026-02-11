package com.sanha.moneytalk.feature.settings.ui

import android.content.Context
import android.util.Log
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.sanha.moneytalk.core.datastore.SettingsDataStore
import com.sanha.moneytalk.core.database.AppDatabase
import com.sanha.moneytalk.core.database.dao.BudgetDao
import com.sanha.moneytalk.core.database.dao.ChatDao
import com.sanha.moneytalk.core.util.BackupData
import com.sanha.moneytalk.core.util.DataBackupManager
import com.sanha.moneytalk.core.util.DriveBackupFile
import com.sanha.moneytalk.core.util.ExportFilter
import com.sanha.moneytalk.core.util.ExportFormat
import com.sanha.moneytalk.core.util.GoogleDriveHelper
import com.sanha.moneytalk.core.ui.AppSnackbarBus
import com.sanha.moneytalk.core.ui.ClassificationState
import com.sanha.moneytalk.feature.chat.data.GeminiRepository
import com.sanha.moneytalk.feature.home.data.CategoryClassifierService
import com.sanha.moneytalk.feature.home.data.CategoryRepository
import com.sanha.moneytalk.feature.home.data.ExpenseRepository
import com.sanha.moneytalk.feature.home.data.IncomeRepository
import com.sanha.moneytalk.core.util.DataRefreshEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class SettingsUiState(
    val apiKey: String = "",
    val hasApiKey: Boolean = false,
    val monthStartDay: Int = 1,
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
    // SMS 제외 키워드 관리
    val exclusionKeywords: List<com.sanha.moneytalk.core.database.entity.SmsExclusionKeywordEntity> = emptyList(),
    // 백그라운드 분류 진행 중 (HomeViewModel에서 진행 중인 경우)
    val isBackgroundClassifying: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val geminiRepository: GeminiRepository,
    private val expenseRepository: ExpenseRepository,
    private val incomeRepository: IncomeRepository,
    private val googleDriveHelper: GoogleDriveHelper,
    private val categoryClassifierService: CategoryClassifierService,
    private val categoryRepository: CategoryRepository,
    private val appDatabase: AppDatabase,
    private val chatDao: ChatDao,
    private val budgetDao: BudgetDao,
    private val dataRefreshEvent: DataRefreshEvent,
    private val ownedCardRepository: com.sanha.moneytalk.core.database.OwnedCardRepository,
    private val smsExclusionRepository: com.sanha.moneytalk.core.database.SmsExclusionRepository,
    private val snackbarBus: AppSnackbarBus,
    private val classificationState: ClassificationState
) : ViewModel() {

    companion object {
        private const val TAG = "SettingsViewModel"
    }

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
        loadFilterOptions()
        loadUnclassifiedCount()
        loadOwnedCards()
        loadExclusionKeywords()
        observeClassificationState()
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
        viewModelScope.launch {
            // Gemini API 키 로드
            settingsDataStore.geminiApiKeyFlow.collect { key ->
                _uiState.update {
                    it.copy(
                        apiKey = maskApiKey(key),
                        hasApiKey = key.isNotBlank()
                    )
                }
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

    fun saveApiKey(key: String) {
        viewModelScope.launch {
            Log.e("sanha", "SettingsViewModel[saveApiKey] : API 키 저장 시작 (길이=${key.length})")
            _uiState.update { it.copy(isLoading = true) }
            try {
                withContext(Dispatchers.IO) { geminiRepository.setApiKey(key) }
                Log.e("sanha", "SettingsViewModel[saveApiKey] : API 키 저장 완료 → 백그라운드 재분류 트리거")
                snackbarBus.show("Gemini API 키가 저장되었습니다")
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        hasApiKey = true,
                        apiKey = maskApiKey(key)
                    )
                }
                launchBackgroundReclassification()
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
                }

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        monthStartDay = 1,
                        unclassifiedCount = 0,
                    )
                }
                snackbarBus.show("모든 데이터가 삭제되었습니다 (학습 데이터는 보존됨)")

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
                            isLoading = false
                        )
                    }
                }
            } catch (e: Exception) {
                snackbarBus.show("업로드 실패: ${e.message}")
                _uiState.update {
                    it.copy(
                        isLoading = false
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
            Log.e("sanha", "SettingsViewModel[classifyUnclassifiedExpenses] : 수동 분류 시작 (hasApiKey=${_uiState.value.hasApiKey}, unclassified=${_uiState.value.unclassifiedCount})")
            // API 키 확인
            if (!_uiState.value.hasApiKey) {
                Log.e("sanha", "SettingsViewModel[classifyUnclassifiedExpenses] : API 키 없음 → 중단")
                snackbarBus.show("API 키를 먼저 설정해주세요")
                return@launch
            }

            // 미정리 항목 확인
            if (_uiState.value.unclassifiedCount == 0) {
                Log.e("sanha", "SettingsViewModel[classifyUnclassifiedExpenses] : 미분류 0건 → 중단")
                snackbarBus.show("정리할 항목이 없습니다")
                return@launch
            }

            val initialCount = _uiState.value.unclassifiedCount
            _uiState.update {
                it.copy(
                    isClassifying = true,
                    classifyProgress = "정리 준비 중...",
                    classifyProgressCurrent = 0,
                    classifyProgressTotal = initialCount
                )
            }
            try {
                val count = withContext(Dispatchers.IO) {
                    categoryClassifierService.classifyUnclassifiedExpenses(onStepProgress = { step, current, total ->
                        _uiState.update {
                            val progressText = if (total > 0) "$step ($current/$total)" else step
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
                        "${count}건의 카테고리가 정리되었습니다"
                    } else {
                        "정리에 실패했습니다. API 키를 확인해주세요."
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
                snackbarBus.show("카테고리 정리 실패: ${e.message}")
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
                        "중복 데이터 ${deletedCount}건이 삭제되었습니다"
                    } else {
                        "중복 데이터가 없습니다"
                    }
                )
                _uiState.update {
                    it.copy(
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                snackbarBus.show("중복 삭제 실패: ${e.message}")
                _uiState.update {
                    it.copy(
                        isLoading = false
                    )
                }
            }
        }
    }

    // ========== SMS 제외 키워드 관리 ==========

    /**
     * 제외 키워드 목록 로드
     */
    private fun loadExclusionKeywords() {
        viewModelScope.launch {
            try {
                val keywords = withContext(Dispatchers.IO) {
                    smsExclusionRepository.getAllKeywords()
                }
                _uiState.update { it.copy(exclusionKeywords = keywords) }
            } catch (e: Exception) {
                // 무시
            }
        }
    }

    /**
     * 제외 키워드 추가
     */
    fun addExclusionKeyword(keyword: String) {
        viewModelScope.launch {
            try {
                val added = withContext(Dispatchers.IO) {
                    smsExclusionRepository.addKeyword(keyword, "user")
                }
                if (added) {
                    loadExclusionKeywords()
                    snackbarBus.show("'${keyword}' 제외 키워드가 추가되었습니다")
                    // Home/History에서 해당 키워드 포함 데이터를 필터링하도록 새로고침
                    dataRefreshEvent.emit(DataRefreshEvent.RefreshType.CATEGORY_UPDATED)
                } else {
                    snackbarBus.show("키워드를 추가할 수 없습니다")
                }
            } catch (e: Exception) {
                snackbarBus.show("추가 실패: ${e.message}")
            }
        }
    }

    /**
     * 제외 키워드 삭제 (default 소스는 삭제 불가)
     */
    fun removeExclusionKeyword(keyword: String) {
        viewModelScope.launch {
            try {
                val deleted = withContext(Dispatchers.IO) {
                    smsExclusionRepository.removeKeyword(keyword)
                }
                if (deleted > 0) {
                    loadExclusionKeywords()
                    snackbarBus.show("'${keyword}' 제외 키워드가 삭제되었습니다")
                    // 필터 해제로 이전에 숨겨졌던 데이터가 다시 표시되도록 새로고침
                    dataRefreshEvent.emit(DataRefreshEvent.RefreshType.CATEGORY_UPDATED)
                } else {
                    snackbarBus.show("기본 키워드는 삭제할 수 없습니다")
                }
            } catch (e: Exception) {
                snackbarBus.show("삭제 실패: ${e.message}")
            }
        }
    }

    private fun launchBackgroundReclassification() {
        Log.e("sanha", "SettingsViewModel[launchBackgroundReclassification] : === 백그라운드 재분류 시작 ===")
        viewModelScope.launch(Dispatchers.IO) {
            try {
                var totalReclassified = 0

                // Step 1: 저신뢰도 임베딩 재분류
                Log.e("sanha", "SettingsViewModel[launchBackgroundReclassification] : Step 1 - 저신뢰도 항목 재분류 시작")
                val reclassifiedCount = categoryClassifierService.reclassifyLowConfidenceItems()
                totalReclassified += reclassifiedCount
                Log.e("sanha", "SettingsViewModel[launchBackgroundReclassification] : Step 1 완료 - ${reclassifiedCount}건 재분류")

                // Step 2: 미분류 지출 분류
                Log.e("sanha", "SettingsViewModel[launchBackgroundReclassification] : Step 2 - 미분류 지출 분류 시작")
                val classifiedCount = categoryClassifierService.classifyUnclassifiedExpenses()
                totalReclassified += classifiedCount
                Log.e("sanha", "SettingsViewModel[launchBackgroundReclassification] : Step 2 완료 - ${classifiedCount}건 분류")

                Log.e("sanha", "SettingsViewModel[launchBackgroundReclassification] : === 총 ${totalReclassified}건 처리 완료 ===")
                if (totalReclassified > 0) {
                    withContext(Dispatchers.Main) {
                        loadUnclassifiedCount()
                        snackbarBus.show("${totalReclassified}건의 카테고리가 정리되었습니다")
                        dataRefreshEvent.emit(DataRefreshEvent.RefreshType.CATEGORY_UPDATED)
                    }
                } else {
                    Log.e("sanha", "SettingsViewModel[launchBackgroundReclassification] : 재분류 대상 없음 (0건)")
                }
            } catch (e: Exception) {
                Log.e("sanha", "SettingsViewModel[launchBackgroundReclassification] : 실패: ${e.message}", e)
                Log.e(TAG, "백그라운드 재분류 실패: ${e.message}")
            }
        }
    }

}
