package com.sanha.moneytalk.feature.home.ui

import com.sanha.moneytalk.core.util.MoneyTalkLogger

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sanha.moneytalk.core.database.dao.CategorySum
import com.sanha.moneytalk.core.database.entity.ExpenseEntity
import com.sanha.moneytalk.core.database.entity.IncomeEntity
import com.sanha.moneytalk.core.datastore.SettingsDataStore
import com.sanha.moneytalk.core.model.Category
import com.sanha.moneytalk.core.ui.component.MonthKey
import com.sanha.moneytalk.core.ui.component.MonthPagerUtils
import com.sanha.moneytalk.core.sms2.DeletedSmsTracker
import com.sanha.moneytalk.core.util.CumulativeChartDataBuilder
import com.sanha.moneytalk.core.util.DataRefreshEvent
import com.sanha.moneytalk.core.util.DateUtils
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
    val aiInsight: String = "",
    /** 이번 달 일별 누적 지출 (index = dayOffset, value = 누적 금액) */
    val dailyCumulativeExpenses: List<Long> = emptyList(),
    /** 전월 일별 누적 지출 */
    val lastMonthDailyCumulative: List<Long> = emptyList(),
    /** 지난 3개월 평균 일별 누적 지출 */
    val avgThreeMonthDailyCumulative: List<Long> = emptyList(),
    /** 지난 6개월 평균 일별 누적 지출 */
    val avgSixMonthDailyCumulative: List<Long> = emptyList(),
    /** 월간 총 예산 (null = 미설정) */
    val monthlyBudget: Int? = null,
    /** 카테고리별 월 예산 (key=카테고리명, value=월예산) */
    val categoryBudgets: Map<String, Int> = emptyMap(),
    /** 해당 월의 총 일수 */
    val daysInMonth: Int = 30,
    /** 오늘이 해당 월의 몇번째 날인지 (0-based, -1이면 과거 월) */
    val todayDayIndex: Int = -1
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
 */
@Stable
data class HomeUiState(
    val pageCache: Map<MonthKey, HomePageData> = emptyMap(),
    val isRefreshing: Boolean = false,
    val selectedYear: Int = DateUtils.getCurrentYear(),
    val selectedMonth: Int = DateUtils.getCurrentMonth(),
    val monthStartDay: Int = 1,
    val errorMessage: String? = null,
    // 카테고리 필터 (null이면 전체 표시)
    val selectedCategory: String? = null,
    // 카테고리 분류 관련
    val showClassifyDialog: Boolean = false,
    val unclassifiedCount: Int = 0,
    val isClassifying: Boolean = false,
    val classifyProgress: String = "",
    val classifyProgressCurrent: Int = 0,
    val classifyProgressTotal: Int = 0
)

