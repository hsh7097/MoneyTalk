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
import java.util.Locale
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

            // 전월 비교 누적 금액
            val todayIdx = pageData.todayDayIndex
            val lastMonthAmt = if (pageData.lastMonthDailyCumulative.isEmpty()) {
                0L
            } else if (todayIdx >= 0) {
                // 현재 월: 오늘 기준 동일 시점의 전월 누적
                val clampedIdx = todayIdx.coerceAtMost(pageData.lastMonthDailyCumulative.size - 1)
                pageData.lastMonthDailyCumulative[clampedIdx]
            } else {
                // 과거 월: 해당 월 완료 상태이므로 전월 최종 누적으로 비교
                pageData.lastMonthDailyCumulative.last()
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
            val absDiff = abs(diff)
            val percentage = ((absDiff.toDouble() / lastMonth) * 100).roundToInt()
            val formattedAmount = NumberFormat.getNumberInstance(Locale.KOREA).format(absDiff)

            return when {
                percentage < 3 -> Pair(
                    stringResource(R.string.home_trend_comparison_same),
                    null
                )
                diff > 0 -> Pair(
                    stringResource(R.string.home_trend_comparison_more, formattedAmount, percentage),
                    true
                )
                else -> Pair(
                    stringResource(R.string.home_trend_comparison_less, formattedAmount, percentage),
                    false
                )
            }
        }
    }
}
