package com.sanha.moneytalk.feature.home.data

/**
 * 카테고리 분류 서비스 인터페이스 (4-Tier 하이브리드)
 *
 * 가게명을 카테고리로 분류하는 4단계 파이프라인:
 *
 * ```
 * Tier 1:   Room DB 정확 매핑 (CategoryMappingEntity) → 즉시 반환 (비용 0)
 * Tier 1.5: 벡터 유사도 매칭 (StoreEmbeddingEntity)  → 임베딩 API 1회 (~0.001$)
 * Tier 2:   SmsParser 로컬 키워드 (250+ 키워드)       → 즉시 반환 (비용 0)
 * Tier 3:   Gemini 배치 API (시맨틱 그룹핑 후)         → API 호출 (비용 절감)
 * ```
 *
 * 자가 학습 피드백 루프:
 * - 사용자가 카테고리를 수동 수정하면 벡터 DB에 저장 + 유사 가게에 전파
 * - Gemini 분류 결과는 벡터 DB에도 캐싱 → 다음 유사 가게는 Tier 1.5에서 즉시 반환
 * - 벡터 매칭 성공 시 Room에도 정확 매핑 저장 (캐시 프로모션: Tier 1.5 → Tier 1)
 *
 * @see CategoryClassifierServiceImpl 기본 구현체
 * @see StoreEmbeddingRepository 벡터 유사도 검색/캐싱 담당
 */
interface CategoryClassifierService {

    /**
     * 인메모리 캐시 초기화 (동기화 전 1회 호출)
     * 모든 카테고리 매핑을 메모리에 로드하여 동기화 루프 중 DB 쿼리/API 호출을 제거합니다.
     */
    suspend fun initCategoryCache()

    /** 인메모리 캐시 해제 */
    fun clearCategoryCache()

    /** 대기 중인 매핑을 Room에 일괄 저장 */
    suspend fun flushPendingMappings()

    /**
     * 가게명으로 카테고리 조회 (4-Tier)
     *
     * 캐시 모드 활성화 시: Tier 1(캐시) + Tier 2(키워드)만 사용 (DB/API 호출 0회)
     * 일반 모드: Tier 1(Room) → Tier 1.5(벡터) → Tier 2(키워드)
     *
     * @param storeName 가게명
     * @param originalSms 원본 SMS (키워드 매칭 보조 정보)
     * @return 분류된 카테고리
     */
    suspend fun getCategory(storeName: String, originalSms: String = ""): String

    /**
     * 미분류("기타") 항목들을 Gemini로 일괄 분류 (시맨틱 그룹핑 최적화)
     *
     * 1. 가게명을 총 지출액 기준 내림차순 정렬 (중요도 우선)
     * 2. maxStoreCount가 설정되면 상위 N개만 처리 (나머지는 다음 라운드)
     * 3. 로컬 룰 사전 분류 → 시맨틱 그룹핑 → Gemini 배치
     * 4. 결과를 Room + 벡터 DB에 모두 저장
     *
     * @param onStepProgress 세부 진행 콜백 (단계명, 현재, 전체)
     * @param maxStoreCount 이번 라운드에서 처리할 최대 가게명 수 (null이면 전체)
     * @return 분류된 항목 수
     */
    suspend fun classifyUnclassifiedExpenses(
        onStepProgress: (suspend (step: String, current: Int, total: Int) -> Unit)? = null,
        maxStoreCount: Int? = null
    ): Int

    /**
     * 특정 지출의 카테고리를 수동 변경 (사용자 지정 + 자가 학습)
     *
     * 1. Room 매핑 업데이트
     * 2. 지출 항목 업데이트
     * 3. 벡터 DB에 source="user"로 저장
     * 4. 유사 가게명에 카테고리 전파 (유사도 >= 0.90)
     */
    suspend fun updateExpenseCategory(expenseId: Long, storeName: String, newCategory: String)

    /**
     * 동일 가게명을 가진 모든 지출의 카테고리 일괄 변경 (자가 학습 포함)
     */
    suspend fun updateCategoryForAllSameStore(storeName: String, newCategory: String)

    /**
     * Gemini API 키 설정 여부 확인
     */
    suspend fun hasGeminiApiKey(): Boolean

    /**
     * 저신뢰도 임베딩 항목 Gemini 재분류
     */
    suspend fun reclassifyLowConfidenceItems(confidenceThreshold: Float = 0.95f): Int

    /**
     * 미분류 항목 수 조회
     */
    suspend fun getUnclassifiedCount(): Int

    /**
     * 벡터 DB 학습 현황 조회
     */
    suspend fun getVectorCacheCount(): Int

    /**
     * 미분류 항목이 없을 때까지 반복 분류
     * @param onProgress 진행 상황 콜백 (현재 라운드, 분류된 수, 남은 미분류 수)
     * @param onStepProgress 세부 단계 진행 콜백 (단계명, 현재, 전체)
     * @param maxRounds 최대 반복 횟수 (무한 루프 방지)
     * @return 총 분류된 항목 수
     */
    suspend fun classifyAllUntilComplete(
        onProgress: suspend (round: Int, classifiedInRound: Int, remaining: Int) -> Unit,
        onStepProgress: (suspend (step: String, current: Int, total: Int) -> Unit)? = null,
        maxRounds: Int = 10
    ): Int
}
