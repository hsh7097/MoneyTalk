package com.sanha.moneytalk.feature.history

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sanha.moneytalk.core.ui.component.MonthPagerUtils
import com.sanha.moneytalk.core.ui.component.rememberMonthPagerPageCount
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 실제 Pager와 공용 월 상태를 사용해 측정/캐시의 페이지 수가 어긋나던 경계를 검증한다. */
@RunWith(AndroidJUnit4::class)
class MonthPagerStateTest {
    @get:Rule val compose = createComposeRule()
    private var clockMonth = YearMonth.now()
    private val monthStartDay = mutableIntStateOf(1)
    private lateinit var lifecycle: LifecycleRegistry
    private lateinit var pager: PagerState

    @Test
    fun monthBoundaryKeepsCountStableDuringDragAndMakesNewMonthAvailableOnResume() {
        val initialLastPage = pageFor(clockMonth)
        showPager(initialLastPage)
        compose.runOnIdle { clockMonth = clockMonth.plusMonths(1) }
        swipeForward()
        compose.runOnIdle { assertEquals(initialLastPage, pager.currentPage) }

        resume()
        swipeForward()
        compose.runOnIdle {
            assertEquals(initialLastPage + 2, pager.pageCount)
            assertEquals(initialLastPage + 1, pager.currentPage)
        }
    }

    @Test
    fun clockMovingBackOnResumeClampsLastPageAndStillAllowsDragging() {
        val initialLastPage = pageFor(clockMonth)
        showPager(initialLastPage)
        compose.runOnIdle { clockMonth = clockMonth.minusMonths(1) }
        resume()
        compose.runOnIdle {
            assertEquals(initialLastPage, pager.pageCount)
            assertEquals(initialLastPage - 1, pager.currentPage)
        }
        swipeBackward()
        swipeForward()
        compose.runOnIdle { assertEquals(initialLastPage - 1, pager.currentPage) }
    }

    @Test
    fun changingBillingStartDayShrinksAndExpandsTheLastAllowedMonth() {
        val currentLastPage = pageFor(clockMonth)
        monthStartDay.intValue = 21
        showPager(currentLastPage + 1)
        compose.runOnIdle { monthStartDay.intValue = 1 }
        compose.runOnIdle {
            assertEquals(currentLastPage + 1, pager.pageCount)
            assertEquals(currentLastPage, pager.currentPage)
        }
        swipeBackward()
        swipeForward()
        compose.runOnIdle { assertEquals(currentLastPage, pager.currentPage) }
        compose.runOnIdle { monthStartDay.intValue = 21 }
        swipeForward()
        compose.runOnIdle { assertEquals(currentLastPage + 1, pager.currentPage) }
    }

    private fun showPager(initialPage: Int) {
        lateinit var owner: LifecycleOwner
        compose.runOnIdle {
            owner = object : LifecycleOwner {
                override val lifecycle: Lifecycle get() = this@MonthPagerStateTest.lifecycle
            }
            lifecycle = LifecycleRegistry(owner).apply { currentState = Lifecycle.State.RESUMED }
        }
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                val pageCount = rememberMonthPagerPageCount(monthStartDay.intValue) { startDay ->
                    // 시계는 Snapshot state가 아니다. 시작일만 사용자 설정처럼 관찰 가능하게 둔다.
                    val month = clockMonth.plusMonths(if (startDay == 1) 0 else 1)
                    month.year to month.monthValue
                }
                pager = rememberPagerState(initialPage = initialPage, pageCount = { pageCount })
                HorizontalPager(
                    state = pager,
                    modifier = Modifier.fillMaxSize().testTag("month-boundary-pager"),
                    beyondViewportPageCount = 1,
                    key = { it }
                ) { page ->
                    Box(Modifier.fillMaxSize()) { Text("Month $page") }
                }
            }
        }
    }

    private fun resume() {
        compose.runOnIdle {
            lifecycle.currentState = Lifecycle.State.STARTED
            lifecycle.currentState = Lifecycle.State.RESUMED
        }
    }

    private fun swipeForward() = compose.onNodeWithTag("month-boundary-pager").performTouchInput { swipeLeft() }
    private fun swipeBackward() = compose.onNodeWithTag("month-boundary-pager").performTouchInput { swipeRight() }
    private fun pageFor(month: YearMonth) = MonthPagerUtils.yearMonthToPage(month.year, month.monthValue)
}
