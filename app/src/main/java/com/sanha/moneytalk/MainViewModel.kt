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
import com.sanha.moneytalk.core.database.entity.SyncCoverageEntity
import com.sanha.moneytalk.core.datastore.SettingsDataStore
import com.sanha.moneytalk.core.firebase.AnalyticsEvent
import com.sanha.moneytalk.core.firebase.AnalyticsHelper
import com.sanha.moneytalk.core.ui.AppSnackbarBus
import com.sanha.moneytalk.core.ui.ClassificationState
import com.sanha.moneytalk.core.util.DataRefreshEvent
import com.sanha.moneytalk.core.util.DateUtils
import com.sanha.moneytalk.core.sms.SmsIncomeParser
import com.sanha.moneytalk.core.sms.SmsInput
import com.sanha.moneytalk.core.sms.SmsInstantProcessor
import com.sanha.moneytalk.core.sms.SmsPipeline
import com.sanha.moneytalk.core.sms.SmsSyncMessageReader
import com.sanha.moneytalk.core.sms.SmsSyncCoordinator
import com.sanha.moneytalk.core.sms.SyncStats
import com.sanha.moneytalk.core.sms.SmsIngestionWriter
import com.sanha.moneytalk.core.sync.ProviderReadRangeCalculator
import com.sanha.moneytalk.core.sync.SmsSyncRangeCalculator
import com.sanha.moneytalk.core.sync.SyncCoveragePagePolicy
import com.sanha.moneytalk.core.sync.SyncCoverageRecordCounts
import com.sanha.moneytalk.core.sync.SyncCoverageRecorder
import com.sanha.moneytalk.feature.home.data.CategoryClassifierService
import com.sanha.moneytalk.feature.home.data.ExpenseRepository
import com.sanha.moneytalk.feature.home.data.IncomeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
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
import java.util.Calendar
import java.util.Locale
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
    private val snackbarBus: AppSnackbarBus,
    private val classificationState: ClassificationState,
    private val analyticsHelper: AnalyticsHelper,
    private val rewardAdManager: com.sanha.moneytalk.core.ad.RewardAdManager,
    private val smsSyncCoordinator: SmsSyncCoordinator,
    private val smsIngestionWriter: SmsIngestionWriter,
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context
) : ViewModel() {

    companion object {

        /** 카테고리 분류 최대 반복 횟수 */
        private const val MAX_CLASSIFICATION_ROUNDS = 3

        /** 즉시 저장 후 silent 동기화 전환 판단 윈도우 (60초) */
        private const val INSTANT_SAVE_SILENT_WINDOW_MS = 60_000L

        /** provider scan 경계 누락 방지용 overlap */
        private const val PROVIDER_SCAN_OVERLAP_MARGIN_MS = 5L * 60 * 1000

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
    /** App Check cooldown 동안 resume local-only 분류를 한 번만 허용 */
    private val hasRunLocalOnlyResumeClassification = AtomicBoolean(false)
    /** syncSmsV2 재진입 방지 플래그 (동시 호출 시 중복 수입 방지) */
    private val isSyncRunning = AtomicBoolean(false)
    /** 동기화 중 들어온 silent 재실행 요청 */
    @Volatile
    private var pendingSilentSyncRequest = false
    @Volatile
    private var pendingFullSyncRegistrationEpoch: Long? = null
    /** 실제 성공한 동기화 구간 캐시 */
    private var syncCoverageEntries: List<SyncCoverageEntity> = emptyList()
    /** 최초 진입(onCreate) 여부 — 첫 onAppResume 호출 시 초기 동기화 다이얼로그 표시용 */
    private var isFirstLaunch = true

    init {
        recordAppEntry()
        observeAppLaunchCreditReward()
        loadSettings()
        normalizeStoredCardNames()
        observeSyncCoverage()
        observeDataRefreshEvents()
        rewardAdManager.preloadAd()
    }

    // ========== 앱 라이프사이클 ==========

    private fun recordAppEntry() {
        viewModelScope.launch(Dispatchers.IO) {
            settingsDataStore.incrementAppEntryCount()
        }
    }

    private fun observeAppLaunchCreditReward() {
        viewModelScope.launch {
            rewardAdManager.isCreditFeatureEnabledFlow.collect { enabled ->
                if (enabled) {
                    withContext(Dispatchers.IO) {
                        rewardAdManager.prepareCreditBalance()
                    }
                }
            }
        }
    }

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
                syncSmsV2(
                    updateLastSyncTime = true,
                    silent = true,
                    trigger = SyncCoverageTrigger.APP_RESUME_INCREMENTAL
                )
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
                        syncSmsV2(
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
                            trigger = SyncCoverageTrigger.DEBUG_FULL_SYNC,
                            readPlan = SyncReadPlan(
                                targetRange = range,
                                reprocessExisting = true
                            )
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

    private fun normalizeStoredCardNames() {
        val job = viewModelScope.launch(Dispatchers.IO, start = CoroutineStart.LAZY) {
            try {
                val updatedCount = expenseRepository.normalizeStoredCardNames()
                if (updatedCount > 0) {
                    ownedCardRepository.registerCardsFromSync(
                        expenseRepository.getAllCardNamesWithDuplicates()
                    )
                    MoneyTalkLogger.i("저장 카드명 정규화 완료: ${updatedCount}건")
                    notifyDataChanged()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                MoneyTalkLogger.w("저장 카드명 정규화 실패: ${e.message}")
            }
        }
        if (classificationState.tryRegisterJob(job)) {
            job.start()
        } else {
            job.cancel()
        }
    }

    // ========== SMS 동기화 (sms 파이프라인) ==========

    /** 동기화 후처리 결과 */
    private data class PostSyncResult(
        val cardNames: List<String>,
        val classifiedCount: Int
    )

    /** 동기화 최종 결과 */
    private data class SyncResult(
        val expenseCount: Int,
        val incomeCount: Int,
        val reconciledExpenseCount: Int = 0,
        val reconciledIncomeCount: Int = 0,
        val repairedIncomeSourceCount: Int = 0,
        val detectedCardNames: List<String>,
        val classifiedCount: Int,
        /** 파이프라인 엔진 통계 (초기 동기화 요약 카드용) */
        val stats: SyncStats = SyncStats()
    )

    private data class SyncReadPlan(
        val targetRange: Pair<Long, Long>,
        val readRange: Pair<Long, Long> = targetRange,
        val rcsReadRange: Pair<Long, Long> = readRange,
        val filterTransactionRange: Pair<Long, Long>? = null,
        val reprocessExisting: Boolean = false
    )

    private data class PreparedSyncRequest(
        val targetMonthRange: Pair<Long, Long>,
        val readPlan: SyncReadPlan
    )

    private suspend fun buildProviderCatchUpReadPlan(targetRange: Pair<Long, Long>): SyncReadPlan {
        val lastRcsScanTime = settingsDataStore.getLastRcsProviderScanTime()
        val fallbackStart = smsSyncRangeCalculator.calculateDefaultProviderCatchUpStart(
            endTime = targetRange.second,
            monthStartDay = _uiState.value.monthStartDay
        )
        // 앱 lastSyncTime과 별개로 RCS provider 성공 scan 지점부터 다시 읽어 지연 노출 row를 복구한다.
        val rcsReadStart = ProviderReadRangeCalculator.calculateCatchUpStart(
            endTime = targetRange.second,
            lastSuccessfulScanTime = lastRcsScanTime,
            fallbackStart = fallbackStart,
            overlapMargin = PROVIDER_SCAN_OVERLAP_MARGIN_MS
        )

        return SyncReadPlan(
            targetRange = targetRange,
            readRange = targetRange,
            rcsReadRange = rcsReadStart to targetRange.second
        )
    }

    /**
     * SMS 동기화 (초기/증분 공통)
     *
     * 초기 동기화: fullRange(전월 1일~현재) 전체를 한 번에 처리 + 완료 후 AI 성과 요약
     * 증분 동기화: lastSyncTime 이후 ~ 현재까지 처리
     */
    private fun launchSync() {
        val registrationEpoch = classificationState.captureRegistrationEpoch() ?: return
        if (!isSyncRunning.compareAndSet(false, true)) {
            MoneyTalkLogger.w("launchSync: 이미 동기화 진행 중 → 스킵")
            return
        }

        analyticsHelper.logClick(AnalyticsEvent.SCREEN_HOME, AnalyticsEvent.CLICK_SYNC_SMS)

        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            var syncCompleted = false
            try {
                acquireSyncClassificationOwnership(registrationEpoch)
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
                    syncSmsV2Internal(
                        readPlan = buildProviderCatchUpReadPlan(fullRange),
                        updateLastSyncTime = true,
                        silent = false,
                        registrationEpoch = registrationEpoch
                    )
                }
                recordSuccessfulSyncCoverage(
                    targetMonthRange = fullRange,
                    trigger = SyncCoverageTrigger.AUTO_INITIAL,
                    result = result
                )

                if (isInitialSync) {
                    // 초기 동기화 완료 → 카드 자동 등록 + 데이터 변경 통지 + AI 성과 요약
                    registerDetectedCards(result.detectedCardNames)
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
                syncCompleted = true
            } catch (e: CancellationException) {
                handleSyncCancellation()
                throw e
            } catch (e: Exception) {
                handleSyncError(e, silent = false)
            } finally {
                isSyncRunning.set(false)
                if (syncCompleted && !pendingSilentSyncRequest) {
                    scheduleResumeClassificationAfterSync()
                }
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
    private fun buildResultMessage(
        expenseCount: Int,
        incomeCount: Int,
        repairedIncomeSourceCount: Int = 0
    ): String = when {
        expenseCount > 0 && incomeCount > 0 ->
            "${expenseCount}건의 지출, ${incomeCount}건의 수입이 추가되었습니다"
        expenseCount > 0 ->
            "${expenseCount}건의 새 지출이 추가되었습니다"
        incomeCount > 0 ->
            "${incomeCount}건의 새 수입이 추가되었습니다"
        repairedIncomeSourceCount > 0 ->
            appContext.getString(R.string.sync_income_source_repaired, repairedIncomeSourceCount)
        else -> "새로운 내역이 없습니다"
    }

    /**
     * 증분 동기화 (앱 resume, 동기화 버튼)
     *
     * lastSyncTime 기반으로 범위를 자동 계산하여 syncSmsV2 호출.
     * Screen에서 직접 호출하는 간편 래퍼.
     */
    fun syncIncremental() {
        syncSmsV2(
            updateLastSyncTime = true,
            trigger = SyncCoverageTrigger.MANUAL_INCREMENTAL
        )
    }

    /**
     * SMS 동기화 (sms 파이프라인)
     *
     * 기존 호출부(syncIncremental, unlockFullSync 등) 호환 유지.
     * 내부적으로 syncSmsV2Internal()을 호출.
     *
     * @param targetMonthRange 동기화 대상 기간. null이면 소유권 확보 후 증분 범위를 계산한다.
     * @param updateLastSyncTime true면 동기화 후 lastSyncTime 갱신 (증분=true, 월별=false)
     * @param silent true면 다이얼로그/진행 상태 표시 안함
     * @param onSyncComplete 동기화 성공 완료 시 추가 콜백 (월별 해제 마킹 등)
     */
    private fun syncSmsV2(
        targetMonthRange: Pair<Long, Long>? = null,
        updateLastSyncTime: Boolean = true,
        silent: Boolean = false,
        trigger: SyncCoverageTrigger = SyncCoverageTrigger.MANUAL_INCREMENTAL,
        onSyncComplete: (suspend () -> Unit)? = null,
        readPlan: SyncReadPlan? = null,
        requestedRegistrationEpoch: Long? = null,
        onSyncAborted: (suspend () -> Unit)? = null
    ): Boolean {
        val currentEpoch = classificationState.captureRegistrationEpoch() ?: return false
        val registrationEpoch = requestedRegistrationEpoch ?: currentEpoch
        if (!classificationState.isRegistrationEpochCurrent(registrationEpoch)) return false
        if (!isSyncRunning.compareAndSet(false, true)) {
            if (silent) {
                pendingSilentSyncRequest = true
                MoneyTalkLogger.i("syncSmsV2: 이미 동기화 진행 중 → silent 재실행 예약")
            }
            MoneyTalkLogger.w("syncSmsV2: 이미 동기화 진행 중 → 스킵")
            return false
        }

        if (!silent) {
            analyticsHelper.logClick(AnalyticsEvent.SCREEN_HOME, AnalyticsEvent.CLICK_SYNC_SMS)
        }
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            var syncCompleted = false
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
                acquireSyncClassificationOwnership(registrationEpoch)
                val preparedRequest = prepareSyncRequest(targetMonthRange, readPlan)
                val result = withContext(Dispatchers.IO) {
                    syncSmsV2Internal(preparedRequest.readPlan, updateLastSyncTime, silent, registrationEpoch)
                }

                recordSuccessfulSyncCoverage(preparedRequest.targetMonthRange, trigger, result)
                handleSyncResult(result, silent)
                onSyncComplete?.invoke()
                syncCompleted = true
            } catch (e: CancellationException) {
                runSyncAbortedCallback(onSyncAborted)
                handleSyncCancellation()
                throw e
            } catch (e: Exception) {
                runSyncAbortedCallback(onSyncAborted)
                handleSyncError(e, silent)
            } finally {
                isSyncRunning.set(false)
                if (syncCompleted && !pendingSilentSyncRequest) {
                    scheduleResumeClassificationAfterSync()
                }
                drainPendingSilentSyncIfNeeded()
            }
        }
        return true
    }

    private suspend fun runSyncAbortedCallback(callback: (suspend () -> Unit)?) {
        if (callback == null) return
        withContext(NonCancellable) {
            try {
                callback()
            } catch (e: Exception) {
                MoneyTalkLogger.w("동기화 중단 후처리 실패: ${e.message}")
            }
        }
    }

    /** 동기화의 DB 처리와 후처리가 끝날 때까지 전역 분류 소유권을 유지한다. */
    private suspend fun acquireSyncClassificationOwnership(registrationEpoch: Long) {
        val syncJob = coroutineContext[Job]
            ?: throw CancellationException("SMS 동기화 Job을 확인할 수 없습니다")
        if (!classificationState.replaceWith(syncJob, expectedEpoch = registrationEpoch)) {
            throw CancellationException("SMS 동기화 분류 소유권을 확보하지 못했습니다")
        }
        if (!classificationState.isRegistrationEpochCurrent(registrationEpoch)) {
            throw CancellationException("데이터 초기화 전에 생성된 SMS 동기화 요청입니다")
        }
    }

    private suspend fun prepareSyncRequest(
        targetMonthRange: Pair<Long, Long>?,
        readPlan: SyncReadPlan?
    ): PreparedSyncRequest = withContext(Dispatchers.IO) {
        val resolvedReadPlan = when {
            readPlan != null -> readPlan
            targetMonthRange != null -> SyncReadPlan(targetRange = targetMonthRange)
            else -> {
                val incrementalRange = calculateIncrementalRange()
                buildProviderCatchUpReadPlan(incrementalRange)
            }
        }
        PreparedSyncRequest(
            targetMonthRange = targetMonthRange ?: resolvedReadPlan.targetRange,
            readPlan = resolvedReadPlan
        )
    }

    /** 현재 sync Job의 completion callback이 소유권을 해제한 뒤 잔여 분류를 시도한다. */
    private suspend fun scheduleResumeClassificationAfterSync() {
        val completedSyncJob = coroutineContext[Job] ?: return
        viewModelScope.launch {
            completedSyncJob.join()
            tryResumeClassification()
        }
    }

    private fun drainPendingSilentSyncIfNeeded() {
        if (!pendingSilentSyncRequest) return
        pendingSilentSyncRequest = false
        syncSmsV2(
            updateLastSyncTime = true,
            silent = true,
            trigger = SyncCoverageTrigger.PENDING_SILENT_INCREMENTAL
        )
    }

    /**
     * SMS 동기화 내부 실행 (순수 suspend 함수)
     *
     * isSyncRunning, viewModelScope.launch 관리 없이 순수 로직만 실행.
     */
    private suspend fun syncSmsV2Internal(
        readPlan: SyncReadPlan,
        updateLastSyncTime: Boolean,
        silent: Boolean,
        registrationEpoch: Long
    ): SyncResult {
        // 제외 키워드 설정
        val userExcludeKeywords = smsExclusionRepository.getUserKeywords()
        smsSyncCoordinator.setUserExcludeKeywords(userExcludeKeywords)
        SmsIncomeParser.setUserExcludeKeywords(userExcludeKeywords)

        // Step 1: SMS 읽기 + 중복 제거
        val readResult = readSmsInputs(readPlan)
        val allSmsList = readResult.messages
        if (allSmsList.isEmpty()) {
            val repairedIncomeSourceCount = repairStoredIncomeSourcesIfNeeded(readPlan)
            saveSyncWatermarks(
                updateLastSyncTime = updateLastSyncTime,
                endTime = readPlan.targetRange.second,
                rcsProviderReadSucceeded = readResult.rcsProviderReadSucceeded
            )
            return SyncResult(
                expenseCount = 0,
                incomeCount = 0,
                detectedCardNames = emptyList(),
                classifiedCount = 0,
                repairedIncomeSourceCount = repairedIncomeSourceCount
            )
        }

        _uiState.update { it.copy(syncProgress = "이미 등록된 내역 확인 중...") }
        val existingSnapshot = smsIngestionWriter.buildExistingSmsSnapshot(allSmsList, readPlan.readRange)
        val pendingReconciliationIds = SmsInstantProcessor.snapshotPendingReconciliationIds()
        val pendingContentIndex = smsIngestionWriter.buildSmsIdCandidateIndex(
            pendingReconciliationIds, existingSnapshot
        )
        val smsInputs = smsIngestionWriter.readAndFilterSms(
            allSmsList = allSmsList,
            pendingContentIndex = pendingContentIndex,
            existingSnapshot = existingSnapshot,
            reprocessExisting = readPlan.reprocessExisting
        )
        MoneyTalkLogger.i("syncSmsV2 Step1 완료: 신규 SMS ${smsInputs.size}건")
        if (smsInputs.isEmpty()) {
            val repairedIncomeSourceCount = repairStoredIncomeSourcesIfNeeded(readPlan)
            saveSyncWatermarks(
                updateLastSyncTime = updateLastSyncTime,
                endTime = readPlan.targetRange.second,
                rcsProviderReadSucceeded = readResult.rcsProviderReadSucceeded
            )
            return SyncResult(
                expenseCount = 0,
                incomeCount = 0,
                detectedCardNames = emptyList(),
                classifiedCount = 0,
                repairedIncomeSourceCount = repairedIncomeSourceCount
            )
        }

        // Step 2: sms 파이프라인 실행
        val syncResult = processSmsPipeline(smsInputs, silent)
        val targetFilteredResult = filterSyncResultByTransactionRange(
            syncResult = syncResult,
            transactionRange = readPlan.filterTransactionRange
        )

        val reconciledExpenseIds = targetFilteredResult.expenses
            .flatMap { parsed -> smsIngestionWriter.findMatchingSmsIds(parsed.input, pendingContentIndex) }
            .toSet()
        val reconciledIncomeIds = targetFilteredResult.incomes
            .flatMap { income -> smsIngestionWriter.findMatchingSmsIds(income, pendingContentIndex) }
            .toSet()

        // Step 3: DB 저장
        val writeResult = smsIngestionWriter.write(
            expenses = targetFilteredResult.expenses,
            incomes = targetFilteredResult.incomes,
            registrationEpoch = registrationEpoch
        ) { progress ->
            _uiState.update {
                it.copy(
                    syncStepIndex = SmsPipeline.STEP_SAVE,
                    syncProgress = progress.message,
                    syncProgressCurrent = progress.current ?: it.syncProgressCurrent,
                    syncProgressTotal = progress.total ?: it.syncProgressTotal
                )
            }
        }
        val expenseSaveResult = writeResult.expenses
        val incomeSaveResult = writeResult.incomes
        val repairedIncomeSourceCount = repairStoredIncomeSourcesIfNeeded(readPlan)

        // Step 4: 후처리 (카테고리 분류, 패턴 정리, lastSyncTime 갱신)
        val cleanup = postSyncCleanup(
            updateLastSyncTime = updateLastSyncTime,
            endTime = readPlan.targetRange.second,
            rcsProviderReadSucceeded = readResult.rcsProviderReadSucceeded
        )
        SmsInstantProcessor.clearPendingReconciliationIds(reconciledExpenseIds + reconciledIncomeIds)

        return SyncResult(
            expenseCount = expenseSaveResult.newCount,
            incomeCount = incomeSaveResult.newCount,
            reconciledExpenseCount = expenseSaveResult.reconciledCount,
            reconciledIncomeCount = incomeSaveResult.reconciledCount,
            repairedIncomeSourceCount = repairedIncomeSourceCount,
            detectedCardNames = cleanup.cardNames,
            classifiedCount = cleanup.classifiedCount,
            stats = targetFilteredResult.stats
        )
    }

    /**
     * 대상 기간의 SMS 입력 목록을 읽는다.
     */
    private suspend fun readSmsInputs(
        readPlan: SyncReadPlan
    ): SmsSyncMessageReader.ReadResult {
        return smsSyncMessageReader.read(
            range = readPlan.readRange,
            rcsRange = readPlan.rcsReadRange
        )
    }

    private fun filterSyncResultByTransactionRange(
        syncResult: com.sanha.moneytalk.core.sms.SyncResult,
        transactionRange: Pair<Long, Long>?
    ): com.sanha.moneytalk.core.sms.SyncResult {
        if (transactionRange == null) return syncResult

        val filteredExpenses = syncResult.expenses.filter { parsed ->
            DateUtils.parseDateTime(parsed.analysis.dateTime) in transactionRange.first..transactionRange.second
        }
        val filteredIncomes = syncResult.incomes.filter { income ->
            val dateTime = SmsIncomeParser.extractDateTime(income.body, income.date)
            DateUtils.parseDateTime(dateTime) in transactionRange.first..transactionRange.second
        }

        val droppedByRange = syncResult.expenses.size + syncResult.incomes.size -
            filteredExpenses.size - filteredIncomes.size
        if (droppedByRange > 0) {
            MoneyTalkLogger.i("월 동기화 저장 범위 밖 SMS ${droppedByRange}건 제외")
        }

        return syncResult.copy(
            expenses = filteredExpenses,
            incomes = filteredIncomes
        )
    }

    private suspend fun repairStoredIncomeSourcesIfNeeded(readPlan: SyncReadPlan): Int {
        if (!readPlan.reprocessExisting) return 0

        _uiState.update {
            it.copy(syncProgress = appContext.getString(R.string.sync_repair_income_sources))
        }

        val incomes = incomeRepository.getIncomesByDateRangeOnce(
            readPlan.targetRange.first,
            readPlan.targetRange.second
        )
        var repairedCount = 0

        for (income in incomes) {
            val originalSms = income.originalSms?.takeIf { it.isNotBlank() } ?: continue
            val parsedSource = SmsIncomeParser.extractIncomeSource(originalSms)
            val shouldRepair = when {
                parsedSource.isNotBlank() &&
                    parsedSource != income.source &&
                    SmsIncomeParser.isInvalidIncomeSource(income.source) -> true
                parsedSource.isBlank() &&
                    income.source.isNotBlank() &&
                    SmsIncomeParser.isInvalidIncomeSource(income.source) -> true
                else -> false
            }
            if (!shouldRepair) continue

            val incomeType = income.type.ifBlank { SmsIncomeParser.extractIncomeType(originalSms) }
            val description = if (parsedSource.isNotBlank()) {
                "${parsedSource}에서 $incomeType"
            } else {
                incomeType
            }
            incomeRepository.update(
                income.copy(
                    source = parsedSource,
                    description = description
                )
            )
            repairedCount++
        }

        if (repairedCount > 0) {
            MoneyTalkLogger.i("기존 수입 출처 보정 완료: ${repairedCount}건")
        }

        return repairedCount
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
     * 동기화 후처리 (카테고리 캐시 정리, lastSyncTime 갱신)
     *
     * 카테고리 분류는 saveExpenses()에서 DB INSERT 전에 완료하므로 여기서는 생략합니다.
     * 잔여 미분류 항목은 tryResumeClassification()에서 백그라운드로 처리됩니다.
     */
    private suspend fun postSyncCleanup(
        updateLastSyncTime: Boolean,
        endTime: Long,
        rcsProviderReadSucceeded: Boolean
    ): PostSyncResult {
        _uiState.update { it.copy(syncProgress = "마무리 중...") }
        categoryClassifierService.flushPendingMappings()
        categoryClassifierService.clearCategoryCache()

        saveSyncWatermarks(updateLastSyncTime, endTime, rcsProviderReadSucceeded)

        val allCardNames = expenseRepository.getAllCardNamesWithDuplicates()

        return PostSyncResult(
            cardNames = allCardNames,
            classifiedCount = 0
        )
    }

    private suspend fun saveSyncWatermarks(
        updateLastSyncTime: Boolean,
        endTime: Long,
        rcsProviderReadSucceeded: Boolean
    ) {
        if (!updateLastSyncTime) return

        settingsDataStore.saveLastSyncTime(endTime)
        if (rcsProviderReadSucceeded) {
            settingsDataStore.saveLastRcsProviderScanTime(endTime)
        }
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
                "교체 지출 ${result.reconciledExpenseCount}건, 교체 수입 ${result.reconciledIncomeCount}건, " +
                "수입 출처 보정 ${result.repairedIncomeSourceCount}건"
        )

        registerDetectedCards(result.detectedCardNames)

        // 실제 데이터 변경이 있을 때만 HomeVM/HistoryVM에 통지 (불필요한 UI 갱신 방지)
        val hasDataChange = result.expenseCount > 0 || result.incomeCount > 0 ||
            result.reconciledExpenseCount > 0 || result.reconciledIncomeCount > 0 ||
            result.repairedIncomeSourceCount > 0
        if (hasDataChange) {
            notifyDataChanged()
        }

        val resultMessage = buildResultMessage(
            expenseCount = result.expenseCount,
            incomeCount = result.incomeCount,
            repairedIncomeSourceCount = result.repairedIncomeSourceCount
        )

        if (silent || _uiState.value.syncDialogDismissed) {
            _uiState.update { it.copy(isSyncing = false) }
            if (hasDataChange) {
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
            if (hasDataChange) {
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

    /** 카드 등록도 sync 소유 Job 안에서 끝내 삭제 gate가 완료까지 기다리게 한다. */
    private suspend fun registerDetectedCards(cardNames: List<String>) {
        if (cardNames.isEmpty()) return
        try {
            withContext(Dispatchers.IO) {
                ownedCardRepository.registerCardsFromSync(cardNames)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            MoneyTalkLogger.w("카드 자동 등록 실패: ${e.message}")
        }
    }

    /** 다른 전역 작업이 동기화를 취소해도 진행 UI가 남지 않도록 정리한다. */
    private fun handleSyncCancellation() {
        pendingSilentSyncRequest = false
        categoryClassifierService.clearCategoryCache()
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

    // ========== 월별 SMS 동기화 CTA (AI 크레딧/리워드 광고) ==========

    /**
     * 월별 SMS 동기화 크레딧 충전 다이얼로그 표시 (광고 미로드 시 프리로드도 함께 실행)
     *
     * @param year 대상 연도 (Activity 레벨 다이얼로그에서 월 라벨 표시에 사용)
     * @param month 대상 월
     */
    private fun showFullSyncAdDialog(year: Int, month: Int, registrationEpoch: Long) {
        pendingFullSyncRegistrationEpoch = registrationEpoch
        rewardAdManager.preloadCreditAd()
        _uiState.update {
            it.copy(showFullSyncAdDialog = true, fullSyncAdYear = year, fullSyncAdMonth = month)
        }
    }

    /** Keep the pending request while the rewarded ad covers the dialog. */
    fun hideFullSyncAdDialogForRewardAd() {
        _uiState.update { it.copy(showFullSyncAdDialog = false) }
    }

    /** 월별 SMS 동기화 광고 다이얼로그 닫기 */
    fun dismissFullSyncAdDialog() {
        pendingFullSyncRegistrationEpoch = null
        _uiState.update { it.copy(showFullSyncAdDialog = false) }
    }

    /**
     * 이전 월 문자 기록 가져오기 요청.
     * 크레딧 기능이 활성화되어 있으면 월 1개당 1크레딧을 차감하고,
     * 부족하면 보상형 광고 충전 다이얼로그를 표시한다.
     */
    fun requestMonthSync(year: Int, month: Int) {
        val registrationEpoch = classificationState.captureRegistrationEpoch() ?: return
        viewModelScope.launch {
            requestMonthSyncInternal(year, month, registrationEpoch)
        }
    }

    fun onFullSyncRewardAdWatched(year: Int, month: Int) {
        val registrationEpoch = pendingFullSyncRegistrationEpoch
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                rewardAdManager.addRewardChats()
            }
            if (registrationEpoch == null ||
                !classificationState.isRegistrationEpochCurrent(registrationEpoch)
            ) {
                dismissFullSyncAdDialog()
                return@launch
            }
            requestMonthSyncInternal(year, month, registrationEpoch)
        }
    }

    private suspend fun requestMonthSyncInternal(
        year: Int,
        month: Int,
        registrationEpoch: Long
    ) {
        if (!classificationState.isRegistrationEpochCurrent(registrationEpoch)) return
        val needsCredit = withContext(Dispatchers.IO) {
            rewardAdManager.isMonthSyncCreditRequired()
        }
        if (!classificationState.isRegistrationEpochCurrent(registrationEpoch)) return
        if (needsCredit) {
            showFullSyncAdDialog(year, month, registrationEpoch)
            return
        }

        val requestJob = coroutineContext[Job]
        val creditConsumption = withContext(NonCancellable) {
            val consumption = withContext(Dispatchers.IO) {
                rewardAdManager.consumeMonthSyncCredit()
            }
            val requestStillValid = requestJob?.isActive == true &&
                classificationState.isRegistrationEpochCurrent(registrationEpoch)
            if (!requestStillValid) {
                if (consumption.charged) {
                    withContext(Dispatchers.IO) {
                        rewardAdManager.refundMonthSyncCredit()
                    }
                }
                consumption.copy(canSync = false, charged = false)
            } else {
                consumption
            }
        }
        if (creditConsumption.canSync) {
            if (!classificationState.isRegistrationEpochCurrent(registrationEpoch)) {
                if (creditConsumption.charged) {
                    withContext(NonCancellable + Dispatchers.IO) {
                        rewardAdManager.refundMonthSyncCredit()
                    }
                }
                return
            }
            val started = unlockFullSync(
                year = year,
                month = month,
                registrationEpoch = registrationEpoch,
                refundCreditOnAbort = creditConsumption.charged
            )
            if (!started && creditConsumption.charged) {
                withContext(NonCancellable + Dispatchers.IO) {
                    rewardAdManager.refundMonthSyncCredit()
                }
            }
        } else if (classificationState.isRegistrationEpochCurrent(registrationEpoch)) {
            showFullSyncAdDialog(year, month, registrationEpoch)
        }
    }

    /**
     * 월별 SMS 동기화 실행 (크레딧 차감 또는 비활성 정책 통과 후 호출)
     *
     * 지정된 월의 실제 커스텀 기간만 가져오고, 성공 시 해당 구간을 coverage로 저장한다.
     * syncedMonths 기록은 기존 사용자 상태와의 호환을 위한 보조 정보만 유지한다.
     *
     * @param year 대상 연도
     * @param month 대상 월
     */
    private fun unlockFullSync(
        year: Int,
        month: Int,
        registrationEpoch: Long,
        isFreeSyncUsed: Boolean = false,
        refundCreditOnAbort: Boolean = false
    ): Boolean {
        if (!classificationState.isRegistrationEpochCurrent(registrationEpoch)) return false
        val yearMonth = String.format(Locale.ROOT, "%04d-%02d", year, month)
        pendingFullSyncRegistrationEpoch = null
        _uiState.update { it.copy(showFullSyncAdDialog = false) }

        val readPlan = calculateMonthReadPlan(year, month)
        val monthLabel = buildSyncMonthLabel(year, month)

        val started = syncSmsV2(
            readPlan.targetRange,
            updateLastSyncTime = false,
            trigger = SyncCoverageTrigger.MANUAL_MONTH_UNLOCK,
            onSyncComplete = {
                settingsDataStore.addSyncedMonth(yearMonth)
                if (isFreeSyncUsed) {
                    settingsDataStore.incrementFreeSyncUsedCount()
                }
            },
            readPlan = readPlan,
            requestedRegistrationEpoch = registrationEpoch,
            onSyncAborted = if (refundCreditOnAbort) {
                {
                    withContext(Dispatchers.IO) {
                        rewardAdManager.refundMonthSyncCredit()
                    }
                }
            } else {
                null
            }
        )
        if (started) {
            snackbarBus.show(appContext.getString(R.string.full_sync_unlocked_message, monthLabel))
        }
        return started
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

    private fun calculateMonthReadPlan(year: Int, month: Int): SyncReadPlan {
        val targetRange = calculateMonthRange(year, month)
        val nextMonth = Calendar.getInstance().apply {
            clear()
            set(year, month - 1, 1, 0, 0, 0)
            add(Calendar.MONTH, 1)
        }
        val nextRange = calculateMonthRange(
            year = nextMonth.get(Calendar.YEAR),
            month = nextMonth.get(Calendar.MONTH) + 1
        )
        val readEnd = minOf(nextRange.second, System.currentTimeMillis())

        return SyncReadPlan(
            targetRange = targetRange,
            readRange = targetRange.first to maxOf(targetRange.second, readEnd),
            filterTransactionRange = targetRange
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
        val readPlan = calculateMonthReadPlan(year, month)
        syncSmsV2(
            readPlan.targetRange,
            updateLastSyncTime = false,
            trigger = SyncCoverageTrigger.MANUAL_MONTH_SYNC,
            readPlan = readPlan
        )
    }

    /** 월별 SMS 동기화용 광고 준비 */
    fun preloadFullSyncAd() {
        rewardAdManager.preloadCreditAd()
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
     * 조건: (1) 동기화 미진행 (2) 분류 미진행 (3) 미분류 항목 존재
     *
     * 동기화 Job이 전역 분류 소유권을 유지하고 완료 뒤 이 경로를 다시 예약하므로,
     * 동기화 중에는 별도 분류를 시작하지 않는다.
     */
    private fun tryResumeClassification() {
        val registrationEpoch = classificationState.captureRegistrationEpoch() ?: return
        if (_uiState.value.isSyncing) return
        if (classificationState.isRunning.value) return
        if (!isResumeClassificationChecking.compareAndSet(false, true)) return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val canAttemptRemote = categoryClassifierService.canAttemptGeminiClassification()
                if (canAttemptRemote) {
                    hasRunLocalOnlyResumeClassification.set(false)
                } else if (hasRunLocalOnlyResumeClassification.get()) {
                    return@launch
                }

                if (!classificationState.isRegistrationEpochCurrent(registrationEpoch)) return@launch
                val unclassifiedCount = categoryClassifierService.getUnclassifiedCount()
                if (unclassifiedCount == 0) return@launch
                if (classificationState.isRunning.value) return@launch

                withContext(Dispatchers.Main) {
                    if (classificationState.isRegistrationEpochCurrent(registrationEpoch) &&
                        !classificationState.isRunning.value
                    ) {
                        launchBackgroundCategoryClassification(
                            markLocalOnlyRun = !canAttemptRemote
                        )
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
    private fun launchBackgroundCategoryClassification(markLocalOnlyRun: Boolean) {
        val job = viewModelScope.launch(Dispatchers.IO, start = CoroutineStart.LAZY) {
            launchBackgroundCategoryClassificationInternal(markLocalOnlyRun)
        }
        if (classificationState.tryRegisterJob(job)) {
            job.start()
        } else {
            job.cancel()
        }
    }

    /**
     * 카테고리 자동 분류 내부 로직 (IO 디스패처에서 실행)
     */
    private suspend fun launchBackgroundCategoryClassificationInternal(markLocalOnlyRun: Boolean) {
        try {
            val count = categoryClassifierService.getUnclassifiedCount()
            if (count == 0) {
                return
            }


            val phase1Count = categoryClassifierService.classifyUnclassifiedExpenses(
                maxStoreCount = 50
            )
            if (markLocalOnlyRun) {
                hasRunLocalOnlyResumeClassification.set(true)
            }


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
