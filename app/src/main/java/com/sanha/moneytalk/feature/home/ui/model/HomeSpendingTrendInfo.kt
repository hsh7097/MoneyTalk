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
import kotlin.math.abs
import kotlin.math.roundToInt

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
        fun from(pageData: HomePageData): HomeSpendingTrendInfo? {
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

            // 이번 달 현재 누적 금액
            val currentAmt = pageData.dailyCumulativeExpenses.lastOrNull() ?: 0L

            // 전월 동일 기간 누적 금액
            val todayIdx = pageData.todayDayIndex
            val lastMonthAmt = if (todayIdx >= 0 && pageData.lastMonthDailyCumulative.isNotEmpty()) {
                val clampedIdx = todayIdx.coerceAtMost(pageData.lastMonthDailyCumulative.size - 1)
                pageData.lastMonthDailyCumulative[clampedIdx]
            } else {
                0L
            }

            // 비교 문구 생성
            val (compText, overSpending) = buildComparisonText(
                currentAmt, lastMonthAmt,
                hasLastMonthData = pageData.lastMonthDailyCumulative.isNotEmpty()
            )

            return HomeSpendingTrendInfo(
                title = stringResource(R.string.home_cumulative_spending),
                primaryLine = primaryLine,
                toggleableLines = toggleableLines,
                daysInMonth = pageData.daysInMonth,
                todayDayIndex = pageData.todayDayIndex,
                currentAmount = currentAmt,
                lastMonthAmount = lastMonthAmt,
                comparisonText = compText,
                isOverSpending = overSpending
            )
        }

        /**
         * 비교 텍스트와 초과 여부를 계산한다.
         *
         * @return Pair(비교 문구, 초과 여부: true=초과, false=절약, null=비교 불가)
         */
        @Composable
        private fun buildComparisonText(
            current: Long,
            lastMonth: Long,
            hasLastMonthData: Boolean
        ): Pair<String, Boolean?> {
            if (!hasLastMonthData || lastMonth <= 0L) {
                return Pair(stringResource(R.string.home_trend_comparison_no_data), null)
            }

            val diff = current - lastMonth
            val percentage = ((abs(diff).toDouble() / lastMonth) * 100).roundToInt()

            return when {
                percentage < 3 -> Pair(
                    stringResource(R.string.home_trend_comparison_same),
                    null
                )
                diff > 0 -> Pair(
                    stringResource(R.string.home_trend_comparison_more, percentage),
                    true
                )
                else -> Pair(
                    stringResource(R.string.home_trend_comparison_less, percentage),
                    false
                )
            }
        }
    }
}
