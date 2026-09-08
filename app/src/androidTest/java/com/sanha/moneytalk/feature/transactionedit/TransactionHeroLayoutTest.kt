package com.sanha.moneytalk.feature.transactionedit

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import android.graphics.Bitmap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sanha.moneytalk.R
import com.sanha.moneytalk.core.theme.MoneyTalkTheme
import com.sanha.moneytalk.core.theme.ThemeMode
import com.sanha.moneytalk.feature.transactionedit.ui.TransactionEditUiState
import com.sanha.moneytalk.feature.transactionedit.ui.TransactionHeroCard
import java.io.File
import java.text.NumberFormat
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 금액 입력의 가로 스크롤로 부호/첫 자리가 숨는 문제를 실제 글자 폭으로 검증한다. */
@RunWith(AndroidJUnit4::class)
class TransactionHeroLayoutTest {
    @get:Rule val compose = createComposeRule()
    private val merchant = "Notification QA expense"

    @Test fun largeTextShowsWholeAmountBeforeAndAfterInputFocus() {
        var state by mutableStateOf(
            TransactionEditUiState(isLoading = false, storeName = merchant, amount = "123456789")
        )
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1.5f)) {
                MoneyTalkTheme(themeMode = ThemeMode.DARK) {
                    Box(modifier = Modifier.fillMaxSize().testTag("hero-fixture"), contentAlignment = Alignment.TopCenter) {
                        Box(modifier = Modifier.width(320.dp)) {
                            TransactionHeroCard(
                                uiState = state,
                                onStoreNameChange = { state = state.copy(storeName = it) },
                                onAmountChange = { state = state.copy(amount = it) },
                                onTypeChange = { state = state.copy(transactionType = it) }
                            )
                        }
                    }
                }
            }
        }

        val merchantNode = editable(merchant)
        val merchantLayout = layoutOf(merchantNode)
        assertTrue("Merchant should wrap naturally", merchantLayout.lineCount > 1)
        assertFalse("Merchant should fit its available lines", merchantLayout.hasVisualOverflow)
        assertAmountFits("123456789")

        editable(formattedAmount("123456789")).performClick().performTextReplacement("2147483647")
        compose.runOnIdle { assertEquals("2147483647", state.amount) }
        assertAmountFits("2147483647")

        merchantNode.performClick()
        assertAmountFits("2147483647")
        val amountBounds = editable(formattedAmount("2147483647")).fetchSemanticsNode().boundsInRoot
        val merchantBounds = merchantNode.fetchSemanticsNode().boundsInRoot
        assertTrue("Amount belongs below the merchant row", amountBounds.top >= merchantBounds.bottom)
        assertTrue("Amount uses the card width beyond the icon column", amountBounds.left < merchantBounds.left)
    }

    private fun assertAmountFits(amount: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val screenshot = compose.onNodeWithTag("hero-fixture").captureToImage().asAndroidBitmap()
        try {
            File(
                requireNotNull(instrumentation.targetContext.getExternalFilesDir(null)),
                "finance-ui-hero-$amount.png"
            ).outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally {
            screenshot.recycle()
        }
        val node = editable(formattedAmount(amount))
        val bounds = node.fetchSemanticsNode().boundsInRoot
        assertTrue("Amount has a visible viewport: $bounds", bounds.width > 0 && bounds.height > 0)
        node.assertIsDisplayed()
        val layout = layoutOf(node)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val expected = context.getString(R.string.transaction_edit_amount_prefix_expense) +
            NumberFormat.getNumberInstance(Locale.KOREA).format(amount.toLong()) +
            context.getString(R.string.transaction_edit_amount_suffix)
        assertEquals("Signed value must be retained", expected, layout.layoutInput.text.text)
        assertEquals(1.5f, layout.layoutInput.density.fontScale, 0.001f)
        assertTrue(
            "Entire signed amount must fit the input viewport; not merely its unconstrained layout",
            layout.getLineRight(0) <= node.fetchSemanticsNode().boundsInRoot.width
        )
    }

    private fun formattedAmount(amount: String): String {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return context.getString(R.string.transaction_edit_amount_prefix_expense) +
            NumberFormat.getNumberInstance(Locale.KOREA).format(amount.toLong()) +
            context.getString(R.string.transaction_edit_amount_suffix)
    }

    private fun editable(text: String) = compose.onNode(hasSetTextAction() and hasText(text))

    private fun layoutOf(node: SemanticsNodeInteraction): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action ->
            assertTrue(action(layouts))
        }
        assertEquals(1, layouts.size)
        return layouts.single()
    }
}
