package com.sanha.moneytalk.feature.home.ui

import android.content.ContentResolver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sanha.moneytalk.core.database.dao.CategorySum
import com.sanha.moneytalk.core.database.entity.ExpenseEntity
import com.sanha.moneytalk.core.database.entity.IncomeEntity
import com.sanha.moneytalk.core.datastore.SettingsDataStore
import com.sanha.moneytalk.core.model.Category
import com.sanha.moneytalk.core.model.SmsAnalysisResult
import com.sanha.moneytalk.core.ui.AppSnackbarBus
import com.sanha.moneytalk.core.ui.ClassificationState
import com.sanha.moneytalk.core.util.DataRefreshEvent
import com.sanha.moneytalk.core.util.DateUtils
import com.sanha.moneytalk.core.util.HybridSmsClassifier
import com.sanha.moneytalk.core.util.SmsBatchProcessor
import com.sanha.moneytalk.core.util.SmsMessage
import com.sanha.moneytalk.core.util.SmsParser
import com.sanha.moneytalk.core.util.SmsReader
import com.sanha.moneytalk.feature.chat.data.GeminiRepository
import com.sanha.moneytalk.feature.home.data.CategoryClassifierService
import com.sanha.moneytalk.feature.home.data.ExpenseRepository
import com.sanha.moneytalk.feature.home.data.IncomeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import androidx.compose.runtime.Stable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

/**
 * 홈 화면 UI 상태
 *
 * @property isLoading 데이터 로딩 중 여부
 * @property isRefreshing Pull-to-Refresh 진행 중 여부
 * @property selectedYear 선택된 연도
 * @property selectedMonth 선택된 월
 * @property monthStartDay 월 시작일 (1~28, 사용자 설정)
 * @property monthlyIncome 해당 월 총 수입
 * @property monthlyExpense 해당 월 총 지출
 * @property categoryExpenses 카테고리별 지출 합계 목록
 * @property recentExpenses 최근 지출 내역 목록 (카테고리 필터용)
 * @property todayExpenses 오늘 지출 내역 (오늘 내역 섹션용)
 * @property todayIncomes 오늘 수입 내역 (오늘 내역 섹션용)
 * @property periodLabel 표시용 기간 레이블 (예: "1/25 ~ 2/24")
 * @property errorMessage 에러 메시지 (null이면 에러 없음)
 * @property isSyncing SMS 동기화 진행 중 여부
 */
@Stable
data class HomeUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val selectedYear: Int = DateUtils.getCurrentYear(),
    val selectedMonth: Int = DateUtils.getCurrentMonth(),
    val monthStartDay: Int = 1,
    val monthlyIncome: Int = 0,
    val monthlyExpense: Int = 0,
    val categoryExpenses: List<CategorySum> = emptyList(),
    val recentExpenses: List<ExpenseEntity> = emptyList(),
    val todayExpenses: List<ExpenseEntity> = emptyList(),
    val todayIncomes: List<IncomeEntity> = emptyList(),
    val periodLabel: String = "",
    val errorMessage: String? = null,
    val isSyncing: Boolean = false,
    // 오늘의 지출
    val todayExpense: Int = 0,
    val todayExpenseCount: Int = 0,
    // 전월 대비
    val lastMonthExpense: Int = 0,
    val comparisonPeriodLabel: String = "", // 예: "2/21 ~ 2/28"
    // AI 인사이트
    val aiInsight: String = "",
    // 카테고리 필터 (null이면 전체 표시)
    val selectedCategory: String? = null,
    // 카테고리 분류 관련
    val showClassifyDialog: Boolean = false,
    val unclassifiedCount: Int = 0,
    val isClassifying: Boolean = false,
    val classifyProgress: String = "",
    val classifyProgressCurrent: Int = 0,
    val classifyProgressTotal: Int = 0,
    // SMS 동기화 진행 관련
    val showSyncDialog: Boolean = false,
    val syncProgress: String = "",
    val syncProgressCurrent: Int = 0,
    val syncProgressTotal: Int = 0
)

