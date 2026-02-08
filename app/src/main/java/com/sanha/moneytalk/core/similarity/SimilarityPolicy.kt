package com.sanha.moneytalk.core.similarity

/**
 * 유사도 판정 정책 인터페이스
 *
 * 도메인별로 유사도 점수를 해석하는 기준을 정의합니다.
 * VectorSearchEngine은 순수 벡터 연산만 담당하고,
 * "유사도 X면 어떤 행동을 할지"는 이 정책이 결정합니다.
 *
 * @see SimilarityProfile
 * @see SmsPatternSimilarityPolicy
 * @see StoreNameSimilarityPolicy
 * @see CategoryPropagationPolicy
 */
interface SimilarityPolicy {

    /** 이 정책의 임계값 프로파일 */
    val profile: SimilarityProfile

    /**
     * 자동 적용 여부 (캐시 재사용, 완전 신뢰)
     * 유사도가 [SimilarityProfile.autoApply] 이상이면 true
     */
    fun shouldAutoApply(similarity: Float): Boolean =
        similarity >= profile.autoApply

    /**
     * 판정 확정 여부 (결제 판정, 카테고리 적용 등)
     * 유사도가 [SimilarityProfile.confirm] 이상이면 true
     */
    fun shouldConfirm(similarity: Float): Boolean =
        similarity >= profile.confirm

    /**
     * 전파 여부 (유사 항목에 결과 전파)
     * 유사도가 [SimilarityProfile.propagate] 이상이면 true
     * propagate가 0이면 항상 false (전파 미지원 도메인)
     */
    fun shouldPropagate(similarity: Float): Boolean =
        profile.propagate > 0f && similarity >= profile.propagate

    /**
     * 그룹핑 여부 (시맨틱 그룹으로 묶기)
     * 유사도가 [SimilarityProfile.group] 이상이면 true
     * group이 0이면 항상 false (그룹핑 미지원 도메인)
     */
    fun shouldGroup(similarity: Float): Boolean =
        profile.group > 0f && similarity >= profile.group

    /**
     * 거부 여부 (이 이하면 매칭 자체를 무시)
     * 유사도가 [SimilarityProfile.reject] 이하이면 true
     * reject가 0이면 항상 false (모든 유사도 허용)
     */
    fun shouldReject(similarity: Float): Boolean =
        profile.reject > 0f && similarity <= profile.reject
}
