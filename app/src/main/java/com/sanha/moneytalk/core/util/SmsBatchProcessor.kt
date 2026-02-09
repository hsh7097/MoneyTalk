package com.sanha.moneytalk.core.util

import android.util.Log
import com.sanha.moneytalk.core.database.dao.SmsPatternDao
import com.sanha.moneytalk.core.database.entity.SmsPatternEntity
import com.sanha.moneytalk.core.similarity.SmsPatternSimilarityPolicy
import com.sanha.moneytalk.core.util.DateUtils
import com.sanha.moneytalk.core.model.SmsAnalysisResult
import kotlinx.coroutines.yield
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 대량 SMS 일괄 처리기 (Initial Sync / Full Sync 전용)
 *
 * 수만 건의 과거 SMS를 처리할 때 LLM 호출을 최소화하는 전략:
 *
 * 1단계: Bulk Regex Filter
 *   → 전체 SMS에 SmsParser를 적용하여 1차 분류 (비용 0)
 *   → 통과한 SMS는 즉시 저장 + 벡터 DB 학습
 *
 * 2단계: Vector Similarity Grouping (Deduplication)
 *   → 정규식 미통과 SMS를 템플릿화 → 배치 임베딩 생성
 *   → 유사도 ≥ 0.95인 SMS를 하나의 '패턴 그룹'으로 묶음
 *   → 각 그룹에서 대표 1건만 선정
 *
 * 3단계: Representative Sampling (LLM)
 *   → 대표 SMS만 Gemini에게 결제 여부 판별 요청
 *   → 결제로 확인된 그룹 → 전체 멤버에 파싱 결과 적용
 *
 * 4단계: Batch Update
 *   → 확인된 패턴 그룹 전체를 벡터 DB에 일괄 등록
 *
 * 결과: 수만 건 → 수십 건 LLM 호출로 축소
 */
