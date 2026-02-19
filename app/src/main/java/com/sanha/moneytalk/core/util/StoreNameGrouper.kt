package com.sanha.moneytalk.core.util

import android.util.Log
import com.sanha.moneytalk.core.similarity.StoreNameSimilarityPolicy
import com.sanha.moneytalk.core.sms.SmsEmbeddingService
import com.sanha.moneytalk.core.sms.VectorSearchEngine
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 가게명 시맨틱 그룹핑
 *
 * 유사한 가게명들을 벡터 유사도 기반으로 그룹핑하여
 * Gemini API 호출 수를 줄입니다.
 *
 * 예시:
 * - "스타벅스강남점", "스타벅스역삼점", "스타벅스서초점" → 1개 그룹 (대표: 스타벅스강남점)
 * - "이마트24강남", "이마트24역삼" → 1개 그룹 (대표: 이마트24강남)
 * - "맥도날드", "버거킹" → 2개 그룹 (유사하지만 다른 가게)
 *
 * 알고리즘: 그리디 클러스터링 (SmsBatchProcessor.groupBySimilarity와 동일 패턴)
 * 1. 각 가게명의 임베딩 벡터 생성 (배치 API)
 * 2. 첫 가게명을 그룹 중심으로, 유사도 ≥ 0.88이면 같은 그룹
 * 3. 각 그룹의 대표만 Gemini에 전송
 *
 * @see SmsBatchProcessor.groupBySimilarity 동일 알고리즘 참고
 * @see StoreNameSimilarityPolicy 가게명 유사도 정책 (그룹핑 임계값 0.88)
 */
@Singleton
class StoreNameGrouper @Inject constructor(
    private val embeddingService: SmsEmbeddingService
) {
    companion object {
        private const val TAG = "StoreNameGrouper"

        /** 배치 임베딩 한 번에 처리할 최대 개수 (batchEmbedContents 최대 100) */
        private const val EMBEDDING_BATCH_SIZE = 100
    }

    /**
     * 가게명 그룹
     *
     * @property representative 대표 가게명 (Gemini에 전송될 가게명)
     * @property members 이 그룹에 속한 모든 가게명 (대표 포함)
     */
    data class StoreGroup(
        val representative: String,
        val members: List<String>
    )

    /**
     * 가게명 목록을 시맨틱 유사도로 그룹핑
     *
     * @param storeNames 그룹핑할 가게명 목록
     * @return 그룹 목록 (그룹 크기 큰 순으로 정렬)
     */
    suspend fun groupStoreNames(storeNames: List<String>): List<StoreGroup> {
        if (storeNames.size <= 1) {
            return storeNames.map { StoreGroup(representative = it, members = listOf(it)) }
        }

        // Step 1: 배치 임베딩 생성
        val embeddedStores = generateBatchEmbeddings(storeNames)

        if (embeddedStores.isEmpty()) {
            Log.w(TAG, "임베딩 생성 실패, 그룹핑 없이 반환")
            return storeNames.map { StoreGroup(representative = it, members = listOf(it)) }
        }

        // Step 2: 그리디 클러스터링
        val groups = clusterByGreedy(embeddedStores)

        Log.d(TAG, "그룹핑 결과: ${storeNames.size}개 → ${groups.size}그룹")
        for (group in groups) {
            if (group.members.size > 1) {
                Log.d(TAG, "  그룹 [${group.representative}]: ${group.members.joinToString(", ")}")
            }
        }

        return groups
    }

    /**
     * 배치 임베딩 생성
     *
     * @return (가게명, 임베딩 벡터) 쌍의 목록
     */
    private suspend fun generateBatchEmbeddings(
        storeNames: List<String>
    ): List<Pair<String, List<Float>>> {
        val results = mutableListOf<Pair<String, List<Float>>>()
        val batches = storeNames.chunked(EMBEDDING_BATCH_SIZE)

        for ((batchIdx, batch) in batches.withIndex()) {
            val startTime = System.currentTimeMillis()
            Log.d(TAG, "[grouping] 가게명 임베딩 배치 ${batchIdx + 1}/${batches.size} 시작 (${batch.size}건)")
            val embeddings = embeddingService.generateEmbeddings(batch)
            val elapsed = System.currentTimeMillis() - startTime

            var batchSuccess = 0
            for ((i, storeName) in batch.withIndex()) {
                val embedding = embeddings.getOrNull(i)
                if (embedding != null) {
                    results.add(storeName to embedding)
                    batchSuccess++
                }
            }
            Log.d(
                TAG,
                "[grouping] 가게명 임베딩 배치 ${batchIdx + 1}/${batches.size} 완료 (${elapsed}ms, 성공: ${batchSuccess}/${batch.size})"
            )

            // 배치 간 최소 딜레이 (마지막 배치 제외)
            if (batchIdx < batches.size - 1) {
                kotlinx.coroutines.delay(200)
            }
        }

        Log.d(TAG, "배치 임베딩 생성: ${results.size}/${storeNames.size}건 성공")
        return results
    }

    /**
     * 그리디 클러스터링
     *
     * 첫 가게명을 그룹 중심으로, 아직 배정 안 된 가게명 중
     * 유사도 ≥ GROUPING_SIMILARITY_THRESHOLD인 가게명을 같은 그룹에 배정.
     *
     * @return 그룹 크기가 큰 순으로 정렬된 StoreGroup 리스트
     */
    private fun clusterByGreedy(
        embeddedStores: List<Pair<String, List<Float>>>
    ): List<StoreGroup> {
        val groups = mutableListOf<StoreGroup>()
        val assigned = BooleanArray(embeddedStores.size)

        for (i in embeddedStores.indices) {
            if (assigned[i]) continue

            val (nameI, embeddingI) = embeddedStores[i]
            val members = mutableListOf(nameI)

            // 이 가게명과 유사한 나머지를 찾아 그룹에 추가
            for (j in (i + 1) until embeddedStores.size) {
                if (assigned[j]) continue

                val (nameJ, embeddingJ) = embeddedStores[j]
                val similarity = VectorSearchEngine.cosineSimilarity(embeddingI, embeddingJ)

                if (StoreNameSimilarityPolicy.shouldGroup(similarity)) {
                    members.add(nameJ)
                    assigned[j] = true
                }
            }

            assigned[i] = true
            groups.add(StoreGroup(representative = nameI, members = members))
        }

        // 그룹 크기가 큰 순으로 정렬 (중요한 패턴 우선)
        return groups.sortedByDescending { it.members.size }
    }
}
