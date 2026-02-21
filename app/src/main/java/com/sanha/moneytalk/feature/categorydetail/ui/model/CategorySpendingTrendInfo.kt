package com.sanha.moneytalk.feature.categorydetail.ui.model

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.sanha.moneytalk.R
import com.sanha.moneytalk.core.ui.component.chart.CumulativeChartLine
import com.sanha.moneytalk.core.ui.component.chart.SpendingTrendInfo
import com.sanha.moneytalk.core.ui.component.chart.ToggleableLine
import com.sanha.moneytalk.core.util.CumulativeChartDataBuilder
import com.sanha.moneytalk.feature.categorydetail.ui.CategoryDetailPageData

/**
 * 카테고리 상세 화면의 누적 추이 데이터 Mapper.
 *
 * [CategoryDetailPageData]의 원시 데이터를 [SpendingTrendInfo]로 변환.
 * stringResource와 MaterialTheme 색상이 필요하므로 @Composable 팩토리에서 생성.
 */
@Immutable
data class CategorySpendingTrendInfo(
    override val title: String,
    override val primaryLine: CumulativeChartLine,
    override val toggleableLines: List<ToggleableLine>,
    override val daysInMonth: Int,
    override val todayDayIndex: Int
) : SpendingTrendInfo {

    companion object {
        /** Purple 600 — 6개월 평균 곡선 색상 */
        private val AVG_SIX_MONTH_COLOR = Color(0xFF8E24AA)

        /**
         * [CategoryDetailPageData]에서 [CategorySpendingTrendInfo] 생성.
         *
         * @param pageData 카테고리 상세 페이지 데이터
         * @param categoryName 카테고리 표시명 (예: "식비")
         * @return null이면 dailyCumulativeExpenses가 비어있어 차트 미표시
         */
        @Composable
        fun from(
            pageData: CategoryDetailPageData,
            categoryName: String
        ): CategorySpendingTrendInfo? {
            if (pageData.dailyCumulativeExpenses.isEmpty()) return null

            val primaryColor = MaterialTheme.colorScheme.primary
            val lastMonthColor = Color.Gray
            val avgThreeColor = MaterialTheme.colorScheme.tertiary
            val budgetColor = MaterialTheme.colorScheme.error

            val primaryLine = CumulativeChartLine(
                points = pageData.dailyCumulativeExpenses,
                color = primaryColor,
                isSolid = true,
                label = stringResource(R.string.home_trend_this_month)
            )

            val toggleableLines = buildList {
                if (pageData.lastMonthDailyCumulative.isNotEmpty()) {
                    add(
                        ToggleableLine(
                            line = CumulativeChartLine(
                                points = pageData.lastMonthDailyCumulative,
                                color = lastMonthColor,
                                isSolid = false,
                                label = stringResource(R.string.home_trend_last_month)
                            ),
                            initialChecked = true
                        )
                    )
                }
                if (pageData.avgThreeMonthDailyCumulative.isNotEmpty()) {
                    add(
                        ToggleableLine(
                            line = CumulativeChartLine(
                                points = pageData.avgThreeMonthDailyCumulative,
                                color = avgThreeColor,
                                isSolid = false,
                                label = stringResource(R.string.home_trend_avg_three_month)
                            ),
                            initialChecked = false
                        )
                    )
                }
                if (pageData.avgSixMonthDailyCumulative.isNotEmpty()) {
                    add(
                        ToggleableLine(
                            line = CumulativeChartLine(
                                points = pageData.avgSixMonthDailyCumulative,
                                color = AVG_SIX_MONTH_COLOR,
                                isSolid = false,
                                label = stringResource(R.string.home_trend_avg_six_month)
                            ),
                            initialChecked = false
                        )
                    )
                }
                val budget = pageData.categoryBudget
                if (budget != null && budget > 0) {
                    add(
                        ToggleableLine(
                            line = CumulativeChartLine(
                                points = CumulativeChartDataBuilder.buildBudgetCumulativePoints(
                                    budget, pageData.daysInMonth
                                ),
                                color = budgetColor,
                                isSolid = false,
                                label = stringResource(R.string.home_trend_budget)
                            ),
                            initialChecked = false
                        )
                    )
                }
            }

            return CategorySpendingTrendInfo(
                title = stringResource(R.string.category_detail_spending_trend, categoryName),
                primaryLine = primaryLine,
                toggleableLines = toggleableLines,
                daysInMonth = pageData.daysInMonth,
                todayDayIndex = pageData.todayDayIndex
            )
        }
    }
}
