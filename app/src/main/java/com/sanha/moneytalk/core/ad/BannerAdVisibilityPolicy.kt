package com.sanha.moneytalk.core.ad

import com.sanha.moneytalk.core.util.BuildVariantPolicy

object BannerAdVisibilityPolicy {
    const val MIN_APP_ENTRY_COUNT = 5

    fun canShowBanner(
        rewardAdEnabled: Boolean,
        appEntryCount: Int,
        isReleaseBuild: Boolean = BuildVariantPolicy.isReleaseBuild
    ): Boolean {
        return isReleaseBuild && rewardAdEnabled && appEntryCount >= MIN_APP_ENTRY_COUNT
    }
}
