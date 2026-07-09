package com.sanha.moneytalk.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiCreditInitialRewardPolicyTest {

    @Test
    fun `initial app launch reward is five credits`() {
        assertEquals(5, AiCreditInitialRewardPolicy.INITIAL_APP_LAUNCH_REWARD_AMOUNT)
    }

    @Test
    fun `initial reward requires monetization and not already granted`() {
        assertTrue(
            AiCreditInitialRewardPolicy.shouldGrantInitialAppLaunchReward(
                isMonetizationEnabled = true,
                alreadyGranted = false
            )
        )
        assertFalse(
            AiCreditInitialRewardPolicy.shouldGrantInitialAppLaunchReward(
                isMonetizationEnabled = false,
                alreadyGranted = false
            )
        )
        assertFalse(
            AiCreditInitialRewardPolicy.shouldGrantInitialAppLaunchReward(
                isMonetizationEnabled = true,
                alreadyGranted = true
            )
        )
    }
}
