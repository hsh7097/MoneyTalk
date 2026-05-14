package com.sanha.moneytalk.core.notification

interface FinancialAppCandidateAnalyzer {
    suspend fun analyze(
        packageName: String,
        displayName: String
    ): FinancialAppAnalysis?
}
