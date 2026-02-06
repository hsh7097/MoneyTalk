package com.sanha.moneytalk.feature.home.data

import android.util.Log
import com.sanha.moneytalk.core.database.dao.StoreEmbeddingDao
import com.sanha.moneytalk.core.database.entity.StoreEmbeddingEntity
import com.sanha.moneytalk.core.util.SmsEmbeddingService
import com.sanha.moneytalk.core.util.VectorSearchEngine
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 가게명 임베딩 Repository
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
 * 인메모리 캐시 전략:
 * - 최초 조회 시 전체 임베딩을 DB에서 로드하여 메모리에 캐시
 * - 새 임베딩 저장/업데이트 시 캐시도 동시 갱신 (invalidation)
 * - 수백~수천 개 가게명 (각 ~3KB) → 수 MB 수준으로 메모리 부담 낮음
 *
 * @see StoreEmbeddingDao
 * @see VectorSearchEngine
 * @see CategoryClassifierService
 */
@Singleton
class StoreEmbeddingRepository @Inject constructor(
    private val storeEmbeddingDao: StoreEmbeddingDao,
    private val embeddingService: SmsEmbeddingService
) {
    companion object {
        private const val TAG = "StoreEmbedding"
    }

    /** 인메모리 캐시 (전체 임베딩) */
    private var cachedEmbeddings: List<StoreEmbeddingEntity>? = null

    /**
     * 캐시된 전체 임베딩 목록 조회
     * 최초 호출 시 DB에서 로드, 이후 캐시 반환
     */
    private suspend fun getEmbeddings(): List<StoreEmbeddingEntity> {
        return cachedEmbeddings ?: storeEmbeddingDao.getAllEmbeddings().also {
            cachedEmbeddings = it
            Log.d(TAG, "임베딩 캐시 로드: ${it.size}건")
        }
    }

    /** 캐시 무효화 (DB 변경 후 호출) */
    private fun invalidateCache() {
        cachedEmbeddings = null
    }

    /**
     * 가게명으로 카테고리 벡터 검색
     *
     * 1. 가게명의 임베딩 벡터 생성 (Gemini Embedding API 1회)
     * 2. 인메모리 캐시에서 코사인 유사도 검색
     * 3. 유사도 ≥ 0.92이면 해당 카테고리 반환
     *
     * @param storeName 검색할 가게명
     * @return 매칭된 카테고리 (없으면 null)
     */
    suspend fun findCategoryByStoreName(storeName: String): VectorSearchEngine.StoreSearchResult? {
        try {
            val embeddings = getEmbeddings()
            if (embeddings.isEmpty()) {
                Log.d(TAG, "임베딩 DB 비어있음, 벡터 검색 건너뜀")
                return null
            }

            // 가게명 임베딩 생성
            val queryVector = embeddingService.generateEmbedding(storeName)
            if (queryVector == null) {
                Log.w(TAG, "임베딩 생성 실패: $storeName")
                return null
            }

            // 코사인 유사도 검색
            val bestMatch = VectorSearchEngine.findBestStoreMatch(
                queryVector = queryVector,
                embeddings = embeddings,
                minSimilarity = VectorSearchEngine.STORE_SIMILARITY_THRESHOLD
            )

            if (bestMatch != null) {
                Log.d(TAG, "벡터 매칭: '$storeName' → '${bestMatch.storeEmbedding.storeName}' " +
                        "(${bestMatch.storeEmbedding.category}, 유사도 ${bestMatch.similarity})")
                storeEmbeddingDao.incrementMatchCount(bestMatch.storeEmbedding.id)
            } else {
                Log.d(TAG, "벡터 매칭 실패: '$storeName' (유사도 미달)")
            }

            return bestMatch
        } catch (e: Exception) {
            Log.e(TAG, "벡터 검색 실패: ${e.message}", e)
            return null
        }
    }

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
    ) {
        try {
            val embedding = embeddingService.generateEmbedding(storeName)
            if (embedding == null) {
                Log.w(TAG, "임베딩 생성 실패, 저장 건너뜀: $storeName")
                return
            }

            val entity = StoreEmbeddingEntity(
                storeName = storeName,
                category = category,
                embedding = embedding,
                source = source,
                confidence = confidence
            )

            storeEmbeddingDao.insertOrReplace(entity)
            invalidateCache()
            Log.d(TAG, "임베딩 저장: $storeName → $category (source=$source)")
        } catch (e: Exception) {
            Log.e(TAG, "임베딩 저장 실패: ${e.message}", e)
        }
    }

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
    ) {
        if (storeCategories.isEmpty()) return

        try {
            val storeNames = storeCategories.keys.toList()
            val embeddings = embeddingService.generateEmbeddings(storeNames)

            val entities = storeNames.mapIndexedNotNull { index, storeName ->
                val embedding = embeddings.getOrNull(index) ?: return@mapIndexedNotNull null
                val category = storeCategories[storeName] ?: return@mapIndexedNotNull null

                StoreEmbeddingEntity(
                    storeName = storeName,
                    category = category,
                    embedding = embedding,
                    source = source,
                    confidence = if (source == "user") 1.0f else 0.8f
                )
            }

            if (entities.isNotEmpty()) {
                storeEmbeddingDao.insertAll(entities)
                invalidateCache()
                Log.d(TAG, "배치 임베딩 저장: ${entities.size}건 (source=$source)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "배치 임베딩 저장 실패: ${e.message}", e)
        }
    }

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
     * @return 전파된 가게 수
     */
    suspend fun propagateCategoryToSimilarStores(
        storeName: String,
        newCategory: String
    ): Int {
        try {
            val queryVector = embeddingService.generateEmbedding(storeName)
            if (queryVector == null) {
                Log.w(TAG, "전파 실패 (임베딩 생성 실패): $storeName")
                return 0
            }

            val embeddings = getEmbeddings()
            val similarStores = VectorSearchEngine.findSimilarStores(
                queryVector = queryVector,
                embeddings = embeddings,
                minSimilarity = VectorSearchEngine.PROPAGATION_SIMILARITY_THRESHOLD
            )

            var propagatedCount = 0
            for (result in similarStores) {
                // 자기 자신은 건너뜀
                if (result.storeEmbedding.storeName == storeName) continue
                // 이미 같은 카테고리면 건너뜀
                if (result.storeEmbedding.category == newCategory) continue

                storeEmbeddingDao.updateCategoryByIdIfNotUser(
                    id = result.storeEmbedding.id,
                    newCategory = newCategory,
                    source = "propagated"
                )
                propagatedCount++

                Log.d(TAG, "카테고리 전파: '${result.storeEmbedding.storeName}' → $newCategory " +
                        "(유사도 ${result.similarity})")
            }

            if (propagatedCount > 0) {
                invalidateCache()
            }

            Log.d(TAG, "전파 완료: $storeName → $newCategory, ${propagatedCount}건 전파됨")
            return propagatedCount
        } catch (e: Exception) {
            Log.e(TAG, "카테고리 전파 실패: ${e.message}", e)
            return 0
        }
    }

    /**
     * 가게명의 카테고리 업데이트 (이미 존재하는 임베딩)
     *
     * @param storeName 가게명
     * @param newCategory 새 카테고리
     * @param source 변경 출처
     */
    suspend fun updateCategory(storeName: String, newCategory: String, source: String = "user") {
        try {
            storeEmbeddingDao.updateCategory(storeName, newCategory, source)
            invalidateCache()
            Log.d(TAG, "카테고리 업데이트: $storeName → $newCategory (source=$source)")
        } catch (e: Exception) {
            Log.e(TAG, "카테고리 업데이트 실패: ${e.message}", e)
        }
    }

    /**
     * 가게명이 벡터 DB에 존재하는지 확인
     */
    suspend fun hasEmbedding(storeName: String): Boolean {
        return storeEmbeddingDao.getByStoreName(storeName) != null
    }

    /**
     * 전체 임베딩 수 조회
     */
    suspend fun getEmbeddingCount(): Int {
        return storeEmbeddingDao.getEmbeddingCount()
    }
}
