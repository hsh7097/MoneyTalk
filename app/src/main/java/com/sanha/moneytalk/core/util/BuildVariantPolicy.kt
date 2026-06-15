package com.sanha.moneytalk.core.util

import com.sanha.moneytalk.BuildConfig

object BuildVariantPolicy {
    private const val RELEASE_BUILD_TYPE = "release"

    val isReleaseBuild: Boolean
        get() = isReleaseBuild(BuildConfig.BUILD_TYPE)

    val isMonetizationEnabled: Boolean
        get() = isReleaseBuild

    fun isReleaseBuild(buildType: String): Boolean {
        return buildType == RELEASE_BUILD_TYPE
    }
}
