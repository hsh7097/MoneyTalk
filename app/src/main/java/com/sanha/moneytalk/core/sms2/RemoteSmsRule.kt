package com.sanha.moneytalk.core.sms2

/**
 * ===== 원격 SMS 정규식 룰 =====
 *
 * RTDB `/sms_regex_rules/v1/{normalizedSender}/{ruleId}` 에서 내려받는 정제된 규칙.
 * 로컬 패턴 매칭(Step 4) 실패 시 2순위 매칭에 사용.
 *
 * 매칭 조건:
 * 1. 동일 normalized sender 우선 필터
 * 2. cosineSimilarity(SMS embedding, rule.embedding) >= rule.minSimilarity
 * 3. regex 파싱 성공 (amount > 0, store 유효)
 *
 * @property ruleId RTDB 룰 고유 ID
 * @property normalizedSenderAddress 정규화된 발신번호
 * @property embedding 3072차원 임베딩 벡터
 * @property amountRegex 금액 추출 정규식
 * @property storeRegex 가게명 추출 정규식
 * @property cardRegex 카드명 추출 정규식 (선택)
 * @property minSimilarity 최소 유사도 임계값 (기본 0.94)
 * @property enabled 활성화 여부
 * @property updatedAt 마지막 업데이트 시각 (ms)
 */
data class RemoteSmsRule(
    val ruleId: String,
    val normalizedSenderAddress: String,
    val embedding: List<Float>,
    val amountRegex: String,
    val storeRegex: String,
    val cardRegex: String = "",
    val minSimilarity: Float = DEFAULT_MIN_SIMILARITY,
    val enabled: Boolean = true,
    val updatedAt: Long = 0L
) {
    companion object {
        /** 원격 룰 매칭 기본 최소 유사도 */
        const val DEFAULT_MIN_SIMILARITY = 0.94f
    }
}
