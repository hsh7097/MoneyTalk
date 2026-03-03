package com.sanha.moneytalk.core.database

import com.sanha.moneytalk.core.database.dao.SmsRegexRuleDao
import com.sanha.moneytalk.core.database.entity.SmsRegexRuleEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * sender 기반 regex 룰 Repository
 *
 * 실시간 파싱에서는 sender 기준 ACTIVE 룰 조회를 주로 사용합니다.
 */
@Singleton
class SmsRegexRuleRepository @Inject constructor(
    private val dao: SmsRegexRuleDao
) {

    suspend fun upsertRule(rule: SmsRegexRuleEntity) {
        dao.insert(rule)
    }

    suspend fun upsertRules(rules: List<SmsRegexRuleEntity>) {
        if (rules.isEmpty()) return
        dao.insertAll(rules)
    }

    suspend fun getActiveRulesBySender(senderAddress: String): List<SmsRegexRuleEntity> {
        if (senderAddress.isBlank()) return emptyList()
        return dao.getActiveRulesBySender(senderAddress)
    }

    suspend fun getActiveRulesBySenderAndType(
        senderAddress: String,
        type: String
    ): List<SmsRegexRuleEntity> {
        if (senderAddress.isBlank() || type.isBlank()) return emptyList()
        return dao.getActiveRulesBySenderAndType(senderAddress, type)
    }

    suspend fun getRule(
        senderAddress: String,
        type: String,
        ruleKey: String
    ): SmsRegexRuleEntity? {
        if (senderAddress.isBlank() || type.isBlank() || ruleKey.isBlank()) return null
        return dao.getRule(senderAddress, type, ruleKey)
    }

    suspend fun incrementMatchCount(
        senderAddress: String,
        type: String,
        ruleKey: String,
        timestamp: Long = System.currentTimeMillis()
    ) {
        dao.incrementMatchCount(senderAddress, type, ruleKey, timestamp)
    }

    suspend fun incrementFailCount(
        senderAddress: String,
        type: String,
        ruleKey: String,
        timestamp: Long = System.currentTimeMillis()
    ) {
        dao.incrementFailCount(senderAddress, type, ruleKey, timestamp)
    }
}
