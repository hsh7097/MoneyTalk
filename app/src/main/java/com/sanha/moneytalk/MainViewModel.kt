package com.sanha.moneytalk

import com.sanha.moneytalk.core.util.MoneyTalkLogger

import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sanha.moneytalk.core.database.OwnedCardRepository
import com.sanha.moneytalk.core.database.SmsExclusionRepository
import com.sanha.moneytalk.core.database.SyncCoverageRepository
import com.sanha.moneytalk.core.database.SyncCoverageTrigger
import com.sanha.moneytalk.core.database.entity.ExpenseEntity
import com.sanha.moneytalk.core.database.entity.IncomeEntity
import com.sanha.moneytalk.core.database.entity.StoreRuleEntity
import com.sanha.moneytalk.core.database.entity.SyncCoverageEntity
import com.sanha.moneytalk.core.model.Category
import com.sanha.moneytalk.core.model.IncomeCategoryMapper
import com.sanha.moneytalk.core.model.TransferDirection
import com.sanha.moneytalk.core.datastore.SettingsDataStore
import com.sanha.moneytalk.core.firebase.AnalyticsEvent
import com.sanha.moneytalk.core.firebase.AnalyticsHelper
import com.sanha.moneytalk.core.ui.AppSnackbarBus
import com.sanha.moneytalk.core.ui.ClassificationState
import com.sanha.moneytalk.core.util.DataRefreshEvent
import com.sanha.moneytalk.core.util.CardNameNormalizer
import com.sanha.moneytalk.core.util.DateUtils
import com.sanha.moneytalk.core.util.StatsExclusionClassifier
import com.sanha.moneytalk.core.sms.SmsIncomeParser
import com.sanha.moneytalk.core.sms.SmsInput
import com.sanha.moneytalk.core.sms.SmsFilter
import com.sanha.moneytalk.core.sms.DeletedSmsTracker
import com.sanha.moneytalk.core.sms.SmsInstantProcessor
import com.sanha.moneytalk.core.sms.SmsPipeline
import com.sanha.moneytalk.core.sms.SmsSyncMessageReader
import com.sanha.moneytalk.core.sms.SmsSyncCoordinator
import com.sanha.moneytalk.core.sms.SyncStats
import com.sanha.moneytalk.core.sync.SmsSyncRangeCalculator
import com.sanha.moneytalk.core.sync.SyncCoveragePagePolicy
import com.sanha.moneytalk.core.sync.SyncCoverageRecordCounts
import com.sanha.moneytalk.core.sync.SyncCoverageRecorder
import com.sanha.moneytalk.feature.chat.data.GeminiRepository
import com.sanha.moneytalk.feature.home.data.CategoryClassifierService
import com.sanha.moneytalk.feature.home.data.ExpenseRepository
import com.sanha.moneytalk.feature.home.data.IncomeRepository
import com.sanha.moneytalk.feature.home.data.StoreRuleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlin.coroutines.coroutineContext

