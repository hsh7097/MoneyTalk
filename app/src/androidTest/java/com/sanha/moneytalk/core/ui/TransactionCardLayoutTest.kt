package com.sanha.moneytalk.core.ui

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sanha.moneytalk.R
import com.sanha.moneytalk.core.model.Category
import com.sanha.moneytalk.core.theme.MoneyTalkTheme
import com.sanha.moneytalk.core.theme.ThemeMode
import com.sanha.moneytalk.core.ui.component.transaction.card.TransactionCardCompose
import com.sanha.moneytalk.core.ui.component.transaction.card.TransactionCardInfo
import java.io.File
import java.text.NumberFormat
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 320dp 거래행에서 큰 글자·큰 금액·상태 표시의 실제 배치를 검증한다. DB는 수정하지 않는다. */
@RunWith(AndroidJUnit4::class)
class TransactionCardLayoutTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val merchant = "아주 긴 거래처 이름을 가진 정기 생활비 결제와 가족 공동 지출 확인"

    @Test fun largeAmountsAndStatusLabelsFitNarrowLightCards() {
        verifyCards(ThemeMode.LIGHT, "finance-ui-card-light-large-text.png")
    }

    @Test fun largeAmountsAndStatusLabelsFitNarrowDarkCards() {
        verifyCards(ThemeMode.DARK, "finance-ui-card-dark-large-text.png")
    }

    private fun verifyCards(themeMode: ThemeMode, screenshotName: String) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1.5f)) {
                MoneyTalkTheme(themeMode = themeMode) {
                    Box(
                        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            modifier = Modifier.width(320.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            listOf(false, true).forEach { income ->
                                TransactionCardCompose(
                                    info = fixture(income),
                                    onClick = {},
                                    onLongClick = {},
                                    modifier = Modifier.testTag(cardTag(income))
                                )
                            }
                        }
                    }
                }
            }
        }

        saveScreenshot(screenshotName)
        listOf(false, true).forEach { income ->
            val tag = cardTag(income)
            val amount = (if (income) "+" else "-") + context.getString(
                R.string.common_won,
                NumberFormat.getNumberInstance(Locale.KOREA).format(123_456_789)
            )
            compose.onNodeWithTag(tag).assertWidthIsEqualTo(320.dp).assertIsDisplayed()
            assertTextFits(tag, amount)
            assertTextFits(tag, context.getString(R.string.transaction_card_fixed_tag))
            assertTextFits(tag, context.getString(R.string.transaction_card_stats_excluded_tag))

            val titleBounds = textInCard(tag, merchant).fetchSemanticsNode().boundsInRoot
            val amountBounds = textInCard(tag, amount).fetchSemanticsNode().boundsInRoot
            assertTrue("Large amounts must be below the long merchant name", amountBounds.top >= titleBounds.bottom)
        }

    }

    private fun saveScreenshot(screenshotName: String) {
        compose.waitForIdle()
        val screenshot = compose.onRoot().captureToImage().asAndroidBitmap()
        try {
            File(requireNotNull(context.getExternalFilesDir(null)), screenshotName).outputStream().use {
                assertTrue("Screenshot should be saved", screenshot.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        } finally {
            screenshot.recycle()
        }
    }

    private fun assertTextFits(tag: String, text: String) {
        val results = mutableListOf<TextLayoutResult>()
        textInCard(tag, text).assertIsDisplayed()
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action ->
                assertTrue("Text layout must be available for $text", action(results))
            }
        assertEquals("Expected one rendered text layout", 1, results.size)
        val layout = results.single()
        assertEquals(1.5f, layout.layoutInput.density.fontScale, 0.001f)
        val lineWidths = (0 until layout.lineCount).map { index ->
            "${layout.getLineRight(index) - layout.getLineLeft(index)} (ellipsis=${layout.isLineEllipsized(index)})"
        }
        assertFalse(
            "Amount/status must not overflow or ellipsize: $text; " +
                "widthOverflow=${layout.didOverflowWidth}, heightOverflow=${layout.didOverflowHeight}, " +
                "size=${layout.size}, constraints=${layout.layoutInput.constraints}, " +
                "paragraphWidth=${layout.multiParagraph.width}, paragraphHeight=${layout.multiParagraph.height}, " +
                "lineCount=${layout.lineCount}, maxLines=${layout.layoutInput.maxLines}, " +
                "lineWidths=$lineWidths, fontSize=${layout.layoutInput.style.fontSize}, " +
                "bounds=${textInCard(tag, text).fetchSemanticsNode().boundsInRoot}",
            layout.hasVisualOverflow
        )
    }

    private fun textInCard(tag: String, text: String) = compose.onNode(
        hasText(text) and hasAnyAncestor(hasTestTag(tag)),
        useUnmergedTree = true
    )

    private fun cardTag(income: Boolean) = if (income) "income-card" else "expense-card"

    private fun fixture(income: Boolean) = object : TransactionCardInfo {
        override val title = merchant
        override val subtitle = "긴 카드명과 메타데이터"
        override val amount = 123_456_789
        override val isIncome = income
        override val category = if (income) null else Category.FOOD
        override val iconEmoji = if (income) "💰" else null
        override val categoryTag = if (income) "급여" else "식비"
        override val time = "18:30"
        override val cardNameText = "이름이 긴 테스트 카드의 가족 생활비 결제 내역"
        override val isFixed = true
        override val isExcludedFromStats = true
    }
}