@Singleton
class SmsBatchProcessor @Inject constructor(
    private val smsPatternDao: SmsPatternDao,
    private val embeddingService: SmsEmbeddingService,
    private val smsExtractor: GeminiSmsExtractor
) {
    companion object {
        private const val TAG = "SmsBatchProcessor"

        /** 벡터 그룹핑 임계값 → SmsPatternSimilarityPolicy.profile.group 참조 */

        /** 배치 임베딩 한 번에 처리할 최대 개수 (batchEmbedContents 최대 100) */
        private const val EMBEDDING_BATCH_SIZE = 100

        /** 한 번에 처리할 최대 미분류 SMS 수 */
        private const val MAX_UNCLASSIFIED_TO_PROCESS = 500

        /** LLM 배치 호출 시 한번에 처리할 그룹 수 */
        private const val LLM_BATCH_SIZE = 20

        // LLM/임베딩 배치 간 고정 딜레이 제거 → 429 발생 시에만 SmsEmbeddingService에서 백오프

        /**
         * 사전 필터링 키워드: 이 키워드가 포함된 SMS는 임베딩 생성 자체를 스킵
         */
        private val NON_PAYMENT_KEYWORDS = listOf(
            // 인증/보안
            "인증번호", "인증코드", "authentication", "verification", "code",
            "OTP", "본인확인", "비밀번호",
            // 해외 발신
            "국외발신", "국제발신", "해외발신",
            // 광고/마케팅 필수어
            "광고", "[광고]", "(광고)", "무료수신거부", "수신거부", "080",
            // 홍보/유혹
            "특가", "이벤트", "증정", "당첨", "축하", "최저가", "마감직전",
            "포인트 적립", "혜택안내", "프로모션", "할인쿠폰", "무료체험",
            // 안내/기타
            "안내문", "점검", "정기점검", "공지사항",
            "회원님", "고객님", "불편을 드려",
            // 청구/안내
            "명세서", "청구서", "이용대금", "결제예정", "결제일",
            "출금예정", "자동이체", "납부안내", "납입일",
            // 배송
            "배송", "택배", "운송장",
            // 설문/투표
            "설문", "survey", "투표",
            // 예약/안내 (결제금액 없이 단순 안내)
            "예약은", "방문때", "접수 완료",
            "보험금", "해외원화결제시", "수수료 발생", "차단신청",
            // 금융/부동산 (결제가 아닌 광고)
            "금리", "대출", "투자", "수익", "분양", "모델하우스"
        )

        /** NON_PAYMENT_KEYWORDS를 미리 lowercase로 캐시 (매번 .lowercase() 호출 방지) */
        private val NON_PAYMENT_KEYWORDS_LOWER = NON_PAYMENT_KEYWORDS.map { it.lowercase() }

        /** HTTP 링크 패턴 */
        private val HTTP_PATTERN = Regex("https?://", RegexOption.IGNORE_CASE)

        /** 결제 SMS 최소 금액 자릿수 (2자리 이상 연속 숫자 필요) */
        private val AMOUNT_PATTERN = Regex("\\d{2,}")

        /** 금액+원 패턴 (사전 컴파일, lacksPaymentRequirements()용) */
        private val AMOUNT_WITH_WON_PATTERN = Regex("[\\d,]+원")

        /** 결제 관련 핵심 키워드 (이 중 하나라도 있으면 결제 가능성 있음) */
        private val PAYMENT_HINT_KEYWORDS = listOf(
            "승인", "결제", "출금", "이체",
            "원", "USD", "JPY", "EUR",
            "카드", "체크", "CMS"
        )
    }

    /**
     * 명백한 비결제 SMS 판별 (키워드 기반 사전 필터링)
     */
    private fun isObviouslyNonPayment(smsBody: String): Boolean {
        val lowerBody = smsBody.lowercase()
        return NON_PAYMENT_KEYWORDS_LOWER.any { keyword ->
            lowerBody.contains(keyword)
        }
    }

    /**
     * 결제 SMS 최소 조건 미충족 여부 판별 (구조적 필터링)
     *
     * 결제 문자가 되려면 최소한:
     * 1. 숫자가 있어야 함 (금액)
     * 2. 너무 짧지 않아야 함
     * 3. 2자리 이상 연속 숫자가 있어야 함 (금액 패턴)
     * 4. 결제 관련 키워드나 금액 패턴(숫자+원)이 있어야 함
     * 5. HTTP 링크만 있고 결제 키워드가 없으면 제외
     *
     * @return true면 결제 최소 조건 미충족 (필터링 대상)
     */
    private fun lacksPaymentRequirements(smsBody: String): Boolean {
        // 1. 20자 미만 SMS는 결제 문자 가능성 매우 낮음
        if (smsBody.length < 20) return true

        // 2. 숫자가 하나도 없으면 결제 문자 아님
        if (!smsBody.any { it.isDigit() }) return true

        // 3. 2자리 이상 연속 숫자가 없으면 결제 금액 패턴 아님
        if (!AMOUNT_PATTERN.containsMatchIn(smsBody)) return true

        // 4. HTTP 링크가 포함되어 있으면서 '결제'나 '승인'이 없으면 제외 (광고/안내 링크)
        if (HTTP_PATTERN.containsMatchIn(smsBody)) {
            val hasPaymentKeyword = smsBody.contains("결제") || smsBody.contains("승인")
            if (!hasPaymentKeyword) return true
        }

        // 5. 결제 힌트 키워드가 하나도 없고, 금액 패턴(숫자+원)도 없으면 제외
        val hasPaymentHint = PAYMENT_HINT_KEYWORDS.any { keyword ->
            smsBody.contains(keyword, ignoreCase = true)
        }
        val hasAmountWithUnit = AMOUNT_WITH_WON_PATTERN.containsMatchIn(smsBody)
        if (!hasPaymentHint && !hasAmountWithUnit) return true

        return false
    }

    /**
     * SMS 데이터 (Batch 처리용)
     */
    data class SmsData(
        val id: String,
        val address: String,
        val body: String,
        val date: Long
    )

    /**
     * 벡터 그룹: 유사한 SMS들의 묶음
     */
    private data class VectorGroup(
        val representative: SmsData,
        val representativeTemplate: String,
        val representativeEmbedding: List<Float>,
        val members: MutableList<SmsData>
    )

    /**
     * 대량 미분류 SMS 일괄 처리
     *
     * 정규식을 통과하지 못한 SMS들을 벡터 그룹핑 → 대표 샘플 LLM 검증
     * 방식으로 효율적으로 처리합니다.
     *
     * @param unclassifiedSms 정규식 미통과 SMS 목록
     * @return 결제로 확인된 SMS 목록 (SmsData + SmsAnalysisResult 쌍)
     */
    suspend fun processBatch(
        unclassifiedSms: List<SmsData>
    ): List<Pair<SmsData, SmsAnalysisResult>> {
        val results = mutableListOf<Pair<SmsData, SmsAnalysisResult>>()

        // 사전 필터링: 1) 키워드 기반 비결제 SMS 제외 2) 결제 최소 조건 미충족 SMS 제외
        val afterKeywordFilter = unclassifiedSms.filter { !isObviouslyNonPayment(it.body) }
        val filtered = afterKeywordFilter.filter { !lacksPaymentRequirements(it.body) }
        val targetSms = filtered.take(MAX_UNCLASSIFIED_TO_PROCESS)
        val keywordFiltered = unclassifiedSms.size - afterKeywordFilter.size
        val requirementFiltered = afterKeywordFilter.size - filtered.size
        Log.d(TAG, "=== 배치 처리 시작: ${targetSms.size}건 (전체 ${unclassifiedSms.size}건, 키워드 필터 ${keywordFiltered}건, 조건 미달 ${requirementFiltered}건 제외) ===")

        if (targetSms.isEmpty()) return results

        // ===== Step 1: 기존 벡터 DB의 패턴과 먼저 매칭 시도 =====
        val (matchedByExisting, remainingAfterExisting) = matchAgainstExistingPatterns(targetSms)
        results.addAll(matchedByExisting)
        Log.d(TAG, "기존 패턴 매칭: ${matchedByExisting.size}건 성공, ${remainingAfterExisting.size}건 남음")

        if (remainingAfterExisting.isEmpty()) return results

        // ===== Step 2: 남은 SMS를 템플릿화 + 배치 임베딩 =====
        val embeddedSms = generateBatchEmbeddings(remainingAfterExisting)
        Log.d(TAG, "벡터화 완료: ${embeddedSms.size}건")

        if (embeddedSms.isEmpty()) return results

        // ===== Step 3: 벡터 유사도 기반 그룹핑 =====
        val groups = groupBySimilarity(embeddedSms)
        Log.d(TAG, "그룹핑 완료: ${groups.size}개 그룹 (${embeddedSms.size}건)")

        // ===== Step 4: 그룹 대표들을 배치 LLM으로 일괄 분석 =====
        val llmBatches = groups.chunked(LLM_BATCH_SIZE)
        var llmBatchCount = 0
        var totalGroupsProcessed = 0

        Log.d(TAG, "LLM 배치 분석: ${groups.size}개 그룹 → ${llmBatches.size}개 배치 (배치 크기: $LLM_BATCH_SIZE)")

        for ((batchIdx, groupBatch) in llmBatches.withIndex()) {
            // 배치 내 대표 SMS 목록을 한번에 LLM에 전송 (수신 시간 포함)
            val smsTexts = groupBatch.map { it.representative.body }
            val smsTimestamps = groupBatch.map { it.representative.date }
            val extractions = try {
                smsExtractor.extractFromSmsBatch(smsTexts, smsTimestamps)
            } catch (e: Exception) {
                Log.e(TAG, "LLM 배치 추출 실패: ${e.message}")
                List(smsTexts.size) { null }
            }

            llmBatchCount++

            // 각 그룹에 결과 적용
            for ((groupIdx, group) in groupBatch.withIndex()) {
                val extraction = extractions.getOrNull(groupIdx)

                if (extraction != null && extraction.isPayment && extraction.amount > 0) {
                    val rawDateTime = if (extraction.dateTime.isNotBlank()) {
                        extraction.dateTime
                    } else {
                        SmsParser.extractDateTime(group.representative.body, group.representative.date)
                    }
                    // LLM이 추출한 연도가 SMS 수신 시간과 크게 다르면 교정
                    val dateTime = DateUtils.validateExtractedDateTime(rawDateTime, group.representative.date)

                    // 대표의 파싱 결과
                    val representativeAnalysis = SmsAnalysisResult(
                        amount = extraction.amount,
                        storeName = extraction.storeName,
                        category = extraction.category,
                        dateTime = dateTime,
                        cardName = extraction.cardName
                    )

                    // 대표를 벡터 DB에 등록
                    registerPattern(
                        group.representative,
                        group.representativeTemplate,
                        group.representativeEmbedding,
                        representativeAnalysis,
                        "llm"
                    )

                    // 대표 SMS 결과 추가
                    results.add(group.representative to representativeAnalysis)

                    // 그룹 멤버들에게 파싱 결과 전파 (금액/날짜만 개별 추출)
                    for (member in group.members) {
                        if (member.id == group.representative.id) continue

                        val memberAmount = SmsParser.extractAmount(member.body) ?: extraction.amount
                        val memberDateTime = SmsParser.extractDateTime(member.body, member.date)

                        val memberAnalysis = SmsAnalysisResult(
                            amount = memberAmount,
                            storeName = extraction.storeName,
                            category = extraction.category,
                            dateTime = memberDateTime,
                            cardName = extraction.cardName
                        )

                        results.add(member to memberAnalysis)
                    }

                    Log.d(TAG, "그룹 승인: ${extraction.storeName} (${group.members.size}건)")
                } else {
                    Log.d(TAG, "그룹 거절 (비결제): ${group.representative.body.take(40)}...")
                }

                totalGroupsProcessed++
            }

            // 고정 딜레이 제거: 429 발생 시 GeminiSmsExtractor 내부에서 재시도 + 백오프
        }

        Log.d(TAG, "=== 배치 처리 완료: LLM ${llmBatchCount}회 배치 호출 (${groups.size}개 그룹), 결제 ${results.size}건 발견 ===")
        return results
    }

    /**
     * Step 1: 기존 벡터 DB 패턴과 매칭 시도 (배치 임베딩 사용)
     *
     * 벡터 DB에 이미 학습된 패턴이 있으면 LLM 없이 바로 매칭.
     * 개별 embedContent 호출 대신 batchEmbedContents로 일괄 처리하여
     * API 호출 횟수를 대폭 줄입니다.
     */
    private suspend fun matchAgainstExistingPatterns(
        smsList: List<SmsData>
    ): Pair<List<Pair<SmsData, SmsAnalysisResult>>, List<SmsData>> {
        val existingPatterns = smsPatternDao.getAllPaymentPatterns()
        if (existingPatterns.isEmpty()) {
            return emptyList<Pair<SmsData, SmsAnalysisResult>>() to smsList
        }

        val matched = mutableListOf<Pair<SmsData, SmsAnalysisResult>>()
        val unmatched = mutableListOf<SmsData>()

        // 전체 SMS를 템플릿화
        val templates = smsList.map { embeddingService.templateizeSms(it.body) }

        // 배치 단위로 임베딩 생성 (EMBEDDING_BATCH_SIZE = 100건씩)
        val batches = smsList.indices.chunked(EMBEDDING_BATCH_SIZE)

        for ((batchIdx, batchIndices) in batches.withIndex()) {
            val batchTemplates = batchIndices.map { templates[it] }
            val embeddings = embeddingService.generateEmbeddings(batchTemplates)

            for ((i, idx) in batchIndices.withIndex()) {
                val embedding = embeddings.getOrNull(i)
                if (embedding != null) {
                    val bestMatch = VectorSearchEngine.findBestMatch(
                        queryVector = embedding,
                        patterns = existingPatterns,
                        minSimilarity = SmsPatternSimilarityPolicy.profile.confirm
                    )

                    if (bestMatch != null) {
                        val cached = bestMatch.pattern
                        val amount = SmsParser.extractAmount(smsList[idx].body) ?: cached.parsedAmount
                        val dateTime = SmsParser.extractDateTime(smsList[idx].body, smsList[idx].date)

                        val analysis = SmsAnalysisResult(
                            amount = amount,
                            storeName = cached.parsedStoreName,
                            category = cached.parsedCategory,
                            dateTime = dateTime,
                            cardName = cached.parsedCardName
                        )

                        matched.add(smsList[idx] to analysis)
                        smsPatternDao.incrementMatchCount(bestMatch.pattern.id)
                        continue
                    }
                }

                unmatched.add(smsList[idx])
            }

            // 고정 딜레이 제거: 429 발생 시 SmsEmbeddingService 내부에서 백오프
        }

        return matched to unmatched
    }

    /**
     * Step 2: 배치 임베딩 생성
     *
     * SMS를 템플릿화한 뒤 배치 API로 한번에 임베딩 생성
     */
    private suspend fun generateBatchEmbeddings(
        smsList: List<SmsData>
    ): List<Triple<SmsData, String, List<Float>>> {
        val results = mutableListOf<Triple<SmsData, String, List<Float>>>()
        val templates = smsList.map { embeddingService.templateizeSms(it.body) }

        // 배치 단위로 임베딩 생성
        val batches = smsList.indices.chunked(EMBEDDING_BATCH_SIZE)

        for ((batchIdx, batch) in batches.withIndex()) {
            val batchTemplates = batch.map { templates[it] }
            val embeddings = embeddingService.generateEmbeddings(batchTemplates)

            for ((i, idx) in batch.withIndex()) {
                val embedding = embeddings.getOrNull(i)
                if (embedding != null) {
                    results.add(Triple(smsList[idx], templates[idx], embedding))
                }
            }

            // 고정 딜레이 제거: 429 발생 시 SmsEmbeddingService 내부에서 백오프
        }

        return results
    }

    /**
     * Step 3: 벡터 유사도 기반 그룹핑
     *
     * 유사한 SMS들을 하나의 그룹으로 묶어서 대표 1개만 LLM에 보냄.
     * 그리디 클러스터링: 첫 SMS를 그룹 중심으로, 유사도 ≥ 0.95면 같은 그룹.
     */
    private suspend fun groupBySimilarity(
        embeddedSms: List<Triple<SmsData, String, List<Float>>>
    ): List<VectorGroup> {
        val groups = mutableListOf<VectorGroup>()
        val assigned = BooleanArray(embeddedSms.size)

        for (i in embeddedSms.indices) {
            if (assigned[i]) continue

            // 50건마다 progress 업데이트 + yield로 다른 코루틴에 실행 기회 제공
            if (i % 50 == 0) {
                yield()
            }

            val (smsI, templateI, embeddingI) = embeddedSms[i]

            val group = VectorGroup(
                representative = smsI,
                representativeTemplate = templateI,
                representativeEmbedding = embeddingI,
                members = mutableListOf(smsI)
            )

            // 이 SMS와 유사한 나머지를 찾아 그룹에 추가
            for (j in (i + 1) until embeddedSms.size) {
                if (assigned[j]) continue

                val (smsJ, _, embeddingJ) = embeddedSms[j]
                val similarity = VectorSearchEngine.cosineSimilarity(embeddingI, embeddingJ)

                if (SmsPatternSimilarityPolicy.shouldGroup(similarity)) {
                    group.members.add(smsJ)
                    assigned[j] = true
                }
            }

            assigned[i] = true
            groups.add(group)
        }

        // 그룹 크기가 큰 순으로 정렬 (중요한 패턴 우선 처리)
        return groups.sortedByDescending { it.members.size }
    }

    /**
     * Step 4: 확인된 패턴을 벡터 DB에 등록
     */
    private suspend fun registerPattern(
        sms: SmsData,
        template: String,
        embedding: List<Float>,
        analysis: SmsAnalysisResult,
        source: String
    ) {
        try {
            val pattern = SmsPatternEntity(
                smsTemplate = template,
                senderAddress = sms.address,
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
        } catch (e: Exception) {
            Log.e(TAG, "패턴 등록 실패: ${e.message}")
        }
    }
}
