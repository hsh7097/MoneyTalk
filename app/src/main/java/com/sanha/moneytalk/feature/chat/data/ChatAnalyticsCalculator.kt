package com.sanha.moneytalk.feature.chat.data

import com.sanha.moneytalk.core.database.entity.ExpenseEntity
import com.sanha.moneytalk.core.model.Category
import com.sanha.moneytalk.core.util.AnalyticsFilter
import com.sanha.moneytalk.core.util.AnalyticsMetric
import com.sanha.moneytalk.core.util.DataQuery
import com.sanha.moneytalk.core.util.DateUtils
import com.sanha.moneytalk.core.util.QueryResult
import com.sanha.moneytalk.core.util.QueryType
import com.sanha.moneytalk.core.util.StoreNameNormalizer
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject

/** DB/UI에 의존하지 않는 채팅 복합 분석: 필터 → 그룹 → 집계 → 결과 문자열. */
class ChatAnalyticsCalculator @Inject constructor() {
    private val numberFormat = NumberFormat.getNumberInstance(Locale.KOREA)

    fun calculate(
        query: DataQuery,
        sourceExpenses: List<ExpenseEntity>,
        periodLabel: String
    ): QueryResult {
        var expenses = sourceExpenses

        // 2. filters 배열 순회하며 메모리 필터링
        val filters = query.filters ?: emptyList()
        val filterDescriptions = mutableListOf<String>()

        for (filter in filters) {
            val before = expenses.size
            expenses = applyAnalyticsFilter(expenses, filter)
            if (expenses.size != before || filters.isNotEmpty()) {
                filterDescriptions.add(describeFilter(filter))
            }
        }

        // 3. groupBy 처리
        val groupBy = query.groupBy
        val grouped: Map<String, List<ExpenseEntity>> =
            if (groupBy.isNullOrBlank() || groupBy == "none") {
                mapOf("전체" to expenses)
            } else {
                expenses.groupBy { expense -> getGroupKey(expense, groupBy) }
            }

        // 4. metrics 계산
        val metrics = if (query.metrics.isNullOrEmpty()) {
            // 기본: sum + count
            listOf(
                AnalyticsMetric(op = "sum", field = "amount"),
                AnalyticsMetric(op = "count", field = "amount")
            )
        } else {
            query.metrics
        }

        // 5. 그룹별 집계 계산
        data class GroupResult(
            val key: String,
            val metricValues: List<Pair<String, Number>>, // (label, value)
            val sortValue: Number // 정렬 기준
        )

        val groupResults = grouped.map { (key, items) ->
            val metricValues = metrics.map { metric ->
                val label = getMetricLabel(metric.op)
                val value: Number = computeMetric(items, metric.op)
                label to value
            }
            val sortValue = metricValues.firstOrNull()?.second ?: 0
            GroupResult(key, metricValues, sortValue)
        }

        // 6. sort + topN 적용
        val sortDir = query.sort ?: "desc"
        val sorted = if (sortDir == "asc") {
            groupResults.sortedBy { it.sortValue.toDouble() }
        } else {
            groupResults.sortedByDescending { it.sortValue.toDouble() }
        }
        val limited = query.topN?.let { sorted.take(it) } ?: sorted

        // 7. 결과 포맷팅
        val sb = StringBuilder()
        sb.appendLine("[ANALYTICS 계산 결과]")

        if (groupBy.isNullOrBlank() || groupBy == "none") {
            // 그룹 없음: 전체 집계
            val result = limited.firstOrNull()
            if (result != null) {
                for ((label, value) in result.metricValues) {
                    sb.appendLine("$label: ${formatMetricValue(label, value)}")
                }
            } else {
                sb.appendLine("해당 조건에 맞는 데이터가 없습니다.")
            }
        } else {
            // 그룹 있음
            val groupLabel = getGroupByLabel(groupBy)
            val topNLabel = query.topN?.let { " (상위 ${it}개)" } ?: ""
            sb.appendLine("${groupLabel}별 집계$topNLabel:")
            if (limited.isEmpty()) {
                sb.appendLine("해당 조건에 맞는 데이터가 없습니다.")
            } else {
                limited.forEachIndexed { idx, result ->
                    val metricsStr = result.metricValues.joinToString(", ") { (label, value) ->
                        "$label: ${formatMetricValue(label, value)}"
                    }
                    sb.appendLine("${idx + 1}. ${result.key}: $metricsStr")
                }
            }
        }

        // 기간 정보
        sb.appendLine("기간: $periodLabel")
        // 전체 건수
        sb.appendLine("필터 후 총 건수: ${expenses.size}건")
        // 필터 설명
        if (filterDescriptions.isNotEmpty()) {
            sb.appendLine("적용된 필터: ${filterDescriptions.joinToString(", ")}")
        }

        val resultData = sb.toString().trimEnd()
        return QueryResult(
            queryType = QueryType.ANALYTICS,
            data = resultData
        )
    }

