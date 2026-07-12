package com.sanha.moneytalk.feature.home.data

/**
 * Gemini API를 사용한 카테고리 분류 Repository 인터페이스
 *
 * @see GeminiCategoryRepositoryImpl
 */
interface GeminiCategoryRepository {

    /** Firebase AI Logic 서비스 사용 가능 여부 확인. 메서드명은 기존 호출부 호환을 위해 유지한다. */
    suspend fun hasApiKey(): Boolean

    /** 운영 설정과 일시적인 App Check 차단을 모두 반영한 원격 분류 시도 가능 여부. */
    suspend fun canAttemptClassification(): Boolean

    /**
     * 가게명 목록을 카테고리로 분류
     * Rate Limit (429) 에러 발생 시 지수 백오프로 재시도
     *
     * @param storeNames 분류할 가게명 목록
     * @return Map<가게명, 카테고리>
     */
    suspend fun classifyStoreNames(storeNames: List<String>): Map<String, String>

    /**
     * 단일 가게명 분류
     */
    suspend fun classifySingleStore(storeName: String): String?

    /**
     * 수입 출처 목록을 수입 카테고리로 분류
     *
     * @param incomeDescriptions Map<출처(source), 설명(description)>
     * @return Map<출처, 수입카테고리>
     */
    suspend fun classifyIncomeSources(incomeDescriptions: Map<String, String>): Map<String, String>
}
