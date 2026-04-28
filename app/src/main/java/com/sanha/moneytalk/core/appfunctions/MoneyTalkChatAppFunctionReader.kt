package com.sanha.moneytalk.core.appfunctions

import android.content.Context
import com.sanha.moneytalk.R
import com.sanha.moneytalk.core.database.SmsExclusionRepository
import com.sanha.moneytalk.core.database.dao.BudgetDao
import com.sanha.moneytalk.core.database.entity.BudgetEntity
import com.sanha.moneytalk.core.database.entity.ExpenseEntity
import com.sanha.moneytalk.core.database.entity.IncomeEntity
import com.sanha.moneytalk.core.datastore.SettingsDataStore
import com.sanha.moneytalk.core.model.Category
import com.sanha.moneytalk.core.sms.DeletedSmsTracker
import com.sanha.moneytalk.core.util.CategoryReferenceProvider
import com.sanha.moneytalk.core.util.DataRefreshEvent
import com.sanha.moneytalk.core.util.DateUtils
import com.sanha.moneytalk.core.util.StoreAliasManager
import com.sanha.moneytalk.feature.home.data.ExpenseRepository
import com.sanha.moneytalk.feature.home.data.IncomeRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

@Singleton
class MoneyTalkChatAppFunctionReader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val expenseRepository: ExpenseRepository,
    private val incomeRepository: IncomeRepository,
    private val settingsDataStore: SettingsDataStore,
    private val smsExclusionRepository: SmsExclusionRepository,
    private val categoryReferenceProvider: CategoryReferenceProvider,
    private val dataRefreshEvent: DataRefreshEvent,
    private val budgetDao: BudgetDao
) {
    fun canExposeChatOperationAppFunctions(): MoneyTalkOperationResult {
        return MoneyTalkOperationResult(
            success = true,
            resultCode = RESULT_SUPPORTED,
            affectedCount = SUPPORTED_CHAT_OPERATION_COUNT,
            resourceId = NO_RESOURCE_ID
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
        var expenses = getExpensesInRange(range)
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

    suspend fun updateExpenseCategory(
        expenseId: Long?,
        newCategory: String?
    ): MoneyTalkOperationResult {
        if (expenseId == null || newCategory.isNullOrBlank()) {
            return failure(RESULT_MISSING_REQUIRED_PARAMETER)
        }
        val normalized = normalizeExpenseCategoryName(newCategory)
        val affected = expenseRepository.updateCategoryById(expenseId, normalized)
        if (affected > 0) {
            categoryReferenceProvider.invalidateCache()
            dataRefreshEvent.emit(DataRefreshEvent.RefreshType.CATEGORY_UPDATED)
        }
        return resultByAffected(affected, expenseId)
    }

    suspend fun updateExpenseCategoryByStore(
        storeName: String?,
        newCategory: String?
    ): MoneyTalkOperationResult {
        if (storeName.isNullOrBlank() || newCategory.isNullOrBlank()) {
            return failure(RESULT_MISSING_REQUIRED_PARAMETER)
        }
        return updateExpenseCategoryByAliases(storeName, newCategory)
    }

    suspend fun updateExpenseCategoryByKeyword(
        keyword: String?,
        newCategory: String?
    ): MoneyTalkOperationResult {
        if (keyword.isNullOrBlank() || newCategory.isNullOrBlank()) {
            return failure(RESULT_MISSING_REQUIRED_PARAMETER)
        }
        return updateExpenseCategoryByAliases(keyword, newCategory)
    }

    suspend fun deleteExpense(expenseId: Long?): MoneyTalkOperationResult {
        if (expenseId == null) return failure(RESULT_MISSING_REQUIRED_PARAMETER)
        val expense = expenseRepository.getExpenseById(expenseId) ?: return failure(RESULT_NOT_FOUND)
        DeletedSmsTracker.markDeleted(expense.smsId)
        expenseRepository.deleteById(expenseId)
        dataRefreshEvent.emit(DataRefreshEvent.RefreshType.TRANSACTION_ADDED)
        return MoneyTalkOperationResult(
            success = true,
            resultCode = RESULT_SUCCESS,
            affectedCount = 1,
            resourceId = expenseId
        )
    }

    suspend fun deleteExpensesByKeyword(keyword: String?): MoneyTalkOperationResult {
        if (keyword.isNullOrBlank()) return failure(RESULT_MISSING_REQUIRED_PARAMETER)
        val affected = expenseRepository.deleteByKeyword(keyword)
        if (affected > 0) dataRefreshEvent.emit(DataRefreshEvent.RefreshType.TRANSACTION_ADDED)
        return resultByAffected(affected)
    }

    suspend fun deleteDuplicateExpenses(): MoneyTalkOperationResult {
        val affected = expenseRepository.deleteDuplicates()
        if (affected > 0) dataRefreshEvent.emit(DataRefreshEvent.RefreshType.TRANSACTION_ADDED)
        return resultByAffected(affected)
    }

    suspend fun addExpense(
        storeName: String?,
        amount: Int?,
        date: String?,
        cardName: String?,
        category: String?,
        memo: String?
    ): MoneyTalkOperationResult {
        if (storeName.isNullOrBlank() || amount == null || amount <= 0) {
            return failure(RESULT_MISSING_REQUIRED_PARAMETER)
        }
        val dateTime = if (date.isNullOrBlank()) {
            System.currentTimeMillis()
        } else {
            parseDate(date, endOfDay = false)
        }
        val id = expenseRepository.insert(
            ExpenseEntity(
                storeName = storeName,
                amount = amount,
                dateTime = dateTime,
                cardName = cardName?.takeIf { it.isNotBlank() } ?: MANUAL_CARD_NAME,
                category = category?.takeIf { it.isNotBlank() }?.let(::normalizeExpenseCategoryName)
                    ?: UNCATEGORIZED_CATEGORY,
                originalSms = EMPTY_TEXT,
                smsId = "${MANUAL_SMS_PREFIX}${System.currentTimeMillis()}",
                memo = memo
            )
        )
        dataRefreshEvent.emit(DataRefreshEvent.RefreshType.TRANSACTION_ADDED)
        return MoneyTalkOperationResult(
            success = true,
            resultCode = RESULT_SUCCESS,
            affectedCount = 1,
            resourceId = id
        )
    }

    suspend fun updateExpenseMemo(
        expenseId: Long?,
        memo: String?
    ): MoneyTalkOperationResult {
        if (expenseId == null) return failure(RESULT_MISSING_REQUIRED_PARAMETER)
        val affected = expenseRepository.updateMemo(expenseId, memo)
        if (affected > 0) dataRefreshEvent.emit(DataRefreshEvent.RefreshType.TRANSACTION_ADDED)
        return resultByAffected(affected, expenseId)
    }

    suspend fun updateExpenseStoreName(
        expenseId: Long?,
        newStoreName: String?
    ): MoneyTalkOperationResult {
        if (expenseId == null || newStoreName.isNullOrBlank()) {
            return failure(RESULT_MISSING_REQUIRED_PARAMETER)
        }
        val affected = expenseRepository.updateStoreName(expenseId, newStoreName)
        if (affected > 0) dataRefreshEvent.emit(DataRefreshEvent.RefreshType.TRANSACTION_ADDED)
        return resultByAffected(affected, expenseId)
    }

    suspend fun updateExpenseAmount(
        expenseId: Long?,
        newAmount: Int?
    ): MoneyTalkOperationResult {
        if (expenseId == null || newAmount == null || newAmount <= 0) {
            return failure(RESULT_MISSING_REQUIRED_PARAMETER)
        }
        val affected = expenseRepository.updateAmount(expenseId, newAmount)
        if (affected > 0) dataRefreshEvent.emit(DataRefreshEvent.RefreshType.TRANSACTION_ADDED)
        return resultByAffected(affected, expenseId)
    }

    suspend fun addSmsExclusionKeyword(keyword: String?): MoneyTalkOperationResult {
        if (keyword.isNullOrBlank()) return failure(RESULT_MISSING_REQUIRED_PARAMETER)
        val added = smsExclusionRepository.addKeyword(keyword, source = SOURCE_APP_FUNCTION)
        return MoneyTalkOperationResult(
            success = added,
            resultCode = if (added) RESULT_SUCCESS else RESULT_ALREADY_EXISTS,
            affectedCount = if (added) 1 else 0,
            resourceId = NO_RESOURCE_ID
        )
    }

    suspend fun removeSmsExclusionKeyword(keyword: String?): MoneyTalkOperationResult {
        if (keyword.isNullOrBlank()) return failure(RESULT_MISSING_REQUIRED_PARAMETER)
        val affected = smsExclusionRepository.removeKeyword(keyword)
        return resultByAffected(affected)
    }

    suspend fun setBudget(category: String?, amount: Int?): MoneyTalkOperationResult {
        if (category.isNullOrBlank() || amount == null || amount < 0) {
            return failure(RESULT_MISSING_REQUIRED_PARAMETER)
        }
        val normalized = if (category == TOTAL_BUDGET_CATEGORY) {
            category
        } else {
            normalizeExpenseCategoryName(category)
        }
        budgetDao.insert(
            BudgetEntity(
                category = normalized,
                monthlyLimit = amount,
                yearMonth = DEFAULT_BUDGET_MONTH
            )
        )
        dataRefreshEvent.emit(DataRefreshEvent.RefreshType.TRANSACTION_ADDED)
        return MoneyTalkOperationResult(
            success = true,
            resultCode = RESULT_SUCCESS,
            affectedCount = 1,
            resourceId = NO_RESOURCE_ID
        )
    }

    private suspend fun updateExpenseCategoryByAliases(
        keyword: String,
        newCategory: String
    ): MoneyTalkOperationResult {
        val normalized = normalizeExpenseCategoryName(newCategory)
        val affected = StoreAliasManager.getAllAliases(keyword).sumOf { alias ->
            expenseRepository.updateCategoryByStoreNameContaining(alias, normalized)
        }
        if (affected > 0) {
            categoryReferenceProvider.invalidateCache()
            dataRefreshEvent.emit(DataRefreshEvent.RefreshType.CATEGORY_UPDATED)
        }
        return resultByAffected(affected)
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
            memo = memo.orEmpty()
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
        val start = startDate?.takeIf { it.isNotBlank() }?.let { parseDate(it, endOfDay = false) }
            ?: if (fullRangeWhenMissing) NO_TIMESTAMP else DateUtils.getMonthStartTimestamp()
        val end = endDate?.takeIf { it.isNotBlank() }?.let { parseDate(it, endOfDay = true) }
            ?: System.currentTimeMillis()
        if (start > end) throw IllegalArgumentException(context.getString(R.string.app_function_error_invalid_date))
        return TimestampRange(start = start, end = end)
    }

    private fun parseDate(date: String, endOfDay: Boolean): Long {
        return try {
            val parsed = DATE_FORMAT.parse(date)
                ?: throw IllegalArgumentException(context.getString(R.string.app_function_error_invalid_date))
            Calendar.getInstance().apply {
                timeInMillis = parsed.time
                set(Calendar.HOUR_OF_DAY, if (endOfDay) 23 else 0)
                set(Calendar.MINUTE, if (endOfDay) 59 else 0)
                set(Calendar.SECOND, if (endOfDay) 59 else 0)
                set(Calendar.MILLISECOND, if (endOfDay) 999 else 0)
            }.timeInMillis
        } catch (exception: Exception) {
            throw IllegalArgumentException(context.getString(R.string.app_function_error_invalid_date))
        }
    }

    private fun normalizeExpenseCategoryName(categoryName: String): String {
        val trimmed = categoryName.trim()
        val category = Category.fromDisplayName(trimmed)
        return if (category == Category.ETC && trimmed != Category.ETC.displayName) {
            trimmed
        } else {
            category.displayName
        }
    }

    private fun categoryNamesIncludingCustom(categoryName: String): List<String> {
        val trimmed = categoryName.trim()
        val category = Category.fromDisplayName(trimmed)
        return if (category == Category.ETC && trimmed != Category.ETC.displayName) {
            listOf(trimmed)
        } else {
            category.displayNamesIncludingSub
        }
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

    private fun percentX100(value: Int, total: Int): Int {
        if (total <= 0) return 0
        return ((value.toLong() * PERCENT_X100) / total).toInt()
    }

    private fun coerceLimit(limit: Int?, defaultLimit: Int = DEFAULT_LIMIT): Int {
        return max(limit ?: defaultLimit, MIN_LIMIT).coerceAtMost(MAX_LIMIT)
    }

    private fun resultByAffected(affected: Int, resourceId: Long = NO_RESOURCE_ID): MoneyTalkOperationResult {
        return MoneyTalkOperationResult(
            success = affected > 0,
            resultCode = if (affected > 0) RESULT_SUCCESS else RESULT_NOT_FOUND,
            affectedCount = affected,
            resourceId = resourceId
        )
    }

    private fun failure(resultCode: String): MoneyTalkOperationResult {
        return MoneyTalkOperationResult(
            success = false,
            resultCode = resultCode,
            affectedCount = 0,
            resourceId = NO_RESOURCE_ID
        )
    }

    private data class TimestampRange(
        val start: Long,
        val end: Long
    )

    private companion object {
        private const val SUPPORTED_CHAT_OPERATION_COUNT = 31
        private const val DEFAULT_LIMIT = 20
        private const val MIN_LIMIT = 1
        private const val MAX_LIMIT = 200
        private const val NO_RESOURCE_ID = 0L
        private const val NO_TIMESTAMP = 0L
        private const val PERCENT_X100 = 10_000
        private const val DATE_TEXT_LENGTH = 10
        private const val MONTH_TEXT_LENGTH = 7

        private const val RESULT_SUPPORTED = "supported"
        private const val RESULT_SUCCESS = "success"
        private const val RESULT_NOT_FOUND = "not_found"
        private const val RESULT_ALREADY_EXISTS = "already_exists"
        private const val RESULT_MISSING_REQUIRED_PARAMETER = "missing_required_parameter"

        private const val DEFAULT_BUDGET_MONTH = "default"
        private const val TOTAL_BUDGET_CATEGORY = "전체"
        private const val UNCATEGORIZED_CATEGORY = "미분류"
        private const val MANUAL_CARD_NAME = "수동입력"
        private const val MANUAL_SMS_PREFIX = "manual_app_function_"
        private const val SOURCE_APP_FUNCTION = "chat"
        private const val EMPTY_TEXT = ""

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

        private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).apply {
            isLenient = false
        }
    }
}
