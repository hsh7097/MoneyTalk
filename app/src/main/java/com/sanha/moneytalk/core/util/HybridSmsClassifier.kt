package com.sanha.moneytalk.core.util

import android.util.Log
import com.sanha.moneytalk.core.database.dao.SmsPatternDao
import com.sanha.moneytalk.core.database.entity.SmsPatternEntity
import com.sanha.moneytalk.feature.chat.data.SmsAnalysisResult
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
 * 부트스트랩 모드:
 *   패턴 DB에 데이터가 적을 때(< 10개) LLM을 적극적으로 사용하여
 *   초기 패턴 데이터를 빠르게 축적합니다.
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
            // 안내/알림 (비결제)
            "명세서", "청구서", "이용대금", "결제예정", "결제일",
            "출금예정", "자동이체", "납부안내",
            // 배송/택배
            "배송", "택배", "운송장",
            // 기타 비결제
            "설문", "survey", "투표"
        )
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
     * SMS를 3-tier로 분류하고 파싱
     *
     * @param smsBody SMS 본문
     * @param smsTimestamp SMS 수신 시간
     * @param senderAddress 발신 번호
     * @return 분류 결과
     */
    suspend fun classify(
        smsBody: String,
        smsTimestamp: Long,
        senderAddress: String = ""
    ): ClassificationResult {
        Log.d(TAG, "=== 3-tier 분류 시작 ===")
        Log.d(TAG, "SMS: ${smsBody.take(60)}...")

        // ===== 1단계: Regex =====
        val regexResult = classifyWithRegex(smsBody, smsTimestamp)
        if (regexResult != null) {
            Log.d(TAG, "✅ Tier 1 (Regex) 성공: amount=${regexResult.analysisResult?.amount}")
            // 정규식 성공 → 벡터 DB에 학습
            learnPattern(smsBody, senderAddress, regexResult.analysisResult, "regex")
            return regexResult
        }

        // ===== 2단계: Vector 유사도 =====
        val vectorResult = classifyWithVector(smsBody, smsTimestamp, senderAddress)
        if (vectorResult != null) {
            Log.d(TAG, "✅ Tier 2 (Vector) 성공: tier=${vectorResult.tier}, confidence=${vectorResult.confidence}")
            return vectorResult
        }

        // ===== 부트스트랩 모드 체크 =====
        val patternCount = smsPatternDao.getPaymentPatternCount()
        val isBootstrap = patternCount < BOOTSTRAP_THRESHOLD

        // ===== 3단계: LLM (부트스트랩 모드이거나 벡터 DB에 없을 때) =====
        if (isBootstrap) {
            // 사전 필터링: 명백히 비결제인 SMS는 LLM 호출 생략
            if (isObviouslyNonPayment(smsBody)) {
                Log.d(TAG, "⏭️ 부트스트랩 스킵: 명백한 비결제 SMS")
                return ClassificationResult(isPayment = false, tier = 0, confidence = 0f)
            }

            Log.d(TAG, "🔄 부트스트랩 모드 (패턴 $patternCount 개): LLM 추출 시도")
            val llmResult = classifyWithLlm(smsBody, smsTimestamp, senderAddress)
            if (llmResult != null) {
                Log.d(TAG, "✅ Tier 3 (LLM-Bootstrap) 성공: isPayment=${llmResult.isPayment}")
                return llmResult
            }
        }

        // 모든 단계 실패 → 비결제 문자로 판정
        Log.d(TAG, "❌ 모든 tier 실패: 비결제 문자로 판정")
        return ClassificationResult(
            isPayment = false,
            tier = 0,
            confidence = 0f
        )
    }

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
     * Regex 성공 결과를 벡터 DB에 학습
     *
     * syncSmsMessages에서 regex로 처리한 SMS를 벡터 DB에 등록할 때 사용.
     * 이를 통해 벡터 DB가 점진적으로 채워짐.
     */
    suspend fun learnFromRegexResult(
        smsBody: String,
        senderAddress: String,
        analysis: SmsAnalysisResult
    ) {
        learnPattern(smsBody, senderAddress, analysis, "regex")
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
                    minSimilarity = VectorSearchEngine.CACHE_REUSE_THRESHOLD  // 높은 임계값: 0.97
                )
                if (nonPaymentMatch != null) {
                    Log.d(TAG, "Tier 2: 비결제 패턴 매칭! similarity=${nonPaymentMatch.similarity} → 비결제로 판정")
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
                minSimilarity = VectorSearchEngine.PAYMENT_SIMILARITY_THRESHOLD
            )

            if (bestMatch == null) {
                Log.d(TAG, "Tier 2: 유사 패턴 없음 (임계값 미달)")
                return null
            }

            Log.d(TAG, "Tier 2: 유사 패턴 발견! similarity=${bestMatch.similarity}")

            // 매칭 횟수 업데이트
            smsPatternDao.incrementMatchCount(bestMatch.pattern.id)

            // 높은 유사도 → 캐시된 파싱 결과 재사용
            if (bestMatch.similarity >= VectorSearchEngine.CACHE_REUSE_THRESHOLD) {
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
                    storeName = extractStoreNameOrCached(smsBody, bestMatch.pattern.parsedStoreName),
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
     */
    private suspend fun classifyWithLlm(
        smsBody: String,
        smsTimestamp: Long,
        senderAddress: String
    ): ClassificationResult? {
        try {
            val extraction = smsExtractor.extractFromSms(smsBody) ?: return null

            if (!extraction.isPayment || extraction.amount <= 0) {
                Log.d(TAG, "Tier 3: LLM이 비결제로 판정 또는 금액 0")
                // 비결제 판정 결과를 벡터 DB에 학습 → 다음에 Tier 2에서 바로 필터링
                learnNonPaymentPattern(smsBody, senderAddress)
                return ClassificationResult(isPayment = false, tier = 3, confidence = 0.8f)
            }

            val dateTime = if (extraction.dateTime.isNotBlank()) {
                extraction.dateTime
            } else {
                SmsParser.extractDateTime(smsBody, smsTimestamp)
            }

            val analysis = SmsAnalysisResult(
                amount = extraction.amount,
                storeName = extraction.storeName,
                category = extraction.category,
                dateTime = dateTime,
                cardName = extraction.cardName
            )

            // LLM 성공 → 벡터 DB에 학습
            learnPattern(smsBody, senderAddress, analysis, "llm")

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
     * 패턴 DB 통계 조회
     */
    suspend fun getPatternStats(): PatternStats {
        return PatternStats(
            totalPatterns = smsPatternDao.getPatternCount(),
            paymentPatterns = smsPatternDao.getPaymentPatternCount(),
            isBootstrapMode = smsPatternDao.getPaymentPatternCount() < BOOTSTRAP_THRESHOLD
        )
    }

    /**
     * 오래된 패턴 정리 (30일 이상 미사용 + 1회만 매칭)
     */
    suspend fun cleanupStalePatterns() {
        val threshold = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
        smsPatternDao.deleteStalePatterns(threshold)
        Log.d(TAG, "오래된 패턴 정리 완료")
    }

    /**
     * 명백한 비결제 SMS 판별 (부트스트랩 모드 사전 필터링)
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
        return NON_PAYMENT_KEYWORDS.any { keyword ->
            lowerBody.contains(keyword.lowercase())
        }
    }

    data class PatternStats(
        val totalPatterns: Int,
        val paymentPatterns: Int,
        val isBootstrapMode: Boolean
    )
}
