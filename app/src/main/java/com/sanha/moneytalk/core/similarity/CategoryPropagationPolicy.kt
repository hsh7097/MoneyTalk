package com.sanha.moneytalk.core.similarity

import com.sanha.moneytalk.core.similarity.CategoryPropagationPolicy.MIN_PROPAGATION_CONFIDENCE


/**
 * 카테고리 전파 정책
 *
 * 사용자가 카테고리를 수정했을 때 유사한 가게에 자동 전파하거나,
 * Gemini 배치 분류 결과를 그룹 멤버에 전파할 때 사용하는 정책입니다.
 *
 * 특이사항:
 * - confidence < [MIN_PROPAGATION_CONFIDENCE]인 항목은 전파를 차단합니다.
 * - 이는 LLM 추출 결과의 신뢰도가 낮은 경우 오분류 전파를 방지합니다.
 *
 * @see com.sanha.moneytalk.feature.home.data.StoreEmbeddingRepository
 * @see com.sanha.moneytalk.feature.home.data.CategoryClassifierService
 */
object CategoryPropagationPolicy : SimilarityPolicy {

    override val profile = SimilarityProfile(
        autoApply = 0.92f,   // 카테고리 자동 적용
        confirm = 0.92f,     // 확정
        propagate = 0.90f,   // 유사 가게 전파 (PROPAGATION_SIMILARITY_THRESHOLD)
        group = 0.88f        // 그룹핑 (GROUPING_SIMILARITY_THRESHOLD)
    )

    /**
     * 자동 전파를 허용하는 최소 confidence
     *
     * confidence가 이 값 미만인 항목은 유사 가게 전파/승격이 차단됩니다.
     * 현재 시스템에서 confidence는:
     * - Regex: 1.0 (항상 전파 허용)
     * - LLM: 0.8 (전파 허용)
     * - 향후 0.6 미만 항목이 추가되면 자동 차단
     */
    const val MIN_PROPAGATION_CONFIDENCE = 0.6f

    /**
     * 전파 허용 여부 (유사도 + confidence 모두 충족해야 함)
     *
     * @param similarity 코사인 유사도
     * @param confidence 분류 신뢰도 (0.0 ~ 1.0)
     * @return true면 전파 허용
     */
    fun shouldPropagateWithConfidence(similarity: Float, confidence: Float): Boolean =
        shouldPropagate(similarity) && confidence >= MIN_PROPAGATION_CONFIDENCE
}
