package com.sanha.moneytalk.core.database

import com.sanha.moneytalk.core.database.dao.AiCreditDao
import com.sanha.moneytalk.core.database.entity.AiCreditLedgerEntity
import com.sanha.moneytalk.core.database.entity.AiCreditLedgerType
import com.sanha.moneytalk.core.datastore.SettingsDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiCreditRepository @Inject constructor(
    private val aiCreditDao: AiCreditDao,
    private val settingsDataStore: SettingsDataStore
) {
    companion object {
        const val CHAT_MESSAGE_COST = 1
        const val REASON_CHAT_MESSAGE = "chat_message"
        const val REASON_REWARD_AD = "reward_ad"
        const val REASON_LEGACY_REWARD_CHAT = "legacy_reward_chat"
        const val REASON_PURCHASE = "purchase"
    }

    val balanceFlow: Flow<Int> = aiCreditDao.observeBalance()
        .map { it ?: 0 }

    fun observeRecentLedger(limit: Int = 20): Flow<List<AiCreditLedgerEntity>> {
        return aiCreditDao.observeRecentLedger(limit)
    }

    suspend fun getBalance(): Int {
        ensureLegacyRewardChatMigrated()
        return aiCreditDao.getBalance() ?: 0
    }

    suspend fun hasEnoughCredits(cost: Int = CHAT_MESSAGE_COST): Boolean {
        if (cost <= 0) return true
        return getBalance() >= cost
    }

    suspend fun spendForChat(relatedSessionId: Long? = null): Boolean {
        ensureLegacyRewardChatMigrated()
        return aiCreditDao.spendCredits(
            amount = CHAT_MESSAGE_COST,
            reason = REASON_CHAT_MESSAGE,
            relatedSessionId = relatedSessionId
        )
    }

    suspend fun grantRewardAdCredits(amount: Int, relatedSessionId: Long? = null): Int {
        ensureLegacyRewardChatMigrated()
        return aiCreditDao.grantCredits(
            amount = amount,
            type = AiCreditLedgerType.AD_REWARD,
            reason = REASON_REWARD_AD,
            relatedSessionId = relatedSessionId
        )
    }

    suspend fun refundCredits(
        amount: Int,
        reason: String,
        relatedSessionId: Long? = null,
        relatedMessageId: Long? = null
    ): Int {
        ensureLegacyRewardChatMigrated()
        return aiCreditDao.grantCredits(
            amount = amount,
            type = AiCreditLedgerType.REFUND,
            reason = reason,
            relatedSessionId = relatedSessionId,
            relatedMessageId = relatedMessageId
        )
    }

    suspend fun grantPurchaseCredits(
        amount: Int,
        purchaseToken: String
    ): Int {
        ensureLegacyRewardChatMigrated()
        return aiCreditDao.grantCredits(
            amount = amount,
            type = AiCreditLedgerType.PURCHASE,
            reason = REASON_PURCHASE,
            purchaseToken = purchaseToken
        )
    }

    suspend fun ensureLegacyRewardChatMigrated() {
        if (settingsDataStore.isAiCreditLegacyMigrated()) return

        val legacyRemaining = settingsDataStore.getRewardChatRemaining()
        if (legacyRemaining > 0) {
            aiCreditDao.grantCredits(
                amount = legacyRemaining,
                type = AiCreditLedgerType.ADMIN,
                reason = REASON_LEGACY_REWARD_CHAT
            )
        }
        settingsDataStore.markAiCreditLegacyMigrated()
    }
}
