package com.sanha.moneytalk.core.ad

import com.sanha.moneytalk.core.firebase.ServiceTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CreditFeaturePolicyTest {

    @Test
    fun rewardedAdCreditAmount_isTwoCredits() {
        assertEquals(2, RewardAdManager.REWARD_AD_CREDIT_AMOUNT)
    }

    @Test
    fun canShowCreditFeature_requiresMonetizationEnabled() {
        assertFalse(
            CreditFeaturePolicy.canShowCreditFeature(
                isMonetizationEnabled = false,
                creditAdEnabled = true
            )
        )
    }

    @Test
    fun canShowCreditFeature_requiresCreditAdEnable() {
        assertFalse(
            CreditFeaturePolicy.canShowCreditFeature(
                isMonetizationEnabled = true,
                creditAdEnabled = false
            )
        )
    }

    @Test
    fun canShowCreditFeature_allowsMonetizationAndCreditAdEnable() {
        assertTrue(
            CreditFeaturePolicy.canShowCreditFeature(
                isMonetizationEnabled = true,
                creditAdEnabled = true
            )
        )
    }

    @Test
    fun canShowCreditFeature_blocksPremiumTier() {
        assertFalse(
            CreditFeaturePolicy.canShowCreditFeature(
                isMonetizationEnabled = true,
                creditAdEnabled = true,
                serviceTier = ServiceTier.PREMIUM
            )
        )
    }

    @Test
    fun canUseCreditRewardAd_requiresCreditFeatureAndRewardAd() {
        assertFalse(
            CreditFeaturePolicy.canUseCreditRewardAd(
                isMonetizationEnabled = true,
                creditAdEnabled = false,
                rewardAdEnabled = true
            )
        )
        assertFalse(
            CreditFeaturePolicy.canUseCreditRewardAd(
                isMonetizationEnabled = true,
                creditAdEnabled = true,
                rewardAdEnabled = false
            )
        )
        assertTrue(
            CreditFeaturePolicy.canUseCreditRewardAd(
                isMonetizationEnabled = true,
                creditAdEnabled = true,
                rewardAdEnabled = true
            )
        )
    }

    @Test
    fun canUseCreditRewardAd_blocksPremiumTier() {
        assertFalse(
            CreditFeaturePolicy.canUseCreditRewardAd(
                isMonetizationEnabled = true,
                creditAdEnabled = true,
                rewardAdEnabled = true,
                serviceTier = ServiceTier.PREMIUM
            )
        )
    }
}
