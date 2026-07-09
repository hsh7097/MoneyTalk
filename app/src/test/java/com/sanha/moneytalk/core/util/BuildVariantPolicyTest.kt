package com.sanha.moneytalk.core.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BuildVariantPolicyTest {

    @Test
    fun `release build enables monetization`() {
        assertTrue(BuildVariantPolicy.isReleaseBuild("release"))
    }

    @Test
    fun `non release builds disable monetization`() {
        assertFalse(BuildVariantPolicy.isReleaseBuild("debug"))
        assertFalse(BuildVariantPolicy.isReleaseBuild("staging"))
        assertFalse(BuildVariantPolicy.isReleaseBuild("qa"))
    }

    @Test
    fun `monetization is enabled only for release or test override`() {
        assertTrue(BuildVariantPolicy.isMonetizationEnabled("release"))
        assertFalse(BuildVariantPolicy.isMonetizationEnabled("debug"))
        assertTrue(BuildVariantPolicy.isMonetizationEnabled("debug", testOverride = true))
    }
}
