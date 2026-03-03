package com.sanha.moneytalk.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sanha.moneytalk.core.database.entity.SmsRegexRuleEntity

/**
 * sender 기반 regex 룰 DAO
 */
@Dao
interface SmsRegexRuleDao {

    /** 단일 룰 upsert */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rule: SmsRegexRuleEntity)

    /** 다건 룰 upsert */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rules: List<SmsRegexRuleEntity>)

    /** sender 기준 ACTIVE 룰 조회 (우선순위 높은 순) */
    @Query(
        """
        SELECT * FROM sms_regex_rules
        WHERE senderAddress = :senderAddress AND status = 'ACTIVE'
        ORDER BY priority DESC, updatedAt DESC
        """
    )
    suspend fun getActiveRulesBySender(senderAddress: String): List<SmsRegexRuleEntity>

    /** sender + type 기준 ACTIVE 룰 조회 */
    @Query(
        """
        SELECT * FROM sms_regex_rules
        WHERE senderAddress = :senderAddress
          AND type = :type
          AND status = 'ACTIVE'
        ORDER BY priority DESC, updatedAt DESC
        """
    )
    suspend fun getActiveRulesBySenderAndType(
        senderAddress: String,
        type: String
    ): List<SmsRegexRuleEntity>

    /** sender + type + ruleKey 단건 조회 */
    @Query(
        """
        SELECT * FROM sms_regex_rules
        WHERE senderAddress = :senderAddress
          AND type = :type
          AND ruleKey = :ruleKey
        LIMIT 1
        """
    )
    suspend fun getRule(
        senderAddress: String,
        type: String,
        ruleKey: String
    ): SmsRegexRuleEntity?

    /** 매칭 성공 카운트 증가 */
    @Query(
        """
        UPDATE sms_regex_rules
        SET matchCount = matchCount + 1,
            lastMatchedAt = :timestamp,
            updatedAt = :timestamp
        WHERE senderAddress = :senderAddress
          AND type = :type
          AND ruleKey = :ruleKey
        """
    )
    suspend fun incrementMatchCount(
        senderAddress: String,
        type: String,
        ruleKey: String,
        timestamp: Long = System.currentTimeMillis()
    ): Int

    /** 매칭 실패 카운트 증가 */
    @Query(
        """
        UPDATE sms_regex_rules
        SET failCount = failCount + 1,
            updatedAt = :timestamp
        WHERE senderAddress = :senderAddress
          AND type = :type
          AND ruleKey = :ruleKey
        """
    )
    suspend fun incrementFailCount(
        senderAddress: String,
        type: String,
        ruleKey: String,
        timestamp: Long = System.currentTimeMillis()
    ): Int

    /** sender 기준 룰 전체 삭제 */
    @Query("DELETE FROM sms_regex_rules WHERE senderAddress = :senderAddress")
    suspend fun deleteBySender(senderAddress: String): Int

    /** 전체 룰 삭제 */
    @Query("DELETE FROM sms_regex_rules")
    suspend fun deleteAll()
}
