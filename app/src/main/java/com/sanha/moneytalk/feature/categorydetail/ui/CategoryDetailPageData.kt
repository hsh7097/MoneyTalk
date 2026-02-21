package com.sanha.moneytalk.feature.categorydetail.ui

import androidx.compose.runtime.Stable
import com.sanha.moneytalk.core.database.entity.ExpenseEntity
import com.sanha.moneytalk.core.ui.component.transaction.card.ExpenseTransactionCardInfo
import com.sanha.moneytalk.core.ui.component.transaction.card.TransactionCardInfo
import com.sanha.moneytalk.core.ui.component.transaction.header.TransactionGroupHeaderInfo

/**
 * 카테고리 상세 화면의 거래 목록 아이템.
 *
 * LazyColumn에 바로 렌더링할 수 있는 플랫 리스트 아이템.
 * History의 TransactionListItem과 동일한 구조이나, 카테고리 상세에서는
 * 지출만 표시하므로 수입 아이템은 불필요.
 */
sealed interface CategoryTransactionItem {
    /** 날짜 그룹 헤더 */
    data class Header(
        override val title: String,
        override val expenseTotal: Int = 0
    ) : CategoryTransactionItem, TransactionGroupHeaderInfo

    /** 지출 아이템 */
    data class ExpenseItem(
        val expense: ExpenseEntity,
        val cardInfo: TransactionCardInfo = ExpenseTransactionCardInfo(expense)
    ) : CategoryTransactionItem
}

/**
 * 카테고리 상세 화면의 페이지별(월별) 데이터.
 *
 * HorizontalPager의 각 페이지가 독립적으로 렌더링할 수 있도록
 * 해당 카테고리의 월별 데이터를 캡슐화.
 */
@Stable
data class CategoryDetailPageData(
    val isLoading: Boolean = true,
    /** 해당 카테고리의 월 총 지출 */
    val monthlyExpense: Int = 0,
    /** 이번 달 일별 누적 지출 (카테고리 필터 적용) */
    val dailyCumulativeExpenses: List<Long> = emptyList(),
    /** 전월 일별 누적 */
    val lastMonthDailyCumulative: List<Long> = emptyList(),
    /** 지난 3개월 평균 일별 누적 */
    val avgThreeMonthDailyCumulative: List<Long> = emptyList(),
    /** 지난 6개월 평균 일별 누적 */
    val avgSixMonthDailyCumulative: List<Long> = emptyList(),
    /** 카테고리 예산 (null = 미설정) */
    val categoryBudget: Int? = null,
    /** 해당 월의 총 일수 */
    val daysInMonth: Int = 30,
    /** 오늘이 해당 월의 몇번째 날인지 (1-based with 0-point, -1이면 과거 월) */
    val todayDayIndex: Int = -1,
    /** 날짜별 그룹핑된 거래 목록 */
    val transactionItems: List<CategoryTransactionItem> = emptyList(),
    /** 기간 레이블 (예: "2/1 ~ 2/28") */
    val periodLabel: String = ""
)
