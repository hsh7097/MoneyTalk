package com.sanha.moneytalk.feature.history

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sanha.moneytalk.R
import com.sanha.moneytalk.core.theme.MoneyTalkTheme
import com.sanha.moneytalk.core.theme.ThemeMode
import com.sanha.moneytalk.feature.history.ui.CalendarDay
import com.sanha.moneytalk.feature.history.ui.CalendarDayCell
import java.text.NumberFormat
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** A seven-column calendar must retain the sign and unit, including at large font scales. */
@RunWith(AndroidJUnit4::class)
class CalendarAmountLayoutTest {
    @get:Rule val compose = createComposeRule()

    private val amounts = listOf(
        2_656_780 to "265.7만",
        12_345_678 to "1,234.6만",
        Int.MAX_VALUE to "21.5억",
        9_999 to "9,999",
        10_000 to "1만",
        100_000_000 to "1억",
        99_999_999 to "10,000만"
    )

    @Test fun fullAmountsFitSevenColumnsAtLargeFontScale() {
        verifyAmounts(fontScale = 1.5f)
    }

    @Test fun fullAmountsFitSevenColumnsAtMaximumFontScale() {
        verifyAmounts(fontScale = 2f)
    }

    private fun verifyAmounts(fontScale: Float) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                MoneyTalkTheme(themeMode = ThemeMode.DARK) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Row(modifier = Modifier.width(320.dp).padding(horizontal = 16.dp)) {
                            amounts.forEachIndexed { index, (amount, _) ->
                                CalendarDayCell(
                                    calendarDay = CalendarDay(
                                        year = 2026,
                                        month = 9,
                                        day = index + 1,
                                        dateString = "2026-09-0${index + 1}",
                                        isCurrentPeriod = true,
                                        isFuture = false,
                                        isToday = false
                                    ),
                                    dayTotal = amount,
                                    dayIncome = amount,
                                    modifier = Modifier.weight(1f).height(160.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val numberFormat = NumberFormat.getNumberInstance(Locale.KOREA)
        amounts.forEach { (amount, compact) ->
            listOf("+", "-").forEach { sign ->
                val text = sign + compact
                val node = compose.onNodeWithText(text, useUnmergedTree = true).assertIsDisplayed()
                val layouts = mutableListOf<TextLayoutResult>()
                node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action ->
                    assertTrue("Text layout must be available for $text", action(layouts))
                }
                val layout = layouts.single()
                assertEquals(text, layout.layoutInput.text.text)
                assertEquals(1, layout.lineCount)
                assertEquals(fontScale, layout.layoutInput.density.fontScale, 0.001f)
                assertFalse("Amount and unit must not overflow: $text", layout.hasVisualOverflow)
                assertTrue(
                    "Entire amount must fit the actual cell viewport: $text",
                    layout.getLineRight(0) <= node.fetchSemanticsNode().boundsInRoot.width
                )
                compose.onNodeWithContentDescription(
                    sign + context.getString(R.string.common_won, numberFormat.format(amount)),
                    useUnmergedTree = true
                ).assertIsDisplayed()
            }
        }
    }
}
