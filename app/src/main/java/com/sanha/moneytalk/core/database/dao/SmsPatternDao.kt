package com.sanha.moneytalk.core.database.dao

import androidx.room.*
import com.sanha.moneytalk.core.database.entity.SmsPatternEntity

@Dao
interface SmsPatternDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(pattern: SmsPatternEntity): Long

    @Update
    suspend fun update(pattern: SmsPatternEntity)

    @Query("SELECT * FROM sms_patterns ORDER BY successCount DESC, lastUsedAt DESC")
    suspend fun getAllPatterns(): List<SmsPatternEntity>

    @Query("SELECT * FROM sms_patterns WHERE cardName = :cardName ORDER BY successCount DESC")
    suspend fun getPatternsByCardName(cardName: String): List<SmsPatternEntity>

    @Query("SELECT * FROM sms_patterns WHERE id = :id")
    suspend fun getPatternById(id: Long): SmsPatternEntity?

    @Query("UPDATE sms_patterns SET successCount = successCount + 1, lastUsedAt = :timestamp WHERE id = :id")
    suspend fun incrementSuccessCount(id: Long, timestamp: Long = System.currentTimeMillis())

    @Query("SELECT COUNT(*) FROM sms_patterns")
    suspend fun getPatternCount(): Int

    @Query("DELETE FROM sms_patterns WHERE successCount <= 1 AND createdAt < :beforeTimestamp")
    suspend fun cleanupOldPatterns(beforeTimestamp: Long)
}
