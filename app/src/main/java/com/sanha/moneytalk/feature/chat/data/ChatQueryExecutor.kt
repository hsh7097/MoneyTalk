package com.sanha.moneytalk.feature.chat.data

import com.sanha.moneytalk.core.util.MoneyTalkLogger
import com.sanha.moneytalk.core.database.OwnedCardRepository
import com.sanha.moneytalk.core.database.dao.BudgetDao
import com.sanha.moneytalk.core.database.entity.ExpenseEntity
import com.sanha.moneytalk.core.database.entity.isIncludedInExpenseStats
import kotlin.math.abs
import com.sanha.moneytalk.core.datastore.SettingsDataStore
import com.sanha.moneytalk.core.model.Category
import com.sanha.moneytalk.core.util.CardVisibilityFilter
import com.sanha.moneytalk.core.util.DataQuery
import com.sanha.moneytalk.core.util.DateUtils
import com.sanha.moneytalk.core.util.QueryResult
import com.sanha.moneytalk.core.util.QueryType
import com.sanha.moneytalk.core.util.StoreAliasManager
import com.sanha.moneytalk.feature.home.data.ExpenseRepository
import com.sanha.moneytalk.feature.home.data.IncomeRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/** 로컬 조회와 Gemini 요청이 함께 사용하는 금융 데이터 조회/표시 경계. */
class ChatQueryExecutor @Inject constructor(
    private val expenseRepository: ExpenseRepository,
    private val incomeRepository: IncomeRepository,
    private val ownedCardRepository: OwnedCardRepository,
    private val settingsDataStore: SettingsDataStore,
    private val smsExclusionRepository: com.sanha.moneytalk.core.database.SmsExclusionRepository,
    private val budgetDao: BudgetDao,
    private val analyticsCalculator: ChatAnalyticsCalculator
) {
    private val numberFormat = NumberFormat.getNumberInstance(Locale.KOREA)

    private suspend fun filterVisibleExpenses(
        expenses: List<ExpenseEntity>,
        statsOnly: Boolean = false
    ): List<ExpenseEntity> {
        val excludedCardNames = ownedCardRepository.getExcludedCardNames()
        val visibleExpenses = CardVisibilityFilter.filterVisibleExpenses(expenses, excludedCardNames)
        if (!statsOnly) return visibleExpenses
        return visibleExpenses.filter { it.isIncludedInExpenseStats() }
    }

    private suspend fun getVisibleStatsExpensesByDateRange(
        startTimestamp: Long,
        endTimestamp: Long
    ): List<ExpenseEntity> {
        return filterVisibleExpenses(
            expenseRepository.getExpensesByDateRangeOnce(startTimestamp, endTimestamp),
            statsOnly = true
        )
    }

    private fun categoryTotals(expenses: List<ExpenseEntity>): List<Pair<String, Int>> {
        return expenses
            .groupBy { it.category }
            .map { (category, items) -> category to items.sumOf { expense -> expense.amount } }
            .sortedByDescending { it.second }
    }

    private fun formatCategoryTotals(expenses: List<ExpenseEntity>): String {
        return categoryTotals(expenses).joinToString("\n") { (categoryName, total) ->
            val category = Category.fromDisplayName(categoryName)
            "${category.emoji} ${category.displayName}: ${numberFormat.format(total)}원"
        }
    }

    /**
     * Gemini가 요청한 쿼리를 실행하여 결과 반환
     */
    suspend fun execute(query: DataQuery): QueryResult? {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA)

        // 전체 기간이 필요한 쿼리 타입 (날짜 없으면 epoch 0부터)
        val needsFullRange = query.type in listOf(
            QueryType.MONTHLY_TOTALS, QueryType.CARD_LIST, QueryType.MONTHLY_INCOME,
            QueryType.DUPLICATE_LIST, QueryType.SMS_EXCLUSION_LIST
        )
        val (defaultStartTimestamp, defaultEndTimestamp) =
            getDefaultQueryDateRange(needsFullRange)

        // 날짜 파싱 (없으면 이번 달 기본값, 전체 기간 필요한 쿼리는 0L)
        val startTimestamp = query.startDate?.let {
            try {
                dateFormat.parse(it)?.time ?: 0L
            } catch (e: Exception) {
                0L
            }
        } ?: defaultStartTimestamp

        val endTimestamp = query.endDate?.let {
            try {
                // 종료일은 해당 일의 끝까지 포함
                (dateFormat.parse(it)?.time
                    ?: System.currentTimeMillis()) + (24 * 60 * 60 * 1000 - 1)
            } catch (e: Exception) {
                System.currentTimeMillis()
            }
        } ?: defaultEndTimestamp
        val periodLabel = formatQueryPeriodLabel(
            dateFormat = dateFormat,
            startTimestamp = startTimestamp,
            endTimestamp = endTimestamp,
            query = query,
            isFullRangeDefault = needsFullRange && query.startDate.isNullOrBlank()
        )

        return when (query.type) {
            QueryType.TOTAL_EXPENSE -> {
                val expenses = getVisibleStatsExpensesByDateRange(startTimestamp, endTimestamp)
                val total = if (query.category != null) {
                    val cat = Category.fromDisplayName(query.category)
                    val categoryNames = cat.displayNamesIncludingSub
                    expenses.filter { it.category in categoryNames }.sumOf { it.amount }
                } else {
                    expenses.sumOf { it.amount }
                }
                val categoryLabel = query.category?.let {
                    val cat = Category.fromDisplayName(it)
                    val label = if (cat.subCategories.isNotEmpty()) "$it 하위 포함" else it
                    " ($label)"
                } ?: ""
                QueryResult(
                    queryType = QueryType.TOTAL_EXPENSE,
                    data = "총 지출$categoryLabel: ${numberFormat.format(total)}원 ($periodLabel)"
                )
            }

            QueryType.TOTAL_INCOME -> {
                val total = incomeRepository.getTotalIncomeByDateRange(startTimestamp, endTimestamp)
                QueryResult(
                    queryType = QueryType.TOTAL_INCOME,
                    data = "총 수입: ${numberFormat.format(total)}원 ($periodLabel)"
                )
            }

            QueryType.EXPENSE_BY_CATEGORY -> {
                val expenses = getVisibleStatsExpensesByDateRange(startTimestamp, endTimestamp)
                if (query.category != null) {
                    val cat = Category.fromDisplayName(query.category)
                    val categoryNames = cat.displayNamesIncludingSub
                    val filteredExpenses = expenses.filter { it.category in categoryNames }
                    val total = filteredExpenses.sumOf { it.amount }
                    val scopedLabel = if (cat.subCategories.isNotEmpty()) {
                        "${cat.displayName} (하위 포함)"
                    } else {
                        cat.displayName
                    }
                    val details = formatCategoryTotals(filteredExpenses)
                    val breakdown = if (details.isBlank()) {
                        "해당 기간 지출 내역이 없습니다."
                    } else if (categoryTotals(filteredExpenses).size > 1) {
                        "${cat.emoji} $scopedLabel: ${numberFormat.format(total)}원\n세부:\n$details"
                    } else {
                        "${cat.emoji} $scopedLabel: ${numberFormat.format(total)}원"
                    }
                    QueryResult(
                        queryType = QueryType.EXPENSE_BY_CATEGORY,
                        data = "카테고리 지출 ($scopedLabel) ($periodLabel):\n$breakdown"
                    )
                } else {
                    val breakdown = formatCategoryTotals(expenses)
                        .ifEmpty { "해당 기간 지출 내역이 없습니다." }
                    QueryResult(
                        queryType = QueryType.EXPENSE_BY_CATEGORY,
                        data = "카테고리별 지출 ($periodLabel):\n$breakdown"
                    )
                }
            }

            QueryType.EXPENSE_LIST -> {
                val limit = query.limit ?: 50
                val expenses = filterVisibleExpenses(
                    if (query.category != null) {
                        val cat = Category.fromDisplayName(query.category)
                        val categoryNames = cat.displayNamesIncludingSub
                        expenseRepository.getExpensesByCategoriesAndDateRangeOnce(
                            categoryNames,
                            startTimestamp,
                            endTimestamp
                        )
                    } else {
                        expenseRepository.getExpensesByDateRangeOnce(startTimestamp, endTimestamp)
                    },
                    statsOnly = true
                )
                    .take(limit)

                val expenseList = expenses.joinToString("\n") { expense ->
                    "${DateUtils.formatDateTime(expense.dateTime)} - ${expense.storeName}: ${
                        numberFormat.format(
                            expense.amount
                        )
                    }원 (${expense.category})${expense.memo?.let { " [메모: $it]" } ?: ""}"
                }.ifEmpty { "해당 기간 지출 내역이 없습니다." }

                QueryResult(
                    queryType = QueryType.EXPENSE_LIST,
                    data = "지출 내역 ($periodLabel):\n$expenseList"
                )
            }

            QueryType.DAILY_TOTALS -> {
                val dailyDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA)
                val dailyTotals = getVisibleStatsExpensesByDateRange(startTimestamp, endTimestamp)
                    .groupBy { dailyDateFormat.format(it.dateTime) }
                    .mapValues { (_, expenses) -> expenses.sumOf { it.amount } }
                    .toSortedMap()
                val totalsStr = dailyTotals.entries.joinToString("\n") { (date, total) ->
                    "$date: ${numberFormat.format(total)}원"
                }.ifEmpty { "해당 기간 일별 지출 내역이 없습니다." }

                QueryResult(
                    queryType = QueryType.DAILY_TOTALS,
                    data = "일별 지출 ($periodLabel):\n$totalsStr"
                )
            }

            QueryType.MONTHLY_TOTALS -> {
                val monthDateFormat = SimpleDateFormat("yyyy-MM", Locale.KOREA)
                val monthlyTotals = getVisibleStatsExpensesByDateRange(startTimestamp, endTimestamp)
                    .groupBy { monthDateFormat.format(it.dateTime) }
                    .mapValues { (_, expenses) -> expenses.sumOf { it.amount } }
                    .toSortedMap()
                val totalsStr = monthlyTotals.entries.joinToString("\n") { (month, total) ->
                    "$month: ${numberFormat.format(total)}원"
                }.ifEmpty { "월별 지출 내역이 없습니다." }

                QueryResult(
                    queryType = QueryType.MONTHLY_TOTALS,
                    data = "월별 지출:\n$totalsStr"
                )
            }

            QueryType.MONTHLY_INCOME -> {
                val income = settingsDataStore.getMonthlyIncome()
                QueryResult(
                    queryType = QueryType.MONTHLY_INCOME,
                    data = "설정된 월 수입: ${numberFormat.format(income)}원"
                )
            }

            QueryType.EXPENSE_BY_STORE -> {
                val storeName = query.storeName ?: return null

                // StoreAliasManager를 사용하여 모든 별칭으로 검색
                val aliases = StoreAliasManager.getAllAliases(storeName)
                val allExpenses = filterVisibleExpenses(aliases.flatMap { alias ->
                    expenseRepository.getExpensesByStoreNameContaining(alias)
                        .filter { it.dateTime in startTimestamp..endTimestamp }
                }.distinctBy { it.id }, statsOnly = true)
                    .sortedByDescending { it.dateTime }

                val total = allExpenses.sumOf { it.amount }
                val expenseList = allExpenses.take(10).joinToString("\n") { expense ->
                    "${DateUtils.formatDateTime(expense.dateTime)} - ${expense.storeName}: ${
                        numberFormat.format(
                            expense.amount
                        )
                    }원"
                }.ifEmpty { "해당 가게 지출 내역이 없습니다." }

                val aliasInfo = if (aliases.size > 1) " (${aliases.joinToString(", ")})" else ""

                QueryResult(
                    queryType = QueryType.EXPENSE_BY_STORE,
                    data = "'$storeName'$aliasInfo 지출 ($periodLabel):\n총 ${
                        numberFormat.format(
                            total
                        )
                    }원 (${allExpenses.size}건)\n$expenseList"
                )
            }

            QueryType.UNCATEGORIZED_LIST -> {
                val limit = query.limit ?: 20
                val expenses = filterVisibleExpenses(
                    expenseRepository.getUncategorizedExpenses(limit),
                    statsOnly = true
                )
                val expenseList = expenses.joinToString("\n") { expense ->
                    "[ID:${expense.id}] ${DateUtils.formatDateTime(expense.dateTime)} - ${expense.storeName}: ${
                        numberFormat.format(
                            expense.amount
                        )
                    }원"
                }.ifEmpty { "미분류 항목이 없습니다." }

                QueryResult(
                    queryType = QueryType.UNCATEGORIZED_LIST,
                    data = "미분류 항목 (${expenses.size}건):\n$expenseList"
                )
            }

            QueryType.CATEGORY_RATIO -> {
                val monthlyIncome = settingsDataStore.getMonthlyIncome()
                val allExpenses = getVisibleStatsExpensesByDateRange(startTimestamp, endTimestamp)
                val selectedCategory = query.category?.let { Category.fromDisplayName(it) }

                // category 필터가 있으면 해당 카테고리(+하위)만 필터링
                val categoryExpenses = if (selectedCategory != null) {
                    val cat = selectedCategory
                    val categoryNames = cat.displayNamesIncludingSub
                    allExpenses.filter { it.category in categoryNames }
                } else {
                    allExpenses
                }

                val totalExpense = allExpenses.sumOf { it.amount }  // 전체 지출 총액 (비율 계산용)

                val ratioBreakdown = if (selectedCategory != null) {
                    val categoryTotal = categoryExpenses.sumOf { it.amount }
                    val incomeRatio =
                        if (monthlyIncome > 0) (categoryTotal * 100.0 / monthlyIncome) else 0.0
                    val expenseRatio =
                        if (totalExpense > 0) (categoryTotal * 100.0 / totalExpense) else 0.0
                    val scopedLabel = if (selectedCategory.subCategories.isNotEmpty()) {
                        "${selectedCategory.displayName} (하위 포함)"
                    } else {
                        selectedCategory.displayName
                    }
                    val details = categoryTotals(categoryExpenses)
                        .joinToString("\n") { (categoryName, total) ->
                            val category = Category.fromDisplayName(categoryName)
                            "${category.emoji} ${category.displayName}: ${numberFormat.format(total)}원"
                        }
                    val summary = "${selectedCategory.emoji} $scopedLabel: ${
                        numberFormat.format(categoryTotal)
                    }원 (수입의 ${
                        String.format(Locale.KOREA, "%.1f", incomeRatio)
                    }%, 지출의 ${String.format(Locale.KOREA, "%.1f", expenseRatio)}%)"
                    if (details.isBlank()) {
                        "해당 기간 지출 내역이 없습니다."
                    } else if (categoryTotals(categoryExpenses).size > 1) {
                        "$summary\n세부:\n$details"
                    } else {
                        summary
                    }
                } else {
                    categoryTotals(categoryExpenses)
                        .joinToString("\n") { (categoryName, total) ->
                            val category = Category.fromDisplayName(categoryName)
                            val incomeRatio =
                                if (monthlyIncome > 0) (total * 100.0 / monthlyIncome) else 0.0
                            val expenseRatio =
                                if (totalExpense > 0) (total * 100.0 / totalExpense) else 0.0
                            "${category.emoji} ${category.displayName}: ${numberFormat.format(total)}원 (수입의 ${
                                String.format(
                                    Locale.KOREA,
                                    "%.1f",
                                    incomeRatio
                                )
                            }%, 지출의 ${String.format(Locale.KOREA, "%.1f", expenseRatio)}%)"
                        }.ifEmpty { "해당 기간 지출 내역이 없습니다." }
                }

                val totalIncomeRatio =
                    if (monthlyIncome > 0) (totalExpense * 100.0 / monthlyIncome) else 0.0
                val categoryLabel = query.category?.let { " ($it)" } ?: ""

                QueryResult(
                    queryType = QueryType.CATEGORY_RATIO,
                    data = "수입 대비 카테고리별 비율$categoryLabel ($periodLabel):\n월 수입: ${
                        numberFormat.format(
                            monthlyIncome
                        )
                    }원\n총 지출: ${numberFormat.format(totalExpense)}원 (수입의 ${
                        String.format(
                            Locale.KOREA,
                            "%.1f",
                            totalIncomeRatio
                        )
                    }%)\n\n$ratioBreakdown"
                )
            }

            QueryType.EXPENSE_BY_CARD -> {
                val cardName = query.cardName ?: query.storeName ?: return null
                val allExpenses =
                    getVisibleStatsExpensesByDateRange(startTimestamp, endTimestamp)
                        .filter { it.cardName.contains(cardName, ignoreCase = true) }
                        .sortedByDescending { it.dateTime }

                val total = allExpenses.sumOf { it.amount }
                val limit = query.limit ?: 20
                val expenseList = allExpenses.take(limit).joinToString("\n") { expense ->
                    "${DateUtils.formatDateTime(expense.dateTime)} - ${expense.storeName}: ${
                        numberFormat.format(
                            expense.amount
                        )
                    }원 (${expense.category})${expense.memo?.let { " [메모: $it]" } ?: ""}"
                }.ifEmpty { "해당 카드 지출 내역이 없습니다." }

                QueryResult(
                    queryType = QueryType.EXPENSE_BY_CARD,
                    data = "'$cardName' 카드 지출 ($periodLabel):\n총 ${
                        numberFormat.format(
                            total
                        )
                    }원 (${allExpenses.size}건)\n$expenseList"
                )
            }

            QueryType.SEARCH_EXPENSE -> {
                val keyword = query.searchKeyword ?: query.storeName ?: return null
                val limit = query.limit ?: 30
                val results = filterVisibleExpenses(expenseRepository.searchExpenses(keyword))
                    .take(limit)
                val resultList = results.joinToString("\n") { expense ->
                    "[ID:${expense.id}] ${DateUtils.formatDateTime(expense.dateTime)} - ${expense.storeName}: ${
                        numberFormat.format(
                            expense.amount
                        )
                    }원 (${expense.category}, ${expense.cardName})${expense.memo?.let { " [메모: $it]" } ?: ""}"
                }.ifEmpty { "'$keyword' 검색 결과가 없습니다." }

                QueryResult(
                    queryType = QueryType.SEARCH_EXPENSE,
                    data = "'$keyword' 검색 결과 (${results.size}건):\n$resultList"
                )
            }

            QueryType.CARD_LIST -> {
                val excludedCardNames = ownedCardRepository.getExcludedCardNames()
                val cardNames = expenseRepository.getAllCardNames()
                    .filterNot { cardName ->
                        CardVisibilityFilter.isExcluded(cardName, excludedCardNames)
                    }
                val cardList = cardNames.joinToString(", ").ifEmpty { "등록된 카드가 없습니다." }

                QueryResult(
                    queryType = QueryType.CARD_LIST,
                    data = "사용 중인 카드 목록 (${cardNames.size}개): $cardList"
                )
            }

            QueryType.INCOME_LIST -> {
                val limit = query.limit ?: 20
                val incomes =
                    incomeRepository.getIncomesByDateRangeOnce(startTimestamp, endTimestamp)
                        .take(limit)
                val total = incomes.sumOf { it.amount }
                val incomeList = incomes.joinToString("\n") { income ->
                    "${DateUtils.formatDateTime(income.dateTime)} - ${income.source}: ${
                        numberFormat.format(
                            income.amount
                        )
                    }원 (${income.type})${income.memo?.let { " [메모: $it]" } ?: ""}"
                }.ifEmpty { "해당 기간 수입 내역이 없습니다." }

                QueryResult(
                    queryType = QueryType.INCOME_LIST,
                    data = "수입 내역 ($periodLabel):\n총 ${
                        numberFormat.format(
                            total
                        )
                    }원 (${incomes.size}건)\n$incomeList"
                )
            }

            QueryType.DUPLICATE_LIST -> {
                val duplicates = filterVisibleExpenses(expenseRepository.getDuplicateExpenses())
                val dupList = duplicates.take(20).joinToString("\n") { expense ->
                    "[ID:${expense.id}] ${DateUtils.formatDateTime(expense.dateTime)} - ${expense.storeName}: ${
                        numberFormat.format(
                            expense.amount
                        )
                    }원 (${expense.category})"
                }.ifEmpty { "중복 항목이 없습니다." }

                QueryResult(
                    queryType = QueryType.DUPLICATE_LIST,
                    data = "중복 지출 항목 (${duplicates.size}건):\n$dupList"
                )
            }

            QueryType.SMS_EXCLUSION_LIST -> {
                val allKeywords = smsExclusionRepository.getAllKeywords()
                val keywordList = if (allKeywords.isEmpty()) {
                    "등록된 제외 키워드가 없습니다."
                } else {
                    allKeywords.joinToString("\n") { entity ->
                        val sourceLabel = when (entity.source) {
                            "default" -> "(기본)"
                            "chat" -> "(채팅)"
                            else -> "(사용자)"
                        }
                        "- ${entity.keyword} $sourceLabel"
                    }
                }

                QueryResult(
                    queryType = QueryType.SMS_EXCLUSION_LIST,
                    data = "SMS 제외 키워드 목록 (${allKeywords.size}건):\n$keywordList"
                )
            }

            QueryType.ANALYTICS -> {
                executeAnalytics(query, startTimestamp, endTimestamp, periodLabel)
            }

            QueryType.BUDGET_STATUS -> {
                executeBudgetStatusQuery(startTimestamp, endTimestamp)
            }
        }
    }

    private fun formatQueryPeriodLabel(
        dateFormat: SimpleDateFormat,
        startTimestamp: Long,
        endTimestamp: Long,
        query: DataQuery,
        isFullRangeDefault: Boolean
    ): String {
        val startLabel = query.startDate ?: if (isFullRangeDefault) {
            "전체"
        } else {
            dateFormat.format(Date(startTimestamp))
        }
        val endLabel = query.endDate ?: if (isFullRangeDefault) {
            "현재"
        } else {
            dateFormat.format(Date(endTimestamp))
        }
        return "$startLabel ~ $endLabel"
    }

    private suspend fun getDefaultQueryDateRange(needsFullRange: Boolean): Pair<Long, Long> {
        val now = System.currentTimeMillis()
        if (needsFullRange) return 0L to now

        val monthStartDay = withContext(Dispatchers.IO) {
            settingsDataStore.getMonthStartDay()
        }
        val (start, rawEnd) = DateUtils.getCurrentCustomMonthPeriod(monthStartDay)
        return start to minOf(rawEnd, now)
    }

    /**
     * BUDGET_STATUS 쿼리 실행: 카테고리별 예산 한도, 사용 금액, 잔여 금액 조회
     */
    private suspend fun executeBudgetStatusQuery(
        startTimestamp: Long,
        endTimestamp: Long
    ): QueryResult {
        // 기간에 포함된 모든 yearMonth 목록 생성
        val startCal = Calendar.getInstance().apply { timeInMillis = startTimestamp }
        val endCal = Calendar.getInstance().apply { timeInMillis = endTimestamp }

        val yearMonths = mutableListOf<String>()
        val iterCal = Calendar.getInstance().apply {
            set(Calendar.YEAR, startCal.get(Calendar.YEAR))
            set(Calendar.MONTH, startCal.get(Calendar.MONTH))
            set(Calendar.DAY_OF_MONTH, 1)
        }
        while (iterCal.get(Calendar.YEAR) < endCal.get(Calendar.YEAR) ||
            (iterCal.get(Calendar.YEAR) == endCal.get(Calendar.YEAR) &&
                iterCal.get(Calendar.MONTH) <= endCal.get(Calendar.MONTH))
        ) {
            yearMonths.add(
                String.format(
                    Locale.ROOT,
                    "%04d-%02d",
                    iterCal.get(Calendar.YEAR),
                    iterCal.get(Calendar.MONTH) + 1
                )
            )
            iterCal.add(Calendar.MONTH, 1)
        }

        val sb = StringBuilder()
        var hasBudgets = false

        // 예산은 "default"로 모든 월 공통 적용
        val budgets = budgetDao.getBudgetsByMonthOnce("default")
        if (budgets.isNotEmpty()) {
            hasBudgets = true
        }

        for (yearMonth in yearMonths) {
            if (!hasBudgets) break

            // 해당 월의 지출 조회 범위: 요청 범위와 월 범위의 교집합
            val ym = yearMonth.split("-")
            val year = ym[0].toInt()
            val month = ym[1].toInt()
            val monthStart = maxOf(startTimestamp, DateUtils.getMonthStartTimestamp(year, month))
            val monthEnd = minOf(endTimestamp, DateUtils.getMonthEndTimestamp(year, month))

            sb.appendLine("예산 현황 ($yearMonth):")
            val visibleExpenses = getVisibleStatsExpensesByDateRange(monthStart, monthEnd)
            for (budget in budgets) {
                val spent = if (budget.category == "전체") {
                    visibleExpenses.sumOf { it.amount }
                } else {
                    val cat = Category.fromDisplayName(budget.category)
                    val categoryNames = cat.displayNamesIncludingSub
                    visibleExpenses.filter { it.category in categoryNames }.sumOf { it.amount }
                }
                val remaining = budget.monthlyLimit - spent
                val status = if (remaining >= 0) "남음" else "초과"
                val absRemaining = abs(remaining)
                sb.appendLine(
                    "- ${budget.category}: 예산 ${numberFormat.format(budget.monthlyLimit)}원, " +
                        "사용 ${numberFormat.format(spent)}원, " +
                        "${numberFormat.format(absRemaining.toLong())}원 $status"
                )
            }
        }

        if (!hasBudgets) {
            return QueryResult(
                queryType = QueryType.BUDGET_STATUS,
                data = "설정된 예산이 없습니다. AI 채팅에서 \"식비 예산 20만원 설정해줘\"처럼 말하면 예산을 설정할 수 있습니다."
            )
        }

        return QueryResult(
            queryType = QueryType.BUDGET_STATUS,
            data = sb.toString().trimEnd()
        )
    }

    private suspend fun executeAnalytics(
        query: DataQuery,
        startTimestamp: Long,
        endTimestamp: Long,
        periodLabel: String
    ): QueryResult {
        return try {
            analyticsCalculator.calculate(
                query = query,
                sourceExpenses = getVisibleStatsExpensesByDateRange(startTimestamp, endTimestamp),
                periodLabel = periodLabel
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            MoneyTalkLogger.e("ANALYTICS 실행 오류: ${e.message}", e)
            QueryResult(
                queryType = QueryType.ANALYTICS,
                data = "[ANALYTICS 계산 결과]\n분석 실행 중 오류가 발생했습니다: ${e.message}"
            )
        }
    }

    /**
     * 기본 쿼리 결과 (쿼리 분석 실패 시 사용)
     */
    suspend fun getDefaultResults(): List<QueryResult> {
        val results = mutableListOf<QueryResult>()
        val (monthStart, monthEnd) = getDefaultQueryDateRange(needsFullRange = false)
        val visibleMonthExpenses = getVisibleStatsExpensesByDateRange(monthStart, monthEnd)

        // 이번 달 총 지출
        val totalExpense = visibleMonthExpenses.sumOf { it.amount }
        results.add(
            QueryResult(
                queryType = QueryType.TOTAL_EXPENSE,
                data = "이번 달 총 지출: ${numberFormat.format(totalExpense)}원"
            )
        )

        // 카테고리별 지출
        val breakdown = categoryTotals(visibleMonthExpenses)
            .joinToString("\n") { (categoryName, total) ->
                val category = Category.fromDisplayName(categoryName)
                "${category.emoji} ${category.displayName}: ${numberFormat.format(total)}원"
            }.ifEmpty { "지출 내역이 없습니다." }
        results.add(
            QueryResult(
                queryType = QueryType.EXPENSE_BY_CATEGORY,
                data = "이번 달 카테고리별 지출:\n$breakdown"
            )
        )

        // 최근 지출 10건
        val recentExpenses = filterVisibleExpenses(
            expenseRepository.getRecentExpenses(30),
            statsOnly = true
        )
            .take(10)
        val expenseList = recentExpenses.joinToString("\n") { expense ->
            "${DateUtils.formatDateTime(expense.dateTime)} - ${expense.storeName}: ${
                numberFormat.format(
                    expense.amount
                )
            }원"
        }.ifEmpty { "최근 지출 내역이 없습니다." }
        results.add(
            QueryResult(
                queryType = QueryType.EXPENSE_LIST,
                data = "최근 지출 내역:\n$expenseList"
            )
        )

        return results
    }

}
