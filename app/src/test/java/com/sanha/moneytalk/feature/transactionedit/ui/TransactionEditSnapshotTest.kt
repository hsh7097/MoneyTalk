package com.sanha.moneytalk.feature.transactionedit.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class TransactionEditSnapshotTest {
    private val original = TransactionEditUiState(isLoading = false, amount = "12000", storeName = "가게")

    @Test fun ruleKeywordChangeIsAnUnsavedEdit() {
        assertNotEquals(original.toEditSnapshot(), original.copy(ruleKeyword = "다른 키워드").toEditSnapshot())
    }

    @Test fun bulkCategoryAndFixedChangesAreUnsavedEdits() {
        assertNotEquals(original.toEditSnapshot(), original.copy(applyCategoryToAll = true).toEditSnapshot())
        assertNotEquals(original.toEditSnapshot(), original.copy(applyFixedToAll = true).toEditSnapshot())
    }

    @Test fun openingPickersDoesNotChangeSavedInputs() {
        assertEquals(original.toEditSnapshot(), original.copy(showCategoryPicker = true,
            showAddCategoryDialog = true, addCategoryName = "아직 추가하지 않음").toEditSnapshot())
    }

    @Test fun returningToOriginalInputsRemovesDirtyState() {
        val edited = original.copy(amount = "24000", ruleKeyword = "가게")
        assertEquals(original.toEditSnapshot(), edited.copy(amount = original.amount,
            ruleKeyword = original.ruleKeyword).toEditSnapshot())
    }
}