/**
 * Activity-scoped ViewModel — SMS 동기화 엔진 + resume/권한/광고 통합 관리
 *
 * HomeViewModel에서 동기화 관련 책임을 Activity 레벨로 이동.
 * HomeScreen/HistoryScreen 모두에서 공유되는 동기화, 권한, 광고 상태를 단일 소스로 관리.
 *
 * 주요 기능:
 * - SMS 동기화 (초기/증분/월별)
 * - 앱 resume 시 자동 동기화 + 자동 분류
 * - SMS 권한 상태 관리
 * - 월별 SMS 동기화 CTA (리워드 광고)
 * - AI 성과 요약 (초기 동기화 완료 후)
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val expenseRepository: ExpenseRepository,
    private val incomeRepository: IncomeRepository,
    private val categoryClassifierService: CategoryClassifierService,
    private val smsSyncMessageReader: SmsSyncMessageReader,
    private val settingsDataStore: SettingsDataStore,
    private val dataRefreshEvent: DataRefreshEvent,
    private val ownedCardRepository: OwnedCardRepository,
    private val smsExclusionRepository: SmsExclusionRepository,
    private val syncCoverageRepository: SyncCoverageRepository,
    private val smsSyncRangeCalculator: SmsSyncRangeCalculator,
    private val syncCoverageRecorder: SyncCoverageRecorder,
    private val syncCoveragePagePolicy: SyncCoveragePagePolicy,
    private val geminiRepository: GeminiRepository,
    private val snackbarBus: AppSnackbarBus,
    private val classificationState: ClassificationState,
    private val analyticsHelper: AnalyticsHelper,
    private val rewardAdManager: com.sanha.moneytalk.core.ad.RewardAdManager,
    private val smsSyncCoordinator: SmsSyncCoordinator,
    private val storeRuleRepository: StoreRuleRepository,
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context
) : ViewModel() {

    companion object {

        /** DB 배치 삽입 크기 */
        private const val DB_BATCH_INSERT_SIZE = 100

        /** smsId 존재 여부 조회 chunk 크기 (SQLite bind limit 여유) */
        private const val SMS_ID_LOOKUP_CHUNK_SIZE = 500

        /** 카테고리 분류 최대 반복 횟수 */
        private const val MAX_CLASSIFICATION_ROUNDS = 3

        /** 즉시 저장 후 silent 동기화 전환 판단 윈도우 (60초) */
        private const val INSTANT_SAVE_SILENT_WINDOW_MS = 60_000L

        /** smsId 타임스탬프 오차 허용 범위 */
        private const val FUZZY_TIME_MARGIN_MS = 10_000L

        /** fuzzy dedupe 후보 조회 시 대상 기간 앞뒤로 확장할 범위 */
        private const val FUZZY_CANDIDATE_PADDING_MS = 3L * 24 * 60 * 60 * 1000

    }

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()
    val screenSyncUiState: Flow<ScreenSyncUiState> = uiState
        .map { state ->
            ScreenSyncUiState(
                hasSmsPermission = state.hasSmsPermission,
                hasFreeSyncRemaining = state.hasFreeSyncRemaining,
                isSyncing = state.isSyncing,
                syncedMonths = state.syncedMonths,
                syncCoverageVersion = state.syncCoverageVersion,
                isLegacyFullSyncUnlocked = state.isLegacyFullSyncUnlocked
            )
        }
        .distinctUntilChanged()
    val dialogUiState: Flow<MainDialogUiState> = uiState
        .map { state ->
            MainDialogUiState(
                showSyncDialog = state.showSyncDialog,
                syncProgress = state.syncProgress,
                syncProgressCurrent = state.syncProgressCurrent,
                syncProgressTotal = state.syncProgressTotal,
                syncStepIndex = state.syncStepIndex,
                showEngineSummary = state.showEngineSummary,
                engineSummaryTotalSms = state.engineSummaryTotalSms,
                engineSummaryPatterns = state.engineSummaryPatterns,
                engineSummaryExpenses = state.engineSummaryExpenses,
                engineSummaryIncomes = state.engineSummaryIncomes,
                showFullSyncAdDialog = state.showFullSyncAdDialog,
                fullSyncAdYear = state.fullSyncAdYear,
                fullSyncAdMonth = state.fullSyncAdMonth,
                monthStartDay = state.monthStartDay
            )
        }
        .distinctUntilChanged()

    /** 광고 매니저 접근 (Activity에서 광고 표시에 필요) */
    val adManager: com.sanha.moneytalk.core.ad.RewardAdManager get() = rewardAdManager

    /** 홈 탭 재클릭 → 오늘 페이지로 이동 이벤트 */
    val homeTabReClickEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    /** 내역 탭 재클릭 → 오늘 페이지로 이동 + 필터 초기화 이벤트 */
    val historyTabReClickEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** resume 자동 분류 중복 실행 방지 플래그 */
    private val isResumeClassificationChecking = AtomicBoolean(false)
    /** syncSmsV2 재진입 방지 플래그 (동시 호출 시 중복 수입 방지) */
    private val isSyncRunning = AtomicBoolean(false)
    /** 동기화 중 들어온 silent 재실행 요청 */
    @Volatile
    private var pendingSilentSyncRequest = false
    /** 실제 성공한 동기화 구간 캐시 */
    private var syncCoverageEntries: List<SyncCoverageEntity> = emptyList()
    /** 최초 진입(onCreate) 여부 — 첫 onAppResume 호출 시 초기 동기화 다이얼로그 표시용 */
    private var isFirstLaunch = true

    init {
        loadSettings()
        observeSyncCoverage()
        observeDataRefreshEvents()
        rewardAdManager.preloadAd()
    }

    // ========== 앱 라이프사이클 ==========

    /**
     * Activity의 ON_RESUME에서 호출
     *
     * 1. SMS 권한 상태 갱신
     * 2. 첫 진입이면 초기 동기화 (다이얼로그 표시)
     * 3. 이후 resume에서는 silent 증분 동기화
     * 4. 미분류 항목 있으면 자동 분류 시작
     */
    fun onAppResume() {
        checkSmsPermission()

        val hasSmsPermission = _uiState.value.hasSmsPermission
        var syncTriggered = false

        if (hasSmsPermission && !_uiState.value.isSyncing) {
            val firstLaunch = isFirstLaunch
            isFirstLaunch = false

            // 최근 즉시 저장이 있으면 다이얼로그 없이 백그라운드 동기화
            val recentInstantSave = (System.currentTimeMillis() -
                SmsInstantProcessor.lastInstantSaveTime) < INSTANT_SAVE_SILENT_WINDOW_MS

            if (firstLaunch && !recentInstantSave) {
                launchSync()
                syncTriggered = true
            } else {
                if (firstLaunch && recentInstantSave) {
                    MoneyTalkLogger.i("onAppResume: 최근 즉시 저장 감지 → silent 백그라운드 동기화")
                }
                viewModelScope.launch {
                    val range = withContext(Dispatchers.IO) { calculateIncrementalRange() }
                    syncSmsV2(
                        targetMonthRange = range,
                        updateLastSyncTime = true,
                        silent = true,
                        trigger = SyncCoverageTrigger.APP_RESUME_INCREMENTAL
                    )
                }
                syncTriggered = true
            }
        } else if (!hasSmsPermission) {
            isFirstLaunch = false
        }
        // isSyncing=true일 때는 isFirstLaunch 유지 → 동기화 완료 후 다음 resume에서 재시도

        // 동기화를 이번 resume에서 시작/예약했으면 분류는 동기화 finally에서 재시도.
        if (!syncTriggered) {
            tryResumeClassification()
        }
    }

    /** SMS 권한 상태 확인 및 갱신 */
    private fun checkSmsPermission() {
        val granted = ContextCompat.checkSelfPermission(
            appContext, Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED
        _uiState.update { it.copy(hasSmsPermission = granted) }
    }

    // ========== 설정 로드 ==========

    private fun loadSettings() {
        // monthStartDay (동기화 범위 계산에 필요)
        viewModelScope.launch {
            settingsDataStore.monthStartDayFlow
                .distinctUntilChanged()
                .collect { monthStartDay ->
                    _uiState.update { it.copy(monthStartDay = monthStartDay) }
                }
        }
        // 월별 동기화 완료 상태
        viewModelScope.launch {
            settingsDataStore.syncedMonthsFlow.collect { months ->
                _uiState.update { it.copy(syncedMonths = months) }
            }
        }
        // 레거시 전역 동기화 해제 상태 (FULL_SYNC_UNLOCKED=true 마이그레이션 호환)
        @Suppress("DEPRECATION")
        viewModelScope.launch {
            settingsDataStore.fullSyncUnlockedFlow.collect { unlocked ->
                _uiState.update { it.copy(isLegacyFullSyncUnlocked = unlocked) }
            }
        }
        // 무료 동기화 사용 횟수
        viewModelScope.launch {
            settingsDataStore.freeSyncUsedCountFlow.collect { count ->
                _uiState.update { it.copy(freeSyncUsedCount = count) }
            }
        }
        // 무료 동기화 최대 횟수 (RTDB)
        viewModelScope.launch {
            rewardAdManager.freeSyncCountFlow.collect { maxCount ->
                _uiState.update { it.copy(freeSyncMaxCount = maxCount) }
            }
        }
    }

    private fun observeSyncCoverage() {
        viewModelScope.launch {
            syncCoverageRepository.coverageFlow.collect { coverages ->
                syncCoverageEntries = coverages
                _uiState.update {
                    it.copy(syncCoverageVersion = coverages.hashCode())
                }
            }
        }
    }

    // ========== 전역 이벤트 처리 ==========

    private fun observeDataRefreshEvents() {
        viewModelScope.launch {
            dataRefreshEvent.refreshEvent.collect { event ->
                when (event) {
                    DataRefreshEvent.RefreshType.ALL_DATA_DELETED -> {
                        classificationState.cancelIfRunning()
                        isFirstLaunch = true
                    }

                    DataRefreshEvent.RefreshType.SMS_RECEIVED -> {
                        MoneyTalkLogger.i("SMS 수신 이벤트 → silent 증분 동기화 시작")
                        val range = withContext(Dispatchers.IO) { calculateIncrementalRange() }
                        syncSmsV2(
                            targetMonthRange = range,
                            updateLastSyncTime = true,
                            silent = true,
                            trigger = SyncCoverageTrigger.SMS_RECEIVED_INCREMENTAL
                        )
                    }

                    DataRefreshEvent.RefreshType.DEBUG_FULL_SYNC_ALL_MESSAGES -> {
                        MoneyTalkLogger.i("DEBUG 전체 메시지 동기화 시작")
                        val range = Pair(0L, System.currentTimeMillis())
                        syncSmsV2(
                            targetMonthRange = range,
                            updateLastSyncTime = true,
                            silent = false,
                            trigger = SyncCoverageTrigger.DEBUG_FULL_SYNC
                        )
                    }

                    DataRefreshEvent.RefreshType.DEBUG_SYNC_TODAY_MESSAGES -> {
                        MoneyTalkLogger.i("DEBUG 어제부터 메시지 동기화 시작")
                        val range = Pair(
                            DateUtils.getDaysAgoTimestamp(1),
                            System.currentTimeMillis()
                        )
                        syncSmsV2(
                            targetMonthRange = range,
                            updateLastSyncTime = false,
                            silent = false,
                            trigger = SyncCoverageTrigger.DEBUG_RECENT_SYNC
                        )
                    }

                    else -> { /* CATEGORY_UPDATED, OWNED_CARD_UPDATED, TRANSACTION_ADDED → HomeVM/HistoryVM이 처리 */ }
                }
            }
        }
    }

    // ========== SMS 동기화 (sms 파이프라인) ==========

    /** 동기화 후처리 결과 */
    private data class PostSyncResult(
        val cardNames: List<String>,
        val classifiedCount: Int
    )

    private data class SaveResult(
        val newCount: Int = 0,
        val reconciledCount: Int = 0
    )

    private data class ParsedSmsId(
        val address: String,
        val timestamp: Long,
        val bodyHash: String
    ) {
        val contentKey: String = "${address}_${bodyHash}"
    }

    private data class SmsMatchCandidate(
        val smsId: String,
        val timestamp: Long
    )

    private data class ExistingSmsSnapshot(
        val exactSmsIds: Set<String> = emptySet(),
        val expensesBySmsId: Map<String, ExpenseEntity> = emptyMap(),
        val incomesBySmsId: Map<String, IncomeEntity> = emptyMap(),
        val contentIndex: Map<String, List<SmsMatchCandidate>> = emptyMap()
    )

    /** 동기화 최종 결과 */
    private data class SyncResult(
        val expenseCount: Int,
        val incomeCount: Int,
        val reconciledExpenseCount: Int = 0,
        val reconciledIncomeCount: Int = 0,
        val detectedCardNames: List<String>,
        val classifiedCount: Int,
        /** 파이프라인 엔진 통계 (초기 동기화 요약 카드용) */
        val stats: SyncStats = SyncStats()
    )

    /**
     * SMS 동기화 (초기/증분 공통)
     *
     * 초기 동기화: fullRange(전월 1일~현재) 전체를 한 번에 처리 + 완료 후 AI 성과 요약
     * 증분 동기화: lastSyncTime 이후 ~ 현재까지 처리
     */
    private fun launchSync() {
        if (!isSyncRunning.compareAndSet(false, true)) {
            MoneyTalkLogger.w("launchSync: 이미 동기화 진행 중 → 스킵")
            return
        }

        analyticsHelper.logClick(AnalyticsEvent.SCREEN_HOME, AnalyticsEvent.CLICK_SYNC_SMS)

        viewModelScope.launch {
            try {
                val fullRange = withContext(Dispatchers.IO) { calculateIncrementalRange() }
                val isInitialSync = withContext(Dispatchers.IO) { settingsDataStore.getLastSyncTime() == 0L }

                _uiState.update {
                    it.copy(
                        isSyncing = true,
                        showSyncDialog = true,
                        syncDialogDismissed = false,
                        syncProgress = "문자 읽는 중...",
                        syncProgressCurrent = 0,
                        syncProgressTotal = 0,
                        syncStepIndex = 0
                    )
                }
                val result = withContext(Dispatchers.IO) {
                    syncSmsV2Internal(fullRange, updateLastSyncTime = true, silent = false)
                }
                recordSuccessfulSyncCoverage(
                    targetMonthRange = fullRange,
                    trigger = SyncCoverageTrigger.AUTO_INITIAL,
                    result = result
                )

                if (isInitialSync) {
                    // 초기 동기화 완료 → 카드 자동 등록 + 데이터 변경 통지 + AI 성과 요약
                    if (result.detectedCardNames.isNotEmpty()) {
                        viewModelScope.launch(Dispatchers.IO) {
                            try {
                                ownedCardRepository.registerCardsFromSync(result.detectedCardNames)
                            } catch (e: Exception) {
                                MoneyTalkLogger.w("카드 자동 등록 실패: ${e.message}")
                            }
                        }
                    }
                    notifyDataChanged()

                    val hasData = result.expenseCount > 0 || result.incomeCount > 0
                    val dialogWasDismissed = _uiState.value.syncDialogDismissed

                    if (hasData && !dialogWasDismissed) {
                        _uiState.update {
                            it.copy(
                                isSyncing = false,
                                showSyncDialog = false,
                                showEngineSummary = true,
                                engineSummaryTotalSms = result.stats.totalInput,
                                engineSummaryPatterns = result.stats.newPatternsCreated,
                                engineSummaryExpenses = result.expenseCount,
                                engineSummaryIncomes = result.incomeCount
                            )
                        }
                    } else {
                        _uiState.update {
                            it.copy(
                                isSyncing = false,
                                showSyncDialog = false,
                                syncProgress = "",
                                syncProgressCurrent = 0,
                                syncProgressTotal = 0,
                                syncStepIndex = 0
                            )
                        }
                        if (hasData) {
                            snackbarBus.show(buildResultMessage(result.expenseCount, result.incomeCount))
                        }
                    }
                } else {
                    handleSyncResult(
                        result = result,
                        silent = false,
                        showNoDataMessage = false
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleSyncError(e, silent = false)
            } finally {
                isSyncRunning.set(false)
                tryResumeClassification()
                drainPendingSilentSyncIfNeeded()
            }
        }
    }

    private suspend fun recordSuccessfulSyncCoverage(
        targetMonthRange: Pair<Long, Long>,
        trigger: SyncCoverageTrigger,
        result: SyncResult
    ) {
        syncCoverageRecorder.recordSuccessfulRange(
            range = targetMonthRange,
            trigger = trigger,
            counts = SyncCoverageRecordCounts(
                expenseCount = result.expenseCount,
                incomeCount = result.incomeCount,
                reconciledExpenseCount = result.reconciledExpenseCount,
                reconciledIncomeCount = result.reconciledIncomeCount
            )
        )
    }

    /** 결과 메시지 빌드 헬퍼 */
    private fun buildResultMessage(expenseCount: Int, incomeCount: Int): String = when {
        expenseCount > 0 && incomeCount > 0 ->
            "${expenseCount}건의 지출, ${incomeCount}건의 수입이 추가되었습니다"
        expenseCount > 0 ->
            "${expenseCount}건의 새 지출이 추가되었습니다"
        incomeCount > 0 ->
            "${incomeCount}건의 새 수입이 추가되었습니다"
        else -> "새로운 내역이 없습니다"
    }

    /**
     * 증분 동기화 (앱 resume, 동기화 버튼)
     *
     * lastSyncTime 기반으로 범위를 자동 계산하여 syncSmsV2 호출.
     * Screen에서 직접 호출하는 간편 래퍼.
     */
    fun syncIncremental() {
        viewModelScope.launch {
            val range = withContext(Dispatchers.IO) { calculateIncrementalRange() }
            syncSmsV2(
                targetMonthRange = range,
                updateLastSyncTime = true,
                trigger = SyncCoverageTrigger.MANUAL_INCREMENTAL
            )
        }
    }

    /**
     * SMS 동기화 (sms 파이프라인)
     *
     * 기존 호출부(syncIncremental, unlockFullSync 등) 호환 유지.
     * 내부적으로 syncSmsV2Internal()을 호출.
     *
     * @param targetMonthRange 동기화 대상 기간 (startMillis, endMillis)
     * @param updateLastSyncTime true면 동기화 후 lastSyncTime 갱신 (증분=true, 월별=false)
     * @param silent true면 다이얼로그/진행 상태 표시 안함
     * @param onSyncComplete 동기화 성공 완료 시 추가 콜백 (월별 해제 마킹 등)
     */
    fun syncSmsV2(
        targetMonthRange: Pair<Long, Long>,
        updateLastSyncTime: Boolean = true,
        silent: Boolean = false,
        trigger: SyncCoverageTrigger = SyncCoverageTrigger.MANUAL_INCREMENTAL,
        onSyncComplete: (suspend () -> Unit)? = null
    ) {
        if (!isSyncRunning.compareAndSet(false, true)) {
            if (silent) {
                pendingSilentSyncRequest = true
                MoneyTalkLogger.i("syncSmsV2: 이미 동기화 진행 중 → silent 재실행 예약")
            }
            MoneyTalkLogger.w("syncSmsV2: 이미 동기화 진행 중 → 스킵")
            return
        }

        if (!silent) {
            analyticsHelper.logClick(AnalyticsEvent.SCREEN_HOME, AnalyticsEvent.CLICK_SYNC_SMS)
        }
        viewModelScope.launch {
            if (!silent) {
                _uiState.update {
                    it.copy(
                        isSyncing = true,
                        showSyncDialog = true,
                        syncDialogDismissed = false,
                        syncProgress = "문자 읽는 중...",
                        syncProgressCurrent = 0,
                        syncProgressTotal = 0,
                        syncStepIndex = 0
                    )
                }
            } else {
                _uiState.update { it.copy(isSyncing = true) }
            }

            try {
                val result = withContext(Dispatchers.IO) {
                    syncSmsV2Internal(targetMonthRange, updateLastSyncTime, silent)
                }

                recordSuccessfulSyncCoverage(targetMonthRange, trigger, result)
                handleSyncResult(result, silent)
                onSyncComplete?.invoke()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleSyncError(e, silent)
            } finally {
                isSyncRunning.set(false)
                tryResumeClassification()
                drainPendingSilentSyncIfNeeded()
            }
        }
    }

    private fun drainPendingSilentSyncIfNeeded() {
        if (!pendingSilentSyncRequest) return
        pendingSilentSyncRequest = false
        viewModelScope.launch {
            val range = withContext(Dispatchers.IO) { calculateIncrementalRange() }
            syncSmsV2(
                targetMonthRange = range,
                updateLastSyncTime = true,
                silent = true,
                trigger = SyncCoverageTrigger.PENDING_SILENT_INCREMENTAL
            )
        }
    }

    /**
     * SMS 동기화 내부 실행 (순수 suspend 함수)
     *
     * isSyncRunning, viewModelScope.launch 관리 없이 순수 로직만 실행.
     */
    private suspend fun syncSmsV2Internal(
        targetMonthRange: Pair<Long, Long>,
        updateLastSyncTime: Boolean,
        silent: Boolean
    ): SyncResult {
        // 제외 키워드 설정
        val userExcludeKeywords = smsExclusionRepository.getUserKeywords()
        smsSyncCoordinator.setUserExcludeKeywords(userExcludeKeywords)
        SmsIncomeParser.setUserExcludeKeywords(userExcludeKeywords)

        // Step 1: SMS 읽기 + 중복 제거
        val allSmsList = readSmsInputs(targetMonthRange)
        if (allSmsList.isEmpty()) {
            if (updateLastSyncTime) {
                settingsDataStore.saveLastSyncTime(targetMonthRange.second)
            }
            return SyncResult(
                expenseCount = 0,
                incomeCount = 0,
                detectedCardNames = emptyList(),
                classifiedCount = 0
            )
        }

        _uiState.update { it.copy(syncProgress = "이미 등록된 내역 확인 중...") }
        val existingSnapshot = buildExistingSmsSnapshot(allSmsList, targetMonthRange)
        val pendingReconciliationIds = SmsInstantProcessor.snapshotPendingReconciliationIds()
        val pendingContentIndex = buildSmsIdCandidateIndex(pendingReconciliationIds)
        val smsInputs = readAndFilterSms(
            allSmsList = allSmsList,
            pendingContentIndex = pendingContentIndex,
            existingSnapshot = existingSnapshot
        )
        MoneyTalkLogger.i("syncSmsV2 Step1 완료: 신규 SMS ${smsInputs.size}건")
        if (smsInputs.isEmpty()) {
            if (updateLastSyncTime) {
                settingsDataStore.saveLastSyncTime(targetMonthRange.second)
            }
            return SyncResult(
                expenseCount = 0,
                incomeCount = 0,
                detectedCardNames = emptyList(),
                classifiedCount = 0
            )
        }

        // Step 2: sms 파이프라인 실행
        val syncResult = processSmsPipeline(smsInputs, silent)

        val reconciledExpenseIds = syncResult.expenses
            .flatMap { parsed -> findMatchingSmsIds(parsed.input.id, pendingContentIndex) }
            .toSet()
        val reconciledIncomeIds = syncResult.incomes
            .flatMap { income -> findMatchingSmsIds(income.id, pendingContentIndex) }
            .toSet()

        // Step 3: DB 저장
        val expenseSaveResult = saveExpenses(syncResult.expenses, existingSnapshot)
        val incomeSaveResult = saveIncomes(syncResult.incomes, existingSnapshot)

        // Step 4: 후처리 (카테고리 분류, 패턴 정리, lastSyncTime 갱신)
        val cleanup = postSyncCleanup(updateLastSyncTime, targetMonthRange.second)
        SmsInstantProcessor.clearPendingReconciliationIds(reconciledExpenseIds + reconciledIncomeIds)

        return SyncResult(
            expenseCount = expenseSaveResult.newCount,
            incomeCount = incomeSaveResult.newCount,
            reconciledExpenseCount = expenseSaveResult.reconciledCount,
            reconciledIncomeCount = incomeSaveResult.reconciledCount,
            detectedCardNames = cleanup.cardNames,
            classifiedCount = cleanup.classifiedCount,
            stats = syncResult.stats
        )
    }

    /**
     * 대상 기간의 SMS 입력 목록을 읽는다.
     */
    private suspend fun readSmsInputs(
        targetMonthRange: Pair<Long, Long>
    ): List<SmsInput> {
        return smsSyncMessageReader.read(targetMonthRange)
    }

    /**
     * 읽은 SMS 목록을 기존 내역 및 pending 상태와 비교해 신규 처리 대상만 남긴다.
     */
    private fun readAndFilterSms(
        allSmsList: List<SmsInput>,
        pendingContentIndex: Map<String, List<SmsMatchCandidate>>,
        existingSnapshot: ExistingSmsSnapshot
    ): List<SmsInput> {
        val newSmsList = allSmsList.filter { sms ->
            // 사용자가 명시적으로 삭제한 SMS는 재처리하지 않음
            if (DeletedSmsTracker.isDeleted(sms.id)) return@filter false

            val contentKey = buildContentKey(sms.address, sms.body)
            val existsInDbExact = sms.id in existingSnapshot.exactSmsIds
            val existsInDbFuzzy = findClosestCandidate(
                contentKey = contentKey,
                timestamp = sms.date,
                candidateIndex = existingSnapshot.contentIndex
            ) != null
            val existsInDb = existsInDbExact || existsInDbFuzzy

            val existsInPending = findClosestCandidate(
                contentKey = contentKey,
                timestamp = sms.date,
                candidateIndex = pendingContentIndex
            ) != null

            when {
                existsInDb && existsInPending -> true
                existsInDb -> false
                else -> true
            }
        }
        MoneyTalkLogger.i("syncSmsV2 중복 제거: ${allSmsList.size}건 → ${newSmsList.size}건")

        return newSmsList
    }

    /**
     * 현재 읽은 SMS 기준으로 기존 DB 스냅샷을 구성한다.
     *
     * exact dedupe는 chunk 조회로 유지하고,
     * fuzzy dedupe 후보는 대상 기간 주변의 기존 거래만 읽어 메모리 사용을 제한한다.
     */
    private suspend fun buildExistingSmsSnapshot(
        allSmsList: List<SmsInput>,
        targetMonthRange: Pair<Long, Long>
    ): ExistingSmsSnapshot {
        if (allSmsList.isEmpty()) return ExistingSmsSnapshot()

        val smsIdChunks = allSmsList
            .map { it.id }
            .distinct()
            .chunked(SMS_ID_LOOKUP_CHUNK_SIZE)

        val exactSmsIds = coroutineScope {
            val expenseExistingDeferred = async {
                val ids = HashSet<String>()
                for (chunk in smsIdChunks) {
                    ids.addAll(expenseRepository.getExistingSmsIds(chunk))
                }
                ids
            }
            val incomeExistingDeferred = async {
                val ids = HashSet<String>()
                for (chunk in smsIdChunks) {
                    ids.addAll(incomeRepository.getExistingSmsIds(chunk))
                }
                ids
            }
            expenseExistingDeferred.await() + incomeExistingDeferred.await()
        }

        val minSmsTimestamp = allSmsList.minOf { it.date }
        val maxSmsTimestamp = allSmsList.maxOf { it.date }
        val candidateStart = maxOf(
            0L,
            minOf(targetMonthRange.first, minSmsTimestamp) - FUZZY_CANDIDATE_PADDING_MS
        )
        val candidateEnd = maxOf(targetMonthRange.second, maxSmsTimestamp) + FUZZY_CANDIDATE_PADDING_MS
        val existingData = coroutineScope {
            val expensesDeferred = async {
                expenseRepository.getExpensesByDateRangeOnce(candidateStart, candidateEnd)
            }
            val incomesDeferred = async {
                incomeRepository.getIncomesByDateRangeOnce(candidateStart, candidateEnd)
            }
            expensesDeferred.await() to incomesDeferred.await()
        }

        val existingExpenses = existingData.first
        val existingIncomes = existingData.second

        return ExistingSmsSnapshot(
            exactSmsIds = exactSmsIds,
            expensesBySmsId = existingExpenses.associateBy { it.smsId },
            incomesBySmsId = existingIncomes
                .mapNotNull { income -> income.smsId?.let { it to income } }
                .toMap(),
            contentIndex = buildExistingContentIndex(existingExpenses, existingIncomes)
        )
    }

    /**
     * smsId 목록을 fuzzy dedupe용 content index로 변환한다.
     */
    private fun buildSmsIdCandidateIndex(
        smsIds: Collection<String>
    ): Map<String, List<SmsMatchCandidate>> {
        return smsIds.mapNotNull { smsId ->
            parseSmsId(smsId)?.let { parsed ->
                parsed.contentKey to SmsMatchCandidate(
                    smsId = smsId,
                    timestamp = parsed.timestamp
                )
            }
        }.groupBy({ it.first }, { it.second })
    }

    private fun buildExistingContentIndex(
        expenses: List<ExpenseEntity>,
        incomes: List<IncomeEntity>
    ): Map<String, List<SmsMatchCandidate>> {
        val entries = mutableListOf<Pair<String, SmsMatchCandidate>>()

        expenses.forEach { expense ->
            parseSmsId(expense.smsId)?.let { parsed ->
                entries += parsed.contentKey to SmsMatchCandidate(
                    smsId = expense.smsId,
                    timestamp = parsed.timestamp
                )
            }
        }

        incomes.forEach { income ->
            val smsId = income.smsId ?: return@forEach
            parseSmsId(smsId)?.let { parsed ->
                entries += parsed.contentKey to SmsMatchCandidate(
                    smsId = smsId,
                    timestamp = parsed.timestamp
                )
            }
        }

        return entries.groupBy({ it.first }, { it.second })
    }

    private fun parseSmsId(smsId: String): ParsedSmsId? {
        val lastSeparator = smsId.lastIndexOf('_')
        if (lastSeparator <= 0 || lastSeparator == smsId.lastIndex) return null

        val secondLastSeparator = smsId.lastIndexOf('_', startIndex = lastSeparator - 1)
        if (secondLastSeparator <= 0 || secondLastSeparator == lastSeparator - 1) return null

        val address = smsId.substring(0, secondLastSeparator)
        val timestamp = smsId.substring(secondLastSeparator + 1, lastSeparator).toLongOrNull()
            ?: return null
        val bodyHash = smsId.substring(lastSeparator + 1)
        if (bodyHash.isBlank()) return null

        return ParsedSmsId(
            address = address,
            timestamp = timestamp,
            bodyHash = bodyHash
        )
    }

    private fun buildContentKey(address: String, body: String): String =
        "${SmsFilter.normalizeAddress(address)}_${body.hashCode()}"

    private fun findClosestCandidate(
        contentKey: String,
        timestamp: Long,
        candidateIndex: Map<String, List<SmsMatchCandidate>>
    ): SmsMatchCandidate? {
        val candidates = candidateIndex[contentKey] ?: return null
        var closestCandidate: SmsMatchCandidate? = null
        var closestDiff = Long.MAX_VALUE

        for (candidate in candidates) {
            val diff = abs(candidate.timestamp - timestamp)
            if (diff <= FUZZY_TIME_MARGIN_MS && diff < closestDiff) {
                closestCandidate = candidate
                closestDiff = diff
            }
        }

        return closestCandidate
    }

    private fun findMatchingSmsIds(
        smsId: String,
        candidateIndex: Map<String, List<SmsMatchCandidate>>
    ): Set<String> {
        val parsed = parseSmsId(smsId) ?: return emptySet()
        val candidates = candidateIndex[parsed.contentKey] ?: return emptySet()
        val matches = mutableSetOf<String>()

        for (candidate in candidates) {
            if (abs(candidate.timestamp - parsed.timestamp) <= FUZZY_TIME_MARGIN_MS) {
                matches += candidate.smsId
            }
        }

        return matches
    }

    private fun supportsFixedExpense(expense: ExpenseEntity): Boolean {
        return expense.transactionType == "EXPENSE" ||
            expense.transactionType == "TRANSFER"
    }

    /**
     * sms 파이프라인 실행 (SmsSyncCoordinator.process)
     *
     * @param silent true면 dataRefreshEvent에 진행 상태를 전파하지 않음
     */
    private suspend fun processSmsPipeline(
        smsInputs: List<SmsInput>,
        silent: Boolean = false
    ): com.sanha.moneytalk.core.sms.SyncResult {
        if (!silent) {
            _uiState.update {
                it.copy(
                    syncProgress = "내역 분석 중...",
                    syncProgressTotal = smsInputs.size
                )
            }
        }
        categoryClassifierService.initCategoryCache()

        return smsSyncCoordinator.process(smsInputs) { stepIndex, step, current, total ->
            if (!silent) {
                _uiState.update {
                    it.copy(
                        syncStepIndex = stepIndex,
                        syncProgress = step,
                        syncProgressCurrent = current,
                        syncProgressTotal = total
                    )
                }
            }
        }
    }

    /**
     * 지출 파싱 결과를 ExpenseEntity로 변환하여 DB에 배치 저장
     *
     * 카테고리 분류를 DB INSERT 전에 완료하여 UI 깜빡임을 방지합니다.
     * Phase 1: 로컬 분류 (캐시 + 키워드)
     * Phase 2: Gemini 사전 분류 ("미분류" 항목을 API로 분류)
     * Phase 3: 분류 완료된 엔티티를 DB에 배치 저장
     */
    private suspend fun saveExpenses(
        expenses: List<com.sanha.moneytalk.core.sms.SmsParseResult>,
        existingSnapshot: ExistingSmsSnapshot
    ): SaveResult {
        if (expenses.isEmpty()) return SaveResult()

        _uiState.update { it.copy(syncStepIndex = SmsPipeline.STEP_SAVE, syncProgress = "지출 저장 중...") }

        // Phase 1: 엔티티 빌드 + 로컬 분류
        val entities = ArrayList<ExpenseEntity>(expenses.size)
        for (parsed in expenses) {
            val localCategory = if (parsed.analysis.category.isNotBlank() &&
                parsed.analysis.category != "미분류" &&
                parsed.analysis.category != "기타"
            ) {
                parsed.analysis.category
            } else {
                categoryClassifierService.getCategory(
                    storeName = parsed.analysis.storeName,
                    originalSms = parsed.input.body
                )
            }

            entities.add(
                ExpenseEntity(
                    amount = parsed.analysis.amount,
                    storeName = parsed.analysis.storeName,
                    category = localCategory,
                    cardName = CardNameNormalizer.normalizeWithFallback(parsed.analysis.cardName, parsed.input.body),
                    dateTime = DateUtils.parseDateTime(parsed.analysis.dateTime),
                    originalSms = parsed.input.body,
                    smsId = parsed.input.id,
                    senderAddress = SmsFilter.normalizeAddress(parsed.input.address)
                )
            )
        }

        // Phase 1.5: StoreRule 적용 (최우선 = Tier 0)
        val allRules = storeRuleRepository.getAllOnce()
        fun findMatchingStoreRule(storeName: String): StoreRuleEntity? {
            val lowerStore = storeName.lowercase()
            return allRules
                .filter { lowerStore.contains(it.keyword.lowercase()) }
                .maxWithOrNull(
                    compareBy<StoreRuleEntity>({ it.keyword.length }, { it.createdAt })
                )
        }

        fun resolveStatsExclusion(entity: ExpenseEntity): Boolean {
            return findMatchingStoreRule(entity.storeName)?.isExcludedFromStats
                ?: StatsExclusionClassifier.shouldExcludeExpense(entity)
        }

        if (allRules.isNotEmpty()) {
            for (i in entities.indices) {
                val entity = entities[i]
                val matchedRule = findMatchingStoreRule(entity.storeName)
                if (matchedRule != null) {
                    entities[i] = entity.copy(
                        category = matchedRule.category ?: entity.category,
                        isFixed = if (supportsFixedExpense(entity)) {
                            matchedRule.isFixed ?: entity.isFixed
                        } else {
                            entity.isFixed
                        },
                        isExcludedFromStats = matchedRule.isExcludedFromStats ?: entity.isExcludedFromStats
                    )
                }
            }
        }

        // Phase 2: "미분류" 가게명을 Gemini로 사전 분류 (DB INSERT 전)
        val unclassifiedStores = entities
            .filter { it.category == "미분류" }
            .map { it.storeName }
            .distinct()

        if (unclassifiedStores.isNotEmpty() && geminiRepository.hasApiKey()) {
            _uiState.update {
                it.copy(syncProgress = "AI가 카테고리 분류 중...")
            }
            try {
                val geminiResults = categoryClassifierService.classifyStoreNamesInMemory(
                    storeNames = unclassifiedStores,
                    onStepProgress = { step, current, total ->
                        _uiState.update {
                            it.copy(
                                syncProgress = "AI가 카테고리 분류 중...\n$step",
                                syncProgressCurrent = current,
                                syncProgressTotal = total
                            )
                        }
                    }
                )

                if (geminiResults.isNotEmpty()) {
                    for (i in entities.indices) {
                        val entity = entities[i]
                        if (entity.category == "미분류") {
                            val newCategory = geminiResults[entity.storeName]
                            if (newCategory != null) {
                                val isTransfer = newCategory == Category.TRANSFER_GENERAL.displayName
                                val updated = entity.copy(
                                    category = newCategory,
                                    transactionType = if (isTransfer) "TRANSFER" else entity.transactionType,
                                    transferDirection = if (isTransfer) TransferDirection.WITHDRAWAL.dbValue else entity.transferDirection
                                )
                                entities[i] = updated.copy(
                                    isExcludedFromStats = resolveStatsExclusion(updated)
                                )
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                MoneyTalkLogger.w("사전 카테고리 분류 실패 (무시): ${e.message}")
            }
        }

        val smsIds = entities.map { it.smsId }.distinct()
        val existingExpensesBySmsId = expenseRepository.getExpensesBySmsIds(smsIds)
            .associateBy { it.smsId }
        val existingIncomesBySmsId = incomeRepository.getIncomesBySmsIds(smsIds)
            .mapNotNull { income -> income.smsId?.let { it to income } }
            .toMap()
        val crossTypeIncomeIdsToDelete = mutableSetOf<Long>()
        val isNewFlags = BooleanArray(entities.size)
        var newCount = 0
        var reconciledCount = 0

        for (i in entities.indices) {
            val entity = entities[i]

            // 1. 정확한 ID로 먼저 확인
            var existingExpense = existingExpensesBySmsId[entity.smsId]
            var existingIncome = existingIncomesBySmsId[entity.smsId]

            // 2. 정확한 ID가 없으면 Fuzzy 매칭 시도
            if (existingExpense == null && existingIncome == null) {
                val parsedSmsId = parseSmsId(entity.smsId)
                if (parsedSmsId != null) {
                    val fuzzyMatch = findClosestCandidate(
                        contentKey = buildContentKey(entity.senderAddress, entity.originalSms),
                        timestamp = parsedSmsId.timestamp,
                        candidateIndex = existingSnapshot.contentIndex
                    )
                    val fuzzyId = fuzzyMatch?.smsId
                    if (fuzzyId != null) {
                        existingExpense = existingSnapshot.expensesBySmsId[fuzzyId]
                        if (existingExpense == null) {
                            existingIncome = existingSnapshot.incomesBySmsId[fuzzyId]
                        }
                    }
                }
            }

            if (existingExpense != null || existingIncome != null) {
                reconciledCount++
            } else {
                isNewFlags[i] = true
                newCount++
            }

            if (existingIncome != null) {
                crossTypeIncomeIdsToDelete += existingIncome.id
            }

            if (existingExpense != null) {
                entities[i] = entity.copy(
                    id = existingExpense.id,
                    memo = existingExpense.memo,
                    isExcludedFromStats = existingExpense.isExcludedFromStats,
                    createdAt = existingExpense.createdAt
                )
            } else {
                entities[i] = entity.copy(
                    isExcludedFromStats = resolveStatsExclusion(entity)
                )
            }
        }

        crossTypeIncomeIdsToDelete.forEach { incomeRepository.deleteById(it) }

        // Phase 3: 사용자가 삭제한 항목 제외 후 DB에 배치 저장
        val filteredEntities = entities.filterNot { DeletedSmsTracker.isDeleted(it.smsId) }
        _uiState.update { it.copy(syncProgress = "지출 저장 중...") }
        for (chunk in filteredEntities.chunked(DB_BATCH_INSERT_SIZE)) {
            expenseRepository.insertAll(chunk)
        }

        return SaveResult(
            newCount = newCount,
            reconciledCount = reconciledCount
        )
    }

    /**
     * 수입 SMS를 SmsIncomeParser로 파싱하여 DB에 배치 저장
     */
    private suspend fun saveIncomes(
        incomes: List<SmsInput>,
        existingSnapshot: ExistingSmsSnapshot
    ): SaveResult {
        if (incomes.isEmpty()) return SaveResult()

        _uiState.update { it.copy(syncProgress = "수입 처리 중...") }
        val batch = mutableListOf<IncomeEntity>()
        val batchSmsIds = mutableSetOf<String>()
        var newCount = 0
        var reconciledCount = 0

        for (income in incomes) {
            try {
                val amount = SmsIncomeParser.extractIncomeAmount(income.body)
                val incomeType = SmsIncomeParser.extractIncomeType(income.body)
                val source = SmsIncomeParser.extractIncomeSource(income.body)
                val dateTime = SmsIncomeParser.extractDateTime(income.body, income.date)

                if (amount > 0) {
                    batchSmsIds += income.id
                    batch.add(
                        IncomeEntity(
                            smsId = income.id,
                            amount = amount,
                            type = incomeType,
                            source = source,
                            description = if (source.isNotBlank()) "${source}에서 $incomeType" else incomeType,
                            isRecurring = incomeType == "급여",
                            dateTime = DateUtils.parseDateTime(dateTime),
                            originalSms = income.body,
                            senderAddress = SmsFilter.normalizeAddress(income.address),
                            category = IncomeCategoryMapper.categoryForType(incomeType)
                        )
                    )
                }
            } catch (e: Exception) {
                MoneyTalkLogger.e("수입 처리 실패: ${income.id} - ${e.message}")
            }
        }

        val existingIncomesBySmsId = incomeRepository.getIncomesBySmsIds(batchSmsIds.toList())
            .mapNotNull { income -> income.smsId?.let { it to income } }
            .toMap()
        val existingExpensesBySmsId = expenseRepository.getExpensesBySmsIds(batchSmsIds.toList())
            .associateBy { it.smsId }
        val crossTypeExpenseIdsToDelete = mutableSetOf<Long>()
        val isNewFlags = BooleanArray(batch.size)

        for (i in batch.indices) {
            val entity = batch[i]
            val smsId = entity.smsId ?: continue

            var existingIncome = existingIncomesBySmsId[smsId]
            var existingExpense = existingExpensesBySmsId[smsId]

            // Fuzzy 매칭
            if (existingIncome == null && existingExpense == null) {
                val parsedSmsId = parseSmsId(smsId)
                if (parsedSmsId != null) {
                    val fuzzyMatch = findClosestCandidate(
                        contentKey = buildContentKey(entity.senderAddress, entity.originalSms.orEmpty()),
                        timestamp = parsedSmsId.timestamp,
                        candidateIndex = existingSnapshot.contentIndex
                    )
                    val fuzzyId = fuzzyMatch?.smsId
                    if (fuzzyId != null) {
                        existingIncome = existingSnapshot.incomesBySmsId[fuzzyId]
                        if (existingIncome == null) {
                            existingExpense = existingSnapshot.expensesBySmsId[fuzzyId]
                        }
                    }
                }
            }

            if (existingIncome != null || existingExpense != null) {
                reconciledCount++
            } else {
                isNewFlags[i] = true
                newCount++
            }

            if (existingExpense != null) {
                crossTypeExpenseIdsToDelete += existingExpense.id
            }

            if (existingIncome != null) {
                batch[i] = entity.copy(
                    id = existingIncome.id,
                    memo = existingIncome.memo,
                    recurringDay = existingIncome.recurringDay,
                    createdAt = existingIncome.createdAt
                )
            }
        }

        crossTypeExpenseIdsToDelete.forEach { expenseRepository.deleteById(it) }

        // 사용자가 삭제한 항목 제외 후 저장
        val filteredBatch = batch.filterNot { entity ->
            entity.smsId?.let { DeletedSmsTracker.isDeleted(it) } == true
        }
        if (filteredBatch.isNotEmpty()) {
            for (chunk in filteredBatch.chunked(DB_BATCH_INSERT_SIZE)) {
                incomeRepository.insertAll(chunk)
            }
        }

        return SaveResult(
            newCount = newCount,
            reconciledCount = reconciledCount
        )
    }
    /**
     * 동기화 후처리 (카테고리 캐시 정리, lastSyncTime 갱신)
     *
     * 카테고리 분류는 saveExpenses()에서 DB INSERT 전에 완료하므로 여기서는 생략합니다.
     * 잔여 미분류 항목은 tryResumeClassification()에서 백그라운드로 처리됩니다.
     */
    private suspend fun postSyncCleanup(
        updateLastSyncTime: Boolean,
        endTime: Long
    ): PostSyncResult {
        _uiState.update { it.copy(syncProgress = "마무리 중...") }
        categoryClassifierService.flushPendingMappings()
        categoryClassifierService.clearCategoryCache()

        if (updateLastSyncTime) {
            settingsDataStore.saveLastSyncTime(endTime)
        }

        val allCardNames = expenseRepository.getAllCardNamesWithDuplicates()

        return PostSyncResult(
            cardNames = allCardNames,
            classifiedCount = 0
        )
    }

    /**
     * 증분 동기화용 시간 범위 계산
     *
     * - lastSyncTime이 있으면: lastSyncTime ~ now (증분)
     * - lastSyncTime이 없으면 (초기): monthStartDay 기준 초기 동기화 범위
     *
     * Auto Backup 감지: savedSyncTime > 0 이지만 DB 비어있으면 초기 상태로 리셋.
     */
    private suspend fun calculateIncrementalRange(): Pair<Long, Long> {
        return smsSyncRangeCalculator.calculateIncrementalRange(_uiState.value.monthStartDay)
    }

    /** 동기화 결과 처리 (UI 상태 업데이트 + snackbar + 데이터 변경 통지) */
    private suspend fun handleSyncResult(
        result: SyncResult,
        silent: Boolean,
        showNoDataMessage: Boolean = true
    ) {
        MoneyTalkLogger.i(
            "syncSmsV2 완료: 신규 지출 ${result.expenseCount}건, 신규 수입 ${result.incomeCount}건, " +
                "교체 지출 ${result.reconciledExpenseCount}건, 교체 수입 ${result.reconciledIncomeCount}건"
        )

        // 카드 자동 등록 (백그라운드)
        if (result.detectedCardNames.isNotEmpty()) {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    ownedCardRepository.registerCardsFromSync(result.detectedCardNames)
                } catch (e: Exception) {
                    MoneyTalkLogger.w("카드 자동 등록 실패: ${e.message}")
                }
            }
        }

        // 실제 데이터 변경이 있을 때만 HomeVM/HistoryVM에 통지 (불필요한 UI 갱신 방지)
        val hasDataChange = result.expenseCount > 0 || result.incomeCount > 0 ||
            result.reconciledExpenseCount > 0 || result.reconciledIncomeCount > 0
        if (hasDataChange) {
            notifyDataChanged()
        }

        val resultMessage = buildResultMessage(result.expenseCount, result.incomeCount)

        if (silent || _uiState.value.syncDialogDismissed) {
            _uiState.update { it.copy(isSyncing = false) }
            if (result.expenseCount > 0 || result.incomeCount > 0) {
                snackbarBus.show(resultMessage)
            }
        } else {
            _uiState.update {
                it.copy(
                    isSyncing = false,
                    showSyncDialog = false,
                    syncProgress = "",
                    syncProgressCurrent = 0,
                    syncProgressTotal = 0,
                    syncStepIndex = 0
                )
            }
            if (result.expenseCount > 0 || result.incomeCount > 0) {
                snackbarBus.show(resultMessage)
            } else if (showNoDataMessage) {
                snackbarBus.show(appContext.getString(R.string.sync_no_data))
            }
        }
    }

    /** 동기화 에러 처리 */
    private fun handleSyncError(e: Exception, silent: Boolean) {
        categoryClassifierService.clearCategoryCache()

        if (silent || _uiState.value.syncDialogDismissed) {
            _uiState.update { it.copy(isSyncing = false) }
            MoneyTalkLogger.w("SMS 동기화 실패: ${e.message}")
        } else {
            _uiState.update {
                it.copy(
                    isSyncing = false,
                    showSyncDialog = false,
                    syncProgress = "",
                    syncProgressCurrent = 0,
                    syncProgressTotal = 0,
                    syncStepIndex = 0
                )
            }
            snackbarBus.show("동기화 실패: ${e.message}")
        }
    }

    /**
     * 동기화 다이얼로그 dismiss (백그라운드에서 계속)
     *
     * 다이얼로그만 닫고 동기화는 계속 진행.
     * 완료 시 snackbar로 결과 표시.
     */
    fun dismissSyncDialog() {
        _uiState.update {
            it.copy(
                showSyncDialog = false,
                syncDialogDismissed = true
            )
        }
    }

    /** AI 성과 요약 카드 dismiss */
    fun dismissEngineSummary() {
        _uiState.update { it.copy(showEngineSummary = false) }
    }

    /** HomeVM/HistoryVM에 데이터 변경 통지 → 각 VM이 페이지 새로고침 */
    private fun notifyDataChanged() {
        dataRefreshEvent.emit(DataRefreshEvent.RefreshType.TRANSACTION_ADDED)
    }

    // ========== 월별 SMS 동기화 CTA (리워드 광고) ==========

    /**
     * 월별 SMS 동기화 광고 다이얼로그 표시 (광고 미로드 시 프리로드도 함께 실행)
     *
     * @param year 대상 연도 (Activity 레벨 다이얼로그에서 월 라벨 표시에 사용)
     * @param month 대상 월
     */
    fun showFullSyncAdDialog(year: Int, month: Int) {
        rewardAdManager.preloadAd()
        _uiState.update {
            it.copy(showFullSyncAdDialog = true, fullSyncAdYear = year, fullSyncAdMonth = month)
        }
    }

    /** 월별 SMS 동기화 광고 다이얼로그 닫기 */
    fun dismissFullSyncAdDialog() {
        _uiState.update { it.copy(showFullSyncAdDialog = false) }
    }

    /**
     * 월별 SMS 동기화 실행 (광고 시청 완료 후 호출)
     *
     * 지정된 월의 실제 커스텀 기간만 가져오고, 성공 시 해당 구간을 coverage로 저장한다.
     * syncedMonths 기록은 기존 사용자 상태와의 호환을 위한 보조 정보만 유지한다.
     *
     * @param year 대상 연도
     * @param month 대상 월
     */
    fun unlockFullSync(year: Int, month: Int, isFreeSyncUsed: Boolean = false) {
        val yearMonth = String.format("%04d-%02d", year, month)
        _uiState.update { it.copy(showFullSyncAdDialog = false) }

        val monthRange = calculateMonthRange(year, month)
        val monthLabel = buildSyncMonthLabel(year, month)
        snackbarBus.show(appContext.getString(R.string.full_sync_unlocked_message, monthLabel))

        syncSmsV2(
            monthRange,
            updateLastSyncTime = false,
            trigger = SyncCoverageTrigger.MANUAL_MONTH_UNLOCK,
            onSyncComplete = {
                settingsDataStore.addSyncedMonth(yearMonth)
                if (isFreeSyncUsed) {
                    settingsDataStore.incrementFreeSyncUsedCount()
                }
            }
        )
    }

    /**
     * 해당 월이 이미 동기화되었는지 확인
     *
     * coverage 기반 판정을 우선 사용하고,
     * 업그레이드 이전 사용자용 syncedMonths는 fallback으로만 유지한다.
     */
    fun isMonthSynced(year: Int, month: Int): Boolean {
        val state = _uiState.value
        return syncCoveragePagePolicy.isMonthSynced(
            year = year,
            month = month,
            monthStartDay = state.monthStartDay,
            isLegacyFullSyncUnlocked = state.isLegacyFullSyncUnlocked,
            syncedMonths = state.syncedMonths,
            coverages = syncCoverageEntries
        )
    }

    /**
     * 특정 년/월의 커스텀 월 기간 계산 (사용자 설정 monthStartDay 반영)
     */
    private fun calculateMonthRange(year: Int, month: Int): Pair<Long, Long> {
        return smsSyncRangeCalculator.calculateMonthRange(
            year = year,
            month = month,
            monthStartDay = _uiState.value.monthStartDay
        )
    }

    /**
     * 해당 페이지의 커스텀 월이 동기화 범위에 부분만 포함되는지 판단
     *
     * 실제 성공한 동기화 구간 합집합 기준으로 커스텀 월이 일부만 덮여 있으면 true.
     * 기존 전체 해제 사용자는 legacy 플래그로 계속 완전 커버 처리한다.
     * 업그레이드 이전 syncedMonths fallback 대상은 partial로 보지 않는다.
     */
    fun isPagePartiallyCovered(year: Int, month: Int): Boolean {
        val state = _uiState.value
        return syncCoveragePagePolicy.isPagePartiallyCovered(
            year = year,
            month = month,
            monthStartDay = state.monthStartDay,
            isLegacyFullSyncUnlocked = state.isLegacyFullSyncUnlocked,
            syncedMonths = state.syncedMonths,
            coverages = syncCoverageEntries
        )
    }

    /**
     * 특정 월 데이터만 동기화 (해제 후 메뉴에서 호출)
     */
    fun syncMonthData(year: Int, month: Int) {
        val monthRange = calculateMonthRange(year, month)
        syncSmsV2(
            monthRange,
            updateLastSyncTime = false,
            trigger = SyncCoverageTrigger.MANUAL_MONTH_SYNC
        )
    }

    /** 월별 SMS 동기화용 광고 준비 */
    fun preloadFullSyncAd() {
        rewardAdManager.preloadAd()
    }

    private fun buildSyncMonthLabel(year: Int, month: Int): String {
        val (effYear, effMonth) = DateUtils.getEffectiveCurrentMonth(_uiState.value.monthStartDay)
        val isCurrentMonth = year == effYear && month == effMonth
        return if (isCurrentMonth) {
            appContext.getString(R.string.home_current_month_sync_label)
        } else {
            appContext.getString(R.string.home_sync_month_label_format, month)
        }
    }

    // ========== resume 시 자동 분류 ==========

    /**
     * resume 시 미분류 항목 자동 분류 시도
     * 조건: (1) 동기화 미진행 (2) 분류 미진행 (3) Gemini API 키 존재 (4) 미분류 항목 존재
     *
     * 동기화 중에는 postSyncCleanup에서 분류를 실행하므로, 여기서 중복 시작하면
     * API 429 에러 + 지수 백오프로 양쪽 모두 느려지는 문제가 발생한다.
     */
    private fun tryResumeClassification() {
        if (_uiState.value.isSyncing) return
        if (classificationState.isRunning.value) return
        if (!isResumeClassificationChecking.compareAndSet(false, true)) return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val hasApiKey = geminiRepository.hasApiKey()
                if (!hasApiKey) return@launch

                val unclassifiedCount = categoryClassifierService.getUnclassifiedCount()
                if (unclassifiedCount == 0) return@launch
                if (classificationState.isRunning.value) return@launch

                withContext(Dispatchers.Main) {
                    if (!classificationState.isRunning.value) {
                        launchBackgroundCategoryClassification()
                    }
                }
            } finally {
                isResumeClassificationChecking.set(false)
            }
        }
    }

    /**
     * 카테고리 자동 분류를 백그라운드에서 실행 (얼럿 없이 자동)
     */
    private fun launchBackgroundCategoryClassification() {
        val job = viewModelScope.launch(Dispatchers.IO) {
            try {
                launchBackgroundCategoryClassificationInternal()
            } finally {
                coroutineContext[Job]?.let { classificationState.completeJob(it) }
            }
        }
        classificationState.registerJob(job)
        if (job.isCompleted) {
            classificationState.completeJob(job)
        }
    }

    /**
     * 카테고리 자동 분류 내부 로직 (IO 디스패처에서 실행)
     */
    private suspend fun launchBackgroundCategoryClassificationInternal() {
        try {
            val count = categoryClassifierService.getUnclassifiedCount()
            if (count == 0) {
                return
            }


            val phase1Count = categoryClassifierService.classifyUnclassifiedExpenses(
                maxStoreCount = 50
            )


            if (phase1Count > 0) {
                withContext(Dispatchers.Main) {
                    dataRefreshEvent.emit(DataRefreshEvent.RefreshType.CATEGORY_UPDATED)
                    snackbarBus.show("${phase1Count}건의 카테고리가 정리되었습니다")
                }
            }

            val remainingCount = categoryClassifierService.getUnclassifiedCount()
            if (remainingCount > 0) {

                val phase2Classified = categoryClassifierService.classifyAllUntilComplete(
                    onProgress = { _, _, _ ->
                    },
                    onStepProgress = null,
                    maxRounds = MAX_CLASSIFICATION_ROUNDS
                )


                if (phase2Classified > 0) {
                    val finalRemaining = categoryClassifierService.getUnclassifiedCount()
                    val message = if (finalRemaining > 0) {
                        "총 ${phase1Count + phase2Classified}건의 카테고리가 정리되었습니다"
                    } else {
                        "카테고리 정리가 완료되었습니다"
                    }
                    withContext(Dispatchers.Main) {
                        dataRefreshEvent.emit(DataRefreshEvent.RefreshType.CATEGORY_UPDATED)
                        snackbarBus.show(message)
                    }
                }
            }

            // ===== 수입 분류 =====
            val incomeCount = categoryClassifierService.getUnclassifiedIncomeCount()
            if (incomeCount > 0) {
                val incomeClassified = categoryClassifierService.classifyUnclassifiedIncomes()
                if (incomeClassified > 0) {
                    withContext(Dispatchers.Main) {
                        dataRefreshEvent.emit(DataRefreshEvent.RefreshType.CATEGORY_UPDATED)
                    }
                }
            }

        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            MoneyTalkLogger.e("백그라운드분류: 실패: ${e.message}", e)
        }
    }
}
