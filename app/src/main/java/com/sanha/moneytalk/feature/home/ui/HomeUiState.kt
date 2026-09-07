package com.sanha.moneytalk.feature.home.ui

import com.sanha.moneytalk.core.database.dao.CategorySum
import com.sanha.moneytalk.core.database.entity.ExpenseEntity
import com.sanha.moneytalk.core.database.entity.IncomeEntity
import com.sanha.moneytalk.core.ui.component.MonthKey
import com.sanha.moneytalk.core.util.DateUtils
import androidx.compose.runtime.Stable

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
