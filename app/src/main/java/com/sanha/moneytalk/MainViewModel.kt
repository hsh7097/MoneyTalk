package com.sanha.moneytalk

import com.sanha.moneytalk.core.util.MoneyTalkLogger

import android.Manifest
import android.content.ContentResolver
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sanha.moneytalk.core.database.OwnedCardRepository
import com.sanha.moneytalk.core.database.SmsExclusionRepository
import com.sanha.moneytalk.core.database.entity.ExpenseEntity
import com.sanha.moneytalk.core.database.entity.IncomeEntity
import com.sanha.moneytalk.core.model.Category
import com.sanha.moneytalk.core.model.TransferDirection
import com.sanha.moneytalk.core.datastore.SettingsDataStore
import com.sanha.moneytalk.core.firebase.AnalyticsEvent
import com.sanha.moneytalk.core.firebase.AnalyticsHelper
import com.sanha.moneytalk.core.ui.AppSnackbarBus
import com.sanha.moneytalk.core.ui.ClassificationState
import com.sanha.moneytalk.core.util.DataRefreshEvent
import com.sanha.moneytalk.core.util.CardNameNormalizer
import com.sanha.moneytalk.core.util.DateUtils
import com.sanha.moneytalk.core.sms2.SmsIncomeParser
import com.sanha.moneytalk.core.sms2.SmsInput
import com.sanha.moneytalk.core.sms2.SmsFilter
import com.sanha.moneytalk.core.sms2.SmsReaderV2
import com.sanha.moneytalk.core.sms2.SmsPipeline
import com.sanha.moneytalk.core.sms2.SmsSyncCoordinator
import com.sanha.moneytalk.core.sms2.SyncStats
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
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlin.coroutines.coroutineContext

