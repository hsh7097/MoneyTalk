package com.sanha.moneytalk.core.sms

import org.junit.Assert.assertEquals
import org.junit.Test

class GeminiSmsExtractorCategoryTest {

    @Test
    fun normalizeCategoryMapsDeliveryAliasesToDelivery() {
        assertEquals("배달", GeminiSmsExtractor.normalizeCategory("배달앱"))
        assertEquals("배달", GeminiSmsExtractor.normalizeCategory("배달음식"))
        assertEquals("배달", GeminiSmsExtractor.normalizeCategory("배민"))
        assertEquals("배달", GeminiSmsExtractor.normalizeCategory("요기요"))
        assertEquals("배달", GeminiSmsExtractor.normalizeCategory("쿠팡이츠"))
    }
}
