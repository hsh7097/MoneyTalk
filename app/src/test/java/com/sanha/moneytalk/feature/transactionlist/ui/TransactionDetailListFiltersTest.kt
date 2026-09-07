package com.sanha.moneytalk.feature.transactionlist.ui

import androidx.lifecycle.SavedStateHandle
import com.sanha.moneytalk.core.database.entity.ExpenseEntity
import com.sanha.moneytalk.core.database.entity.IncomeEntity
import com.sanha.moneytalk.feature.history.ui.FixedExpenseFilter
import com.sanha.moneytalk.feature.history.ui.SortOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TransactionDetailListFiltersTest {
    @Test
    fun selectedCard_keepsOnlyMatchingExpensesAndHidesIncomes() {
        val expenses = listOf(
            expense(1, card = "우리카드"),
            expense(2, card = "우리"),
            expense(3, card = "KB국민"),
            expense(4, card = "현금")
        )

        val result = build(expenses, listOf(income(1)), TransactionDetailFilter(cardNames = setOf("우리")))

        assertEquals(listOf("E2", "E1"), result.ids())
    }

    @Test
    fun transactionTypesAndExactCategories_areIndependent() {
        val expenses = listOf(
            expense(1, category = "식비"),
            expense(2, category = "외식"),
            expense(3, category = "이체출금", transfer = true),
            expense(4, category = "이체입금", transfer = true)
        )
        val incomes = listOf(income(1, category = "급여"), income(2, category = "환급"))
        val filter = TransactionDetailFilter(
            expenseCategories = setOf("식비"),
            transferCategories = setOf("이체출금"),
            incomeCategories = setOf("급여")
        )

        assertEquals(listOf("E3", "E1", "I1"), build(expenses, incomes, filter).ids())
        assertEquals(listOf("I1"), build(expenses, incomes, filter.copy(showExpenses = false, showTransfers = false)).ids())
        assertEquals(listOf("E1"), build(expenses, incomes, filter.copy(showIncomes = false, showTransfers = false)).ids())
    }

    @Test
    fun fixedFilter_usesExpenseFixedAndIncomeRecurring() {
        val expenses = listOf(expense(1, fixed = true), expense(2))
        val incomes = listOf(income(3, recurring = true), income(4))
        val fixed = TransactionDetailFilter(fixedExpenseFilter = FixedExpenseFilter.FIXED_ONLY)

        assertEquals(listOf("I3", "E1"), build(expenses, incomes, fixed).ids())
        assertEquals(
            listOf("I4", "E2"),
            build(expenses, incomes, fixed.copy(fixedExpenseFilter = FixedExpenseFilter.EXCLUDE_FIXED)).ids()
        )
    }

    @Test
    fun displayPolicy_removesSmsKeywordsAndHiddenCardsButKeepsStatsExcludedRecords() {
        val expenses = listOf(
            expense(1).copy(isExcludedFromStats = true),
            expense(2).copy(originalSms = "SPAM 거래"),
            expense(3, card = "현대카드")
        )
        val incomes = listOf(income(4), income(5).copy(originalSms = "SPAM 입금"))

        val result = TransactionDetailListFilters.buildItems(
            expenses, incomes, TransactionDetailFilter(), setOf("spam"), setOf("현대")
        )

        assertEquals(listOf("I4", "E1"), result.ids())
        assertEquals(true, (result.last() as TransactionDetailListItem.Expense).expense.isExcludedFromStats)
    }

    @Test
    fun dateAndAmountSort_mergeExpenseAndIncomeRows() {
        val expenses = listOf(expense(1).copy(amount = 300), expense(3).copy(amount = 100))
        val incomes = listOf(income(2).copy(amount = 200))

        assertEquals(listOf("E3", "I2", "E1"), build(expenses, incomes, TransactionDetailFilter()).ids())
        assertEquals(
            listOf("E1", "I2", "E3"),
            build(expenses, incomes, TransactionDetailFilter(sortOrder = SortOrder.AMOUNT_DESC)).ids()
        )
    }

    @Test
    fun storeFrequencySort_groupsExpensesBeforeIncomeSources() {
        val expenses = listOf(
            expense(1).copy(storeName = "가게 A"),
            expense(2).copy(storeName = "가게 B"),
            expense(3).copy(storeName = "가게 A")
        )
        val incomes = listOf(income(4).copy(source = "회사"), income(5).copy(source = "회사"))

        assertEquals(
            listOf("E3", "E1", "E2", "I5", "I4"),
            build(expenses, incomes, TransactionDetailFilter(sortOrder = SortOrder.STORE_FREQ)).ids()
        )
    }

    @Test
    fun incomeOnlyMode_keepsHistoryDateOrderEvenWhenAmountSortWasSelected() {
        val incomes = listOf(income(1).copy(amount = 500), income(2).copy(amount = 100))
        val filter = TransactionDetailFilter(showExpenses = false, showTransfers = false, sortOrder = SortOrder.AMOUNT_DESC)

        assertEquals(listOf("I2", "I1"), build(emptyList(), incomes, filter).ids())
    }

    @Test
    fun dateOnlyEntry_preservesIncomeFirstOrderAndExistingVisibilityRules() {
        val result = TransactionDetailListFilters.buildItems(
            listOf(expense(3).copy(originalSms = "spam"), expense(4, card = "현대")),
            listOf(income(1), income(2)),
            null,
            setOf("spam"),
            setOf("현대")
        )

        assertEquals(listOf("I2", "I1", "E3"), result.ids())
    }

    @Test
    fun savedState_restoresEveryFilterFromPrimitiveExtras() {
        val state = SavedStateHandle(mapOf(
            TransactionDetailFilter.EXTRA_FILTER_ENABLED to true,
            TransactionDetailFilter.EXTRA_SORT_ORDER to "AMOUNT_DESC",
            TransactionDetailFilter.EXTRA_SHOW_EXPENSES to false,
            TransactionDetailFilter.EXTRA_SHOW_INCOMES to false,
            TransactionDetailFilter.EXTRA_SHOW_TRANSFERS to true,
            TransactionDetailFilter.EXTRA_EXPENSE_CATEGORIES to arrayListOf("식비"),
            TransactionDetailFilter.EXTRA_INCOME_CATEGORIES to arrayListOf("급여"),
            TransactionDetailFilter.EXTRA_TRANSFER_CATEGORIES to arrayListOf("이체출금"),
            TransactionDetailFilter.EXTRA_CARD_NAMES to arrayListOf("우리"),
            TransactionDetailFilter.EXTRA_FIXED_FILTER to "FIXED_ONLY"
        ))

        assertEquals(
            TransactionDetailFilter(
                sortOrder = SortOrder.AMOUNT_DESC,
                showExpenses = false,
                showIncomes = false,
                showTransfers = true,
                expenseCategories = setOf("식비"),
                incomeCategories = setOf("급여"),
                transferCategories = setOf("이체출금"),
                cardNames = setOf("우리"),
                fixedExpenseFilter = FixedExpenseFilter.FIXED_ONLY
            ),
            TransactionDetailFilter.fromSavedStateHandle(state)
        )
    }

    @Test
    fun savedState_dateOnlyAndUnknownEnumValuesUseCompatibleDefaults() {
        assertNull(TransactionDetailFilter.fromSavedStateHandle(SavedStateHandle(mapOf("extra_date" to "2026-09-07"))))
        val state = SavedStateHandle(mapOf(
            TransactionDetailFilter.EXTRA_FILTER_ENABLED to true,
            TransactionDetailFilter.EXTRA_SORT_ORDER to "unknown",
            TransactionDetailFilter.EXTRA_FIXED_FILTER to "unknown"
        ))
        assertEquals(TransactionDetailFilter(), TransactionDetailFilter.fromSavedStateHandle(state))
    }

    private fun build(expenses: List<ExpenseEntity>, incomes: List<IncomeEntity>, filter: TransactionDetailFilter) =
        TransactionDetailListFilters.buildItems(expenses, incomes, filter, emptySet(), emptySet())

    private fun List<TransactionDetailListItem>.ids(): List<String> = map {
        when (it) {
            is TransactionDetailListItem.Expense -> "E${it.expense.id}"
            is TransactionDetailListItem.Income -> "I${it.income.id}"
        }
    }

    private fun expense(
        id: Long,
        card: String = "우리",
        category: String = "식비",
        fixed: Boolean = false,
        transfer: Boolean = false
    ) = ExpenseEntity(
        id = id,
        amount = 100,
        storeName = "가게",
        category = category,
        cardName = card,
        dateTime = id,
        originalSms = "정상 거래",
        smsId = "sms_$id",
        isFixed = fixed,
        transactionType = if (transfer) "TRANSFER" else "EXPENSE"
    )

    private fun income(id: Long, category: String = "급여", recurring: Boolean = false) =
        IncomeEntity(
            id = id,
            amount = 100,
            type = "입금",
            description = "입금",
            isRecurring = recurring,
            dateTime = id,
            category = category
        )
}
