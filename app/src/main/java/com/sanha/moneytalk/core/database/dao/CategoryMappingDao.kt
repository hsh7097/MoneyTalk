package com.sanha.moneytalk.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.sanha.moneytalk.core.database.entity.CategoryMappingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryMappingDao {

    /**
     * 가게명으로 카테고리 조회
     */
    @Query("SELECT * FROM category_mappings WHERE storeName = :storeName LIMIT 1")
    suspend fun getCategoryByStoreName(storeName: String): CategoryMappingEntity?

    /**
     * 가게명으로 카테고리 조회 (부분 일치)
     */
    @Query("SELECT * FROM category_mappings WHERE :storeName LIKE '%' || storeName || '%' OR storeName LIKE '%' || :storeName || '%' LIMIT 1")
    suspend fun getCategoryByStoreNamePartial(storeName: String): CategoryMappingEntity?

    /**
     * 모든 매핑 조회
     */
    @Query("SELECT * FROM category_mappings ORDER BY updatedAt DESC")
    fun getAllMappings(): Flow<List<CategoryMappingEntity>>

    /**
     * 모든 매핑 조회 (한 번)
     */
    @Query("SELECT * FROM category_mappings ORDER BY updatedAt DESC")
    suspend fun getAllMappingsOnce(): List<CategoryMappingEntity>

    /**
     * 매핑 개수
     */
    @Query("SELECT COUNT(*) FROM category_mappings")
    suspend fun getMappingCount(): Int

    /**
     * 새 매핑 추가 (충돌 시 무시)
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(mapping: CategoryMappingEntity): Long

    /**
     * 새 매핑 추가 (충돌 시 교체)
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(mapping: CategoryMappingEntity): Long

    /**
     * 여러 매핑 추가
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(mappings: List<CategoryMappingEntity>)

    /**
     * 매핑 업데이트
     */
    @Update
    suspend fun update(mapping: CategoryMappingEntity)

    /**
     * 가게명으로 카테고리 업데이트
     */
    @Query("UPDATE category_mappings SET category = :category, source = :source, updatedAt = :updatedAt WHERE storeName = :storeName")
    suspend fun updateCategoryByStoreName(
        storeName: String,
        category: String,
        source: String,
        updatedAt: Long = System.currentTimeMillis()
    )

    /**
     * 매핑 삭제
     */
    @Query("DELETE FROM category_mappings WHERE id = :id")
    suspend fun deleteById(id: Long)

    /**
     * 모든 매핑 삭제
     */
    @Query("DELETE FROM category_mappings")
    suspend fun deleteAll()

    /**
     * 특정 출처의 매핑만 삭제
     */
    @Query("DELETE FROM category_mappings WHERE source = :source")
    suspend fun deleteBySource(source: String)
}
