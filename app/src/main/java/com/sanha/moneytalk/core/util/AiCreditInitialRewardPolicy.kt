package com.sanha.moneytalk.core.util

object AiCreditInitialRewardPolicy {
    const val INITIAL_APP_LAUNCH_REWARD_AMOUNT = 5

    fun shouldGrantInitialAppLaunchReward(
        isMonetizationEnabled: Boolean,
        alreadyGranted: Boolean
    ): Boolean {
        return isMonetizationEnabled && !alreadyGranted
    }
}
