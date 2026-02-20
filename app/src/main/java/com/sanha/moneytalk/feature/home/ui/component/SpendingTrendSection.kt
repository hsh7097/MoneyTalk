package com.sanha.moneytalk.feature.home.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.sanha.moneytalk.core.ui.component.chart.CumulativeTrendSection
import com.sanha.moneytalk.core.ui.component.chart.SpendingTrendInfo

/**
 * 누적 추이 섹션 Composable.
 *
 * [SpendingTrendInfo] Contract를 받아 [CumulativeTrendSection]에 위임하는 얇은 래퍼.
 * 도메인별 Mapper(HomeSpendingTrendInfo, CategorySpendingTrendInfo 등)가
 * 생성한 데이터를 그대로 전달한다.
 *
 * @param info 누적 추이 데이터 (null이면 미표시 → 호출부에서 null 체크)
 * @param modifier 외부 Modifier
 */
@Composable
fun SpendingTrendSection(
    info: SpendingTrendInfo,
    modifier: Modifier = Modifier
) {
    if (info.primaryLine.points.isEmpty()) return

    CumulativeTrendSection(
        title = info.title,
        primaryLine = info.primaryLine,
        toggleableLines = info.toggleableLines,
        daysInMonth = info.daysInMonth,
        todayDayIndex = info.todayDayIndex,
        modifier = modifier
    )
}
