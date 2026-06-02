package com.sanha.moneytalk.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * AI 크레딧 현재 잔액.
 *
 * 단일 행(id=1)으로 관리하고, 모든 변동 근거는 AiCreditLedgerEntity에 기록한다.
 */
@Entity(tableName = "ai_credit_balance")
data class AiCreditBalanceEntity(
    @PrimaryKey
    val id: Int = BALANCE_ROW_ID,
    val balance: Int,
    val updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val BALANCE_ROW_ID = 1
    }
}
