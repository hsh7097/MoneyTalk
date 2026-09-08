package com.sanha.moneytalk.core.ui.component.chart

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sanha.moneytalk.R
import com.sanha.moneytalk.core.theme.MoneyTalkTheme
import com.sanha.moneytalk.core.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.text.NumberFormat
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class CumulativeInspectionInteractionTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val period = mutableStateOf(LocalDate.parse("2026-08-19"))

    @Test fun tapAndHeldDragInspectActualDatesAndEnabledRawAmountsThenCloseOrMonthChangeClears() {
        showChart()
        compose.onNodeWithTag("cumulative-inspection-chart").performTouchInput {
            click(Offset(width * 0.35f, height * 0.45f))
        }
        compose.onNodeWithTag("chart-inspection-readout").assertIsDisplayed()
        val firstDay = selectedDay()
        assertAmount(0, firstDay * 10_000L)
        assertAmount(1, firstDay * 5_000L)
        compose.onNodeWithTag("cumulative-inspection-chart").performTouchInput {
            down(Offset(width * 0.35f, height * 0.45f))
            advanceEventTime(650)
            moveTo(Offset(width * 0.60f, height * 0.45f), delayMillis = 40)
            up()
        }
        val nextDay = selectedDay()
        assertTrue(nextDay > firstDay)
        assertAmount(0, nextDay * 10_000L)
        val date = period.value.plusDays(nextDay.toLong() - 1)
        compose.onNodeWithTag("chart-inspection-day").assertTextEquals(
            context.getString(R.string.chart_inspection_date, date.monthValue, date.dayOfMonth, nextDay)
        )
        saveScreenshot("chart-inspection-light.png")
        compose.onNodeWithTag("cumulative-inspection-chart").performTouchInput {
            click(Offset(width * 0.88f, height * 0.45f))
        }
        val previousOnlyDay = selectedDay()
        assertTrue(previousOnlyDay > 22)
        assertAmount(0, previousOnlyDay * 5_000L)
        compose.onNodeWithTag("chart-inspection-value-1").assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.chart_inspection_primary_today_limit, "current fixture")).assertIsDisplayed()
        compose.onNodeWithTag("cumulative-inspection-chart").performTouchInput {
            click(Offset(width * 0.60f, height * 0.45f))
        }
        compose.onNode(hasText("previous fixture") and hasClickAction()).performClick()
        compose.onNodeWithTag("chart-inspection-value-1").assertDoesNotExist()
        compose.onNode(hasText("average fixture") and hasClickAction()).performClick()
        assertAmount(1, nextDay * 2_000L)
        compose.onNodeWithText(context.getString(R.string.chart_inspection_close)).performClick()
        compose.onNodeWithTag("chart-inspection-readout").assertDoesNotExist()
        compose.onNodeWithTag("cumulative-inspection-chart").performTouchInput {
            click(Offset(width * 0.4f, height * 0.45f))
        }
        compose.onNodeWithTag("chart-inspection-readout").assertIsDisplayed()
        compose.runOnIdle { period.value = period.value.plusMonths(1) }
        compose.onNodeWithTag("chart-inspection-readout").assertDoesNotExist()
    }

    @Test fun darkLargeFontSelectionCanBeReadAfterReleasingTheFinger() {
        showChart(dark = true, fontScale = 1.5f)
        compose.onNodeWithTag("cumulative-inspection-chart").performTouchInput {
            click(Offset(width * 0.4f, height * 0.45f))
        }
        compose.onNodeWithTag("chart-inspection-day").assertIsDisplayed()
        assertAmount(0, selectedDay() * 10_000L)
        saveScreenshot("chart-inspection-dark-large.png")
    }

    @Test fun ordinaryHorizontalAndVerticalSwipesStillReachTheParentScrollers() {
        var page = 0
        var scroll = 0
        val selections = mutableListOf<Offset>()
        compose.setContent {
            val pager = rememberPagerState { 2 }
            val vertical = rememberScrollState()
            page = pager.currentPage
            scroll = vertical.value
            Column(Modifier.fillMaxSize().verticalScroll(vertical)) {
                HorizontalPager(state = pager, modifier = Modifier.fillMaxWidth().height(350.dp)) { index ->
                    Box(Modifier.fillMaxSize().testTag("gesture-page-$index")
                        .cumulativeInspectionGesture(index) { selections.add(it) })
                }
                Spacer(Modifier.height(1_500.dp))
            }
        }
        compose.onNodeWithTag("gesture-page-0").performTouchInput { swipeLeft(durationMillis = 200) }
        compose.runOnIdle { assertEquals(1, page); assertTrue(selections.isEmpty()) }
        compose.onNodeWithTag("gesture-page-1").performTouchInput { swipeUp(durationMillis = 200) }
        compose.runOnIdle { assertTrue(scroll > 0); assertTrue(selections.isEmpty()) }
    }

    private fun showChart(dark: Boolean = false, fontScale: Float = 1f) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                MoneyTalkTheme(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.TopCenter) {
                        Column(Modifier.width(400.dp).verticalScroll(rememberScrollState()).padding(16.dp)) {
                            CumulativeTrendSection(
                                info = fixture(), showCard = false, scaleToVisibleLines = true,
                                inspectionPeriodStart = period.value
                            )
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun fixture() = object : SpendingTrendInfo {
        override val title = "Cumulative inspection fixture"
        override val primaryLine = line("current fixture", 10_000, Color(0xFF1D5FC4))
        override val toggleableLines = listOf(
            ToggleableLine(line("previous fixture", 5_000, Color.Gray), true),
            ToggleableLine(line("average fixture", 2_000, Color(0xFF8E24AA)), false)
        )
        override val daysInMonth = 31
        override val todayDayIndex = 22
        override val currentAmount = 220_000L
    }

    private fun line(label: String, daily: Long, color: Color) = CumulativeChartLine(
        points = (0..31).map { it * daily }, color = color, label = label
    )
    private fun selectedDay() = compose.onNodeWithTag("chart-inspection-day")
        .fetchSemanticsNode().config[SemanticsProperties.StateDescription].toInt()
    private fun assertAmount(index: Int, value: Long) = compose.onNodeWithTag("chart-inspection-value-$index")
        .assertTextEquals(context.getString(R.string.common_won, NumberFormat.getNumberInstance().format(value)))
    private fun saveScreenshot(name: String) {
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        try {
            File(requireNotNull(context.getExternalFilesDir(null)), name).outputStream().use {
                image.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        } finally { image.recycle() }
    }
}
