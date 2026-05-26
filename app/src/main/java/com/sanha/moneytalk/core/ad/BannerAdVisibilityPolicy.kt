package com.sanha.moneytalk.core.ad

object BannerAdVisibilityPolicy {
    const val MIN_APP_ENTRY_COUNT = 5

    fun canShowBanner(
        rewardAdEnabled: Boolean,
        appEntryCount: Int
    ): Boolean {
        return rewardAdEnabled && appEntryCount >= MIN_APP_ENTRY_COUNT
    }
}
