package com.sanha.moneytalk.core.firebase

import org.junit.Assert.assertEquals
import org.junit.Test

class GeminiModelConfigTest {

    @Test
    fun defaultChatModelsUseCostGuardedFlashLite() {
        val config = GeminiModelConfig()

        assertEquals("gemini-3.1-flash-lite", config.queryAnalyzer)
        assertEquals("gemini-3.1-flash-lite", config.financialAdvisor)
        assertEquals("gemini-3.1-flash-lite", config.homeInsight)
    }
}
