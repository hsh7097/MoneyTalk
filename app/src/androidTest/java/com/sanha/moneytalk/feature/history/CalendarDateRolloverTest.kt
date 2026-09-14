package com.sanha.moneytalk.feature.history

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sanha.moneytalk.R
import com.sanha.moneytalk.core.theme.MoneyTalkTheme
import com.sanha.moneytalk.core.theme.ThemeMode
import com.sanha.moneytalk.feature.history.ui.BillingCycleCalendarView
import com.sanha.moneytalk.feature.history.ui.rememberCalendarToday
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 월과 거래 입력을 그대로 둔 채 날짜만 바뀌어도 복귀 후 달력의 날짜 상태가 바뀌어야 한다. */
@RunWith(AndroidJUnit4::class)
class CalendarDateRolloverTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun resumeAfterMidnightRevealsTodayAmountsEnablesDetailsAndUpdatesNoSpendDays() {
        var currentDate = LocalDate.of(2026, 9, 14)
        var selectedDate: String? = null
        lateinit var owner: LifecycleOwner
        lateinit var testLifecycle: LifecycleRegistry
        compose.runOnIdle {
            owner = object : LifecycleOwner {
                override val lifecycle: Lifecycle get() = testLifecycle
            }
            testLifecycle = LifecycleRegistry(owner).apply { currentState = Lifecycle.State.RESUMED }
        }
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                MoneyTalkTheme(themeMode = ThemeMode.LIGHT) {
                    BillingCycleCalendarView(
                        year = 2026,
                        month = 9,
                        monthStartDay = 1,
                        dailyTotals = mapOf("2026-09-14" to 1_500, "2026-09-15" to 3_400),
                        dailyIncomeTotals = mapOf("2026-09-15" to 25_500),
                        today = rememberCalendarToday { currentDate },
                        onDateClick = { selectedDate = it }
                    )
                }
            }
        }

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val expenseDescription = "-" + context.getString(R.string.common_won, "3,400")
        val incomeDescription = "+" + context.getString(R.string.common_won, "25,500")
        compose.onNode(hasText("15") and hasClickAction()).assertIsNotEnabled()
        compose.onNodeWithContentDescription(expenseDescription, useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithContentDescription(incomeDescription, useUnmergedTree = true).assertDoesNotExist()

        compose.runOnIdle {
            testLifecycle.currentState = Lifecycle.State.STARTED
            currentDate = currentDate.plusDays(1)
            testLifecycle.currentState = Lifecycle.State.RESUMED
        }
        compose.onNode(hasText("15") and hasClickAction()).performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals("2026-09-15", selectedDate) }
        compose.onNodeWithContentDescription(expenseDescription, useUnmergedTree = true).assertExists()
        compose.onNodeWithContentDescription(incomeDescription, useUnmergedTree = true).assertExists()
        compose.onNodeWithText(context.getString(R.string.history_no_spend_total, 13)).assertExists()

        compose.runOnIdle {
            testLifecycle.currentState = Lifecycle.State.STARTED
            currentDate = currentDate.plusDays(1)
            testLifecycle.currentState = Lifecycle.State.RESUMED
        }
        compose.onNode(hasText("16") and hasClickAction()).performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals("2026-09-16", selectedDate) }
        compose.onNodeWithText(context.getString(R.string.history_no_spend_total, 14)).assertExists()
    }
}
