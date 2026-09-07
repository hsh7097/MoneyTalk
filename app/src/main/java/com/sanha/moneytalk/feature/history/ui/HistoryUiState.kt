package com.sanha.moneytalk.feature.history.ui

import androidx.compose.runtime.Stable
import com.sanha.moneytalk.core.database.entity.ExpenseEntity
import com.sanha.moneytalk.core.database.entity.IncomeEntity
import com.sanha.moneytalk.core.database.entity.isIncludedInExpenseStats
import com.sanha.moneytalk.core.database.entity.isIncludedInTransferIncomeStats
import com.sanha.moneytalk.core.model.Category
import com.sanha.moneytalk.core.model.CategoryInfo
import com.sanha.moneytalk.core.ui.component.MonthKey
import com.sanha.moneytalk.core.util.DateUtils

/**
 * 정렬 방식
 */
enum class SortOrder {
    DATE_DESC,      // 최신순 (기본값)
    AMOUNT_DESC,    // 금액 높은순
    STORE_FREQ      // 사용처별 (많이 사용한 곳 순)
}

/**
 * 고정 거래 필터
 */
enum class FixedExpenseFilter {
    ALL,            // 포함 (기본값)
    FIXED_ONLY,     // 고정 거래만 표시
    EXCLUDE_FIXED   // 고정 거래 제외
}

internal fun List<ExpenseEntity>.filterExpensesByFixed(
    fixedFilter: FixedExpenseFilter
): List<ExpenseEntity> = when (fixedFilter) {
    FixedExpenseFilter.ALL -> this
    FixedExpenseFilter.FIXED_ONLY -> filter { it.isFixed }
    FixedExpenseFilter.EXCLUDE_FIXED -> filter { !it.isFixed }
}

internal fun List<IncomeEntity>.filterIncomesByFixed(
    fixedFilter: FixedExpenseFilter
): List<IncomeEntity> = when (fixedFilter) {
    FixedExpenseFilter.ALL -> this
    FixedExpenseFilter.FIXED_ONLY -> filter { it.isRecurring }
    FixedExpenseFilter.EXCLUDE_FIXED -> filter { !it.isRecurring }
}

/**
 * 내역 화면의 페이지별(월별) 데이터.
 * HorizontalPager의 각 페이지가 독립적으로 렌더링할 수 있도록 월별 데이터를 캡슐화.
 */
@Stable
data class HistoryPageData(
    val isLoading: Boolean = true,
    val expenses: List<ExpenseEntity> = emptyList(),
    val incomes: List<IncomeEntity> = emptyList(),
    val monthlyTotal: Int = 0,
    val dailyTotals: Map<String, Int> = emptyMap(),
    val dailyIncomeTotals: Map<String, Int> = emptyMap(),
    val monthlyIncomeTotal: Int = 0,
    val transactionListItems: List<TransactionListItem> = emptyList()
)

/**
 * 내역 화면 UI 상태
 *
 * 월별 데이터는 [pageCache]에서 관리하며, 글로벌 상태만 직접 보유.
 * HorizontalPager의 각 페이지는 pageCache[MonthKey]에서 자기 월의 데이터를 읽어 렌더링.
 *
 * @property pageCache 월별 페이지 데이터 캐시 (최대 3~5개)
 * @property selectedCategory 선택된 카테고리 필터 (null이면 전체)
 * @property selectedYear 선택된 연도
 * @property selectedMonth 선택된 월
 * @property monthStartDay 월 시작일 (1~28, 사용자 설정)
 * @property errorMessage 에러 메시지 (null이면 에러 없음)
 * @property searchQuery 검색어
 * @property isSearchMode 검색 모드 여부
 * @property sortOrder 정렬 순서
 * @property showExpenses 지출 표시 여부 (BottomSheet 필터)
 * @property showIncomes 수입 표시 여부 (BottomSheet 필터)
 */
@Stable
data class HistoryUiState(
    val pageCache: Map<MonthKey, HistoryPageData> = emptyMap(),
    val isRefreshing: Boolean = false,
    val selectedExpenseCategories: Set<String> = emptySet(),
    val selectedIncomeCategories: Set<String> = emptySet(),
    val selectedTransferCategories: Set<String> = emptySet(),
    val selectedCardNames: Set<String> = emptySet(),
    val availableCardNames: List<String> = emptyList(),
    val selectedCategory: String? = null,
    val selectedYear: Int = DateUtils.getCurrentYear(),
    val selectedMonth: Int = DateUtils.getCurrentMonth(),
    val monthStartDay: Int = 1,
    val errorMessage: String? = null,
    val searchQuery: String = "",
    val isSearchMode: Boolean = false,
    val sortOrder: SortOrder = SortOrder.DATE_DESC,
    val showExpenses: Boolean = true,
    val showIncomes: Boolean = true,
    val showTransfers: Boolean = true,
    val fixedExpenseFilter: FixedExpenseFilter = FixedExpenseFilter.ALL,
    val expenseCategories: List<CategoryInfo> = Category.expenseEntries,
    val incomeCategories: List<CategoryInfo> = Category.incomeEntries,
    val transferCategories: List<CategoryInfo> = Category.transferEntries,
    // 다이얼로그 상태 (Composable에서 remember 대신 ViewModel에서 관리)
    val selectedExpense: ExpenseEntity? = null,
    val selectedIncome: IncomeEntity? = null
) {
    /** 현재 선택 월의 페이지 데이터 (하위 호환용) */
    private val currentPageData: HistoryPageData
        get() = pageCache[MonthKey(selectedYear, selectedMonth)] ?: HistoryPageData()

    /** 필터 적용된 지출 총합 (고정 거래 필터 반영) */
    val filteredExpenseTotal: Int
        get() {
            return currentPageData.expenses.filterExpensesByFixed(fixedExpenseFilter).filter { expense ->
                if (expense.transactionType == "TRANSFER") {
                    showTransfers && expense.isIncludedInExpenseStats()
                } else {
                    showExpenses && expense.isIncludedInExpenseStats()
                }
            }.sumOf { it.amount }
        }

    /** 필터 적용된 수입 총합 (수입 + 이체 입금) */
    val filteredIncomeTotal: Int
        get() {
            val fixedFilteredExpenses = currentPageData.expenses.filterExpensesByFixed(fixedExpenseFilter)
            val incomeTotal = if (showIncomes) {
                currentPageData.incomes.filterIncomesByFixed(fixedExpenseFilter).sumOf { it.amount }
            } else {
                0
            }
            val transferDepositTotal = if (showTransfers) {
                fixedFilteredExpenses.filter {
                    it.transactionType == "TRANSFER" &&
                            it.isIncludedInTransferIncomeStats()
                }.sumOf { it.amount }
            } else {
                0
            }
            return incomeTotal + transferDepositTotal
        }

    /** 카테고리 필터 활성 여부 */
    val hasCategoryFilter: Boolean
        get() = selectedCategory != null ||
                selectedExpenseCategories.isNotEmpty() ||
                selectedIncomeCategories.isNotEmpty() ||
                selectedTransferCategories.isNotEmpty()

    val hasCardFilter: Boolean
        get() = selectedCardNames.isNotEmpty()

    val hasActiveFilter: Boolean
        get() = hasCategoryFilter ||
                hasCardFilter ||
                sortOrder != SortOrder.DATE_DESC ||
                !showExpenses ||
                !showIncomes ||
                !showTransfers ||
                fixedExpenseFilter != FixedExpenseFilter.ALL
}
