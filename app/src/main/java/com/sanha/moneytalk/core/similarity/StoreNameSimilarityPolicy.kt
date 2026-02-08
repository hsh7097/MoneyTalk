package com.sanha.moneytalk.core.similarity

/**
 * 가게명 유사도 판정 정책
 *
 * 가게명 임베딩 벡터를 이용한 카테고리 자동 적용, 전파, 그룹핑에
 * 사용되는 임계값을 관리합니다.
 *
 * 임계값 매핑 (기존 VectorSearchEngine 상수 → SimilarityProfile):
 * - autoApply = 0.92 (STORE_SIMILARITY_THRESHOLD) → 가게명 → 카테고리 자동 적용
 * - confirm = 0.92 (STORE_SIMILARITY_THRESHOLD) → 카테고리 매칭 확정
 * - propagate = 0.90 (PROPAGATION_SIMILARITY_THRESHOLD) → 유사 가게 카테고리 전파
 * - group = 0.88 (GROUPING_SIMILARITY_THRESHOLD) → 가게명 시맨틱 그룹핑
 *
 * @see com.sanha.moneytalk.feature.home.data.StoreEmbeddingRepository
 * @see com.sanha.moneytalk.core.util.StoreNameGrouper
 */
object StoreNameSimilarityPolicy : SimilarityPolicy {

    override val profile = SimilarityProfile(
        autoApply = 0.92f,   // STORE_SIMILARITY_THRESHOLD: 가게명 → 카테고리 자동 적용
        confirm = 0.92f,     // STORE_SIMILARITY_THRESHOLD: 매칭 확정
        propagate = 0.90f,   // PROPAGATION_SIMILARITY_THRESHOLD: 유사 가게 전파
        group = 0.88f        // GROUPING_SIMILARITY_THRESHOLD: 시맨틱 그룹핑
    )
}