/**
 * 홈 화면 ViewModel
 *
 * 홈 화면의 월간 지출 현황, 카테고리별 지출, 최근 지출 내역을 관리합니다.
 * SMS 동기화는 Activity-scoped MainViewModel에서 처리합니다.
 *
 * 주요 기능:
 * - 월별 수입/지출/잔여 예산 표시 (페이지별 독립 캐시)
 * - 카테고리별 지출 합계 표시
 * - AI 인사이트 생성
 * - Pull-to-Refresh 지원
 * - 카테고리 수동 변경
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val expenseRepository: ExpenseRepository,
    private val incomeRepository: IncomeRepository,
    private val categoryClassifierService: CategoryClassifierService,
    private val settingsDataStore: SettingsDataStore,
    private val dataRefreshEvent: DataRefreshEvent,
    private val smsExclusionRepository: com.sanha.moneytalk.core.database.SmsExclusionRepository,
    private val geminiRepository: GeminiRepository,
    private val budgetDao: com.sanha.moneytalk.core.database.dao.BudgetDao
) : ViewModel() {

    companion object {

        /** 기본 조회 기간 (1년, 밀리초) */
        private const val ONE_YEAR_MILLIS = 365L * 24 * 60 * 60 * 1000

        /** 카테고리 분류 최대 반복 횟수 */
        private const val MAX_CLASSIFICATION_ROUNDS = 3

        /** 페이지 캐시 최대 허용 범위 (현재 월 ± 이 값) */
        private const val PAGE_CACHE_RANGE = 2
    }

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    /** 페이지별 로드 Job 관리 (월별 독립 취소) */
    private val pageLoadJobs = mutableMapOf<MonthKey, Job>()

    /** 마지막 AI 인사이트 생성 시 사용된 입력 데이터 해시 (동일 데이터 재생성 방지) */
    private val lastInsightInputHash = AtomicInteger(0)

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

    /** 현재 월 ± PAGE_CACHE_RANGE 밖의 캐시 + Job 정리 */
    private fun evictDistantCache(year: Int, month: Int) {
        val currentTotal = year * 12 + month
        // 범위 밖 Job 취소
        val evictKeys = pageLoadJobs.keys.filter { key ->
            kotlin.math.abs(key.year * 12 + key.month - currentTotal) > PAGE_CACHE_RANGE
        }
        evictKeys.forEach { key ->
            pageLoadJobs.remove(key)?.cancel()
        }
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

    /**
     * 현재 + 인접 페이지 데이터 새로고침 (캐시를 비우지 않고 덮어쓰기).
     * 백그라운드 데이터 변경 시 스크롤 위치를 유지하면서 데이터만 갱신.
     */
    private fun refreshCurrentPages() {
        val state = _uiState.value
        val year = state.selectedYear
        val month = state.selectedMonth

        loadPageData(year, month, forceReload = true)
        val (prevY, prevM) = MonthPagerUtils.adjacentMonth(year, month, -1)
        loadPageData(prevY, prevM, forceReload = true)
        val (nextY, nextM) = MonthPagerUtils.adjacentMonth(year, month, +1)
        if (!MonthPagerUtils.isFutureYearMonth(nextY, nextM, state.monthStartDay)) {
            loadPageData(nextY, nextM, forceReload = true)
        }
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
        if (!MonthPagerUtils.isFutureYearMonth(nextY, nextM, state.monthStartDay)) {
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
                        clearAllPageCache()
                        loadSettings()
                    }

                    DataRefreshEvent.RefreshType.CATEGORY_UPDATED,
                    DataRefreshEvent.RefreshType.OWNED_CARD_UPDATED -> {
                        refreshCurrentPages()
                    }

                    DataRefreshEvent.RefreshType.TRANSACTION_ADDED -> {
                        refreshCurrentPages()
                    }

                    else -> { /* SMS_RECEIVED 등은 MainViewModel에서 처리 */ }
                }
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
                        // 최초: 커스텀 시작일 기준 실효 월로 selectedYear/selectedMonth 설정
                        val (year, month) = DateUtils.getEffectiveCurrentMonth(monthStartDay)
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
                        // 월 시작일 변경 시 기존 syncedMonths 무효화
                        // (기존 "2025-12" 레코드는 이전 monthStartDay 기준이므로 stale)
                        // 레거시 FULL_SYNC_UNLOCKED는 보존 (monthStartDay와 무관한 전역 플래그)
                        settingsDataStore.resetSyncedMonths()
                    }
                    clearAllPageCache()
                    loadCurrentAndAdjacentPages()
                }
        }
    }

    // ========== 페이지별 데이터 로드 ==========

    /**
     * 특정 월의 페이지 데이터 로드
     * 해당 월의 수입, 지출, 카테고리별 합계, 오늘 내역을 조회하여 pageCache에 저장.
     * @param year 대상 연도
     * @param month 대상 월
     */
    private fun loadPageData(
        year: Int,
        month: Int,
        forceReload: Boolean = false
    ) {
        val key = MonthKey(year, month)
        if (!forceReload) {
            // 이미 로드 완료된 캐시가 있으면 스킵 (스와이프 시 불필요한 재로드 방지)
            val existing = _uiState.value.pageCache[key]
            if (existing != null && !existing.isLoading) return
        }

        pageLoadJobs[key]?.cancel()
        pageLoadJobs[key] = viewModelScope.launch {
            // forceReload가 아니고 캐시에 없으면 로딩 상태로 초기화
            if (!forceReload && _uiState.value.pageCache[key] == null) {
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

                // ── 누적 지출 차트 데이터 (1회성) ──
                val daysInMonth = ((monthEnd - monthStart) / (24L * 60 * 60 * 1000)).toInt() + 1
                // +1: points[0]=0원 시작점이므로 1일=index1, 2일=index2, ...
                val todayDayIndex = if (now in monthStart..monthEnd) {
                    ((now - monthStart) / (24L * 60 * 60 * 1000)).toInt() + 1
                } else -1

                // 전월 일별 누적 (filteredLastMonthExpenses 재사용)
                val (lastMonthFullStart, lastMonthFullEnd) = DateUtils.getCustomMonthPeriod(
                    prevYear, prevMonth, state.monthStartDay
                )
                val lastMonthDaysInMonth = ((lastMonthFullEnd - lastMonthFullStart) / (24L * 60 * 60 * 1000)).toInt() + 1
                val fullLastMonthExpenses = withContext(Dispatchers.IO) {
                    expenseRepository.getExpensesByDateRangeOnce(lastMonthFullStart, lastMonthFullEnd)
                }
                val filteredFullLastMonthExpenses = if (exclusionKeywords.isEmpty()) {
                    fullLastMonthExpenses
                } else {
                    fullLastMonthExpenses.filter { expense ->
                        val smsLower = expense.originalSms.lowercase()
                        exclusionKeywords.none { kw -> smsLower.contains(kw) }
                    }
                }
                val lastMonthCumulative = CumulativeChartDataBuilder.buildDailyCumulative(
                    filteredFullLastMonthExpenses, lastMonthFullStart, lastMonthDaysInMonth
                )

                // 지난 3개월 / 6개월 평균 (IO에서 1회 로드)
                // exclusionKeywords 필터링을 포함한 데이터 로드 람다
                val loadFilteredExpenses: suspend (Long, Long) -> List<ExpenseEntity> = { s, e ->
                    val raw = expenseRepository.getExpensesByDateRangeOnce(s, e)
                    if (exclusionKeywords.isEmpty()) raw
                    else raw.filter { ex ->
                        exclusionKeywords.none { kw -> ex.originalSms.lowercase().contains(kw) }
                    }
                }
                val avgThreeMonthCumulative = withContext(Dispatchers.IO) {
                    CumulativeChartDataBuilder.buildAvgNMonthCumulative(
                        3, year, month, state.monthStartDay, daysInMonth, loadFilteredExpenses
                    )
                }
                val avgSixMonthCumulative = withContext(Dispatchers.IO) {
                    CumulativeChartDataBuilder.buildAvgNMonthCumulative(
                        6, year, month, state.monthStartDay, daysInMonth, loadFilteredExpenses
                    )
                }

                // 예산 로드 (전체 + 카테고리별 예산을 일괄 조회, "default"로 모든 월 공통)
                val allBudgets = withContext(Dispatchers.IO) {
                    budgetDao.migrateToDefault()
                    budgetDao.getBudgetsByMonthOnce("default")
                }
                val monthlyBudgetValue = allBudgets.find { it.category == "전체" }?.monthlyLimit
                val categoryBudgetsMap = allBudgets
                    .filter { it.category != "전체" }
                    .associate { it.category to it.monthlyLimit }

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
                    comparisonPeriodLabel = comparisonLabel,
                    lastMonthDailyCumulative = lastMonthCumulative,
                    avgThreeMonthDailyCumulative = avgThreeMonthCumulative,
                    avgSixMonthDailyCumulative = avgSixMonthCumulative,
                    monthlyBudget = monthlyBudgetValue,
                    categoryBudgets = categoryBudgetsMap,
                    daysInMonth = daysInMonth,
                    todayDayIndex = todayDayIndex
                ))

                // 지출 내역은 Flow로 실시간 감지 (Room DB 변경 시 자동 업데이트)
                var insightLoaded = false

                expenseRepository.getExpensesByDateRange(monthStart, monthEnd)
                    .catch { _ ->
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
                                // 커스텀 카테고리는 원래 이름 유지 (기타로 합치지 않음)
                                if (cat == Category.ETC && expense.category != Category.ETC.displayName) {
                                    expense.category
                                } else {
                                    cat.parentCategory?.displayName ?: cat.displayName
                                }
                            }
                            .map { (category, items) ->
                                CategorySum(category = category, total = items.sumOf { it.amount })
                            }
                            .sortedByDescending { it.total }

                        // 이번 달 일별 누적 지출 계산
                        val dailyCumulative = CumulativeChartDataBuilder.buildDailyCumulative(expenses, monthStart, daysInMonth)

                        // 현재 캐시의 1회성 데이터를 유지하면서 지출 데이터 업데이트
                        val current = _uiState.value.pageCache[key] ?: HomePageData()
                        updatePageCache(key, current.copy(
                            isLoading = false,
                            monthlyExpense = totalExpense,
                            categoryExpenses = categories,
                            recentExpenses = expenses.sortedByDescending { e -> e.dateTime },
                            dailyCumulativeExpenses = dailyCumulative
                        ))

                        // AI 인사이트 생성 (데이터가 있는 모든 월에서 첫 emit 시)
                        if (!insightLoaded && totalExpense > 0) {
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
                    // 해당 월의 캐시가 아직 존재하면 인사이트 저장
                    val current = _uiState.value.pageCache[monthKey] ?: return@launch
                    updatePageCache(monthKey, current.copy(aiInsight = insight))
                }
            } catch (e: Exception) {
                MoneyTalkLogger.w("AI 인사이트 생성 실패 (무시): ${e.message}")
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

    /** 다음 월로 이동 (현재 실효 월 이후로는 이동 불가) */
    fun nextMonth() {
        val state = _uiState.value
        val (effectiveYear, effectiveMonth) = DateUtils.getEffectiveCurrentMonth(state.monthStartDay)
        if (state.selectedYear >= effectiveYear && state.selectedMonth >= effectiveMonth) return

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

                refreshCurrentPages()
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
                MoneyTalkLogger.e("분류 실패: ${e.message}")
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

                // 수입 분류
                val incomeClassified = withContext(Dispatchers.IO) {
                    val incomeCount = categoryClassifierService.getUnclassifiedIncomeCount()
                    if (incomeCount > 0) {
                        _uiState.update {
                            it.copy(classifyProgress = "수입 항목 분류 중...")
                        }
                        categoryClassifierService.classifyUnclassifiedIncomes()
                    } else 0
                }

                clearAllPageCache()
                loadCurrentAndAdjacentPages()

                val totalAll = totalClassified + incomeClassified
                val finalRemaining = withContext(Dispatchers.IO) {
                    categoryClassifierService.getUnclassifiedCount()
                }
                val resultMessage = if (finalRemaining > 0) {
                    "${totalAll}건 정리 완료. ${finalRemaining}건은 직접 확인이 필요합니다."
                } else {
                    "${totalAll}건 정리 완료!"
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
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                MoneyTalkLogger.e("전체 분류 실패: ${e.message}")
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

    // buildDailyCumulative, buildAvgNMonthCumulative → CumulativeChartDataBuilder로 이동

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
                MoneyTalkLogger.e("카테고리 변경 실패: ${e.message}")
            }
        }
    }

    fun deleteExpense(expense: ExpenseEntity) {
        viewModelScope.launch {
            try {
                DeletedSmsTracker.markDeleted(expense.smsId)
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

    fun deleteIncome(income: IncomeEntity) {
        viewModelScope.launch {
            try {
                income.smsId?.let { DeletedSmsTracker.markDeleted(it) }
                withContext(Dispatchers.IO) { incomeRepository.delete(income) }
                _uiState.update { it.copy(errorMessage = "수입이 삭제되었습니다") }
                clearAllPageCache()
                loadCurrentAndAdjacentPages()
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "수입 삭제 실패: ${e.message}") }
            }
        }
    }

    fun updateIncomeMemo(incomeId: Long, memo: String?) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    incomeRepository.updateMemo(incomeId, memo?.ifBlank { null })
                }
                clearAllPageCache()
                loadCurrentAndAdjacentPages()
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "메모 저장 실패: ${e.message}") }
            }
        }
    }

    // ===== 화면별 온보딩 =====

    /** 특정 화면의 온보딩 완료 여부 Flow */
    fun hasSeenScreenOnboardingFlow(screenId: String) =
        settingsDataStore.hasSeenScreenOnboardingFlow(screenId)

    /** 특정 화면의 온보딩 완료 처리 */
    fun markScreenOnboardingSeen(screenId: String) {
        viewModelScope.launch {
            settingsDataStore.setScreenOnboardingSeen(screenId)
        }
    }
}