    /**
     * 단일 필터 조건을 적용하여 필터링된 리스트 반환
     */
    private fun applyAnalyticsFilter(
        expenses: List<ExpenseEntity>,
        filter: AnalyticsFilter
    ): List<ExpenseEntity> {
        return expenses.filter { expense ->
            when (filter.field) {
                "category" -> {
                    val expenseCategory = expense.category
                    val targetValue = filter.value
                    if (filter.includeSubcategories && targetValue is String) {
                        // 하위 카테고리 포함 (displayNamesIncludingSub)
                        val cat = Category.fromDisplayName(targetValue)
                        val names = cat.displayNamesIncludingSub
                        when (filter.op) {
                            "==" -> expenseCategory in names
                            "!=" -> expenseCategory !in names
                            "in" -> {
                                // value가 배열이면 각각에 대해 subcategory 포함
                                val valueList = toStringList(targetValue)
                                val allNames = valueList.flatMap {
                                    Category.fromDisplayName(it).displayNamesIncludingSub
                                }
                                expenseCategory in allNames
                            }

                            "not_in" -> {
                                val valueList = toStringList(targetValue)
                                val allNames = valueList.flatMap {
                                    Category.fromDisplayName(it).displayNamesIncludingSub
                                }
                                expenseCategory !in allNames
                            }

                            else -> matchStringOp(expenseCategory, filter.op, targetValue)
                        }
                    } else {
                        when (filter.op) {
                            "in" -> expenseCategory in toStringList(filter.value)
                            "not_in" -> expenseCategory !in toStringList(filter.value)
                            else -> matchStringOp(
                                expenseCategory,
                                filter.op,
                                filter.value?.toString() ?: ""
                            )
                        }
                    }
                }

                "storeName" -> {
                    val value = filter.value?.toString() ?: ""
                    when (filter.op) {
                        "==" -> StoreNameNormalizer.equalsForComparison(expense.storeName, value)
                        "!=" -> !StoreNameNormalizer.equalsForComparison(expense.storeName, value)
                        "contains" -> StoreNameNormalizer.containsForComparison(expense.storeName, value)
                        "not_contains" -> !StoreNameNormalizer.containsForComparison(expense.storeName, value)
                        "in" -> toStringList(filter.value).any {
                            StoreNameNormalizer.equalsForComparison(expense.storeName, it)
                        }

                        "not_in" -> toStringList(filter.value).none {
                            StoreNameNormalizer.equalsForComparison(expense.storeName, it)
                        }

                        else -> true
                    }
                }

                "cardName" -> {
                    val value = filter.value?.toString() ?: ""
                    when (filter.op) {
                        "==" -> expense.cardName.equals(value, ignoreCase = true)
                        "!=" -> !expense.cardName.equals(value, ignoreCase = true)
                        "contains" -> expense.cardName.contains(value, ignoreCase = true)
                        "not_contains" -> !expense.cardName.contains(value, ignoreCase = true)
                        "in" -> toStringList(filter.value).any {
                            expense.cardName.equals(
                                it,
                                ignoreCase = true
                            )
                        }

                        "not_in" -> toStringList(filter.value).none {
                            expense.cardName.equals(
                                it,
                                ignoreCase = true
                            )
                        }

                        else -> true
                    }
                }

                "amount" -> {
                    val targetAmount = toNumber(filter.value)
                    when (filter.op) {
                        "==" -> expense.amount.toDouble() == targetAmount
                        "!=" -> expense.amount.toDouble() != targetAmount
                        ">" -> expense.amount > targetAmount
                        ">=" -> expense.amount >= targetAmount
                        "<" -> expense.amount < targetAmount
                        "<=" -> expense.amount <= targetAmount
                        else -> true
                    }
                }

                "memo" -> {
                    val value = filter.value?.toString() ?: ""
                    val memo = expense.memo ?: ""
                    when (filter.op) {
                        "==" -> memo.equals(value, ignoreCase = true)
                        "!=" -> !memo.equals(value, ignoreCase = true)
                        "contains" -> memo.contains(value, ignoreCase = true)
                        "not_contains" -> !memo.contains(value, ignoreCase = true)
                        else -> true
                    }
                }

                "dayOfWeek" -> {
                    val cal = Calendar.getInstance().apply { timeInMillis = expense.dateTime }
                    val dayOfWeek = getDayOfWeekString(cal.get(Calendar.DAY_OF_WEEK))
                    when (filter.op) {
                        "==" -> dayOfWeek.equals(filter.value?.toString(), ignoreCase = true)
                        "!=" -> !dayOfWeek.equals(filter.value?.toString(), ignoreCase = true)
                        "in" -> dayOfWeek.uppercase() in toStringList(filter.value).map { it.uppercase() }
                        "not_in" -> dayOfWeek.uppercase() !in toStringList(filter.value).map { it.uppercase() }
                        else -> true
                    }
                }

                else -> true // 미인식 필드는 무시 (필터 통과)
            }
        }
    }

