package com.sanha.moneytalk.core.util

import com.sanha.moneytalk.core.database.entity.ExpenseEntity

/**
 * 누적 지출 차트 데이터 빌딩 유틸.
 *
 * 도메인/UI 독립적인 순수 계산 함수만 포함.
 * Repository를 직접 참조하지 않으며, 데이터 로드 방식은 호출부가 함수 파라미터로 제공.
 *
 * 사용처: HomeViewModel, (추후) CategoryViewModel 등
 */
object CumulativeChartDataBuilder {

    private const val DAY_MS = 24L * 60 * 60 * 1000

    /**
     * 지출 목록을 일별 누적 리스트로 변환.
     *
     * @param expenses 해당 월 지출 목록
     * @param monthStart 월 시작 timestamp (ms)
     * @param daysInMonth 해당 월 총 일수
     * @return index=dayOffset(0-based), value=누적금액. daysInMonth <= 0이면 빈 리스트.
     */
    fun buildDailyCumulative(
        expenses: List<ExpenseEntity>,
        monthStart: Long,
        daysInMonth: Int
    ): List<Long> {
        if (daysInMonth <= 0) return emptyList()
        val dailyMap = expenses.groupBy {
            ((it.dateTime - monthStart) / DAY_MS).toInt().coerceIn(0, daysInMonth - 1)
        }.mapValues { (_, items) -> items.sumOf { it.amount.toLong() } }

        val result = mutableListOf<Long>()
        var cumulative = 0L
        for (day in 0 until daysInMonth) {
            cumulative += dailyMap[day] ?: 0L
            result.add(cumulative)
        }
        return result
    }

    /**
     * 이전 N개월 평균 일별 누적 지출 계산.
     *
     * Repository 접근이 필요하므로 suspend. 데이터 로드 방식은 [getExpensesByDateRange]로 주입하여
     * 호출부에서 exclusionKeywords 필터링, 카테고리 필터링 등을 적용 가능.
     *
     * N개월 모두 데이터가 있어야 평균을 노출하며, 하나라도 비면 빈 리스트를 반환.
     * 짧은 월은 마지막 누적값을 carry-forward하여 평균 계산 시 하락 방지.
     *
     * @param n 과거 몇 개월 (예: 3, 6)
     * @param year 기준 연도
     * @param month 기준 월
     * @param monthStartDay 사용자 설정 월 시작일
     * @param daysInMonth 기준 월의 총 일수
     * @param getExpensesByDateRange 호출부가 제공하는 데이터 로드 함수 (startTime, endTime) → 지출 목록
     * @return 일별 평균 누적 리스트. 데이터 부족 시 빈 리스트.
     */
    suspend fun buildAvgNMonthCumulative(
        n: Int,
        year: Int,
        month: Int,
        monthStartDay: Int,
        daysInMonth: Int,
        getExpensesByDateRange: suspend (Long, Long) -> List<ExpenseEntity>
    ): List<Long> {
        val cumulatives = mutableListOf<List<Long>>()
        var y = year
        var m = month
        for (i in 1..n) {
            m -= 1
            if (m < 1) { m = 12; y -= 1 }
            val (s, e) = DateUtils.getCustomMonthPeriod(y, m, monthStartDay)
            val days = ((e - s) / DAY_MS).toInt()
            val exps = getExpensesByDateRange(s, e)
            // n개월 모두 데이터가 있어야 평균 노출 → 하나라도 비면 즉시 빈 리스트 반환
            if (exps.isEmpty()) return emptyList()
            cumulatives.add(buildDailyCumulative(exps, s, days))
        }
        if (cumulatives.isEmpty()) return emptyList()
        // 짧은 월은 마지막 누적값을 carry forward하여 평균 계산 시 하락 방지
        return (0 until daysInMonth).map { day ->
            val values = cumulatives.map { monthData ->
                monthData.getOrNull(day) ?: monthData.lastOrNull() ?: 0L
            }
            values.sum() / values.size
        }
    }

    /**
     * 예산 선형 누적 포인트 생성.
     *
     * 월 총 예산을 일수로 균등 분배하여 선형 누적 곡선 데이터를 생성.
     * 예: 300만원/30일 = 하루 10만원씩 누적 (0→10만→20만→...→300만)
     *
     * @param monthlyBudget 월 총 예산 (원)
     * @param daysInMonth 해당 월 총 일수
     * @return 일별 예산 누적 리스트. daysInMonth <= 0이면 빈 리스트.
     */
    fun buildBudgetCumulativePoints(monthlyBudget: Int, daysInMonth: Int): List<Long> {
        if (daysInMonth <= 0) return emptyList()
        val dailyBudget = monthlyBudget.toLong()
        return (0 until daysInMonth).map { day ->
            dailyBudget * (day + 1) / daysInMonth
        }
    }
}
