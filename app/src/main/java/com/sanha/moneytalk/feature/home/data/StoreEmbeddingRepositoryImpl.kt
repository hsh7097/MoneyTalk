package com.sanha.moneytalk.feature.home.data

import android.util.Log
import com.sanha.moneytalk.core.database.dao.StoreEmbeddingDao
import com.sanha.moneytalk.core.database.entity.StoreEmbeddingEntity
import com.sanha.moneytalk.core.similarity.CategoryPropagationPolicy
import com.sanha.moneytalk.core.similarity.StoreNameSimilarityPolicy
import com.sanha.moneytalk.core.sms.SmsEmbeddingService
import com.sanha.moneytalk.core.sms.VectorSearchEngine
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 가게명 임베딩 Repository 구현체
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
 * @see StoreEmbeddingRepository
 * @see StoreEmbeddingDao
 * @see VectorSearchEngine
 * @see CategoryClassifierService
 */
@Singleton
class StoreEmbeddingRepositoryImpl @Inject constructor(
    private val storeEmbeddingDao: StoreEmbeddingDao,
    private val embeddingService: SmsEmbeddingService
) : StoreEmbeddingRepository {

    companion object {
        private const val TAG = "StoreEmbedding"

        /** 임베딩 배치 병렬 동시 실행 수 (API 키 5개 × 키당 2 = 10) */
        private const val EMBEDDING_CONCURRENCY = 10

        /** 배치 임베딩 처리 크기 (batchEmbedContents 최대값) */
        private const val EMBEDDING_BATCH_SIZE = 100
    }

    /** 인메모리 캐시 (전체 임베딩) */
    private var cachedEmbeddings: List<StoreEmbeddingEntity>? = null

    /** 현재 임베딩 생성 중인 가게명 (동시 중복 요청 방지) */
    private val inFlightStoreNames: MutableSet<String> =
        ConcurrentHashMap.newKeySet()

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

    override suspend fun generateEmbeddingVector(storeName: String): List<Float>? {
        return try {
            embeddingService.generateEmbedding(storeName)
        } catch (e: Exception) {
            Log.e(TAG, "임베딩 생성 실패: ${e.message}", e)
            null
        }
    }

    override suspend fun findCategoryByStoreName(
        storeName: String,
        queryVector: List<Float>?
    ): VectorSearchEngine.StoreSearchResult? {
        try {
            val embeddings = getEmbeddings()
            if (embeddings.isEmpty()) {
                Log.d(TAG, "임베딩 DB 비어있음, 벡터 검색 건너뜀")
                return null
            }

            // 가게명 임베딩: 외부 제공 또는 내부 생성
            val vector = queryVector ?: embeddingService.generateEmbedding(storeName)
            if (vector == null) {
                Log.w(TAG, "임베딩 생성 실패: $storeName")
                return null
            }

            // 코사인 유사도 검색
            val bestMatch = VectorSearchEngine.findBestStoreMatch(
                queryVector = vector,
                embeddings = embeddings,
                minSimilarity = StoreNameSimilarityPolicy.profile.autoApply
            )

            if (bestMatch != null) {
                Log.d(
                    TAG, "벡터 매칭: '$storeName' → '${bestMatch.storeEmbedding.storeName}' " +
                            "(${bestMatch.storeEmbedding.category}, 유사도 ${bestMatch.similarity})"
                )
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

    override suspend fun findCategoryByGroup(
        storeName: String,
        queryVector: List<Float>?
    ): Pair<String, Float>? {
        try {
            val embeddings = getEmbeddings()
            if (embeddings.isEmpty()) return null

            // 가게명 임베딩: 외부 제공 또는 내부 생성
            val vector = queryVector ?: embeddingService.generateEmbedding(storeName) ?: return null

            // 그룹핑 임계값 이상인 모든 유사 가게 검색
            val similarStores = VectorSearchEngine.findSimilarStores(
                queryVector = vector,
                embeddings = embeddings,
                minSimilarity = StoreNameSimilarityPolicy.profile.group
            )

            if (similarStores.isEmpty()) return null

            // 그룹의 다수결 카테고리 결정
            val categoryCounts = similarStores
                .groupBy { it.storeEmbedding.category }
                .mapValues { (_, results) -> results.size }

            val dominantCategory = categoryCounts.maxByOrNull { it.value }?.key ?: return null
            val avgSimilarity = similarStores.map { it.similarity }.average().toFloat()

            Log.d(
                TAG, "그룹 매칭: '$storeName' → '$dominantCategory' " +
                        "(그룹 ${similarStores.size}개, 평균 유사도 $avgSimilarity)"
            )

            // 매칭된 가게들의 카운트 증가
            for (result in similarStores) {
                storeEmbeddingDao.incrementMatchCount(result.storeEmbedding.id)
            }

            return dominantCategory to avgSimilarity
        } catch (e: Exception) {
            Log.e(TAG, "그룹 검색 실패: ${e.message}", e)
            return null
        }
    }

    override suspend fun saveStoreEmbedding(
        storeName: String,
        category: String,
        source: String,
        confidence: Float
    ) {
        // inFlight 중복 방지
        if (!inFlightStoreNames.add(storeName)) {
            Log.d(TAG, "임베딩 생성 중복 스킵 (inFlight): $storeName")
            return
        }

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
        } finally {
            inFlightStoreNames.remove(storeName)
        }
    }

    override suspend fun saveStoreEmbeddings(
        storeCategories: Map<String, String>,
        source: String
    ) {
        if (storeCategories.isEmpty()) return

        try {
            // inFlight 중복 제거: 이미 임베딩 생성 중인 가게명 스킵
            val storeNames = storeCategories.keys.filter { inFlightStoreNames.add(it) }
            if (storeNames.isEmpty()) {
                Log.d(TAG, "배치 임베딩: 모두 inFlight 중복 → 스킵")
                return
            }
            val skipped = storeCategories.size - storeNames.size
            if (skipped > 0) {
                Log.d(TAG, "배치 임베딩: ${skipped}건 inFlight 중복 제거")
            }

            try {
                // 100건씩 청킹 → 병렬 임베딩 생성
                val chunks = storeNames.chunked(EMBEDDING_BATCH_SIZE)
                val semaphore = Semaphore(EMBEDDING_CONCURRENCY)
                val batchEmbeddings = coroutineScope {
                    chunks.map { chunk ->
                        async {
                            semaphore.withPermit {
                                embeddingService.generateEmbeddings(chunk)
                            }
                        }
                    }.awaitAll()
                }

                val allEntities = mutableListOf<StoreEmbeddingEntity>()
                for ((chunkIdx, chunk) in chunks.withIndex()) {
                    val embeddings = batchEmbeddings[chunkIdx]
                    val entities = chunk.mapIndexedNotNull { index, storeName ->
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
                    allEntities.addAll(entities)
                }

                if (allEntities.isNotEmpty()) {
                    storeEmbeddingDao.insertAll(allEntities)
                    invalidateCache()
                    Log.d(TAG, "배치 임베딩 저장: ${allEntities.size}건 (source=$source)")
                }
            } finally {
                // 완료 후 inFlight에서 제거
                storeNames.forEach { inFlightStoreNames.remove(it) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "배치 임베딩 저장 실패: ${e.message}", e)
        }
    }

    override suspend fun propagateCategoryToSimilarStores(
        storeName: String,
        newCategory: String,
        confidence: Float
    ): Int {
        // confidence가 낮으면 전파 차단 (오분류 전파 방지)
        if (!CategoryPropagationPolicy.shouldPropagateWithConfidence(
                similarity = CategoryPropagationPolicy.profile.propagate,
                confidence = confidence
            )
        ) {
            Log.d(
                TAG,
                "전파 차단: $storeName → $newCategory (confidence=$confidence < ${CategoryPropagationPolicy.MIN_PROPAGATION_CONFIDENCE})"
            )
            return 0
        }

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
                minSimilarity = StoreNameSimilarityPolicy.profile.propagate
            )

            var propagatedCount = 0
            for (result in similarStores) {
                // 자기 자신은 건너뜀
                if (result.storeEmbedding.storeName == storeName) continue
                // 이미 같은 카테고리면 건너뜀
                if (result.storeEmbedding.category == newCategory) continue
                // confidence+유사도 통합 체크
                if (!CategoryPropagationPolicy.shouldPropagateWithConfidence(
                        result.similarity,
                        confidence
                    )
                ) continue

                storeEmbeddingDao.updateCategoryByIdIfNotUser(
                    id = result.storeEmbedding.id,
                    newCategory = newCategory,
                    source = "propagated"
                )
                propagatedCount++

                Log.d(
                    TAG, "카테고리 전파: '${result.storeEmbedding.storeName}' → $newCategory " +
                            "(유사도 ${result.similarity}, confidence=$confidence)"
                )
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

    override suspend fun updateCategory(storeName: String, newCategory: String, source: String) {
        try {
            storeEmbeddingDao.updateCategory(storeName, newCategory, source)
            invalidateCache()
            Log.d(TAG, "카테고리 업데이트: $storeName → $newCategory (source=$source)")
        } catch (e: Exception) {
            Log.e(TAG, "카테고리 업데이트 실패: ${e.message}", e)
        }
    }

    override suspend fun hasEmbedding(storeName: String): Boolean {
        return storeEmbeddingDao.getByStoreName(storeName) != null
    }

    override suspend fun upsertCategory(storeName: String, newCategory: String, source: String) {
        try {
            val existing = storeEmbeddingDao.getByStoreName(storeName)
            if (existing != null) {
                // 기존 임베딩 존재: 카테고리만 업데이트 (임베딩 API 호출 불필요)
                storeEmbeddingDao.updateCategory(storeName, newCategory, source)
                invalidateCache()
            } else {
                // 새 가게명: 임베딩 생성 + 저장
                saveStoreEmbedding(storeName, newCategory, source, 1.0f)
            }
        } catch (e: Exception) {
            Log.e(TAG, "UPSERT 실패: ${e.message}", e)
        }
    }

    override suspend fun getEmbeddingCount(): Int {
        return storeEmbeddingDao.getEmbeddingCount()
    }

    override suspend fun getLowConfidenceEmbeddings(threshold: Float): List<StoreEmbeddingEntity> {
        Log.e(
            "MT_DEBUG",
            "StoreEmbeddingRepository[getLowConfidenceEmbeddings] : threshold=$threshold 조회 시작"
        )
        val result = storeEmbeddingDao.getLowConfidenceEmbeddings(threshold)
        Log.e("MT_DEBUG", "StoreEmbeddingRepository[getLowConfidenceEmbeddings] : ${result.size}건 발견")
        return result
    }
}
