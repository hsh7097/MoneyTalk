package com.sanha.moneytalk.feature.history

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sanha.moneytalk.R
import com.sanha.moneytalk.core.model.Category
import com.sanha.moneytalk.core.theme.MoneyTalkTheme
import com.sanha.moneytalk.feature.history.ui.FilterTabRow
import com.sanha.moneytalk.feature.history.ui.SortOrder
import com.sanha.moneytalk.feature.history.ui.ViewMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 활성 필터의 재편집·취소·초기화를 검증한다. 앱 DB나 사용자 설정은 변경하지 않는다. */
@RunWith(AndroidJUnit4::class)
class HistoryFilterInteractionTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val initialCategories = setOf(Category.FOOD.displayName)
    private var resetCount = 0
    private var applyCount = 0
    private var appliedSort: SortOrder? = null
    private var appliedCategories: Set<String>? = null

    @Test fun activeFilterCanReopenAndCloseWithoutResetUntilDedicatedResetIsPressed() {
        showActiveFilter()

        openFilter()
        closeFilter()
        compose.onNodeWithTag("history_filter_action").assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.StateDescription,
                context.getString(R.string.history_filter_active_category)
            )
        )

        openFilter()
        closeFilter()
        compose.runOnIdle {
            assertEquals(0, resetCount)
            assertEquals(0, applyCount)
        }

        compose.onNodeWithContentDescription(context.getString(R.string.history_filter_reset))
            .performClick()
        compose.onNodeWithTag("history_filter_action").assert(
            !SemanticsMatcher.keyIsDefined(SemanticsProperties.StateDescription)
        )
        compose.onNodeWithText(context.getString(R.string.common_filter)).assertExists()
        compose.runOnIdle {
            assertEquals(1, resetCount)
            assertEquals(0, applyCount)
        }
    }

    @Test fun canceledSortChangeIsDiscardedAndApplyingPreservesExistingCategory() {
        showActiveFilter()

        openFilter()
        compose.onNodeWithText(context.getString(R.string.history_sort_amount_short))
            .performScrollTo().performClick()
        closeFilter()
        compose.runOnIdle { assertEquals(0, applyCount) }

        openFilter()
        compose.onNodeWithText(context.getString(R.string.common_apply)).performClick()
        waitForSheetClosed()
        compose.runOnIdle {
            assertEquals(1, applyCount)
            assertEquals(SortOrder.DATE_DESC, appliedSort)
            assertEquals(initialCategories, appliedCategories)
            assertEquals(0, resetCount)
        }

        openFilter()
        compose.onNodeWithText(context.getString(R.string.history_sort_amount_short))
            .performScrollTo().performClick()
        compose.onNodeWithText(context.getString(R.string.common_apply)).performClick()
        waitForSheetClosed()
        compose.runOnIdle {
            assertEquals(2, applyCount)
            assertEquals(SortOrder.AMOUNT_DESC, appliedSort)
            assertEquals(initialCategories, appliedCategories)
            assertEquals(0, resetCount)
        }
    }

    private fun showActiveFilter() {
        compose.setContent {
            var categories by remember { mutableStateOf(initialCategories) }
            var sortOrder by remember { mutableStateOf(SortOrder.DATE_DESC) }
            MoneyTalkTheme {
                Column(modifier = Modifier.fillMaxSize()) {
                    FilterTabRow(
                        currentMode = ViewMode.LIST,
                        onModeChange = {},
                        sortOrder = sortOrder,
                        selectedExpenseCategories = categories,
                        onResetFilter = {
                            resetCount += 1
                            categories = emptySet()
                            sortOrder = SortOrder.DATE_DESC
                        },
                        onApplyFilter = { sort, _, _, _, expenseCategories, _, _, _, _ ->
                            applyCount += 1
                            appliedSort = sort
                            appliedCategories = expenseCategories
                            categories = expenseCategories
                            sortOrder = sort
                        }
                    )
                }
            }
        }
    }

    private fun openFilter() {
        compose.onNodeWithTag("history_filter_action").performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.common_close)).assertExists()
    }

    private fun closeFilter() {
        compose.onNodeWithContentDescription(context.getString(R.string.common_close)).performClick()
        waitForSheetClosed()
    }

    private fun waitForSheetClosed() {
        compose.waitUntil(5_000) {
            compose.onAllNodesWithContentDescription(context.getString(R.string.common_close))
                .fetchSemanticsNodes().isEmpty()
        }
    }
}
