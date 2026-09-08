package com.sanha.moneytalk.core.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class TransactionSemanticPaletteTest {
    @Test
    fun historicalIncomeAndExpenseColorsAreRestored() {
        assertEquals(Color(0xFF137FEC), IncomeLight)
        assertEquals(Color(0xFF3AC977), IncomeDark)
        assertEquals(Color(0xFFEF4444), ExpenseColor)
        assertEquals(ExpenseColor, ExpenseLight)
        assertEquals(ExpenseColor, ExpenseDark)
    }

    @Test
    fun historicalLightAndDarkSurfaceAndActionRolesAreRestored() {
        assertEquals(Color(0xFF1B2838), Primary)
        assertEquals(Color(0xFFE8EDF2), PrimaryContainer)
        assertEquals(Color.White, OnPrimary)
        assertEquals(Color(0xFFF9FAFB), Background)
        assertEquals(Color.White, Surface)
        assertEquals(Color(0xFFF3F4F6), SurfaceVariant)
        assertEquals(Color(0xFF111827), OnSurface)
        assertEquals(Color(0xFF6B7280), OnSurfaceVariant)

        assertEquals(Color(0xFF4CAF50), DarkPrimary)
        assertEquals(Color(0xFF1B5E20), DarkPrimaryContainer)
        assertEquals(Color(0xFF171A1E), BackgroundDark)
        assertEquals(Color(0xFF252A30), SurfaceDark)
        assertEquals(Color(0xFF2D3239), SurfaceVariantDark)
        assertEquals(Color(0xFFECECEC), OnSurfaceDark)
        assertEquals(Color(0xFF6B7684), DarkGrey400)
    }
}
