package com.sanha.moneytalk.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryHierarchyTest {

    @Test
    fun deliveryIsFoodSubcategoryButSelectableExpenseCategory() {
        assertEquals(Category.FOOD, Category.DELIVERY.parentCategory)
        assertEquals("배달", Category.DELIVERY.displayName)
        assertTrue(Category.expenseEntries.contains(Category.DELIVERY))
        assertFalse(Category.parentEntries.contains(Category.DELIVERY))
    }

    @Test
    fun foodIncludesDeliveryWhenSubcategoriesAreRequested() {
        assertEquals(listOf("식비", "배달"), Category.FOOD.displayNamesIncludingSub)
        assertEquals(listOf("배달"), Category.DELIVERY.displayNamesIncludingSub)
    }

    @Test
    fun deliveryDisplayNamesResolveToDeliveryCategory() {
        assertEquals(Category.DELIVERY, Category.fromDisplayName("배달"))
        assertEquals(Category.DELIVERY, Category.fromDisplayName("배달앱"))
    }
}
