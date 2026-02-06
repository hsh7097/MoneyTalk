package com.sanha.moneytalk.core.util

import kotlin.math.sqrt

/**
 * 벡터 연산 유틸리티
 * 코사인 유사도 계산 및 벡터 검색 알고리즘
 */
object VectorUtils {

    /**
     * 코사인 유사도 계산
     * @return -1.0 ~ 1.0 (1.0이 가장 유사)
     */
    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        require(a.size == b.size) { "Vector dimensions must match: ${a.size} vs ${b.size}" }

        var dotProduct = 0f
        var normA = 0f
        var normB = 0f

        for (i in a.indices) {
            dotProduct += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }

        val denominator = sqrt(normA) * sqrt(normB)
        return if (denominator == 0f) 0f else dotProduct / denominator
    }

    /**
     * 벡터 목록에서 가장 유사한 항목 찾기
     * @return (인덱스, 유사도) 쌍, 없으면 null
     */
    fun findMostSimilar(
        query: FloatArray,
        candidates: List<FloatArray>,
        threshold: Float = 0.9f
    ): Pair<Int, Float>? {
        var bestIndex = -1
        var bestSimilarity = -1f

        for (i in candidates.indices) {
            val similarity = cosineSimilarity(query, candidates[i])
            if (similarity > bestSimilarity) {
                bestSimilarity = similarity
                bestIndex = i
            }
        }

        return if (bestIndex >= 0 && bestSimilarity >= threshold) {
            Pair(bestIndex, bestSimilarity)
        } else {
            null
        }
    }

    /**
     * 벡터 목록에서 임계값 이상인 모든 유사 항목 찾기
     * @return (인덱스, 유사도) 리스트 (유사도 내림차순)
     */
    fun findAllSimilar(
        query: FloatArray,
        candidates: List<FloatArray>,
        threshold: Float = 0.9f
    ): List<Pair<Int, Float>> {
        return candidates.mapIndexedNotNull { index, candidate ->
            val similarity = cosineSimilarity(query, candidate)
            if (similarity >= threshold) Pair(index, similarity) else null
        }.sortedByDescending { it.second }
    }
}
