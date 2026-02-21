package com.sanha.moneytalk.feature.categorydetail.ui

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Stable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sanha.moneytalk.R
import com.sanha.moneytalk.core.database.SmsExclusionRepository
import com.sanha.moneytalk.core.database.dao.BudgetDao
import com.sanha.moneytalk.core.database.entity.ExpenseEntity
import com.sanha.moneytalk.core.datastore.SettingsDataStore
import com.sanha.moneytalk.core.model.Category
import com.sanha.moneytalk.core.ui.component.MonthKey
import com.sanha.moneytalk.core.ui.component.MonthPagerUtils
import com.sanha.moneytalk.core.ui.component.transaction.card.ExpenseTransactionCardInfo
import com.sanha.moneytalk.core.util.CumulativeChartDataBuilder
import com.sanha.moneytalk.core.util.DataRefreshEvent
import com.sanha.moneytalk.core.util.DateUtils
import com.sanha.moneytalk.feature.home.data.CategoryClassifierService
import com.sanha.moneytalk.feature.home.data.ExpenseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Date
import javax.inject.Inject

/**
 * 카테고리 상세 화면 UI 상태
 */
@Stable
data class CategoryDetailUiState(
    val pageCache: Map<MonthKey, CategoryDetailPageData> = emptyMap(),
    val selectedYear: Int = DateUtils.getCurrentYear(),
    val selectedMonth: Int = DateUtils.getCurrentMonth(),
    val monthStartDay: Int = 1,
    val categoryDisplayName: String = "",
    val categoryEmoji: String = ""
)

/**
 * 카테고리 상세 화면 ViewModel
 *
 * 특정 카테고리의 월별 누적 지출 차트 + 거래 목록을 관리.
 * HomeViewModel의 페이지 캐시 패턴을 따르되, 카테고리 필터를 적용.
 *
 * - 차트 데이터: CumulativeChartDataBuilder로 빌딩 (카테고리 필터 적용)
 * - 거래 목록: Room Flow로 실시간 갱신
 * - 크로스 스크린 동기화: DataRefreshEvent 구독
 */
