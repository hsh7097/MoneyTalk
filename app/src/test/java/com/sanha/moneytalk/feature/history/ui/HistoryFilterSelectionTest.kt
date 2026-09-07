package com.sanha.moneytalk.feature.history.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryFilterSelectionTest {
    @Test
    fun selectingType_keepsItsCategoriesAndClearsOtherTypes() {
        val initial = HistoryFilterSelection(
            expenseCategories = setOf("식비"), incomeCategories = setOf("급여"),
            transferCategories = setOf("이체출금"), cardNames = setOf("신한"),
            sortOrder = SortOrder.AMOUNT_DESC, fixedExpenseFilter = FixedExpenseFilter.FIXED_ONLY
        )
        val selected = initial.selectType(FilterTransactionType.EXPENSE)

        assertEquals(listOf(FilterTransactionType.EXPENSE), selected.selectedTypes)
        assertEquals(setOf("식비"), selected.expenseCategories)
        assertTrue(selected.incomeCategories.isEmpty())
        assertTrue(selected.transferCategories.isEmpty())
        assertEquals(initial.cardNames, selected.cardNames)
        assertEquals(initial.sortOrder, selected.sortOrder)
        assertEquals(initial.fixedExpenseFilter, selected.fixedExpenseFilter)
        assertEquals(3, initial.selectedTypes.size)
    }

    @Test
    fun selectingOnlyActiveTypeAgain_returnsToAllTypesAndClearsCategories() {
        val selected = HistoryFilterSelection().selectType(FilterTransactionType.INCOME)
            .selectCategories(CategorySheetType.INCOME, setOf("급여"))

        assertTrue(selected.selectType(FilterTransactionType.INCOME).isDefault)
    }

    @Test
    fun firstNonemptyCategorySelection_collapsesAllTypesOnlyOnce() {
        val expense = HistoryFilterSelection().selectCategories(CategorySheetType.EXPENSE, setOf("식비"))
        val income = expense.selectCategories(CategorySheetType.INCOME, setOf("급여"))
        val transfer = income.selectCategories(CategorySheetType.TRANSFER, setOf("이체출금"))

        assertEquals(listOf(FilterTransactionType.EXPENSE), expense.selectedTypes)
        assertEquals(listOf(FilterTransactionType.EXPENSE, FilterTransactionType.INCOME), income.selectedTypes)
        assertEquals(3, transfer.selectedTypes.size)
        assertEquals(setOf("식비"), transfer.expenseCategories)
    }

    @Test
    fun emptyCategorySelection_doesNotCollapseDefaultTypes() {
        val all = HistoryFilterSelection().selectCategories(CategorySheetType.EXPENSE, emptySet())
        assertTrue(all.isDefault)
        assertEquals(
            listOf(FilterTransactionType.INCOME),
            all.selectCategories(CategorySheetType.INCOME, setOf("급여")).selectedTypes
        )
    }

    @Test
    fun selectAll_rearmsFirstCategoryCollapseButKeepsCardAndFixedSelection() {
        val all = HistoryFilterSelection(cardNames = setOf("삼성"), fixedExpenseFilter = FixedExpenseFilter.EXCLUDE_FIXED)
            .selectType(FilterTransactionType.EXPENSE).selectType(FilterTransactionType.ALL)
        val transfer = all.selectCategories(CategorySheetType.TRANSFER, setOf("이체출금"))

        assertEquals(listOf(FilterTransactionType.TRANSFER), transfer.selectedTypes)
        assertEquals(all.cardNames, transfer.cardNames)
        assertEquals(all.fixedExpenseFilter, transfer.fixedExpenseFilter)
        assertFalse(all.isDefault)
        assertTrue(HistoryFilterSelection().isDefault)
    }

    @Test
    fun openingAlreadyNarrowedFilter_doesNotCollapseNewTypeSelection() {
        val initial = HistoryFilterSelection(showExpenses = false, showTransfers = false)
        val changed = initial.selectCategories(CategorySheetType.EXPENSE, setOf("식비"))

        assertEquals(listOf(FilterTransactionType.EXPENSE, FilterTransactionType.INCOME), changed.selectedTypes)
    }
}
