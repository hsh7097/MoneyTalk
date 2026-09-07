package com.sanha.moneytalk.feature.home.ui.model

import com.sanha.moneytalk.core.database.dao.CategorySum
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeCategoryExpenseMapperTest {
    @Test
    fun ranking_sortsCategoriesButMergesUnclassifiedAtEnd() {
        val result = HomeCategoryExpenseMapper.build(
            listOf(CategorySum("식비", 100), CategorySum("미분류", 200),
                CategorySum("교통", 300), CategorySum("미분류", 400)), emptyMap()
        )
        assertEquals(listOf("교통", "식비", "미분류"), result.map { it.category })
        assertEquals(listOf(300, 100, 600), result.map { it.total })
        assertEquals(listOf(30, 10, 60), result.map { it.percentage })
    }

    @Test
    fun budgetProgress_preservesNinetyPercentWarningAndOverBudgetLimit() {
        val result = HomeCategoryExpenseMapper.build(
            listOf(CategorySum("A", 90), CategorySum("B", 100), CategorySum("C", 150)),
            mapOf("A" to 100, "B" to 100, "C" to 100)
        ).associateBy { it.category }
        assertTrue(result.getValue("A").isWarningBudget)
        assertTrue(result.getValue("B").isWarningBudget)
        assertFalse(result.getValue("B").isOverBudget)
        assertTrue(result.getValue("C").isOverBudget)
        assertFalse(result.getValue("C").isWarningBudget)
        assertEquals(150, result.getValue("C").percentage)
        assertEquals(1f, result.getValue("C").progress, 0f)
    }

    @Test
    fun zeroOrMissingBudget_usesSpendingShare() {
        val result = HomeCategoryExpenseMapper.build(
            listOf(CategorySum("A", 100), CategorySum("B", 300)), mapOf("A" to 0)
        )
        assertEquals(listOf(75, 25), result.map { it.percentage })
        assertTrue(result.none { it.hasBudget || it.isWarningBudget || it.isOverBudget })
    }

    @Test
    fun emptyAndZeroTotals_haveNoInvalidProgress() {
        assertTrue(HomeCategoryExpenseMapper.build(emptyList(), emptyMap()).isEmpty())
        val result = HomeCategoryExpenseMapper.build(
            listOf(CategorySum("A", 0), CategorySum("미분류", 0)), emptyMap()
        ).single()
        assertEquals(0, result.percentage)
        assertEquals(0f, result.progress, 0f)
    }
}
