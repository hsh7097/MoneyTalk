package com.sanha.moneytalk.feature.home.ui.model

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
import com.sanha.moneytalk.feature.home.ui.HomePageData
import java.text.NumberFormat
import java.time.YearMonth
import java.util.Locale
import kotlin.math.abs

/**
 * 홈 화면의 누적 추이 데이터 Mapper.
 *
 * [HomePageData]의 원시 데이터를 [SpendingTrendInfo]로 변환.
 * stringResource와 MaterialTheme 색상이 필요하므로 @Composable 팩토리에서 생성.
 */
@Immutable
data class HomeSpendingTrendInfo(
    override val title: String,
    override val primaryLine: CumulativeChartLine,
    override val toggleableLines: List<ToggleableLine>,
    override val daysInMonth: Int,
    override val todayDayIndex: Int,
    override val currentAmount: Long,
    override val lastMonthAmount: Long,
    override val comparisonText: String,
    override val isOverSpending: Boolean?
) : SpendingTrendInfo {

    companion object {
        /** Purple 600 — 6개월 평균 곡선 색상 */
        private val AVG_SIX_MONTH_COLOR = Color(0xFF8E24AA)

        /**
         * [HomePageData]에서 [HomeSpendingTrendInfo] 생성.
         *
         * @return null이면 currentMonthPoints가 비어있어 차트 미표시
         */
        @Composable
        fun from(
            pageData: HomePageData,
            year: Int,
            month: Int,
            isCurrentPeriodComplete: Boolean,
            isPreviousPeriodComplete: Boolean
        ): HomeSpendingTrendInfo? {
            if (pageData.dailyCumulativeExpenses.isEmpty()) return null

            val primaryColor = MaterialTheme.colorScheme.primary
            val lastMonthColor = Color.Gray
            val avgThreeColor = MaterialTheme.colorScheme.tertiary
            val budgetColor = MaterialTheme.colorScheme.error

            val primaryLine = CumulativeChartLine(
                points = pageData.dailyCumulativeExpenses,
                color = primaryColor,
                isSolid = true,
                label = stringResource(R.string.finance_comparison_month_label, month)
            )

            val previousMonth = YearMonth.of(year, month).minusMonths(1)
            val previousMonthLine = if (isPreviousPeriodComplete && pageData.lastMonthDailyCumulative.isNotEmpty()) {
                CumulativeChartLine(
                    points = pageData.lastMonthDailyCumulative,
                    color = lastMonthColor,
                    isSolid = false,
                    label = stringResource(
                        R.string.finance_comparison_month_label,
                        previousMonth.monthValue
                    )
                )
            } else null

            val toggleableLines = buildList {
                previousMonthLine?.let { line ->
                    add(ToggleableLine(line = line, initialChecked = true))
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
                val budget = pageData.monthlyBudget
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

            val comparison = HomeSpendingComparison.calculate(
                currentPoints = pageData.dailyCumulativeExpenses,
                previousPoints = pageData.lastMonthDailyCumulative,
                todayDayIndex = pageData.todayDayIndex,
                isCurrentPeriodComplete = isCurrentPeriodComplete,
                isPreviousPeriodComplete = isPreviousPeriodComplete
            )
            val difference = comparison.difference

            return HomeSpendingTrendInfo(
                title = stringResource(R.string.home_cumulative_spending),
                primaryLine = primaryLine,
                toggleableLines = toggleableLines,
                daysInMonth = pageData.daysInMonth,
                todayDayIndex = pageData.todayDayIndex,
                currentAmount = comparison.currentAmount,
                lastMonthAmount = comparison.previousAmount,
                comparisonText = buildComparisonText(comparison, pageData.todayDayIndex >= 0),
                isOverSpending = when {
                    difference == null || difference == 0L -> null
                    else -> difference > 0L
                }
            )
        }

        /** 실제 0원도 금액 차이로 비교하고, 수집 범위가 미완료면 평가 대신 안내한다. */
        @Composable
        private fun buildComparisonText(
            comparison: HomeSpendingComparison,
            isCurrentPeriod: Boolean
        ): String {
            comparison.unavailableReason?.let { reason ->
                return stringResource(
                    when (reason) {
                        HomeSpendingComparison.UnavailableReason.CURRENT_PERIOD_INCOMPLETE ->
                            R.string.finance_comparison_current_incomplete
                        HomeSpendingComparison.UnavailableReason.PREVIOUS_PERIOD_INCOMPLETE ->
                            R.string.finance_comparison_previous_incomplete
                        HomeSpendingComparison.UnavailableReason.MISSING_DATA ->
                            R.string.finance_comparison_missing_data
                    }
                )
            }
            val difference = comparison.difference ?: return stringResource(R.string.finance_comparison_missing_data)
            val message = when {
                isCurrentPeriod && difference > 0L -> R.string.finance_comparison_current_more
                isCurrentPeriod && difference < 0L -> R.string.finance_comparison_current_less
                isCurrentPeriod -> R.string.finance_comparison_current_same
                difference > 0L -> R.string.finance_comparison_past_more
                difference < 0L -> R.string.finance_comparison_past_less
                else -> R.string.finance_comparison_past_same
            }
            return if (difference == 0L) stringResource(message)
            else stringResource(message, NumberFormat.getNumberInstance(Locale.KOREA).format(abs(difference)))
        }
    }
}
