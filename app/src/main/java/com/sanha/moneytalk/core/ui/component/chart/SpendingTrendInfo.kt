package com.sanha.moneytalk.core.ui.component.chart

/**
 * 누적 추이 섹션에 필요한 데이터 Contract.
 *
 * [CumulativeTrendSection]에 전달할 모든 데이터를 정의한다.
 * 도메인별 Mapper(HomeSpendingTrendInfo, CategorySpendingTrendInfo 등)가
 * 이 Interface를 구현하여 각 도메인의 원시 데이터를 변환한다.
 *
 * @see CumulativeTrendSection
 */
interface SpendingTrendInfo {
    /** 섹션 제목 (예: "지출 추이", "식비 추이") */
    val title: String

    /** 항상 표시되는 메인 곡선 (이번 달 누적) */
    val primaryLine: CumulativeChartLine

    /** 토글 가능한 비교 곡선 리스트 */
    val toggleableLines: List<ToggleableLine>

    /** 해당 월 총 일수 */
    val daysInMonth: Int

    /** 오늘이 해당 월의 몇번째 날인지 (0-based, -1이면 과거 월) */
    val todayDayIndex: Int
}
