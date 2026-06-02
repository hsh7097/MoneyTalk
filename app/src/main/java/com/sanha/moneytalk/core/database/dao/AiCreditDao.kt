package com.sanha.moneytalk.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.sanha.moneytalk.core.database.entity.AiCreditBalanceEntity
import com.sanha.moneytalk.core.database.entity.AiCreditLedgerEntity
import com.sanha.moneytalk.core.database.entity.AiCreditLedgerType
import kotlinx.coroutines.flow.Flow

@Dao
interface AiCreditDao {

    @Query("SELECT balance FROM ai_credit_balance WHERE id = 1 LIMIT 1")
    fun observeBalance(): Flow<Int?>

    @Query("SELECT balance FROM ai_credit_balance WHERE id = 1 LIMIT 1")
    suspend fun getBalance(): Int?

    @Query("SELECT * FROM ai_credit_ledger ORDER BY createdAt DESC, id DESC LIMIT :limit")
    fun observeRecentLedger(limit: Int): Flow<List<AiCreditLedgerEntity>>

    @Query("SELECT COUNT(*) FROM ai_credit_ledger WHERE purchaseToken = :purchaseToken")
    suspend fun countByPurchaseToken(purchaseToken: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBalance(balance: AiCreditBalanceEntity)

    @Insert
    suspend fun insertLedger(ledger: AiCreditLedgerEntity): Long

    @Transaction
    suspend fun grantCredits(
        amount: Int,
        type: String,
        reason: String,
        relatedSessionId: Long? = null,
        relatedMessageId: Long? = null,
        purchaseToken: String? = null
    ): Int {
        if (amount <= 0) return getBalance() ?: 0
        if (!purchaseToken.isNullOrBlank() && countByPurchaseToken(purchaseToken) > 0) {
            return getBalance() ?: 0
        }

        val now = System.currentTimeMillis()
        val nextBalance = (getBalance() ?: 0) + amount
        upsertBalance(
            AiCreditBalanceEntity(
                balance = nextBalance,
                updatedAt = now
            )
        )
        insertLedger(
            AiCreditLedgerEntity(
                type = type,
                amount = amount,
                reason = reason,
                relatedSessionId = relatedSessionId,
                relatedMessageId = relatedMessageId,
                purchaseToken = purchaseToken,
                createdAt = now
            )
        )
        return nextBalance
    }

    @Transaction
    suspend fun spendCredits(
        amount: Int,
        reason: String,
        relatedSessionId: Long? = null,
        relatedMessageId: Long? = null
    ): Boolean {
        if (amount <= 0) return true
        val current = getBalance() ?: 0
        if (current < amount) return false

        val now = System.currentTimeMillis()
        upsertBalance(
            AiCreditBalanceEntity(
                balance = current - amount,
                updatedAt = now
            )
        )
        insertLedger(
            AiCreditLedgerEntity(
                type = AiCreditLedgerType.SPEND,
                amount = -amount,
                reason = reason,
                relatedSessionId = relatedSessionId,
                relatedMessageId = relatedMessageId,
                createdAt = now
            )
        )
        return true
    }
}
