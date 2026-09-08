package com.sanha.moneytalk.core.ui.component.transaction.card

import com.sanha.moneytalk.core.database.entity.IncomeEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IncomeTransactionCardInfoTest {
    @Test
    fun categoryTagUsesSavedCategoryInsteadOfSmsIncomeType() {
        val info = IncomeTransactionCardInfo(income().copy(type = "입금", category = "급여"))
        assertEquals("급여", info.categoryTag)
        assertTrue(info.subtitle.startsWith("입금 | "))
    }

    @Test
    fun customAndUnclassifiedCategoriesRemainVisibleAsStored() {
        assertEquals("개인 부수입", IncomeTransactionCardInfo(income().copy(category = "개인 부수입")).categoryTag)
        assertEquals("미분류", IncomeTransactionCardInfo(income().copy(category = "미분류")).categoryTag)
    }

    @Test
    fun categoryEditUpdatesBadgeWhileOtherCardFieldsStayTheSame() {
        val original = income()
        val before = IncomeTransactionCardInfo(original)
        val after = IncomeTransactionCardInfo(original.copy(category = "상여금"))
        assertEquals("상여금", after.categoryTag)
        assertEquals(before.title, after.title)
        assertEquals(before.amount, after.amount)
        assertEquals(before.time, after.time)
        assertEquals(before.memoText, after.memoText)
        assertEquals(before.isFixed, after.isFixed)
        assertTrue(after.isIncome)
    }

    private fun income() = IncomeEntity(
        id = 1, amount = 50_000, type = "입금", source = "회사", description = "급여 입금",
        isRecurring = true, recurringDay = 25, dateTime = 1_783_332_000_000L,
        category = "급여", memo = "정기 수입"
    )
}
