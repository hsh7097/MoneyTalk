package com.sanha.moneytalk.core.appfunctions

import com.sanha.moneytalk.core.appfunctions.MoneyTalkAppFunctionInputRules.categoryNamesIncludingCustom
import com.sanha.moneytalk.core.appfunctions.MoneyTalkAppFunctionInputRules.coerceLimit
import com.sanha.moneytalk.core.database.entity.ExpenseEntity
import com.sanha.moneytalk.core.util.DateUtils
import java.util.Calendar
import javax.inject.Inject

/** 조회된 지출을 typed App Function 분석 응답으로 계산한다. 저장소와 UI 상태를 참조하지 않는다. */
class MoneyTalkAppFunctionAnalyticsCalculator @Inject constructor() {
    fun calculate(
        sourceExpenses: List<ExpenseEntity>,
        filters: List<MoneyTalkAnalyticsFilter>?,
        groupBy: String?,
        metrics: List<MoneyTalkAnalyticsMetric>?,
        topN: Int?,
        sort: String?
    ): MoneyTalkAnalyticsResponse {
        var expenses = sourceExpenses
        filters.orEmpty().forEach { filter ->
            expenses = applyAnalyticsFilter(expenses, filter)
        }

        val normalizedGroupBy = groupBy?.takeIf { it.isNotBlank() } ?: GROUP_NONE
        val grouped = if (normalizedGroupBy == GROUP_NONE) {
            mapOf(GROUP_ALL to expenses)
        } else {
            expenses.groupBy { getGroupKey(it, normalizedGroupBy) }
        }

        val normalizedMetrics = metrics.orEmpty().ifEmpty {
            listOf(
                MoneyTalkAnalyticsMetric(op = METRIC_SUM, field = FIELD_AMOUNT),
                MoneyTalkAnalyticsMetric(op = METRIC_COUNT, field = FIELD_AMOUNT)
            )
        }
        val sortDirection = if (sort == SORT_ASC) SORT_ASC else SORT_DESC

        val groups = grouped.map { (key, items) ->
            val metricResults = normalizedMetrics.map { metric ->
                val value = computeMetric(items, metric.op)
                MoneyTalkAnalyticsMetricResult(
                    op = metric.op,
                    field = metric.field,
                    value = value,
                    unit = if (metric.op == METRIC_COUNT) UNIT_COUNT else UNIT_WON
                )
            }
            MoneyTalkAnalyticsGroupResult(groupKey = key, metrics = metricResults)
        }
        val sortedGroups = if (sortDirection == SORT_ASC) {
            groups.sortedBy { it.metrics.firstOrNull()?.value ?: 0 }
        } else {
            groups.sortedByDescending { it.metrics.firstOrNull()?.value ?: 0 }
        }
        val limitedGroups = sortedGroups.take(coerceLimit(topN, defaultLimit = sortedGroups.size))

        return MoneyTalkAnalyticsResponse(
            filteredCount = expenses.size,
            groupBy = normalizedGroupBy,
            sort = sortDirection,
            results = limitedGroups
        )
    }

    private fun applyAnalyticsFilter(
        expenses: List<ExpenseEntity>,
        filter: MoneyTalkAnalyticsFilter
    ): List<ExpenseEntity> {
        return expenses.filter { expense ->
            when (filter.field) {
                FIELD_CATEGORY -> matchCategoryFilter(expense.category, filter)
                FIELD_STORE_NAME -> matchStringFilter(expense.storeName, filter)
                FIELD_CARD_NAME -> matchStringFilter(expense.cardName, filter)
                FIELD_AMOUNT -> matchAmountFilter(expense.amount, filter)
                FIELD_MEMO -> matchStringFilter(expense.memo.orEmpty(), filter)
                FIELD_DAY_OF_WEEK -> matchStringFilter(getDayOfWeekString(expense.dateTime), filter)
                else -> true
            }
        }
    }

    private fun matchCategoryFilter(actual: String, filter: MoneyTalkAnalyticsFilter): Boolean {
        val values = filter.value.toCsvValues()
        val targets = if (filter.includeSubcategories) {
            values.flatMap { categoryNamesIncludingCustom(it) }
        } else {
            values
        }
        return when (filter.op) {
            OP_EQ -> actual in targets
            OP_NE -> actual !in targets
            OP_IN -> actual in targets
            OP_NOT_IN -> actual !in targets
            else -> matchStringFilter(actual, filter)
        }
    }

