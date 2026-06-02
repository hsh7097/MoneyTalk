package com.sanha.moneytalk.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 앱 알림 금융앱 후보 캐시.
 *
 * packageName을 기준으로 저장해 같은 앱을 LLM/RTDB에 반복 전달하지 않는다.
 */
@Entity(
    tableName = "financial_app_candidates",
    indices = [
        Index(value = ["status"]),
        Index(value = ["updatedAt"])
    ]
)
data class FinancialAppCandidateEntity(
    @PrimaryKey
    val packageName: String,
    val displayName: String,
    val status: String,
    val appType: String = "",
    val parserProfile: String = "",
    val confidence: Float = 0f,
    val reason: String = "",
    val source: String,
    val reportedAt: Long? = null,
    val lastSeenAt: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val STATUS_SUPPORTED = "SUPPORTED"
        const val STATUS_REPORTED = "REPORTED"
        const val STATUS_REJECTED = "REJECTED"
        const val STATUS_REPORT_DISABLED = "REPORT_DISABLED"

        const val SOURCE_RTDB = "RTDB"
        const val SOURCE_LLM = "LLM"
    }
}
