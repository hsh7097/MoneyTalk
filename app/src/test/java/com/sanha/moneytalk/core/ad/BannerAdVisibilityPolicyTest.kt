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
                isDebugBuild = false
            )
        )
    }

    @Test
    fun canShowBanner_requiresMinimumAppEntryCount() {
        assertFalse(
            BannerAdVisibilityPolicy.canShowBanner(
                rewardAdEnabled = true,
                appEntryCount = BannerAdVisibilityPolicy.MIN_APP_ENTRY_COUNT - 1,
                isDebugBuild = false
            )
        )
        assertTrue(
            BannerAdVisibilityPolicy.canShowBanner(
                rewardAdEnabled = true,
                appEntryCount = BannerAdVisibilityPolicy.MIN_APP_ENTRY_COUNT,
                isDebugBuild = false
            )
        )
    }

    @Test
    fun canShowBanner_blocksDebugBuild() {
        assertFalse(
            BannerAdVisibilityPolicy.canShowBanner(
                rewardAdEnabled = true,
                appEntryCount = BannerAdVisibilityPolicy.MIN_APP_ENTRY_COUNT,
                isDebugBuild = true
            )
        )
    }
}