/**
 * 홈 화면 ViewModel
 *
 * 홈 화면의 월간 지출 현황, 카테고리별 지출, 최근 지출 내역을 관리합니다.
 * SMS 동기화 기능을 통해 카드 결제 문자에서 자동으로 지출 내역을 추출합니다.
 *
 * 주요 기능:
 * - 월별 수입/지출/잔여 예산 표시
 * - 카테고리별 지출 합계 표시
 * - 최근 지출 내역 목록 표시
 * - SMS 동기화 (증분/전체)
 * - Pull-to-Refresh 지원
 * - 카테고리 수동 변경
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val expenseRepository: ExpenseRepository,
    private val incomeRepository: IncomeRepository,
    private val categoryClassifierService: CategoryClassifierService,
    private val smsReader: SmsReader,
    private val settingsDataStore: SettingsDataStore,
    private val hybridSmsClassifier: HybridSmsClassifier,
    private val smsBatchProcessor: SmsBatchProcessor,
    private val dataRefreshEvent: DataRefreshEvent,
    private val ownedCardRepository: com.sanha.moneytalk.core.database.OwnedCardRepository,
    private val smsExclusionRepository: com.sanha.moneytalk.core.database.SmsExclusionRepository,
    private val geminiRepository: GeminiRepository,
    private val snackbarBus: AppSnackbarBus,
    private val classificationState: ClassificationState
) : ViewModel() {

    companion object {
        /** DB 배치 삽입 크기 */
        private const val DB_BATCH_INSERT_SIZE = 100

        /** 기본 조회 기간 (1년, 밀리초) */
        private const val ONE_YEAR_MILLIS = 365L * 24 * 60 * 60 * 1000

        /** 배치 처리 최소 건수 (이 이상이면 배치 처리) */
        private const val BATCH_PROCESSING_THRESHOLD = 50

        /** 카테고리 분류 최대 반복 횟수 */
        private const val MAX_CLASSIFICATION_ROUNDS = 3

        /**
         * 동적 진행률 업데이트 간격 계산
         *
         * SMS 건수에 따라 UI 업데이트 빈도를 조절합니다.
         * 2만 건 기준으로 50건마다 업데이트하면 400회 recomposition이 발생하지만,
         * 200건마다 업데이트하면 100회로 줄어들어 UI 부하가 75% 감소합니다.
         *
         * @param totalCount 전체 SMS 건수
         * @return 업데이트 간격 (건)
         */
        private fun calculateProgressInterval(totalCount: Int): Int = when {
            totalCount >= 10000 -> 500    // 1만+ → 500건마다 (~20~40회 업데이트)
            totalCount >= 5000 -> 200     // 5천+ → 200건마다 (~25~50회 업데이트)
            totalCount >= 1000 -> 100     // 1천+ → 100건마다 (~10~50회 업데이트)
            else -> 50                     // 1천 미만 → 50건마다 (기존과 동일)
        }
    }

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    /** 현재 실행 중인 데이터 로드 작업 (취소 가능) */
    private var loadJob: kotlinx.coroutines.Job? = null

    /** 마지막 AI 인사이트 생성 시 사용된 입력 데이터 해시 (동일 데이터 재생성 방지) */
    private val lastInsightInputHash = AtomicInteger(0)

    init {
        loadSettings()
        observeDataRefreshEvents()
    }

    /**
     * 전역 데이터 새로고침 이벤트 구독
     * 설정에서 전체 삭제 등의 이벤트 발생 시 홈 화면 데이터를 새로고침
     */
    private fun observeDataRefreshEvents() {
        viewModelScope.launch {
            dataRefreshEvent.refreshEvent.collect { event ->
                when (event) {
                    DataRefreshEvent.RefreshType.ALL_DATA_DELETED -> {
                        // 수입/지출 상태를 즉시 0으로 초기화하고 데이터 새로고침
                        _uiState.update {
                            it.copy(
                                monthlyIncome = 0,
                                monthlyExpense = 0,
                                categoryExpenses = emptyList(),
                                recentExpenses = emptyList()
                            )
                        }
                        loadSettings()
                    }

                    DataRefreshEvent.RefreshType.CATEGORY_UPDATED,
                    DataRefreshEvent.RefreshType.OWNED_CARD_UPDATED -> {
                        loadData()
                    }
                }
            }
        }
    }

    /**
     * 설정 로드 및 월 시작일 변경 감지
     * 월 시작일이 변경되면 자동으로 홈 데이터를 다시 로드합니다.
     */
    private fun loadSettings() {
        viewModelScope.launch {
            settingsDataStore.monthStartDayFlow.collect { monthStartDay ->
                val (year, month) = withContext(Dispatchers.IO) {
                    val (_, endTs) = DateUtils.getCurrentCustomMonthPeriod(monthStartDay)
                    val calendar = java.util.Calendar.getInstance().apply { timeInMillis = endTs }
                    Pair(
                        calendar.get(java.util.Calendar.YEAR),
                        calendar.get(java.util.Calendar.MONTH) + 1
                    )
                }
                _uiState.update {
                    it.copy(
                        monthStartDay = monthStartDay,
                        selectedYear = year,
                        selectedMonth = month
                    )
                }
                loadData()
            }
        }
    }

    /**
     * 홈 화면 데이터 로드
     * 선택된 월의 수입, 지출, 카테고리별 합계, 최근 지출 내역을 조회합니다.
     */
    private fun loadData() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            try {
                val state = _uiState.value
                val (monthStart, monthEnd) = DateUtils.getCustomMonthPeriod(
                    state.selectedYear, state.selectedMonth, state.monthStartDay
                )
                val periodLabel = DateUtils.formatCustomMonthPeriod(
                    state.selectedYear, state.selectedMonth, state.monthStartDay
                )

                // 제외 키워드 로드 (필터링용)
                val exclusionKeywords = withContext(Dispatchers.IO) {
                    smsExclusionRepository.getAllKeywordStrings()
                }

                // 수입 로드 (1회성, 제외 키워드 필터 적용)
                val totalIncome = withContext(Dispatchers.IO) {
                    if (exclusionKeywords.isEmpty()) {
                        incomeRepository.getTotalIncomeByDateRange(monthStart, monthEnd)
                    } else {
                        val incomes =
                            incomeRepository.getIncomesByDateRangeOnce(monthStart, monthEnd)
                        incomes.filter { income ->
                            val smsLower = income.originalSms?.lowercase()
                            smsLower == null || exclusionKeywords.none { kw -> smsLower.contains(kw) }
                        }.sumOf { it.amount }
                    }
                }

                // 오늘의 지출 조회
                val todayStart = DateUtils.getTodayStartTimestamp()
                val todayEnd = DateUtils.getTodayEndTimestamp()
                val todayExpenses = withContext(Dispatchers.IO) {
                    expenseRepository.getExpensesByDateRangeOnce(todayStart, todayEnd)
                }
                val filteredTodayExpenses = if (exclusionKeywords.isEmpty()) {
                    todayExpenses
                } else {
                    todayExpenses.filter { expense ->
                        val smsLower = expense.originalSms?.lowercase()
                        smsLower == null || exclusionKeywords.none { kw -> smsLower.contains(kw) }
                    }
                }

                // 오늘의 수입 조회
                val todayIncomesList = withContext(Dispatchers.IO) {
                    incomeRepository.getIncomesByDateRangeOnce(todayStart, todayEnd)
                }
                val filteredTodayIncomes = if (exclusionKeywords.isEmpty()) {
                    todayIncomesList
                } else {
                    todayIncomesList.filter { income ->
                        val smsLower = income.originalSms?.lowercase()
                        smsLower == null || exclusionKeywords.none { kw -> smsLower.contains(kw) }
                    }
                }

                // 전월 동일 기간 지출 조회
                // 선택 월 기준: 현재 시점이 선택 월 내라면 현재 시각, 아니면 monthEnd를 기준점으로 사용
                val now = System.currentTimeMillis()
                val referencePoint = if (now in monthStart..monthEnd) now else monthEnd
                val elapsedDays = ((referencePoint - monthStart) / (24L * 60 * 60 * 1000)).toInt()
                val prevYear = if (state.selectedMonth == 1) state.selectedYear - 1 else state.selectedYear
                val prevMonth = if (state.selectedMonth == 1) 12 else state.selectedMonth - 1
                val (lastMonthStart, _) = DateUtils.getCustomMonthPeriod(
                    prevYear, prevMonth, state.monthStartDay
                )
                // 전월 시작일 + 동일 경과일수 = 전월 동일 시점
                val lastMonthSamePoint = lastMonthStart + (elapsedDays.toLong() * 24 * 60 * 60 * 1000)
                val lastMonthExpenses = withContext(Dispatchers.IO) {
                    expenseRepository.getExpensesByDateRangeOnce(lastMonthStart, lastMonthSamePoint)
                }
                // 전월 지출에도 제외 키워드 필터 적용
                val filteredLastMonthExpenses = if (exclusionKeywords.isEmpty()) {
                    lastMonthExpenses
                } else {
                    lastMonthExpenses.filter { expense ->
                        val smsLower = expense.originalSms?.lowercase()
                        smsLower == null || exclusionKeywords.none { kw -> smsLower.contains(kw) }
                    }
                }
                val filteredLastMonthExpense = filteredLastMonthExpenses.sumOf { it.amount }

                // 비교 기간 레이블 생성 - 전월 기간 표시 (예: "1/21 ~ 1/28")
                val dateFormat = java.text.SimpleDateFormat("M/d", java.util.Locale.KOREA)
                val comparisonLabel = "${dateFormat.format(java.util.Date(lastMonthStart))} ~ ${dateFormat.format(java.util.Date(lastMonthSamePoint))}"

                _uiState.update {
                    it.copy(
                        periodLabel = periodLabel,
                        monthlyIncome = totalIncome,
                        todayExpense = filteredTodayExpenses.sumOf { e -> e.amount },
                        todayExpenseCount = filteredTodayExpenses.size,
                        todayExpenses = filteredTodayExpenses.sortedByDescending { e -> e.dateTime },
                        todayIncomes = filteredTodayIncomes.sortedByDescending { e -> e.dateTime },
                        lastMonthExpense = filteredLastMonthExpense,
                        comparisonPeriodLabel = comparisonLabel
                    )
                }

                // 지출 내역은 Flow로 실시간 감지 (Room DB 변경 시 자동 업데이트)
                var insightLoaded = false
                expenseRepository.getExpensesByDateRange(monthStart, monthEnd)
                    .catch { e ->
                        _uiState.update { it.copy(isLoading = false, errorMessage = e.message) }
                    }
                    .collect { allExpenses ->
                        // 제외 키워드 필터 적용
                        val expenses = if (exclusionKeywords.isEmpty()) {
                            allExpenses
                        } else {
                            allExpenses.filter { expense ->
                                val smsLower = expense.originalSms?.lowercase()
                                smsLower == null || exclusionKeywords.none { kw ->
                                    smsLower.contains(
                                        kw
                                    )
                                }
                            }
                        }
                        val totalExpense = expenses.sumOf { it.amount }
                        // 카테고리별 합계도 필터링된 데이터 기준으로 계산
                        // 소 카테고리(예: 배달)는 대 카테고리(예: 식비)에 합산
                        val categories = expenses
                            .groupBy { expense ->
                                val cat = Category.fromDisplayName(expense.category)
                                cat.parentCategory?.displayName ?: cat.displayName
                            }
                            .map { (category, items) ->
                                CategorySum(category = category, total = items.sumOf { it.amount })
                            }
                            .sortedByDescending { it.total }
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                monthlyExpense = totalExpense,
                                categoryExpenses = categories,
                                recentExpenses = expenses.sortedByDescending { e -> e.dateTime }
                            )
                        }

                        // AI 인사이트 생성 (loadData 호출 시 첫 emit에서만 생성, DB 변경 emit은 스킵)
                        if (!insightLoaded) {
                            insightLoaded = true
                            // 이번 달 TOP 3 카테고리 기준으로 전월 동일 카테고리 금액 매칭
                            val top3 = categories.take(3)
                            val lastMonthByCategory = filteredLastMonthExpenses
                                .groupBy { expense ->
                                    val cat = Category.fromDisplayName(expense.category)
                                    cat.parentCategory?.displayName ?: cat.displayName
                                }
                                .mapValues { (_, items) -> items.sumOf { it.amount } }
                            val lastMonthTop3ForComparison = top3.map { c ->
                                Pair(c.category, lastMonthByCategory[c.category] ?: 0)
                            }
                            loadAiInsight(
                                totalExpense,
                                filteredLastMonthExpense,
                                filteredTodayExpenses.sumOf { e -> e.amount },
                                top3.map { c -> Pair(c.category, c.total) },
                                lastMonthTop3ForComparison
                            )
                        }
                    }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = e.message)
                }
            }
        }
    }

    /** AI 인사이트 비동기 생성 (월 전환 시 이전 응답 무시, 입력 데이터 동일 시 스킵) */
    private fun loadAiInsight(
        monthlyExpense: Int,
        lastMonthExpense: Int,
        todayExpense: Int,
        topCategories: List<Pair<String, Int>>,
        lastMonthTopCategories: List<Pair<String, Int>>
    ) {
        // 입력 데이터 해시 비교 — 동일하면 재생성 스킵 (탭 전환/resume 시 불필요한 Gemini 호출 방지)
        val inputHash = listOf(monthlyExpense, lastMonthExpense, todayExpense, topCategories, lastMonthTopCategories).hashCode()
        if (inputHash == lastInsightInputHash.get() && _uiState.value.aiInsight.isNotEmpty()) return
        lastInsightInputHash.set(inputHash)

        // 요청 시점의 월 정보 캡처 — 응답 도착 시 월이 바뀌었으면 무시
        val requestMonth = _uiState.value.selectedMonth
        val requestYear = _uiState.value.selectedYear
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val insight = geminiRepository.generateHomeInsight(
                    monthlyExpense = monthlyExpense,
                    lastMonthExpense = lastMonthExpense,
                    todayExpense = todayExpense,
                    topCategories = topCategories,
                    lastMonthTopCategories = lastMonthTopCategories
                )
                if (insight != null) {
                    val currentState = _uiState.value
                    // 응답 도착 시 월이 바뀌었으면 무시
                    if (currentState.selectedMonth == requestMonth &&
                        currentState.selectedYear == requestYear
                    ) {
                        _uiState.update { it.copy(aiInsight = insight) }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("HomeViewModel", "AI 인사이트 생성 실패 (무시): ${e.message}")
            }
        }
    }

    /** 이전 월로 이동 */
    fun previousMonth() {
        val state = _uiState.value
        var newYear = state.selectedYear
        var newMonth = state.selectedMonth - 1
        if (newMonth < 1) {
            newMonth = 12
            newYear -= 1
        }
        lastInsightInputHash.set(0)
        _uiState.update { it.copy(selectedYear = newYear, selectedMonth = newMonth, aiInsight = "") }
        loadData()
    }

    /** 다음 월로 이동 */
    fun nextMonth() {
        val state = _uiState.value
        var newYear = state.selectedYear
        var newMonth = state.selectedMonth + 1
        if (newMonth > 12) {
            newMonth = 1
            newYear += 1
        }
        lastInsightInputHash.set(0)
        _uiState.update { it.copy(selectedYear = newYear, selectedMonth = newMonth, aiInsight = "") }
        loadData()
    }

    /** 화면이 다시 표시될 때 데이터 새로고침 (LaunchedEffect에서 호출) */
    fun refreshData() {
        loadData()
    }

    /**
     * SMS 메시지 동기화 (하이브리드 3-tier 분류)
     *
     * 카드 결제 문자를 읽어서 지출 내역으로 변환합니다.
     *
     * 분류 전략:
     * 1단계 (Regex): 기존 정규식으로 빠르게 분류 + 파싱
     * 2단계 (Vector): 정규식 미스 SMS를 벡터 유사도로 재분류
     * 3단계 (LLM): 벡터로 확인된 결제 SMS의 데이터를 Gemini로 추출
     *
     * @param contentResolver SMS 읽기용 ContentResolver
     * @param forceFullSync true면 전체 동기화, false면 마지막 동기화 이후만 (증분 동기화)
     */
    fun syncSmsMessages(
        contentResolver: ContentResolver,
        forceFullSync: Boolean = false,
        todayOnly: Boolean = false
    ) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSyncing = true,
                    showSyncDialog = true,
                    syncProgress = "문자 읽는 중...",
                    syncProgressCurrent = 0,
                    syncProgressTotal = 0
                )
            }

            try {
                // ===== 모든 무거운 작업을 IO 스레드에서 실행 (UI 스레드 블로킹 방지) =====
                data class SyncResult(
                    val regexCount: Int,
                    val incomeCount: Int,
                    val hasGeminiKey: Boolean,
                    val regexLearningData: List<Triple<String, String, SmsAnalysisResult>>,
                    val detectedCardNames: List<String> = emptyList(),
                    val hybridSmsList: List<SmsMessage> = emptyList()
                )

                val result = withContext(Dispatchers.IO) {
                    // DB에서 사용자 제외 키워드 로드 → SmsParser에 설정
                    val userExcludeKeywords = smsExclusionRepository.getUserKeywords()
                    SmsParser.setUserExcludeKeywords(userExcludeKeywords)

                    // 마지막 동기화 시간 가져오기
                    val lastSyncTime = when {
                        forceFullSync -> 0L
                        todayOnly -> {
                            // 오늘 자정 (00:00:00) 기준
                            val cal = java.util.Calendar.getInstance()
                            cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
                            cal.set(java.util.Calendar.MINUTE, 0)
                            cal.set(java.util.Calendar.SECOND, 0)
                            cal.set(java.util.Calendar.MILLISECOND, 0)
                            cal.timeInMillis
                        }

                        else -> settingsDataStore.getLastSyncTime()
                    }
                    val currentTime = System.currentTimeMillis()

                    android.util.Log.e("sanha", "=== syncSmsMessages (Hybrid) 시작 ===")
                    android.util.Log.e(
                        "sanha",
                        "forceFullSync: $forceFullSync, todayOnly: $todayOnly, lastSyncTime: $lastSyncTime"
                    )
                    android.util.Log.e(
                        "sanha",
                        "currentTime: $currentTime, 범위: ${
                            java.text.SimpleDateFormat(
                                "yyyy-MM-dd HH:mm:ss",
                                java.util.Locale.KOREA
                            ).format(java.util.Date(lastSyncTime))
                        } ~ ${
                            java.text.SimpleDateFormat(
                                "yyyy-MM-dd HH:mm:ss",
                                java.util.Locale.KOREA
                            ).format(java.util.Date(currentTime))
                        }"
                    )

                    // 진단: todayOnly일 때 모든 메시지 provider 탐색 (신한카드 등 RCS 메시지 위치 확인)
                    if (todayOnly) {
                        smsReader.diagnoseAllMessageProviders(contentResolver)
                    }

                    // ===== 성능 최적화: 인메모리 캐시 초기화 =====
                    _uiState.update { it.copy(syncProgress = "준비 중...") }
                    val existingSmsIds = expenseRepository.getAllSmsIds() // O(1) 조회용 HashSet
                    val existingIncomeSmsIds = incomeRepository.getAllSmsIds().toHashSet()
                    categoryClassifierService.initCategoryCache() // DB 쿼리 제거

                    // ===== 전체 SMS를 한 번만 읽고 지출/수입 동시 분류 =====
                    // ContentResolver 쿼리 6회(지출3+수입3) → 3회(전체1세트)로 절반 감소
                    // for-loop 2회(지출+수입) → 1회로 통합
                    _uiState.update { it.copy(syncProgress = "문자 내역 확인 중...") }
                    val allSmsList = if (lastSyncTime > 0) {
                        smsReader.readAllMessagesByDateRange(
                            contentResolver,
                            lastSyncTime,
                            currentTime
                        )
                    } else {
                        smsReader.readAllMessagesByDateRange(contentResolver, 0L, currentTime)
                    }

                    _uiState.update {
                        it.copy(
                            syncProgress = "내역 분류 중...",
                            syncProgressCurrent = 0,
                            syncProgressTotal = allSmsList.size
                        )
                    }

                    var regexCount = 0
                    var incomeCount = 0
                    val processedSmsIds = mutableSetOf<String>()
                    val expenseBatch = mutableListOf<ExpenseEntity>()
                    val incomeBatch =
                        mutableListOf<com.sanha.moneytalk.core.database.entity.IncomeEntity>()
                    val regexLearningData =
                        mutableListOf<Triple<String, String, SmsAnalysisResult>>()
                    val hybridCandidates = mutableListOf<SmsMessage>()
                    val hasGeminiKey = settingsDataStore.getGeminiApiKey().isNotBlank()

                    // 동적 진행률 업데이트 간격 (SMS 건수에 따라 UI recomposition 횟수 조절)
                    // 2만 건 기준: 50건마다 → 400회 recomposition → 500건마다 → 40회로 90% 감소
                    val progressInterval = calculateProgressInterval(allSmsList.size)

                    for ((smsIdx, sms) in allSmsList.withIndex()) {
                        try {
                            if (smsIdx % progressInterval == 0) {
                                _uiState.update {
                                    it.copy(
                                        syncProgress = "내역 분류 중... (${smsIdx}/${allSmsList.size}건)",
                                        syncProgressCurrent = smsIdx,
                                        syncProgressTotal = allSmsList.size
                                    )
                                }
                            }

                            // ===== 성능 최적화: classifySmsType()로 지출/수입 동시 판별 =====
                            // 기존: isCardPaymentSms() + isIncomeSms() → lowercase() 2회 + 키워드 스캔 중복
                            // 개선: classifySmsType() → lowercase() 1회 + 공통 키워드 스캔 1회
                            val smsType = SmsParser.classifySmsType(sms.body)

                            // --- 지출 체크 ---
                            if (smsType.isPayment) {
                                if (sms.id in existingSmsIds) {
                                    processedSmsIds.add(sms.id)
                                    continue
                                }

                                val result =
                                    hybridSmsClassifier.classifyRegexOnly(sms.body, sms.date)
                                if (result != null && result.isPayment && result.analysisResult != null) {
                                    val analysis = result.analysisResult
                                    val category = if (analysis.category.isNotBlank() &&
                                        analysis.category != "미분류" &&
                                        analysis.category != "기타"
                                    ) {
                                        analysis.category
                                    } else {
                                        categoryClassifierService.getCategory(
                                            storeName = analysis.storeName,
                                            originalSms = sms.body
                                        )
                                    }

                                    val expense = ExpenseEntity(
                                        amount = analysis.amount,
                                        storeName = analysis.storeName,
                                        category = category,
                                        cardName = analysis.cardName,
                                        dateTime = DateUtils.parseDateTime(analysis.dateTime),
                                        originalSms = sms.body,
                                        smsId = sms.id
                                    )
                                    expenseBatch.add(expense)
                                    regexCount++
                                    processedSmsIds.add(sms.id)
                                    regexLearningData.add(Triple(sms.body, sms.address, analysis))

                                    if (expenseBatch.size >= DB_BATCH_INSERT_SIZE) {
                                        expenseRepository.insertAll(expenseBatch)
                                        expenseBatch.clear()
                                    }
                                }
                                continue
                            }

                            // --- 수입 체크 (classifySmsType이 수입으로 판별한 경우만) ---
                            if (smsType.isIncome) {
                                processedSmsIds.add(sms.id)
                                if (sms.id in existingIncomeSmsIds) continue

                                val amount = SmsParser.extractIncomeAmount(sms.body)
                                val incomeType = SmsParser.extractIncomeType(sms.body)
                                val source = SmsParser.extractIncomeSource(sms.body)
                                val dateTime = SmsParser.extractDateTime(sms.body, sms.date)

                                if (amount > 0) {
                                    val income =
                                        com.sanha.moneytalk.core.database.entity.IncomeEntity(
                                            smsId = sms.id,
                                            amount = amount,
                                            type = incomeType,
                                            source = source,
                                            description = if (source.isNotBlank()) "${source}에서 $incomeType" else incomeType,
                                            isRecurring = incomeType == "급여",
                                            dateTime = DateUtils.parseDateTime(dateTime),
                                            originalSms = sms.body
                                        )
                                    incomeBatch.add(income)
                                    incomeCount++

                                    if (incomeBatch.size >= DB_BATCH_INSERT_SIZE) {
                                        incomeRepository.insertAll(incomeBatch)
                                        incomeBatch.clear()
                                    }
                                }
                                continue
                            }

                            // --- 미분류: Hybrid 후보 (지출도 수입도 아닌 SMS) ---
                            if (hasGeminiKey &&
                                sms.id !in existingSmsIds &&
                                sms.body.length >= 10
                            ) {
                                hybridCandidates.add(sms)
                            }
                        } catch (e: Exception) {
                            android.util.Log.w("sanha", "SMS 처리 실패 (무시): ${sms.id} - ${e.message}")
                        }
                    }

                    if (expenseBatch.isNotEmpty()) {
                        expenseRepository.insertAll(expenseBatch)
                        expenseBatch.clear()
                    }
                    if (incomeBatch.isNotEmpty()) {
                        incomeRepository.insertAll(incomeBatch)
                        incomeBatch.clear()
                    }

                    _uiState.update {
                        it.copy(
                            syncProgress = "지출 ${regexCount}건, 수입 ${incomeCount}건 확인 완료",
                            syncProgressCurrent = allSmsList.size,
                            syncProgressTotal = allSmsList.size
                        )
                    }
                    android.util.Log.e("sanha", "통합 분류: 지출 ${regexCount}건, 수입 ${incomeCount}건 처리")

                    // ===== 2~3단계: 비동기 처리할 미분류 SMS =====
                    val hybridSmsList: List<SmsMessage> = hybridCandidates

                    android.util.Log.e("sanha", "미분류 SMS: ${hybridSmsList.size}건 (비동기 처리 예정)")
                    android.util.Log.e("sanha", "=== 동기화 결과 요약 ===")
                    android.util.Log.e(
                        "sanha",
                        "Regex: ${regexCount}건, 수입: ${incomeCount}건, 미분류 SMS: ${hybridSmsList.size}건 (비동기), 처리된 SMS ID: ${processedSmsIds.size}건"
                    )

                    // ===== 성능 최적화: 캐시 정리 =====
                    _uiState.update { it.copy(syncProgress = "정리 중...") }
                    categoryClassifierService.flushPendingMappings()
                    categoryClassifierService.clearCategoryCache()

                    // 마지막 동기화 시간 저장
                    settingsDataStore.saveLastSyncTime(currentTime)

                    // 오래된 패턴 정리
                    hybridSmsClassifier.cleanupStalePatterns()

                    // ===== OwnedCard 자동 등록: 동기화된 지출의 카드명 수집 (중복 포함) =====
                    val allCardNames = expenseRepository.getAllCardNamesWithDuplicates()

                    SyncResult(
                        regexCount = regexCount,
                        incomeCount = incomeCount,
                        hasGeminiKey = hasGeminiKey,
                        regexLearningData = regexLearningData,
                        detectedCardNames = allCardNames,
                        hybridSmsList = hybridSmsList
                    )
                } // withContext(Dispatchers.IO) 끝

                // ===== UI 스레드에서 결과 처리 =====
                val newCount = result.regexCount

                // OwnedCard 자동 등록 (백그라운드)
                if (result.detectedCardNames.isNotEmpty()) {
                    viewModelScope.launch(Dispatchers.IO) {
                        try {
                            ownedCardRepository.registerCardsFromSync(result.detectedCardNames)
                        } catch (e: Exception) {
                            android.util.Log.w("HomeViewModel", "카드 자동 등록 실패 (무시): ${e.message}")
                        }
                    }
                }

                // 벡터 DB 배치 학습 (백그라운드, 동기화 시간에 영향 없음)
                if (result.regexLearningData.isNotEmpty() && result.hasGeminiKey) {
                    viewModelScope.launch(Dispatchers.IO) {
                        try {
                            hybridSmsClassifier.batchLearnFromRegexResults(result.regexLearningData)
                            android.util.Log.d(
                                "HomeViewModel",
                                "벡터 패턴 학습 완료: ${result.regexLearningData.size}건"
                            )
                        } catch (e: Exception) {
                            android.util.Log.e("HomeViewModel", "벡터 패턴 학습 실패: ${e.message}", e)
                        }
                    }
                }

                // 데이터 새로고침
                loadData()

                // 결과 메시지 생성
                val resultMessage = when {
                    newCount > 0 && result.incomeCount > 0 -> "${newCount}건의 지출, ${result.incomeCount}건의 수입이 추가되었습니다"
                    newCount > 0 -> "${newCount}건의 새 지출이 추가되었습니다"
                    result.incomeCount > 0 -> "${result.incomeCount}건의 새 수입이 추가되었습니다"
                    else -> "새로운 내역이 없습니다"
                }

                _uiState.update {
                    it.copy(
                        isSyncing = false,
                        showSyncDialog = false,
                        syncProgress = "",
                        syncProgressCurrent = 0,
                        syncProgressTotal = 0,
                        errorMessage = resultMessage
                    )
                }

                // ===== 비동기 처리: Hybrid SMS 분류 + 카테고리 자동 분류 =====
                // 로딩 다이얼로그에 표시하지 않고 백그라운드에서 처리
                // 주의: Hybrid와 Category를 동시에 실행하면 Gemini API 경쟁 + DB 경합 → 렉 발생
                // Hybrid가 있으면 Hybrid 완료 후 순차로 Category 실행 (내부에서 호출)
                // Hybrid가 없으면 Category만 단독 실행
                if (result.hybridSmsList.isNotEmpty() && result.hasGeminiKey) {
                    snackbarBus.show("백그라운드에서 카테고리를 분류하고 있습니다")
                    launchBackgroundHybridClassification(result.hybridSmsList, forceFullSync)
                } else if (result.hasGeminiKey) {
                    snackbarBus.show("백그라운드에서 카테고리를 분류하고 있습니다")
                    launchBackgroundCategoryClassification()
                }
            } catch (e: Exception) {
                // 예외 시에도 캐시 정리
                categoryClassifierService.clearCategoryCache()

                _uiState.update {
                    it.copy(
                        isSyncing = false,
                        showSyncDialog = false,
                        syncProgress = "",
                        syncProgressCurrent = 0,
                        syncProgressTotal = 0,
                        errorMessage = "동기화 실패: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * Pull-to-Refresh 및 외부에서 호출 가능한 데이터 새로고침
     * 다른 화면에서 DB 변경 시에도 호출됨
     */
    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }

            try {
                val (monthStartDay, year, month) = withContext(Dispatchers.IO) {
                    val msd = settingsDataStore.getMonthStartDay()
                    val (_, endTs) = DateUtils.getCurrentCustomMonthPeriod(msd)
                    val calendar = java.util.Calendar.getInstance().apply { timeInMillis = endTs }
                    Triple(
                        msd,
                        calendar.get(java.util.Calendar.YEAR),
                        calendar.get(java.util.Calendar.MONTH) + 1
                    )
                }

                _uiState.update {
                    it.copy(
                        monthStartDay = monthStartDay,
                        selectedYear = year,
                        selectedMonth = month,
                        isRefreshing = false
                    )
                }

                // loadData()가 Flow 기반이므로 새로 시작하면 자동 갱신
                loadData()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, isRefreshing = false, errorMessage = e.message)
                }
            }
        }
    }

    /** 카테고리 필터 선택/해제 */
    fun selectCategory(category: String?) {
        _uiState.update { it.copy(selectedCategory = category) }
    }

    /** 에러 메시지 초기화 */
    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    /**
     * 미분류 항목을 Gemini로 일괄 분류
     */
    fun classifyUnclassifiedExpenses(onResult: (Int) -> Unit) {
        viewModelScope.launch {
            try {
                val count = withContext(Dispatchers.IO) {
                    categoryClassifierService.classifyUnclassifiedExpenses()
                }
                if (count > 0) {
                    loadData()
                }
                onResult(count)
            } catch (e: Exception) {
                android.util.Log.e("HomeViewModel", "분류 실패: ${e.message}")
                onResult(0)
            }
        }
    }

    /**
     * 미분류 항목 수 조회
     */
    fun getUnclassifiedCount(onResult: (Int) -> Unit) {
        viewModelScope.launch {
            val count = withContext(Dispatchers.IO) {
                categoryClassifierService.getUnclassifiedCount()
            }
            onResult(count)
        }
    }

    // ===== 비동기 백그라운드 처리 메서드 =====

    /**
     * Hybrid SMS 분류를 백그라운드에서 비동기 처리
     * Tier 2~3(벡터+LLM) 분류 → 추가 결제건 저장 → 카테고리 자동 분류 → 토스트 알림
     * 로딩 다이얼로그에 표시하지 않음
     */
    private fun launchBackgroundHybridClassification(
        hybridSmsList: List<SmsMessage>,
        forceFullSync: Boolean
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            classificationState.setRunning(true)
            try {
                android.util.Log.d("HomeViewModel", "백그라운드 Hybrid 분류 시작: ${hybridSmsList.size}건")
                var hybridCount = 0
                val expenseBatch = mutableListOf<ExpenseEntity>()
                val existingSmsIds = expenseRepository.getAllSmsIds()

                if (forceFullSync || hybridSmsList.size > BATCH_PROCESSING_THRESHOLD) {
                    // 배치 모드
                    val batchData = hybridSmsList.map { sms ->
                        SmsBatchProcessor.SmsData(
                            id = sms.id,
                            address = sms.address,
                            body = sms.body,
                            date = sms.date
                        )
                    }

                    val batchResults = smsBatchProcessor.processBatch(
                        unclassifiedSms = batchData
                    )

                    for ((smsData, analysis) in batchResults) {
                        try {
                            if (analysis.amount > 0 && smsData.id !in existingSmsIds) {
                                val category = if (analysis.category.isNotBlank() &&
                                    analysis.category != "미분류" &&
                                    analysis.category != "기타"
                                ) {
                                    analysis.category
                                } else {
                                    categoryClassifierService.getCategory(
                                        storeName = analysis.storeName,
                                        originalSms = smsData.body
                                    )
                                }

                                val expense = ExpenseEntity(
                                    amount = analysis.amount,
                                    storeName = analysis.storeName,
                                    category = category,
                                    cardName = analysis.cardName,
                                    dateTime = DateUtils.parseDateTime(analysis.dateTime),
                                    originalSms = smsData.body,
                                    smsId = smsData.id
                                )
                                expenseBatch.add(expense)
                                hybridCount++

                                if (expenseBatch.size >= DB_BATCH_INSERT_SIZE) {
                                    expenseRepository.insertAll(expenseBatch)
                                    expenseBatch.clear()
                                }
                            }
                        } catch (e: Exception) {
                            android.util.Log.w("HomeViewModel", "배치 결과 저장 실패 (무시): ${e.message}")
                        }
                    }
                } else {
                    // 소량 배치 모드
                    try {
                        val batchInput = hybridSmsList.map { sms ->
                            Triple(sms.body, sms.date, sms.address)
                        }
                        val batchResults = hybridSmsClassifier.batchClassify(batchInput)

                        for ((idx, classResult) in batchResults.withIndex()) {
                            if (classResult.isPayment && classResult.analysisResult != null) {
                                val analysis = classResult.analysisResult
                                if (analysis.amount > 0) {
                                    val sms = hybridSmsList[idx]
                                    val category = if (analysis.category.isNotBlank() &&
                                        analysis.category != "미분류" &&
                                        analysis.category != "기타"
                                    ) {
                                        analysis.category
                                    } else {
                                        categoryClassifierService.getCategory(
                                            storeName = analysis.storeName,
                                            originalSms = sms.body
                                        )
                                    }

                                    val expense = ExpenseEntity(
                                        amount = analysis.amount,
                                        storeName = analysis.storeName,
                                        category = category,
                                        cardName = analysis.cardName,
                                        dateTime = DateUtils.parseDateTime(analysis.dateTime),
                                        originalSms = sms.body,
                                        smsId = sms.id
                                    )
                                    expenseBatch.add(expense)
                                    hybridCount++
                                }
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("HomeViewModel", "소량 배치 하이브리드 분류 실패 (무시): ${e.message}")
                    }
                }

                // 남은 배치 저장
                if (expenseBatch.isNotEmpty()) {
                    expenseRepository.insertAll(expenseBatch)
                }

                android.util.Log.d("HomeViewModel", "백그라운드 Hybrid 분류 완료: ${hybridCount}건 추가")

                if (hybridCount > 0) {
                    // 데이터 변경 → UI 새로고침 + 토스트
                    withContext(Dispatchers.Main) {
                        loadData()
                        _uiState.update {
                            it.copy(errorMessage = "${hybridCount}건의 추가 지출이 발견되었습니다")
                        }
                    }

                    // 추가된 결제건에 대해 카테고리 분류도 비동기 실행
                    // (launchBackgroundCategoryClassificationInternal의 finally에서 setRunning(false) 처리)
                    launchBackgroundCategoryClassificationInternal()
                } else {
                    classificationState.setRunning(false)
                }
            } catch (e: Exception) {
                classificationState.setRunning(false)
                android.util.Log.e("HomeViewModel", "백그라운드 Hybrid 분류 실패: ${e.message}", e)
            }
        }
    }

    /**
     * 카테고리 자동 분류를 백그라운드에서 실행 (얼럿 없이 자동)
     * 동기화 완료 후 호출됨
     */
    private fun launchBackgroundCategoryClassification() {
        viewModelScope.launch(Dispatchers.IO) {
            launchBackgroundCategoryClassificationInternal()
        }
    }

    /**
     * 카테고리 자동 분류 내부 로직 (IO 디스패처에서 실행)
     *
     * 2-phase 전략:
     * Phase 1: 상위 50개 가게(총액 기준)만 빠르게 분류 → 즉시 UI 반영
     * Phase 2: 나머지를 백그라운드에서 전체 분류 (최대 3라운드)
     */
    private suspend fun launchBackgroundCategoryClassificationInternal() {
        try {
            val count = categoryClassifierService.getUnclassifiedCount()
            if (count == 0) {
                android.util.Log.d("HomeViewModel", "미분류 항목 없음, 자동 분류 스킵")
                return
            }

            classificationState.setRunning(true)
            android.util.Log.d("HomeViewModel", "백그라운드 카테고리 자동 분류 시작: ${count}건")

            // ===== Phase 1: 상위 50개 가게 빠르게 분류 =====
            val phase1Count = categoryClassifierService.classifyUnclassifiedExpenses(
                maxStoreCount = 50
            )

            if (phase1Count > 0) {
                withContext(Dispatchers.Main) {
                    loadData()
                    _uiState.update { it.copy(errorMessage = "${phase1Count}건의 카테고리가 정리되었습니다") }
                }
            }

            // ===== Phase 2: 나머지 전체 분류 =====
            val remainingCount = categoryClassifierService.getUnclassifiedCount()
            if (remainingCount > 0) {
                android.util.Log.d("HomeViewModel", "Phase 2: 남은 ${remainingCount}건 전체 분류 시작")

                val phase2Classified = categoryClassifierService.classifyAllUntilComplete(
                    onProgress = { round, classifiedInRound, remaining ->
                        android.util.Log.d(
                            "HomeViewModel",
                            "자동 분류 라운드 $round: ${classifiedInRound}건 완료 (남은: ${remaining}건)"
                        )
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
                        loadData()
                        _uiState.update { it.copy(errorMessage = message) }
                    }
                }
            }

            android.util.Log.d("HomeViewModel", "백그라운드 카테고리 자동 분류 완료")
        } catch (e: Exception) {
            android.util.Log.e("HomeViewModel", "백그라운드 카테고리 자동 분류 실패: ${e.message}", e)
        } finally {
            classificationState.setRunning(false)
        }
    }

    /**
     * 동기화 완료 후 미분류 항목 확인하고 분류 다이얼로그 표시
     * (설정에서 수동으로 호출할 때만 사용)
     */
    fun checkUnclassifiedAfterSync() {
        viewModelScope.launch {
            val (count, hasApiKey) = withContext(Dispatchers.IO) {
                Pair(
                    categoryClassifierService.getUnclassifiedCount(),
                    categoryClassifierService.hasGeminiApiKey()
                )
            }
            if (count > 0 && hasApiKey) {
                _uiState.update {
                    it.copy(showClassifyDialog = true, unclassifiedCount = count)
                }
            }
        }
    }

    /**
     * 분류 다이얼로그 닫기
     */
    fun dismissClassifyDialog() {
        _uiState.update { it.copy(showClassifyDialog = false) }
    }

    /**
     * 미분류 항목 전체 분류 시작 (최대 3라운드)
     */
    fun startFullClassification() {
        viewModelScope.launch {
            val initialCount = withContext(Dispatchers.IO) {
                categoryClassifierService.getUnclassifiedCount()
            }

            _uiState.update {
                it.copy(
                    showClassifyDialog = false,
                    isClassifying = true,
                    classifyProgress = "정리 준비 중...",
                    classifyProgressCurrent = 0,
                    classifyProgressTotal = initialCount
                )
            }

            try {
                val totalClassified = withContext(Dispatchers.IO) {
                    categoryClassifierService.classifyAllUntilComplete(
                        onProgress = { _, classifiedInRound, remaining ->
                            _uiState.update {
                                it.copy(
                                    classifyProgress = "${classifiedInRound}건 정리 완료 (남은: ${remaining}건)",
                                    classifyProgressCurrent = initialCount - remaining,
                                    classifyProgressTotal = initialCount
                                )
                            }
                        },
                        onStepProgress = { step, current, total ->
                            _uiState.update {
                                val progressText =
                                    if (total > 0) "$step ($current/$total)" else step
                                it.copy(classifyProgress = progressText)
                            }
                        },
                        maxRounds = MAX_CLASSIFICATION_ROUNDS
                    )
                }

                loadData()

                val finalRemaining = withContext(Dispatchers.IO) {
                    categoryClassifierService.getUnclassifiedCount()
                }
                val resultMessage = if (finalRemaining > 0) {
                    "${totalClassified}건 정리 완료. ${finalRemaining}건은 직접 확인이 필요합니다."
                } else {
                    "${totalClassified}건 정리 완료!"
                }

                _uiState.update {
                    it.copy(
                        isClassifying = false,
                        classifyProgress = "",
                        classifyProgressCurrent = 0,
                        classifyProgressTotal = 0,
                        errorMessage = resultMessage
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e("HomeViewModel", "전체 분류 실패: ${e.message}")
                _uiState.update {
                    it.copy(
                        isClassifying = false,
                        classifyProgress = "",
                        classifyProgressCurrent = 0,
                        classifyProgressTotal = 0,
                        errorMessage = "카테고리 정리 실패: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * Gemini API 키 존재 여부 확인
     */
    fun hasGeminiApiKey(callback: (Boolean) -> Unit) {
        viewModelScope.launch {
            val hasKey = withContext(Dispatchers.IO) {
                categoryClassifierService.hasGeminiApiKey()
            }
            callback(hasKey)
        }
    }

    /**
     * 특정 지출의 카테고리 변경
     * 동일 가게명의 모든 지출을 일괄 변경 + 벡터 학습 + 유사 가게 전파
     */
    fun updateExpenseCategory(storeName: String, newCategory: String) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    categoryClassifierService.updateCategoryForAllSameStore(
                        storeName,
                        newCategory
                    )
                }
                loadData()
            } catch (e: Exception) {
                android.util.Log.e("HomeViewModel", "카테고리 변경 실패: ${e.message}")
            }
        }
    }

    fun deleteExpense(expense: ExpenseEntity) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    expenseRepository.delete(expense)
                }
                _uiState.update { it.copy(errorMessage = "지출이 삭제되었습니다") }
                loadData()
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "삭제 실패: ${e.message}") }
            }
        }
    }

    fun updateExpenseMemo(expenseId: Long, memo: String?) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    expenseRepository.updateMemo(expenseId, memo?.ifBlank { null })
                }
                loadData()
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "메모 저장 실패: ${e.message}") }
            }
        }
    }
}
