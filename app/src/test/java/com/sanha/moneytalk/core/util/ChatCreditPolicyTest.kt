package com.sanha.moneytalk.core.util

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatCreditPolicyTest {

    @Test
    fun `blank message is free`() {
        val decision = ChatCreditPolicy.estimate("")

        assertEquals(ChatCreditTier.FREE_LOOKUP, decision.tier)
        assertEquals(0, decision.cost)
    }

    @Test
    fun `simple lookup costs one credit`() {
        val decision = ChatCreditPolicy.estimate("이번 달 식비 얼마야?")

        assertEquals(ChatCreditTier.CHAT_MESSAGE, decision.tier)
        assertEquals(1, decision.cost)
    }

    @Test
    fun `period lookup costs one credit`() {
        val decision = ChatCreditPolicy.estimate("올해 월별 지출 보여줘")

        assertEquals(ChatCreditTier.CHAT_MESSAGE, decision.tier)
        assertEquals(1, decision.cost)
    }

    @Test
    fun `light advice costs one credit`() {
        val decision = ChatCreditPolicy.estimate("카페 지출 줄일 방법 추천해줘")

        assertEquals(ChatCreditTier.CHAT_MESSAGE, decision.tier)
        assertEquals(1, decision.cost)
    }

    @Test
    fun `natural spending evaluation costs one credit`() {
        val decision = ChatCreditPolicy.estimate("이번 달 식비가 많은 편이야?")

        assertEquals(ChatCreditTier.CHAT_MESSAGE, decision.tier)
        assertEquals(1, decision.cost)
    }

    @Test
    fun `standard spending analysis costs one credit`() {
        val decision = ChatCreditPolicy.estimate("올해 소비 흐름 분석해줘")

        assertEquals(ChatCreditTier.CHAT_MESSAGE, decision.tier)
        assertEquals(1, decision.cost)
    }

    @Test
    fun `explicit deep analysis costs one credit`() {
        val decision = ChatCreditPolicy.estimate("올해 전체 데이터 분석으로 상세 리포트 만들어줘")

        assertEquals(ChatCreditTier.CHAT_MESSAGE, decision.tier)
        assertEquals(1, decision.cost)
    }
}
