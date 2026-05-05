package com.sanha.moneytalk.core.util

import org.junit.Assert.assertEquals
import org.junit.Test

class CardNameNormalizerTest {

    @Test
    fun smileCard_isNormalizedToHyundai() {
        assertEquals("현대", CardNameNormalizer.normalize("스마일카드"))
    }

    @Test
    fun smileCardApprovalText_isNormalizedToHyundai() {
        assertEquals("현대", CardNameNormalizer.normalize("스마일카드승인"))
    }
}
