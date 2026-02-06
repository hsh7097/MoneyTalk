package com.sanha.moneytalk.core.database.dao

import androidx.room.*
import com.sanha.moneytalk.core.database.entity.StoreEmbeddingEntity
import kotlinx.coroutines.flow.Flow

/**
 * 가게명 임베딩 DAO (Data Access Object)
 *
 * 벡터 유사도 기반 카테고리 분류 시스템의 데이터 접근 계층입니다.
 * StoreEmbeddingEntity에 대한 CRUD 및 검색 쿼리를 제공합니다.
 *
 * 주요 사용처:
 * - StoreEmbeddingRepository: 벡터 검색을 위한 임베딩 조회/등록
 * - CategoryClassifierService: Tier 1.5 벡터 매칭
 * - StoreNameGrouper: 시맨틱 그룹핑 시 기존 분류 재사용
 *
 * @see StoreEmbeddingEntity
 * @see com.sanha.moneytalk.feature.home.data.StoreEmbeddingRepository
 */
@Dao
interface StoreEmbeddingDao {

    /**
     * 단일 임베딩 삽입/교체 (충돌 시 교체)
     * storeName에 unique 인덱스가 있으므로 같은 가게명이면 덮어씀
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(embedding: StoreEmbeddingEntity): Long

    /**
     * 다수 임베딩 일괄 삽입/교체 (배치 저장용)
     * Gemini 배치 분류 결과를 한꺼번에 저장할 때 사용
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(embeddings: List<StoreEmbeddingEntity>)

    /** 임베딩 정보 업데이트 (카테고리 변경 등) */
    @Update
    suspend fun update(embedding: StoreEmbeddingEntity)

    /** 특정 임베딩 삭제 */
    @Delete
    suspend fun delete(embedding: StoreEmbeddingEntity)

    /**
     * 모든 임베딩 조회 (벡터 검색용)
     * StoreEmbeddingRepository에서 인메모리 캐시로 로드하여 코사인 유사도 검색에 사용
     */
    @Query("SELECT * FROM store_embeddings")
    suspend fun getAllEmbeddings(): List<StoreEmbeddingEntity>

    /**
     * 가게명으로 임베딩 조회 (정확 매칭)
     * 이미 학습된 가게인지 확인할 때 사용
     */
    @Query("SELECT * FROM store_embeddings WHERE storeName = :storeName LIMIT 1")
    suspend fun getByStoreName(storeName: String): StoreEmbeddingEntity?

    /**
     * 특정 카테고리의 임베딩 목록 조회
     * 카테고리별 학습 현황 확인용
     */
    @Query("SELECT * FROM store_embeddings WHERE category = :category")
    suspend fun getByCategory(category: String): List<StoreEmbeddingEntity>

    /**
     * 매칭 횟수 증가 + 마지막 업데이트 시간 갱신
     * 벡터 매칭 시 호출하여 사용 빈도를 추적
     */
    @Query("UPDATE store_embeddings SET matchCount = matchCount + 1, updatedAt = :timestamp WHERE id = :embeddingId")
    suspend fun incrementMatchCount(embeddingId: Long, timestamp: Long = System.currentTimeMillis())

    /**
     * 가게명으로 카테고리 업데이트
     * 사용자 수동 분류 변경 시 벡터 DB도 같이 업데이트
     */
    @Query("UPDATE store_embeddings SET category = :newCategory, source = :source, updatedAt = :timestamp WHERE storeName = :storeName")
    suspend fun updateCategory(storeName: String, newCategory: String, source: String = "user", timestamp: Long = System.currentTimeMillis())

    /**
     * ID로 카테고리 업데이트 (전파용)
     * 유사 가게 카테고리 일괄 전파 시 사용
     */
    @Query("UPDATE store_embeddings SET category = :newCategory, source = :source, updatedAt = :timestamp WHERE id = :id AND source != 'user'")
    suspend fun updateCategoryByIdIfNotUser(id: Long, newCategory: String, source: String = "propagated", timestamp: Long = System.currentTimeMillis())

    /** 전체 임베딩 수 조회 */
    @Query("SELECT COUNT(*) FROM store_embeddings")
    suspend fun getEmbeddingCount(): Int

    /** 전체 임베딩 삭제 (DB 초기화 시 사용) */
    @Query("DELETE FROM store_embeddings")
    suspend fun deleteAll()

    /** 모든 임베딩 실시간 관찰 (매칭 횟수 내림차순) - 디버깅/모니터링용 */
    @Query("SELECT * FROM store_embeddings ORDER BY matchCount DESC")
    fun observeAllEmbeddings(): Flow<List<StoreEmbeddingEntity>>
}
