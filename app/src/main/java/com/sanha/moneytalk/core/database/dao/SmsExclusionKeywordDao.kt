package com.sanha.moneytalk.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sanha.moneytalk.core.database.entity.SmsExclusionKeywordEntity

/**
 * SMS 제외 키워드 DAO
 *
 * SMS 파싱 시 제외할 키워드의 CRUD를 담당합니다.
 */
@Dao
interface SmsExclusionKeywordDao {

    /** 전체 키워드 엔티티 조회 (설정 화면 표시용) */
    @Query("SELECT * FROM sms_exclusion_keywords ORDER BY source ASC, keyword ASC")
    suspend fun getAll(): List<SmsExclusionKeywordEntity>

    /** 전체 키워드 문자열만 조회 (SmsParser 연동용) */
    @Query("SELECT keyword FROM sms_exclusion_keywords")
    suspend fun getAllKeywords(): List<String>

    /** 사용자/채팅 추가 키워드만 조회 */
    @Query("SELECT keyword FROM sms_exclusion_keywords WHERE source != 'default'")
    suspend fun getUserKeywords(): List<String>

    /** 키워드 추가 (중복 무시) */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: SmsExclusionKeywordEntity)

    /** 키워드 삭제 */
    @Query("DELETE FROM sms_exclusion_keywords WHERE keyword = :keyword AND source != 'default'")
    suspend fun deleteByKeyword(keyword: String): Int

    /** 전체 개수 */
    @Query("SELECT COUNT(*) FROM sms_exclusion_keywords")
    suspend fun getCount(): Int

    /** 소스별 개수 */
    @Query("SELECT COUNT(*) FROM sms_exclusion_keywords WHERE source = :source")
    suspend fun getCountBySource(source: String): Int
}