@HiltViewModel
class CategoryDetailViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val expenseRepository: ExpenseRepository,
    private val settingsDataStore: SettingsDataStore,
    private val dataRefreshEvent: DataRefreshEvent,
    private val smsExclusionRepository: SmsExclusionRepository,
    private val categoryClassifierService: CategoryClassifierService,
    private val budgetDao: BudgetDao,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    companion object {
        private const val TAG = "CategoryDetailVM"
        /** 페이지 캐시 최대 허용 범위 (현재 월 ± 이 값) */
        private const val PAGE_CACHE_RANGE = 2
    }

    // Intent extras (SavedStateHandle로 주입)
    private val categoryDisplayName: String =
        savedStateHandle[CategoryDetailActivity.EXTRA_CATEGORY] ?: ""
    private val initialYear: Int =
        savedStateHandle[CategoryDetailActivity.EXTRA_YEAR] ?: DateUtils.getCurrentYear()
    private val initialMonth: Int =
        savedStateHandle[CategoryDetailActivity.EXTRA_MONTH] ?: DateUtils.getCurrentMonth()

    // Category enum → displayNamesIncludingSub (소 카테고리 포함 필터)
    private val category: Category = Category.fromDisplayName(categoryDisplayName)
    private val categoryNames: List<String> = category.displayNamesIncludingSub

    private val _uiState = MutableStateFlow(
        CategoryDetailUiState(
            selectedYear = initialYear,
            selectedMonth = initialMonth,
            categoryDisplayName = category.displayName,
            categoryEmoji = category.emoji
        )
    )
    val uiState: StateFlow<CategoryDetailUiState> = _uiState.asStateFlow()

    /** 페이지별 로드 Job 관리 (월별 독립 취소) */
    private val pageLoadJobs = mutableMapOf<MonthKey, Job>()

    init {
        loadSettings()
        observeDataRefreshEvents()
    }

    // ========== 페이지 캐시 관리 ==========

    /** 특정 월의 페이지 캐시 업데이트 */
    private fun updatePageCache(key: MonthKey, data: CategoryDetailPageData) {
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

    /** 현재 + 인접 월 데이터 로드 */
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

    /** 전역 데이터 새로고침 이벤트 구독 */
    private fun observeDataRefreshEvents() {
        viewModelScope.launch {
            dataRefreshEvent.refreshEvent.collect { event ->
                when (event) {
                    DataRefreshEvent.RefreshType.ALL_DATA_DELETED -> {
                        clearAllPageCache()
                    }
                    DataRefreshEvent.RefreshType.CATEGORY_UPDATED,
                    DataRefreshEvent.RefreshType.TRANSACTION_ADDED,
                    DataRefreshEvent.RefreshType.OWNED_CARD_UPDATED -> {
                        clearAllPageCache()
                        loadCurrentAndAdjacentPages()
                    }
                    DataRefreshEvent.RefreshType.SMS_RECEIVED -> {
                        // SMS 동기화는 Home에서 처리 — 여기서는 무시
                    }
                }
            }
        }
    }

    /** 설정 로드 및 월 시작일 변경 감지 */
    private fun loadSettings() {
        viewModelScope.launch {
            settingsDataStore.monthStartDayFlow
                .distinctUntilChanged()
                .collect { monthStartDay ->
                    _uiState.update { it.copy(monthStartDay = monthStartDay) }
                    clearAllPageCache()
                    loadCurrentAndAdjacentPages()
                }
        }
    }

    // ========== 월 변경 ==========

    /** 페이지 스와이프 시 호출 */
    fun setMonth(year: Int, month: Int) {
        val state = _uiState.value
        if (state.selectedYear == year && state.selectedMonth == month) return

        _uiState.update { it.copy(selectedYear = year, selectedMonth = month) }
        loadCurrentAndAdjacentPages()
    }

    // ========== 페이지별 데이터 로드 ==========

    /**
     * 특정 월의 카테고리 상세 데이터 로드.
     *
     * 1) 1회성 데이터: 전월 누적, 3/6개월 평균, 예산, daysInMonth, todayDayIndex
     * 2) Flow 데이터: 이번 달 지출 → 실시간 누적 + 거래 목록 갱신
     */
    private fun loadPageData(year: Int, month: Int) {
        val key = MonthKey(year, month)
        val existing = _uiState.value.pageCache[key]
        if (existing != null && !existing.isLoading) return

        pageLoadJobs[key]?.cancel()
        pageLoadJobs[key] = viewModelScope.launch {
            if (_uiState.value.pageCache[key] == null) {
                updatePageCache(key, CategoryDetailPageData(isLoading = true))
            }

            try {
                val state = _uiState.value
                val (monthStart, monthEnd) = DateUtils.getCustomMonthPeriod(
                    year, month, state.monthStartDay
                )
                val periodLabel = DateUtils.formatCustomMonthPeriod(
                    year, month, state.monthStartDay
                )

                // 제외 키워드 로드
                val exclusionKeywords = withContext(Dispatchers.IO) {
                    smsExclusionRepository.getAllKeywordStrings()
                }

                val now = System.currentTimeMillis()
                val daysInMonth = ((monthEnd - monthStart) / (24L * 60 * 60 * 1000)).toInt() + 1
                val todayDayIndex = if (now in monthStart..monthEnd) {
                    ((now - monthStart) / (24L * 60 * 60 * 1000)).toInt() + 1
                } else -1

                // 전월 일별 누적 (카테고리 필터 적용)
                val prevYear = if (month == 1) year - 1 else year
                val prevMonth = if (month == 1) 12 else month - 1
                val (lastMonthFullStart, lastMonthFullEnd) = DateUtils.getCustomMonthPeriod(
                    prevYear, prevMonth, state.monthStartDay
                )
                val lastMonthDaysInMonth =
                    ((lastMonthFullEnd - lastMonthFullStart) / (24L * 60 * 60 * 1000)).toInt() + 1
                val fullLastMonthExpenses = withContext(Dispatchers.IO) {
                    expenseRepository.getExpensesByCategoriesAndDateRangeOnce(
                        categoryNames, lastMonthFullStart, lastMonthFullEnd
                    )
                }
                val filteredFullLastMonthExpenses = filterByExclusion(
                    fullLastMonthExpenses, exclusionKeywords
                )
                val lastMonthCumulative = CumulativeChartDataBuilder.buildDailyCumulative(
                    filteredFullLastMonthExpenses, lastMonthFullStart, lastMonthDaysInMonth
                )

                // 3개월 / 6개월 평균 (카테고리 필터 적용)
                val loadFilteredExpenses: suspend (Long, Long) -> List<ExpenseEntity> = { s, e ->
                    val raw = expenseRepository.getExpensesByCategoriesAndDateRangeOnce(
                        categoryNames, s, e
                    )
                    filterByExclusion(raw, exclusionKeywords)
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

                // 카테고리 예산 로드
                val yearMonth = String.format("%04d-%02d", year, month)
                val categoryBudget = withContext(Dispatchers.IO) {
                    budgetDao.getBudgetByCategory(categoryDisplayName, yearMonth)?.monthlyLimit
                }

                // 1회성 데이터 캐시 저장
                val existingData = _uiState.value.pageCache[key]
                updatePageCache(
                    key, CategoryDetailPageData(
                        isLoading = existingData == null,
                        periodLabel = periodLabel,
                        lastMonthDailyCumulative = lastMonthCumulative,
                        avgThreeMonthDailyCumulative = avgThreeMonthCumulative,
                        avgSixMonthDailyCumulative = avgSixMonthCumulative,
                        categoryBudget = categoryBudget,
                        daysInMonth = daysInMonth,
                        todayDayIndex = todayDayIndex
                    )
                )

                // Flow로 실시간 거래 목록 + 이번 달 누적 갱신
                expenseRepository.getExpensesFilteredByCategories(
                    null, categoryNames, monthStart, monthEnd
                )
                    .catch { e ->
                        Log.e(TAG, "Flow 수집 오류: ${e.message}")
                        updatePageCache(
                            key,
                            (_uiState.value.pageCache[key] ?: CategoryDetailPageData())
                                .copy(isLoading = false)
                        )
                    }
                    .collect { allExpenses ->
                        val expenses = filterByExclusion(allExpenses, exclusionKeywords)
                        val totalExpense = expenses.sumOf { it.amount }

                        // 이번 달 일별 누적 지출
                        val dailyCumulative = CumulativeChartDataBuilder.buildDailyCumulative(
                            expenses, monthStart, daysInMonth
                        )

                        // 날짜별 그룹핑 거래 목록
                        val transactionItems = buildTransactionItems(expenses)

                        val current =
                            _uiState.value.pageCache[key] ?: CategoryDetailPageData()
                        updatePageCache(
                            key, current.copy(
                                isLoading = false,
                                monthlyExpense = totalExpense,
                                dailyCumulativeExpenses = dailyCumulative,
                                transactionItems = transactionItems
                            )
                        )
                    }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.e(TAG, "loadPageData 오류: ${e.message}")
                updatePageCache(
                    key,
                    (_uiState.value.pageCache[key] ?: CategoryDetailPageData())
                        .copy(isLoading = false)
                )
            }
        }
    }

    // ========== 거래 목록 빌딩 ==========

    /** 지출 목록을 날짜별 그룹핑된 플랫 리스트로 변환 */
    private fun buildTransactionItems(
        expenses: List<ExpenseEntity>
    ): List<CategoryTransactionItem> {
        if (expenses.isEmpty()) return emptyList()

        val items = mutableListOf<CategoryTransactionItem>()
        val grouped = expenses.groupBy { it.dateTime.toDateKey() }
        val sortedDates = grouped.keys.sortedDescending()

        sortedDates.forEach { date ->
            val dayExpenses = grouped[date] ?: return@forEach
            val dailyTotal = dayExpenses.sumOf { it.amount }

            val calendar = Calendar.getInstance().apply { time = date }
            val dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH)
            val dayOfWeekStr = appContext.getString(
                getDayOfWeekResId(calendar.get(Calendar.DAY_OF_WEEK))
            )
            val title = appContext.getString(
                R.string.history_day_header, dayOfMonth, dayOfWeekStr
            )

            items.add(CategoryTransactionItem.Header(title = title, expenseTotal = dailyTotal))
            dayExpenses.sortedByDescending { it.dateTime }.forEach { expense ->
                items.add(CategoryTransactionItem.ExpenseItem(expense))
            }
        }

        return items
    }

    /** timestamp → 날짜 키 (시분초 제거) */
    private fun Long.toDateKey(): Date {
        return try {
            val calendar = Calendar.getInstance().apply {
                timeInMillis = this@toDateKey
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            calendar.time
        } catch (e: Exception) {
            Date()
        }
    }

    /** Calendar.DAY_OF_WEEK → R.string.day_* 리소스 ID */
    private fun getDayOfWeekResId(dayOfWeek: Int): Int {
        return when (dayOfWeek) {
            Calendar.SUNDAY -> R.string.day_sunday
            Calendar.MONDAY -> R.string.day_monday
            Calendar.TUESDAY -> R.string.day_tuesday
            Calendar.WEDNESDAY -> R.string.day_wednesday
            Calendar.THURSDAY -> R.string.day_thursday
            Calendar.FRIDAY -> R.string.day_friday
            Calendar.SATURDAY -> R.string.day_saturday
            else -> R.string.day_sunday
        }
    }

    // ========== 제외 키워드 필터 ==========

    /** 제외 키워드로 지출 목록 필터링 */
    private fun filterByExclusion(
        expenses: List<ExpenseEntity>,
        exclusionKeywords: Set<String>
    ): List<ExpenseEntity> {
        if (exclusionKeywords.isEmpty()) return expenses
        return expenses.filter { expense ->
            val smsLower = expense.originalSms.lowercase()
            exclusionKeywords.none { kw -> smsLower.contains(kw) }
        }
    }

    // ========== CRUD ==========

    /** 지출 삭제 */
    fun deleteExpense(expense: ExpenseEntity) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    expenseRepository.delete(expense)
                }
            } catch (e: Exception) {
                Log.e(TAG, "삭제 실패: ${e.message}")
            }
        }
    }

    /** 특정 지출의 카테고리 변경 (동일 가게명 전체 변경) */
    fun updateExpenseCategory(storeName: String, newCategory: String) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    categoryClassifierService.updateCategoryForAllSameStore(
                        storeName, newCategory
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "카테고리 변경 실패: ${e.message}")
            }
        }
    }

    /** 지출 메모 변경 */
    fun updateExpenseMemo(expenseId: Long, memo: String?) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    expenseRepository.updateMemo(expenseId, memo?.ifBlank { null })
                }
            } catch (e: Exception) {
                Log.e(TAG, "메모 변경 실패: ${e.message}")
            }
        }
    }
}
