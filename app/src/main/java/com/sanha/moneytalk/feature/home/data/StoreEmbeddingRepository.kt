package com.sanha.moneytalk.feature.home.data

import com.sanha.moneytalk.core.database.entity.StoreEmbeddingEntity
import com.sanha.moneytalk.core.util.VectorSearchEngine

/**
 * 가게명 임베딩 Repository 인터페이스
 *
 * 가게명의 벡터 임베딩을 관리하고, 유사도 검색을 통해
 * Gemini API 호출 없이 카테고리를 즉시 반환합니다.
 *
 * 카테고리 분류 4-tier 시스템의 Tier 1.5 역할:
 * ```
 * Tier 1: Room DB 정확 매핑 → 즉시 반환 (CategoryMappingEntity)
 * Tier 1.5: 벡터 유사도 매칭 → 임베딩 API 1회 (THIS)
 * Tier 2: SmsParser 로컬 키워드 → 즉시 반환
 * Tier 3: Gemini 배치 API → API 호출 필요
 * ```
 *
 * @see StoreEmbeddingRepositoryImpl
 * @see VectorSearchEngine
 */
interface StoreEmbeddingRepository {

    /**
     * 가게명 임베딩 벡터 생성
     * CategoryClassifierService에서 Tier 1.5a/b를 한 번의 임베딩으로 처리할 때 사용
     *
     * @param storeName 가게명
     * @return 임베딩 벡터 (생성 실패 시 null)
     */
    suspend fun generateEmbeddingVector(storeName: String): List<Float>?

    /**
     * 가게명으로 카테고리 벡터 검색
     *
     * 1. 가게명의 임베딩 벡터 생성 (Gemini Embedding API 1회)
     * 2. 인메모리 캐시에서 코사인 유사도 검색
     * 3. 유사도 ≥ 0.92이면 해당 카테고리 반환
     *
     * @param storeName 검색할 가게명
     * @param queryVector 미리 생성된 임베딩 벡터 (null이면 내부에서 생성)
     * @return 매칭된 카테고리 (없으면 null)
     */
    suspend fun findCategoryByStoreName(
        storeName: String,
        queryVector: List<Float>? = null
    ): VectorSearchEngine.StoreSearchResult?

    /**
     * 벡터 그룹 기반 카테고리 검색
     *
     * 단일 최고 매칭이 아닌, 유사도 ≥ 그룹핑 임계값(0.88) 이상인
     * 모든 유사 가게를 찾아 그룹의 다수결 카테고리를 반환합니다.
     *
     * 그룹 내 가게들의 카테고리가 모두 같으면 → 해당 카테고리
     * 그룹 내 가게들의 카테고리가 다르면 → 가장 많은 카테고리 (다수결)
     *
     * @param storeName 검색할 가게명
     * @param queryVector 미리 생성된 임베딩 벡터 (null이면 내부에서 생성)
     * @return Pair<카테고리, 유사도> (없으면 null)
     */
    suspend fun findCategoryByGroup(
        storeName: String,
        queryVector: List<Float>? = null
    ): Pair<String, Float>?

    /**
     * 가게명 임베딩 저장 (단일)
     *
     * Gemini 분류 결과나 사용자 수정 결과를 벡터 DB에 저장합니다.
     * 이미 존재하는 가게명이면 덮어씁니다.
     *
     * @param storeName 가게명
     * @param category 분류된 카테고리
     * @param source 분류 출처 (gemini/user/local/propagated)
     * @param confidence 신뢰도 (0.0~1.0)
     */
    suspend fun saveStoreEmbedding(
        storeName: String,
        category: String,
        source: String = "gemini",
        confidence: Float = 0.8f
    )

    /**
     * 가게명 임베딩 일괄 저장 (배치)
     *
     * Gemini 배치 분류 결과를 한꺼번에 벡터 DB에 저장합니다.
     * 배치 임베딩 API를 사용하여 효율적으로 처리합니다.
     *
     * @param storeCategories 가게명→카테고리 매핑 목록
     * @param source 분류 출처
     */
    suspend fun saveStoreEmbeddings(
        storeCategories: Map<String, String>,
        source: String = "gemini"
    )

    /**
     * 사용자 수정 카테고리를 유사 가게에 전파
     *
     * 사용자가 "스타벅스강남점"의 카테고리를 수정하면,
     * 벡터 유사도 ≥ 0.90인 다른 가게("스타벅스역삼점" 등)에도
     * 같은 카테고리를 전파합니다.
     *
     * 단, source="user"인 가게는 덮어쓰지 않음 (사용자 직접 설정 우선)
     *
     * @param storeName 수정된 가게명
     * @param newCategory 새 카테고리
     * @param confidence 분류 신뢰도 (기본 1.0, 사용자 수정은 항상 1.0)
     * @return 전파된 가게 수
     */
    suspend fun propagateCategoryToSimilarStores(
        storeName: String,
        newCategory: String,
        confidence: Float = 1.0f
    ): Int

    /**
     * 가게명의 카테고리 업데이트 (이미 존재하는 임베딩)
     *
     * @param storeName 가게명
     * @param newCategory 새 카테고리
     * @param source 변경 출처
     */
    suspend fun updateCategory(storeName: String, newCategory: String, source: String = "user")

    /**
     * 가게명이 벡터 DB에 존재하는지 확인
     */
    suspend fun hasEmbedding(storeName: String): Boolean

    /**
     * 카테고리 UPSERT (존재하면 카테고리만 업데이트, 없으면 임베딩 생성+저장)
     * hasEmbedding() + updateCategory()/saveStoreEmbedding() 2~3회 DB 접근을
     * 1회 DB 접근 + 필요시 임베딩 API 1회로 줄입니다.
     */
    suspend fun upsertCategory(storeName: String, newCategory: String, source: String = "user")

    /**
     * 전체 임베딩 수 조회
     */
    suspend fun getEmbeddingCount(): Int

    /**
     * 저신뢰도 임베딩 조회 (재분류 대상)
     */
    suspend fun getLowConfidenceEmbeddings(threshold: Float = 0.95f): List<StoreEmbeddingEntity>
}
