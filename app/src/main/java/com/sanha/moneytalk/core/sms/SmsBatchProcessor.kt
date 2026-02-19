package com.sanha.moneytalk.core.sms

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
import kotlinx.coroutines.yield
import com.google.firebase.database.FirebaseDatabase
import java.util.concurrent.ConcurrentHashMap
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
    private val smsExtractor: GeminiSmsExtractor,
    private val database: FirebaseDatabase?
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

        /** 임베딩 배치 병렬 동시 실행 수 (API 키 5개 × 키당 2 = 10) */
        private const val EMBEDDING_CONCURRENCY = 10

        /** LLM 병렬 동시 실행 수 (API 키 5개 × 키당 1 = 5, LLM은 임베딩보다 무거움) */
        private const val LLM_CONCURRENCY = 5

        /** 정규식 생성 샘플 수 (그룹 대표 포함 최대 3건) */
        private const val REGEX_SAMPLE_SIZE = 3

        /** RTDB 표본 중복 판정 유사도 임계값 (0.99 = 사실상 동일 형식만 스킵) */
        private const val RTDB_DEDUP_SIMILARITY = 0.99f

        /** 정규식 생성 최소 샘플 수 (그룹 멤버 3건 미만이면 생성 스킵) */
        private const val REGEX_MIN_SAMPLES_FOR_GENERATION = 3

        /** 정규식 생성 실패 누적 시 쿨다운 기준 횟수 */
        private const val REGEX_FAILURE_THRESHOLD = 2

        /** 정규식 생성 실패 쿨다운 시간 (30분) */
        private const val REGEX_FAILURE_COOLDOWN_MS = 30L * 60L * 1000L

        /** 발신번호 내 소그룹 병합 임계값: 이 수 이하 멤버 그룹은 최대 그룹에 흡수 */
        private const val SMALL_GROUP_MERGE_THRESHOLD = 5

        /** 소그룹 병합 시 대표 벡터 최소 유사도: 이 값 미만이면 병합하지 않고 독립 그룹 유지 */
        private const val SMALL_GROUP_MERGE_MIN_SIMILARITY = 0.70f

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
            "결제내역", "명세서", "청구서", "이용대금", "결제예정", "결제일",
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
        // 1. 20자 미만/100자 초과 SMS는 결제 문자 가능성 낮음
        if (smsBody.length < 20) return true
        if (smsBody.length > SmsParser.MAX_SMS_LENGTH) return true

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

    /** 템플릿별 정규식 생성 실패 상태 */
    private data class RegexFailureState(
        val failCount: Int,
        val lastFailedAt: Long
    )

    private val regexFailureStates = ConcurrentHashMap<String, RegexFailureState>()

    /**
     * 대량 SMS 일괄 처리
     *
     * SMS를 벡터 그룹핑 → 대표 샘플 LLM 검증 방식으로 효율적으로 처리합니다.
     *
     * @param unclassifiedSms 처리할 SMS 목록
     * @param maxProcessCount 최대 처리 건수 (기본값: MAX_UNCLASSIFIED_TO_PROCESS)
     * @param onProgress 진행률 콜백 (step, current, total)
     * @return 결제로 확인된 SMS 목록 (SmsData + SmsAnalysisResult 쌍)
     */
    suspend fun processBatch(
        unclassifiedSms: List<SmsData>,
        maxProcessCount: Int = MAX_UNCLASSIFIED_TO_PROCESS,
        onProgress: ((step: String, current: Int, total: Int) -> Unit)? = null
    ): List<Pair<SmsData, SmsAnalysisResult>> {
        val results = mutableListOf<Pair<SmsData, SmsAnalysisResult>>()

        // 사전 필터링: 1) 키워드 기반 비결제 SMS 제외 2) 결제 최소 조건 미충족 SMS 제외
        val afterKeywordFilter = unclassifiedSms.filter { !isObviouslyNonPayment(it.body) }
        val filtered = afterKeywordFilter.filter { !lacksPaymentRequirements(it.body) }
        val targetSms = filtered.take(maxProcessCount)
        val keywordFiltered = unclassifiedSms.size - afterKeywordFilter.size
        val requirementFiltered = afterKeywordFilter.size - filtered.size
        Log.d(
            TAG,
            "=== 배치 처리 시작: ${targetSms.size}건 (전체 ${unclassifiedSms.size}건, 키워드 필터 ${keywordFiltered}건, 조건 미달 ${requirementFiltered}건 제외) ==="
        )

        if (targetSms.isEmpty()) return results

        onProgress?.invoke("기존 패턴 매칭", 0, targetSms.size)

        // ===== Step 1+2 통합: 배치 임베딩 → 기존 패턴 매칭 → 미매칭분 임베딩 재사용 =====
        // 기존: Step1에서 109건 임베딩 → 96건 매칭 → Step2에서 13건 다시 임베딩 (중복!)
        // 개선: Step1에서 생성한 임베딩을 미매칭 SMS에 대해 그대로 Step3 그룹핑에 전달
        val step1Start = System.currentTimeMillis()
        val (matchedByExisting, unmatchedWithEmbeddings) = matchAgainstExistingPatterns(targetSms)
        results.addAll(matchedByExisting)
        val step1Elapsed = System.currentTimeMillis() - step1Start
        Log.e("MT_DEBUG", "SmsBatchProcessor[Step1+2 벡터매칭] : ${matchedByExisting.size}건 성공, ${unmatchedWithEmbeddings.size}건 남음 (${step1Elapsed}ms, 임베딩 재사용)")

        if (unmatchedWithEmbeddings.isEmpty()) return results

        // Step2 스킵: Step1에서 이미 임베딩 생성됨 → 바로 그룹핑 진입
        val embeddedSms = unmatchedWithEmbeddings
        if (embeddedSms.isNotEmpty()) {
            val sampleEmb = embeddedSms.first()
            Log.e("MT_DEBUG", "SmsBatchProcessor[Step2 스킵] : dim=${sampleEmb.third.size}, template='${sampleEmb.second.take(60)}...' (Step1 임베딩 재사용)")
        }

        onProgress?.invoke("유사도 그룹핑", matchedByExisting.size, targetSms.size)

        // ===== Step 3: 벡터 유사도 기반 그룹핑 =====
        val step3Start = System.currentTimeMillis()
        val groups = groupByAddressThenSimilarity(embeddedSms)
        val step3Elapsed = System.currentTimeMillis() - step3Start
        val multiMemberGroups = groups.count { it.members.size > 1 }
        val maxGroupSize = groups.maxOfOrNull { it.members.size } ?: 0
        Log.e("MT_DEBUG", "SmsBatchProcessor[Step3 그룹핑] : ${embeddedSms.size}건 → ${groups.size}그룹 (다중: ${multiMemberGroups}, 최대: ${maxGroupSize}멤버) (${step3Elapsed}ms)")
        Log.e("MT_DEBUG", "SmsBatchProcessor[Step3 그룹상세] : ${buildGroupSizeSummary(groups)}")

        onProgress?.invoke("LLM 분석 + 정규식 생성", matchedByExisting.size, targetSms.size)

        // ===== Step 4: 그룹 대표들을 배치 LLM으로 일괄 분석 =====
        val llmBatches = groups.chunked(LLM_BATCH_SIZE)

        Log.e("MT_DEBUG", "SmsBatchProcessor[Step4 LLM] : ${groups.size}그룹 → ${llmBatches.size}배치 (크기: $LLM_BATCH_SIZE, 병렬: $LLM_CONCURRENCY)")

        // Step 4-A: 모든 배치의 LLM 추출을 병렬 실행
        val llmBatchSemaphore = Semaphore(LLM_CONCURRENCY)
        val batchExtractions = coroutineScope {
            llmBatches.map { groupBatch ->
                async {
                    llmBatchSemaphore.withPermit {
                        val smsTexts = groupBatch.map { it.representative.body }
                        val smsTimestamps = groupBatch.map { it.representative.date }
                        try {
                            smsExtractor.extractFromSmsBatch(smsTexts, smsTimestamps)
                        } catch (e: Exception) {
                            Log.e(TAG, "LLM 배치 추출 실패: ${e.message}")
                            List(smsTexts.size) { null }
                        }
                    }
                }
            }.awaitAll()
        }

        // Step 4-B: 결제 그룹 필터링 + 정규식 생성 + 파싱 (배치별 순차, 정규식은 병렬)
        for ((batchIdx, groupBatch) in llmBatches.withIndex()) {
            val extractions = batchExtractions[batchIdx]

            // 결제로 확인된 그룹만 필터링
            val paymentGroups = mutableListOf<Pair<Int, GeminiSmsExtractor.LlmExtractionResult>>()
            for ((groupIdx, group) in groupBatch.withIndex()) {
                val extraction = extractions.getOrNull(groupIdx)
                if (extraction != null && extraction.isPayment && extraction.amount > 0) {
                    paymentGroups.add(groupIdx to extraction)
                } else {
                    Log.d(TAG, "그룹 거절 (비결제): ${group.representative.body.take(40)}...")
                }
            }

            // 결제 그룹 대표들의 정규식을 병렬 생성
            val regexSemaphore = Semaphore(LLM_CONCURRENCY)
            val regexResults = if (paymentGroups.isNotEmpty()) {
                coroutineScope {
                    paymentGroups.map { (groupIdx, _) ->
                        val group = groupBatch[groupIdx]
                        async {
                            regexSemaphore.withPermit {
                                val templateKey = group.representativeTemplate
                                if (shouldSkipRegexGeneration(templateKey)) {
                                    Log.d(TAG, "정규식 생성 쿨다운 스킵: template='${templateKey.take(40)}...'")
                                    return@withPermit null
                                }
                                if (group.members.size < REGEX_MIN_SAMPLES_FOR_GENERATION) {
                                    Log.d(
                                        TAG,
                                        "정규식 생성 스킵: 샘플 부족 (${group.members.size}/$REGEX_MIN_SAMPLES_FOR_GENERATION), 대표='${group.representative.body.take(40)}...'"
                                    )
                                    return@withPermit null
                                }

                                try {
                                    val sampleMembers = group.members.take(REGEX_SAMPLE_SIZE)
                                    val sampleBodies = sampleMembers.map { it.body }
                                    val sampleTimestamps = sampleMembers.map { it.date }

                                    val generated = smsExtractor.generateRegexForGroup(
                                        smsBodies = sampleBodies,
                                        smsTimestamps = sampleTimestamps
                                    )
                                    if (generated == null) {
                                        recordRegexFailure(templateKey)
                                    } else {
                                        clearRegexFailure(templateKey)
                                    }
                                    generated
                                } catch (e: Exception) {
                                    Log.w(TAG, "정규식 생성 실패 (대표 SMS): ${e.message}")
                                    recordRegexFailure(templateKey)
                                    null
                                }
                            }
                        }
                    }.awaitAll()
                }
            } else {
                emptyList()
            }

            // 각 결제 그룹에 결과 적용
            for ((paymentIdx, pair) in paymentGroups.withIndex()) {
                val (groupIdx, extraction) = pair
                val group = groupBatch[groupIdx]

                val rawDateTime = if (extraction.dateTime.isNotBlank()) {
                    extraction.dateTime
                } else {
                    SmsParser.extractDateTime(
                        group.representative.body,
                        group.representative.date
                    )
                }
                val dateTime =
                    DateUtils.validateExtractedDateTime(rawDateTime, group.representative.date)

                val fallbackRepresentativeAnalysis = SmsAnalysisResult(
                    amount = extraction.amount,
                    storeName = extraction.storeName,
                    category = extraction.category,
                    dateTime = dateTime,
                    cardName = extraction.cardName
                )

                val llmRegexResult = regexResults.getOrNull(paymentIdx)
                val hasLlmRegex = llmRegexResult != null &&
                    llmRegexResult.isPayment &&
                    llmRegexResult.amountRegex.isNotBlank() &&
                    llmRegexResult.storeRegex.isNotBlank()

                val templateFallbackRegex =
                    if (!hasLlmRegex) buildTemplateFallbackRegex(group.representativeTemplate) else null

                val regexResult = llmRegexResult ?: templateFallbackRegex
                val hasGeneratedRegex = regexResult != null &&
                    regexResult.isPayment &&
                    regexResult.amountRegex.isNotBlank() &&
                    regexResult.storeRegex.isNotBlank()

                val parseSource = when {
                    hasLlmRegex -> "llm_regex"
                    hasGeneratedRegex -> "template_regex"
                    else -> "llm"
                }

                Log.e("MT_DEBUG", "SmsBatchProcessor[정규식] : store='${extraction.storeName}' | hasRegex=$hasGeneratedRegex | " +
                    "amountRegex='${regexResult?.amountRegex?.take(50).orEmpty()}' | " +
                    "storeRegex='${regexResult?.storeRegex?.take(50).orEmpty()}' | " +
                    "cardRegex='${regexResult?.cardRegex?.take(50).orEmpty()}' | source=$parseSource")

                val representativeAnalysis = if (hasGeneratedRegex) {
                    GeneratedSmsRegexParser.parseWithRegex(
                        smsBody = group.representative.body,
                        smsTimestamp = group.representative.date,
                        amountRegex = regexResult?.amountRegex.orEmpty(),
                        storeRegex = regexResult?.storeRegex.orEmpty(),
                        cardRegex = regexResult?.cardRegex.orEmpty(),
                        fallbackAmount = extraction.amount,
                        fallbackStoreName = extraction.storeName,
                        fallbackCardName = extraction.cardName,
                        fallbackCategory = extraction.category
                    ) ?: fallbackRepresentativeAnalysis
                } else {
                    fallbackRepresentativeAnalysis
                }

                Log.e("MT_DEBUG", "SmsBatchProcessor[파싱결과] : store='${representativeAnalysis.storeName}' | " +
                    "amount=${representativeAnalysis.amount} | card='${representativeAnalysis.cardName}' | " +
                    "category='${representativeAnalysis.category}' | source=${if (hasGeneratedRegex) parseSource else "llm_fallback"} | " +
                    "members=${group.members.size}")

                registerPattern(
                    group.representative,
                    group.representativeTemplate,
                    group.representativeEmbedding,
                    representativeAnalysis,
                    source = parseSource,
                    amountRegex = if (hasGeneratedRegex) regexResult?.amountRegex.orEmpty() else "",
                    storeRegex = if (hasGeneratedRegex) regexResult?.storeRegex.orEmpty() else "",
                    cardRegex = if (hasGeneratedRegex) regexResult?.cardRegex.orEmpty() else ""
                )

                results.add(group.representative to representativeAnalysis)

                for (member in group.members) {
                    if (member.id == group.representative.id) continue

                    val regexParsedMember = if (hasGeneratedRegex) {
                        GeneratedSmsRegexParser.parseWithRegex(
                            smsBody = member.body,
                            smsTimestamp = member.date,
                            amountRegex = regexResult?.amountRegex.orEmpty(),
                            storeRegex = regexResult?.storeRegex.orEmpty(),
                            cardRegex = regexResult?.cardRegex.orEmpty(),
                            fallbackAmount = representativeAnalysis.amount,
                            fallbackStoreName = representativeAnalysis.storeName,
                            fallbackCardName = representativeAnalysis.cardName,
                            fallbackCategory = representativeAnalysis.category
                        )
                    } else {
                        null
                    }

                    val memberAnalysis = regexParsedMember ?: run {
                        val memberAmount =
                            SmsParser.extractAmount(member.body) ?: representativeAnalysis.amount
                        val memberDateTime = SmsParser.extractDateTime(member.body, member.date)
                        // 발신번호 그룹에서는 멤버마다 가게명이 다르므로 개별 추출
                        val memberStoreName = SmsParser.extractStoreName(member.body)
                            .takeIf { it != "결제" && it.length >= 2 }
                            ?: representativeAnalysis.storeName
                        val memberCategory = if (memberStoreName != representativeAnalysis.storeName) {
                            SmsParser.inferCategory(memberStoreName, member.body)
                        } else {
                            representativeAnalysis.category
                        }
                        SmsAnalysisResult(
                            amount = memberAmount,
                            storeName = memberStoreName,
                            category = memberCategory,
                            dateTime = memberDateTime,
                            cardName = representativeAnalysis.cardName
                        )
                    }

                    results.add(member to memberAnalysis)
                }

                Log.d(TAG, "그룹 승인: ${extraction.storeName} (${group.members.size}건)")
            }
        }

        Log.e("MT_DEBUG", "SmsBatchProcessor[완료] : 결제 ${results.size}건 / LLM ${llmBatches.size}배치(병렬) / 벡터매칭 ${matchedByExisting.size}건 / 그룹 ${groups.size}개")
        return results
    }

    /**
     * Step 1+2 통합: 기존 벡터 DB 패턴과 매칭 + 미매칭 SMS 임베딩 재사용
     *
     * 기존 문제: Step1에서 전체 SMS 임베딩 생성 → Step2에서 미매칭분 다시 임베딩 (중복 API 호출)
     * 개선: Step1에서 생성한 임베딩을 미매칭 SMS에 대해 (SmsData, template, embedding) 형태로 반환하여
     * Step3 그룹핑에서 바로 사용. Step2 임베딩 호출 완전 제거.
     *
     * @return Pair(매칭 결과, 미매칭 SMS + 템플릿 + 임베딩)
     */
    private suspend fun matchAgainstExistingPatterns(
        smsList: List<SmsData>
    ): Pair<List<Pair<SmsData, SmsAnalysisResult>>, List<Triple<SmsData, String, List<Float>>>> {
        val existingPatterns = smsPatternDao.getAllPaymentPatterns()
        if (existingPatterns.isEmpty()) {
            // 패턴 없음 → 전체 임베딩 후 미매칭으로 반환
            val allEmbedded = generateBatchEmbeddings(smsList)
            return emptyList<Pair<SmsData, SmsAnalysisResult>>() to allEmbedded
        }

        val matched = mutableListOf<Pair<SmsData, SmsAnalysisResult>>()
        val unmatchedWithEmbeddings = mutableListOf<Triple<SmsData, String, List<Float>>>()

        // 전체 SMS를 템플릿화
        val templates = smsList.map { embeddingService.templateizeSms(it.body) }

        // 배치 단위 병렬 임베딩 생성
        val indexBatches = smsList.indices.chunked(EMBEDDING_BATCH_SIZE)
        val semaphore = Semaphore(EMBEDDING_CONCURRENCY)
        val batchEmbeddings = coroutineScope {
            indexBatches.map { batchIndices ->
                async {
                    semaphore.withPermit {
                        val batchTemplates = batchIndices.map { templates[it] }
                        embeddingService.generateEmbeddings(batchTemplates)
                    }
                }
            }.awaitAll()
        }

        // 임베딩 결과로 벡터 매칭 (순차) — 미매칭 시 임베딩도 함께 보존
        for ((batchIdx, batchIndices) in indexBatches.withIndex()) {
            val embeddings = batchEmbeddings[batchIdx]

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
                        val analysis = GeneratedSmsRegexParser.parseWithPattern(
                            smsBody = smsList[idx].body,
                            smsTimestamp = smsList[idx].date,
                            pattern = cached
                        ) ?: run {
                            val smsBody = smsList[idx].body
                            val amount = SmsParser.extractAmount(smsBody) ?: cached.parsedAmount
                            if (amount <= 0) {
                                null
                            } else {
                                val extractedStore = SmsParser.extractStoreName(smsBody)
                                    .takeIf { it != "결제" && it.length >= 2 }
                                val storeName = extractedStore ?: cached.parsedStoreName.ifBlank { "결제" }
                                val category = when {
                                    extractedStore != null -> SmsParser.inferCategory(storeName, smsBody)
                                    cached.parsedCategory.isNotBlank() -> cached.parsedCategory
                                    else -> SmsParser.inferCategory(storeName, smsBody)
                                }
                                val cardFromSms = SmsParser.extractCardName(smsBody)
                                val cardName = if (cardFromSms != "기타") {
                                    cardFromSms
                                } else {
                                    cached.parsedCardName.ifBlank { "기타" }
                                }
                                val dateTime = SmsParser.extractDateTime(smsBody, smsList[idx].date)
                                SmsAnalysisResult(
                                    amount = amount,
                                    storeName = storeName,
                                    category = category,
                                    dateTime = dateTime,
                                    cardName = cardName
                                )
                            }
                        }

                        if (analysis != null && matched.size < 5) {
                            Log.e("MT_DEBUG", "SmsBatchProcessor[기존패턴] : sim=${String.format("%.3f", bestMatch.similarity)} | " +
                                "store='${analysis.storeName}' | amount=${analysis.amount} | " +
                                "regexParsed=${cached.amountRegex.isNotBlank()} | sms='${smsList[idx].body.take(40)}...'")
                        }

                        if (analysis != null) {
                            matched.add(smsList[idx] to analysis)
                            smsPatternDao.incrementMatchCount(bestMatch.pattern.id)
                            continue
                        }
                    }

                    // 미매칭 — 임베딩 보존하여 Step3에서 재사용
                    unmatchedWithEmbeddings.add(Triple(smsList[idx], templates[idx], embedding))
                } else {
                    // 임베딩 생성 실패 — 미매칭이지만 임베딩 없음 (Step3에서 제외)
                    Log.w(TAG, "임베딩 생성 실패로 매칭 불가: ${smsList[idx].body.take(40)}...")
                }
            }
        }

        return matched to unmatchedWithEmbeddings
    }

    /**
     * Step 2: 배치 임베딩 생성
     *
     * SMS를 템플릿화한 뒤 배치 API로 한번에 임베딩 생성
     */
    private suspend fun generateBatchEmbeddings(
        smsList: List<SmsData>
    ): List<Triple<SmsData, String, List<Float>>> {
        val templates = smsList.map { embeddingService.templateizeSms(it.body) }

        // 배치 단위 병렬 임베딩 생성
        val indexBatches = smsList.indices.chunked(EMBEDDING_BATCH_SIZE)
        val semaphore = Semaphore(EMBEDDING_CONCURRENCY)
        val batchEmbeddings = coroutineScope {
            indexBatches.map { batch ->
                async {
                    semaphore.withPermit {
                        val batchTemplates = batch.map { templates[it] }
                        embeddingService.generateEmbeddings(batchTemplates)
                    }
                }
            }.awaitAll()
        }

        // 결과 조립
        val results = mutableListOf<Triple<SmsData, String, List<Float>>>()
        for ((batchIdx, batch) in indexBatches.withIndex()) {
            val embeddings = batchEmbeddings[batchIdx]
            for ((i, idx) in batch.withIndex()) {
                val embedding = embeddings.getOrNull(i)
                if (embedding != null) {
                    results.add(Triple(smsList[idx], templates[idx], embedding))
                }
            }
        }

        return results
    }

    /**
     * Step 3: 발신번호 기반 2레벨 그룹핑 + 소그룹 병합
     *
     * Level 1: 발신번호(address)별 분류 (O(n), API 호출 없음)
     * Level 2: 같은 발신번호 내에서 템플릿 유사도 기반 서브그룹핑 (≥ 0.95)
     * Level 3: 같은 발신번호 내 소그룹(≤ SMALL_GROUP_MERGE_THRESHOLD) → 최대 그룹에 흡수
     *
     * 같은 발신번호 = 같은 카드사이므로 소그룹(해외승인, ATM출금 등 변형)을
     * 주 그룹에 병합해도 멤버별 개별 추출(SmsParser)로 정확도 유지.
     */
    private suspend fun groupByAddressThenSimilarity(
        embeddedSms: List<Triple<SmsData, String, List<Float>>>
    ): List<VectorGroup> {
        // +82/하이픈 등 표기 차이로 같은 발신자가 분리되지 않도록 정규화 키 사용
        val addressGroups = embeddedSms.groupBy { SmsFilter.normalizeAddress(it.first.address) }

        Log.e("MT_DEBUG", "SmsBatchProcessor[그룹핑] : ${embeddedSms.size}건 → " +
            "발신번호 ${addressGroups.size}개 (${addressGroups.map { "${it.key}:${it.value.size}건" }.joinToString(", ")})")

        val allGroups = mutableListOf<VectorGroup>()

        for ((address, smsInAddress) in addressGroups) {
            if (smsInAddress.size == 1) {
                val (sms, template, embedding) = smsInAddress[0]
                allGroups.add(VectorGroup(sms, template, embedding, mutableListOf(sms)))
                continue
            }

            val subGroups = groupBySimilarityInternal(smsInAddress)

            // 소그룹 병합: 같은 발신번호 내 소그룹을 최대 그룹에 흡수
            val merged = mergeSmallGroups(subGroups, address)
            allGroups.addAll(merged)
        }

        return allGroups.sortedByDescending { it.members.size }
    }

    /**
     * 같은 발신번호 내 소그룹(멤버 ≤ SMALL_GROUP_MERGE_THRESHOLD)을 최대 그룹에 병합
     *
     * 같은 카드사에서 오는 SMS 변형(해외승인, ATM출금, 출금취소 등)은
     * 형식은 다르지만 같은 발신번호이므로, 소그룹을 주 그룹에 흡수하여
     * 전체 그룹 수를 줄입니다. 멤버별 개별 파싱(SmsParser)이 있으므로 정확도 유지.
     *
     * 가드레일: 대표 벡터 간 유사도가 SMALL_GROUP_MERGE_MIN_SIMILARITY 미만이면
     * 병합하지 않고 독립 그룹으로 유지 (완전히 다른 형식의 오병합 방지).
     */
    private fun mergeSmallGroups(
        subGroups: List<VectorGroup>,
        address: String
    ): List<VectorGroup> {
        if (subGroups.size <= 1) return subGroups

        // 멤버 수 기준 내림차순 정렬 (이미 정렬되어 있지만 명시적으로)
        val sorted = subGroups.sortedByDescending { it.members.size }
        val largestGroup = sorted.first()

        val keptGroups = mutableListOf(largestGroup)
        var mergedCount = 0
        var skippedCount = 0

        for (i in 1 until sorted.size) {
            val smallGroup = sorted[i]
            if (smallGroup.members.size <= SMALL_GROUP_MERGE_THRESHOLD) {
                // 대표 벡터 간 유사도 확인: 너무 다른 형식이면 병합하지 않음
                val similarity = VectorSearchEngine.cosineSimilarity(
                    largestGroup.representativeEmbedding,
                    smallGroup.representativeEmbedding
                )
                if (similarity >= SMALL_GROUP_MERGE_MIN_SIMILARITY) {
                    largestGroup.members.addAll(smallGroup.members)
                    mergedCount += smallGroup.members.size
                } else {
                    // 유사도 미달: 독립 그룹으로 유지
                    keptGroups.add(smallGroup)
                    skippedCount += smallGroup.members.size
                }
            } else {
                keptGroups.add(smallGroup)
            }
        }

        if (mergedCount > 0 || skippedCount > 0) {
            Log.e("MT_DEBUG", "SmsBatchProcessor[소그룹병합] : " +
                "발신번호=$address | ${subGroups.size}서브그룹 → ${keptGroups.size}그룹 " +
                "(${mergedCount}건 병합, ${skippedCount}건 유사도미달 독립유지, 최대그룹=${largestGroup.members.size}건)")
        }

        return keptGroups
    }

    /**
     * 벡터 유사도 기반 그룹핑 (내부용)
     *
     * 유사한 SMS들을 하나의 그룹으로 묶어서 대표 1개만 LLM에 보냄.
     * 그리디 클러스터링: 첫 SMS를 그룹 중심으로, 유사도 ≥ 0.95면 같은 그룹.
     */
    private suspend fun groupBySimilarityInternal(
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
     * 동일 템플릿 정규식 생성 쿨다운 여부
     */
    private fun shouldSkipRegexGeneration(template: String): Boolean {
        val state = regexFailureStates[template] ?: return false
        if (state.failCount < REGEX_FAILURE_THRESHOLD) return false

        val elapsed = System.currentTimeMillis() - state.lastFailedAt
        return elapsed < REGEX_FAILURE_COOLDOWN_MS
    }

    private fun recordRegexFailure(template: String) {
        val now = System.currentTimeMillis()
        regexFailureStates.compute(template) { _, current ->
            if (current == null) {
                RegexFailureState(failCount = 1, lastFailedAt = now)
            } else if (now - current.lastFailedAt >= REGEX_FAILURE_COOLDOWN_MS) {
                RegexFailureState(failCount = 1, lastFailedAt = now)
            } else {
                current.copy(
                    failCount = current.failCount + 1,
                    lastFailedAt = now
                )
            }
        }
    }

    private fun clearRegexFailure(template: String) {
        regexFailureStates.remove(template)
    }

    /**
     * 그룹별 멤버 수 요약 문자열 생성
     *
     * 예: "G1:12건(1588XXXX), G2:8건(1577XXXX), ..."
     */
    private fun buildGroupSizeSummary(groups: List<VectorGroup>): String {
        if (groups.isEmpty()) return "그룹 없음"

        val maxLogGroups = 20
        val summary = groups.take(maxLogGroups).mapIndexed { index, group ->
            val normalizedAddress = SmsFilter.normalizeAddress(group.representative.address)
            "G${index + 1}:${group.members.size}건($normalizedAddress)"
        }.joinToString(", ")

        val hiddenCount = groups.size - maxLogGroups
        return if (hiddenCount > 0) {
            "$summary, ... 외 ${hiddenCount}개 그룹"
        } else {
            summary
        }
    }

    /**
     * LLM 정규식 생성 실패 시 템플릿 기반 최소 정규식 생성
     *
     * 그룹 템플릿에 {STORE}/{AMOUNT}가 있는 경우에만 제한적으로 생성합니다.
     * - 멀티라인: "{STORE}\n출금|승인|결제..." 형태 우선
     * - 단일라인: "{TIME} ... {AMOUNT}" 형태 보조
     */
    private fun buildTemplateFallbackRegex(
        template: String
    ): GeminiSmsExtractor.LlmRegexResult? {
        if (!template.contains("{AMOUNT}") || !template.contains("{STORE}")) return null

        val amountRegex = when {
            template.contains("{AMOUNT}원") -> "([\\d,]{2,})원"
            template.contains("\n{AMOUNT}\n") -> "\\n([\\d,]{2,})\\n"
            else -> "([\\d,]{2,})(?:원)?"
        }

        val storeRegex = when {
            template.contains("\n{STORE}\n") ->
                "\\n([^\\n]{2,30})\\n(?:체크카드출금|출금|승인|결제|사용|일시불|할부)"

            template.contains("{TIME}") ->
                "\\d{1,2}:\\d{2}\\s+(.+?)\\s+[\\d,]{2,}(?:원)?"

            else ->
                "([가-힣a-zA-Z0-9()'&._\\-\\s]{2,30})\\s+[\\d,]{2,}(?:원)?"
        }

        val cardRegex = if (template.contains("[")) {
            "\\[([^\\]]+)\\]"
        } else {
            ""
        }

        return GeminiSmsExtractor.LlmRegexResult(
            isPayment = true,
            amountRegex = amountRegex,
            storeRegex = storeRegex,
            cardRegex = cardRegex
        )
    }

    /**
     * Step 4: 확인된 패턴을 벡터 DB에 등록
     */
    private suspend fun registerPattern(
        sms: SmsData,
        template: String,
        embedding: List<Float>,
        analysis: SmsAnalysisResult,
        source: String,
        amountRegex: String = "",
        storeRegex: String = "",
        cardRegex: String = ""
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
                amountRegex = amountRegex,
                storeRegex = storeRegex,
                cardRegex = cardRegex,
                parseSource = source,
                confidence = when (source) {
                    "regex", "llm_regex" -> 1.0f
                    "template_regex" -> 0.85f  // 템플릿 폴백 정규식: LLM 정규식보다 낮은 신뢰도
                    else -> 0.8f               // "llm" 등 정규식 없는 LLM 결과
                }
            )
            smsPatternDao.insert(pattern)

            // RTDB 표본 수집: 검증된 정규식이 있는 소스만 정규식 포함 업로드
            // "llm" (정규식 없음)은 표본만, "regex"/"llm_regex"는 정규식 포함
            val hasVerifiedRegex = source in listOf("regex", "llm_regex")
            collectSampleToRtdb(
                smsBody = sms.body,
                template = template,
                embedding = embedding,
                cardName = analysis.cardName,
                senderAddress = sms.address,
                parseSource = source,
                amountRegex = if (hasVerifiedRegex) amountRegex else "",
                storeRegex = if (hasVerifiedRegex) storeRegex else "",
                cardRegex = if (hasVerifiedRegex) cardRegex else ""
            )
        } catch (e: Exception) {
            Log.e(TAG, "패턴 등록 실패: ${e.message}")
        }
    }

    // ===== RTDB SMS 표본 수집 =====

    /** 이미 전송한 표본의 임베딩 목록 (유사도 기반 중복 방지) */
    private val sentSampleEmbeddings = mutableListOf<List<Float>>()

    // SMS 표본을 Firebase RTDB에 수집
    // 카드사별 SMS 형식 표본 + 정규식 → 향후 정규식 주입에 활용
    // 중복 방지: 임베딩 유사도 >= 0.99이면 동일 형식으로 판단하여 스킵
    private fun collectSampleToRtdb(
        smsBody: String,
        template: String,
        embedding: List<Float>,
        cardName: String,
        senderAddress: String,
        parseSource: String,
        amountRegex: String,
        storeRegex: String,
        cardRegex: String
    ) {
        val db = database ?: return

        // 유사도 기반 중복 방지: 기존 전송 표본과 99% 이상 유사하면 스킵
        synchronized(sentSampleEmbeddings) {
            for (sentEmbedding in sentSampleEmbeddings) {
                val similarity = VectorSearchEngine.cosineSimilarity(embedding, sentEmbedding)
                if (similarity >= RTDB_DEDUP_SIMILARITY) return
            }
            sentSampleEmbeddings.add(embedding)
        }

        try {
            val maskedBody = maskSmsBody(smsBody)
            val sampleKey = "${senderAddress}_${template.hashCode().toUInt()}"

            val ref = db.getReference("sms_samples").child(sampleKey)
            val data = mutableMapOf<String, Any>(
                "maskedBody" to maskedBody,
                "cardName" to cardName,
                "senderAddress" to senderAddress,
                "parseSource" to parseSource,
                "count" to com.google.firebase.database.ServerValue.increment(1),
                "lastSeen" to com.google.firebase.database.ServerValue.TIMESTAMP
            )
            // 정규식이 있는 경우만 포함
            if (amountRegex.isNotBlank()) data["amountRegex"] = amountRegex
            if (storeRegex.isNotBlank()) data["storeRegex"] = storeRegex
            if (cardRegex.isNotBlank()) data["cardRegex"] = cardRegex

            ref.updateChildren(data)
                .addOnSuccessListener {
                    Log.d(TAG, "RTDB 표본 수집 성공: $cardName ($parseSource)")
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "RTDB 표본 수집 실패 (무시): ${e.message}")
                }
        } catch (e: Exception) {
            Log.w(TAG, "RTDB 표본 수집 예외 (무시): ${e.message}")
        }
    }

    // PII 마스킹: 숫자→*, 가게명→*, 구조 키워드/구분자는 보존
    private fun maskSmsBody(smsBody: String): String {
        var masked = smsBody

        // 1) 가게명 마스킹 (원본 상태에서 판별해야 정확)
        val lines = masked.split("\n")
        if (lines.size >= 4) {
            var storeReplaced = false
            val result = lines.map { line ->
                val trimmed = line.trim()
                if (!storeReplaced && embeddingService.isLikelyStoreName(trimmed)) {
                    storeReplaced = true
                    "*".repeat(trimmed.length.coerceAtMost(10))
                } else {
                    line
                }
            }
            masked = result.joinToString("\n")
        }
        // 2) 카드번호 마스킹: "1234*5678" → "****"
        masked = masked.replace(Regex("""\d+\*+\d+""")) { match ->
            "*".repeat(match.value.length)
        }
        // 3) 날짜 마스킹: "01/15" → "**/**" (금액보다 먼저 — 숫자 공유 방지)
        masked = masked.replace(Regex("""\d{1,2}([/.\-])\d{1,2}""")) { match ->
            match.value.replace(Regex("""\d"""), "*")
        }
        // 4) 시간 마스킹: "12:30" → "**:**"
        masked = masked.replace(Regex("""\d{1,2}:\d{2}""")) { match ->
            match.value.replace(Regex("""\d"""), "*")
        }
        // 5) 금액 마스킹: "4,500" → "*,***" (쉼표 구분자 보존)
        masked = masked.replace(Regex("""(\d{1,3})(,\d{3})*""")) { match ->
            match.value.replace(Regex("""\d"""), "*")
        }
        // 6) 남은 연속 숫자 마스킹 (잔여 숫자)
        masked = masked.replace(Regex("""\d+""")) { match ->
            "*".repeat(match.value.length)
        }

        return masked
    }
}
