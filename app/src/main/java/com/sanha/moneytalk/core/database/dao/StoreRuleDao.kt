package com.sanha.moneytalk.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sanha.moneytalk.core.database.entity.StoreRuleEntity
import kotlinx.coroutines.flow.Flow

/**
 * 거래처 규칙 DAO
 *
 * 거래처 키워드 기반 카테고리/고정지출 자동 적용 규칙을 관리합니다.
 *
 * @see StoreRuleEntity
 */
@Dao
interface StoreRuleDao {

    /** 모든 규칙 조회 (Flow, 최신순) */
    @Query("SELECT * FROM store_rules ORDER BY createdAt DESC")
    fun getAll(): Flow<List<StoreRuleEntity>>

    /** 모든 규칙 조회 (1회성) */
    @Query("SELECT * FROM store_rules ORDER BY createdAt DESC")
    suspend fun getAllOnce(): List<StoreRuleEntity>

    /** keyword로 규칙 조회 (정확 매칭) */
    @Query("SELECT * FROM store_rules WHERE keyword = :keyword LIMIT 1")
    suspend fun getByKeyword(keyword: String): StoreRuleEntity?

    /** 규칙 추가/갱신 (keyword unique → REPLACE) */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: StoreRuleEntity)

    /** 규칙 삭제 */
    @Delete
    suspend fun delete(rule: StoreRuleEntity)

    /** ID로 규칙 삭제 */
    @Query("DELETE FROM store_rules WHERE id = :id")
    suspend fun deleteById(id: Long)
}
