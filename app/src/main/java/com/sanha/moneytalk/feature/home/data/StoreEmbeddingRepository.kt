package com.sanha.moneytalk.feature.home.data

import android.util.Log
import com.sanha.moneytalk.core.database.dao.StoreEmbeddingDao
import com.sanha.moneytalk.core.database.entity.StoreEmbeddingEntity
import com.sanha.moneytalk.core.similarity.CategoryPropagationPolicy
import com.sanha.moneytalk.core.similarity.StoreNameSimilarityPolicy
import com.sanha.moneytalk.core.util.SmsEmbeddingService
import com.sanha.moneytalk.core.util.VectorSearchEngine
import java.util.concurrent.ConcurrentHashMap
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

    /**
     * 가게명 임베딩 벡터 생성
     * CategoryClassifierService에서 Tier 1.5a/b를 한 번의 임베딩으로 처리할 때 사용
     *
     * @param storeName 가게명
     * @return 임베딩 벡터 (생성 실패 시 null)
     */
    suspend fun generateEmbeddingVector(storeName: String): List<Float>? {
        return try {
            embeddingService.generateEmbedding(storeName)
        } catch (e: Exception) {
            Log.e(TAG, "임베딩 생성 실패: ${e.message}", e)
            null
        }
    }

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

    /**
     * 벡터 그룹 기반 카테고리 검색
     *
     * 단일 최고 매칭이 아닌, 유사도 ≥ 그룹핑 임계값(0.88) 이상인
     * 모든 유사 가게를 찾아 그룹의 다수결 카테고리를 반환합니다.
     *
     * 그룹 내 가게들의 카테고리가 모두 같으면 → 해당 카테고리
     * 그룹 내 가게들의 카테고리가 다르면 → 가장 많은 카테고리 (다수결)
     *
     * autoApply 임계값(0.92) 미만이지만 group 임계값(0.88) 이상인
     * 경우에도 그룹 카테고리를 활용할 수 있어 분류 범위가 넓어집니다.
     *
     * @param storeName 검색할 가게명
     * @param queryVector 미리 생성된 임베딩 벡터 (null이면 내부에서 생성)
     * @return Pair<카테고리, 유사도> (없으면 null)
     */
    suspend fun findCategoryByGroup(
        storeName: String,
        queryVector: List<Float>? = null
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
                val allEntities = mutableListOf<StoreEmbeddingEntity>()

                // 100건씩 청킹 (batchEmbedContents 최대 100)
                for (chunk in storeNames.chunked(100)) {
                    val embeddings = embeddingService.generateEmbeddings(chunk)

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
     * 카테고리 UPSERT (존재하면 카테고리만 업데이트, 없으면 임베딩 생성+저장)
     * hasEmbedding() + updateCategory()/saveStoreEmbedding() 2~3회 DB 접근을
     * 1회 DB 접근 + 필요시 임베딩 API 1회로 줄입니다.
     */
    suspend fun upsertCategory(storeName: String, newCategory: String, source: String = "user") {
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

    /**
     * 전체 임베딩 수 조회
     */
    suspend fun getEmbeddingCount(): Int {
        return storeEmbeddingDao.getEmbeddingCount()
    }

    /**
     * 저신뢰도 임베딩 조회 (재분류 대상)
     */
    suspend fun getLowConfidenceEmbeddings(threshold: Float = 0.95f): List<StoreEmbeddingEntity> {
        Log.e(
            "sanha",
            "StoreEmbeddingRepository[getLowConfidenceEmbeddings] : threshold=$threshold 조회 시작"
        )
        val result = storeEmbeddingDao.getLowConfidenceEmbeddings(threshold)
        Log.e("sanha", "StoreEmbeddingRepository[getLowConfidenceEmbeddings] : ${result.size}건 발견")
        return result
    }

}