/**
 * Activity-scoped ViewModel — SMS 동기화 엔진 + resume/권한/광고 통합 관리
 *
 * HomeViewModel(1,890줄)에서 동기화 관련 ~600줄을 추출하여 Activity 레벨로 이동.
 * HomeScreen/HistoryScreen 모두에서 공유되는 동기화, 권한, 광고 상태를 단일 소스로 관리.
 *
 * 주요 기능:
 * - SMS 동기화 (초기/증분/월별)
 * - 앱 resume 시 자동 동기화 + 자동 분류
 * - SMS 권한 상태 관리
 * - 월별 동기화 해제 (리워드 광고)
 * - AI 성과 요약 (초기 동기화 완료 후)
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val expenseRepository: ExpenseRepository,
    private val incomeRepository: IncomeRepository,
    private val categoryClassifierService: CategoryClassifierService,
    private val smsReaderV2: SmsReaderV2,
    private val settingsDataStore: SettingsDataStore,
    private val dataRefreshEvent: DataRefreshEvent,
    private val ownedCardRepository: OwnedCardRepository,
    private val smsExclusionRepository: SmsExclusionRepository,
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

        /** 초기 동기화 제한 기간 (2개월, 밀리초) — 전체 동기화 미해제 시 적용 */
        private const val DEFAULT_SYNC_PERIOD_MILLIS = 60L * 24 * 60 * 60 * 1000

        /** 카테고리 분류 최대 반복 횟수 */
        private const val MAX_CLASSIFICATION_ROUNDS = 3

    }

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    /** 광고 매니저 접근 (Activity에서 광고 표시에 필요) */
    val adManager: com.sanha.moneytalk.core.ad.RewardAdManager get() = rewardAdManager

    /** 홈 탭 재클릭 → 오늘 페이지로 이동 이벤트 */
    val homeTabReClickEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    /** 내역 탭 재클릭 → 오늘 페이지로 이동 + 필터 초기화 이벤트 */
    val historyTabReClickEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** appContext의 ContentResolver (SMS 읽기용) */
    private val contentResolver: ContentResolver get() = appContext.contentResolver

    /** resume 자동 분류 중복 실행 방지 플래그 */
    private val isResumeClassificationChecking = AtomicBoolean(false)
    /** syncSmsV2 재진입 방지 플래그 (동시 호출 시 중복 수입 방지) */
    private val isSyncRunning = AtomicBoolean(false)
    /** 최초 진입(onCreate) 여부 — 첫 onAppResume 호출 시 초기 동기화 다이얼로그 표시용 */
    private var isFirstLaunch = true

    init {
        loadSettings()
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

            if (firstLaunch) {
                launchSync()
                syncTriggered = true
            } else {
                viewModelScope.launch {
                    val range = withContext(Dispatchers.IO) { calculateIncrementalRange() }
                    syncSmsV2(range, updateLastSyncTime = true, silent = true)
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
        // 월별 동기화 해제 상태
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
                        syncSmsV2(range, updateLastSyncTime = true, silent = true)
                    }

                    DataRefreshEvent.RefreshType.DEBUG_FULL_SYNC_ALL_MESSAGES -> {
                        MoneyTalkLogger.i("DEBUG 전체 메시지 동기화 시작")
                        val range = Pair(0L, System.currentTimeMillis())
                        syncSmsV2(range, updateLastSyncTime = true, silent = false)
                    }

                    else -> { /* CATEGORY_UPDATED, OWNED_CARD_UPDATED, TRANSACTION_ADDED → HomeVM/HistoryVM이 처리 */ }
                }
            }
        }
    }

    // ========== SMS 동기화 (sms2 파이프라인) ==========

    /** 동기화 후처리 결과 */
    private data class PostSyncResult(
        val cardNames: List<String>,
        val classifiedCount: Int
    )

    /** 동기화 최종 결과 */
    private data class SyncResult(
        val expenseCount: Int,
        val incomeCount: Int,
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
                    handleSyncResult(result, silent = false)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleSyncError(e, silent = false)
            } finally {
                isSyncRunning.set(false)
                tryResumeClassification()
            }
        }
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
            syncSmsV2(range, updateLastSyncTime = true)
        }
    }

    /**
     * SMS 동기화 (sms2 파이프라인)
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
        onSyncComplete: (suspend () -> Unit)? = null
    ) {
        if (!isSyncRunning.compareAndSet(false, true)) {
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

                handleSyncResult(result, silent)
                onSyncComplete?.invoke()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleSyncError(e, silent)
            } finally {
                isSyncRunning.set(false)
                tryResumeClassification()
            }
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
        val smsInputs = readAndFilterSms(targetMonthRange)
        MoneyTalkLogger.i("syncSmsV2 Step1 완료: 신규 SMS ${smsInputs.size}건")
        if (smsInputs.isEmpty()) {
            if (updateLastSyncTime) {
                settingsDataStore.saveLastSyncTime(targetMonthRange.second)
            }
            return SyncResult(0, 0, emptyList(), 0)
        }

        // Step 2: sms2 파이프라인 실행
        val syncResult = processSmsPipeline(smsInputs, silent)

        // Step 3: DB 저장
        val expenseCount = saveExpenses(syncResult.expenses)
        val incomeCount = saveIncomes(syncResult.incomes)

        // Step 4: 후처리 (카테고리 분류, 패턴 정리, lastSyncTime 갱신)
        val cleanup = postSyncCleanup(updateLastSyncTime, targetMonthRange.second)

        return SyncResult(
            expenseCount = expenseCount,
            incomeCount = incomeCount,
            detectedCardNames = cleanup.cardNames,
            classifiedCount = cleanup.classifiedCount,
            stats = syncResult.stats
        )
    }

    /**
     * SMS 읽기 + 중복 제거
     */
    private suspend fun readAndFilterSms(
        targetMonthRange: Pair<Long, Long>
    ): List<SmsInput> {
        val allSmsList = smsReaderV2.readAllMessagesByDateRange(
            contentResolver,
            targetMonthRange.first,
            targetMonthRange.second
        )
        MoneyTalkLogger.i("syncSmsV2 SMS 읽기: ${allSmsList.size}건")

        if (allSmsList.isEmpty()) return emptyList()

        _uiState.update { it.copy(syncProgress = "이미 등록된 내역 확인 중...") }
        val smsIdChunks = allSmsList
            .map { it.id }
            .chunked(SMS_ID_LOOKUP_CHUNK_SIZE)
        val allExistingIds = coroutineScope {
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

        val newSmsList = allSmsList.filter { it.id !in allExistingIds }
        MoneyTalkLogger.i("syncSmsV2 중복 제거: ${allSmsList.size}건 → ${newSmsList.size}건")

        return newSmsList
    }

    /**
     * sms2 파이프라인 실행 (SmsSyncCoordinator.process)
     *
     * @param silent true면 dataRefreshEvent에 진행 상태를 전파하지 않음
     */
    private suspend fun processSmsPipeline(
        smsInputs: List<SmsInput>,
        silent: Boolean = false
    ): com.sanha.moneytalk.core.sms2.SyncResult {
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
        expenses: List<com.sanha.moneytalk.core.sms2.SmsParseResult>
    ): Int {
        if (expenses.isEmpty()) return 0

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
        if (allRules.isNotEmpty()) {
            for (i in entities.indices) {
                val entity = entities[i]
                val lowerStore = entity.storeName.lowercase()
                val matchedRule = allRules.firstOrNull { lowerStore.contains(it.keyword.lowercase()) }
                if (matchedRule != null) {
                    entities[i] = entity.copy(
                        category = matchedRule.category ?: entity.category,
                        isFixed = matchedRule.isFixed ?: entity.isFixed
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
                                entities[i] = entity.copy(
                                    category = newCategory,
                                    transactionType = if (isTransfer) "TRANSFER" else entity.transactionType,
                                    transferDirection = if (isTransfer) TransferDirection.WITHDRAWAL.dbValue else entity.transferDirection
                                )
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                MoneyTalkLogger.w("사전 카테고리 분류 실패 (무시): ${e.message}")
            }
        }

        // Phase 3: 분류 완료된 엔티티를 DB에 배치 저장
        _uiState.update { it.copy(syncProgress = "지출 저장 중...") }
        for (chunk in entities.chunked(DB_BATCH_INSERT_SIZE)) {
            expenseRepository.insertAll(chunk)
        }

        return expenses.size
    }

    /**
     * 수입 SMS를 SmsIncomeParser로 파싱하여 DB에 배치 저장
     */
    private suspend fun saveIncomes(
        incomes: List<SmsInput>
    ): Int {
        if (incomes.isEmpty()) return 0

        _uiState.update { it.copy(syncProgress = "수입 처리 중...") }
        val batch = mutableListOf<IncomeEntity>()
        var count = 0

        for (income in incomes) {
            try {
                val amount = SmsIncomeParser.extractIncomeAmount(income.body)
                val incomeType = SmsIncomeParser.extractIncomeType(income.body)
                val source = SmsIncomeParser.extractIncomeSource(income.body)
                val dateTime = SmsIncomeParser.extractDateTime(income.body, income.date)

                if (amount > 0) {
                    val category = mapIncomeTypeToCategory(incomeType)
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
                            category = category
                        )
                    )
                    count++

                    if (batch.size >= DB_BATCH_INSERT_SIZE) {
                        incomeRepository.insertAll(batch)
                        batch.clear()
                    }
                }
            } catch (e: Exception) {
                MoneyTalkLogger.e("수입 처리 실패: ${income.id} - ${e.message}")
            }
        }
        if (batch.isNotEmpty()) {
            incomeRepository.insertAll(batch)
        }

        return count
    }

    /** 수입 type → category 초기 매핑 */
    private fun mapIncomeTypeToCategory(type: String): String {
        return when (type) {
            "급여" -> "급여"
            "보너스" -> "상여금"
            "정산" -> "더치페이"
            else -> "미분류"
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
     * - lastSyncTime이 없으면 (초기): 전월 1일 ~ now (2달치)
     *
     * Auto Backup 감지: savedSyncTime > 0 이지만 DB 비어있으면 초기 상태로 리셋.
     */
    private suspend fun calculateIncrementalRange(): Pair<Long, Long> {
        val savedSyncTime = settingsDataStore.getLastSyncTime()
        val now = System.currentTimeMillis()
        val monthStartDay = _uiState.value.monthStartDay

        val dbCount = expenseRepository.getExpenseCount() + incomeRepository.getIncomeCount()
        val effectiveSyncTime = if (savedSyncTime > 0 && dbCount == 0) {
            MoneyTalkLogger.w("Auto Backup 감지: savedSyncTime 있으나 DB 비어있음 → 리셋")
            settingsDataStore.saveLastSyncTime(0L)
            0L
        } else {
            savedSyncTime
        }

        // monthStartDay > 1이면 커스텀 월이 달을 걸치므로 추가 마진 필요
        val extraDaysMillis = if (monthStartDay > 1) {
            (monthStartDay - 1).toLong() * 24 * 60 * 60 * 1000
        } else 0L

        val minStartTime = now - DEFAULT_SYNC_PERIOD_MILLIS - extraDaysMillis

        val OVERLAP_MARGIN_MILLIS = 5L * 60 * 1000 // 5분

        val startTime = if (effectiveSyncTime > 0) {
            maxOf(effectiveSyncTime - OVERLAP_MARGIN_MILLIS, minStartTime)
        } else {
            val cal = java.util.Calendar.getInstance()
            if (monthStartDay > 1) {
                cal.add(java.util.Calendar.MONTH, -2)
                cal.set(
                    java.util.Calendar.DAY_OF_MONTH,
                    monthStartDay.coerceAtMost(cal.getActualMaximum(java.util.Calendar.DAY_OF_MONTH))
                )
            } else {
                cal.add(java.util.Calendar.MONTH, -1)
                cal.set(java.util.Calendar.DAY_OF_MONTH, 1)
            }
            cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
            cal.set(java.util.Calendar.MINUTE, 0)
            cal.set(java.util.Calendar.SECOND, 0)
            cal.set(java.util.Calendar.MILLISECOND, 0)
            cal.timeInMillis
        }

        return Pair(startTime, now)
    }

    /** 동기화 결과 처리 (UI 상태 업데이트 + snackbar + 데이터 변경 통지) */
    private suspend fun handleSyncResult(result: SyncResult, silent: Boolean) {
        MoneyTalkLogger.i("syncSmsV2 완료: 지출 ${result.expenseCount}건, 수입 ${result.incomeCount}건")

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

        // HomeVM/HistoryVM에 데이터 변경 통지
        notifyDataChanged()

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
            } else {
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

    // ========== 전체 동기화 해제 (리워드 광고) ==========

    /**
     * 전체 동기화 광고 다이얼로그 표시 (광고 미로드 시 프리로드도 함께 실행)
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

    /** 전체 동기화 광고 다이얼로그 닫기 */
    fun dismissFullSyncAdDialog() {
        _uiState.update { it.copy(showFullSyncAdDialog = false) }
    }

    /**
     * 월별 동기화 해제 (광고 시청 완료 후 호출)
     *
     * 지정된 월의 SMS만 가져오고, 해당 월을 syncedMonths에 기록.
     *
     * @param year 대상 연도
     * @param month 대상 월
     */
    fun unlockFullSync(year: Int, month: Int, isFreeSyncUsed: Boolean = false) {
        val yearMonth = String.format("%04d-%02d", year, month)
        _uiState.update { it.copy(showFullSyncAdDialog = false) }

        val monthRange = calculateMonthRange(year, month)
        val (effYear, effMonth) = DateUtils.getEffectiveCurrentMonth(_uiState.value.monthStartDay)
        val isCurrentMonth = year == effYear && month == effMonth
        val monthLabel = if (isCurrentMonth) "이번달" else "${month}월"
        snackbarBus.show("${monthLabel} 데이터를 가져옵니다.")

        syncSmsV2(
            monthRange,
            updateLastSyncTime = false,
            onSyncComplete = {
                settingsDataStore.addSyncedMonth(yearMonth)
                if (isFreeSyncUsed) {
                    settingsDataStore.incrementFreeSyncUsedCount()
                }
            }
        )
    }

    /** 해당 월이 이미 동기화(광고 시청) 되었는지 확인 */
    fun isMonthSynced(year: Int, month: Int): Boolean {
        val state = _uiState.value
        if (state.isLegacyFullSyncUnlocked) return true
        val yearMonth = String.format("%04d-%02d", year, month)
        return yearMonth in state.syncedMonths
    }

    /**
     * 특정 년/월의 커스텀 월 기간 계산 (사용자 설정 monthStartDay 반영)
     */
    private fun calculateMonthRange(year: Int, month: Int): Pair<Long, Long> {
        return DateUtils.getCustomMonthPeriod(year, month, _uiState.value.monthStartDay)
    }

    /**
     * 해당 페이지의 커스텀 월이 동기화 범위에 부분만 포함되는지 판단
     *
     * 해당 월이 이미 동기화(광고 시청) 되었으면 → false (완전 커버)
     * 미동기화 시 → 커스텀 월 시작이 (현재 - DEFAULT_SYNC_PERIOD_MILLIS) 이전이면 부분 커버
     */
    fun isPagePartiallyCovered(year: Int, month: Int): Boolean {
        val state = _uiState.value
        if (state.isLegacyFullSyncUnlocked) return false
        val yearMonth = String.format("%04d-%02d", year, month)
        if (yearMonth in state.syncedMonths) return false
        val (customMonthStart, _) = DateUtils.getCustomMonthPeriod(
            year, month, state.monthStartDay
        )
        val syncCoverageStart = System.currentTimeMillis() - DEFAULT_SYNC_PERIOD_MILLIS
        return customMonthStart < syncCoverageStart
    }

    /**
     * 특정 월 데이터만 동기화 (해제 후 메뉴에서 호출)
     */
    fun syncMonthData(year: Int, month: Int) {
        val monthRange = calculateMonthRange(year, month)
        syncSmsV2(
            monthRange,
            updateLastSyncTime = false
        )
    }

    /** 전체 동기화 해제용 광고 준비 */
    fun preloadFullSyncAd() {
        rewardAdManager.preloadAd()
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
                    onProgress = { round, classifiedInRound, remaining ->
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
