package com.sanha.moneytalk.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sanha.moneytalk.core.database.entity.SmsChannelProbeLogEntity

/**
 * 수집 채널 진단 로그 DAO.
 */
@Dao
interface SmsChannelProbeLogDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: SmsChannelProbeLogEntity)

    @Query("DELETE FROM sms_channel_probe_logs WHERE createdAt < :minCreatedAt")
    suspend fun deleteOlderThan(minCreatedAt: Long): Int
}
