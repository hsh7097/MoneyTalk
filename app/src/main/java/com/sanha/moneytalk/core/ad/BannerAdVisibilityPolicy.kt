package com.sanha.moneytalk.core.ad

import com.sanha.moneytalk.BuildConfig

object BannerAdVisibilityPolicy {
    const val MIN_APP_ENTRY_COUNT = 5

    fun canShowBanner(
        rewardAdEnabled: Boolean,
        appEntryCount: Int,
        isDebugBuild: Boolean = BuildConfig.DEBUG
    ): Boolean {
        return !isDebugBuild && rewardAdEnabled && appEntryCount >= MIN_APP_ENTRY_COUNT
    }
}
