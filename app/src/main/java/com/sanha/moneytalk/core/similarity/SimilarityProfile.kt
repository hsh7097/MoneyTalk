package com.sanha.moneytalk.core.similarity

/**
 * 유사도 임계값 프로파일
 *
 * 도메인별로 사용되는 유사도 임계값을 구조화한 데이터 클래스.
 * 각 임계값은 코사인 유사도 (0.0 ~ 1.0) 범위이며, 높을수록 엄격.
 *
 * 임계값 관계:
 * ```
 * autoApply ≥ confirm ≥ propagate ≥ group > reject
 * ```
 *
 * @property autoApply 자동 적용 임계값 (캐시 재사용, 완전 신뢰)
 * @property confirm 판정 확정 임계값 (결제 판정, 카테고리 적용 등)
 * @property propagate 전파 임계값 (유사 항목에 결과 전파)
 * @property group 그룹핑 임계값 (시맨틱 그룹으로 묶기)
 * @property reject 거부 임계값 (이 이하면 무시, 0이면 사용 안 함)
 */
data class SimilarityProfile(
    val autoApply: Float,
    val confirm: Float,
    val propagate: Float = 0f,
    val group: Float = 0f,
    val reject: Float = 0f
) {
    init {
        require(autoApply in 0f..1f) { "autoApply must be in [0, 1]" }
        require(confirm in 0f..1f) { "confirm must be in [0, 1]" }
        require(propagate in 0f..1f) { "propagate must be in [0, 1]" }
        require(group in 0f..1f) { "group must be in [0, 1]" }
        require(reject in 0f..1f) { "reject must be in [0, 1]" }
    }
}
