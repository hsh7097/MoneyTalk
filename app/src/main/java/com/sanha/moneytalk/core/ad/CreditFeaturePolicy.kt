package com.sanha.moneytalk.core.ad

import com.sanha.moneytalk.core.firebase.ServiceTier

object CreditFeaturePolicy {
    fun canShowCreditFeature(
        isReleaseBuild: Boolean,
        creditAdEnabled: Boolean,
        serviceTier: ServiceTier = ServiceTier.FREE
    ): Boolean {
        return isReleaseBuild && creditAdEnabled && serviceTier == ServiceTier.FREE
    }

    fun canUseCreditRewardAd(
        isReleaseBuild: Boolean,
        creditAdEnabled: Boolean,
        rewardAdEnabled: Boolean,
        serviceTier: ServiceTier = ServiceTier.FREE
    ): Boolean {
        return canShowCreditFeature(
            isReleaseBuild = isReleaseBuild,
            creditAdEnabled = creditAdEnabled,
            serviceTier = serviceTier
        ) && rewardAdEnabled
    }
}
