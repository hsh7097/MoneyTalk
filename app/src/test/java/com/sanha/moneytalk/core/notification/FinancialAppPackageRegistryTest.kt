package com.sanha.moneytalk.core.notification

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FinancialAppPackageRegistryTest {

    @Test
    fun `known financial app packages are supported for notification parsing`() {
        assertTrue(
            FinancialAppPackageRegistry.isSupportedAppNotificationPackage("com.kakaobank.channel")
        )
        assertTrue(
            FinancialAppPackageRegistry.isSupportedAppNotificationPackage("com.hyundaicard.appcard")
        )
        assertTrue(
            FinancialAppPackageRegistry.isSupportedAppNotificationPackage("com.kbcard.cxh.appcard")
        )
        assertTrue(
            FinancialAppPackageRegistry.isSupportedAppNotificationPackage("kvp.jjy.MispAndroid320")
        )
    }

    @Test
    fun `visibility only packages are not parsed as financial notifications by default`() {
        assertFalse(
            FinancialAppPackageRegistry.isSupportedAppNotificationPackage("com.ahnlab.v3mobileplus")
        )
        assertFalse(
            FinancialAppPackageRegistry.isSupportedAppNotificationPackage("com.ebay.kr.gmarket")
        )
        assertFalse(
            FinancialAppPackageRegistry.isSupportedAppNotificationPackage("com.sktelecom.tauth")
        )
        assertFalse(
            FinancialAppPackageRegistry.isSupportedAppNotificationPackage("com.kakao.talk")
        )
        assertFalse(
            FinancialAppPackageRegistry.isSupportedAppNotificationPackage("com.nhn.android.search")
        )
    }
}