    private fun matchStringFilter(actual: String, filter: MoneyTalkAnalyticsFilter): Boolean {
        val values = filter.value.toCsvValues()
        return when (filter.op) {
            OP_EQ -> actual.equals(filter.value, ignoreCase = true)
            OP_NE -> !actual.equals(filter.value, ignoreCase = true)
            OP_CONTAINS -> actual.contains(filter.value, ignoreCase = true)
            OP_NOT_CONTAINS -> !actual.contains(filter.value, ignoreCase = true)
            OP_IN -> values.any { actual.equals(it, ignoreCase = true) }
            OP_NOT_IN -> values.none { actual.equals(it, ignoreCase = true) }
            else -> true
        }
    }

    private fun matchAmountFilter(actual: Int, filter: MoneyTalkAnalyticsFilter): Boolean {
        val expected = filter.value.toIntOrNull() ?: return true
        return when (filter.op) {
            OP_EQ -> actual == expected
            OP_NE -> actual != expected
            OP_GT -> actual > expected
            OP_GTE -> actual >= expected
            OP_LT -> actual < expected
            OP_LTE -> actual <= expected
            else -> true
        }
    }

    private fun getGroupKey(expense: ExpenseEntity, groupBy: String): String {
        return when (groupBy) {
            FIELD_CATEGORY -> expense.category
            FIELD_STORE_NAME -> expense.storeName
            FIELD_CARD_NAME -> expense.cardName
            FIELD_DATE -> DateUtils.formatDateTime(expense.dateTime).take(DATE_TEXT_LENGTH)
            FIELD_MONTH -> DateUtils.formatDateTime(expense.dateTime).take(MONTH_TEXT_LENGTH)
            FIELD_DAY_OF_WEEK -> getDayOfWeekString(expense.dateTime)
            else -> GROUP_ALL
        }
    }

    private fun computeMetric(items: List<ExpenseEntity>, op: String): Int {
        val values = items.map { it.amount }
        return when (op) {
            METRIC_SUM -> values.sum()
            METRIC_AVG -> if (values.isEmpty()) 0 else values.sum() / values.size
            METRIC_COUNT -> values.size
            METRIC_MAX -> values.maxOrNull() ?: 0
            METRIC_MIN -> values.minOrNull() ?: 0
            else -> 0
        }
    }

    private fun getDayOfWeekString(dateTime: Long): String {
        val day = Calendar.getInstance().apply { timeInMillis = dateTime }
            .get(Calendar.DAY_OF_WEEK)
        return when (day) {
            Calendar.MONDAY -> DAY_MON
            Calendar.TUESDAY -> DAY_TUE
            Calendar.WEDNESDAY -> DAY_WED
            Calendar.THURSDAY -> DAY_THU
            Calendar.FRIDAY -> DAY_FRI
            Calendar.SATURDAY -> DAY_SAT
            Calendar.SUNDAY -> DAY_SUN
            else -> DAY_UNKNOWN
        }
    }

    private fun String.toCsvValues(): List<String> {
        return split(",").map { it.trim() }.filter { it.isNotBlank() }
    }

    private companion object {
        private const val DATE_TEXT_LENGTH = 10
        private const val MONTH_TEXT_LENGTH = 7
        private const val GROUP_NONE = "none"
        private const val GROUP_ALL = "all"
        private const val SORT_ASC = "asc"
        private const val SORT_DESC = "desc"
        private const val FIELD_CATEGORY = "category"
        private const val FIELD_STORE_NAME = "storeName"
        private const val FIELD_CARD_NAME = "cardName"
        private const val FIELD_AMOUNT = "amount"
        private const val FIELD_MEMO = "memo"
        private const val FIELD_DAY_OF_WEEK = "dayOfWeek"
        private const val FIELD_DATE = "date"
        private const val FIELD_MONTH = "month"
        private const val METRIC_SUM = "sum"
        private const val METRIC_AVG = "avg"
        private const val METRIC_COUNT = "count"
        private const val METRIC_MAX = "max"
        private const val METRIC_MIN = "min"
        private const val UNIT_WON = "won"
        private const val UNIT_COUNT = "count"
        private const val OP_EQ = "=="
        private const val OP_NE = "!="
        private const val OP_GT = ">"
        private const val OP_GTE = ">="
        private const val OP_LT = "<"
        private const val OP_LTE = "<="
        private const val OP_CONTAINS = "contains"
        private const val OP_NOT_CONTAINS = "not_contains"
        private const val OP_IN = "in"
        private const val OP_NOT_IN = "not_in"
        private const val DAY_MON = "MON"
        private const val DAY_TUE = "TUE"
        private const val DAY_WED = "WED"
        private const val DAY_THU = "THU"
        private const val DAY_FRI = "FRI"
        private const val DAY_SAT = "SAT"
        private const val DAY_SUN = "SUN"
        private const val DAY_UNKNOWN = "UNKNOWN"
    }
}
