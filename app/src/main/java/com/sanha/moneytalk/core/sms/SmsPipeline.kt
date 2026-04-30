package com.sanha.moneytalk.core.sms

import com.sanha.moneytalk.core.util.MoneyTalkLogger

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ===== SMS 통합 파이프라인 (메인 오케스트레이터) =====
 *
 * 역할: 전체 파이프라인 단계를 순서대로 호출하는 진입점.
 *       배치 동기화(HomeViewModel)와 실시간 수신(SmsReceiver → 증분 동기화) 모두
 *       이 하나의 process() 메소드를 사용.
 *
 * 파이프라인 단계:
 * ┌────────────────────────────────────────────────────────────┐
 * │ Step 2: SmsPreFilter.filter()                              │
 * │   입력 SMS에서 비결제 키워드/구조 필터링                      │
 * │   예: 인증번호, 광고, 배송 알림 → 제거                       │
 * │                                                            │
 * │ Step 3: batchEmbed()                                       │
 * │   필터 통과한 SMS를 100건씩 배치 임베딩                      │
 * │   SmsTemplateEngine.templateize() → 템플릿화                │
 * │   SmsTemplateEngine.batchEmbed() → 벡터 생성                │
 * │   Semaphore(10)으로 병렬 제한                               │
 * │   결과: List<EmbeddedSms> (원본 + 템플릿 + 768차원 벡터)     │
 * │                                                            │
 * │ Step 4: SmsPatternMatcher.matchPatterns()                  │
 * │   DB 기존 패턴과 벡터 유사도 비교                            │
 * │   매칭(≥0.92) → 패턴 regex로 파싱 → SmsParseResult          │
 * │   미매칭(<0.92) → Step 5로                                  │
 * │                                                            │
 * │ Step 5: SmsGroupClassifier.classifyUnmatched()             │
 * │   미매칭 SMS 그룹핑 → LLM → regex 생성 → 파싱               │
 * │   → SmsParseResult                                         │
 * │                                                            │
 * │ 결과: Step 4 결과 + Step 5 결과 합산 반환                    │
 * └────────────────────────────────────────────────────────────┘
 *
 * 호출 예시:
 *   // 배치 동기화
 *   val results = smsPipeline.process(paymentSmsInputs)
 *   results.forEach { expenseRepository.insert(it.toExpenseEntity()) }
 *
 *   // 실시간 수신 (1건)
 *   val results = smsPipeline.process(listOf(smsInput))
 *
 * 의존성:
 * - SmsPreFilter (sms) — 사전 필터링
 * - SmsTemplateEngine (sms) — 템플릿화 + 임베딩 API 호출
 * - SmsPatternMatcher (sms) — 벡터 매칭 + regex 파싱
 * - SmsGroupClassifier (sms) — 그룹핑 + LLM + regex 생성
 */
