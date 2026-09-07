package com.sanha.moneytalk.feature.settings.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BudgetInputAmountsTest {

    @Test
    fun `percent mode recalculates saved amounts after total budget changes`() {
        val previousAmounts = mapOf("식비" to "300000", "교통" to "100000")
        val percents = mapOf("식비" to "30", "교통" to "10")

        assertEquals(
            mapOf("식비" to 600_000, "교통" to 200_000),
            resolveCategoryBudgetAmounts(2_000_000, true, previousAmounts, percents)
        )
        assertEquals(
            mapOf("식비" to 150_000, "교통" to 50_000),
            resolveCategoryBudgetAmounts(500_000, true, previousAmounts, percents)
        )
    }

    @Test
    fun `amount mode preserves exact amounts when total budget changes`() {
        assertEquals(
            mapOf("식비" to 333_333),
            resolveCategoryBudgetAmounts(
                totalBudget = 2_000_000,
                isPercentMode = false,
                categoryAmounts = mapOf("식비" to "333333"),
                categoryPercents = mapOf("식비" to "33")
            )
        )
    }

    @Test
    fun `missing total budget saves the displayed amount inputs`() {
        for (totalBudget in listOf(null, 0)) {
            assertEquals(
                mapOf("식비" to 350_000),
                resolveCategoryBudgetAmounts(
                    totalBudget = totalBudget,
                    isPercentMode = true,
                    categoryAmounts = mapOf("식비" to "350000"),
                    categoryPercents = mapOf("식비" to "30")
                )
            )
        }
    }

    @Test
    fun `percent mode does not restore cleared category amounts`() {
        assertEquals(
            emptyMap<String, Int>(),
            resolveCategoryBudgetAmounts(
                totalBudget = 1_000_000,
                isPercentMode = true,
                categoryAmounts = mapOf("식비" to "300000", "교통" to "100000"),
                categoryPercents = mapOf("식비" to "", "교통" to "0")
            )
        )
    }

    @Test
    fun `percent calculation retains whole won precision and avoids intermediate overflow`() {
        assertEquals(
            mapOf("식비" to 330_000),
            resolveCategoryBudgetAmounts(1_000_001, true, emptyMap(), mapOf("식비" to "33"))
        )
        assertEquals(
            mapOf("식비" to Int.MAX_VALUE),
            resolveCategoryBudgetAmounts(Int.MAX_VALUE, true, emptyMap(), mapOf("식비" to "100"))
        )
    }

    @Test
    fun `increasing total with an automatic percentage rejects overflow instead of dropping category`() {
        val amounts = mapOf("식비" to "2000000000")
        val percents = resolveCategoryBudgetPercents(1_000_000_000, amounts).orEmpty()

        assertEquals(mapOf("식비" to "200"), percents)
        assertEquals(
            mapOf("식비" to 2_000_000_000),
            resolveCategoryBudgetAmounts(1_000_000_000, true, amounts, percents)
        )
        assertNull(resolveCategoryBudgetAmounts(2_000_000_000, true, amounts, percents))
        assertNull(resolveBudgetPercentAmount(2_000_000_000, "200"))
    }

    @Test
    fun `small total converts large amounts without overflowing percentage or multiplication`() {
        val amounts = mapOf("식비" to Int.MAX_VALUE.toString())
        val percents = resolveCategoryBudgetPercents(1, amounts).orEmpty()

        assertEquals(mapOf("식비" to "214748364700"), percents)
        assertEquals(
            mapOf("식비" to Int.MAX_VALUE),
            resolveCategoryBudgetAmounts(1, true, amounts, percents)
        )
        assertNull(resolveCategoryBudgetAmounts(Int.MAX_VALUE, true, amounts, percents))
    }

    @Test
    fun `invalid direct amounts prevent saving and percent conversion without partial results`() {
        for (invalid in listOf("2147483648", "99999999999999999999999", "-1", "invalid")) {
            val amounts = mapOf("식비" to "100000", "교통" to invalid)
            assertNull(resolveCategoryBudgetAmounts(1_000_000, false, amounts, emptyMap()))
            assertNull(resolveCategoryBudgetPercents(1_000_000, amounts))
        }
    }

    @Test
    fun `invalid or excessive percentage does not silently clear an existing amount`() {
        for (invalid in listOf(Long.MAX_VALUE.toString(), "99999999999999999999999", "-1", "invalid")) {
            assertNull(
                resolveCategoryBudgetAmounts(
                    1_000_000,
                    true,
                    mapOf("식비" to "300000"),
                    mapOf("식비" to invalid)
                )
            )
        }
    }

    @Test
    fun `whole won truncation accepts representable boundary and rejects the next won`() {
        assertEquals(Int.MAX_VALUE, resolveBudgetPercentAmount(1, "214748364799"))
        assertNull(resolveBudgetPercentAmount(1, "214748364800"))
        assertEquals(1_500_000, resolveBudgetPercentAmount(1_000_000, "150"))
        assertNull(resolveCategoryBudgetAmounts(-1, false, emptyMap(), emptyMap()))
    }
}
