package com.sanha.moneytalk.core.appfunctions

import android.content.Context
import com.sanha.moneytalk.R
import com.sanha.moneytalk.core.appfunctions.MoneyTalkAppFunctionInputRules.categoryNamesIncludingCustom
import com.sanha.moneytalk.core.appfunctions.MoneyTalkAppFunctionInputRules.coerceLimit
import com.sanha.moneytalk.core.appfunctions.MoneyTalkAppFunctionInputRules.parseDate
import com.sanha.moneytalk.core.database.CustomCategoryRepository
import com.sanha.moneytalk.core.database.OwnedCardRepository
import com.sanha.moneytalk.core.database.SmsExclusionRepository
import com.sanha.moneytalk.core.database.dao.BudgetDao
import com.sanha.moneytalk.core.database.entity.CustomCategoryEntity
import com.sanha.moneytalk.core.database.entity.ExpenseEntity
import com.sanha.moneytalk.core.database.entity.IncomeEntity
import com.sanha.moneytalk.core.database.entity.OwnedCardEntity
import com.sanha.moneytalk.core.database.entity.StoreRuleEntity
import com.sanha.moneytalk.core.datastore.SettingsDataStore
import com.sanha.moneytalk.core.util.DateUtils
import com.sanha.moneytalk.core.util.StoreAliasManager
import com.sanha.moneytalk.feature.home.data.ExpenseRepository
import com.sanha.moneytalk.feature.home.data.IncomeRepository
import com.sanha.moneytalk.feature.home.data.StoreRuleRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** App Functions의 저장 데이터 조회와 typed 응답 조합을 담당한다. */
@Singleton
class MoneyTalkChatAppFunctionReader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val expenseRepository: ExpenseRepository,
    private val incomeRepository: IncomeRepository,
    private val settingsDataStore: SettingsDataStore,
    private val smsExclusionRepository: SmsExclusionRepository,
    private val ownedCardRepository: OwnedCardRepository,
    private val storeRuleRepository: StoreRuleRepository,
    private val customCategoryRepository: CustomCategoryRepository,
    private val budgetDao: BudgetDao,
    private val analyticsCalculator: MoneyTalkAppFunctionAnalyticsCalculator
) {
    fun canExposeChatOperationAppFunctions(): MoneyTalkOperationResult {
        return MoneyTalkOperationResult(
            success = true,
            resultCode = RESULT_SUPPORTED,
            affectedCount = SUPPORTED_CHAT_OPERATION_COUNT,
            resourceId = NO_RESOURCE_ID
        )
    }

    suspend fun getDatabaseSnapshot(): MoneyTalkDatabaseSnapshot {
        val cards = ownedCardRepository.getAllCardsOnce()
        val storeRules = storeRuleRepository.getAllOnce()
        val customCategories = customCategoryRepository.getAll()
        return MoneyTalkDatabaseSnapshot(
            expenseCount = expenseRepository.getExpenseCount(),
            incomeCount = incomeRepository.getIncomeCount(),
            duplicateExpenseCount = expenseRepository.getDuplicateExpenses().size,
            cardCount = cards.size,
            ownedCardCount = cards.count { it.isOwned },
            excludedCardCount = cards.count { !it.isOwned },
            storeRuleCount = storeRules.size,
            customCategoryCount = customCategories.size,
            monthlyIncome = settingsDataStore.getMonthlyIncome(),
            monthStartDay = settingsDataStore.getMonthStartDay(),
            lastSyncTime = settingsDataStore.getLastSyncTime(),
            lastRcsProviderScanTime = settingsDataStore.getLastRcsProviderScanTime()
        )
    }

    suspend fun getTotalExpense(
        startDate: String?,
        endDate: String?,
        category: String?
    ): MoneyTalkAmountSummary {
        val range = parseDateRange(startDate, endDate)
        val expenses = getExpensesInRange(range)
        val filtered = category?.takeIf { it.isNotBlank() }?.let { filterByCategory(expenses, it) } ?: expenses
        return MoneyTalkAmountSummary(
            periodStart = range.start,
            periodEnd = range.end,
            totalAmount = filtered.sumOf { it.amount },
            transactionCount = filtered.size
        )
    }

    suspend fun getTotalIncome(startDate: String?, endDate: String?): MoneyTalkAmountSummary {
        val range = parseDateRange(startDate, endDate)
        val incomes = incomeRepository.getIncomesByDateRangeOnce(range.start, range.end)
        return MoneyTalkAmountSummary(
            periodStart = range.start,
            periodEnd = range.end,
            totalAmount = incomes.sumOf { it.amount },
            transactionCount = incomes.size
        )
    }

    suspend fun getExpenseCategoryTotals(
        startDate: String?,
        endDate: String?,
        category: String?
    ): MoneyTalkCategoryTotalsResponse {
        val range = parseDateRange(startDate, endDate)
        val expenses = getExpensesInRange(range)
        val filtered = category?.takeIf { it.isNotBlank() }?.let { filterByCategory(expenses, it) } ?: expenses
        val total = filtered.sumOf { it.amount }
        val categories = filtered.groupBy { it.category }
            .map { (categoryName, items) ->
                val amount = items.sumOf { it.amount }
                MoneyTalkCategoryTotal(
                    category = categoryName,
                    amount = amount,
                    expenseRatioPercentX100 = percentX100(amount, total)
                )
            }
            .sortedByDescending { it.amount }

        return MoneyTalkCategoryTotalsResponse(
            periodStart = range.start,
            periodEnd = range.end,
            totalAmount = total,
            categories = categories
        )
    }

    suspend fun getExpenses(
        startDate: String?,
        endDate: String?,
        category: String?,
        limit: Int?
    ): MoneyTalkExpenseListResponse {
        val range = parseDateRange(startDate, endDate)
        val expenses = getExpensesInRange(range)
            .let { items -> category?.takeIf { it.isNotBlank() }?.let { filterByCategory(items, it) } ?: items }
            .sortedByDescending { it.dateTime }
        return expenses.toExpenseListResponse(limit)
    }

    suspend fun getExpensesByStore(
        storeName: String,
        startDate: String?,
        endDate: String?,
        limit: Int?
    ): MoneyTalkExpenseListResponse {
        if (storeName.isBlank()) return emptyExpenseList()
        val range = parseDateRange(startDate, endDate)
        val expenses = StoreAliasManager.getAllAliases(storeName)
            .flatMap { alias -> expenseRepository.getExpensesByStoreNameContaining(alias) }
            .distinctBy { it.id }
            .filter { it.dateTime in range.start..range.end }
            .sortedByDescending { it.dateTime }
        return expenses.toExpenseListResponse(limit)
    }

    suspend fun getExpensesByCard(
        cardName: String,
        startDate: String?,
        endDate: String?,
        limit: Int?
    ): MoneyTalkExpenseListResponse {
        if (cardName.isBlank()) return emptyExpenseList()
        val range = parseDateRange(startDate, endDate)
        val expenses = getExpensesInRange(range)
            .filter { it.cardName.contains(cardName, ignoreCase = true) }
            .sortedByDescending { it.dateTime }
        return expenses.toExpenseListResponse(limit)
    }

    suspend fun getDailyExpenseTotals(
        startDate: String?,
        endDate: String?
    ): MoneyTalkDailyTotalsResponse {
        val range = parseDateRange(startDate, endDate)
        return MoneyTalkDailyTotalsResponse(
            days = expenseRepository.getDailyTotals(range.start, range.end)
                .map { MoneyTalkDailyTotal(date = it.date, amount = it.total) }
        )
    }

    suspend fun getMonthlyExpenseTotals(): MoneyTalkMonthlyTotalsResponse {
        return MoneyTalkMonthlyTotalsResponse(
            months = expenseRepository.getMonthlyTotals()
                .map { MoneyTalkMonthlyTotal(month = it.month, amount = it.total) }
        )
    }

    suspend fun getConfiguredMonthlyIncome(): MoneyTalkAmountSummary {
        val monthlyIncome = settingsDataStore.getMonthlyIncome()
        return MoneyTalkAmountSummary(
            periodStart = NO_TIMESTAMP,
            periodEnd = NO_TIMESTAMP,
            totalAmount = monthlyIncome,
            transactionCount = if (monthlyIncome > 0) 1 else 0
        )
    }

    suspend fun getUncategorizedExpenses(limit: Int?): MoneyTalkExpenseListResponse {
        val expenses = expenseRepository.getUncategorizedExpenses(coerceLimit(limit))
        return expenses.toExpenseListResponse(limit)
    }

    suspend fun getCategoryRatio(
        startDate: String?,
        endDate: String?,
        category: String?
    ): MoneyTalkCategoryRatioResponse {
        val range = parseDateRange(startDate, endDate)
        val monthlyIncome = settingsDataStore.getMonthlyIncome()
        val expenses = getExpensesInRange(range)
        val filtered = category?.takeIf { it.isNotBlank() }?.let { filterByCategory(expenses, it) } ?: expenses
        val totalExpense = expenses.sumOf { it.amount }

        val items = filtered.groupBy { it.category }
            .map { (categoryName, transactions) ->
                val amount = transactions.sumOf { it.amount }
                MoneyTalkCategoryRatioItem(
                    category = categoryName,
                    amount = amount,
                    incomeRatioPercentX100 = percentX100(amount, monthlyIncome),
                    expenseRatioPercentX100 = percentX100(amount, totalExpense)
                )
            }
            .sortedByDescending { it.amount }

        return MoneyTalkCategoryRatioResponse(
            monthlyIncome = monthlyIncome,
            totalExpense = totalExpense,
            totalIncomeRatioPercentX100 = percentX100(totalExpense, monthlyIncome),
            categories = items
        )
    }

    suspend fun searchExpenses(keyword: String, limit: Int?): MoneyTalkExpenseListResponse {
        if (keyword.isBlank()) return emptyExpenseList()
        val expenses = expenseRepository.searchExpenses(keyword)
        return expenses.toExpenseListResponse(limit)
    }

    suspend fun getUsedCards(): MoneyTalkStringListResponse {
        val cards = expenseRepository.getAllCardNames()
        return MoneyTalkStringListResponse(totalCount = cards.size, items = cards)
    }

    suspend fun getOwnedCards(): MoneyTalkCardListResponse {
        val cards = ownedCardRepository.getAllCardsOnce()
        return MoneyTalkCardListResponse(
            totalCount = cards.size,
            ownedCount = cards.count { it.isOwned },
            excludedCount = cards.count { !it.isOwned },
            cards = cards.map { it.toCardRecord() }
        )
    }

    suspend fun getStoreRules(): MoneyTalkStoreRuleListResponse {
        val rules = storeRuleRepository.getAllOnce()
        return MoneyTalkStoreRuleListResponse(
            totalCount = rules.size,
            rules = rules.map { it.toStoreRuleRecord() }
        )
    }

    suspend fun getCustomCategories(): MoneyTalkCustomCategoryListResponse {
        val categories = customCategoryRepository.getAll()
        return MoneyTalkCustomCategoryListResponse(
            totalCount = categories.size,
            categories = categories.map { it.toCustomCategoryRecord() }
        )
    }

    suspend fun getIncomes(
        startDate: String?,
        endDate: String?,
        limit: Int?
    ): MoneyTalkIncomeListResponse {
        val range = parseDateRange(startDate, endDate, fullRangeWhenMissing = true)
        val incomes = incomeRepository.getIncomesByDateRangeOnce(range.start, range.end)
            .sortedByDescending { it.dateTime }
        return incomes.toIncomeListResponse(limit)
    }

    suspend fun getDuplicateExpenses(limit: Int?): MoneyTalkExpenseListResponse {
        return expenseRepository.getDuplicateExpenses().toExpenseListResponse(limit)
    }

    suspend fun getSmsExclusionKeywords(): MoneyTalkSmsExclusionKeywordsResponse {
        val keywords = smsExclusionRepository.getAllKeywords()
            .map { MoneyTalkSmsExclusionKeyword(keyword = it.keyword, source = it.source) }
        return MoneyTalkSmsExclusionKeywordsResponse(
            totalCount = keywords.size,
            keywords = keywords
        )
    }

    suspend fun getBudgetStatus(
        startDate: String?,
        endDate: String?
    ): MoneyTalkBudgetStatusResponse {
        val range = parseDateRange(startDate, endDate)
        budgetDao.migrateToDefault()
        val budgets = budgetDao.getBudgetsByMonthOnce(DEFAULT_BUDGET_MONTH)
        if (budgets.isEmpty()) {
            return MoneyTalkBudgetStatusResponse(
                budgetConfigured = false,
                budgets = emptyList()
            )
        }

        val items = budgets.map { budget ->
            val spent = if (budget.category == TOTAL_BUDGET_CATEGORY) {
                expenseRepository.getTotalExpenseByDateRange(range.start, range.end)
            } else {
                val categories = categoryNamesIncludingCustom(budget.category)
                expenseRepository.getTotalExpenseByCategoriesAndDateRange(
                    categories,
                    range.start,
                    range.end
                )
            }
            MoneyTalkBudgetStatusItem(
                category = budget.category,
                budget = budget.monthlyLimit,
                spent = spent,
                remaining = budget.monthlyLimit - spent
            )
        }

        return MoneyTalkBudgetStatusResponse(
            budgetConfigured = true,
            budgets = items
        )
    }

    suspend fun analyzeExpenses(
        startDate: String?,
        endDate: String?,
        filters: List<MoneyTalkAnalyticsFilter>?,
        groupBy: String?,
        metrics: List<MoneyTalkAnalyticsMetric>?,
        topN: Int?,
        sort: String?
    ): MoneyTalkAnalyticsResponse {
        val range = parseDateRange(startDate, endDate)
        return analyticsCalculator.calculate(
            sourceExpenses = getExpensesInRange(range),
            filters = filters,
            groupBy = groupBy,
            metrics = metrics,
            topN = topN,
            sort = sort
        )
    }

    private suspend fun getExpensesInRange(range: TimestampRange): List<ExpenseEntity> {
        return expenseRepository.getExpensesByDateRangeOnce(range.start, range.end)
    }

    private fun filterByCategory(
        expenses: List<ExpenseEntity>,
        categoryName: String
    ): List<ExpenseEntity> {
        val categories = categoryNamesIncludingCustom(categoryName)
        return expenses.filter { it.category in categories }
    }

    private fun List<ExpenseEntity>.toExpenseListResponse(limit: Int?): MoneyTalkExpenseListResponse {
        val limited = take(coerceLimit(limit))
        return MoneyTalkExpenseListResponse(
            totalCount = size,
            returnedCount = limited.size,
            returnedAmount = limited.sumOf { it.amount },
            expenses = limited.map { it.toExpenseRecord() }
        )
    }

    private fun List<IncomeEntity>.toIncomeListResponse(limit: Int?): MoneyTalkIncomeListResponse {
        val limited = take(coerceLimit(limit))
        return MoneyTalkIncomeListResponse(
            totalCount = size,
            returnedCount = limited.size,
            returnedAmount = limited.sumOf { it.amount },
            incomes = limited.map { it.toIncomeRecord() }
        )
    }

    private fun ExpenseEntity.toExpenseRecord(): MoneyTalkExpenseRecord {
        return MoneyTalkExpenseRecord(
            id = id,
            storeName = storeName,
            amount = amount,
            category = category,
            cardName = cardName,
            dateMillis = dateTime,
            dateText = DateUtils.formatDateTime(dateTime),
            memo = memo.orEmpty(),
            isFixed = isFixed,
            isExcludedFromStats = isExcludedFromStats
        )
    }

    private fun IncomeEntity.toIncomeRecord(): MoneyTalkIncomeRecord {
        return MoneyTalkIncomeRecord(
            id = id,
            source = source,
            description = description,
            type = type,
            category = category,
            amount = amount,
            dateMillis = dateTime,
            dateText = DateUtils.formatDateTime(dateTime),
            memo = memo.orEmpty()
        )
    }

    private fun OwnedCardEntity.toCardRecord(): MoneyTalkCardRecord {
        return MoneyTalkCardRecord(
            cardName = cardName,
            isOwned = isOwned,
            firstSeenAt = firstSeenAt,
            lastSeenAt = lastSeenAt,
            seenCount = seenCount,
            source = source
        )
    }

    private fun StoreRuleEntity.toStoreRuleRecord(): MoneyTalkStoreRuleRecord {
        return MoneyTalkStoreRuleRecord(
            id = id,
            keyword = keyword,
            category = category.orEmpty(),
            categoryConfigured = category != null,
            fixedConfigured = isFixed != null,
            isFixed = isFixed ?: false,
            statsExcludedConfigured = isExcludedFromStats != null,
            isExcludedFromStats = isExcludedFromStats ?: false,
            createdAt = createdAt
        )
    }

    private fun CustomCategoryEntity.toCustomCategoryRecord(): MoneyTalkCustomCategoryRecord {
        return MoneyTalkCustomCategoryRecord(
            id = id,
            displayName = displayName,
            emoji = emoji,
            categoryType = categoryType,
            displayOrder = displayOrder,
            createdAt = createdAt
        )
    }

    private fun emptyExpenseList(): MoneyTalkExpenseListResponse {
        return MoneyTalkExpenseListResponse(
            totalCount = 0,
            returnedCount = 0,
            returnedAmount = 0,
            expenses = emptyList()
        )
    }

    private fun parseDateRange(
        startDate: String?,
        endDate: String?,
        fullRangeWhenMissing: Boolean = false
    ): TimestampRange {
        val start = startDate?.takeIf { it.isNotBlank() }?.let { parseDate(context, it, endOfDay = false) }
            ?: if (fullRangeWhenMissing) NO_TIMESTAMP else DateUtils.getMonthStartTimestamp()
        val end = endDate?.takeIf { it.isNotBlank() }?.let { parseDate(context, it, endOfDay = true) }
            ?: System.currentTimeMillis()
        if (start > end) throw IllegalArgumentException(context.getString(R.string.app_function_error_invalid_date))
        return TimestampRange(start = start, end = end)
    }

    private fun percentX100(value: Int, total: Int): Int {
        if (total <= 0) return 0
        return ((value.toLong() * PERCENT_X100) / total).toInt()
    }

    private data class TimestampRange(
        val start: Long,
        val end: Long
    )

    private companion object {
        private const val SUPPORTED_CHAT_OPERATION_COUNT = 48
        private const val NO_RESOURCE_ID = 0L
        private const val NO_TIMESTAMP = 0L
        private const val PERCENT_X100 = 10_000
        private const val RESULT_SUPPORTED = "supported"
        private const val DEFAULT_BUDGET_MONTH = "default"
        private const val TOTAL_BUDGET_CATEGORY = "전체"
    }
}
