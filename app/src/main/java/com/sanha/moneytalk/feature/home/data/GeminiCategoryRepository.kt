package com.sanha.moneytalk.feature.home.data

/**
 * Gemini API를 사용한 카테고리 분류 Repository 인터페이스
 *
 * @see GeminiCategoryRepositoryImpl
 */
interface GeminiCategoryRepository {

    /**
     * Gemini API 키 설정
     */
    suspend fun setApiKey(apiKey: String)

    /**
     * API 키 존재 여부 확인
     */
    suspend fun hasApiKey(): Boolean

    /**
     * API 키 가져오기
     */
    suspend fun getApiKey(): String

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
}
