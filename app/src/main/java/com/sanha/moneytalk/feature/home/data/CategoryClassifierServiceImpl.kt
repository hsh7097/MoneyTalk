package com.sanha.moneytalk.feature.home.data

import android.util.Log
import com.sanha.moneytalk.core.sms2.SmsParser
import com.sanha.moneytalk.core.util.StoreNameGrouper
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 카테고리 분류 서비스 구현체 (4-Tier 하이브리드)
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
 * @see CategoryClassifierService
 * @see StoreEmbeddingRepository 벡터 유사도 검색/캐싱 담당
 * @see StoreNameGrouper 시맨틱 그룹핑으로 Gemini 호출 최적화
 */
@Singleton
class CategoryClassifierServiceImpl @Inject constructor(
    private val categoryRepository: CategoryRepository,
    private val geminiRepository: GeminiCategoryRepository,
    private val expenseRepository: ExpenseRepository,
    private val storeEmbeddingRepository: StoreEmbeddingRepository,
    private val storeNameGrouper: StoreNameGrouper,
    private val categoryReferenceProvider: com.sanha.moneytalk.core.util.CategoryReferenceProvider
) : CategoryClassifierService {
    companion object {
        private const val TAG = "CategoryClassifier"

        /**
         * Gemini 호출 전 로컬 룰로 사전 분류 가능한 패턴
         * SmsParser.inferCategory가 놓치는 가게명 패턴을 보완
         */
        private val PRE_CLASSIFY_RULES: List<Pair<List<String>, String>> = listOf(
            // 보험사 (SmsParser는 "보험"/"보험료" 키워드만 있어서 사명을 놓침)
            listOf(
                "삼성화재", "삼성생명", "메리츠화재", "메리츠생명",
                "현대해상", "DB손해", "DB생명", "KB손해", "KB생명",
                "한화손해", "한화생명", "라이나생명", "교보생명",
                "흥국생명", "미래에셋생명", "동양생명", "신한생명",
                "하나생명", "롯데손해", "NH생명", "농협생명",
                "AIA", "처브", "화재보험", "생명보험", "손해보험"
            ) to "보험",
            // 구독 서비스 (영문 패턴)
            listOf(
                "GOOGLE*", "APPLE.COM", "SPOTIFY", "NETFLIX",
                "YOUTUBE", "DISNEY+", "AMAZON", "CHATGPT",
                "OPENAI", "NOTION", "GITHUB", "FIGMA",
                "ADOBE", "CANVA", "DROPBOX", "ICLOUD"
            ) to "구독",
            // 결제대행/PG사 → 기타 (결제 주체가 아님)
            listOf(
                "KICC", "KCP", "NICE페이", "NICE정보", "이니시스",
                "다날", "토스페이먼츠", "NHN페이코", "페이먼츠",
                "PAYCO", "INICIS"
            ) to "기타",
            // 카드사/은행 알림 노이즈 → 기타
            listOf(
                "카드승인", "입출통지", "잔액통보", "이용내역",
                "자동납부", "CMS출금"
            ) to "기타"
        )

        /** 사전 컴파일된 lowercase 룰 */
        private val PRE_CLASSIFY_RULES_LOWER: List<Pair<List<String>, String>> =
            PRE_CLASSIFY_RULES.map { (keywords, category) ->
                keywords.map { it.lowercase() } to category
            }
    }

    // ===== 인메모리 캐시 (동기화 성능 최적화) =====
    private var categoryCache: MutableMap<String, String>? = null
    private val pendingMappings = mutableListOf<Triple<String, String, String>>()

    /**
     * 인메모리 캐시 초기화 (동기화 전 1회 호출)
     * 모든 카테고리 매핑을 메모리에 로드하여 동기화 루프 중 DB 쿼리/API 호출을 제거합니다.
     */
    override suspend fun initCategoryCache() {
        val mappings = categoryRepository.getAllMappingsOnce()
        categoryCache = HashMap<String, String>(mappings.size * 2).apply {
            for (mapping in mappings) {
                put(mapping.storeName, mapping.category)
            }
        }
        Log.d(TAG, "캐시 초기화: ${mappings.size}개 매핑 로드")
    }

    /** 인메모리 캐시 해제 */
    override fun clearCategoryCache() {
        categoryCache = null
    }

    /** 대기 중인 매핑을 Room에 일괄 저장 */
    override suspend fun flushPendingMappings() {
        if (pendingMappings.isEmpty()) return
        val mappings = pendingMappings.map { (store, category, _) -> store to category }
        val source = pendingMappings.firstOrNull()?.third ?: "local"
        categoryRepository.saveMappings(mappings, source)
        Log.d(TAG, "매핑 일괄 저장: ${pendingMappings.size}건")
        pendingMappings.clear()
    }

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
    override suspend fun getCategory(storeName: String, originalSms: String): String {
        // 캐시 모드: DB/API 호출 없이 인메모리에서 분류 (동기화 성능 최적화)
        val cache = categoryCache
        if (cache != null) {
            return getCategoryFromCache(storeName, originalSms, cache)
        }

        // ===== 일반 모드 (개별 조회) =====
        // Tier 1: Room DB에서 저장된 매핑 확인 (비용 0)
        categoryRepository.getCategoryByStoreName(storeName)?.let {
            Log.d(TAG, "[Tier 1] Room 매핑: $storeName → $it")
            return it
        }

        // Tier 1.5: 벡터 유사도 매칭 (임베딩 API 1회로 1.5a + 1.5b 처리)
        try {
            // 임베딩 벡터를 1회만 생성하여 Tier 1.5a/b 모두에서 재사용
            val queryVector = storeEmbeddingRepository.generateEmbeddingVector(storeName)
            if (queryVector != null) {
                // Tier 1.5a: 개별 최고 매칭 (유사도 ≥ 0.92)
                val vectorMatch =
                    storeEmbeddingRepository.findCategoryByStoreName(storeName, queryVector)
                if (vectorMatch != null) {
                    val matchedCategory = vectorMatch.storeEmbedding.category
                    Log.d(
                        TAG, "[Tier 1.5a] 벡터 매칭: $storeName → $matchedCategory " +
                                "(원본: ${vectorMatch.storeEmbedding.storeName}, 유사도: ${vectorMatch.similarity})"
                    )

                    // 캐시 프로모션: 벡터 매칭 결과를 Room에도 저장 → 다음 조회 시 Tier 1에서 즉시 반환
                    categoryRepository.saveMapping(storeName, matchedCategory, "vector")

                    return matchedCategory
                }

                // Tier 1.5b: 그룹 기반 매칭 (유사도 ≥ 0.88, 다수결)
                val groupResult =
                    storeEmbeddingRepository.findCategoryByGroup(storeName, queryVector)
                if (groupResult != null) {
                    val (groupCategory, avgSimilarity) = groupResult
                    Log.d(
                        TAG,
                        "[Tier 1.5b] 그룹 매칭: $storeName → $groupCategory (평균 유사도: $avgSimilarity)"
                    )

                    // 캐시 프로모션: 그룹 매칭 결과도 Room에 저장
                    categoryRepository.saveMapping(storeName, groupCategory, "vector")

                    return groupCategory
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "[Tier 1.5] 벡터 검색 실패 (무시): ${e.message}")
        }

        // Tier 2: SmsParser의 로컬 키워드 매칭 (비용 0)
        val localCategory = SmsParser.inferCategory(storeName, originalSms)
        if (localCategory != "미분류") {
            // 로컬 매칭 결과를 Room에 저장
            categoryRepository.saveMapping(storeName, localCategory, "local")
            Log.d(TAG, "[Tier 2] 로컬 키워드: $storeName → $localCategory")
            return localCategory
        }

        // Tier 3 대기: 미분류로 반환 (Gemini 분류는 배치로 별도 처리)
        Log.e("MT_DEBUG", "CategoryClassifier[getCategory] : $storeName → 미분류 (모든 Tier 실패)")
        return "미분류"
    }

    /**
     * 로컬 룰 기반 사전 분류 (Gemini 호출 없이 분류 가능한 가게명 분리)
     *
     * @param storeNames 분류 대상 가게명 목록
     * @return Pair(룰로 분류된 맵, Gemini로 보낼 남은 가게명 목록)
     */
    private fun preClassifyByRules(storeNames: List<String>): Pair<Map<String, String>, List<String>> {
        val classified = mutableMapOf<String, String>()
        val remaining = mutableListOf<String>()

        for (storeName in storeNames) {
            val lowerName = storeName.lowercase()
            var matched = false

            for ((keywords, category) in PRE_CLASSIFY_RULES_LOWER) {
                if (keywords.any { keyword -> lowerName.contains(keyword) }) {
                    classified[storeName] = category
                    matched = true
                    break
                }
            }

            if (!matched) {
                remaining.add(storeName)
            }
        }

        if (classified.isNotEmpty()) {
            Log.d(TAG, "로컬 룰 사전 분류: ${classified.size}건 (${storeNames.size}건 중)")
        }

        return classified to remaining
    }

    /**
     * 인메모리 캐시 기반 카테고리 조회 (DB/API 호출 0회)
     *
     * 부분 매칭 최적화: 전체 캐시 순회(O(n)) 대신 짧은 쪽이 긴 쪽에 포함되는지만 확인.
     * 부분 매칭 성공 시 캐시에 정확 매핑도 추가하여 다음 조회 시 O(1)에 반환.
     */
    private fun getCategoryFromCache(
        storeName: String,
        originalSms: String,
        cache: MutableMap<String, String>
    ): String {
        // 1. 캐시에서 정확 매칭 (O(1) HashMap lookup)
        cache[storeName]?.let { return it }

        // 2. 캐시에서 부분 매칭 (짧은 가게명이 긴 가게명에 포함되는 케이스)
        for ((cachedName, category) in cache) {
            if (storeName.length >= cachedName.length) {
                if (storeName.contains(cachedName)) {
                    // 부분 매칭 성공 → 캐시에 정확 매핑 추가 (다음 조회 O(1))
                    cache[storeName] = category
                    return category
                }
            } else {
                if (cachedName.contains(storeName)) {
                    cache[storeName] = category
                    return category
                }
            }
        }

        // 3. SmsParser 로컬 키워드 매칭
        val localCategory = SmsParser.inferCategory(storeName, originalSms)
        if (localCategory != "미분류") {
            cache[storeName] = localCategory
            pendingMappings.add(Triple(storeName, localCategory, "local"))
            return localCategory
        }

        return "미분류"
    }

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
    override suspend fun classifyUnclassifiedExpenses(
        onStepProgress: (suspend (step: String, current: Int, total: Int) -> Unit)?,
        maxStoreCount: Int?
    ): Int {
        val totalStartTime = System.currentTimeMillis()
        Log.e(
            "MT_DEBUG",
            "CategoryClassifier[classifyUnclassifiedExpenses] : ========== 카테고리 분류 파이프라인 시작 (maxStoreCount=$maxStoreCount) =========="
        )

        // ===== [1/6] 미분류 항목 조회 =====
        val step1Start = System.currentTimeMillis()
        onStepProgress?.invoke("미분류 항목 조회 중...", 0, 0)
        val unclassifiedExpenses = expenseRepository.getExpensesByCategoryOnce("미분류")
        val step1Elapsed = System.currentTimeMillis() - step1Start
        Log.e("MT_DEBUG", "CategoryClassifier[1/6 DB조회] : ${unclassifiedExpenses.size}건 (${step1Elapsed}ms)")

        if (unclassifiedExpenses.isEmpty()) {
            Log.e("MT_DEBUG", "CategoryClassifier : 미분류 항목 없음 → 종료 (총 ${step1Elapsed}ms)")
            return 0
        }

        // 가게명별 총 지출액 기준으로 정렬 (중요도 높은 것 우선 처리)
        val storeAmountMap = unclassifiedExpenses
            .groupBy { it.storeName }
            .mapValues { (_, expenses) -> expenses.sumOf { it.amount } }

        val allStoreNames = storeAmountMap.entries
            .sortedByDescending { it.value }
            .map { it.key }

        // maxStoreCount 적용: 상위 N개만 처리
        val storeNames = if (maxStoreCount != null) {
            allStoreNames.take(maxStoreCount)
        } else {
            allStoreNames
        }

        Log.e(
            "MT_DEBUG",
            "CategoryClassifier : 미분류 지출 ${unclassifiedExpenses.size}건 / 고유 가게명 ${allStoreNames.size}개 (처리 대상: ${storeNames.size}개)"
        )

        // ===== [2/6] 로컬 룰 사전 분류 =====
        val step2Start = System.currentTimeMillis()
        onStepProgress?.invoke("로컬 룰 분류 중...", 0, storeNames.size)
        val (ruleClassified, storeNamesForGemini) = preClassifyByRules(storeNames)
        val step2Elapsed = System.currentTimeMillis() - step2Start
        Log.e("MT_DEBUG", "CategoryClassifier[2/6 로컬룰] : ${ruleClassified.size}건 분류, ${storeNamesForGemini.size}건 남음 (${step2Elapsed}ms)")

        // ===== [3/6] 시맨틱 그룹핑 (임베딩 + 클러스터링) =====
        val step3Start = System.currentTimeMillis()
        onStepProgress?.invoke("유사 가게 그룹핑 중...", 0, storeNamesForGemini.size)
        val groups = try {
            storeNameGrouper.groupStoreNames(storeNamesForGemini)
        } catch (e: Exception) {
            Log.w(TAG, "시맨틱 그룹핑 실패, 개별 처리로 폴백: ${e.message}")
            storeNamesForGemini.map {
                StoreNameGrouper.StoreGroup(
                    representative = it,
                    members = listOf(it)
                )
            }
        }
        val step3Elapsed = System.currentTimeMillis() - step3Start

        val representatives = groups.map { it.representative }
        val multiMemberGroups = groups.count { it.members.size > 1 }
        val maxGroupSize = groups.maxOfOrNull { it.members.size } ?: 0
        Log.e(
            "MT_DEBUG",
            "CategoryClassifier[3/6 그룹핑] : ${storeNamesForGemini.size}개 → ${groups.size}그룹 (다중멤버: ${multiMemberGroups}개, 최대그룹: ${maxGroupSize}명) (${step3Elapsed}ms)"
        )

        // ===== [4/6] Gemini LLM 분류 =====
        val step4Start = System.currentTimeMillis()
        val classifications: Map<String, String>
        val step4Elapsed: Long
        if (representatives.isNotEmpty()) {
            onStepProgress?.invoke("AI 분류 중...", 0, representatives.size)
            val batchCount = (representatives.size + 49) / 50 // BATCH_SIZE=50 기준
            Log.e(
                "MT_DEBUG",
                "CategoryClassifier[4/6 Gemini] : 시작 — 대표 ${representatives.size}건, ${batchCount}개 배치 (병렬 5)"
            )
            classifications = geminiRepository.classifyStoreNames(representatives)
            step4Elapsed = System.currentTimeMillis() - step4Start
            Log.e(
                "MT_DEBUG",
                "CategoryClassifier[4/6 Gemini] : 완료 — ${classifications.size}/${representatives.size}건 성공 (${step4Elapsed}ms, 건당 ${if (representatives.isNotEmpty()) step4Elapsed / representatives.size else 0}ms)"
            )
        } else {
            Log.e("MT_DEBUG", "CategoryClassifier[4/6 Gemini] : 스킵 (모두 로컬 룰로 분류됨)")
            classifications = emptyMap()
            step4Elapsed = System.currentTimeMillis() - step4Start
        }

        if (classifications.isEmpty() && ruleClassified.isEmpty()) {
            val totalElapsed = System.currentTimeMillis() - totalStartTime
            Log.e(
                "MT_DEBUG",
                "CategoryClassifier : 분류 실패 (Gemini 빈 응답 + 룰 매칭 없음) (총 ${totalElapsed}ms)"
            )
            return 0
        }

        // ===== [5/6] 결과 전파 + Room 저장 + 벡터 DB 캐싱 =====
        val step5Start = System.currentTimeMillis()
        val allClassifications = mutableMapOf<String, String>()
        allClassifications.putAll(ruleClassified)

        for (group in groups) {
            val category = classifications[group.representative] ?: continue
            allClassifications[group.representative] = category
            for (member in group.members) {
                if (member != group.representative) {
                    allClassifications[member] = category
                }
            }
        }

        // Room 매핑 저장
        onStepProgress?.invoke("결과 저장 중...", classifications.size, representatives.size)
        val mappings = allClassifications.map { (store, category) -> store to category }
        categoryRepository.saveMappings(mappings, "gemini")
        val roomSaveElapsed = System.currentTimeMillis() - step5Start

        // 벡터 DB 캐싱
        val vectorStart = System.currentTimeMillis()
        try {
            storeEmbeddingRepository.saveStoreEmbeddings(allClassifications, "gemini")
        } catch (e: Exception) {
            Log.w(TAG, "벡터 DB 캐싱 실패 (무시): ${e.message}")
        }
        val vectorElapsed = System.currentTimeMillis() - vectorStart
        val step5Elapsed = System.currentTimeMillis() - step5Start
        Log.e(
            "MT_DEBUG",
            "CategoryClassifier[5/6 저장] : Room ${allClassifications.size}건 (${roomSaveElapsed}ms), 벡터DB (${vectorElapsed}ms) → 총 ${step5Elapsed}ms"
        )

        // ===== [6/6] 지출 항목 카테고리 업데이트 =====
        val step6Start = System.currentTimeMillis()
        val storeNamesToUpdate = unclassifiedExpenses.map { it.storeName }.distinct()
            .filter { allClassifications.containsKey(it) }
        for ((idx, store) in storeNamesToUpdate.withIndex()) {
            allClassifications[store]?.let { newCategory ->
                expenseRepository.updateCategoryByStoreName(store, newCategory)
            }
            if (idx % 10 == 0) {
                onStepProgress?.invoke("지출 업데이트 중...", idx, storeNamesToUpdate.size)
            }
        }
        val step6Elapsed = System.currentTimeMillis() - step6Start
        val updatedCount =
            unclassifiedExpenses.count { allClassifications.containsKey(it.storeName) }

        val totalElapsed = System.currentTimeMillis() - totalStartTime
        Log.e(
            "MT_DEBUG",
            "CategoryClassifier[6/6 업데이트] : ${storeNamesToUpdate.size}개 가게명 업데이트 (${step6Elapsed}ms)"
        )
        Log.e(
            "MT_DEBUG",
            "CategoryClassifier ========== 파이프라인 완료 =========="
        )
        Log.e(
            "MT_DEBUG",
            "CategoryClassifier [결과] ${updatedCount}건 업데이트 (Gemini ${classifications.size}건 + 전파 ${allClassifications.size - classifications.size - ruleClassified.size}건 + 룰 ${ruleClassified.size}건)"
        )
        Log.e(
            "MT_DEBUG",
            "CategoryClassifier [시간] 총 ${totalElapsed}ms = DB조회 ${step1Elapsed}ms + 로컬룰 ${step2Elapsed}ms + 그룹핑 ${step3Elapsed}ms + Gemini ${step4Elapsed}ms + 저장 ${step5Elapsed}ms + 업데이트 ${step6Elapsed}ms"
        )
        return updatedCount
    }

    /**
     * 특정 지출의 카테고리를 수동 변경 (사용자 지정 + 자가 학습)
     *
     * 1. Room 매핑 업데이트
     * 2. 지출 항목 업데이트
     * 3. 벡터 DB에 source="user"로 저장
     * 4. 유사 가게명에 카테고리 전파 (유사도 >= 0.90)
     */
    override suspend fun updateExpenseCategory(expenseId: Long, storeName: String, newCategory: String) {
        // Room 매핑 업데이트/추가
        categoryRepository.saveMapping(storeName, newCategory, "user")

        // 지출 항목 업데이트
        expenseRepository.updateCategoryById(expenseId, newCategory)

        // 참조 리스트 캐시 무효화 (프롬프트에 반영)
        categoryReferenceProvider.invalidateCache()

        // 벡터 DB에 저장 (source="user"로 최고 신뢰도) - UPSERT 패턴으로 DB 접근 최소화
        try {
            storeEmbeddingRepository.upsertCategory(storeName, newCategory, "user")

            // 유사 가게에 전파
            val propagated =
                storeEmbeddingRepository.propagateCategoryToSimilarStores(storeName, newCategory)
            if (propagated > 0) {
                Log.d(TAG, "유사 가게 ${propagated}건에 카테고리 전파됨")
            }
        } catch (e: Exception) {
            Log.w(TAG, "벡터 DB 업데이트/전파 실패 (무시): ${e.message}")
        }

        Log.d(TAG, "수동 분류: $storeName → $newCategory")
    }

    /**
     * 동일 가게명을 가진 모든 지출의 카테고리 일괄 변경 (자가 학습 포함)
     */
    override suspend fun updateCategoryForAllSameStore(storeName: String, newCategory: String) {
        // Room 매핑 업데이트/추가
        categoryRepository.saveMapping(storeName, newCategory, "user")

        // 해당 가게명의 모든 지출 업데이트
        expenseRepository.updateCategoryByStoreName(storeName, newCategory)

        // 참조 리스트 캐시 무효화 (프롬프트에 반영)
        categoryReferenceProvider.invalidateCache()

        // 벡터 DB에 저장 + 유사 가게 전파 - UPSERT 패턴으로 DB 접근 최소화
        try {
            storeEmbeddingRepository.upsertCategory(storeName, newCategory, "user")

            val propagated =
                storeEmbeddingRepository.propagateCategoryToSimilarStores(storeName, newCategory)
            if (propagated > 0) {
                Log.d(TAG, "유사 가게 ${propagated}건에 카테고리 전파됨")
            }
        } catch (e: Exception) {
            Log.w(TAG, "벡터 DB 업데이트/전파 실패 (무시): ${e.message}")
        }

        Log.d(TAG, "일괄 분류: $storeName → $newCategory (모든 항목)")
    }

    /**
     * Gemini API 키 설정 여부 확인
     */
    override suspend fun hasGeminiApiKey(): Boolean {
        return geminiRepository.hasApiKey()
    }

    /**
     * 저신뢰도 임베딩 항목 Gemini 재분류
     */
    override suspend fun reclassifyLowConfidenceItems(confidenceThreshold: Float): Int {
        try {
            Log.e(
                "MT_DEBUG",
                "CategoryClassifier[reclassifyLowConfidenceItems] : === 저신뢰도 재분류 시작 === threshold=$confidenceThreshold"
            )
            val lowConfidenceItems =
                storeEmbeddingRepository.getLowConfidenceEmbeddings(confidenceThreshold)
            if (lowConfidenceItems.isEmpty()) {
                Log.e("MT_DEBUG", "CategoryClassifier[reclassifyLowConfidenceItems] : 재분류 대상 없음")
                Log.d(TAG, "재분류 대상 없음 (threshold=$confidenceThreshold)")
                return 0
            }
            Log.e(
                "MT_DEBUG",
                "CategoryClassifier[reclassifyLowConfidenceItems] : 재분류 대상 ${lowConfidenceItems.size}건 발견"
            )
            for (item in lowConfidenceItems) {
                Log.e(
                    "MT_DEBUG",
                    "  - ${item.storeName} | category=${item.category} | source=${item.source} | confidence=${item.confidence}"
                )
            }
            Log.d(TAG, "재분류 시작: ${lowConfidenceItems.size}건 (threshold=$confidenceThreshold)")
            val storeNames = lowConfidenceItems.map { it.storeName }
            Log.e(
                "MT_DEBUG",
                "CategoryClassifier[reclassifyLowConfidenceItems] : Gemini API 호출 시작 (${storeNames.size}건)"
            )
            val classifications = geminiRepository.classifyStoreNames(storeNames)
            Log.e(
                "MT_DEBUG",
                "CategoryClassifier[reclassifyLowConfidenceItems] : Gemini 응답 ${classifications.size}건"
            )
            var reclassifiedCount = 0
            for ((storeName, newCategory) in classifications) {
                if (newCategory == "미분류") {
                    Log.e("MT_DEBUG", "  - SKIP: $storeName → 미분류 (변경 없음)")
                    continue
                }
                Log.e("MT_DEBUG", "  - UPDATE: $storeName → $newCategory")
                categoryRepository.saveMapping(storeName, newCategory, "gemini")
                storeEmbeddingRepository.updateCategory(storeName, newCategory, "gemini")
                expenseRepository.updateCategoryByStoreName(storeName, newCategory)
                reclassifiedCount++
            }
            Log.e(
                "MT_DEBUG",
                "CategoryClassifier[reclassifyLowConfidenceItems] : === 재분류 완료: ${reclassifiedCount}건 ==="
            )
            Log.d(TAG, "재분류 완료: ${reclassifiedCount}건")
            return reclassifiedCount
        } catch (e: Exception) {
            Log.e(
                "MT_DEBUG",
                "CategoryClassifier[reclassifyLowConfidenceItems] : 재분류 실패: ${e.message}",
                e
            )
            Log.e(TAG, "재분류 실패: ${e.message}", e)
            return 0
        }
    }

    /**
     * 미분류 항목 수 조회
     */
    override suspend fun getUnclassifiedCount(): Int {
        return expenseRepository.getExpensesByCategoryOnce("미분류").size
    }

    /**
     * 벡터 DB 학습 현황 조회
     */
    override suspend fun getVectorCacheCount(): Int {
        return storeEmbeddingRepository.getEmbeddingCount()
    }

    /**
     * 미분류 항목이 없을 때까지 반복 분류
     * @param onProgress 진행 상황 콜백 (현재 라운드, 분류된 수, 남은 미분류 수)
     * @param onStepProgress 세부 단계 진행 콜백 (단계명, 현재, 전체)
     * @param maxRounds 최대 반복 횟수 (무한 루프 방지)
     * @return 총 분류된 항목 수
     */
    override suspend fun classifyAllUntilComplete(
        onProgress: suspend (round: Int, classifiedInRound: Int, remaining: Int) -> Unit,
        onStepProgress: (suspend (step: String, current: Int, total: Int) -> Unit)?,
        maxRounds: Int
    ): Int {
        var totalClassified = 0
        var round = 0

        while (round < maxRounds) {
            round++
            val remainingBefore = getUnclassifiedCount()

            if (remainingBefore == 0) {
                Log.d(TAG, "모든 항목 분류 완료 (라운드 $round)")
                break
            }

            Log.d(TAG, "라운드 $round 시작: ${remainingBefore}개 미분류")

            val classifiedInRound = classifyUnclassifiedExpenses(onStepProgress)
            totalClassified += classifiedInRound

            val remainingAfter = getUnclassifiedCount()
            onProgress(round, classifiedInRound, remainingAfter)

            // 더 이상 분류가 안 되면 종료 (진전이 없음)
            if (classifiedInRound == 0 || remainingAfter == remainingBefore) {
                Log.d(TAG, "더 이상 분류 불가, 종료 (남은 미분류: $remainingAfter)")
                break
            }

            // 고정 딜레이 제거: 429 발생 시 GeminiCategoryRepository 내부에서 백오프
        }

        Log.d(TAG, "전체 분류 완료: 총 ${totalClassified}건 분류됨 (벡터 캐시 ${getVectorCacheCount()}건)")
        return totalClassified
    }
}
