package com.sanha.moneytalk.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * AI 크레딧 충전/사용 원장.
 *
 * amount는 잔액 변동 방향을 포함한다.
 * - 충전/환불: 양수
 * - 사용: 음수
 */
@Entity(
    tableName = "ai_credit_ledger",
    indices = [
        Index(value = ["createdAt"]),
        Index(value = ["type"]),
        Index(value = ["purchaseToken"], unique = true)
    ]
)
data class AiCreditLedgerEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val type: String,
    val amount: Int,
    val reason: String,
    val relatedSessionId: Long? = null,
    val relatedMessageId: Long? = null,
    val purchaseToken: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
