package com.sanha.moneytalk.core.firebase

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PremiumManagerPolicyTest {

    @Test
    fun `service disabled blocks every tier`() {
        val config = PremiumConfig(serviceEnabled = false, freeTierEnabled = true)

        assertFalse(PremiumManager.canUseAi(config, ServiceTier.FREE))
        assertFalse(PremiumManager.canUseAi(config, ServiceTier.PREMIUM))
    }

    @Test
    fun `free tier flag blocks only free users`() {
        val config = PremiumConfig(serviceEnabled = true, freeTierEnabled = false)

        assertFalse(PremiumManager.canUseAi(config, ServiceTier.FREE))
        assertTrue(PremiumManager.canUseAi(config, ServiceTier.PREMIUM))
    }

    @Test
    fun `enabled service allows enabled free tier`() {
        val config = PremiumConfig(serviceEnabled = true, freeTierEnabled = true)

        assertTrue(PremiumManager.canUseAi(config, ServiceTier.FREE))
    }
}
