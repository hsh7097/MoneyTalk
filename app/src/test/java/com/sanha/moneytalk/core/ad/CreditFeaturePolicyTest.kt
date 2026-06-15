package com.sanha.moneytalk.core.ad

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CreditFeaturePolicyTest {

    @Test
    fun canShowCreditFeature_requiresReleaseBuild() {
        assertFalse(
            CreditFeaturePolicy.canShowCreditFeature(
                isReleaseBuild = false,
                creditAdEnabled = true
            )
        )
    }

    @Test
    fun canShowCreditFeature_requiresCreditAdEnable() {
        assertFalse(
            CreditFeaturePolicy.canShowCreditFeature(
                isReleaseBuild = true,
                creditAdEnabled = false
            )
        )
    }

    @Test
    fun canShowCreditFeature_allowsReleaseAndCreditAdEnable() {
        assertTrue(
            CreditFeaturePolicy.canShowCreditFeature(
                isReleaseBuild = true,
                creditAdEnabled = true
            )
        )
    }

    @Test
    fun canUseCreditRewardAd_requiresCreditFeatureAndRewardAd() {
        assertFalse(
            CreditFeaturePolicy.canUseCreditRewardAd(
                isReleaseBuild = true,
                creditAdEnabled = false,
                rewardAdEnabled = true
            )
        )
        assertFalse(
            CreditFeaturePolicy.canUseCreditRewardAd(
                isReleaseBuild = true,
                creditAdEnabled = true,
                rewardAdEnabled = false
            )
        )
        assertTrue(
            CreditFeaturePolicy.canUseCreditRewardAd(
                isReleaseBuild = true,
                creditAdEnabled = true,
                rewardAdEnabled = true
            )
        )
    }
}
