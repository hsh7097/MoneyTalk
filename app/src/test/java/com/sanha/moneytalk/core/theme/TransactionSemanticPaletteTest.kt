package com.sanha.moneytalk.core.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

class TransactionSemanticPaletteTest {
    @Test
    fun incomeIsRedAndExpenseIsBlueInBothModes() {
        listOf(IncomeLight, IncomeDark).forEach { color ->
            assertTrue(color.red > color.green && color.red > color.blue)
        }
        listOf(ExpenseLight, ExpenseDark).forEach { color ->
            assertTrue(color.blue > color.red && color.blue > color.green)
        }
    }

    @Test
    fun normalTransactionTextRemainsReadableOnSharedAndHomeSurfaces() {
        val lightSurfaces = listOf(Surface, SurfaceVariant, Color(0xFFF9FAFB), Color(0xFFF3F4F6))
        val darkSurfaces = listOf(SurfaceDark, SurfaceVariantDark, Color(0xFF252A30), Color(0xFF2D3239))
        listOf(IncomeLight, ExpenseLight).forEach { color ->
            lightSurfaces.forEach { surface -> assertTrue(contrast(color, surface) >= 4.5f) }
        }
        listOf(IncomeDark, ExpenseDark).forEach { color ->
            darkSurfaces.forEach { surface -> assertTrue(contrast(color, surface) >= 4.5f) }
        }
    }

    private fun contrast(foreground: Color, background: Color): Float {
        val first = foreground.luminance()
        val second = background.luminance()
        return (maxOf(first, second) + 0.05f) / (minOf(first, second) + 0.05f)
    }
}
