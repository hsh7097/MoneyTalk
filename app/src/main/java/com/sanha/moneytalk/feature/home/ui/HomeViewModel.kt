package com.sanha.moneytalk.feature.home.ui

import android.content.ContentResolver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sanha.moneytalk.core.database.dao.CategorySum
import com.sanha.moneytalk.core.database.entity.ExpenseEntity
import com.sanha.moneytalk.core.database.entity.IncomeEntity
import com.sanha.moneytalk.core.datastore.SettingsDataStore
import com.sanha.moneytalk.core.firebase.AnalyticsEvent
import com.sanha.moneytalk.core.firebase.AnalyticsHelper
import com.sanha.moneytalk.core.model.Category
import com.sanha.moneytalk.core.ui.AppSnackbarBus
import com.sanha.moneytalk.core.ui.ClassificationState
import com.sanha.moneytalk.core.ui.component.MonthKey
import com.sanha.moneytalk.core.ui.component.MonthPagerUtils
import com.sanha.moneytalk.core.util.DataRefreshEvent
import com.sanha.moneytalk.core.util.DateUtils
import com.sanha.moneytalk.core.sms.HybridSmsClassifier
import com.sanha.moneytalk.core.sms.SmsBatchProcessor
import com.sanha.moneytalk.core.sms.SmsMessage
import com.sanha.moneytalk.core.sms.SmsParser
import com.sanha.moneytalk.core.sms.SmsReader
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import androidx.compose.runtime.Stable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

/**
 * 홈 화면의 페이지별(월별) 데이터.
 * HorizontalPager의 각 페이지가 독립적으로 렌더링할 수 있도록 월별 데이터를 캡슐화.
 */
@Stable
data class HomePageData(
    val isLoading: Boolean = true,
    val monthlyIncome: Int = 0,
    val monthlyExpense: Int = 0,
    val categoryExpenses: List<CategorySum> = emptyList(),
    val recentExpenses: List<ExpenseEntity> = emptyList(),
    val todayExpenses: List<ExpenseEntity> = emptyList(),
    val todayIncomes: List<IncomeEntity> = emptyList(),
    val todayExpense: Int = 0,
    val todayExpenseCount: Int = 0,
    val lastMonthExpense: Int = 0,
    val comparisonPeriodLabel: String = "",
    val periodLabel: String = "",
    val aiInsight: String = ""
)

/**
 * 홈 화면 UI 상태
 *
 * 월별 데이터는 [pageCache]에서 관리하며, 글로벌 상태만 직접 보유.
 * HorizontalPager의 각 페이지는 pageCache[MonthKey]에서 자기 월의 데이터를 읽어 렌더링.
 *
 * @property pageCache 월별 페이지 데이터 캐시 (최대 3~5개)
 * @property selectedYear 현재 선택된 연도
 * @property selectedMonth 현재 선택된 월
 * @property monthStartDay 월 시작일 (1~28, 사용자 설정)
 * @property errorMessage 에러 메시지 (null이면 에러 없음)
 * @property isSyncing SMS 동기화 진행 중 여부
 */
@Stable
data class HomeUiState(
    val pageCache: Map<MonthKey, HomePageData> = emptyMap(),
    val isRefreshing: Boolean = false,
    val selectedYear: Int = DateUtils.getCurrentYear(),
    val selectedMonth: Int = DateUtils.getCurrentMonth(),
    val monthStartDay: Int = 1,
    val errorMessage: String? = null,
    val isSyncing: Boolean = false,
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
    val syncProgressTotal: Int = 0,
    // 월별 동기화 해제 관련
    val syncedMonths: Set<String> = emptySet(),
    val isLegacyFullSyncUnlocked: Boolean = false,
    val showFullSyncAdDialog: Boolean = false
)

