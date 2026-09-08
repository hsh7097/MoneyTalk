package com.sanha.moneytalk.feature.categoryreview

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sanha.moneytalk.R
import com.sanha.moneytalk.core.database.entity.ExpenseEntity
import com.sanha.moneytalk.core.theme.MoneyTalkTheme
import com.sanha.moneytalk.core.theme.ThemeMode
import com.sanha.moneytalk.feature.categoryreview.ui.CategoryReviewContent
import com.sanha.moneytalk.feature.categoryreview.ui.CategoryReviewUiState
import com.sanha.moneytalk.feature.settings.ui.SettingsCategorySection
import com.sanha.moneytalk.feature.settings.ui.SettingsIntent
import com.sanha.moneytalk.feature.settings.ui.SettingsUiState
import com.sanha.moneytalk.feature.transactionedit.ui.TransactionEditActivity
import com.sanha.moneytalk.feature.transactionedit.ui.TransactionEditArgs
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CategoryReviewScreenTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun loadingErrorRetryAndEmptyAreDistinctStates() {
        val state = mutableStateOf<CategoryReviewUiState>(CategoryReviewUiState.Loading)
        var retries = 0
        compose.setContent {
            MoneyTalkTheme(themeMode = ThemeMode.LIGHT) {
                CategoryReviewContent(state.value, {}, { retries++ }, {})
            }
        }
        compose.onNodeWithTag("category_review_loading").assertIsDisplayed()
        compose.onNodeWithTag("category_review_list").assertDoesNotExist()
        compose.runOnIdle { state.value = CategoryReviewUiState.Error }
        compose.onNodeWithText(context.getString(R.string.category_review_error)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.category_review_retry)).performClick()
        compose.runOnIdle {
            assertEquals(1, retries)
            state.value = CategoryReviewUiState.Content.from(emptyList())
        }
        compose.onNodeWithText(context.getString(R.string.category_review_empty)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.category_review_error)).assertDoesNotExist()
    }

    @Test
    fun transactionOpensExpenseEditByIdAndListShowsDatesAcrossPeriods() {
        var clickedId = -1L
        val rows = listOf(expense(42), expense(41).copy(dateTime = 946_684_800_000L))
        compose.setContent {
            MoneyTalkTheme(themeMode = ThemeMode.LIGHT) {
                Box(Modifier.width(400.dp).fillMaxHeight()) {
                    CategoryReviewContent(CategoryReviewUiState.Content.from(rows), {}, {}, { clickedId = it })
                }
            }
        }
        compose.onNodeWithText(context.getString(R.string.category_review_count, 2)).assertIsDisplayed()
        compose.onNodeWithText(
            DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).format(LocalDate.of(2000, 1, 1))
        ).assertIsDisplayed()
        compose.onNodeWithTag("category_review_expense_41").assertIsDisplayed()
        compose.onNodeWithTag("category_review_expense_42").performClick()
        compose.runOnIdle {
            assertEquals(42L, clickedId)
            val intent = TransactionEditActivity.createIntent(context, expenseId = clickedId)
            assertEquals(42L, intent.getLongExtra(TransactionEditArgs.EXPENSE_ID, -1L))
            assertEquals(-1L, intent.getLongExtra(TransactionEditArgs.INCOME_ID, -1L))
        }
        capture("category-review-sample.png")
    }

    @Test
    fun darkLargeFontListKeepsLongTransactionAndAmountAccessible() {
        var clickedId = -1L
        val row = expense(99).copy(
            storeName = "합성 긴 거래처 이름을 가진 미정리 지출 내역",
            amount = Int.MAX_VALUE,
            isExcludedFromStats = true
        )
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                MoneyTalkTheme(themeMode = ThemeMode.DARK) {
                    Box(Modifier.width(320.dp).fillMaxHeight()) {
                        CategoryReviewContent(CategoryReviewUiState.Content.from(listOf(row)), {}, {}, { clickedId = it })
                    }
                }
            }
        }
        compose.onNodeWithTag("category_review_expense_99").assertIsDisplayed().performClick()
        compose.onNodeWithText("-" + context.getString(R.string.common_won, "2,147,483,647")).assertIsDisplayed()
        compose.runOnIdle { assertEquals(99L, clickedId) }
        capture("category-review-dark-large.png")
    }

    @Test
    fun directReviewAndAutomaticClassificationAreSeparateActionsAtLargeFont() {
        val state = mutableStateOf(SettingsUiState(
            unclassifiedCount = 3,
            isReviewCountLoading = false,
            hasApiKey = true
        ))
        var reviewClicks = 0
        val intents = mutableListOf<SettingsIntent>()
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                MoneyTalkTheme(themeMode = ThemeMode.DARK) {
                    Box(Modifier.width(320.dp)) {
                        SettingsCategorySection(state.value, { intents.add(it) }, { reviewClicks++ }, {}, {})
                    }
                }
            }
        }
        compose.onNodeWithTag("settings_category_review").performClick()
        compose.runOnIdle {
            assertEquals(1, reviewClicks)
            assertEquals(emptyList<SettingsIntent>(), intents)
        }
        compose.onNodeWithTag("settings_category_auto").performClick()
        compose.runOnIdle {
            assertEquals(listOf(SettingsIntent.ClassifyUnclassified), intents)
            state.value = state.value.copy(isBackgroundClassifying = true)
        }
        compose.onNodeWithTag("settings_category_auto").assertIsNotEnabled()
        compose.onNodeWithTag("settings_category_review").performClick()
        compose.runOnIdle { assertEquals(2, reviewClicks) }
    }

    private fun capture(name: String) {
        val screenshot = compose.onRoot().captureToImage().asAndroidBitmap()
        try {
            File(requireNotNull(context.getExternalFilesDir(null)), name)
                .outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally {
            screenshot.recycle()
        }
    }

    private fun expense(id: Long) = ExpenseEntity(
        id = id,
        amount = 12_000,
        storeName = "합성 미정리 거래 $id",
        category = "미분류",
        cardName = "합성 카드",
        dateTime = 1_788_880_000_000L,
        originalSms = "합성 원문",
        smsId = "category-review-ui-$id"
    )
}
