package com.sanha.moneytalk.feature.transactionactions.ui

import com.sanha.moneytalk.core.model.CategoryType
import com.sanha.moneytalk.feature.transactionactions.model.QuickTransaction
import com.sanha.moneytalk.feature.transactionactions.model.TransactionQuickPatch
import com.sanha.moneytalk.feature.transactionactions.model.TransactionTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransactionQuickActionUiStateTest {
    @Test
    fun unloadedAndUnchangedTransactionsCannotSave() {
        assertFalse(TransactionQuickActionUiState().hasChanges)
        assertFalse(TransactionQuickActionUiState(target = TransactionTarget.Expense(1), isLoading = true).hasChanges)
        assertFalse(loaded().hasChanges)
        assertEquals(TransactionQuickPatch(), loaded().patch)
    }

    @Test
    fun patchContainsOnlyFieldsChangedByUser() {
        val state = loaded()
        assertEquals(TransactionQuickPatch(category = "카페"), state.copy(category = "카페").patch)
        assertEquals(TransactionQuickPatch(isFixed = true), state.copy(isFixed = true).patch)
        assertEquals(TransactionQuickPatch(isExcludedFromStats = true), state.copy(isExcludedFromStats = true).patch)
        assertTrue(state.copy(isFixed = true).hasChanges)
    }

    @Test
    fun revertingInputRemovesItFromPatch() {
        val state = loaded()
        val edited = state.copy(category = "카페", isFixed = true, isExcludedFromStats = true)
        assertTrue(edited.hasChanges)
        val reverted = edited.copy(category = state.category, isFixed = state.isFixed, isExcludedFromStats = state.isExcludedFromStats)
        assertFalse(reverted.hasChanges)
        assertEquals(TransactionQuickPatch(), reverted.patch)
    }

    @Test
    fun dialogLoadingAndErrorFlagsDoNotCreateDataChanges() {
        val state = loaded().copy(showCategories = true, confirmDelete = true, isSaving = true, isLoading = true, error = 1)
        assertFalse(state.hasChanges)
        assertEquals(TransactionQuickPatch(), state.patch)
    }

    @Test
    fun incomeHasNoStatsPatchAndFalseFixedValueIsNotLost() {
        val transaction = QuickTransaction(TransactionTarget.Income(1), "급여", 50_000, "급여", CategoryType.INCOME, true, null)
        val state = TransactionQuickActionUiState(target = transaction.target, transaction = transaction, category = transaction.category, isFixed = true)
        assertFalse(state.hasChanges)
        assertEquals(TransactionQuickPatch(isFixed = false), state.copy(isFixed = false).patch)
    }

    @Test
    fun switchingTargetStartsWithIndependentExpenseOrIncomeBaseline() {
        val expense = loaded().copy(isFixed = true)
        val income = QuickTransaction(TransactionTarget.Income(1), "수입", 10_000, "사용자 수입", CategoryType.INCOME, false, null)
        val state = TransactionQuickActionUiState(target = income.target, transaction = income, category = income.category)
        assertTrue(expense.hasChanges)
        assertFalse(state.hasChanges)
        assertEquals(TransactionQuickPatch(), state.patch)
    }

    private fun loaded(): TransactionQuickActionUiState {
        val transaction = QuickTransaction(TransactionTarget.Expense(1), "상점", 10_000, "식비", CategoryType.EXPENSE, false, false)
        return TransactionQuickActionUiState(
            target = transaction.target, transaction = transaction, category = transaction.category,
            isFixed = transaction.isFixed, isExcludedFromStats = transaction.isExcludedFromStats
        )
    }
}
