package com.sanha.moneytalk.core.appfunctions

import com.sanha.moneytalk.core.database.entity.ExpenseEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class MoneyTalkAppFunctionAnalyticsCalculatorTest {
    private val calculator = MoneyTalkAppFunctionAnalyticsCalculator()

    @Test
    fun defaultMetricsReturnTypedSumAndCount() {
        val result = calculate(listOf(expense(2_000), expense(3_500)))

        assertEquals(2, result.filteredCount)
        assertEquals("none", result.groupBy)
        assertEquals("desc", result.sort)
        assertEquals(
            listOf(
                MoneyTalkAnalyticsMetricResult("sum", "amount", 5_500, "won"),
                MoneyTalkAnalyticsMetricResult("count", "amount", 2, "count")
            ),
            result.results.single().metrics
        )
        assertEquals("all", result.results.single().groupKey)
    }

    @Test
    fun filtersApplyBeforeGroupingSortingAndTopN() {
        val result = calculate(
            listOf(expense(2_000, "A"), expense(3_000, "A"), expense(4_500, "B"), expense(1_000, "B")),
            filters = listOf(MoneyTalkAnalyticsFilter("amount", ">=", "2000", false)),
            groupBy = "storeName",
            topN = 1
        )

        assertEquals(3, result.filteredCount)
        assertEquals("A", result.results.single().groupKey)
        assertEquals(5_000, result.results.single().metrics.first().value)
    }

    @Test
    fun categorySubcategoriesAndCustomCategoryRemainDistinct() {
        val expenses = listOf(
            expense(1_000, category = "식비"), expense(2_000, category = "배달"),
            expense(4_000, category = "내 카테고리"), expense(9_000, category = "교통")
        )
        val category = calculate(
            expenses,
            filters = listOf(MoneyTalkAnalyticsFilter("category", "in", "식비", true))
        )
        val custom = calculate(
            expenses,
            filters = listOf(MoneyTalkAnalyticsFilter("category", "==", "내 카테고리", true))
        )

        assertEquals(2, category.filteredCount)
        assertEquals(3_000, category.results.single().metrics.first().value)
        assertEquals(1, custom.filteredCount)
        assertEquals(4_000, custom.results.single().metrics.first().value)
    }

    @Test
    fun averageRetainsIntegerPrecisionAndAscendingOrder() {
        val result = calculate(
            listOf(expense(1_001, "A"), expense(2_000, "A"), expense(500, "B")),
            groupBy = "storeName",
            metrics = listOf(
                MoneyTalkAnalyticsMetric("avg", "amount"),
                MoneyTalkAnalyticsMetric("min", "amount"),
                MoneyTalkAnalyticsMetric("max", "amount")
            ),
            sort = "asc"
        )

        assertEquals(listOf("B", "A"), result.results.map { it.groupKey })
        assertEquals(listOf(1_500, 1_001, 2_000), result.results.last().metrics.map { it.value })
    }

    @Test
    fun emptyInputRetainsGroupedAndUngroupedContracts() {
        val grouped = calculate(emptyList(), groupBy = "category")
        val ungrouped = calculate(emptyList())

        assertEquals(0, grouped.filteredCount)
        assertTrue(grouped.results.isEmpty())
        assertEquals(listOf(0, 0), ungrouped.results.single().metrics.map { it.value })
    }

    @Test
    fun weekdayAndMemoFiltersCombineWithAndIgnoringCase() {
        val monday = Calendar.getInstance().apply { set(2026, Calendar.SEPTEMBER, 7, 12, 0, 0) }.timeInMillis
        val tuesday = Calendar.getInstance().apply { set(2026, Calendar.SEPTEMBER, 8, 12, 0, 0) }.timeInMillis
        val result = calculate(
            listOf(
                expense(4_000).copy(dateTime = monday, memo = "Lunch with team"),
                expense(9_000).copy(dateTime = tuesday, memo = "Lunch"),
                expense(2_000).copy(dateTime = monday, memo = null)
            ),
            filters = listOf(
                MoneyTalkAnalyticsFilter("dayOfWeek", "in", "mon", false),
                MoneyTalkAnalyticsFilter("memo", "contains", "lunch", false)
            )
        )

        assertEquals(1, result.filteredCount)
        assertEquals(4_000, result.results.single().metrics.first().value)
    }

    @Test
    fun topNRemainsWithinExistingOneToTwoHundredResultLimit() {
        val expenses = (1..205).map { expense(it, "store-$it") }

        assertEquals(200, calculate(expenses, groupBy = "storeName", topN = 300).results.size)
        assertEquals(1, calculate(expenses, groupBy = "storeName", topN = 0).results.size)
    }

    private fun calculate(
        expenses: List<ExpenseEntity>,
        filters: List<MoneyTalkAnalyticsFilter>? = null,
        groupBy: String? = null,
        metrics: List<MoneyTalkAnalyticsMetric>? = null,
        topN: Int? = null,
        sort: String? = null
    ) = calculator.calculate(expenses, filters, groupBy, metrics, topN, sort)

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
