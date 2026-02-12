package com.sanha.moneytalk.core.util

import android.util.Log
import com.sanha.moneytalk.core.database.dao.SmsPatternDao
import com.sanha.moneytalk.core.database.entity.SmsPatternEntity
import com.sanha.moneytalk.core.model.SmsAnalysisResult
import com.sanha.moneytalk.core.similarity.SmsPatternSimilarityPolicy
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 하이브리드 SMS 분류기 (3-tier 시스템)
 *
 * SMS 수신 시 다음 3단계를 거쳐 결제 문자를 분류하고 파싱합니다:
 *
 * 1단계 (Regex): 기존 SmsParser 정규식으로 판별 + 파싱
 *   → 성공하면 결과 반환 + 벡터 DB에 패턴 등록 (학습)
 *
 * 2단계 (Vector): 임베딩 벡터 유사도로 결제 문자 판별
 *   → 유사도가 높으면(≥0.97) 캐시된 파싱 결과 재사용
 *   → 유사도가 중간(≥0.92)이면 결제 문자로 판정, 3단계로
 *
 * 3단계 (LLM): Gemini로 구조화된 데이터 추출
 *   → 추출 성공하면 결과 반환 + 벡터 DB에 패턴 등록 (학습)
 *
 * LLM 호출 조건:
 *   Tier 1~2 실패 시, 결제 가능성 사전 체크(hasPotentialPaymentIndicators)를
 *   통과한 SMS에 한해 LLM을 호출합니다. 비용 통제를 위해 금액 패턴, 결제 키워드,
 *   카드사 키워드 중 2개 이상 매칭되어야 LLM 호출이 허용됩니다.
 */
