package com.sanha.moneytalk.core.ad

object CreditFeaturePolicy {
    fun canShowCreditFeature(
        isReleaseBuild: Boolean,
        creditAdEnabled: Boolean
    ): Boolean {
        return isReleaseBuild && creditAdEnabled
    }

    fun canUseCreditRewardAd(
        isReleaseBuild: Boolean,
        creditAdEnabled: Boolean,
        rewardAdEnabled: Boolean
    ): Boolean {
        return canShowCreditFeature(
            isReleaseBuild = isReleaseBuild,
            creditAdEnabled = creditAdEnabled
        ) && rewardAdEnabled
    }
}
