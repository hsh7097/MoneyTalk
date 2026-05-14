package com.sanha.moneytalk.core.notification

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.FirebaseDatabase
import com.sanha.moneytalk.BuildConfig
import com.sanha.moneytalk.core.util.MoneyTalkLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RemoteFinancialAppRepository @Inject constructor(
    private val database: FirebaseDatabase?
) : RemoteFinancialAppSource {
    companion object {
        private const val APPROVED_APPS_PATH = "financial_apps/v1/packages"
        private const val CANDIDATE_REPORTS_PATH = "financial_app_reports/v1"
    }

    override fun canReportCandidates(): Boolean = BuildConfig.DEBUG

    override suspend fun loadApprovedApps(): List<RemoteFinancialApp>? {
        val db = database ?: return null
        return try {
            withContext(Dispatchers.IO) {
                val snapshot = db.getReference(APPROVED_APPS_PATH).get().await()
                parseApprovedApps(snapshot)
            }
        } catch (e: Exception) {
            MoneyTalkLogger.w("[FinancialAppRemote] 승인 목록 로드 실패: ${e.message}")
            null
        }
    }

    override suspend fun reportCandidate(analysis: FinancialAppAnalysis): Boolean {
        if (!canReportCandidates()) {
            MoneyTalkLogger.i("[FinancialAppRemote] 후보 리포트 비활성화: release build")
            return false
        }

        val db = database ?: return false
        return try {
            withContext(Dispatchers.IO) {
                val now = System.currentTimeMillis()
                db.getReference(CANDIDATE_REPORTS_PATH)
                    .child(encodePackageKey(analysis.packageName))
                    .setValue(
                        mapOf(
                            "packageName" to analysis.packageName,
                            "displayName" to analysis.displayName,
                            "classification" to analysis.classification,
                            "appType" to analysis.appType,
                            "parserProfile" to analysis.parserProfile,
                            "confidence" to analysis.confidence,
                            "reason" to analysis.reason,
                            "appVersionCode" to BuildConfig.VERSION_CODE,
                            "appVersionName" to BuildConfig.VERSION_NAME,
                            "createdAt" to now,
                            "updatedAt" to now
                        )
                    )
                    .await()
                true
            }
        } catch (e: Exception) {
            MoneyTalkLogger.w(
                "[FinancialAppRemote] 후보 리포트 저장 실패: " +
                    "${analysis.packageName}, ${e.message}"
            )
            false
        }
    }

    private fun parseApprovedApps(snapshot: DataSnapshot): List<RemoteFinancialApp> {
        return snapshot.children.mapNotNull { child ->
            val data = child.value as? Map<*, *> ?: return@mapNotNull null
            val packageName = data["packageName"] as? String
                ?: decodePackageKey(child.key.orEmpty())
            if (packageName.isBlank()) return@mapNotNull null

            val displayName = (data["displayName"] as? String)
                ?.takeIf { it.isNotBlank() }
                ?: packageName.substringAfterLast('.')
            val enabled = data["enabled"] as? Boolean ?: true

            RemoteFinancialApp(
                packageName = packageName,
                displayName = displayName,
                appType = (data["appType"] as? String).orEmpty(),
                parserProfile = (data["parserProfile"] as? String)
                    ?.takeIf { it.isNotBlank() }
                    ?: FinancialAppAnalysis.DEFAULT_PARSER_PROFILE,
                enabled = enabled
            )
        }
    }

    private fun encodePackageKey(packageName: String): String =
        packageName.replace('.', ',')

    private fun decodePackageKey(key: String): String =
        key.replace(',', '.')
}
