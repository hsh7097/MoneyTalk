package com.sanha.moneytalk.core.ui

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sanha.moneytalk.R
import com.sanha.moneytalk.core.theme.ExpenseDark
import com.sanha.moneytalk.core.theme.ExpenseLight
import com.sanha.moneytalk.core.theme.IncomeDark
import com.sanha.moneytalk.core.theme.IncomeLight
import com.sanha.moneytalk.core.theme.MoneyTalkTheme
import com.sanha.moneytalk.core.theme.ThemeMode
import com.sanha.moneytalk.core.ui.component.transaction.card.TransactionCardCompose
import com.sanha.moneytalk.core.ui.component.transaction.card.TransactionCardInfo
import com.sanha.moneytalk.feature.home.ui.component.HomeTransactionCard
import com.sanha.moneytalk.feature.home.ui.component.MonthlyOverviewSection
import com.sanha.moneytalk.feature.home.ui.theme.HomeTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 실제 Text 레이아웃의 색을 검사한다. 거래/테마 DB는 변경하지 않는다. */
@RunWith(AndroidJUnit4::class)
class TransactionSemanticColorsTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun lightSharedAndHomeAmountsUseTheSameIncomeAndExpenseRoles() = verify(ThemeMode.LIGHT)
    @Test fun darkSharedAndHomeAmountsUseTheSameIncomeAndExpenseRoles() = verify(ThemeMode.DARK)

    private fun verify(theme: ThemeMode) {
        compose.setContent {
            MoneyTalkTheme(themeMode = theme) {
                CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
                    Column(
                        modifier = Modifier.width(400.dp)
                            .background(MaterialTheme.colorScheme.background)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TransactionCardCompose(fixture(1_200, income = true), {})
                        TransactionCardCompose(fixture(2_300, income = false), {})
                        HomeTheme {
                            HomeTransactionCard(fixture(3_400, income = true), {})
                            HomeTransactionCard(fixture(4_500, income = false), {})
                            HomeTransactionCard(fixture(5_100, income = true, excluded = true), {})
                            HomeTransactionCard(fixture(5_200, income = false, excluded = true), {})
                            MonthlyOverviewSection(2026, 9, 1, "9/1 ~ 9/30", 6_700, 5_600, {}, {})
                        }
                    }
                }
            }
        }
        val incomeColor = if (theme == ThemeMode.DARK) IncomeDark else IncomeLight
        val expenseColor = if (theme == ThemeMode.DARK) ExpenseDark else ExpenseLight
        assertColor("+" + won("1,200"), incomeColor)
        assertColor("-" + won("2,300"), expenseColor)
        assertColor("+" + won("3,400"), incomeColor)
        assertColor("-" + won("4,500"), expenseColor)
        val excludedColor = if (theme == ThemeMode.DARK) Color(0xFF6B7684) else Color(0xFF6B7280)
        assertColor("+" + won("5,100"), excludedColor.copy(alpha = 0.78f))
        assertColor("-" + won("5,200"), excludedColor.copy(alpha = 0.78f))
        assertColor(won("5,600"), Color.White)
        assertColor(context.getString(R.string.home_income_badge, "6,700"), Color.White.copy(alpha = 0.8f))
        val screenshot = compose.onRoot().captureToImage().asAndroidBitmap()
        try {
            File(requireNotNull(context.getExternalFilesDir(null)), "historical-colors-${theme.name.lowercase()}.png")
                .outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally {
            screenshot.recycle()
        }
    }

    private fun assertColor(text: String, expected: Color) {
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text, useUnmergedTree = true).performScrollTo()
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        assertEquals(expected, results.single().layoutInput.style.color)
    }

    private fun won(amount: String) = context.getString(R.string.common_won, amount)

    private fun fixture(value: Int, income: Boolean, excluded: Boolean = false) = object : TransactionCardInfo {
        override val title = "semantic fixture $value"
        override val subtitle = ""
        override val amount = value
        override val isIncome = income
        override val isExcludedFromStats = excluded
    }
}