    /** Calendar.DAY_OF_WEEK → "MON"~"SUN" 문자열 변환 */
    private fun getDayOfWeekString(calendarDay: Int): String {
        return when (calendarDay) {
            Calendar.MONDAY -> "MON"
            Calendar.TUESDAY -> "TUE"
            Calendar.WEDNESDAY -> "WED"
            Calendar.THURSDAY -> "THU"
            Calendar.FRIDAY -> "FRI"
            Calendar.SATURDAY -> "SAT"
            Calendar.SUNDAY -> "SUN"
            else -> "UNKNOWN"
        }
    }

    /** 요일 코드를 한글로 변환 */
    private fun dayOfWeekToKorean(code: String): String {
        return when (code.uppercase()) {
            "MON" -> "월"
            "TUE" -> "화"
            "WED" -> "수"
            "THU" -> "목"
            "FRI" -> "금"
            "SAT" -> "토"
            "SUN" -> "일"
            else -> code
        }
    }

    /** 문자열 비교 연산 헬퍼 */
    private fun matchStringOp(actual: String, op: String, expected: String): Boolean {
        return when (op) {
            "==" -> actual.equals(expected, ignoreCase = true)
            "!=" -> !actual.equals(expected, ignoreCase = true)
            "contains" -> actual.contains(expected, ignoreCase = true)
            "not_contains" -> !actual.contains(expected, ignoreCase = true)
            else -> true
        }
    }

    /** Any? → List<String> 변환 (Gson이 배열을 ArrayList로 파싱) */
    private fun toStringList(value: Any?): List<String> {
        return when (value) {
            is List<*> -> value.mapNotNull { it?.toString() }
            is String -> listOf(value)
            else -> emptyList()
        }
    }

    /** Any? → Number 변환 */
    private fun toNumber(value: Any?): Double {
        return when (value) {
            is Number -> value.toDouble()
            is String -> value.toDoubleOrNull() ?: 0.0
            else -> 0.0
        }
    }

    /** 그룹 키 추출 */
    private fun getGroupKey(expense: ExpenseEntity, groupBy: String): String {
        return when (groupBy) {
            "category" -> expense.category
            "storeName" -> expense.storeName
            "cardName" -> expense.cardName
            "date" -> DateUtils.formatDateTime(expense.dateTime).substring(0, 10) // "yyyy-MM-dd"
            "month" -> DateUtils.formatDateTime(expense.dateTime).substring(0, 7) // "yyyy-MM"
            "dayOfWeek" -> {
                val cal = Calendar.getInstance().apply { timeInMillis = expense.dateTime }
                getDayOfWeekString(cal.get(Calendar.DAY_OF_WEEK))
            }

            else -> "전체" // 미인식 groupBy → 전체 집계
        }
    }

    /** 그룹 기준 한글 라벨 */
    private fun getGroupByLabel(groupBy: String): String {
        return when (groupBy) {
            "category" -> "카테고리"
            "storeName" -> "가게명"
            "cardName" -> "카드"
            "date" -> "날짜"
            "month" -> "월"
            "dayOfWeek" -> "요일"
            else -> groupBy
        }
    }

    /** 메트릭 연산 실행 */
    private fun computeMetric(items: List<ExpenseEntity>, op: String): Number {
        // 현재 amount만 지원
        val values = items.map { it.amount }
        return when (op) {
            "sum" -> values.sum()
            "avg" -> if (values.isEmpty()) 0 else (values.sum().toDouble() / values.size).toInt()
            "count" -> values.size
            "max" -> values.maxOrNull() ?: 0
            "min" -> values.minOrNull() ?: 0
            else -> 0
        }
    }

    /** 메트릭 라벨 생성 */
    private fun getMetricLabel(op: String): String {
        return when (op) {
            "sum" -> "합계"
            "avg" -> "평균"
            "count" -> "건수"
            "max" -> "최대"
            "min" -> "최소"
            else -> op
        }
    }

    /** 메트릭 값 포맷팅 */
    private fun formatMetricValue(label: String, value: Number): String {
        return when (label) {
            "건수" -> "${numberFormat.format(value)}건"
            else -> "${numberFormat.format(value)}원"
        }
    }

    /** 필터 조건 설명 문자열 */
    private fun describeFilter(filter: AnalyticsFilter): String {
        val fieldLabel = when (filter.field) {
            "category" -> "카테고리"
            "storeName" -> "가게명"
            "cardName" -> "카드"
            "amount" -> "금액"
            "memo" -> "메모"
            "dayOfWeek" -> "요일"
            else -> filter.field
        }
        val opLabel = when (filter.op) {
            "==" -> "="
            "!=" -> "≠"
            ">" -> ">"
            ">=" -> "≥"
            "<" -> "<"
            "<=" -> "≤"
            "contains" -> "포함"
            "not_contains" -> "미포함"
            "in" -> "∈"
            "not_in" -> "∉"
            else -> filter.op
        }
        val valueStr = when (val v = filter.value) {
            is List<*> -> v.joinToString(",")
            else -> v?.toString() ?: ""
        }
        val subLabel = if (filter.includeSubcategories) "(하위포함)" else ""
        return "$fieldLabel$opLabel$valueStr$subLabel"
    }

}
