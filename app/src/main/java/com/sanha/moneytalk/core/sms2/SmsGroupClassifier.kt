package com.sanha.moneytalk.core.sms2

import android.util.Log
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.sanha.moneytalk.core.database.dao.SmsPatternDao
import com.sanha.moneytalk.core.database.entity.SmsPatternEntity
import com.sanha.moneytalk.core.model.SmsAnalysisResult
import com.sanha.moneytalk.core.sms.GeminiSmsExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.yield
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ===== SMS 그룹 분류기 (Step 5) =====
 *
 * 역할: Step 4에서 미매칭된 SMS를 벡터 유사도로 그룹핑한 뒤,
 *       그룹 대표만 LLM에 보내서 파싱 + regex 생성 + 패턴 DB 등록.
 *       등록된 regex로 그룹 전체 멤버를 파싱.
 *
 * 처리 순서:
 * ┌─────────────────────────────────────────────────────────┐
 * │ [5-1] 그룹핑                                            │
 * │   미매칭 EmbeddedSms 리스트                              │
 * │     → 1차: 발신번호(address) 기준 분류                    │
 * │     → 2차: 같은 발신번호 내에서 embedding 유사도 ≥0.95로   │
 * │            그리디 클러스터링                               │
 * │     → 3차: 소그룹(≤5멤버)을 최대 그룹에 흡수 (≥0.70)     │
 * │                                                         │
 * │ [5-2] LLM 배치 요청                                     │
 * │   그룹별 대표 SMS의 원본(input.body) + 샘플 N건을          │
 * │   GeminiSmsExtractor.extractFromSmsBatch()에 전달         │
 * │   → 결제 여부 + 금액/가게명/카드명/카테고리 추출           │
 * │   Semaphore(5)로 병렬 제한                               │
 * │                                                         │
 * │ [5-3] regex 생성 + 검증 + 패턴 DB 등록                   │
 * │   GeminiSmsExtractor.generateRegexForGroup() 호출         │
 * │   → amountRegex, storeRegex, cardRegex 생성              │
 * │   → SmsPatternEntity로 DB에 insert                       │
 * │   → RTDB에 마스킹된 샘플 수집 (비동기)                    │
 * │                                                         │
 * │ [5-4] 그룹 전체 멤버 파싱                                 │
 * │   등록된 regex로 그룹 각 멤버의 원본(input.body) 파싱       │
 * │   SmsPatternMatcher.parseWithRegex() 사용                 │
 * │   → SmsParseResult(tier=3) 생성                          │
 * └─────────────────────────────────────────────────────────┘
 *
 * 의존성:
 * - GeminiSmsExtractor (core/sms) — LLM 호출 (배치 추출 + regex 생성)
 *   ※ LLM 호출 자체는 독립적이므로 core/sms 참조 허용
 * - SmsPatternDao (core/database) — 패턴 DB 등록
 * - SmsPatternMatcher (sms2) — regex 파싱 + 코사인 유사도
 * - FirebaseDatabase — RTDB 표본 수집
 */
