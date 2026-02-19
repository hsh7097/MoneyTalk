package com.sanha.moneytalk.core.sms2

import android.util.Log
import com.sanha.moneytalk.core.database.dao.SmsPatternDao
import com.sanha.moneytalk.core.database.entity.SmsPatternEntity
import com.sanha.moneytalk.core.model.SmsAnalysisResult
import com.sanha.moneytalk.core.similarity.SmsPatternSimilarityPolicy
import com.sanha.moneytalk.core.util.DateUtils
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
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

        /** 임베딩 배치 처리 크기 (Google batchEmbedContents 최대값) */
        private const val EMBEDDING_BATCH_SIZE = 100

        /** 임베딩 배치 병렬 동시 실행 수 (API 키 5개 × 키당 2 = 10) */
        private const val EMBEDDING_CONCURRENCY = 10

        /** LLM 병렬 동시 실행 수 (API 키 5개 × 키당 1 = 5, LLM은 임베딩보다 무거움) */
        private const val LLM_CONCURRENCY = 5

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
            "결제내역", "명세서", "청구서", "이용대금", "결제예정", "결제일",
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
     * 1. 사전 필터링: 20자 미만/100자 초과 + 비결제 키워드 → 조기 제거
     * 2. Regex: 필터 통과한 SMS에만 정규식 적용
     * 3. 배치 임베딩 생성 (100건씩 batchEmbedContents)
     * 4. 벡터 DB 매칭 (비결제 패턴 → 결제 패턴 순)
     * 5. 매칭 안 된 SMS 중 결제 가능성 있는 것만 LLM에 전달
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

        // ===== Step 1: 사전 필터링 (Regex보다 먼저 — 명백한 비결제 SMS 조기 제거) =====
        val preFilterPassed = mutableListOf<Int>() // 사전 필터 통과 인덱스
        for (i in smsList.indices) {
            val body = smsList[i].first
            if (body.length > SmsParser.MAX_SMS_LENGTH || body.length < 20) {
                // 20자 미만/100자 초과 SMS → 결제 가능성 낮음
                results[i] = ClassificationResult(isPayment = false, tier = 0, confidence = 0f)
            } else if (isObviouslyNonPayment(body)) {
                // 명백한 비결제 키워드 → 즉시 비결제 판정 (Regex도 스킵)
                results[i] = ClassificationResult(isPayment = false, tier = 0, confidence = 0f)
            } else {
                preFilterPassed.add(i)
            }
        }
        val preFilteredCount = smsList.size - preFilterPassed.size
        Log.d(
            TAG,
            "배치 사전 필터링: ${preFilteredCount}건 비결제 제외, ${preFilterPassed.size}건 통과"
        )

        if (preFilterPassed.isEmpty()) return results.toList()

        // ===== Step 2: Regex 처리 (사전 필터 통과한 SMS만) =====
        val regexUnresolved = mutableListOf<Int>() // Regex 미통과 인덱스
        for (idx in preFilterPassed) {
            val (body, timestamp, _) = smsList[idx]
            val regexResult = classifyWithRegex(body, timestamp)
            if (regexResult != null) {
                results[idx] = regexResult
            } else {
                regexUnresolved.add(idx)
            }
        }
        Log.d(
            TAG,
            "배치 Regex: ${preFilterPassed.size - regexUnresolved.size}건 성공, ${regexUnresolved.size}건 미통과"
        )

        if (regexUnresolved.isEmpty()) return results.toList()

        // regexUnresolved가 곧 벡터 후보 (사전 필터 이미 통과)
        val vectorCandidateIndices = regexUnresolved

        // ===== Step 3: 배치 임베딩 생성 (Regex 미통과 + 사전 필터 통과, 병렬) =====
        val templates =
            vectorCandidateIndices.map { embeddingService.templateizeSms(smsList[it].first) }

        // 100건씩 배치 → Semaphore로 동시 N개 병렬 실행
        val batches = templates.chunked(EMBEDDING_BATCH_SIZE)
        val embeddingStartTime = System.currentTimeMillis()
        Log.d(TAG, "[batchClassify] 임베딩 시작: ${templates.size}건 → ${batches.size}개 배치 (병렬 $EMBEDDING_CONCURRENCY)")

        val semaphore = Semaphore(EMBEDDING_CONCURRENCY)
        val allEmbeddings = coroutineScope {
            batches.mapIndexed { batchIdx, batch ->
                async {
                    semaphore.withPermit {
                        val startTime = System.currentTimeMillis()
                        val embeddings = embeddingService.generateEmbeddings(batch)
                        val elapsed = System.currentTimeMillis() - startTime
                        Log.d(TAG, "[batchClassify] 임베딩 배치 ${batchIdx + 1}/${batches.size} 완료 (${elapsed}ms, 성공: ${embeddings.count { it != null }}/${batch.size})")
                        embeddings
                    }
                }
            }.awaitAll().flatten()
        }

        val embeddingElapsed = System.currentTimeMillis() - embeddingStartTime
        Log.d(TAG, "배치 임베딩 완료: ${allEmbeddings.count { it != null }}/${allEmbeddings.size}건 성공 (${embeddingElapsed}ms)")

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

            // 결제 패턴 매칭 — LLM 트리거(0.80) 이상부터 검색
            if (paymentPatterns.isNotEmpty()) {
                val bestMatch = VectorSearchEngine.findBestMatch(
                    queryVector = embedding,
                    patterns = paymentPatterns,
                    minSimilarity = SmsPatternSimilarityPolicy.LLM_TRIGGER_THRESHOLD
                )

                if (bestMatch != null) {
                    if (bestMatch.similarity >= SmsPatternSimilarityPolicy.profile.autoApply) {
                        // 높은 유사도(≥0.95) → 캐시 재사용
                        smsPatternDao.incrementMatchCount(bestMatch.pattern.id)
                        val cached = bestMatch.pattern
                        val parsed = buildAnalysisFromPattern(
                            smsBody = body,
                            smsTimestamp = timestamp,
                            pattern = cached
                        )
                        if (parsed == null) {
                            llmCandidates.add(originalIdx)
                            continue
                        }

                        results[originalIdx] = ClassificationResult(
                            isPayment = true,
                            analysisResult = parsed,
                            tier = 2,
                            confidence = bestMatch.similarity
                        )
                    } else if (bestMatch.similarity >= SmsPatternSimilarityPolicy.profile.confirm) {
                        // 중간 유사도(0.92~0.95) → 결제 판정은 됐지만 파싱은 캐시 폴백
                        smsPatternDao.incrementMatchCount(bestMatch.pattern.id)
                        val parsed = buildAnalysisFromPattern(
                            smsBody = body,
                            smsTimestamp = timestamp,
                            pattern = bestMatch.pattern
                        )
                        if (parsed == null) {
                            llmCandidates.add(originalIdx)
                            continue
                        }

                        results[originalIdx] = ClassificationResult(
                            isPayment = true,
                            analysisResult = parsed,
                            tier = 2,
                            confidence = bestMatch.similarity
                        )
                    } else {
                        // 낮은 유사도(0.80~0.92) → 결제 가능성은 있지만 확정 아님 → LLM 트리거
                        // matchCount 증가하지 않음 — 확정 매칭이 아니므로 통계 오염 방지
                        Log.d(TAG, "[batchClassify] 벡터 유사도 ${bestMatch.similarity} (0.80~0.92) → LLM 트리거: ${body.take(30)}")
                        llmCandidates.add(originalIdx)
                    }
                    continue
                }
            }

            // 벡터 매칭 안 됨 (0.80 미만) → 기존 키워드 기반 LLM 후보 선별
            if (hasPotentialPaymentIndicators(body)) {
                llmCandidates.add(originalIdx)
            }
        }

        // ===== Step 5: LLM 호출 (벡터 미매칭 중 결제 가능성 있는 SMS, 병렬) =====
        if (llmCandidates.isNotEmpty()) {
            Log.d(TAG, "[batchClassify] LLM 후보: ${llmCandidates.size}건 (병렬 $LLM_CONCURRENCY)")
            val llmSemaphore = Semaphore(LLM_CONCURRENCY)
            val llmResults = coroutineScope {
                llmCandidates.mapIndexed { llmIdx, idx ->
                    val (body, timestamp, address) = smsList[idx]
                    async {
                        llmSemaphore.withPermit {
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
                            Triple(idx, llmResult, Triple(body, timestamp, address))
                        }
                    }
                }.awaitAll()
            }

            // 결과 적용 + 패턴 학습 (병렬)
            val learnTargets = mutableListOf<Triple<Int, ClassificationResult, Triple<String, Long, String>>>()
            for ((idx, llmResult, smsInfo) in llmResults) {
                if (llmResult != null) {
                    results[idx] = llmResult
                    if (llmResult.isPayment && llmResult.analysisResult != null) {
                        learnTargets.add(Triple(idx, llmResult, smsInfo))
                    }
                }
            }

            // 패턴 학습도 병렬 처리
            if (learnTargets.isNotEmpty()) {
                coroutineScope {
                    learnTargets.map { (_, llmResult, smsInfo) ->
                        val (body, timestamp, address) = smsInfo
                        async {
                            llmSemaphore.withPermit {
                                learnPatternWithLlmRegex(
                                    smsBody = body,
                                    smsTimestamp = timestamp,
                                    senderAddress = address,
                                    analysis = llmResult.analysisResult!!
                                )
                            }
                        }
                    }.awaitAll()
                }
            }
        }

        val resultList = results.toList()
        val paymentCount = resultList.count { it.isPayment }
        val tier1Count = resultList.count { it.isPayment && it.tier == 1 }
        val tier2Count = resultList.count { it.isPayment && it.tier == 2 }
        val tier3Count = resultList.count { it.isPayment && it.tier == 3 }
        Log.d(TAG, "=== 배치 분류 완료: ${smsList.size}건 중 결제 ${paymentCount}건 " +
                "(Tier1:$tier1Count, Tier2:$tier2Count, Tier3:$tier3Count) " +
                "사전필터:${preFilteredCount}건, LLM호출:${llmCandidates.size}건 ===")

        return resultList
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

        // 100건씩 chunking → 병렬 처리
        val chunks = templatedItems.chunked(EMBEDDING_BATCH_SIZE)
        val learnStartTime = System.currentTimeMillis()
        Log.d(TAG, "[batchLearn] ${chunks.size}개 배치 병렬 임베딩 시작 (동시 $EMBEDDING_CONCURRENCY)")

        val semaphore = Semaphore(EMBEDDING_CONCURRENCY)
        val allPatterns = coroutineScope {
            chunks.mapIndexed { chunkIdx, chunk ->
                async {
                    semaphore.withPermit {
                        val startTime = System.currentTimeMillis()
                        val patterns = processChunk(chunk)
                        val elapsed = System.currentTimeMillis() - startTime
                        Log.d(TAG, "[batchLearn] 학습 배치 ${chunkIdx + 1}/${chunks.size} 완료 (${elapsed}ms, 패턴: ${patterns.size}건)")
                        patterns
                    }
                }
            }.awaitAll()
        }

        var learnedCount = 0
        for (patterns in allPatterns) {
            if (patterns.isNotEmpty()) {
                smsPatternDao.insertAll(patterns)
                learnedCount += patterns.size
            }
        }

        val learnElapsed = System.currentTimeMillis() - learnStartTime
        Log.d(TAG, "배치 학습 완료: ${learnedCount}건 학습됨 (${learnElapsed}ms)")
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

        // Regex 오파싱 방어: 가게명이 기본값("결제")이면 파싱 품질 불충분
        // → Tier 2/3으로 이관하여 Vector/LLM이 정확한 가게명을 추출하도록 함
        // 현대카드 등 카드사별 다른 포맷에서 regex가 "성공하되 잘못 파싱"하는 케이스 방어
        if (analysis.storeName == "결제") {
            Log.d(TAG, "Regex 오파싱 방어: storeName='결제' → Tier 2/3 이관 [${smsBody.take(40)}]")
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

                val cachedPattern = bestMatch.pattern
                val analysis = buildAnalysisFromPattern(smsBody, smsTimestamp, cachedPattern)
                    ?: return null

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
            val fallbackAnalysis =
                buildAnalysisFromPattern(smsBody, smsTimestamp, bestMatch.pattern) ?: return null

            return ClassificationResult(
                isPayment = true,
                analysisResult = fallbackAnalysis,
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
     * LLM 결제 결과를 동적 정규식과 함께 패턴 DB에 저장
     *
     * 소량 배치(batchClassify)에서도 임베딩↔정규식 매핑이 누적되도록 합니다.
     */
    private suspend fun learnPatternWithLlmRegex(
        smsBody: String,
        smsTimestamp: Long,
        senderAddress: String,
        analysis: SmsAnalysisResult
    ) {
        if (analysis.amount <= 0) return

        try {
            val template = embeddingService.templateizeSms(smsBody)

            // 같은 발신자+템플릿 패턴이 이미 있으면 중복 저장 스킵
            val isDuplicate = smsPatternDao.getPatternsBySender(senderAddress).any {
                it.smsTemplate == template && it.isPayment
            }
            if (isDuplicate) return

            val embedding = embeddingService.generateEmbedding(template) ?: return

            val regexResult = smsExtractor.generateRegexForSms(
                smsBody = smsBody,
                smsTimestamp = smsTimestamp
            )
            val hasRegex = regexResult != null &&
                regexResult.isPayment &&
                regexResult.amountRegex.isNotBlank() &&
                regexResult.storeRegex.isNotBlank()

            val pattern = SmsPatternEntity(
                smsTemplate = template,
                senderAddress = senderAddress,
                embedding = embedding,
                isPayment = true,
                parsedAmount = analysis.amount,
                parsedStoreName = analysis.storeName,
                parsedCardName = analysis.cardName,
                parsedCategory = analysis.category,
                amountRegex = if (hasRegex) regexResult?.amountRegex.orEmpty() else "",
                storeRegex = if (hasRegex) regexResult?.storeRegex.orEmpty() else "",
                cardRegex = if (hasRegex) regexResult?.cardRegex.orEmpty() else "",
                parseSource = if (hasRegex) "llm_regex" else "llm",
                confidence = 0.8f
            )
            smsPatternDao.insert(pattern)
        } catch (e: Exception) {
            Log.w(TAG, "LLM 정규식 패턴 학습 실패: ${e.message}")
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
     * 저장된 패턴으로 현재 SMS를 파싱
     *
     * 1) 동적 정규식이 있으면 우선 적용
     * 2) 실패 시 기존 캐시 + SmsParser 폴백
     */
    private fun buildAnalysisFromPattern(
        smsBody: String,
        smsTimestamp: Long,
        pattern: SmsPatternEntity
    ): SmsAnalysisResult? {
        val regexParsed = if (GeneratedSmsRegexParser.hasUsableRegex(pattern)) {
            GeneratedSmsRegexParser.parseWithPattern(
                smsBody = smsBody,
                smsTimestamp = smsTimestamp,
                pattern = pattern
            )
        } else {
            null
        }
        if (regexParsed != null) return regexParsed

        val amount = SmsParser.extractAmount(smsBody) ?: pattern.parsedAmount
        if (amount <= 0) return null

        val storeName = extractStoreNameOrCached(smsBody, pattern.parsedStoreName)
        if (storeName.isBlank()) return null

        val cardFromSms = SmsParser.extractCardName(smsBody)
        val cardName = if (cardFromSms != "기타") cardFromSms else pattern.parsedCardName.ifBlank { "기타" }
        val category =
            pattern.parsedCategory.ifBlank { SmsParser.inferCategory(storeName, smsBody) }

        return SmsAnalysisResult(
            amount = amount,
            storeName = storeName,
            category = category,
            dateTime = SmsParser.extractDateTime(smsBody, smsTimestamp),
            cardName = cardName
        )
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
