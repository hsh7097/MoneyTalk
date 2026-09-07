package com.sanha.moneytalk.feature.chat.data

import com.sanha.moneytalk.core.database.entity.ExpenseEntity
import com.sanha.moneytalk.core.util.AnalyticsFilter
import com.sanha.moneytalk.core.util.AnalyticsMetric
import com.sanha.moneytalk.core.util.DataQuery
import com.sanha.moneytalk.core.util.QueryType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class ChatAnalyticsCalculatorTest {
    private val calculator = ChatAnalyticsCalculator()
    private val period = "2026-09-01 ~ 2026-09-30"

    @Test
    fun defaultMetricsUseAllSuppliedExpenses() {
        val result = calculator.calculate(
            DataQuery(QueryType.ANALYTICS),
            listOf(expense(2_000), expense(3_500)),
            period
        )

        assertEquals(QueryType.ANALYTICS, result.queryType)
        assertEquals(
            "[ANALYTICS 계산 결과]\n합계: 5,500원\n건수: 2건\n기간: $period\n필터 후 총 건수: 2건",
            result.data
        )
    }

    @Test
    fun groupedResultsApplyFiltersBeforeSortingAndTopN() {
        val result = calculator.calculate(
            DataQuery(
                type = QueryType.ANALYTICS,
                filters = listOf(AnalyticsFilter("amount", ">=", 2_000)),
                groupBy = "storeName",
                topN = 1
            ),
            listOf(expense(2_000, "A"), expense(3_000, "A"), expense(4_500, "B"), expense(1_000, "B")),
            period
        )

        assertTrue(result.data.contains("1. A: 합계: 5,000원, 건수: 2건"))
        assertFalse(result.data.contains("2. B:"))
        assertTrue(result.data.contains("필터 후 총 건수: 3건"))
        assertTrue(result.data.contains("금액≥2000"))
    }

    @Test
    fun categoryFilterIncludesChildCategoriesWhenRequested() {
        val result = calculator.calculate(
            DataQuery(
                type = QueryType.ANALYTICS,
                filters = listOf(AnalyticsFilter("category", "==", "식비", includeSubcategories = true))
            ),
            listOf(expense(1_000, category = "식비"), expense(2_000, category = "배달"), expense(9_000, category = "교통")),
            period
        )

        assertTrue(result.data.contains("합계: 3,000원"))
        assertTrue(result.data.contains("건수: 2건"))
    }

    @Test
    fun averageMinMaxAndAscendingSortKeepExistingIntegerPrecision() {
        val result = calculator.calculate(
            DataQuery(
                type = QueryType.ANALYTICS,
                groupBy = "storeName",
                metrics = listOf(AnalyticsMetric("avg"), AnalyticsMetric("min"), AnalyticsMetric("max")),
                sort = "asc"
            ),
            listOf(expense(1_001, "A"), expense(2_000, "A"), expense(500, "B")),
            period
        )

        assertTrue(result.data.contains("1. B: 평균: 500원, 최소: 500원, 최대: 500원"))
        assertTrue(result.data.contains("2. A: 평균: 1,500원, 최소: 1,001원, 최대: 2,000원"))
    }

    @Test
    fun emptyGroupedAndUngroupedResultsRemainExplicit() {
        val grouped = calculator.calculate(DataQuery(QueryType.ANALYTICS, groupBy = "category"), emptyList(), period)
        val ungrouped = calculator.calculate(DataQuery(QueryType.ANALYTICS), emptyList(), period)

        assertTrue(grouped.data.contains("해당 조건에 맞는 데이터가 없습니다."))
        assertTrue(grouped.data.contains("필터 후 총 건수: 0건"))
        assertTrue(ungrouped.data.contains("합계: 0원\n건수: 0건"))
    }

    @Test
    fun weekdayAndMemoFiltersCombineWithAnd() {
        val monday = Calendar.getInstance().apply { set(2026, Calendar.SEPTEMBER, 7, 12, 0, 0) }.timeInMillis
        val tuesday = Calendar.getInstance().apply { set(2026, Calendar.SEPTEMBER, 8, 12, 0, 0) }.timeInMillis
        val result = calculator.calculate(
            DataQuery(
                type = QueryType.ANALYTICS,
                filters = listOf(AnalyticsFilter("dayOfWeek", "in", listOf("mon")), AnalyticsFilter("memo", "contains", "lunch"))
            ),
            listOf(
                expense(4_000).copy(dateTime = monday, memo = "Lunch with team"),
                expense(9_000).copy(dateTime = tuesday, memo = "Lunch"),
                expense(2_000).copy(dateTime = monday, memo = null)
            ),
            period
        )

        assertTrue(result.data.contains("합계: 4,000원\n건수: 1건"))
    }

    private fun expense(amount: Int, storeName: String = "상점", category: String = "식비") = ExpenseEntity(
        amount = amount,
        storeName = storeName,
        category = category,
        cardName = "테스트카드",
        dateTime = 0L,
        originalSms = "",
        smsId = "test"
    )
}
