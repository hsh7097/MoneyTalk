package com.sanha.moneytalk.core.util

import com.sanha.moneytalk.BuildConfig

object BuildVariantPolicy {
    private const val RELEASE_BUILD_TYPE = "release"

    val isReleaseBuild: Boolean
        get() = isReleaseBuild(BuildConfig.BUILD_TYPE)

    val isMonetizationEnabled: Boolean
        get() = isMonetizationEnabled(
            buildType = BuildConfig.BUILD_TYPE,
            testOverride = BuildConfig.MONETIZATION_TEST_OVERRIDE
        )

    val isMonetizationTestOverride: Boolean
        get() = BuildConfig.MONETIZATION_TEST_OVERRIDE

    fun isReleaseBuild(buildType: String): Boolean {
        return buildType == RELEASE_BUILD_TYPE
    }

    fun isMonetizationEnabled(buildType: String, testOverride: Boolean = false): Boolean {
        return isReleaseBuild(buildType) || testOverride
    }
}