@Singleton
class SmsPipeline @Inject constructor(
    private val preFilter: SmsPreFilter,
    private val templateEngine: SmsTemplateEngine,
    private val patternMatcher: SmsPatternMatcher,
    private val groupClassifier: SmsGroupClassifier
) {

    companion object {
        private const val PERF_LOG_PREFIX = "[SyncPerf]"

        /** 배치 임베딩 크기 (batchEmbedContents API 최대 100) */
        private const val EMBEDDING_BATCH_SIZE = 100

        /** 임베딩 병렬 동시 실행 수 (API 키 5개 × 키당 2) */
        private const val EMBEDDING_CONCURRENCY = 10

        // ===== 파이프라인 단계 인덱스 (Stepper UI용) =====
        /** Step 0: 문자 분류 (PreFilter + IncomeFilter) */
        const val STEP_FILTER = 0
        /** Step 1: 패턴 분석 (임베딩) */
        const val STEP_EMBED = 1
        /** Step 2: 이전 내역 비교 (벡터 매칭) */
        const val STEP_MATCH = 2
        /** Step 3: AI 분석 (LLM) */
        const val STEP_LLM = 3
        /** Step 4: 저장 */
        const val STEP_SAVE = 4
        /** 총 단계 수 */
        const val TOTAL_STEPS = 5
    }

    /**
     * 파이프라인 결과 (결제 결과 + 엔진 통계)
     */
    data class PipelineResult(
        val results: List<SmsParseResult>,
        val vectorMatchCount: Int,
        /** Step 4.5/5에서 LLM으로 최종 파싱된 SMS 건수 */
        val llmProcessCount: Int,
        /** Step 5에서 새로 등록했거나 학습 큐에 적재한 결제 패턴 수 */
        val newPatternCount: Int = 0,
        /** Step 4.5에서 regex 실패→LLM 복구된 건수 */
        val regexFailedRecoveredCount: Int = 0,
        /** 파이프라인 처리 중 최종 파싱 누락된 건수 */
        val droppedCount: Int = 0
    )

    /**
     * SMS 통합 파이프라인 실행
     *
     * 배치(수백~수천건)와 실시간(1건) 모두 이 메소드 사용.
     * 내부에서 Step 2→3→4→5 순서대로 처리.
     *
     * @param smsList 처리할 SMS 목록 (호출자가 SmsInput으로 변환하여 전달)
     * @param onProgress 진행률 콜백 (stepIndex, stepName, current, total)
     * @param skipPreFilter true면 사전 필터링 스킵 (SmsSyncCoordinator에서 이미 수행한 경우)
     * @return PipelineResult (결제 결과 + 벡터/LLM 카운트)
     */
    suspend fun process(
        smsList: List<SmsInput>,
        onProgress: ((stepIndex: Int, step: String, current: Int, total: Int) -> Unit)? = null,
        skipPreFilter: Boolean = false
    ): PipelineResult {
        val totalStart = System.currentTimeMillis()
        if (smsList.isEmpty()) return PipelineResult(emptyList(), 0, 0)
        MoneyTalkLogger.e("SmsPipeline 시작: 입력 ${smsList.size}건")


        // Step 2: 사전 필터링 — 비결제 SMS 제거
        // SmsSyncCoordinator 경유 시 이미 필터링 완료 → 스킵
        val filtered = if (skipPreFilter) {
            smsList
        } else {
            onProgress?.invoke(STEP_FILTER, "문자 분류 준비 중...", 0, smsList.size)
            val preFilterStart = System.currentTimeMillis()
            val result = preFilter.filter(smsList)
            MoneyTalkLogger.i(
                "$PERF_LOG_PREFIX pipeline.preFilter.done " +
                    "input=${smsList.size}, output=${result.size}, " +
                    "elapsedMs=${System.currentTimeMillis() - preFilterStart}"
            )
            result
        }
        MoneyTalkLogger.e("Step2 PreFilter: ${smsList.size}건 → ${filtered.size}건")

        if (filtered.isEmpty()) return PipelineResult(emptyList(), 0, 0)

        // Step 3: 전체 임베딩 — 100건씩 배치, Semaphore(10)로 병렬 제한
        onProgress?.invoke(STEP_EMBED, "문자 패턴 분석 중...", 0, filtered.size)
        val embedStart = System.currentTimeMillis()
        val embedded = batchEmbed(filtered)
        val embedElapsed = System.currentTimeMillis() - embedStart
        MoneyTalkLogger.e("Step3 Embedding: ${filtered.size}건 → ${embedded.size}건")
        MoneyTalkLogger.i(
            "$PERF_LOG_PREFIX pipeline.embedding.done " +
                "input=${filtered.size}, embedded=${embedded.size}, elapsedMs=$embedElapsed"
        )

        if (embedded.isEmpty()) return PipelineResult(emptyList(), 0, 0)

        // Step 4: 벡터 매칭 — DB 기존 패턴과 유사도 비교
        onProgress?.invoke(STEP_MATCH, "이전 내역과 비교 중...", 0, embedded.size)
        val matchStart = System.currentTimeMillis()
        val matchResult = patternMatcher.matchPatterns(embedded)
        val matchElapsed = System.currentTimeMillis() - matchStart
        MoneyTalkLogger.e("Step4 VectorMatch: 매칭 ${matchResult.matched.size}건, regex실패 ${matchResult.regexFailed.size}건, 미매칭 ${matchResult.unmatched.size}건")
        MoneyTalkLogger.i(
            "$PERF_LOG_PREFIX pipeline.vectorMatch.done " +
                "input=${embedded.size}, matched=${matchResult.matched.size}, " +
                "regexFailed=${matchResult.regexFailed.size}, unmatched=${matchResult.unmatched.size}, " +
                "elapsedMs=$matchElapsed"
        )

        // Step 4.5: regex 실패건 배치 LLM (그룹핑+regex생성 스킵)
        var regexRecoveryElapsed = 0L
        val (regexFailedResults, regexFailedFallback) = if (matchResult.regexFailed.isNotEmpty()) {
            onProgress?.invoke(STEP_LLM, "regex 실패건 복구 중...", matchResult.matched.size, embedded.size)
            val regexRecoveryStart = System.currentTimeMillis()
            groupClassifier.batchExtractRegexFailed(matchResult.regexFailed).also {
                regexRecoveryElapsed = System.currentTimeMillis() - regexRecoveryStart
            }
        } else {
            Pair(emptyList(), emptyList())
        }
        if (matchResult.regexFailed.isNotEmpty()) {
            MoneyTalkLogger.e("Step4.5 RegexFailedRecovery: ${matchResult.regexFailed.size}건 → 복구 ${regexFailedResults.size}건, Step5 fallback ${regexFailedFallback.size}건")
            MoneyTalkLogger.i(
                "$PERF_LOG_PREFIX pipeline.regexRecovery.done " +
                    "input=${matchResult.regexFailed.size}, recovered=${regexFailedResults.size}, " +
                    "fallback=${regexFailedFallback.size}, elapsedMs=$regexRecoveryElapsed"
            )
        }

        // Step 5-A: 순수 미매칭건은 기존 정책(그룹핑 + regex 생성 가능) 유지
        var unmatchedElapsed = 0L
        val unmatchedClassification = if (matchResult.unmatched.isNotEmpty()) {
            val preview = matchResult.unmatched.take(5)
            preview.forEachIndexed { index, embeddedSms ->
                MoneyTalkLogger.i(
                    "[Step5-A input] #${index + 1} id=${embeddedSms.input.id}, addr=${embeddedSms.input.address}, body=${embeddedSms.input.body.replace("\n", "↵").take(120)}"
                )
            }
            if (matchResult.unmatched.size > preview.size) {
                MoneyTalkLogger.i(
                    "[Step5-A input] ... ${matchResult.unmatched.size - preview.size}건 추가"
                )
            }
            onProgress?.invoke(STEP_LLM, "AI가 결제 내역 분석 중...", matchResult.matched.size + regexFailedResults.size, embedded.size)
            val unmatchedStart = System.currentTimeMillis()
            groupClassifier.classifyUnmatched(matchResult.unmatched) { step, current, total ->
                onProgress?.invoke(STEP_LLM, step, current, total)
            }.also {
                unmatchedElapsed = System.currentTimeMillis() - unmatchedStart
            }
        } else {
            SmsGroupClassifier.ClassificationResult(emptyList(), 0)
        }
        if (matchResult.unmatched.isNotEmpty()) {
            val unmatchedParsedIds = unmatchedClassification.results.map { it.input.id }.toHashSet()
            val unresolvedFromUnmatched = matchResult.unmatched
                .filter { it.input.id !in unmatchedParsedIds }
            MoneyTalkLogger.i(
                "Step5-A 결과: 입력 ${matchResult.unmatched.size}건 → 결제확정 ${unmatchedClassification.results.size}건, 미확정 ${unresolvedFromUnmatched.size}건"
            )
            MoneyTalkLogger.i(
                "$PERF_LOG_PREFIX pipeline.unmatchedLlm.done " +
                    "input=${matchResult.unmatched.size}, parsed=${unmatchedClassification.results.size}, " +
                    "learned=${unmatchedClassification.learnedPatternCount}, elapsedMs=$unmatchedElapsed"
            )
            unresolvedFromUnmatched.take(5).forEachIndexed { index, embeddedSms ->
                MoneyTalkLogger.w(
                    "[Step5-A unresolved] #${index + 1} id=${embeddedSms.input.id}, addr=${embeddedSms.input.address}, " +
                        "body=${embeddedSms.input.body.replace("\n", "↵").take(120)} (비결제 판정 또는 추출 실패 가능)"
                )
            }
        }

        // Step 5-B: Step4.5 fallback은 regex 재시도하지 않고 direct LLM만 수행
        var regexFallbackElapsed = 0L
        val regexFallbackClassification = if (regexFailedFallback.isNotEmpty()) {
            val preview = regexFailedFallback.take(5)
            preview.forEachIndexed { index, embeddedSms ->
                MoneyTalkLogger.i(
                    "[Step5-B input] #${index + 1} id=${embeddedSms.input.id}, addr=${embeddedSms.input.address}, body=${embeddedSms.input.body.replace("\n", "↵").take(120)}"
                )
            }
            onProgress?.invoke(STEP_LLM, "AI가 결제 내역 분석 중...", matchResult.matched.size + regexFailedResults.size + unmatchedClassification.results.size, embedded.size)
            val regexFallbackStart = System.currentTimeMillis()
            groupClassifier.classifyUnmatched(
                unmatchedList = regexFailedFallback,
                forceSkipRegexAll = true
            ) { step, current, total ->
                onProgress?.invoke(STEP_LLM, step, current, total)
            }.also {
                regexFallbackElapsed = System.currentTimeMillis() - regexFallbackStart
            }
        } else {
            SmsGroupClassifier.ClassificationResult(emptyList(), 0)
        }
        if (regexFailedFallback.isNotEmpty()) {
            val fallbackParsedIds = regexFallbackClassification.results.map { it.input.id }.toHashSet()
            val unresolvedFromRegexFallback = regexFailedFallback
                .filter { it.input.id !in fallbackParsedIds }
            MoneyTalkLogger.i(
                "Step5-B 결과: 입력 ${regexFailedFallback.size}건 → 결제확정 ${regexFallbackClassification.results.size}건, 미확정 ${unresolvedFromRegexFallback.size}건"
            )
            MoneyTalkLogger.i(
                "$PERF_LOG_PREFIX pipeline.regexFallbackLlm.done " +
                    "input=${regexFailedFallback.size}, parsed=${regexFallbackClassification.results.size}, " +
                    "learned=${regexFallbackClassification.learnedPatternCount}, elapsedMs=$regexFallbackElapsed"
            )
            unresolvedFromRegexFallback.take(5).forEachIndexed { index, embeddedSms ->
                MoneyTalkLogger.w(
                    "[Step5-B unresolved] #${index + 1} id=${embeddedSms.input.id}, addr=${embeddedSms.input.address}, " +
                        "body=${embeddedSms.input.body.replace("\n", "↵").take(120)} (비결제 판정 또는 추출 실패 가능)"
                )
            }
        }

        val llmResults = unmatchedClassification.results + regexFallbackClassification.results
        val newPatternCount =
            unmatchedClassification.learnedPatternCount + regexFallbackClassification.learnedPatternCount

        val total = matchResult.matched + regexFailedResults + llmResults
        val parsedIds = total.map { it.input.id }.toHashSet()
        val droppedCount = (embedded.size - parsedIds.size).coerceAtLeast(0)
        if (parsedIds.size < embedded.size) {
            val regexFailedFallbackIds = regexFailedFallback.map { it.input.id }.toHashSet()
            val unmatchedIds = matchResult.unmatched.map { it.input.id }.toHashSet()
            val dropped = embedded.filter { it.input.id !in parsedIds }

            MoneyTalkLogger.w(
                "[pipelineDrop] 누락 ${dropped.size}건 (input=${embedded.size}, parsed=${total.size})"
            )
            dropped.forEachIndexed { index, item ->
                val reason = when (item.input.id) {
                    in regexFailedFallbackIds -> "regex_fallback_drop"
                    in unmatchedIds -> "unmatched_drop"
                    else -> "unknown_drop"
                }
                MoneyTalkLogger.w(
                    "[pipelineDrop] #${index + 1} reason=$reason, id=${item.input.id}, " +
                        "addr=${item.input.address}, body=${item.input.body.replace("\n", "↵")}"
                )
            }
        }
        MoneyTalkLogger.e("SmsPipeline 완료: 결제 확정 ${total.size}건 (벡터${matchResult.matched.size} + regex복구${regexFailedResults.size} + LLM${llmResults.size})")
        MoneyTalkLogger.i(
            "$PERF_LOG_PREFIX pipeline.done " +
                "input=${smsList.size}, filtered=${filtered.size}, embedded=${embedded.size}, " +
                "matched=${matchResult.matched.size}, regexRecovered=${regexFailedResults.size}, " +
                "llm=${llmResults.size}, dropped=$droppedCount, elapsedMs=${System.currentTimeMillis() - totalStart}"
        )
        return PipelineResult(
            results = total,
            vectorMatchCount = matchResult.matched.size,
            llmProcessCount = llmResults.size,
            newPatternCount = newPatternCount,
            regexFailedRecoveredCount = regexFailedResults.size,
            droppedCount = droppedCount
        )
    }

    /**
     * Step 3: 배치 임베딩 생성
     *
     * 처리 흐름:
     * 1. 각 SMS를 SmsTemplateEngine.templateize()로 템플릿화
     *    예: "[KB]02/05 스타벅스 11,940원" → "[KB]{DATE} {STORE} {AMOUNT}원"
     *
     * 2. 100건씩 묶어서 SmsTemplateEngine.batchEmbed()로 배치 임베딩
     *    Semaphore(10)으로 병렬 제한 (API 키 5개 × 키당 2)
     *
     * 3. 임베딩 성공한 SMS만 EmbeddedSms로 변환하여 반환
     *    (임베딩 실패 = API 오류 → 해당 SMS는 처리에서 제외)
     *
     * @param smsList 필터 통과한 SMS 리스트
     * @return 임베딩 완료된 SMS 리스트
     */
    private suspend fun batchEmbed(smsList: List<SmsInput>): List<EmbeddedSms> {
        // 1. 각 SMS 템플릿화
        val templated = smsList.map { sms ->
            sms to templateEngine.templateize(sms.body)
        }

        // 2. 100건씩 배치로 나눠서 임베딩 API 호출
        val batches = templated.chunked(EMBEDDING_BATCH_SIZE)
        val semaphore = Semaphore(EMBEDDING_CONCURRENCY)
        val results = mutableListOf<EmbeddedSms>()

        coroutineScope {
            val batchResults = batches.map { batch ->
                async(Dispatchers.IO) {
                    semaphore.acquire()
                    try {
                        val templates = batch.map { it.second }
                        val embeddings = templateEngine.batchEmbed(templates)

                        // 임베딩 성공한 것만 EmbeddedSms로 변환
                        batch.mapIndexedNotNull { index, (sms, template) ->
                            val embedding = embeddings.getOrNull(index)
                            if (embedding != null) {
                                EmbeddedSms(
                                    input = sms,
                                    template = template,
                                    embedding = embedding
                                )
                            } else {
                                MoneyTalkLogger.w("임베딩 실패 (null): ${sms.body.take(30)}")
                                null
                            }
                        }
                    } finally {
                        semaphore.release()
                    }
                }
            }.awaitAll()

            results.addAll(batchResults.flatten())
        }

        return results
    }
}
