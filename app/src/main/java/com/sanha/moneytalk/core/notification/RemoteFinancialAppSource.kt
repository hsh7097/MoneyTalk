package com.sanha.moneytalk.core.notification

interface RemoteFinancialAppSource {
    fun canReportCandidates(): Boolean

    suspend fun loadApprovedApps(): List<RemoteFinancialApp>?

    suspend fun reportCandidate(analysis: FinancialAppAnalysis): Boolean
}
