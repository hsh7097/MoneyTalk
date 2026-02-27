package com.sanha.moneytalk.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sanha.moneytalk.core.database.entity.SmsBlockedSenderEntity
import kotlinx.coroutines.flow.Flow

/**
 * SMS 수신거부 발신번호 DAO
 */
@Dao
interface SmsBlockedSenderDao {

    /** 전체 수신거부 번호 관찰 (설정 화면) */
    @Query("SELECT * FROM sms_blocked_senders ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<SmsBlockedSenderEntity>>

    /** 파싱 필터링용 주소 목록 조회 (동기 쿼리, 백그라운드 스레드에서만 사용) */
    @Query("SELECT address FROM sms_blocked_senders")
    fun getAllAddresses(): List<String>

    /** 수신거부 번호 추가/갱신 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: SmsBlockedSenderEntity)

    /** 수신거부 번호 삭제 */
    @Query("DELETE FROM sms_blocked_senders WHERE address = :address")
    suspend fun deleteByAddress(address: String): Int
}
