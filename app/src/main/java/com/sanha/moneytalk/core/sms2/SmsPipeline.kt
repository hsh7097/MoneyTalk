package com.sanha.moneytalk.core.sms2

import android.util.Log
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
 *       배치 동기화(HomeViewModel)와 실시간 수신(SmsProcessingService) 모두
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
 * │   결과: List<EmbeddedSms> (원본 + 템플릿 + 3072차원 벡터)    │
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
 * - SmsPreFilter (sms2) — 사전 필터링
 * - SmsTemplateEngine (sms2) — 템플릿화 + 임베딩 API 호출
 * - SmsPatternMatcher (sms2) — 벡터 매칭 + regex 파싱
 * - SmsGroupClassifier (sms2) — 그룹핑 + LLM + regex 생성
 */
@Singleton
class SmsPipeline @Inject constructor(
    private val preFilter: SmsPreFilter,
    private val templateEngine: SmsTemplateEngine,
    private val patternMatcher: SmsPatternMatcher,
    private val groupClassifier: SmsGroupClassifier
) {

    companion object {
        private const val TAG = "SmsPipeline"

        /** 배치 임베딩 크기 (batchEmbedContents API 최대 100) */
        private const val EMBEDDING_BATCH_SIZE = 100

        /** 임베딩 병렬 동시 실행 수 (API 키 5개 × 키당 2) */
        private const val EMBEDDING_CONCURRENCY = 10
    }

    /**
     * SMS 통합 파이프라인 실행
     *
     * 배치(수백~수천건)와 실시간(1건) 모두 이 메소드 사용.
     * 내부에서 Step 2→3→4→5 순서대로 처리.
     *
     * @param smsList 처리할 SMS 목록 (호출자가 SmsInput으로 변환하여 전달)
     * @param onProgress 진행률 콜백 (UI 표시용, 선택)
     * @return 결제로 확인되고 파싱 성공한 결과 리스트
     */
    suspend fun process(
        smsList: List<SmsInput>,
        onProgress: ((step: String, current: Int, total: Int) -> Unit)? = null
    ): List<SmsParseResult> {
        if (smsList.isEmpty()) return emptyList()

        Log.d(TAG, "=== 파이프라인 시작: ${smsList.size}건 ===")

        // Step 2: 사전 필터링 — 비결제 SMS 제거
        onProgress?.invoke("사전 필터링", 0, smsList.size)
        val filtered = preFilter.filter(smsList)
        Log.d(TAG, "필터링: ${smsList.size}건 → ${filtered.size}건 (${smsList.size - filtered.size}건 제외)")

        if (filtered.isEmpty()) return emptyList()

        // Step 3: 전체 임베딩 — 100건씩 배치, Semaphore(10)로 병렬 제한
        onProgress?.invoke("임베딩 생성", 0, filtered.size)
        val embedded = batchEmbed(filtered)
        Log.d(TAG, "임베딩: ${filtered.size}건 → ${embedded.size}건 성공")

        if (embedded.isEmpty()) return emptyList()

        // Step 4: 벡터 매칭 — DB 기존 패턴과 유사도 비교
        onProgress?.invoke("패턴 매칭", 0, embedded.size)
        val (vectorResults, unmatched) = patternMatcher.matchPatterns(embedded)
        Log.d(TAG, "벡터매칭: ${embedded.size}건 → 매칭 ${vectorResults.size}건, 미매칭 ${unmatched.size}건")

        // Step 5: 미매칭분 그룹핑 + LLM
        val llmResults = if (unmatched.isNotEmpty()) {
            onProgress?.invoke("LLM 분석", vectorResults.size, embedded.size)
            groupClassifier.classifyUnmatched(unmatched, onProgress)
        } else {
            emptyList()
        }
        Log.d(TAG, "LLM: ${unmatched.size}건 → ${llmResults.size}건 성공")

        val total = vectorResults + llmResults
        Log.d(TAG, "=== 파이프라인 완료: ${smsList.size}건 입력 → ${total.size}건 결제 확인 ===")
        return total
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
                                Log.w(TAG, "임베딩 실패 (null): ${sms.body.take(30)}")
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