@Singleton
class SmsGroupClassifier @Inject constructor(
    private val smsPatternDao: SmsPatternDao,
    private val smsExtractor: GeminiSmsExtractor,
    private val patternMatcher: SmsPatternMatcher,
    private val templateEngine: SmsTemplateEngine,
    private val database: FirebaseDatabase?
) {

    companion object {
        private const val TAG = "SmsGroupClassifier"

        /** LLM 배치 크기 (한 번에 LLM에 보내는 그룹 대표 수) */
        private const val LLM_BATCH_SIZE = 20

        /** LLM 병렬 동시 실행 수 (API 키 5개 × 키당 1) */
        private const val LLM_CONCURRENCY = 5

        /** regex 생성 샘플 수 (그룹 대표 포함 최대 3건) */
        private const val REGEX_SAMPLE_SIZE = 3

        /** regex 생성 최소 멤버 수 (3건 미만이면 regex 생성 스킵) */
        private const val REGEX_MIN_SAMPLES = 3

        /** 그룹핑 유사도 임계값 (같은 발신번호 내 벡터 클러스터링) */
        private const val GROUPING_SIMILARITY = 0.95f

        /** 소그룹 병합 임계값: 이 수 이하 멤버 그룹은 최대 그룹에 흡수 */
        private const val SMALL_GROUP_MERGE_THRESHOLD = 5

        /** 소그룹 병합 최소 유사도: 이 값 미만이면 독립 그룹 유지 */
        private const val SMALL_GROUP_MERGE_MIN_SIMILARITY = 0.70f

        /** regex 생성 실패 쿨다운 기준 횟수 */
        private const val REGEX_FAILURE_THRESHOLD = 2

        /** regex 생성 실패 쿨다운 시간 (30분) */
        private const val REGEX_FAILURE_COOLDOWN_MS = 30L * 60L * 1000L

        /** RTDB 표본 중복 판정 유사도 (0.99 = 동일 형식만 스킵) */
        private const val RTDB_DEDUP_SIMILARITY = 0.99f

        /** 발신번호 정규화: 하이픈/공백/괄호 제거용 */
        private val ADDRESS_CLEAN_PATTERN = Regex("""[-\s().]""")
    }

    // ===== 내부 데이터 =====

    /**
     * 벡터 그룹 — 그룹핑 결과를 담는 내부 클래스
     *
     * @property representative 그룹 대표 SMS (첫 번째 멤버, LLM 요청 + regex 생성에 사용)
     * @property members 전체 멤버 리스트 (대표 포함, regex로 일괄 파싱 대상)
     */
    private data class VectorGroup(
        val representative: EmbeddedSms,
        val members: MutableList<EmbeddedSms>
    )

    /**
     * 발신번호 단위 그룹 — 같은 발신번호의 모든 VectorGroup을 묶는 단위
     *
     * 발신번호 기반 생태계 분석의 핵심 단위:
     * - 하나의 발신번호(카드사)가 보내는 모든 SMS 형식을 한눈에 파악
     * - 분포도 기반으로 메인 케이스(최대 서브그룹)와 예외 케이스 분리
     * - LLM에 전체 컨텍스트(모든 서브그룹 템플릿 + 분포 + 원본 샘플)를 전달
     *
     * @property address 정규화된 발신번호
     * @property subGroups 벡터 유사도로 클러스터링된 서브그룹 목록 (멤버 수 내림차순)
     * @property totalCount 전체 SMS 수
     */
    private data class SourceGroup(
        val address: String,
        val subGroups: List<VectorGroup>,
        val totalCount: Int
    ) {
        /** 메인 그룹 = 가장 큰 서브그룹 */
        val mainGroup: VectorGroup get() = subGroups.first()

        /** 예외 그룹 = 메인 그룹을 제외한 나머지 */
        val exceptionGroups: List<VectorGroup> get() = subGroups.drop(1)

        /**
         * LLM 프롬프트용 분포도 요약 문자열 생성
         *
         * 예외 케이스 LLM 호출 시, 메인 케이스의 컨텍스트를 함께 전달하여
         * LLM이 "이 번호의 주 형식은 결제이므로 이 변형도 결제일 가능성 높음"
         * 같은 판단을 할 수 있도록 함.
         */
        fun buildDistributionSummary(): String {
            val sb = StringBuilder()
            sb.appendLine("발신번호 $address 총 ${totalCount}건:")
            subGroups.forEachIndexed { idx, group ->
                val ratio = (group.members.size * 100 / totalCount)
                val label = if (idx == 0) " (메인)" else ""
                val sample = group.representative.input.body
                    .replace("\n", " ")
                    .take(60)
                sb.appendLine("  - 서브그룹${idx + 1}$label: ${group.members.size}건(${ratio}%) | 원본: $sample")
            }
            return sb.toString().trimEnd()
        }
    }

    /**
     * 메인 케이스 참조 정보 — 예외 케이스 LLM 호출 시 함께 전달
     *
     * 메인 케이스가 "결제"로 확인된 후, 예외 케이스 LLM 호출에
     * 이 정보를 포함하여 분류 정확도를 높임.
     *
     * @property cardName 메인 케이스에서 추출된 카드명 (예: "KB국민")
     * @property template 메인 케이스의 템플릿 (예: "[KB]{DATE} {STORE} {AMOUNT}원 승인")
     * @property sample 메인 케이스의 원본 SMS 샘플
     * @property isPayment 메인 케이스가 결제인지 여부
     */
    private data class MainCaseContext(
        val cardName: String,
        val template: String,
        val sample: String,
        val isPayment: Boolean
    )

    /** regex 생성 실패 쿨다운 추적 */
    private val regexFailureStates = ConcurrentHashMap<String, RegexFailureState>()
    private data class RegexFailureState(val failCount: Int, val lastFailedAt: Long)

    /** RTDB 중복 방지용 전송 기록 */
    private val sentSampleEmbeddings = mutableListOf<List<Float>>()

    // ===== 메인 진입점 =====

    /**
     * 미매칭 SMS를 그룹핑 → 발신번호 단위 LLM → regex 생성 → 파싱
     *
     * **핵심 전략: 발신번호 기반 생태계 분석**
     *
     * 기존: VectorGroup별 독립 LLM 호출 (컨텍스트 없음)
     * 변경: 같은 발신번호의 모든 서브그룹을 SourceGroup으로 묶어서
     *       메인 케이스 먼저 처리 → 메인 결과를 예외 케이스에 참조 전달
     *
     * 이점:
     * - 메인 케이스(95%+) 결과가 예외 케이스 LLM 판단의 힌트로 작용
     * - "이 번호는 KB카드이고 주 형식은 승인 SMS" → 해외승인/ATM도 결제 판정 정확도 향상
     * - LLM이 전체 그림을 보고 판단 → 오파싱/오분류 감소
     *
     * @param unmatchedList Step 4에서 미매칭된 EmbeddedSms 리스트
     * @param onProgress 진행률 콜백
     * @return 결제로 확인되고 파싱 성공한 결과 리스트
     */
    suspend fun classifyUnmatched(
        unmatchedList: List<EmbeddedSms>,
        onProgress: ((step: String, current: Int, total: Int) -> Unit)? = null
    ): List<SmsParseResult> {
        if (unmatchedList.isEmpty()) return emptyList()

        val results = mutableListOf<SmsParseResult>()

        // [5-1] 그룹핑 (Level 1~3: 발신번호 → 벡터 유사도 → 소그룹 병합)
        onProgress?.invoke("그룹핑", 0, unmatchedList.size)
        val groups = groupByAddressThenSimilarity(unmatchedList)
        Log.d(TAG, "그룹핑 완료: ${unmatchedList.size}건 → ${groups.size}그룹")

        // [5-1.5] 발신번호 단위로 재집계 → SourceGroup 생성
        val sourceGroups = buildSourceGroups(groups)
        Log.d(TAG, "발신번호 집계: ${groups.size}그룹 → ${sourceGroups.size}발신번호")

        // [5-2 ~ 5-4] 발신번호 단위로 처리
        // 각 SourceGroup 내에서: 메인 케이스 먼저 → 예외 케이스에 메인 컨텍스트 전달
        val semaphore = Semaphore(LLM_CONCURRENCY)
        val processedSources = AtomicInteger(0)

        coroutineScope {
            sourceGroups.chunked(LLM_BATCH_SIZE).forEach { batch ->
                val batchResults = batch.map { sourceGroup ->
                    async(Dispatchers.IO) {
                        semaphore.acquire()
                        try {
                            processSourceGroup(sourceGroup)
                        } finally {
                            semaphore.release()
                            val done = processedSources.incrementAndGet()
                            onProgress?.invoke("LLM 분석", done, sourceGroups.size)
                        }
                    }
                }.awaitAll()

                results.addAll(batchResults.flatten())
            }
        }

        Log.d(TAG, "=== 그룹 분류 완료: ${unmatchedList.size}건 → ${results.size}건 결제 확인 ===")
        return results
    }

    /**
     * VectorGroup 리스트를 발신번호 단위 SourceGroup으로 재집계
     *
     * groupByAddressThenSimilarity()의 결과는 VectorGroup 리스트(멤버 수 정렬)이지만,
     * 같은 발신번호의 서브그룹이 분산되어 있음.
     * 이를 발신번호 기준으로 다시 묶어서 SourceGroup을 생성.
     *
     * @param groups 벡터 유사도 기반 그룹 리스트
     * @return 발신번호 단위 그룹 리스트 (전체 SMS 수 내림차순)
     */
    private fun buildSourceGroups(groups: List<VectorGroup>): List<SourceGroup> {
        // 발신번호별 서브그룹 집계
        val addressMap = linkedMapOf<String, MutableList<VectorGroup>>()
        for (group in groups) {
            val address = normalizeAddress(group.representative.input.address)
            addressMap.getOrPut(address) { mutableListOf() }.add(group)
        }

        return addressMap.map { (address, subGroups) ->
            // 서브그룹을 멤버 수 내림차순 정렬 (메인 그룹이 first)
            val sorted = subGroups.sortedByDescending { it.members.size }
            val totalCount = sorted.sumOf { it.members.size }
            SourceGroup(
                address = address,
                subGroups = sorted,
                totalCount = totalCount
            )
        }.sortedByDescending { it.totalCount }
    }

    /**
     * 발신번호 단위 처리: 메인 케이스 먼저 → 예외 케이스에 메인 컨텍스트 전달
     *
     * 처리 순서:
     * 1. 메인 그룹(가장 큰 서브그룹) → processGroup()으로 LLM 호출
     * 2. 메인 결과를 MainCaseContext로 저장
     * 3. 예외 그룹들 → processGroup(mainContext)로 LLM 호출
     *    - LLM에 "이 번호의 메인 형식은 [KB] 승인 SMS (카드: KB국민)"를 힌트로 전달
     *
     * 서브그룹이 1개면 기존과 동일하게 처리.
     */
    private suspend fun processSourceGroup(sourceGroup: SourceGroup): List<SmsParseResult> {
        val results = mutableListOf<SmsParseResult>()

        if (sourceGroup.subGroups.size == 1) {
            // 서브그룹 1개 = 메인만 존재 → 기존 처리
            results.addAll(processGroup(sourceGroup.mainGroup))
            return results
        }

        Log.d(TAG, "발신번호 ${sourceGroup.address}: ${sourceGroup.subGroups.size}서브그룹, " +
            "메인 ${sourceGroup.mainGroup.members.size}건/${sourceGroup.totalCount}건")

        // Step 1: 메인 그룹 먼저 처리
        val mainResults = processGroup(sourceGroup.mainGroup)
        results.addAll(mainResults)

        // Step 2: 메인 결과로 컨텍스트 생성
        val mainContext = if (mainResults.isNotEmpty()) {
            MainCaseContext(
                cardName = mainResults.first().analysis.cardName,
                template = sourceGroup.mainGroup.representative.template,
                sample = sourceGroup.mainGroup.representative.input.body
                    .replace("\n", " ")
                    .take(80),
                isPayment = true
            )
        } else {
            // 메인이 비결제 → 예외 그룹도 비결제 가능성 높지만 독립 판단
            MainCaseContext(
                cardName = "",
                template = sourceGroup.mainGroup.representative.template,
                sample = sourceGroup.mainGroup.representative.input.body
                    .replace("\n", " ")
                    .take(80),
                isPayment = false
            )
        }

        // Step 3: 예외 그룹 처리 (메인 컨텍스트 전달)
        for (exceptionGroup in sourceGroup.exceptionGroups) {
            val exResults = processGroup(
                group = exceptionGroup,
                mainContext = mainContext,
                distributionSummary = sourceGroup.buildDistributionSummary()
            )
            results.addAll(exResults)
        }

        return results
    }

    /**
     * 단일 그룹 처리: LLM 배치 추출 → regex 생성 → 패턴 등록 → 멤버 파싱
     *
     * 처리 흐름:
     * 1. 그룹 대표 SMS 원본(input.body)을 LLM에 전달하여 결제 정보 추출
     *    - mainContext가 있으면 LLM 프롬프트에 메인 케이스 참조 정보 추가
     * 2. 결제 확인 시: regex 생성 (멤버 ≥3건) 또는 템플릿 폴백 regex
     * 3. 패턴 DB에 등록 (향후 같은 형식 SMS는 Step 4에서 매칭)
     * 4. 등록된 regex로 그룹 전체 멤버의 원본 파싱 → SmsParseResult 생성
     *
     * @param group 처리할 벡터 그룹
     * @param mainContext 메인 케이스 참조 정보 (예외 그룹 처리 시 non-null)
     * @param distributionSummary 발신번호 전체 분포도 요약 (예외 그룹 처리 시 non-null)
     */
    private suspend fun processGroup(
        group: VectorGroup,
        mainContext: MainCaseContext? = null,
        distributionSummary: String? = null
    ): List<SmsParseResult> {
        val results = mutableListOf<SmsParseResult>()
        val representative = group.representative

        // --- [5-2] LLM 배치 추출 ---
        // 대표 SMS 원본 + (예외 케이스인 경우) 메인 케이스 참조 정보를 LLM에 전달
        val smsBodyForLlm = if (mainContext != null && distributionSummary != null) {
            buildContextualLlmInput(representative.input.body, mainContext, distributionSummary)
        } else {
            representative.input.body
        }

        val llmResults = smsExtractor.extractFromSmsBatch(
            smsMessages = listOf(smsBodyForLlm),
            smsTimestamps = listOf(representative.input.date)
        )
        val llmResult = llmResults.firstOrNull() ?: return emptyList()

        // 비결제 판정 → 비결제 패턴으로 DB 등록 후 종료
        if (!llmResult.isPayment) {
            registerNonPaymentPattern(representative)
            Log.d(TAG, "비결제 확정 (LLM): ${representative.input.body.take(30)}")
            return emptyList()
        }

        // LLM이 추출한 결제 정보
        val llmAnalysis = SmsAnalysisResult(
            amount = llmResult.amount,
            storeName = llmResult.storeName,
            category = llmResult.category,
            dateTime = llmResult.dateTime,
            cardName = llmResult.cardName
        )

        // --- [5-3] regex 생성 + 패턴 등록 ---
        var amountRegex = ""
        var storeRegex = ""
        var cardRegex = ""
        var parseSource = "llm"  // 기본: regex 없는 LLM 결과

        // 멤버가 충분하고 쿨다운이 아니면 regex 생성 시도
        if (group.members.size >= REGEX_MIN_SAMPLES &&
            !shouldSkipRegexGeneration(representative.template)
        ) {
            val samples = group.members
                .take(REGEX_SAMPLE_SIZE)
                .map { it.input.body }
            val timestamps = group.members
                .take(REGEX_SAMPLE_SIZE)
                .map { it.input.date }

            val regexResult = smsExtractor.generateRegexForGroup(
                smsBodies = samples,
                smsTimestamps = timestamps
            )

            if (regexResult != null && regexResult.isPayment &&
                regexResult.amountRegex.isNotBlank() && regexResult.storeRegex.isNotBlank()
            ) {
                amountRegex = regexResult.amountRegex
                storeRegex = regexResult.storeRegex
                cardRegex = regexResult.cardRegex
                parseSource = "llm_regex"
                clearRegexFailure(representative.template)
                Log.d(TAG, "regex 생성 성공: amount=${amountRegex.take(40)}, store=${storeRegex.take(40)}")
            } else {
                recordRegexFailure(representative.template)
                Log.w(TAG, "regex 생성 실패 → 템플릿 폴백 시도")

                // 템플릿 기반 폴백 regex 시도
                val fallback = buildTemplateFallbackRegex(representative.template)
                if (fallback != null) {
                    amountRegex = fallback.amountRegex
                    storeRegex = fallback.storeRegex
                    cardRegex = fallback.cardRegex
                    parseSource = "template_regex"
                }
            }
        } else if (group.members.size < REGEX_MIN_SAMPLES) {
            // 멤버 부족 시에도 템플릿 폴백 시도
            val fallback = buildTemplateFallbackRegex(representative.template)
            if (fallback != null) {
                amountRegex = fallback.amountRegex
                storeRegex = fallback.storeRegex
                cardRegex = fallback.cardRegex
                parseSource = "template_regex"
            }
        }

        // 결제 패턴 DB 등록
        registerPaymentPattern(
            embedded = representative,
            analysis = llmAnalysis,
            source = parseSource,
            amountRegex = amountRegex,
            storeRegex = storeRegex,
            cardRegex = cardRegex
        )

        // --- [5-4] 그룹 전체 멤버 파싱 ---
        for (member in group.members) {
            val parsed = if (amountRegex.isNotBlank() && storeRegex.isNotBlank()) {
                // regex가 있으면 멤버 원본(body)에 적용
                patternMatcher.parseWithRegex(
                    smsBody = member.input.body,
                    smsTimestamp = member.input.date,
                    amountRegex = amountRegex,
                    storeRegex = storeRegex,
                    cardRegex = cardRegex,
                    fallbackAmount = llmAnalysis.amount,
                    fallbackStoreName = llmAnalysis.storeName,
                    fallbackCardName = llmAnalysis.cardName,
                    fallbackCategory = llmAnalysis.category
                )
            } else {
                // regex 없으면 LLM 추출값을 그대로 사용 (대표와 동일)
                llmAnalysis
            }

            if (parsed != null && parsed.amount > 0) {
                results.add(
                    SmsParseResult(
                        input = member.input,
                        analysis = parsed,
                        tier = 3,  // LLM 경로
                        confidence = 1.0f
                    )
                )
            }
        }

        return results
    }

    /**
     * 예외 케이스 LLM 호출을 위한 컨텍스트 포함 입력 구성
     *
     * 원본 SMS 본문 앞에 메인 케이스 참조 정보와 분포도 요약을 추가.
     * LLM이 "이 SMS가 어떤 맥락에서 온 것인지"를 이해하고 판단할 수 있도록 함.
     *
     * 예:
     * "[참조 정보]
     *  이 SMS는 아래 발신번호의 예외 케이스입니다.
     *  발신번호 15881688 총 85건:
     *    - 서브그룹1 (메인): 80건(94%) | 원본: [KB]02/05 14:30 스타벅스 11,940원 승인
     *    - 서브그룹2: 3건(4%) | 원본: [KB]해외승인 02/05 STARBUCKS USD12.00
     *  메인 케이스 카드: KB국민
     *
     *  [분석 대상 SMS]
     *  [KB]해외승인 02/05 STARBUCKS USD12.00"
     */
    private fun buildContextualLlmInput(
        smsBody: String,
        mainContext: MainCaseContext,
        distributionSummary: String
    ): String {
        val contextInfo = StringBuilder()
        contextInfo.appendLine("[참조 정보]")
        contextInfo.appendLine("이 SMS는 아래 발신번호의 예외 케이스입니다.")
        contextInfo.appendLine(distributionSummary)
        if (mainContext.isPayment && mainContext.cardName.isNotBlank()) {
            contextInfo.appendLine("메인 케이스 카드: ${mainContext.cardName}")
        }
        contextInfo.appendLine()
        contextInfo.appendLine("[분석 대상 SMS]")
        contextInfo.append(smsBody)
        return contextInfo.toString()
    }

    // ===== [5-1] 그룹핑 =====

    /**
     * 미매칭 SMS를 발신번호 → 벡터 유사도 기반으로 그룹핑
     *
     * Level 1: 발신번호(address) 기준 분류 (O(n), API 없음)
     *   - +82/하이픈 등 표기 차이 정규화
     *
     * Level 2: 같은 발신번호 내에서 임베딩 유사도 ≥ 0.95 그리디 클러스터링
     *   - 첫 SMS를 그룹 중심으로, 나머지를 순회하며 유사도 비교
     *
     * Level 3: 소그룹(≤5멤버) → 최대 그룹에 흡수 (유사도 ≥ 0.70)
     *   - 같은 카드사의 변형 SMS (해외승인, ATM출금 등)를 주 그룹에 병합
     *   - 유사도 0.70 미만이면 독립 유지 (완전히 다른 형식 보호)
     *
     * @return 그룹 리스트 (멤버 많은 순 정렬)
     */
    private suspend fun groupByAddressThenSimilarity(
        embeddedSms: List<EmbeddedSms>
    ): List<VectorGroup> {
        // Level 1: 발신번호 기준 분류
        val addressGroups = embeddedSms.groupBy { normalizeAddress(it.input.address) }

        Log.d(TAG, "발신번호 분류: ${embeddedSms.size}건 → ${addressGroups.size}개 번호")

        val allGroups = mutableListOf<VectorGroup>()

        for ((address, smsInAddress) in addressGroups) {
            if (smsInAddress.size == 1) {
                // 1건뿐이면 단독 그룹
                allGroups.add(VectorGroup(smsInAddress[0], mutableListOf(smsInAddress[0])))
                continue
            }

            // Level 2: 벡터 유사도 기반 서브그룹핑
            val subGroups = groupBySimilarityInternal(smsInAddress)

            // Level 3: 소그룹 병합
            val merged = mergeSmallGroups(subGroups, address)
            allGroups.addAll(merged)
        }

        return allGroups.sortedByDescending { it.members.size }
    }

    /**
     * 같은 발신번호 내 벡터 유사도 기반 서브그룹핑
     *
     * 그리디 클러스터링:
     * - i번째 SMS를 새 그룹의 중심으로 설정
     * - i+1 ~ 끝까지 순회, 유사도 ≥ 0.95면 같은 그룹
     * - 이미 할당된 SMS는 스킵
     * - 50건마다 yield()로 코루틴 양보
     */
    private suspend fun groupBySimilarityInternal(
        embeddedSms: List<EmbeddedSms>
    ): List<VectorGroup> {
        val groups = mutableListOf<VectorGroup>()
        val assigned = BooleanArray(embeddedSms.size)

        for (i in embeddedSms.indices) {
            if (assigned[i]) continue

            // 50건마다 yield로 다른 코루틴에 실행 기회 제공
            if (i % 50 == 0) yield()

            val smsI = embeddedSms[i]
            val group = VectorGroup(
                representative = smsI,
                members = mutableListOf(smsI)
            )

            // 유사한 SMS를 그룹에 추가
            for (j in (i + 1) until embeddedSms.size) {
                if (assigned[j]) continue

                val smsJ = embeddedSms[j]
                val similarity = patternMatcher.cosineSimilarity(
                    smsI.embedding, smsJ.embedding
                )

                if (similarity >= GROUPING_SIMILARITY) {
                    group.members.add(smsJ)
                    assigned[j] = true
                }
            }

            assigned[i] = true
            groups.add(group)
        }

        return groups.sortedByDescending { it.members.size }
    }

    /**
     * 소그룹 병합
     *
     * 같은 발신번호 내 소그룹(멤버 ≤ 5)을 최대 그룹에 흡수.
     * 대표 벡터 간 유사도 ≥ 0.70이면 병합, 미만이면 독립 유지.
     */
    private fun mergeSmallGroups(
        subGroups: List<VectorGroup>,
        address: String
    ): List<VectorGroup> {
        if (subGroups.size <= 1) return subGroups

        val sorted = subGroups.sortedByDescending { it.members.size }
        val largestGroup = sorted.first()
        val keptGroups = mutableListOf(largestGroup)
        var mergedCount = 0

        for (i in 1 until sorted.size) {
            val smallGroup = sorted[i]
            if (smallGroup.members.size <= SMALL_GROUP_MERGE_THRESHOLD) {
                val similarity = patternMatcher.cosineSimilarity(
                    largestGroup.representative.embedding,
                    smallGroup.representative.embedding
                )
                if (similarity >= SMALL_GROUP_MERGE_MIN_SIMILARITY) {
                    largestGroup.members.addAll(smallGroup.members)
                    mergedCount += smallGroup.members.size
                } else {
                    keptGroups.add(smallGroup)
                }
            } else {
                keptGroups.add(smallGroup)
            }
        }

        if (mergedCount > 0) {
            Log.d(TAG, "소그룹 병합: 발신=$address, ${subGroups.size}서브→${keptGroups.size}그룹 (${mergedCount}건 흡수)")
        }

        return keptGroups
    }

    // ===== 발신번호 정규화 =====

    /**
     * 발신번호 정규화
     *
     * +82/하이픈/공백/괄호 등 표기 차이로 같은 발신자가 분리되지 않도록 정규화.
     * 예: "+82-10-1234-5678" → "01012345678"
     *     "1588 1234" → "15881234"
     */
    private fun normalizeAddress(rawAddress: String): String {
        var normalized = rawAddress.trim()
        normalized = ADDRESS_CLEAN_PATTERN.replace(normalized, "")
        if (normalized.startsWith("+82")) {
            normalized = "0" + normalized.substring(3)
        } else if (normalized.startsWith("82") && normalized.length >= 11) {
            normalized = "0" + normalized.substring(2)
        }
        return normalized
    }

    // ===== [5-3] 패턴 등록 =====

    /**
     * 결제 패턴을 DB에 등록
     *
     * SmsPatternEntity 생성 후 Room DB에 insert.
     * 향후 같은 형식 SMS는 Step 4에서 벡터 매칭으로 재사용.
     *
     * confidence 기준:
     * - "llm_regex": 1.0 (LLM regex 검증 완료)
     * - "template_regex": 0.85 (템플릿 폴백)
     * - "llm": 0.8 (regex 없는 LLM 결과)
     */
    private suspend fun registerPaymentPattern(
        embedded: EmbeddedSms,
        analysis: SmsAnalysisResult,
        source: String,
        amountRegex: String = "",
        storeRegex: String = "",
        cardRegex: String = ""
    ) {
        try {
            val pattern = SmsPatternEntity(
                smsTemplate = embedded.template,
                senderAddress = embedded.input.address,
                embedding = embedded.embedding,
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
                    "llm_regex" -> 1.0f
                    "template_regex" -> 0.85f
                    else -> 0.8f
                }
            )
            smsPatternDao.insert(pattern)
            Log.d(TAG, "패턴 등록: ${analysis.cardName} ${analysis.storeName} ($source)")

            // RTDB 표본 수집 (비동기, 실패 무시)
            val hasVerifiedRegex = source in listOf("llm_regex")
            collectSampleToRtdb(
                embedded = embedded,
                cardName = analysis.cardName,
                parseSource = source,
                amountRegex = if (hasVerifiedRegex) amountRegex else "",
                storeRegex = if (hasVerifiedRegex) storeRegex else "",
                cardRegex = if (hasVerifiedRegex) cardRegex else ""
            )
        } catch (e: Exception) {
            Log.e(TAG, "패턴 등록 실패: ${e.message}")
        }
    }

    /**
     * 비결제 패턴을 DB에 등록
     *
     * LLM이 비결제로 판정한 SMS의 벡터를 캐시.
     * 다음에 유사한 SMS가 오면 Step 4에서 비결제 매칭(≥0.97)으로 빠르게 제외.
     */
    private suspend fun registerNonPaymentPattern(embedded: EmbeddedSms) {
        try {
            val pattern = SmsPatternEntity(
                smsTemplate = embedded.template,
                senderAddress = embedded.input.address,
                embedding = embedded.embedding,
                isPayment = false,
                parseSource = "llm"
            )
            smsPatternDao.insert(pattern)
        } catch (e: Exception) {
            Log.e(TAG, "비결제 패턴 등록 실패: ${e.message}")
        }
    }

    // ===== [5-3] RTDB 표본 수집 =====

    /**
     * RTDB에 마스킹된 SMS 샘플 수집 (비동기, 실패해도 무시)
     *
     * 목적: 향후 정규식 주입/개선용 표본 축적
     * 중복 방지: 기존 전송 표본과 유사도 ≥ 0.99면 스킵
     * PII 마스킹: 숫자→*, 가게명→*, 날짜→*
     */
    private fun collectSampleToRtdb(
        embedded: EmbeddedSms,
        cardName: String,
        parseSource: String,
        amountRegex: String,
        storeRegex: String,
        cardRegex: String
    ) {
        val db = database ?: return

        // 유사도 기반 중복 방지
        synchronized(sentSampleEmbeddings) {
            for (sentEmbedding in sentSampleEmbeddings) {
                val similarity = patternMatcher.cosineSimilarity(embedded.embedding, sentEmbedding)
                if (similarity >= RTDB_DEDUP_SIMILARITY) return
            }
            sentSampleEmbeddings.add(embedded.embedding)
        }

        try {
            val maskedBody = maskSmsBody(embedded.input.body)
            val sampleKey = "${embedded.input.address}_${embedded.template.hashCode().toUInt()}"

            val ref = db.getReference("sms_samples").child(sampleKey)
            val data = mutableMapOf<String, Any>(
                "maskedBody" to maskedBody,
                "cardName" to cardName,
                "senderAddress" to embedded.input.address,
                "parseSource" to parseSource,
                "count" to ServerValue.increment(1),
                "lastSeen" to ServerValue.TIMESTAMP
            )
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

    /**
     * PII 마스킹: 원본 SMS에서 개인정보를 제거
     *
     * 마스킹 순서 (중요: 순서가 바뀌면 패턴 충돌 발생):
     * 1. 가게명 마스킹 (4줄 이상 SMS에서 가게명 줄 판별)
     * 2. 카드번호 마스킹 (1234x5678 형태)
     * 3. 날짜 마스킹 (01/15 형태)
     * 4. 시간 마스킹 (12:30 형태)
     * 5. 금액 마스킹 (4,500 형태, 쉼표 보존)
     * 6. 남은 숫자 마스킹
     */
    private fun maskSmsBody(smsBody: String): String {
        var masked = smsBody

        // 1) 가게명 마스킹
        val lines = masked.split("\n")
        if (lines.size >= 4) {
            var storeReplaced = false
            val result = lines.map { line ->
                val trimmed = line.trim()
                if (!storeReplaced && templateEngine.isLikelyStoreName(trimmed)) {
                    storeReplaced = true
                    "*".repeat(trimmed.length.coerceAtMost(10))
                } else {
                    line
                }
            }
            masked = result.joinToString("\n")
        }
        // 2) 카드번호 마스킹
        masked = masked.replace(Regex("""\d+\*+\d+""")) { match ->
            "*".repeat(match.value.length)
        }
        // 3) 날짜 마스킹
        masked = masked.replace(Regex("""\d{1,2}([/.\-])\d{1,2}""")) { match ->
            match.value.replace(Regex("""\d"""), "*")
        }
        // 4) 시간 마스킹
        masked = masked.replace(Regex("""\d{1,2}:\d{2}""")) { match ->
            match.value.replace(Regex("""\d"""), "*")
        }
        // 5) 금액 마스킹 (쉼표 보존)
        masked = masked.replace(Regex("""(\d{1,3})(,\d{3})*""")) { match ->
            match.value.replace(Regex("""\d"""), "*")
        }
        // 6) 남은 숫자 마스킹
        masked = masked.replace(Regex("""\d+""")) { match ->
            "*".repeat(match.value.length)
        }

        return masked
    }

    // ===== [5-3] regex 실패 쿨다운 =====

    /**
     * 같은 템플릿에 대해 regex 생성이 2회 이상 실패하면 30분 쿨다운.
     * 실패하는 패턴에 대해 반복 LLM 호출 방지.
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
                current.copy(failCount = current.failCount + 1, lastFailedAt = now)
            }
        }
    }

    private fun clearRegexFailure(template: String) {
        regexFailureStates.remove(template)
    }

    // ===== [5-3] 템플릿 기반 폴백 regex =====

    /**
     * LLM regex 생성 실패 시, 템플릿의 {STORE}/{AMOUNT} 플레이스홀더를 기반으로
     * 최소한의 폴백 regex 생성.
     *
     * 조건: 템플릿에 {AMOUNT}와 {STORE}가 모두 있어야 함
     *
     * 결과 예시:
     * - amountRegex: "([\d,]{2,})원" 또는 "\n([\d,]{2,})\n"
     * - storeRegex: "\n([^\n]{2,30})\n(?:체크카드출금|출금|승인|결제)"
     * - cardRegex: "\[([^\]]+)\]"
     */
    private fun buildTemplateFallbackRegex(
        template: String
    ): FallbackRegex? {
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

        return FallbackRegex(amountRegex, storeRegex, cardRegex)
    }

    /** 폴백 regex 결과 (내부용) */
    private data class FallbackRegex(
        val amountRegex: String,
        val storeRegex: String,
        val cardRegex: String
    )
}
