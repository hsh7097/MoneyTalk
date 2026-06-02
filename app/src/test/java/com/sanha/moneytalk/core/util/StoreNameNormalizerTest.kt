package com.sanha.moneytalk.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StoreNameNormalizerTest {

    @Test
    fun normalizeForComparison_removesInnerWhitespace() {
        assertEquals("가나다라", StoreNameNormalizer.normalizeForComparison(" 가나 다라 "))
    }

    @Test
    fun containsForComparison_ignoresWhitespaceAndCase() {
        assertTrue(StoreNameNormalizer.containsForComparison("Kakao Pay 결제", "kakaopay"))
        assertTrue(StoreNameNormalizer.containsForComparison("가나 다라", "가나다라"))
    }

    @Test
    fun containsForComparison_rejectsBlankKeyword() {
        assertFalse(StoreNameNormalizer.containsForComparison("가나다라", "   "))
    }

    @Test
    fun matchesStoreRule_acceptsTruncatedPrefixStoreName() {
        assertTrue(StoreNameNormalizer.matchesStoreRule("유튜브프리미", "유튜브프리미엄"))
    }
}