/**
 * 홈 화면 ViewModel
 *
 * 홈 화면의 월간 지출 현황, 카테고리별 지출, 최근 지출 내역을 관리합니다.
 * SMS 동기화 기능을 통해 카드 결제 문자에서 자동으로 지출 내역을 추출합니다.
 *
 * 주요 기능:
 * - 월별 수입/지출/잔여 예산 표시 (페이지별 독립 캐시)
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
    private val classificationState: ClassificationState,
    private val analyticsHelper: AnalyticsHelper,
    private val rewardAdManager: com.sanha.moneytalk.core.ad.RewardAdManager,
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context
) : ViewModel() {

    companion object {
        /** DB 배치 삽입 크기 */
        private const val DB_BATCH_INSERT_SIZE = 100

        /** 기본 조회 기간 (1년, 밀리초) */
        private const val ONE_YEAR_MILLIS = 365L * 24 * 60 * 60 * 1000

        /** 초기 동기화 제한 기간 (2개월, 밀리초) — 전체 동기화 미해제 시 적용 */
        private const val DEFAULT_SYNC_PERIOD_MILLIS = 60L * 24 * 60 * 60 * 1000

        /** 배치 처리 최소 건수 (이 이상이면 배치 처리) */
        private const val BATCH_PROCESSING_THRESHOLD = 50

        /** 카테고리 분류 최대 반복 횟수 */
        private const val MAX_CLASSIFICATION_ROUNDS = 3

        /** 페이지 캐시 최대 허용 범위 (현재 월 ± 이 값) */
        private const val PAGE_CACHE_RANGE = 2

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

    /** 광고 매니저 접근 (HomeScreen에서 Activity 기반 광고 표시에 필요) */
    val adManager: com.sanha.moneytalk.core.ad.RewardAdManager get() = rewardAdManager

    /** 페이지별 로드 Job 관리 (월별 독립 취소) */
    private val pageLoadJobs = mutableMapOf<MonthKey, Job>()

    /** 마지막 AI 인사이트 생성 시 사용된 입력 데이터 해시 (동일 데이터 재생성 방지) */
    private val lastInsightInputHash = AtomicInteger(0)
    /** resume 자동 분류 중복 실행 방지 플래그 */
    private val isResumeClassificationChecking = AtomicBoolean(false)

    init {
        loadSettings()
        observeDataRefreshEvents()
    }

    // ========== 페이지 캐시 관리 ==========

    /** 특정 월의 페이지 캐시 업데이트 */
    private fun updatePageCache(key: MonthKey, data: HomePageData) {
        _uiState.update { state ->
            state.copy(pageCache = state.pageCache + (key to data))
        }
    }

    /** 현재 월 ± PAGE_CACHE_RANGE 밖의 캐시 정리 */
    private fun evictDistantCache(year: Int, month: Int) {
        val currentTotal = year * 12 + month
        _uiState.update { state ->
            val filtered = state.pageCache.filter { (key, _) ->
                val keyTotal = key.year * 12 + key.month
                kotlin.math.abs(keyTotal - currentTotal) <= PAGE_CACHE_RANGE
            }
            state.copy(pageCache = filtered)
        }
    }

    /** 전체 페이지 캐시 클리어 */
    private fun clearAllPageCache() {
        pageLoadJobs.values.forEach { it.cancel() }
        pageLoadJobs.clear()
        _uiState.update { it.copy(pageCache = emptyMap()) }
    }

    /** 현재 + 인접 월 데이터 로드 (공통 진입점) */
    private fun loadCurrentAndAdjacentPages() {
        val state = _uiState.value
        val year = state.selectedYear
        val month = state.selectedMonth

        loadPageData(year, month)
        val (prevY, prevM) = MonthPagerUtils.adjacentMonth(year, month, -1)
        loadPageData(prevY, prevM)
        val (nextY, nextM) = MonthPagerUtils.adjacentMonth(year, month, +1)
        if (!MonthPagerUtils.isFutureYearMonth(nextY, nextM)) {
            loadPageData(nextY, nextM)
        }
        evictDistantCache(year, month)
    }

    // ========== 전역 이벤트 처리 ==========

    /**
     * 전역 데이터 새로고침 이벤트 구독
     * 설정에서 전체 삭제 등의 이벤트 발생 시 홈 화면 데이터를 새로고침
     */
    private fun observeDataRefreshEvents() {
        viewModelScope.launch {
            dataRefreshEvent.refreshEvent.collect { event ->
                when (event) {
                    DataRefreshEvent.RefreshType.ALL_DATA_DELETED -> {
                        // 진행 중인 백그라운드 분류 작업 즉시 취소
                        classificationState.cancelIfRunning()
                        clearAllPageCache()
                        loadSettings()
                    }

                    DataRefreshEvent.RefreshType.CATEGORY_UPDATED,
                    DataRefreshEvent.RefreshType.OWNED_CARD_UPDATED -> {
                        clearAllPageCache()
                        loadCurrentAndAdjacentPages()
                    }

                    DataRefreshEvent.RefreshType.TRANSACTION_ADDED -> {
                        clearAllPageCache()
                        loadCurrentAndAdjacentPages()
                    }
                }
            }
        }
        // History 탭에서 광고 시청 후 해당 달 SMS 동기화 요청 수신
        viewModelScope.launch {
            dataRefreshEvent.monthSyncEvent.collect { request ->
                val monthRange = calculateMonthRange(request.year, request.month)
                syncSmsMessages(
                    appContext.contentResolver,
                    forceFullSync = true,
                    targetMonthRange = monthRange
                )
            }
        }
    }

    /**
     * 설정 로드 및 월 시작일 변경 감지
     * 월 시작일이 변경되면 자동으로 홈 데이터를 다시 로드합니다.
     */
    private fun loadSettings() {
        var isFirstEmit = true
        viewModelScope.launch {
            settingsDataStore.monthStartDayFlow
                .distinctUntilChanged()
                .collect { monthStartDay ->
                    if (isFirstEmit) {
                        // 최초: 현재 월 기준으로 selectedYear/selectedMonth 설정
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
                        isFirstEmit = false
                    } else {
                        // 이후: monthStartDay만 갱신 (사용자가 보고 있는 월은 유지)
                        _uiState.update {
                            it.copy(monthStartDay = monthStartDay)
                        }
                    }
                    clearAllPageCache()
                    loadCurrentAndAdjacentPages()
                }
        }
        // 월별 동기화 해제 상태 로드
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
    }

    // ========== 페이지별 데이터 로드 ==========

    /**
     * 특정 월의 페이지 데이터 로드
     * 해당 월의 수입, 지출, 카테고리별 합계, 오늘 내역을 조회하여 pageCache에 저장.
     * @param year 대상 연도
     * @param month 대상 월
     * @param withInsight true면 AI 인사이트도 생성 (현재 월에서만 사용)
     */
    private fun loadPageData(year: Int, month: Int, withInsight: Boolean = false) {
        val key = MonthKey(year, month)
        // 이미 로드 완료된 캐시가 있으면 스킵 (스와이프 시 불필요한 재로드 방지)
        val existing = _uiState.value.pageCache[key]
        if (existing != null && !existing.isLoading) return

        pageLoadJobs[key]?.cancel()
        pageLoadJobs[key] = viewModelScope.launch {
            // 캐시에 없으면 로딩 상태로 초기화
            if (_uiState.value.pageCache[key] == null) {
                updatePageCache(key, HomePageData(isLoading = true))
            }

            try {
                val state = _uiState.value
                val (monthStart, monthEnd) = DateUtils.getCustomMonthPeriod(
                    year, month, state.monthStartDay
                )
                val periodLabel = DateUtils.formatCustomMonthPeriod(
                    year, month, state.monthStartDay
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
                        val smsLower = expense.originalSms.lowercase()
                        exclusionKeywords.none { kw -> smsLower.contains(kw) }
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
                val now = System.currentTimeMillis()
                val referencePoint = if (now in monthStart..monthEnd) now else monthEnd
                val elapsedDays = ((referencePoint - monthStart) / (24L * 60 * 60 * 1000)).toInt()
                val prevYear = if (month == 1) year - 1 else year
                val prevMonth = if (month == 1) 12 else month - 1
                val (lastMonthStart, _) = DateUtils.getCustomMonthPeriod(
                    prevYear, prevMonth, state.monthStartDay
                )
                val lastMonthSamePoint = lastMonthStart + (elapsedDays.toLong() * 24 * 60 * 60 * 1000)
                val lastMonthExpenses = withContext(Dispatchers.IO) {
                    expenseRepository.getExpensesByDateRangeOnce(lastMonthStart, lastMonthSamePoint)
                }
                val filteredLastMonthExpenses = if (exclusionKeywords.isEmpty()) {
                    lastMonthExpenses
                } else {
                    lastMonthExpenses.filter { expense ->
                        val smsLower = expense.originalSms.lowercase()
                        exclusionKeywords.none { kw -> smsLower.contains(kw) }
                    }
                }
                val filteredLastMonthExpense = filteredLastMonthExpenses.sumOf { it.amount }

                // 비교 기간 레이블 생성
                val dateFormat = java.text.SimpleDateFormat("M/d", java.util.Locale.KOREA)
                val comparisonLabel = "${dateFormat.format(java.util.Date(lastMonthStart))} ~ ${dateFormat.format(java.util.Date(lastMonthSamePoint))}"

                // 1회성 데이터를 먼저 캐시에 저장 (Flow 수집 전)
                // 기존 캐시가 있으면 isLoading 유지 (깜빡임 방지)
                val existingData = _uiState.value.pageCache[key]
                updatePageCache(key, HomePageData(
                    isLoading = existingData == null, // 캐시 없을 때만 로딩 표시
                    periodLabel = periodLabel,
                    monthlyIncome = totalIncome,
                    todayExpense = filteredTodayExpenses.sumOf { e -> e.amount },
                    todayExpenseCount = filteredTodayExpenses.size,
                    todayExpenses = filteredTodayExpenses.sortedByDescending { e -> e.dateTime },
                    todayIncomes = filteredTodayIncomes.sortedByDescending { e -> e.dateTime },
                    lastMonthExpense = filteredLastMonthExpense,
                    comparisonPeriodLabel = comparisonLabel
                ))

                // 지출 내역은 Flow로 실시간 감지 (Room DB 변경 시 자동 업데이트)
                var insightLoaded = false
                // 현재 선택 월인 경우에만 AI 인사이트 생성
                val shouldLoadInsight = withInsight ||
                    (_uiState.value.selectedYear == year && _uiState.value.selectedMonth == month)

                expenseRepository.getExpensesByDateRange(monthStart, monthEnd)
                    .catch { e ->
                        updatePageCache(key, (_uiState.value.pageCache[key] ?: HomePageData())
                            .copy(isLoading = false))
                    }
                    .collect { allExpenses ->
                        // 제외 키워드 필터 적용
                        val expenses = if (exclusionKeywords.isEmpty()) {
                            allExpenses
                        } else {
                            allExpenses.filter { expense ->
                                val smsLower = expense.originalSms.lowercase()
                                exclusionKeywords.none { kw -> smsLower.contains(kw) }
                            }
                        }
                        val totalExpense = expenses.sumOf { it.amount }
                        val categories = expenses
                            .groupBy { expense ->
                                val cat = Category.fromDisplayName(expense.category)
                                cat.parentCategory?.displayName ?: cat.displayName
                            }
                            .map { (category, items) ->
                                CategorySum(category = category, total = items.sumOf { it.amount })
                            }
                            .sortedByDescending { it.total }

                        // 현재 캐시의 1회성 데이터를 유지하면서 지출 데이터 업데이트
                        val current = _uiState.value.pageCache[key] ?: HomePageData()
                        updatePageCache(key, current.copy(
                            isLoading = false,
                            monthlyExpense = totalExpense,
                            categoryExpenses = categories,
                            recentExpenses = expenses.sortedByDescending { e -> e.dateTime }
                        ))

                        // AI 인사이트 생성 (현재 선택 월의 첫 emit에서만)
                        if (shouldLoadInsight && !insightLoaded) {
                            insightLoaded = true
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
                                key,
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
                updatePageCache(key, (_uiState.value.pageCache[key] ?: HomePageData())
                    .copy(isLoading = false))
                _uiState.update { it.copy(errorMessage = e.message) }
            }
        }
    }

    /** AI 인사이트 비동기 생성 (특정 월의 pageCache에 저장) */
    private fun loadAiInsight(
        monthKey: MonthKey,
        monthlyExpense: Int,
        lastMonthExpense: Int,
        todayExpense: Int,
        topCategories: List<Pair<String, Int>>,
        lastMonthTopCategories: List<Pair<String, Int>>
    ) {
        // 입력 데이터 해시 비교
        val inputHash = listOf(monthlyExpense, lastMonthExpense, todayExpense, topCategories, lastMonthTopCategories).hashCode()
        val existingInsight = _uiState.value.pageCache[monthKey]?.aiInsight
        if (inputHash == lastInsightInputHash.get() && existingInsight?.isNotEmpty() == true) return
        lastInsightInputHash.set(inputHash)

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
                    // 응답 도착 시 해당 월이 아직 선택 중인지 확인
                    val currentState = _uiState.value
                    if (currentState.selectedYear == monthKey.year &&
                        currentState.selectedMonth == monthKey.month
                    ) {
                        val current = currentState.pageCache[monthKey] ?: return@launch
                        updatePageCache(monthKey, current.copy(aiInsight = insight))
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("HomeViewModel", "AI 인사이트 생성 실패 (무시): ${e.message}")
            }
        }
    }

    // ========== 월 이동 ==========

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
        _uiState.update { it.copy(selectedYear = newYear, selectedMonth = newMonth) }
        loadCurrentAndAdjacentPages()
    }

    /** 다음 월로 이동 (현재 월 이후로는 이동 불가) */
    fun nextMonth() {
        val state = _uiState.value
        val currentYear = DateUtils.getCurrentYear()
        val currentMonth = DateUtils.getCurrentMonth()
        if (state.selectedYear >= currentYear && state.selectedMonth >= currentMonth) return

        var newYear = state.selectedYear
        var newMonth = state.selectedMonth + 1
        if (newMonth > 12) {
            newMonth = 1
            newYear += 1
        }
        lastInsightInputHash.set(0)
        _uiState.update { it.copy(selectedYear = newYear, selectedMonth = newMonth) }
        loadCurrentAndAdjacentPages()
    }

    /** 특정 년/월로 이동 (HorizontalPager에서 호출) */
    fun setMonth(year: Int, month: Int) {
        val state = _uiState.value
        if (state.selectedYear == year && state.selectedMonth == month) return
        lastInsightInputHash.set(0)
        _uiState.update { it.copy(selectedYear = year, selectedMonth = month) }
        loadCurrentAndAdjacentPages()
    }

    /** 화면이 다시 표시될 때 데이터 새로고침 (LaunchedEffect에서 호출) */
    fun refreshData() {
        // 캐시를 지우지 않고 재로드 → 기존 데이터 유지하면서 갱신 (깜빡임 방지)
        loadCurrentAndAdjacentPages()
        // resume 시 미분류 항목이 있고 분류가 진행 중이 아니면 자동 분류 시작
        tryResumeClassification()
    }

    /**
     * resume 시 미분류 항목 자동 분류 시도
     * 조건: (1) 분류 미진행 (2) Gemini API 키 존재 (3) 미분류 항목 존재
     */
    private fun tryResumeClassification() {
        if (classificationState.isRunning.value) return
        if (!isResumeClassificationChecking.compareAndSet(false, true)) return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val hasApiKey = geminiRepository.hasApiKey()
                if (!hasApiKey) return@launch

                val unclassifiedCount = categoryClassifierService.getUnclassifiedCount()
                if (unclassifiedCount == 0) return@launch
                if (classificationState.isRunning.value) return@launch

                android.util.Log.e("MT_DEBUG", "HomeViewModel[tryResumeClassification] : Resume 시 미분류 ${unclassifiedCount}건 발견 → 자동 분류 시작")
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
     * @param targetMonthRange 특정 월만 동기화 시 (startMillis, endMillis) 쌍. null이면 기존 동작.
     */
    fun syncSmsMessages(
        contentResolver: ContentResolver,
        forceFullSync: Boolean = false,
        todayOnly: Boolean = false,
        targetMonthRange: Pair<Long, Long>? = null
    ) {
        analyticsHelper.logClick(AnalyticsEvent.SCREEN_HOME, AnalyticsEvent.CLICK_SYNC_SMS)
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
                    val detectedCardNames: List<String> = emptyList(),
                    val classifiedCount: Int = 0
                )

                val result = withContext(Dispatchers.IO) {
                    // DB에서 사용자 제외 키워드 로드 → SmsParser에 설정
                    val userExcludeKeywords = smsExclusionRepository.getUserKeywords()
                    SmsParser.setUserExcludeKeywords(userExcludeKeywords)

                    // 전체 동기화 해제 여부 확인 (한 번이라도 광고 시청했으면 true)
                    val isFullSyncUnlocked = settingsDataStore.hasAnySyncedMonth()
                    val savedSyncTimeRaw = settingsDataStore.getLastSyncTime()
                    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.KOREA)

                    // Auto Backup 복원 감지: savedSyncTime이 있지만 DB가 비어있으면 stale → 리셋
                    // (existingSmsIds를 여기서 미리 로드 — 아래에서 중복 제거에도 재사용)
                    val existingSmsIdsPreload = expenseRepository.getAllSmsIds()
                    val dbExpenseCount = existingSmsIdsPreload.size
                    val effectiveSyncTime = if (savedSyncTimeRaw > 0 && dbExpenseCount == 0) {
                        android.util.Log.e("MT_DEBUG", "⚠️ Auto Backup 감지: savedSyncTime=${sdf.format(java.util.Date(savedSyncTimeRaw))} 이지만 DB 비어있음 → 0으로 리셋")
                        settingsDataStore.saveLastSyncTime(0L)
                        0L
                    } else {
                        savedSyncTimeRaw
                    }

                    android.util.Log.e("MT_DEBUG", "=== syncSmsMessages 분기 판단 ===")
                    android.util.Log.e("MT_DEBUG", "forceFullSync=$forceFullSync, todayOnly=$todayOnly")
                    android.util.Log.e("MT_DEBUG", "isFullSyncUnlocked=$isFullSyncUnlocked")
                    android.util.Log.e("MT_DEBUG", "savedSyncTimeRaw=$savedSyncTimeRaw (${if (savedSyncTimeRaw > 0) sdf.format(java.util.Date(savedSyncTimeRaw)) else "없음"})")
                    android.util.Log.e("MT_DEBUG", "effectiveSyncTime=$effectiveSyncTime (dbExpenseCount=$dbExpenseCount)")
                    android.util.Log.e("MT_DEBUG", "DEFAULT_SYNC_PERIOD_MILLIS=$DEFAULT_SYNC_PERIOD_MILLIS (${DEFAULT_SYNC_PERIOD_MILLIS / 1000 / 60 / 60 / 24}일)")
                    android.util.Log.e("MT_DEBUG", "System.currentTimeMillis()=${System.currentTimeMillis()}")
                    android.util.Log.e("MT_DEBUG", "기본 동기화 기준 시각=${sdf.format(java.util.Date(System.currentTimeMillis() - DEFAULT_SYNC_PERIOD_MILLIS))}")

                    // 마지막 동기화 시간 가져오기
                    val lastSyncTime = when {
                        forceFullSync -> {
                            if (isFullSyncUnlocked) {
                                // 특정 월 동기화 요청이면 해당 월만, 아니면 전체
                                val targetRange = targetMonthRange
                                if (targetRange != null) {
                                    android.util.Log.e("MT_DEBUG", "→ 분기: forceFullSync + unlocked + 월별 → ${sdf.format(java.util.Date(targetRange.first))}")
                                    targetRange.first
                                } else {
                                    android.util.Log.e("MT_DEBUG", "→ 분기: forceFullSync + unlocked → 0L (처음부터)")
                                    0L
                                }
                            } else {
                                val t = System.currentTimeMillis() - DEFAULT_SYNC_PERIOD_MILLIS
                                android.util.Log.e("MT_DEBUG", "→ 분기: forceFullSync + locked → 기본 기간 ($t = ${sdf.format(java.util.Date(t))})")
                                t
                            }
                        }
                        todayOnly -> {
                            // 오늘 자정 (00:00:00) 기준
                            val cal = java.util.Calendar.getInstance()
                            cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
                            cal.set(java.util.Calendar.MINUTE, 0)
                            cal.set(java.util.Calendar.SECOND, 0)
                            cal.set(java.util.Calendar.MILLISECOND, 0)
                            android.util.Log.e("MT_DEBUG", "→ 분기: todayOnly → ${cal.timeInMillis} (${sdf.format(cal.time)})")
                            cal.timeInMillis
                        }

                        else -> {
                            val defaultPeriodAgo = System.currentTimeMillis() - DEFAULT_SYNC_PERIOD_MILLIS
                            if (effectiveSyncTime == 0L && !isFullSyncUnlocked) {
                                android.util.Log.e("MT_DEBUG", "→ 분기: else + savedSync=0 + locked → 기본 기간 ($defaultPeriodAgo = ${sdf.format(java.util.Date(defaultPeriodAgo))})")
                                defaultPeriodAgo
                            } else if (!isFullSyncUnlocked && effectiveSyncTime < defaultPeriodAgo) {
                                android.util.Log.e("MT_DEBUG", "→ 분기: else + locked + saved<기본기간 → 기본 기간 ($defaultPeriodAgo = ${sdf.format(java.util.Date(defaultPeriodAgo))})")
                                defaultPeriodAgo
                            } else {
                                android.util.Log.e("MT_DEBUG", "→ 분기: else + 증분 → effectiveSyncTime ($effectiveSyncTime = ${if (effectiveSyncTime > 0) sdf.format(java.util.Date(effectiveSyncTime)) else "0"})")
                                effectiveSyncTime
                            }
                        }
                    }
                    // endTime: 월별 동기화 시 해당 월 끝까지, 아니면 현재 시간
                    val currentTime = targetMonthRange?.second ?: System.currentTimeMillis()

                    android.util.Log.e("MT_DEBUG", "=== syncSmsMessages (Hybrid) 시작 ===")
                    android.util.Log.e("MT_DEBUG", "최종 lastSyncTime=$lastSyncTime, currentTime=$currentTime, targetMonth=${targetMonthRange != null}")
                    android.util.Log.e(
                        "MT_DEBUG",
                        "최종 범위: ${sdf.format(java.util.Date(lastSyncTime))} ~ ${sdf.format(java.util.Date(currentTime))}"
                    )

                    // 진단: todayOnly일 때 모든 메시지 provider 탐색 (신한카드 등 RCS 메시지 위치 확인)
                    if (todayOnly) {
                        smsReader.diagnoseAllMessageProviders(contentResolver)
                    }

                    // ===== 성능 최적화: 인메모리 캐시 초기화 =====
                    _uiState.update { it.copy(syncProgress = "준비 중...") }
                    val existingSmsIds = existingSmsIdsPreload // 위에서 이미 로드됨
                    val existingIncomeSmsIds = incomeRepository.getAllSmsIds().toHashSet()
                    categoryClassifierService.initCategoryCache() // DB 쿼리 제거

                    // ===== 전체 SMS를 한 번만 읽고 지출/수입 동시 분류 =====
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

                    android.util.Log.e("MT_DEBUG", "=== SMS 읽기 결과 ===")
                    android.util.Log.e("MT_DEBUG", "allSmsList.size=${allSmsList.size}건")
                    android.util.Log.e("MT_DEBUG", "existingSmsIds.size=${existingSmsIds.size}건 (DB에 이미 존재)")
                    android.util.Log.e("MT_DEBUG", "existingIncomeSmsIds.size=${existingIncomeSmsIds.size}건")

                    _uiState.update {
                        it.copy(
                            syncProgress = "내역 분류 중...",
                            syncProgressCurrent = 0,
                            syncProgressTotal = allSmsList.size
                        )
                    }

                    var batchCount = 0
                    var incomeCount = 0
                    val expenseBatch = mutableListOf<ExpenseEntity>()
                    val incomeBatch =
                        mutableListOf<com.sanha.moneytalk.core.database.entity.IncomeEntity>()
                    val hasGeminiKey = geminiRepository.hasApiKey()

                    // === Phase 1: 수입 SMS 분리 + 중복 필터 (API 불필요) ===
                    val incomeCandidates = mutableListOf<SmsMessage>()
                    val paymentCandidates = mutableListOf<SmsMessage>()

                    for (sms in allSmsList) {
                        val smsType = SmsParser.classifySmsType(sms.body)

                        // 수입 처리 (중복 제외)
                        if (smsType.isIncome && sms.id !in existingIncomeSmsIds) {
                            incomeCandidates.add(sms)
                        }

                        // 결제 후보: DB에 없고, 순수 수입이 아닌 SMS → SmsBatchProcessor로
                        // (수입 SMS는 SmsBatchProcessor에서 불필요한 임베딩 생성 방지)
                        if (sms.id !in existingSmsIds && !smsType.isIncome) {
                            paymentCandidates.add(sms)
                        }
                    }

                    android.util.Log.e("MT_DEBUG", "Phase 1 완료: 수입 후보 ${incomeCandidates.size}건, 결제 후보 ${paymentCandidates.size}건")

                    // === Phase 2: 수입 처리 (SmsParser 정규식, API 불필요) ===
                    _uiState.update { it.copy(syncProgress = "수입 내역 처리 중...") }
                    for (sms in incomeCandidates) {
                        try {
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
                        } catch (e: Exception) {
                            android.util.Log.w("MT_DEBUG", "수입 SMS 처리 실패 (무시): ${sms.id} - ${e.message}")
                        }
                    }
                    if (incomeBatch.isNotEmpty()) {
                        incomeRepository.insertAll(incomeBatch)
                        incomeBatch.clear()
                    }
                    android.util.Log.e("MT_DEBUG", "Phase 2 완료: 수입 ${incomeCount}건 처리")

                    // === Phase 3: SmsBatchProcessor로 전체 결제 SMS 처리 ===
                    // (필터→임베딩→벡터매칭→그룹화→LLM→정규식 파이프라인)
                    val BATCH_CHUNK_SIZE = 500
                    val smsDataList = paymentCandidates.map { sms ->
                        SmsBatchProcessor.SmsData(
                            id = sms.id,
                            address = sms.address,
                            body = sms.body,
                            date = sms.date
                        )
                    }
                    val chunks = smsDataList.chunked(BATCH_CHUNK_SIZE)

                    android.util.Log.e("MT_DEBUG", "Phase 3 시작: ${paymentCandidates.size}건 → ${chunks.size}개 청크")

                    for ((chunkIdx, chunk) in chunks.withIndex()) {
                        _uiState.update {
                            it.copy(
                                syncProgress = "결제 내역 분석 중... (${chunkIdx * BATCH_CHUNK_SIZE}/${paymentCandidates.size}건)",
                                syncProgressCurrent = chunkIdx * BATCH_CHUNK_SIZE,
                                syncProgressTotal = paymentCandidates.size
                            )
                        }

                        try {
                            val batchResults = smsBatchProcessor.processBatch(
                                unclassifiedSms = chunk,
                                maxProcessCount = chunk.size
                            )

                            for ((smsData, analysis) in batchResults) {
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

                                if (expenseBatch.size >= DB_BATCH_INSERT_SIZE) {
                                    expenseRepository.insertAll(expenseBatch)
                                    expenseBatch.clear()
                                }
                            }
                            batchCount += batchResults.size
                        } catch (e: Exception) {
                            android.util.Log.e("MT_DEBUG", "배치 처리 실패 (청크 ${chunkIdx}): ${e.message}", e)
                        }
                    }

                    if (expenseBatch.isNotEmpty()) {
                        expenseRepository.insertAll(expenseBatch)
                        expenseBatch.clear()
                    }

                    _uiState.update {
                        it.copy(
                            syncProgress = "지출 ${batchCount}건, 수입 ${incomeCount}건 확인 완료",
                            syncProgressCurrent = paymentCandidates.size,
                            syncProgressTotal = paymentCandidates.size
                        )
                    }
                    android.util.Log.e("MT_DEBUG", "Phase 3 완료: 지출 ${batchCount}건 처리")

                    android.util.Log.e("MT_DEBUG", "=== 동기화 결과 요약 ===")
                    android.util.Log.e(
                        "MT_DEBUG",
                        "BatchProcessor: ${batchCount}건, 수입: ${incomeCount}건"
                    )

                    _uiState.update { it.copy(syncProgress = "정리 중...") }
                    categoryClassifierService.flushPendingMappings()
                    categoryClassifierService.clearCategoryCache()

                    // 월별 동기화(targetMonthRange != null)는 과거/미래 타임스탬프이므로 lastSyncTime 갱신 스킵
                    if (targetMonthRange == null) {
                        settingsDataStore.saveLastSyncTime(currentTime)
                    }
                    hybridSmsClassifier.cleanupStalePatterns()

                    val allCardNames = expenseRepository.getAllCardNamesWithDuplicates()

                    // === Phase 4: 카테고리 분류 (다이얼로그 유지한 채 인라인 처리) ===
                    var classifiedCount = 0
                    if (hasGeminiKey) {
                        val unclassifiedCount = categoryClassifierService.getUnclassifiedCount()
                        if (unclassifiedCount > 0) {
                            android.util.Log.e("MT_DEBUG", "HomeViewModel[syncSms] : 동기화 후 → 카테고리 분류 시작 (${unclassifiedCount}건)")
                            _uiState.update {
                                it.copy(
                                    syncProgress = "카테고리 분류 중...",
                                    syncProgressCurrent = 0,
                                    syncProgressTotal = unclassifiedCount
                                )
                            }

                            classifiedCount = categoryClassifierService.classifyUnclassifiedExpenses(
                                onStepProgress = { step, current, total ->
                                    _uiState.update {
                                        it.copy(
                                            syncProgress = "카테고리 분류 중...\n$step",
                                            syncProgressCurrent = current,
                                            syncProgressTotal = total
                                        )
                                    }
                                },
                                maxStoreCount = 50
                            )

                            android.util.Log.e("MT_DEBUG", "HomeViewModel[syncSms] : 카테고리 분류 완료 → ${classifiedCount}건")
                        } else {
                            android.util.Log.e("MT_DEBUG", "HomeViewModel[syncSms] : 미분류 0건 → 분류 스킵")
                        }
                    } else {
                        android.util.Log.e("MT_DEBUG", "HomeViewModel[syncSms] : 분류 스킵 (hasKey=${hasGeminiKey})")
                    }

                    SyncResult(
                        regexCount = batchCount,
                        incomeCount = incomeCount,
                        hasGeminiKey = hasGeminiKey,
                        detectedCardNames = allCardNames,
                        classifiedCount = classifiedCount
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

                // 데이터 새로고침 (캐시 클리어 후 재로드)
                clearAllPageCache()
                loadCurrentAndAdjacentPages()

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
            } catch (e: Exception) {
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

                clearAllPageCache()
                loadCurrentAndAdjacentPages()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isRefreshing = false, errorMessage = e.message)
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

    // ========== 전체 동기화 해제 (리워드 광고) ==========

    /** 전체 동기화 광고 다이얼로그 표시 */
    fun showFullSyncAdDialog() {
        _uiState.update { it.copy(showFullSyncAdDialog = true) }
    }

    /** 전체 동기화 광고 다이얼로그 닫기 */
    fun dismissFullSyncAdDialog() {
        _uiState.update { it.copy(showFullSyncAdDialog = false) }
    }

    /**
     * 월별 동기화 해제 (광고 시청 완료 후 호출)
     * 현재 보고 있는 달의 SMS만 가져오고, 해당 월을 syncedMonths에 기록.
     */
    fun unlockFullSync(contentResolver: ContentResolver) {
        viewModelScope.launch {
            val state = _uiState.value
            val yearMonth = String.format("%04d-%02d", state.selectedYear, state.selectedMonth)
            settingsDataStore.addSyncedMonth(yearMonth)
            _uiState.update { it.copy(showFullSyncAdDialog = false) }

            // 현재 보고 있는 월의 범위 계산
            val monthRange = calculateMonthRange(state.selectedYear, state.selectedMonth)
            val isCurrentMonth = state.selectedYear == DateUtils.getCurrentYear() &&
                    state.selectedMonth == DateUtils.getCurrentMonth()
            val monthLabel = if (isCurrentMonth) "이번달" else "${state.selectedMonth}월"
            snackbarBus.show("${monthLabel} 데이터를 가져옵니다.")

            syncSmsMessages(
                contentResolver,
                forceFullSync = true,
                targetMonthRange = monthRange
            )
        }
    }

    /** 해당 월이 이미 동기화(광고 시청) 되었는지 확인 */
    fun isMonthSynced(year: Int, month: Int): Boolean {
        val state = _uiState.value
        // 레거시: 기존 FULL_SYNC_UNLOCKED=true 사용자는 모든 월 해제로 처리
        if (state.isLegacyFullSyncUnlocked) return true
        val yearMonth = String.format("%04d-%02d", year, month)
        return yearMonth in state.syncedMonths
    }

    /**
     * 특정 년/월의 커스텀 월 기간 계산 (사용자 설정 monthStartDay 반영)
     * @return Pair(startMillis, endMillis)
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
        // 레거시 전역 해제 또는 월별 해제 시 완전 커버로 처리
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
    fun syncMonthData(contentResolver: ContentResolver, year: Int, month: Int) {
        val monthRange = calculateMonthRange(year, month)
        syncSmsMessages(
            contentResolver,
            forceFullSync = true,
            targetMonthRange = monthRange
        )
    }

    /** 전체 동기화 해제용 광고 준비 */
    fun preloadFullSyncAd() {
        rewardAdManager.preloadAd()
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
                    clearAllPageCache()
                    loadCurrentAndAdjacentPages()
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
     */
    private fun launchBackgroundHybridClassification(
        hybridSmsList: List<SmsMessage>,
        forceFullSync: Boolean
    ) {
        val job = viewModelScope.launch(Dispatchers.IO) {
            try {
                android.util.Log.e("MT_DEBUG", "HomeViewModel[Hybrid분류] : ===== 시작 (${hybridSmsList.size}건) =====")
                var hybridCount = 0
                val expenseBatch = mutableListOf<ExpenseEntity>()
                val existingSmsIds = expenseRepository.getAllSmsIds()

                if (forceFullSync || hybridSmsList.size > BATCH_PROCESSING_THRESHOLD) {
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

                if (expenseBatch.isNotEmpty()) {
                    expenseRepository.insertAll(expenseBatch)
                }

                android.util.Log.e("MT_DEBUG", "HomeViewModel[Hybrid분류] : 완료 → ${hybridCount}건 추가")

                if (hybridCount > 0) {
                    withContext(Dispatchers.Main) {
                        clearAllPageCache()
                        loadCurrentAndAdjacentPages()
                        _uiState.update {
                            it.copy(errorMessage = "${hybridCount}건의 추가 지출이 발견되었습니다")
                        }
                    }

                    android.util.Log.e("MT_DEBUG", "HomeViewModel[Hybrid분류] : → 카테고리 분류로 이어짐")
                    launchBackgroundCategoryClassificationInternal()
                }
            } catch (e: CancellationException) {
                android.util.Log.e("MT_DEBUG", "HomeViewModel[Hybrid분류] : 취소됨")
            } catch (e: Exception) {
                android.util.Log.e("MT_DEBUG", "HomeViewModel[Hybrid분류] : 실패: ${e.message}", e)
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
                android.util.Log.e("MT_DEBUG", "HomeViewModel[백그라운드분류] : 미분류 항목 없음 → 스킵")
                return
            }

            android.util.Log.e("MT_DEBUG", "HomeViewModel[백그라운드분류] : ===== Phase 1 시작 (미분류 ${count}건, 상위 50개) =====")

            val phase1Count = categoryClassifierService.classifyUnclassifiedExpenses(
                maxStoreCount = 50
            )

            android.util.Log.e("MT_DEBUG", "HomeViewModel[백그라운드분류] : Phase 1 완료 → ${phase1Count}건 분류")

            if (phase1Count > 0) {
                withContext(Dispatchers.Main) {
                    clearAllPageCache()
                    loadCurrentAndAdjacentPages()
                    _uiState.update { it.copy(errorMessage = "${phase1Count}건의 카테고리가 정리되었습니다") }
                }
            }

            val remainingCount = categoryClassifierService.getUnclassifiedCount()
            if (remainingCount > 0) {
                android.util.Log.e("MT_DEBUG", "HomeViewModel[백그라운드분류] : ===== Phase 2 시작 (남은 ${remainingCount}건, 최대 ${MAX_CLASSIFICATION_ROUNDS}라운드) =====")

                val phase2Classified = categoryClassifierService.classifyAllUntilComplete(
                    onProgress = { round, classifiedInRound, remaining ->
                        android.util.Log.e(
                            "MT_DEBUG",
                            "HomeViewModel[백그라운드분류] : 라운드 $round 완료 → ${classifiedInRound}건 분류 (남은: ${remaining}건)"
                        )
                    },
                    onStepProgress = null,
                    maxRounds = MAX_CLASSIFICATION_ROUNDS
                )

                android.util.Log.e("MT_DEBUG", "HomeViewModel[백그라운드분류] : Phase 2 완료 → ${phase2Classified}건 분류")

                if (phase2Classified > 0) {
                    val finalRemaining = categoryClassifierService.getUnclassifiedCount()
                    val message = if (finalRemaining > 0) {
                        "총 ${phase1Count + phase2Classified}건의 카테고리가 정리되었습니다"
                    } else {
                        "카테고리 정리가 완료되었습니다"
                    }
                    withContext(Dispatchers.Main) {
                        clearAllPageCache()
                        loadCurrentAndAdjacentPages()
                        _uiState.update { it.copy(errorMessage = message) }
                    }
                }
            }

            android.util.Log.e("MT_DEBUG", "HomeViewModel[백그라운드분류] : ===== 전체 완료 =====")
        } catch (e: CancellationException) {
            android.util.Log.e("MT_DEBUG", "HomeViewModel[백그라운드분류] : 취소됨")
        } catch (e: Exception) {
            android.util.Log.e("MT_DEBUG", "HomeViewModel[백그라운드분류] : 실패: ${e.message}", e)
        }
    }

    /**
     * 동기화 완료 후 미분류 항목 확인하고 분류 다이얼로그 표시
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

                clearAllPageCache()
                loadCurrentAndAdjacentPages()

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
                clearAllPageCache()
                loadCurrentAndAdjacentPages()
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
                clearAllPageCache()
                loadCurrentAndAdjacentPages()
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
                clearAllPageCache()
                loadCurrentAndAdjacentPages()
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "메모 저장 실패: ${e.message}") }
            }
        }
    }
}
