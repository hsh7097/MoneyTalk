package com.sanha.moneytalk.feature.chat.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeInsightNumberGuardTest {

    private val deterministicInsight = "지난달 대비 지출이 855,936원(39%) 감소했습니다."

    @Test
    fun acceptsMatchingDeterministicClaims() {
        assertTrue(
            HomeInsightNumberGuard.matchesDeterministicClaims(
                insight = "지난달 대비 지출이 855,936원(39%) 감소했습니다.",
                deterministicInsight = deterministicInsight
            )
        )
    }

    @Test
    fun rejectsNumbersFromDifferentComparison() {
        assertFalse(
            HomeInsightNumberGuard.matchesDeterministicClaims(
                insight = "지난달 대비 지출이 825,836원(38%) 감소했습니다.",
                deterministicInsight = deterministicInsight
            )
        )
    }

    @Test
    fun rejectsOppositeDirectionWithoutNumbers() {
        assertFalse(
            HomeInsightNumberGuard.matchesDeterministicClaims(
                insight = "지난달보다 지출이 늘었습니다.",
                deterministicInsight = deterministicInsight
            )
        )
    }

    @Test
    fun acceptsConsistentInsightWithoutNumbers() {
        assertTrue(
            HomeInsightNumberGuard.matchesDeterministicClaims(
                insight = "지난달보다 지출이 줄었습니다.",
                deterministicInsight = deterministicInsight
            )
        )
    }
}
