package com.sanha.moneytalk.core.util

import com.sanha.moneytalk.core.database.entity.SmsPatternEntity
import com.sanha.moneytalk.core.database.entity.StoreEmbeddingEntity
import kotlin.math.sqrt

/**
 * 벡터 유사도 검색 엔진
 *
 * 임베딩 벡터간 코사인 유사도를 계산하여
 * 가장 유사한 SMS 패턴을 찾습니다.
 *
 * 코사인 유사도 = (A·B) / (|A| × |B|)
 * 범위: -1 ~ 1 (1에 가까울수록 유사)
 */
object VectorSearchEngine {

    /** 결제 SMS로 판정하는 유사도 임계값 */
    const val PAYMENT_SIMILARITY_THRESHOLD = 0.92f

    /** 캐시 파싱 결과를 재사용하는 유사도 임계값 (더 높은 기준) */
    const val CACHE_REUSE_THRESHOLD = 0.97f

    /** 가게명 벡터 매칭 시 카테고리를 자동 적용하는 유사도 임계값 */
    const val STORE_SIMILARITY_THRESHOLD = 0.92f

    /** 사용자 수정 카테고리를 유사 가게에 전파하는 유사도 임계값 */
    const val PROPAGATION_SIMILARITY_THRESHOLD = 0.90f

    /** 시맨틱 그룹핑 시 같은 그룹으로 묶는 유사도 임계값 */
    const val GROUPING_SIMILARITY_THRESHOLD = 0.88f

    /**
     * 두 벡터 간의 코사인 유사도 계산
     *
     * @param vectorA 첫 번째 벡터
     * @param vectorB 두 번째 벡터
     * @return 코사인 유사도 (-1 ~ 1)
     */
    fun cosineSimilarity(vectorA: List<Float>, vectorB: List<Float>): Float {
        if (vectorA.size != vectorB.size || vectorA.isEmpty()) return 0f

        var dotProduct = 0f
        var normA = 0f
        var normB = 0f

        for (i in vectorA.indices) {
            dotProduct += vectorA[i] * vectorB[i]
            normA += vectorA[i] * vectorA[i]
            normB += vectorB[i] * vectorB[i]
        }

        val denominator = sqrt(normA) * sqrt(normB)
        return if (denominator == 0f) 0f else dotProduct / denominator
    }

    /**
     * 벡터 유사도 검색 결과
     *
     * @property pattern 매칭된 SMS 패턴
     * @property similarity 유사도 점수 (0 ~ 1)
     */
    data class SearchResult(
        val pattern: SmsPatternEntity,
        val similarity: Float
    )

    /**
     * 가장 유사한 패턴 찾기 (Top-K)
     *
     * @param queryVector 검색할 벡터
     * @param patterns DB에 저장된 패턴 목록
     * @param topK 반환할 최대 개수
     * @param minSimilarity 최소 유사도 임계값
     * @return 유사도가 높은 순으로 정렬된 결과 리스트
     */
    fun findTopK(
        queryVector: List<Float>,
        patterns: List<SmsPatternEntity>,
        topK: Int = 3,
        minSimilarity: Float = PAYMENT_SIMILARITY_THRESHOLD
    ): List<SearchResult> {
        return patterns
            .map { pattern ->
                SearchResult(
                    pattern = pattern,
                    similarity = cosineSimilarity(queryVector, pattern.embedding)
                )
            }
            .filter { it.similarity >= minSimilarity }
            .sortedByDescending { it.similarity }
            .take(topK)
    }

    /**
     * 가장 유사한 패턴 1개 찾기
     *
     * @param queryVector 검색할 벡터
     * @param patterns DB에 저장된 패턴 목록
     * @param minSimilarity 최소 유사도 임계값
     * @return 가장 유사한 결과 (임계값 이상인 경우만)
     */
    fun findBestMatch(
        queryVector: List<Float>,
        patterns: List<SmsPatternEntity>,
        minSimilarity: Float = PAYMENT_SIMILARITY_THRESHOLD
    ): SearchResult? {
        var bestMatch: SearchResult? = null

        for (pattern in patterns) {
            val similarity = cosineSimilarity(queryVector, pattern.embedding)
            if (similarity >= minSimilarity) {
                if (bestMatch == null || similarity > bestMatch.similarity) {
                    bestMatch = SearchResult(pattern, similarity)
                }
            }
        }

        return bestMatch
    }

    // ========================
    // 가게명 임베딩 검색 (카테고리 벡터 캐싱)
    // ========================

    /**
     * 가게명 벡터 유사도 검색 결과
     *
     * @property storeEmbedding 매칭된 가게명 임베딩
     * @property similarity 유사도 점수 (0 ~ 1)
     */
    data class StoreSearchResult(
        val storeEmbedding: StoreEmbeddingEntity,
        val similarity: Float
    )

    /**
     * 가장 유사한 가게명 임베딩 1개 찾기
     *
     * @param queryVector 검색할 가게명의 임베딩 벡터
     * @param embeddings DB에 저장된 가게명 임베딩 목록
     * @param minSimilarity 최소 유사도 임계값
     * @return 가장 유사한 결과 (임계값 이상인 경우만)
     */
    fun findBestStoreMatch(
        queryVector: List<Float>,
        embeddings: List<StoreEmbeddingEntity>,
        minSimilarity: Float = STORE_SIMILARITY_THRESHOLD
    ): StoreSearchResult? {
        var bestMatch: StoreSearchResult? = null

        for (embedding in embeddings) {
            val similarity = cosineSimilarity(queryVector, embedding.embedding)
            if (similarity >= minSimilarity) {
                if (bestMatch == null || similarity > bestMatch.similarity) {
                    bestMatch = StoreSearchResult(embedding, similarity)
                }
            }
        }

        return bestMatch
    }

    /**
     * 유사한 가게명 임베딩 전체 찾기 (전파용)
     *
     * 사용자가 카테고리를 수정했을 때, 유사한 다른 가게에도 전파하기 위해
     * 임계값 이상인 모든 가게명을 반환합니다.
     *
     * @param queryVector 기준 가게명의 임베딩 벡터
     * @param embeddings DB에 저장된 가게명 임베딩 목록
     * @param minSimilarity 최소 유사도 임계값
     * @return 유사도가 높은 순으로 정렬된 결과 리스트
     */
    fun findSimilarStores(
        queryVector: List<Float>,
        embeddings: List<StoreEmbeddingEntity>,
        minSimilarity: Float = PROPAGATION_SIMILARITY_THRESHOLD
    ): List<StoreSearchResult> {
        return embeddings
            .map { embedding ->
                StoreSearchResult(
                    storeEmbedding = embedding,
                    similarity = cosineSimilarity(queryVector, embedding.embedding)
                )
            }
            .filter { it.similarity >= minSimilarity }
            .sortedByDescending { it.similarity }
    }
}
