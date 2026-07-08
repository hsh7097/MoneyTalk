package com.sanha.moneytalk.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class LocalChatQueryRouterTest {

    @Test
    fun `total expense lookup routes locally`() {
        val route = LocalChatQueryRouter.tryRoute("이번 달 총 지출 얼마야?")

        assertEquals(LocalChatQueryType.TOTAL_EXPENSE, route?.type)
        assertEquals(QueryType.TOTAL_EXPENSE, route?.queries?.single()?.type)
    }

    @Test
    fun `category amount lookup routes with category filter`() {
        val route = LocalChatQueryRouter.tryRoute("이번 달 식비 얼마야?")
        val query = route?.queries?.single()

        assertEquals(LocalChatQueryType.CATEGORY_EXPENSE, route?.type)
        assertEquals(QueryType.TOTAL_EXPENSE, query?.type)
        assertEquals("식비", query?.category)
    }

    @Test
    fun `recent expense lookup uses full range and limit`() {
        val route = LocalChatQueryRouter.tryRoute(
            message = "최근 지출 5개 보여줘",
            today = LocalDate.of(2026, 7, 8)
        )
        val query = route?.queries?.single()

        assertEquals(LocalChatQueryType.RECENT_EXPENSES, route?.type)
        assertEquals(QueryType.EXPENSE_LIST, query?.type)
        assertEquals("1970-01-01", query?.startDate)
        assertEquals("2026-07-08", query?.endDate)
        assertEquals(5, query?.limit)
    }

    @Test
    fun `recent expense lookup supports korean count suffix`() {
        val route = LocalChatQueryRouter.tryRoute(
            message = "최근 지출 다섯 건 보여줘",
            today = LocalDate.of(2026, 7, 8)
        )

        assertEquals(5, route?.queries?.single()?.limit)
    }

    @Test
    fun `budget status lookup routes locally but budget mutation does not`() {
        val lookup = LocalChatQueryRouter.tryRoute("이번 달 예산 현황 보여줘")
        val mutation = LocalChatQueryRouter.tryRoute("식비 예산 20만원으로 설정해줘")

        assertEquals(LocalChatQueryType.BUDGET_STATUS, lookup?.type)
        assertEquals(QueryType.BUDGET_STATUS, lookup?.queries?.single()?.type)
        assertNull(mutation)
    }

    @Test
    fun `uncategorized and duplicate lookups route locally`() {
        val uncategorized = LocalChatQueryRouter.tryRoute("미분류 항목 보여줘")
        val duplicate = LocalChatQueryRouter.tryRoute("중복 지출 내역 있어?")

        assertEquals(LocalChatQueryType.UNCATEGORIZED, uncategorized?.type)
        assertEquals(QueryType.UNCATEGORIZED_LIST, uncategorized?.queries?.single()?.type)
        assertEquals(LocalChatQueryType.DUPLICATES, duplicate?.type)
        assertEquals(QueryType.DUPLICATE_LIST, duplicate?.queries?.single()?.type)
    }

    @Test
    fun `analysis and advice intents stay on gemini path`() {
        val advice = LocalChatQueryRouter.tryRoute("카페 지출 줄일 방법 추천해줘")
        val evaluation = LocalChatQueryRouter.tryRoute("이번 달 식비가 많은 편이야?")
        val comparison = LocalChatQueryRouter.tryRoute("지난달 대비 이번 달 지출이 늘었어?")

        assertNull(advice)
        assertNull(evaluation)
        assertNull(comparison)
    }

    @Test
    fun `monthly totals and card list route locally`() {
        val monthlyTotals = LocalChatQueryRouter.tryRoute(
            message = "올해 월별 지출 보여줘",
            today = LocalDate.of(2026, 7, 8)
        )
        val cardList = LocalChatQueryRouter.tryRoute("사용 카드 목록 보여줘")
        val monthlyQuery = monthlyTotals?.queries?.single()

        assertEquals(LocalChatQueryType.MONTHLY_TOTALS, monthlyTotals?.type)
        assertEquals(QueryType.MONTHLY_TOTALS, monthlyQuery?.type)
        assertEquals("2026-01-01", monthlyQuery?.startDate)
        assertEquals("2026-07-08", monthlyQuery?.endDate)
        assertEquals(LocalChatQueryType.CARD_LIST, cardList?.type)
        assertEquals(QueryType.CARD_LIST, cardList?.queries?.single()?.type)
    }

    @Test
    fun `ambiguous shopping alias does not force category route`() {
        val route = LocalChatQueryRouter.tryRoute("쇼핑 얼마야?")

        assertTrue(route == null || route.type != LocalChatQueryType.CATEGORY_EXPENSE)
    }

    @Test
    fun `unsupported period expressions stay on gemini path`() {
        assertNull(LocalChatQueryRouter.tryRoute("지난달 총 지출 얼마야?"))
        assertNull(LocalChatQueryRouter.tryRoute("오늘 식비 얼마야?"))
        assertNull(LocalChatQueryRouter.tryRoute("지난 3개월 배달 얼마야?"))
        assertNull(LocalChatQueryRouter.tryRoute("최근 한달 지출 보여줘"))
    }
}