@Singleton
class HybridSmsClassifier @Inject constructor(
    private val smsPatternDao: SmsPatternDao,
    private val embeddingService: SmsEmbeddingService,
    private val smsExtractor: GeminiSmsExtractor
) {
    companion object {
        private const val TAG = "HybridSmsClassifier"
        private const val BOOTSTRAP_THRESHOLD = 10  // 부트스트랩 모드 임계값

        /** 임베딩 배치 처리 크기 (Google batchEmbedContents 최대값) */
        private const val EMBEDDING_BATCH_SIZE = 100

        /** 배치 임베딩 간 최소 딜레이 (밀리초) — 429 미발생 시 최소값 유지 */
        private const val RATE_LIMIT_DELAY_MS = 50L

        /** LLM 호출 간 최소 딜레이 (밀리초) — 429 미발생 시 최소값 유지 */
        private const val LLM_RATE_LIMIT_DELAY_MS = 50L

        /** 오래된 패턴 판단 기준 (30일, 밀리초) */
        private const val STALE_PATTERN_THRESHOLD_MS = 30L * 24 * 60 * 60 * 1000

        /**
         * LLM 호출 전 사전 필터링 키워드
         * 이 키워드가 포함된 SMS는 결제 문자가 아님이 확실하므로
         * 부트스트랩 모드에서도 LLM에 보내지 않음
         */
        private val NON_PAYMENT_KEYWORDS = listOf(
            // 인증/보안 관련
            "인증번호", "인증코드", "authentication", "verification", "code",
            "OTP", "본인확인", "비밀번호",
            // 발신 표시
            "국외발신", "국제발신", "해외발신",
            // 광고/마케팅
            "광고", "[광고]", "(광고)", "무료수신거부", "수신거부",
            "홍보", "이벤트", "혜택안내", "프로모션", "할인쿠폰",
            // 안내/알림 (비결제) - 카드 대금/청구 관련
            "명세서", "청구서", "이용대금", "결제예정", "결제일",
            "결제금액", // 카드사 결제예정 금액 안내 (예: "01/25결제금액(01/26기준)")
            "카드대금", // 카드 대금 결제/이체 안내
            "결제대금", // 카드 결제대금 안내
            "청구금액", // 카드사 청구금액 안내
            "출금 예정", // 자동이체 출금 예정 안내 (띄어쓰기 포함)
            "출금예정", "자동이체", "납부안내",
            // 배송/택배
            "배송", "택배", "운송장",
            // 기타 비결제
            "설문", "survey", "투표"
        )

        /** NON_PAYMENT_KEYWORDS를 미리 lowercase로 캐시 (매번 .lowercase() 호출 방지) */
        private val NON_PAYMENT_KEYWORDS_LOWER = NON_PAYMENT_KEYWORDS.map { it.lowercase() }

        /** 금액+원 패턴 사전 컴파일 (hasPotentialPaymentIndicators에서 매 호출마다 재생성 방지) */
        private val AMOUNT_WON_PATTERN = Regex("""[\d,]+원""")
    }

    /**
     * SMS 분류 결과
     *
     * @property isPayment 결제 문자 여부
     * @property analysisResult 파싱된 결제 정보 (결제 문자일 때만)
     * @property tier 판정에 사용된 tier (1=Regex, 2=Vector, 3=LLM)
     * @property confidence 판정 신뢰도 (0.0 ~ 1.0)
     */
    data class ClassificationResult(
        val isPayment: Boolean,
        val analysisResult: SmsAnalysisResult? = null,
        val tier: Int = 0,
        val confidence: Float = 0f
    )

    /**
     * 기존 정규식 기반으로만 분류 (Tier 1 전용)
     *
     * SmsReceiver에서 실시간 수신 시 빠르게 판별할 때 사용.
     * 벡터 학습은 하지 않음 (비동기 작업 불가한 환경용)
     */
    fun classifyRegexOnly(smsBody: String, smsTimestamp: Long): ClassificationResult? {
        return classifyWithRegex(smsBody, smsTimestamp)
    }

    /**
     * SMS를 배치로 벡터 분류 (Tier 2 전용, 배치 임베딩 사용)
     *
     * 소량/대량 상관없이 batchEmbedContents를 사용하여 한번에 임베딩을 생성하고
     * 벡터 DB와 매칭합니다. 개별 classify()와 달리 API 호출 횟수를 대폭 줄입니다.
     *
     * 흐름:
     * 1. 사전 필터링: 명백한 비결제 SMS 제외
     * 2. 배치 임베딩 생성 (50건씩 batchEmbedContents)
     * 3. 벡터 DB 매칭 (비결제 패턴 → 결제 패턴 순)
     * 4. 매칭 안 된 SMS 중 결제 가능성 있는 것만 LLM에 전달
     *
     * @param smsList SMS 목록 (body, timestamp, address)
     * @return 각 SMS의 분류 결과 (index와 1:1 매핑)
     */
    suspend fun batchClassify(
        smsList: List<Triple<String, Long, String>> // (body, timestamp, address)
    ): List<ClassificationResult> {
        if (smsList.isEmpty()) return emptyList()

        Log.d(TAG, "=== 배치 분류 시작: ${smsList.size}건 ===")

        val results = Array(smsList.size) {
            ClassificationResult(isPayment = false, tier = 0, confidence = 0f)
        }

        // ===== Step 1: Regex 먼저 처리 =====
        val regexUnresolved = mutableListOf<Int>() // Regex 미통과 인덱스
        for (i in smsList.indices) {
            val (body, timestamp, _) = smsList[i]
            val regexResult = classifyWithRegex(body, timestamp)
            if (regexResult != null) {
                results[i] = regexResult
            } else {
                regexUnresolved.add(i)
            }
        }
        Log.d(
            TAG,
            "배치 Regex: ${smsList.size - regexUnresolved.size}건 성공, ${regexUnresolved.size}건 미통과"
        )

        if (regexUnresolved.isEmpty()) return results.toList()

        // ===== Step 2: 사전 필터링 (인증번호, 광고 등 제외) =====
        val vectorCandidateIndices = mutableListOf<Int>()
        for (idx in regexUnresolved) {
            val body = smsList[idx].first
            if (isObviouslyNonPayment(body)) {
                // 명백한 비결제 → 즉시 비결제 판정 (벡터 매칭도 스킵)
                results[idx] = ClassificationResult(isPayment = false, tier = 0, confidence = 0f)
            } else {
                vectorCandidateIndices.add(idx)
            }
        }
        Log.d(
            TAG,
            "배치 사전 필터링: ${regexUnresolved.size - vectorCandidateIndices.size}건 비결제 제외, ${vectorCandidateIndices.size}건 벡터 분석 대상"
        )

        if (vectorCandidateIndices.isEmpty()) return results.toList()

        // ===== Step 3: 배치 임베딩 생성 =====
        val templates =
            vectorCandidateIndices.map { embeddingService.templateizeSms(smsList[it].first) }
        val allEmbeddings = mutableListOf<List<Float>?>()

        // 100건씩 배치 임베딩 (batchEmbedContents 최대 100)
        val batchSize = EMBEDDING_BATCH_SIZE
        val batches = templates.chunked(batchSize)
        for ((batchIdx, batch) in batches.withIndex()) {
            val startTime = System.currentTimeMillis()
            Log.d(TAG, "[batchClassify] 임베딩 배치 ${batchIdx + 1}/${batches.size} 시작 (${batch.size}건)")
            val embeddings = embeddingService.generateEmbeddings(batch)
            val elapsed = System.currentTimeMillis() - startTime
            val successCount = embeddings.count { it != null }
            Log.d(
                TAG,
                "[batchClassify] 임베딩 배치 ${batchIdx + 1}/${batches.size} 완료 (${elapsed}ms, 성공: ${successCount}/${batch.size})"
            )
            allEmbeddings.addAll(embeddings)

            if (batchIdx < batches.size - 1) {
                kotlinx.coroutines.delay(RATE_LIMIT_DELAY_MS)
            }
        }

        Log.d(TAG, "배치 임베딩 완료: ${allEmbeddings.count { it != null }}/${allEmbeddings.size}건 성공")

        // ===== Step 4: 벡터 DB 매칭 =====
        val nonPaymentPatterns = smsPatternDao.getAllNonPaymentPatterns()
        val paymentPatterns = smsPatternDao.getAllPaymentPatterns()
        val llmCandidates = mutableListOf<Int>() // 벡터 매칭 안 된 인덱스 (LLM 후보)

        for ((localIdx, originalIdx) in vectorCandidateIndices.withIndex()) {
            val embedding = allEmbeddings.getOrNull(localIdx)
            if (embedding == null) {
                // 임베딩 생성 실패 → 비결제로 처리
                continue
            }

            val (body, timestamp, _) = smsList[originalIdx]

            // 비결제 패턴 우선 매칭 (비결제는 더 높은 임계값 사용)
            if (nonPaymentPatterns.isNotEmpty()) {
                val nonPaymentMatch = VectorSearchEngine.findBestMatch(
                    queryVector = embedding,
                    patterns = nonPaymentPatterns,
                    minSimilarity = SmsPatternSimilarityPolicy.NON_PAYMENT_CACHE_THRESHOLD
                )
                if (nonPaymentMatch != null) {
                    results[originalIdx] = ClassificationResult(
                        isPayment = false, tier = 2, confidence = nonPaymentMatch.similarity
                    )
                    smsPatternDao.incrementMatchCount(nonPaymentMatch.pattern.id)
                    continue
                }
            }

            // 결제 패턴 매칭
            if (paymentPatterns.isNotEmpty()) {
                val bestMatch = VectorSearchEngine.findBestMatch(
                    queryVector = embedding,
                    patterns = paymentPatterns,
                    minSimilarity = SmsPatternSimilarityPolicy.profile.confirm
                )

                if (bestMatch != null) {
                    smsPatternDao.incrementMatchCount(bestMatch.pattern.id)

                    if (bestMatch.similarity >= SmsPatternSimilarityPolicy.profile.autoApply) {
                        // 높은 유사도 → 캐시 재사용
                        val cached = bestMatch.pattern
                        val currentAmount = SmsParser.extractAmount(body) ?: cached.parsedAmount
                        val currentDateTime = SmsParser.extractDateTime(body, timestamp)

                        results[originalIdx] = ClassificationResult(
                            isPayment = true,
                            analysisResult = SmsAnalysisResult(
                                amount = currentAmount,
                                storeName = extractStoreNameOrCached(body, cached.parsedStoreName),
                                category = cached.parsedCategory,
                                dateTime = currentDateTime,
                                cardName = cached.parsedCardName
                            ),
                            tier = 2,
                            confidence = bestMatch.similarity
                        )
                    } else {
                        // 중간 유사도 → 결제 판정은 됐지만 파싱은 캐시 폴백
                        val fallbackAmount =
                            SmsParser.extractAmount(body) ?: bestMatch.pattern.parsedAmount
                        val fallbackDateTime = SmsParser.extractDateTime(body, timestamp)

                        results[originalIdx] = ClassificationResult(
                            isPayment = true,
                            analysisResult = SmsAnalysisResult(
                                amount = fallbackAmount,
                                storeName = extractStoreNameOrCached(
                                    body,
                                    bestMatch.pattern.parsedStoreName
                                ),
                                category = bestMatch.pattern.parsedCategory,
                                dateTime = fallbackDateTime,
                                cardName = bestMatch.pattern.parsedCardName
                            ),
                            tier = 2,
                            confidence = bestMatch.similarity
                        )
                    }
                    continue
                }
            }

            // 벡터 매칭 안 됨 → 결제 가능성 있으면 LLM 후보
            if (hasPotentialPaymentIndicators(body)) {
                llmCandidates.add(originalIdx)
            }
        }

        // ===== Step 5: 결제 가능성 있는 미매칭 SMS에 LLM 호출 =====
        if (llmCandidates.isNotEmpty()) {
            Log.d(TAG, "[batchClassify] LLM 후보: ${llmCandidates.size}건")
            for ((llmIdx, idx) in llmCandidates.withIndex()) {
                val (body, timestamp, address) = smsList[idx]
                val startTime = System.currentTimeMillis()
                Log.d(
                    TAG,
                    "[batchClassify] LLM 호출 ${llmIdx + 1}/${llmCandidates.size}: ${body.take(40)}..."
                )
                val llmResult = classifyWithLlm(body, timestamp, address)
                val elapsed = System.currentTimeMillis() - startTime
                Log.d(
                    TAG,
                    "[batchClassify] LLM 완료 ${llmIdx + 1}/${llmCandidates.size} (${elapsed}ms): isPayment=${llmResult?.isPayment}"
                )
                if (llmResult != null) {
                    results[idx] = llmResult
                }
                kotlinx.coroutines.delay(LLM_RATE_LIMIT_DELAY_MS)
            }
        }

        val paymentCount = results.count { it.isPayment }
        Log.d(TAG, "=== 배치 분류 완료: ${smsList.size}건 중 결제 ${paymentCount}건 ===")

        return results.toList()
    }

    /**
     * Regex 성공 결과를 벡터 DB에 배치 학습 (동기화 후 백그라운드에서 호출)
     *
     * 개별 learnFromRegexResult는 SMS당 임베딩 API 1회 호출(~0.4s)이라 동기화 루프에서 병목이 됨.
     * 이 메서드는 batchEmbedContents API + 병렬 코루틴으로 대량 처리합니다.
     *
     * 최적화:
     * - 100건 단위로 chunking (구글 batchEmbedContents 최대값)
     * - 순차 처리 + 배치 간 1.5초 딜레이 (429 Rate Limit 방지)
     * - 기존 학습된 템플릿은 중복 제거
     *
     * @param items (smsBody, senderAddress, analysis) 트리플 목록
     */
    suspend fun batchLearnFromRegexResults(
        items: List<Triple<String, String, SmsAnalysisResult>>
    ) {
        if (items.isEmpty()) return

        // 이미 학습된 패턴 중복 방지: 기존 템플릿 로드
        val existingTemplates = smsPatternDao.getAllPaymentPatterns()
            .map { it.smsTemplate }
            .toHashSet()

        // 템플릿화 + 중복 필터링
        val templatedItems = items.mapNotNull { (smsBody, address, analysis) ->
            if (analysis.amount <= 0) return@mapNotNull null
            val template = embeddingService.templateizeSms(smsBody)
            if (template in existingTemplates) return@mapNotNull null
            Triple(template, address, analysis)
        }

        if (templatedItems.isEmpty()) {
            Log.d(TAG, "배치 학습: 새로운 패턴 없음 (모두 기존에 학습됨)")
            return
        }

        Log.d(TAG, "배치 학습 시작: ${templatedItems.size}건")

        // 100건씩 chunking (batchEmbedContents 최대값)
        val chunks = templatedItems.chunked(EMBEDDING_BATCH_SIZE)
        var learnedCount = 0

        // 순차 처리 + 배치 간 최소 딜레이
        for ((chunkIdx, chunk) in chunks.withIndex()) {
            val startTime = System.currentTimeMillis()
            Log.d(TAG, "[batchLearn] 학습 임베딩 배치 ${chunkIdx + 1}/${chunks.size} 시작 (${chunk.size}건)")
            val patterns = processChunk(chunk)
            val elapsed = System.currentTimeMillis() - startTime
            Log.d(
                TAG,
                "[batchLearn] 학습 임베딩 배치 ${chunkIdx + 1}/${chunks.size} 완료 (${elapsed}ms, 패턴: ${patterns.size}건)"
            )
            if (patterns.isNotEmpty()) {
                smsPatternDao.insertAll(patterns)
                learnedCount += patterns.size
            }

            // 배치 간 최소 딜레이 (마지막 chunk 제외)
            if (chunkIdx < chunks.size - 1) {
                kotlinx.coroutines.delay(RATE_LIMIT_DELAY_MS)
            }
        }

        Log.d(TAG, "배치 학습 완료: ${learnedCount}건 학습됨")
    }

    /**
     * 단일 chunk의 배치 임베딩 + SmsPatternEntity 생성
     */
    private suspend fun processChunk(
        chunk: List<Triple<String, String, SmsAnalysisResult>>
    ): List<SmsPatternEntity> {
        return try {
            val templates = chunk.map { it.first }
            val embeddings = embeddingService.generateEmbeddings(templates)

            chunk.zip(embeddings).mapNotNull { (item, embedding) ->
                if (embedding == null) return@mapNotNull null
                val (template, address, analysis) = item
                SmsPatternEntity(
                    smsTemplate = template,
                    senderAddress = address,
                    embedding = embedding,
                    isPayment = true,
                    parsedAmount = analysis.amount,
                    parsedStoreName = analysis.storeName,
                    parsedCardName = analysis.cardName,
                    parsedCategory = analysis.category,
                    parseSource = "regex",
                    confidence = 1.0f
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "배치 학습 실패 (chunk): ${e.message}")
            emptyList()
        }
    }

    // ========================
    // 내부 구현
    // ========================

    /**
     * Tier 1: 정규식 기반 분류
     */
    private fun classifyWithRegex(smsBody: String, smsTimestamp: Long): ClassificationResult? {
        if (!SmsParser.isCardPaymentSms(smsBody)) {
            return null
        }

        val analysis = SmsParser.parseSms(smsBody, smsTimestamp)
        if (analysis.amount <= 0) {
            return null
        }

        return ClassificationResult(
            isPayment = true,
            analysisResult = analysis,
            tier = 1,
            confidence = 1.0f
        )
    }

    /**
     * Tier 2: 벡터 유사도 기반 분류
     */
    private suspend fun classifyWithVector(
        smsBody: String,
        smsTimestamp: Long,
        senderAddress: String
    ): ClassificationResult? {
        try {
            // SMS 템플릿화 + 임베딩 생성 (결제/비결제 패턴 모두 검색해야 하므로 먼저 생성)
            val template = embeddingService.templateizeSms(smsBody)
            val queryVector = embeddingService.generateEmbedding(template)
            if (queryVector == null) {
                Log.e(TAG, "Tier 2: 임베딩 생성 실패")
                return null
            }

            // ===== 비결제 패턴 우선 검색 (빠른 필터링) =====
            val nonPaymentPatterns = smsPatternDao.getAllNonPaymentPatterns()
            if (nonPaymentPatterns.isNotEmpty()) {
                val nonPaymentMatch = VectorSearchEngine.findBestMatch(
                    queryVector = queryVector,
                    patterns = nonPaymentPatterns,
                    minSimilarity = SmsPatternSimilarityPolicy.NON_PAYMENT_CACHE_THRESHOLD  // 비결제 캐시: 0.97
                )
                if (nonPaymentMatch != null) {
                    Log.d(
                        TAG,
                        "Tier 2: 비결제 패턴 매칭! similarity=${nonPaymentMatch.similarity} → 비결제로 판정"
                    )
                    smsPatternDao.incrementMatchCount(nonPaymentMatch.pattern.id)
                    return ClassificationResult(
                        isPayment = false,
                        tier = 2,
                        confidence = nonPaymentMatch.similarity
                    )
                }
            }

            // ===== 결제 패턴 검색 =====
            val patterns = smsPatternDao.getAllPaymentPatterns()
            if (patterns.isEmpty()) {
                Log.d(TAG, "Tier 2: 결제 패턴 DB 비어있음, 스킵")
                return null
            }

            // 가장 유사한 결제 패턴 검색
            val bestMatch = VectorSearchEngine.findBestMatch(
                queryVector = queryVector,
                patterns = patterns,
                minSimilarity = SmsPatternSimilarityPolicy.profile.confirm
            )

            if (bestMatch == null) {
                Log.d(TAG, "Tier 2: 유사 패턴 없음 (임계값 미달)")
                return null
            }

            Log.d(TAG, "Tier 2: 유사 패턴 발견! similarity=${bestMatch.similarity}")

            // 매칭 횟수 업데이트
            smsPatternDao.incrementMatchCount(bestMatch.pattern.id)

            // 높은 유사도 → 캐시된 파싱 결과 재사용
            if (bestMatch.similarity >= SmsPatternSimilarityPolicy.profile.autoApply) {
                Log.d(TAG, "Tier 2: 캐시 재사용 (similarity=${bestMatch.similarity})")

                // 캐시된 결과에서 가게명/카드명 재사용, 금액/날짜는 현재 SMS에서 추출 시도
                val cachedPattern = bestMatch.pattern
                val currentAmount = SmsParser.extractAmount(smsBody) ?: cachedPattern.parsedAmount
                val currentDateTime = SmsParser.extractDateTime(smsBody, smsTimestamp)

                val analysis = SmsAnalysisResult(
                    amount = currentAmount,
                    storeName = extractStoreNameOrCached(smsBody, cachedPattern.parsedStoreName),
                    category = cachedPattern.parsedCategory,
                    dateTime = currentDateTime,
                    cardName = cachedPattern.parsedCardName
                )

                return ClassificationResult(
                    isPayment = true,
                    analysisResult = analysis,
                    tier = 2,
                    confidence = bestMatch.similarity
                )
            }

            // 중간 유사도 → 결제 문자로 판정은 되지만 파싱은 LLM에 위임
            Log.d(TAG, "Tier 2: 결제 판정 OK, LLM 추출 위임")
            val llmResult = classifyWithLlm(smsBody, smsTimestamp, senderAddress)
            if (llmResult != null && llmResult.isPayment) {
                return llmResult.copy(tier = 2)  // 판정은 벡터, 추출은 LLM
            }

            // LLM도 실패하면 캐시로 폴백
            val fallbackAmount = SmsParser.extractAmount(smsBody) ?: bestMatch.pattern.parsedAmount
            val fallbackDateTime = SmsParser.extractDateTime(smsBody, smsTimestamp)

            return ClassificationResult(
                isPayment = true,
                analysisResult = SmsAnalysisResult(
                    amount = fallbackAmount,
                    storeName = extractStoreNameOrCached(
                        smsBody,
                        bestMatch.pattern.parsedStoreName
                    ),
                    category = bestMatch.pattern.parsedCategory,
                    dateTime = fallbackDateTime,
                    cardName = bestMatch.pattern.parsedCardName
                ),
                tier = 2,
                confidence = bestMatch.similarity
            )
        } catch (e: Exception) {
            Log.e(TAG, "Tier 2 실패: ${e.message}", e)
            return null
        }
    }

    /**
     * Tier 3: LLM 기반 추출
     *
     * @param senderAddress 향후 LLM 프롬프트에 발신자 정보 포함 시 사용 예정
     */
    @Suppress("UNUSED_PARAMETER")
    private suspend fun classifyWithLlm(
        smsBody: String,
        smsTimestamp: Long,
        senderAddress: String
    ): ClassificationResult? {
        try {
            val extraction = smsExtractor.extractFromSms(smsBody, smsTimestamp) ?: return null

            if (!extraction.isPayment || extraction.amount <= 0) {
                Log.d(TAG, "Tier 3: LLM이 비결제로 판정 또는 금액 0")
                // 비결제 패턴 학습은 배치로 처리 (개별 API 호출 방지)
                return ClassificationResult(isPayment = false, tier = 3, confidence = 0.8f)
            }

            val rawDateTime = if (extraction.dateTime.isNotBlank()) {
                extraction.dateTime
            } else {
                SmsParser.extractDateTime(smsBody, smsTimestamp)
            }
            // LLM이 추출한 연도가 잘못될 수 있으므로 SMS 수신 시간 기준으로 검증
            val dateTime = DateUtils.validateExtractedDateTime(rawDateTime, smsTimestamp)

            val analysis = SmsAnalysisResult(
                amount = extraction.amount,
                storeName = extraction.storeName,
                category = extraction.category,
                dateTime = dateTime,
                cardName = extraction.cardName
            )

            // LLM 결과 학습은 배치로 처리 (개별 API 호출 방지)

            return ClassificationResult(
                isPayment = true,
                analysisResult = analysis,
                tier = 3,
                confidence = 0.8f  // LLM 추출은 고정 신뢰도
            )
        } catch (e: Exception) {
            Log.e(TAG, "Tier 3 실패: ${e.message}", e)
            return null
        }
    }

    /**
     * 패턴 학습: 성공적으로 파싱된 SMS를 벡터 DB에 등록
     */
    private suspend fun learnPattern(
        smsBody: String,
        senderAddress: String,
        analysis: SmsAnalysisResult?,
        source: String
    ) {
        if (analysis == null || analysis.amount <= 0) return

        try {
            val template = embeddingService.templateizeSms(smsBody)
            val embedding = embeddingService.generateEmbedding(template)
            if (embedding == null) {
                Log.w(TAG, "학습 실패: 임베딩 생성 불가")
                return
            }

            val pattern = SmsPatternEntity(
                smsTemplate = template,
                senderAddress = senderAddress,
                embedding = embedding,
                isPayment = true,
                parsedAmount = analysis.amount,
                parsedStoreName = analysis.storeName,
                parsedCardName = analysis.cardName,
                parsedCategory = analysis.category,
                parseSource = source,
                confidence = if (source == "regex") 1.0f else 0.8f
            )

            smsPatternDao.insert(pattern)
            Log.d(TAG, "패턴 학습 완료: ${analysis.storeName} ($source)")
        } catch (e: Exception) {
            Log.e(TAG, "패턴 학습 실패: ${e.message}", e)
        }
    }

    /**
     * 비결제 패턴 학습: LLM이 비결제로 판정한 SMS를 벡터 DB에 등록
     *
     * 다음에 유사한 SMS가 오면 Tier 2에서 바로 비결제로 판정하여
     * 불필요한 LLM 호출을 방지합니다.
     */
    private suspend fun learnNonPaymentPattern(
        smsBody: String,
        senderAddress: String
    ) {
        try {
            val template = embeddingService.templateizeSms(smsBody)
            val embedding = embeddingService.generateEmbedding(template)
            if (embedding == null) {
                Log.w(TAG, "비결제 패턴 학습 실패: 임베딩 생성 불가")
                return
            }

            val pattern = SmsPatternEntity(
                smsTemplate = template,
                senderAddress = senderAddress,
                embedding = embedding,
                isPayment = false,
                parsedAmount = 0,
                parsedStoreName = "",
                parsedCardName = "",
                parsedCategory = "",
                parseSource = "llm_non_payment",
                confidence = 0.8f
            )

            smsPatternDao.insert(pattern)
            Log.d(TAG, "비결제 패턴 학습 완료: ${smsBody.take(30)}...")
        } catch (e: Exception) {
            Log.e(TAG, "비결제 패턴 학습 실패: ${e.message}", e)
        }
    }

    /**
     * 현재 SMS에서 가게명 추출 시도, 실패하면 캐시된 값 사용
     */
    private fun extractStoreNameOrCached(smsBody: String, cachedName: String): String {
        val extracted = SmsParser.extractStoreName(smsBody)
        return if (extracted != "결제" && extracted.length >= 2) extracted else cachedName
    }

    /**
     * 오래된 패턴 정리 (30일 이상 미사용 + 1회만 매칭)
     */
    suspend fun cleanupStalePatterns() {
        val threshold = System.currentTimeMillis() - STALE_PATTERN_THRESHOLD_MS
        smsPatternDao.deleteStalePatterns(threshold)
        Log.d(TAG, "오래된 패턴 정리 완료")
    }

    /**
     * 명백한 비결제 SMS 판별
     *
     * NON_PAYMENT_KEYWORDS에 포함된 키워드가 SMS 본문에 있으면
     * LLM 호출 없이 즉시 비결제로 판정합니다.
     *
     * 예: 인증번호, 국외발신, 광고, 택배 등
     *
     * @param smsBody SMS 본문
     * @return true면 명백한 비결제 SMS
     */
    private fun isObviouslyNonPayment(smsBody: String): Boolean {
        val lowerBody = smsBody.lowercase()
        return NON_PAYMENT_KEYWORDS_LOWER.any { keyword ->
            lowerBody.contains(keyword)
        }
    }

    /**
     * 결제 가능성 사전 체크 (LLM 호출 비용 통제)
     *
     * 금액 패턴, 결제 키워드, 카드사 키워드 중 2개 이상 매칭되면
     * 결제 SMS일 가능성이 높다고 판단하여 LLM 호출을 허용합니다.
     *
     * @param smsBody SMS 본문
     * @return true면 결제 가능성 있음 (LLM 호출 허용)
     */
    private fun hasPotentialPaymentIndicators(smsBody: String): Boolean {
        var indicatorCount = 0

        // 1. 금액 패턴 (숫자+원) — 사전 컴파일된 Regex 사용
        if (AMOUNT_WON_PATTERN.containsMatchIn(smsBody)) indicatorCount++

        // 2. 결제 키워드 ("누적"은 카드사 누적 사용금액 표시로 결제 SMS 가능성 높음)
        val paymentKeywords = listOf("승인", "결제", "출금", "사용", "이용", "체크카드", "신용카드", "누적")
        if (paymentKeywords.any { smsBody.contains(it) }) indicatorCount++

        // 3. 카드사 키워드
        val cardName = SmsParser.extractCardName(smsBody)
        if (cardName != "기타") indicatorCount++

        return indicatorCount >= 2
    }

}
