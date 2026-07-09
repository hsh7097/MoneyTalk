package com.sanha.moneytalk.core.ad

import com.sanha.moneytalk.core.firebase.ServiceTier

object CreditFeaturePolicy {
    fun canShowCreditFeature(
        isMonetizationEnabled: Boolean,
        creditAdEnabled: Boolean,
        serviceTier: ServiceTier = ServiceTier.FREE
    ): Boolean {
        return isMonetizationEnabled && creditAdEnabled && serviceTier == ServiceTier.FREE
    }

    fun canUseCreditRewardAd(
        isMonetizationEnabled: Boolean,
        creditAdEnabled: Boolean,
        rewardAdEnabled: Boolean,
        serviceTier: ServiceTier = ServiceTier.FREE
    ): Boolean {
        return canShowCreditFeature(
            isMonetizationEnabled = isMonetizationEnabled,
            creditAdEnabled = creditAdEnabled,
            serviceTier = serviceTier
        ) && rewardAdEnabled
    }
}
