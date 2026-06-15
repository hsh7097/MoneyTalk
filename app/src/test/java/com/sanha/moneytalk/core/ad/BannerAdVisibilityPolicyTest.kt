package com.sanha.moneytalk.core.ad

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BannerAdVisibilityPolicyTest {

    @Test
    fun canShowBanner_requiresRewardAdEnabled() {
        assertFalse(
            BannerAdVisibilityPolicy.canShowBanner(
                rewardAdEnabled = false,
                appEntryCount = BannerAdVisibilityPolicy.MIN_APP_ENTRY_COUNT,
                isReleaseBuild = true
            )
        )
    }

    @Test
    fun canShowBanner_requiresMinimumAppEntryCount() {
        assertFalse(
            BannerAdVisibilityPolicy.canShowBanner(
                rewardAdEnabled = true,
                appEntryCount = BannerAdVisibilityPolicy.MIN_APP_ENTRY_COUNT - 1,
                isReleaseBuild = true
            )
        )
        assertTrue(
            BannerAdVisibilityPolicy.canShowBanner(
                rewardAdEnabled = true,
                appEntryCount = BannerAdVisibilityPolicy.MIN_APP_ENTRY_COUNT,
                isReleaseBuild = true
            )
        )
    }

    @Test
    fun canShowBanner_blocksNonReleaseBuild() {
        assertFalse(
            BannerAdVisibilityPolicy.canShowBanner(
                rewardAdEnabled = true,
                appEntryCount = BannerAdVisibilityPolicy.MIN_APP_ENTRY_COUNT,
                isReleaseBuild = false
            )
        )
    }
}
