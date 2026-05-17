package com.sanha.moneytalk.core.notification

data class FinancialAppAnalysis(
    val packageName: String,
    val displayName: String,
    val classification: String,
    val appType: String,
    val parserProfile: String,
    val confidence: Float,
    val reason: String
) {
    val shouldReport: Boolean
        get() = classification in REPORTABLE_CLASSIFICATIONS && confidence >= MIN_REPORT_CONFIDENCE

    companion object {
        const val CLASSIFICATION_FINANCIAL_SUPPORTED = "FINANCIAL_SUPPORTED"
        const val CLASSIFICATION_FINANCIAL_NEEDS_TEST = "FINANCIAL_NEEDS_TEST"
        const val CLASSIFICATION_PAYMENT_MODULE_ONLY = "PAYMENT_MODULE_ONLY"
        const val CLASSIFICATION_SECURITY_AUTH_ONLY = "SECURITY_AUTH_ONLY"
        const val CLASSIFICATION_SHOPPING_OR_MESSENGER = "SHOPPING_OR_MESSENGER"
        const val CLASSIFICATION_UNKNOWN = "UNKNOWN"

        const val DEFAULT_TYPE = "UNKNOWN"
        const val DEFAULT_PARSER_PROFILE = "COMMON_APP_NOTIFICATION"

        private const val MIN_REPORT_CONFIDENCE = 0.7f
        private val REPORTABLE_CLASSIFICATIONS = setOf(
            CLASSIFICATION_FINANCIAL_SUPPORTED,
            CLASSIFICATION_FINANCIAL_NEEDS_TEST,
            CLASSIFICATION_PAYMENT_MODULE_ONLY
        )
    }
}
