package com.sanha.moneytalk.feature.history.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sanha.moneytalk.core.database.entity.ExpenseEntity
import com.sanha.moneytalk.core.database.entity.IncomeEntity
import com.sanha.moneytalk.feature.categorydetail.ui.CategorySortOrder
import com.sanha.moneytalk.feature.categorydetail.ui.CategoryTransactionListMapper
import com.sanha.moneytalk.feature.categorydetail.ui.model.CategoryTransactionItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Calendar

/** 목록의 표시 순서와 통계 정책을 실제 Android 문자열/날짜 환경에서 확인한다. DB는 변경하지 않는다. */
@RunWith(AndroidJUnit4::class)
class TransactionListMapperInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val history = HistoryTransactionListMapper(context)
    private val category = CategoryTransactionListMapper(context)

    @Test
    fun dateGroups_mergeTypesAndKeepExcludedRowsOutsideTotals() {
        val expenses = listOf(
            expense(1, 10, 100), expense(2, 11, 900).copy(isExcludedFromStats = true),
            expense(3, 12, 300).copy(transactionType = "TRANSFER", transferDirection = "DEPOSIT")
        )
        val result = build(expenses, listOf(income(1, 13, 500)))
        val header = result.first() as TransactionListItem.Header

        assertEquals(100, header.expenseTotal)
        assertEquals(800, header.incomeTotal)
        assertEquals(listOf("I1", "E3", "E2", "E1"), result.ids())
        assertTrue(header.title.isNotBlank())
    }

    @Test
    fun fixedAndTypeFilters_applyToExpenseIncomeAndTransferSeparately() {
        val expenses = listOf(expense(1, 10, 100).copy(isFixed = true), expense(2, 11, 200),
            expense(3, 12, 300).copy(isFixed = true, transactionType = "TRANSFER"))
        val incomes = listOf(income(1, 13, 400).copy(isRecurring = true), income(2, 14, 500))
        val fixed = history.build(expenses, incomes, SortOrder.DATE_DESC,
            showExpenses = true, showIncomes = true, showTransfers = false,
            fixedExpenseFilter = FixedExpenseFilter.FIXED_ONLY)

        assertEquals(listOf("I1", "E1"), fixed.ids())
        assertTrue(history.build(expenses, incomes, SortOrder.DATE_DESC, false, false, false).isEmpty())
    }

    @Test
    fun amountSort_mergesTypesAndKeepsAggregatePolicy() {
        val result = build(listOf(expense(1, 10, 100), expense(2, 11, 900).copy(isExcludedFromStats = true)),
            listOf(income(1, 13, 500)), SortOrder.AMOUNT_DESC)
        val header = result.first() as TransactionListItem.Header

        assertEquals(listOf("E2", "I1", "E1"), result.ids())
        assertEquals(100, header.expenseTotal)
        assertEquals(500, header.incomeTotal)
    }

    @Test
    fun storeSort_keepsExpenseGroupsBeforeIncomeSourceGroups() {
        val result = build(listOf(expense(1, 10, 100), expense(2, 11, 100).copy(storeName = "B"),
            expense(3, 12, 100)), listOf(income(1, 13, 500)), SortOrder.STORE_FREQ)

        assertEquals(listOf("E3", "E1", "E2", "I1"), result.ids())
        assertEquals(3, result.filterIsInstance<TransactionListItem.Header>().size)
    }

    @Test
    fun incomeOnly_keepsDateGroupingDespiteAmountSortSelection() {
        // Repository supplies newest-first income rows; grouping must retain that order.
        val result = history.build(emptyList(), listOf(income(2, 14, 100), income(1, 13, 500)),
            SortOrder.AMOUNT_DESC, false, true, false)
        assertEquals(listOf("I2", "I1"), result.ids())
        assertEquals(1, result.filterIsInstance<TransactionListItem.Header>().size)
    }

    @Test
    fun categoryGroups_keepExcludedRowsButSumOnlyIncludedExpenses() {
        val expenses = listOf(expense(1, 10, 100), expense(2, 11, 900).copy(isExcludedFromStats = true),
            expense(3, 12, 300).copy(transactionType = "TRANSFER", transferDirection = "DEPOSIT"))
        val result = category.build(expenses)

        assertEquals(100, (result.first() as CategoryTransactionItem.Header).expenseTotal)
        assertEquals(listOf(3L, 2L, 1L), result.filterIsInstance<CategoryTransactionItem.ExpenseItem>().map { it.expense.id })
        assertEquals(listOf(2L, 3L, 1L), category.build(expenses, CategorySortOrder.AMOUNT_DESC)
            .filterIsInstance<CategoryTransactionItem.ExpenseItem>().map { it.expense.id })
    }

    @Test
    fun dateGroups_separateLocalMidnightAndSortNewestDayFirst() {
        val before = expense(1, 23, 100).copy(dateTime = at(7, 23))
        val after = expense(2, 0, 200)
        val result = build(listOf(before, after), emptyList())
        assertEquals(listOf("E2", "E1"), result.ids())
        assertEquals(listOf(200, 100), result.filterIsInstance<TransactionListItem.Header>().map { it.expenseTotal })
    }

    private fun build(expenses: List<ExpenseEntity>, incomes: List<IncomeEntity>, sort: SortOrder = SortOrder.DATE_DESC) =
        history.build(expenses, incomes, sort, true, true, true)

    private fun List<TransactionListItem>.ids() = mapNotNull {
        when (it) {
            is TransactionListItem.ExpenseItem -> "E${it.expense.id}"
            is TransactionListItem.IncomeItem -> "I${it.income.id}"
            is TransactionListItem.Header -> null
        }
    }

    private fun expense(id: Long, hour: Int, amount: Int) = ExpenseEntity(
        id = id, amount = amount, storeName = "A", category = "식비", cardName = "신한",
        dateTime = at(8, hour), originalSms = "test", smsId = "mapper_$id"
    )

    private fun income(id: Long, hour: Int, amount: Int) = IncomeEntity(
        id = id, amount = amount, type = "급여", source = "회사", description = "test",
        isRecurring = false, dateTime = at(8, hour)
    )

    private fun at(day: Int, hour: Int): Long = Calendar.getInstance().apply {
        clear()
        set(2026, Calendar.SEPTEMBER, day, hour, 0, 0)
    }.timeInMillis
}
