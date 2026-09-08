package com.sanha.moneytalk.feature.weeklyevidence

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sanha.moneytalk.R
import com.sanha.moneytalk.core.database.entity.ExpenseEntity
import com.sanha.moneytalk.core.theme.MoneyTalkTheme
import com.sanha.moneytalk.core.theme.ThemeMode
import com.sanha.moneytalk.feature.home.briefing.BriefingExpense
import com.sanha.moneytalk.feature.home.briefing.SpendingBriefingCalculator
import com.sanha.moneytalk.feature.home.briefing.SpendingBriefingCard
import com.sanha.moneytalk.feature.home.ui.theme.HomeTheme
import com.sanha.moneytalk.feature.weeklyevidence.ui.WeeklyEvidenceContent
import com.sanha.moneytalk.feature.weeklyevidence.ui.WeeklyEvidencePeriod
import com.sanha.moneytalk.feature.weeklyevidence.ui.WeeklyEvidenceUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.text.NumberFormat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** 합성 기록만 사용한다. 앱 DB나 사용자 거래를 읽거나 쓰지 않는다. */
@RunWith(AndroidJUnit4::class)
class WeeklyEvidenceInteractionTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val zone = ZoneId.of("Asia/Seoul")
    private val rows = listOf(
        expense(11, 3_000, "2026-09-09T12:00", "recent fixture"),
        expense(12, 1_000, "2026-09-02T12:00", "previous fixture")
    )
    private val briefing = SpendingBriefingCalculator.calculate(
        rows.map { BriefingExpense(it.amount.toLong(), it.category, it.dateTime) },
        LocalDate.parse("2026-09-01"), LocalDate.parse("2026-09-30"),
        LocalDateTime.parse("2026-09-09T13:00").atZone(zone).toInstant(), zone, null
    )

    @Test fun switchingPeriodsUpdatesTotalAndRowsAndPreservesEditAndLongClickIds() {
        val request = WeeklyEvidenceRequest.from(requireNotNull(briefing.weeklyComparison), "식비")
        val state = mutableStateOf(WeeklyEvidenceUiState(
            request = request,
            records = WeeklyEvidenceFilter.filter(rows, request, emptySet(), emptySet()),
            isLoading = false
        ))
        val clicked = mutableListOf<Long>()
        val longClicked = mutableListOf<Long>()
        compose.setContent {
            MoneyTalkTheme {
                WeeklyEvidenceContent(
                    state = state.value, onBack = {},
                    onPeriodSelected = { state.value = state.value.copy(selectedPeriod = it) },
                    onRetry = {}, onExpenseClick = { clicked.add(it) }, onExpenseLongClick = { longClicked.add(it) }
                )
            }
        }
        compose.onNodeWithTag("weekly-evidence-total").assertTextEquals(amount(3_000))
        compose.onNodeWithText("recent fixture").assertIsDisplayed()
        compose.onNodeWithTag("weekly-expense-11").performClick()
        compose.onNodeWithTag("weekly-period-PREVIOUS").performClick()
        compose.onNodeWithTag("weekly-evidence-total").assertTextEquals(amount(1_000))
        compose.onNodeWithText("recent fixture").assertDoesNotExist()
        compose.onNodeWithText("previous fixture").assertIsDisplayed()
        compose.onNodeWithTag("weekly-expense-12").performTouchInput { longClick() }
        compose.runOnIdle {
            assertEquals(listOf(11L), clicked)
            assertEquals(listOf(12L), longClicked)
            state.value = state.value.copy(records = WeeklyEvidenceFilter.filter(emptyList(), request, emptySet(), emptySet()))
        }
        compose.onNodeWithTag("weekly-evidence-total").assertTextEquals(amount(0))
        compose.onNodeWithText(context.getString(R.string.weekly_evidence_empty)).assertIsDisplayed()
        compose.onNodeWithTag("weekly-period-RECENT").performClick()
        compose.onNodeWithTag("weekly-evidence-total").assertTextEquals(amount(0))
    }

    @Test fun briefingAmountCardsOpenBothWholePeriodsAndCategoryButtonKeepsItsScope() {
        val requests = mutableListOf<WeeklyEvidenceRequest>()
        compose.setContent {
            MoneyTalkTheme(themeMode = ThemeMode.LIGHT) {
                HomeTheme {
                    Box(Modifier.width(400.dp).background(MaterialTheme.colorScheme.background).padding(16.dp).testTag("briefing-fixture")) {
                        SpendingBriefingCard(briefing, false, true, { comparison, category, recent ->
                            requests.add(WeeklyEvidenceRequest.from(comparison, category, recent))
                        })
                    }
                }
            }
        }
        saveFixture("weekly-briefing-light.png")
        compose.onNodeWithTag("briefing-recent-evidence").performClick()
        compose.onNodeWithTag("briefing-previous-evidence").performClick()
        compose.onNodeWithText(context.getString(R.string.weekly_evidence_open, "식비")).performClick()
        compose.runOnIdle {
            assertEquals(listOf(null, null, "식비"), requests.map { it.category })
            assertEquals(listOf(true, false, true), requests.map { it.initiallyRecent })
            assertEquals(listOf(LocalDate.parse("2026-09-03")), requests.map { it.recentStart }.distinct())
            assertEquals(listOf(LocalDate.parse("2026-08-27")), requests.map { it.previousStart }.distinct())
        }
    }

    @Test fun narrowDarkLargeFontKeepsBothDatesAndFixedExpenseBasisVisible() {
        val pastBriefing = briefing.copy(isCurrentPeriod = false, weeklyComparison = briefing.weeklyComparison?.copy(largestCategoryIncrease = null))
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                MoneyTalkTheme(themeMode = ThemeMode.DARK) {
                    HomeTheme {
                        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.TopCenter) {
                            Box(Modifier.width(320.dp).padding(12.dp).testTag("briefing-fixture")) {
                                SpendingBriefingCard(pastBriefing, false, true, { _, _, _ -> })
                            }
                        }
                    }
                }
            }
        }
        compose.onNodeWithText(context.getString(R.string.weekly_evidence_basis)).assertIsDisplayed()
        compose.onNodeWithTag("briefing-recent-evidence").assertIsDisplayed()
        compose.onNodeWithTag("briefing-previous-evidence").assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.weekly_evidence_past_anchor)).assertIsDisplayed()
        saveFixture("weekly-briefing-dark-large.png")
    }

    private fun saveFixture(name: String) {
        val image = compose.onNodeWithTag("briefing-fixture").captureToImage().asAndroidBitmap()
        try {
            File(requireNotNull(context.getExternalFilesDir(null)), name).outputStream().use {
                image.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        } finally { image.recycle() }
    }

    private fun amount(value: Int) = context.getString(R.string.home_briefing_amount, NumberFormat.getNumberInstance().format(value))
    private fun expense(id: Long, amount: Int, time: String, name: String) = ExpenseEntity(
        id = id, amount = amount, storeName = name, category = "식비", cardName = "fixture",
        dateTime = LocalDateTime.parse(time).atZone(zone).toInstant().toEpochMilli(), originalSms = "", smsId = "fixture-$id"
    )
}
