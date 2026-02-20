package com.sanha.moneytalk.core.similarity

/**
 * SMS 패턴 유사도 판정 정책
 *
 * SmsPatternMatcher의 벡터 캐시 매칭 및 SmsGroupClassifier의
 * SMS 그룹핑에 사용되는 임계값을 관리합니다.
 *
 * 임계값 매핑 (기존 VectorSearchEngine 상수 → SimilarityProfile):
 * - autoApply = 0.95 (CACHE_REUSE_THRESHOLD) → 캐시된 파싱 결과 재사용
 * - confirm = 0.92 (PAYMENT_SIMILARITY_THRESHOLD) → 결제 문자 판정
 * - group = 0.95 (SmsGroupClassifier.GROUPING_SIMILARITY) → SMS 패턴 그룹핑
 * - LLM_TRIGGER_THRESHOLD = 0.80 → LLM 요청 대상 선별 (결제 판정 기준이 아님)
 *
 * @see com.sanha.moneytalk.core.sms2.SmsPatternMatcher
 * @see com.sanha.moneytalk.core.sms2.SmsGroupClassifier
 */
object SmsPatternSimilarityPolicy : SimilarityPolicy {

    override val profile = SimilarityProfile(
        autoApply = 0.95f,   // CACHE_REUSE_THRESHOLD: 캐시 파싱 결과 재사용
        confirm = 0.92f,     // PAYMENT_SIMILARITY_THRESHOLD: 결제 문자 판정
        propagate = 0f,      // SMS 패턴은 전파 개념 없음
        group = 0.95f        // SmsGroupClassifier의 SMS 패턴 그룹핑
    )

    /**
     * 비결제 패턴 캐시 히트 임계값
     *
     * 비결제 SMS는 결제 SMS보다 높은 임계값(0.97)을 사용하여
     * 오판(결제 SMS를 비결제로 분류)을 방지합니다.
     */
    const val NON_PAYMENT_CACHE_THRESHOLD = 0.97f

    /**
     * LLM 요청 트리거 임계값
     *
     * 벡터 유사도가 이 값 이상이면 LLM 요청 대상으로 선별합니다.
     * 주의: 이것은 "결제 판정 기준"이 아니라 "LLM 호출 대상 선별 기준"입니다.
     * 결제 판정은 여전히 confirm(0.92) 이상에서만 이루어집니다.
     */
    const val LLM_TRIGGER_THRESHOLD = 0.80f

    /**
     * 비결제 패턴으로 판정할지 여부
     * 비결제 캐시는 더 높은 임계값을 사용
     */
    fun shouldMatchNonPayment(similarity: Float): Boolean =
        similarity >= NON_PAYMENT_CACHE_THRESHOLD

    /**
     * LLM 요청 대상인지 여부
     * confirm 미달이지만 LLM 트리거 이상이면 LLM에게 확인 요청
     */
    fun shouldTriggerLlm(similarity: Float): Boolean =
        similarity >= LLM_TRIGGER_THRESHOLD && similarity < profile.confirm
}
