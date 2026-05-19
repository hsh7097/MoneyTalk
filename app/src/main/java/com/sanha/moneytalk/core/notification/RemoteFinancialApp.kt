package com.sanha.moneytalk.core.notification

data class RemoteFinancialApp(
    val packageName: String,
    val displayName: String,
    val appType: String,
    val parserProfile: String,
    val enabled: Boolean
)
