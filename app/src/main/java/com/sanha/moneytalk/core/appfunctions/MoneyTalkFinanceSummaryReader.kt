package com.sanha.moneytalk.core.appfunctions

import android.content.Context
import com.sanha.moneytalk.R
import com.sanha.moneytalk.core.database.SmsExclusionRepository
import com.sanha.moneytalk.core.database.dao.BudgetDao
import com.sanha.moneytalk.core.database.entity.ExpenseEntity
import com.sanha.moneytalk.core.database.entity.IncomeEntity
import com.sanha.moneytalk.core.datastore.SettingsDataStore
import com.sanha.moneytalk.core.model.TransferDirection
import com.sanha.moneytalk.core.util.DateUtils
import com.sanha.moneytalk.feature.home.data.ExpenseRepository
import com.sanha.moneytalk.feature.home.data.IncomeRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MoneyTalkFinanceSummaryReader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val expenseRepository: ExpenseRepository,
    private val incomeRepository: IncomeRepository,
    private val settingsDataStore: SettingsDataStore,
    private val smsExclusionRepository: SmsExclusionRepository,
    private val budgetDao: BudgetDao
) {
    suspend fun readMonthlySummary(year: Int?, month: Int?): MoneyTalkMonthlyFinanceSummary {
        val monthStartDay = settingsDataStore.getMonthStartDay().coerceIn(MIN_MONTH_START_DAY, MAX_MONTH_START_DAY)
        val (targetYear, targetMonth) = resolveTargetMonth(year, month, monthStartDay)
        validateTargetMonth(targetYear, targetMonth, monthStartDay)

        val (periodStart, periodEnd) = DateUtils.getCustomMonthPeriod(
            targetYear,
            targetMonth,
            monthStartDay
        )
        val (previousYear, previousMonth) = previousMonth(targetYear, targetMonth)
        val (previousStart, previousEnd) = DateUtils.getCustomMonthPeriod(
            previousYear,
            previousMonth,
            monthStartDay
        )
        val comparisonEnd = calculateComparisonEnd(periodStart, periodEnd, previousStart, previousEnd)

        val exclusionKeywords = smsExclusionRepository.getAllKeywordStrings()
        val expenses = expenseRepository.getExpensesByDateRangeOnce(periodStart, periodEnd)
            .filterNot { it.matchesAnyKeyword(exclusionKeywords) }
        val incomes = incomeRepository.getIncomesByDateRangeOnce(periodStart, periodEnd)
            .filterNot { it.matchesAnyKeyword(exclusionKeywords) }
        val previousExpenses = expenseRepository.getExpensesByDateRangeOnce(previousStart, comparisonEnd)
            .filterNot { it.matchesAnyKeyword(exclusionKeywords) }

        budgetDao.migrateToDefault()
        val budgets = budgetDao.getBudgetsByMonthOnce(DEFAULT_BUDGET_MONTH)
        val monthlyBudget = budgets.find { it.category == TOTAL_BUDGET_CATEGORY }?.monthlyLimit
        val categoryBudgets = budgets
            .filter { it.category != TOTAL_BUDGET_CATEGORY }
            .associate { it.category to it.monthlyLimit }

        val incomeTransactions = incomes.map { it.toRecentTransaction() }
        val expenseTransactions = expenses.map { it.toRecentTransaction() }
        val expenseLikeTransactions = expenses.filter { it.isExpenseOutflow() }
        val incomeLikeTransferTransactions = expenses.filter { it.isTransferDeposit() }

        val monthlyExpense = expenseLikeTransactions.sumOf { it.amount }
        val monthlyIncome = incomes.sumOf { it.amount } + incomeLikeTransferTransactions.sumOf { it.amount }
        val previousPeriodExpense = previousExpenses
            .filter { it.isExpenseOutflow() }
            .sumOf { it.amount }

        return MoneyTalkMonthlyFinanceSummary(
            summaryYear = targetYear,
            summaryMonth = targetMonth,
            monthStartDay = monthStartDay,
            usesCustomMonth = monthStartDay > DEFAULT_MONTH_START_DAY,
            periodLabel = DateUtils.formatCustomMonthPeriod(targetYear, targetMonth, monthStartDay),
            periodStart = periodStart,
            periodEnd = periodEnd,
            monthlyIncome = monthlyIncome,
            monthlyExpense = monthlyExpense,
            balance = monthlyIncome - monthlyExpense,
            budgetConfigured = monthlyBudget != null,
            monthlyBudget = monthlyBudget ?: 0,
            remainingBudget = monthlyBudget?.minus(monthlyExpense) ?: 0,
            expenseCount = expenseLikeTransactions.size,
            incomeCount = incomes.size + incomeLikeTransferTransactions.size,
            previousPeriodExpense = previousPeriodExpense,
            comparisonPeriodLabel = DateUtils.formatCustomMonthPeriod(previousYear, previousMonth, monthStartDay),
            comparisonExpenseDelta = monthlyExpense - previousPeriodExpense,
            topExpenseCategories = expenseLikeTransactions.toTopCategorySummaries(
                totalExpense = monthlyExpense,
                categoryBudgets = categoryBudgets
            ),
            recentTransactions = (incomeTransactions + expenseTransactions)
                .sortedByDescending { it.dateMillis }
                .take(RECENT_TRANSACTION_LIMIT)
        )
    }

    private fun resolveTargetMonth(
        year: Int?,
        month: Int?,
        monthStartDay: Int
    ): Pair<Int, Int> {
        val (currentYear, currentMonth) = DateUtils.getEffectiveCurrentMonth(monthStartDay)
        return Pair(year ?: currentYear, month ?: currentMonth)
    }

    private fun validateTargetMonth(year: Int, month: Int, monthStartDay: Int) {
        if (year < MIN_YEAR) {
            throw IllegalArgumentException(context.getString(R.string.app_function_error_invalid_year))
        }
        if (month !in MIN_MONTH..MAX_MONTH) {
            throw IllegalArgumentException(context.getString(R.string.app_function_error_invalid_month))
        }

        val (currentYear, currentMonth) = DateUtils.getEffectiveCurrentMonth(monthStartDay)
        val targetMonthIndex = year * MONTHS_PER_YEAR + month
        val currentMonthIndex = currentYear * MONTHS_PER_YEAR + currentMonth
        if (targetMonthIndex > currentMonthIndex) {
            throw IllegalArgumentException(context.getString(R.string.app_function_error_future_month))
        }
    }

    private fun previousMonth(year: Int, month: Int): Pair<Int, Int> {
        return if (month == MIN_MONTH) {
            Pair(year - 1, MAX_MONTH)
        } else {
            Pair(year, month - 1)
        }
    }

    private fun calculateComparisonEnd(
        periodStart: Long,
        periodEnd: Long,
        previousStart: Long,
        previousEnd: Long
    ): Long {
        val now = System.currentTimeMillis()
        val elapsed = if (now in periodStart..periodEnd) {
            now - periodStart
        } else {
            periodEnd - periodStart
        }
        return (previousStart + elapsed).coerceAtMost(previousEnd)
    }

    private fun List<ExpenseEntity>.toTopCategorySummaries(
        totalExpense: Int,
        categoryBudgets: Map<String, Int>
    ): List<MoneyTalkCategorySummary> {
        if (totalExpense <= 0) return emptyList()

        return groupBy { it.category }
            .map { (category, transactions) ->
                val amount = transactions.sumOf { it.amount }
                val categoryBudget = categoryBudgets[category]
                MoneyTalkCategorySummary(
                    category = category,
                    amount = amount,
                    percentage = ((amount.toDouble() / totalExpense) * PERCENT_TOTAL).toInt(),
                    budgetConfigured = categoryBudget != null,
                    budget = categoryBudget ?: 0,
                    remainingBudget = categoryBudget?.minus(amount) ?: 0
                )
            }
            .sortedByDescending { it.amount }
            .take(TOP_CATEGORY_LIMIT)
    }

    private fun ExpenseEntity.toRecentTransaction(): MoneyTalkRecentTransaction {
        val direction = TransferDirection.fromDbValue(transferDirection)
        val transactionType = when {
            this.transactionType == TRANSACTION_TYPE_TRANSFER &&
                direction == TransferDirection.DEPOSIT -> TRANSACTION_TYPE_TRANSFER_DEPOSIT
            this.transactionType == TRANSACTION_TYPE_TRANSFER -> TRANSACTION_TYPE_TRANSFER_WITHDRAWAL
            else -> TRANSACTION_TYPE_EXPENSE_LOWER
        }

        return MoneyTalkRecentTransaction(
            id = id,
            transactionType = transactionType,
            title = storeName,
            category = category,
            amount = amount,
            dateMillis = dateTime,
            dateText = formatDateTime(dateTime),
            paymentMethod = cardName
        )
    }

    private fun IncomeEntity.toRecentTransaction(): MoneyTalkRecentTransaction {
        return MoneyTalkRecentTransaction(
            id = id,
            transactionType = TRANSACTION_TYPE_INCOME_LOWER,
            title = source.ifBlank { description },
            category = category,
            amount = amount,
            dateMillis = dateTime,
            dateText = formatDateTime(dateTime),
            paymentMethod = source
        )
    }

    private fun ExpenseEntity.isExpenseOutflow(): Boolean {
        return transactionType != TRANSACTION_TYPE_TRANSFER || !isTransferDeposit()
    }

    private fun ExpenseEntity.isTransferDeposit(): Boolean {
        return transactionType == TRANSACTION_TYPE_TRANSFER &&
            transferDirection == TransferDirection.DEPOSIT.dbValue
    }

    private fun ExpenseEntity.matchesAnyKeyword(keywords: Set<String>): Boolean {
        if (keywords.isEmpty()) return false
        return listOf(storeName, category, cardName, originalSms, memo.orEmpty())
            .any { value -> value.containsAnyKeyword(keywords) }
    }

    private fun IncomeEntity.matchesAnyKeyword(keywords: Set<String>): Boolean {
        if (keywords.isEmpty()) return false
        return listOf(type, source, description, category, originalSms.orEmpty(), memo.orEmpty())
            .any { value -> value.containsAnyKeyword(keywords) }
    }

    private fun String.containsAnyKeyword(keywords: Set<String>): Boolean {
        val normalized = lowercase()
        return keywords.any { keyword -> normalized.contains(keyword) }
    }

    private fun formatDateTime(timestamp: Long): String {
        return DATE_TIME_FORMATTER.format(
            Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault())
        )
    }

    private companion object {
        private const val MIN_YEAR = 1970
        private const val MIN_MONTH = 1
        private const val MAX_MONTH = 12
        private const val MONTHS_PER_YEAR = 12
        private const val MIN_MONTH_START_DAY = 1
        private const val MAX_MONTH_START_DAY = 31
        private const val DEFAULT_MONTH_START_DAY = 1
        private const val DEFAULT_BUDGET_MONTH = "default"
        private const val TOTAL_BUDGET_CATEGORY = "전체"
        private const val TRANSACTION_TYPE_TRANSFER = "TRANSFER"
        private const val TRANSACTION_TYPE_EXPENSE_LOWER = "expense"
        private const val TRANSACTION_TYPE_INCOME_LOWER = "income"
        private const val TRANSACTION_TYPE_TRANSFER_WITHDRAWAL = "transfer_withdrawal"
        private const val TRANSACTION_TYPE_TRANSFER_DEPOSIT = "transfer_deposit"
        private const val TOP_CATEGORY_LIMIT = 5
        private const val RECENT_TRANSACTION_LIMIT = 10
        private const val PERCENT_TOTAL = 100

        private val DATE_TIME_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    }
}
