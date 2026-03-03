package com.sanha.moneytalk.core.sms2

import com.sanha.moneytalk.core.util.MoneyTalkLogger

import com.sanha.moneytalk.core.database.dao.SmsPatternDao
import com.sanha.moneytalk.core.database.entity.SmsPatternEntity
import com.sanha.moneytalk.core.model.SmsAnalysisResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
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
 * │     → 3차: 소그룹(≤5멤버)을 최대 그룹에 흡수 (≥0.90)     │
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
 * - SmsOriginSampleCollector — RTDB 표본 수집
 */
@Singleton
class SmsGroupClassifier @Inject constructor(
    private val smsPatternDao: SmsPatternDao,
    private val smsExtractor: GeminiSmsExtractor,
    private val patternMatcher: SmsPatternMatcher,
    private val templateEngine: SmsTemplateEngine,
    private val originSampleCollector: SmsOriginSampleCollector
) {

    companion object {

        /** LLM 병렬 동시 실행 수 (API 키 5개 × 키당 1) */
        private const val LLM_CONCURRENCY = 5

        /** regex 생성 샘플 수 (그룹 대표 포함 최대 5건) */
        private const val REGEX_SAMPLE_SIZE = 5

        /** regex 생성 최소 멤버 수 (5건 미만이면 regex 생성 스킵, LLM 배치로 직접 파싱) */
        private const val REGEX_MIN_SAMPLES = 5

        /** processGroup 전체 시간 예산 — LLM 추출 + regex 생성/검증/repair (초과 시 regex 스킵) */
        private const val GROUP_TIME_BUDGET_MS = 10_000L

        /** processGroup 단건 LLM 추출 null 응답 재시도 횟수 */
        private const val GROUP_LLM_NULL_RETRY_MAX = 1
        private const val GROUP_LLM_NULL_RETRY_DELAY_MS = 300L

        /** Step5 소프트 시간 예산 — 초과 시 regex 생략하고 Direct LLM 강등 */
        private const val STEP5_TIME_BUDGET_MS = 20_000L

        /** 그룹핑 유사도 임계값 (같은 발신번호 내 벡터 클러스터링) */
        private const val GROUPING_SIMILARITY = 0.95f

        /** 소그룹 병합 임계값: 이 수 이하 멤버 그룹은 최대 그룹에 흡수 */
        private const val SMALL_GROUP_MERGE_THRESHOLD = 5

        /** 소그룹 병합 최소 유사도: 이 값 미만이면 독립 그룹 유지 */
        private const val SMALL_GROUP_MERGE_MIN_SIMILARITY = 0.90f

        /** regex 생성 실패 쿨다운 기준 횟수 */
        private const val REGEX_FAILURE_THRESHOLD = 2

        /** regex 생성 실패 쿨다운 시간 (30분) */
        private const val REGEX_FAILURE_COOLDOWN_MS = 30L * 60L * 1000L

        /** regex 미생성 시 LLM 폴백 배치 크기 */
        private const val LLM_FALLBACK_MAX_SAMPLES = 10

        /** 그룹 대표 추출 시 LLM에 전달할 컨텍스트 샘플 수 */
        private const val GROUP_CONTEXT_MAX_SAMPLES = 10

        /** sender IN 쿼리 chunk 크기 (SQLite bind limit 여유) */
        private const val MAIN_PATTERN_QUERY_CHUNK_SIZE = 500

        /** regex 검증: 샘플 중 이 비율 이상 파싱 성공해야 유효 (80%) */
        private const val REGEX_VALIDATION_MIN_PASS_RATIO = 0.8f

        /** Classifier repair 대상 near-miss 하한 (이 비율 미만이면 repair 스킵 → abort) */
        private const val REGEX_NEAR_MISS_MIN_RATIO = 0.6f

        /** 템플릿 기반 시드 그룹 최소 멤버 수 */
        private const val TEMPLATE_SEED_MIN_BUCKET_SIZE = 2

        /** Phase 2(D): unstable 그룹 판단 기준 (대표 임베딩 대비 유사도 분포) */
        private const val UNSTABLE_MEDIAN_MIN_SIMILARITY = 0.92f
        private const val UNSTABLE_P20_MIN_SIMILARITY = 0.88f
        private const val UNSTABLE_DIRECT_LLM_MAX_SIZE = 10

        /** 발신번호 내 유사도 그룹핑 최대 대상 수 (초과 시 단독 그룹으로 빠르게 처리) */
        private const val SIMILARITY_GROUPING_MAX_SIZE = 120

        /** 발신번호 정규화: 하이픈/공백/괄호 제거용 */
        private val ADDRESS_CLEAN_PATTERN = Regex("""[-\s().]""")

        // ===== Step4.5 regex 실패 복구 정책 =====
        /** Step4.5에서 같은 키가 반복 실패할 때 쿨다운 적용 기준 횟수 */
        private const val REGEX_FAILED_RECOVERY_FAILURE_THRESHOLD = 2
        /** Step4.5 반복 실패 키 쿨다운 (30분) */
        private const val REGEX_FAILED_RECOVERY_COOLDOWN_MS = 30L * 60L * 1000L

        /** 복구 대상 최소 길이 (이보다 짧으면 정보 부족으로 Step5 fallback) */
        private const val REGEX_FAILED_RECOVERY_MIN_LENGTH = 12

        /** 정규식 실패건 recoverable 판단용 키워드 */
        private val REGEX_FAILED_RECOVERY_HINT_KEYWORDS = listOf(
            "승인", "결제", "출금", "입금", "사용", "취소", "잔액", "이체"
        )

        /** amount 근거 탐지용 정규식 */
        private val RECOVERY_AMOUNT_WITH_CURRENCY = Regex("""\d{1,3}(,\d{3})*(원|usd|jpy|eur)""", RegexOption.IGNORE_CASE)
        private val RECOVERY_AMOUNT_WITH_COMMA = Regex("""\d{1,3}(,\d{3})+""")
        private val RECOVERY_PLAIN_AMOUNT = Regex("""\b\d{3,8}\b""")

        /** grounding 검증에서 제외할 generic store 후보 */
        private val RECOVERY_GENERIC_STORE_NAMES = setOf(
            "결제", "사용", "출금", "입금", "승인", "취소", "기타"
        )

        /** 공백/구분자 제거용 정규식 (grounding 비교) */
        private val WHITESPACE_OR_SEP = Regex("""[\s\-\[\]()_/]""")

        /** Step4.5/Step5 fallback용 KB 멀티라인 출금 휴리스틱 regex */
        private const val KB_DEBIT_FALLBACK_AMOUNT_REGEX = """출금\n([\d,]+)\n잔액"""
        private const val KB_DEBIT_FALLBACK_STORE_REGEX = """\d+\*+\d+\n(.+?)\n출금"""

        /** Phase4: 백그라운드 학습 큐 최대 적재량 (초과 시 oldest drop) */
        private const val LEARNING_QUEUE_MAX_SIZE = 1000
        private const val LEARNING_PROCESSOR_BATCH_LOG = 20

        /** maskSmsBody용 사전 컴파일 정규식 (매 호출마다 컴파일 방지) */
        private val MASK_CARD_NUM = Regex("""\d+\*+\d+""")
        private val MASK_DATE = Regex("""\d{1,2}([/.\-])\d{1,2}""")
        private val MASK_TIME = Regex("""\d{1,2}:\d{2}""")
        private val MASK_AMOUNT = Regex("""(\d{1,3})(,\d{3})*""")
        private val MASK_REMAINING_DIGITS = Regex("""\d+""")
        private val MASK_SINGLE_DIGIT = Regex("""\d""")
    }

    // ===== 내부 데이터 =====

    /**
     * 그룹 처리 결과 — processGroup()의 반환 타입
     *
     * 파싱 결과뿐 아니라 생성된 regex 정보도 함께 반환하여,
     * 메인 그룹의 regex를 예외 그룹 regex 생성 시 참조로 전달할 수 있도록 함.
     *
     * @property results 파싱 결과 리스트
     * @property amountRegex 생성된 금액 정규식 (빈 문자열이면 미생성)
     * @property storeRegex 생성된 가게명 정규식
     * @property cardRegex 생성된 카드명 정규식
     */
    private data class GroupProcessResult(
        val results: List<SmsParseResult>,
        val amountRegex: String = "",
        val storeRegex: String = "",
        val cardRegex: String = "",
        val outcomeCode: String = ""
    )

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
     * 메인 케이스 참조 정보 — 서브그룹 LLM 호출 시 함께 전달
     *
     * 메인 케이스가 "결제"로 확인된 후, 서브그룹 LLM 호출에
     * 이 정보를 포함하여 분류 정확도를 높임.
     *
     * @property cardName 메인 케이스에서 추출된 카드명 (예: "KB국민")
     * @property template 메인 케이스의 템플릿 (예: "[KB]{DATE} {STORE} {AMOUNT}원 승인")
     * @property samples 메인 케이스의 원본 SMS 샘플 (최대 3건)
     * @property isPayment 메인 케이스가 결제인지 여부
     * @property amountRegex 메인 케이스의 금액 정규식 (regex 생성 성공 시)
     * @property storeRegex 메인 케이스의 가게명 정규식 (regex 생성 성공 시)
     * @property cardRegex 메인 케이스의 카드명 정규식 (regex 생성 성공 시)
     */
    private data class MainCaseContext(
        val cardName: String,
        val template: String,
        val samples: List<String>,
        val isPayment: Boolean,
        val amountRegex: String = "",
        val storeRegex: String = "",
        val cardRegex: String = ""
    )

    /**
     * regex 검증 상세 결과 — near-miss repair 판단에 사용
     *
     * @property passed 검증 통과 여부 (passRatio >= REGEX_VALIDATION_MIN_PASS_RATIO)
     * @property passRatio 샘플 파싱 성공 비율 (0.0 ~ 1.0)
     * @property failedSampleIndices 파싱 실패한 샘플의 인덱스 목록
     * @property failedSampleDiagnostics 파싱 실패 사유(샘플 인덱스와 동일 순서)
     */
    private data class RegexValidationDetail(
        val passed: Boolean,
        val passRatio: Float,
        val failedSampleIndices: List<Int>,
        val failedSampleDiagnostics: List<String>
    )

    /**
     * 그룹 안정도 진단 결과 (Phase 2-D)
     *
     * 대표 임베딩 대비 멤버 유사도 분포의 중앙값/하위20%로 그룹 순도를 판단.
     */
    private data class GroupStability(
        val unstable: Boolean,
        val medianSimilarity: Float,
        val p20Similarity: Float
    )

    /**
     * Step4.5 chunk 실행 계획 (발신번호별 chunk를 병렬 처리)
     */
    private data class RegexFailedChunk(
        val address: String,
        val items: List<EmbeddedSms>
    )

    /**
     * Step4.5 chunk 처리 결과
     */
    private data class RegexFailedChunkResult(
        val address: String,
        val recovered: List<SmsParseResult>,
        val fallback: List<EmbeddedSms>,
        val retryAttempted: Int,
        val groundingRejected: Int,
        val llmFailed: Int
    )

    /**
     * Step4.5 발신번호 단위 통계
     */
    private data class RegexFailedAddressStats(
        var inputCount: Int = 0,
        var recoveredCount: Int = 0,
        var fallbackCount: Int = 0,
        var retryAttempted: Int = 0,
        var groundingRejected: Int = 0,
        var llmFailed: Int = 0
    )

    /**
     * Phase4: 동기화/학습 완전 분리를 위한 백그라운드 학습 작업
     */
    private sealed class DeferredLearningTask {
        abstract val reasonCode: String
        abstract val dedupKey: String

        data class Payment(
            val embedded: EmbeddedSms,
            val analysis: SmsAnalysisResult,
            val source: String,
            val amountRegex: String,
            val storeRegex: String,
            val cardRegex: String,
            val isMainGroup: Boolean,
            val groupMemberCount: Int,
            override val reasonCode: String,
            override val dedupKey: String
        ) : DeferredLearningTask()

        data class NonPayment(
            val embedded: EmbeddedSms,
            override val reasonCode: String,
            override val dedupKey: String
        ) : DeferredLearningTask()
    }

    /** regex 생성 실패 쿨다운 추적 */
    private val regexFailureStates = ConcurrentHashMap<String, RegexFailureState>()
    private data class RegexFailureState(val failCount: Int, val lastFailedAt: Long)

    /** Step4.5 regex 실패 복구 쿨다운 추적 */
    private val regexFailedRecoveryStates = ConcurrentHashMap<String, RegexFailureState>()

    /** Phase4: 백그라운드 학습 큐 */
    private val learningScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val learningQueue = ConcurrentLinkedQueue<DeferredLearningTask>()
    private val learningQueueSize = AtomicInteger(0)
    private val learningDroppedCount = AtomicInteger(0)
    private val learningProcessorRunning = AtomicBoolean(false)
    private val learningDedupKeys = ConcurrentHashMap.newKeySet<String>()

    // ===== Step 4.5: regex 실패건 배치 LLM 추출 =====

    /**
     * Step4 벡터매칭 OK + regex 파싱 실패건을 배치 LLM으로 추출
     *
     * Step5(그룹핑+regex생성)를 건너뛰고, 같은 발신번호끼리 묶어 배치 LLM으로 추출.
     * regex 실패건은 이미 패턴 DB에 임베딩이 존재하므로 regex 재생성이 무의미.
     * LLM 배치 추출로 빠르게 처리 (~3초).
     *
     * @param regexFailedList Step4에서 벡터매칭 OK + regex 파싱 실패한 SMS 리스트
     * @return Pair(성공 결과, 실패→Step5 fallback)
     */
    suspend fun batchExtractRegexFailed(
        regexFailedList: List<EmbeddedSms>
    ): Pair<List<SmsParseResult>, List<EmbeddedSms>> {
        if (regexFailedList.isEmpty()) return Pair(emptyList(), emptyList())

        val startTime = System.currentTimeMillis()
        val immediateFallback = mutableListOf<EmbeddedSms>()
        val addressCandidates = linkedMapOf<String, MutableList<EmbeddedSms>>()
        var skippedByCooldown = 0
        var skippedByUnrecoverable = 0

        // 1) recoverable/cooldown 게이트: 비복구/반복실패 키는 Step5로 즉시 위임
        for (embedded in regexFailedList) {
            when {
                shouldSkipRegexFailedRecovery(embedded) -> {
                    immediateFallback.add(embedded)
                    skippedByCooldown++
                }
                !isRecoverableRegexFailedSms(embedded.input.body) -> {
                    immediateFallback.add(embedded)
                    recordRegexFailedRecoveryFailure(embedded)
                    skippedByUnrecoverable++
                }
                else -> {
                    val address = normalizeAddress(embedded.input.address)
                    addressCandidates.getOrPut(address) { mutableListOf() }.add(embedded)
                }
            }
        }

        // 복구 대상이 없으면 즉시 반환
        if (addressCandidates.isEmpty()) {
            MoneyTalkLogger.i(
                "[batchExtractRegexFailed] 총 ${regexFailedList.size}건 → 복구 0건, " +
                    "Step5 fallback ${immediateFallback.size}건, skipCooldown=${skippedByCooldown}건, " +
                    "skipUnrecoverable=${skippedByUnrecoverable}건, elapsed=${System.currentTimeMillis() - startTime}ms"
            )
            return Pair(emptyList(), immediateFallback)
        }

        val chunks = addressCandidates.flatMap { (address, members) ->
            members.chunked(LLM_FALLBACK_MAX_SAMPLES).map { chunk ->
                RegexFailedChunk(address = address, items = chunk)
            }
        }

        // 2) chunk 병렬 실행 (같은 발신번호도 chunk 단위 병렬 가능)
        val semaphore = Semaphore(LLM_CONCURRENCY)
        val chunkResults = coroutineScope {
            chunks.map { chunk ->
                async(Dispatchers.IO) {
                    semaphore.acquire()
                    try {
                        processRegexFailedChunk(chunk)
                    } finally {
                        semaphore.release()
                    }
                }
            }.awaitAll()
        }

        // 3) 결과 집계
        val recovered = mutableListOf<SmsParseResult>()
        val fallback = mutableListOf<EmbeddedSms>()
        fallback.addAll(immediateFallback)

        val addrStats = linkedMapOf<String, RegexFailedAddressStats>()
        for (result in chunkResults) {
            recovered.addAll(result.recovered)
            fallback.addAll(result.fallback)

            val stats = addrStats.getOrPut(result.address) { RegexFailedAddressStats() }
            stats.inputCount += result.recovered.size + result.fallback.size
            stats.recoveredCount += result.recovered.size
            stats.fallbackCount += result.fallback.size
            stats.retryAttempted += result.retryAttempted
            stats.groundingRejected += result.groundingRejected
            stats.llmFailed += result.llmFailed
        }

        addrStats.forEach { (address, stats) ->
            val addr = address.takeLast(4)
            MoneyTalkLogger.i(
                "[batchExtractRegexFailed] addr=*$addr: ${stats.inputCount}건 → " +
                    "복구${stats.recoveredCount}건, Step5 ${stats.fallbackCount}건, " +
                    "retry=${stats.retryAttempted}건, groundingReject=${stats.groundingRejected}건, llmFail=${stats.llmFailed}건"
            )
        }

        val elapsed = System.currentTimeMillis() - startTime
        val totalRetry = addrStats.values.sumOf { it.retryAttempted }
        val totalGroundingReject = addrStats.values.sumOf { it.groundingRejected }
        val totalLlmFail = addrStats.values.sumOf { it.llmFailed }
        MoneyTalkLogger.i(
            "[batchExtractRegexFailed] 총 ${regexFailedList.size}건 → 복구 ${recovered.size}건, Step5 fallback ${fallback.size}건, " +
                "retry=${totalRetry}건, groundingReject=${totalGroundingReject}건, llmFail=${totalLlmFail}건, " +
                "skipCooldown=${skippedByCooldown}건, skipUnrecoverable=${skippedByUnrecoverable}건, elapsed=${elapsed}ms"
        )
        return Pair(recovered, fallback)
    }

    /**
     * Step4.5 단일 chunk 처리
     * - 1차 배치 호출
     * - null 응답 항목은 단건 재시도 1회
     * - grounding 검증 통과분만 채택
     */
    private suspend fun processRegexFailedChunk(chunk: RegexFailedChunk): RegexFailedChunkResult {
        val bodies = chunk.items.map { it.input.body }
        val timestamps = chunk.items.map { it.input.date }

        val primary = smsExtractor.extractFromSmsBatch(bodies, timestamps).toMutableList()
        val retryIndices = primary.indices.filter { primary[it] == null }
        var retryAttempted = 0

        // parse 오류(JsonNull/빈응답 등)로 null인 항목만 1회 단건 재시도
        for (index in retryIndices) {
            retryAttempted++
            val retried = smsExtractor.extractFromSmsBatch(
                smsMessages = listOf(bodies[index]),
                smsTimestamps = listOf(timestamps[index])
            ).firstOrNull()
            primary[index] = retried
        }

        val recovered = mutableListOf<SmsParseResult>()
        val fallback = mutableListOf<EmbeddedSms>()
        var groundingRejected = 0
        var llmFailed = 0

        chunk.items.forEachIndexed { index, embedded ->
            val llm = primary.getOrNull(index)
            val accepted = llm != null &&
                llm.isPayment &&
                llm.amount > 0 &&
                isGroundedRegexFailedRecovery(embedded.input.body, llm)

            if (accepted && llm != null) {
                clearRegexFailedRecoveryFailure(embedded)
                recovered.add(
                    SmsParseResult(
                        input = embedded.input,
                        analysis = SmsAnalysisResult(
                            amount = llm.amount,
                            storeName = llm.storeName,
                            category = llm.category,
                            dateTime = llm.dateTime,
                            cardName = llm.cardName
                        ),
                        tier = 3,
                        confidence = 1.0f
                    )
                )
            } else {
                val heuristicResult = tryHeuristicParse(
                    embedded = embedded,
                    fallbackCardName = llm?.cardName?.takeIf { it.isNotBlank() } ?: "KB"
                )
                if (heuristicResult != null) {
                    clearRegexFailedRecoveryFailure(embedded)
                    recovered.add(heuristicResult)
                    return@forEachIndexed
                }

                fallback.add(embedded)
                recordRegexFailedRecoveryFailure(embedded)
                if (llm != null && llm.isPayment && llm.amount > 0) {
                    groundingRejected++
                } else {
                    llmFailed++
                }
            }
        }

        return RegexFailedChunkResult(
            address = chunk.address,
            recovered = recovered,
            fallback = fallback,
            retryAttempted = retryAttempted,
            groundingRejected = groundingRejected,
            llmFailed = llmFailed
        )
    }

    /**
     * Step4.5 복구 대상 여부 판단.
     * - 너무 짧은/근거 부족 본문은 LLM 복구 대신 Step5로 위임.
     */
    private fun isRecoverableRegexFailedSms(body: String): Boolean {
        if (body.length < REGEX_FAILED_RECOVERY_MIN_LENGTH) return false

        val hasAmountPattern = RECOVERY_AMOUNT_WITH_CURRENCY.containsMatchIn(body) ||
            RECOVERY_AMOUNT_WITH_COMMA.containsMatchIn(body)
        if (hasAmountPattern) return true

        val lower = body.lowercase(Locale.getDefault())
        val hasHintKeyword = REGEX_FAILED_RECOVERY_HINT_KEYWORDS.any { keyword ->
            lower.contains(keyword.lowercase(Locale.getDefault()))
        }
        val hasPlainAmount = RECOVERY_PLAIN_AMOUNT.containsMatchIn(body)
        return hasHintKeyword && hasPlainAmount
    }

    /**
     * Step4.5 LLM 결과가 원문 근거를 갖는지 검증.
     * - amount는 원문에서 직접 매칭되어야 함
     * - store는 generic 명칭 제외 + 원문 substring 매칭 필요
     */
    private fun isGroundedRegexFailedRecovery(
        body: String,
        llm: GeminiSmsExtractor.LlmExtractionResult
    ): Boolean {
        if (!containsAmountEvidence(body, llm.amount)) return false

        val store = llm.storeName.trim()
        if (store.isBlank()) return false
        if (store in RECOVERY_GENERIC_STORE_NAMES) return false

        val compactBody = body
            .lowercase(Locale.getDefault())
            .replace(WHITESPACE_OR_SEP, "")
        val compactStore = store
            .lowercase(Locale.getDefault())
            .replace(WHITESPACE_OR_SEP, "")
        if (compactStore.length < 2) return false
        return compactBody.contains(compactStore)
    }

    private fun containsAmountEvidence(body: String, amount: Int): Boolean {
        if (amount <= 0) return false
        val normalizedBody = body.replace(",", "")
        val amountToken = amount.toString()
        val pattern = Regex("(^|\\D)$amountToken(\\D|$)")
        return pattern.containsMatchIn(normalizedBody)
    }

    /**
     * KB 멀티라인 출금 SMS 휴리스틱 파싱.
     * Step4.5/Step5에서 LLM이 흔들릴 때 마지막 보정 수단으로 사용한다.
     */
    private fun tryParseKbDebitFallback(
        embedded: EmbeddedSms,
        fallbackCardName: String = "KB"
    ): SmsAnalysisResult? {
        val body = embedded.input.body
        if (!body.contains("[KB]")) return null
        if (!body.contains("\n출금\n")) return null

        return patternMatcher.parseWithRegex(
            smsBody = body,
            smsTimestamp = embedded.input.date,
            amountRegex = KB_DEBIT_FALLBACK_AMOUNT_REGEX,
            storeRegex = KB_DEBIT_FALLBACK_STORE_REGEX,
            fallbackCardName = fallbackCardName
        )
    }

    /**
     * KB 멀티라인 출금 휴리스틱 파싱 → SmsParseResult 변환 helper.
     * tryParseKbDebitFallback 호출 + SmsParseResult 래핑을 한곳에서 관리한다.
     */
    private fun tryHeuristicParse(
        embedded: EmbeddedSms,
        fallbackCardName: String = "KB"
    ): SmsParseResult? {
        val analysis = tryParseKbDebitFallback(embedded, fallbackCardName) ?: return null
        return SmsParseResult(
            input = embedded.input,
            analysis = analysis,
            tier = 3,
            confidence = 1.0f
        )
    }

    private fun shouldSkipRegexFailedRecovery(embedded: EmbeddedSms): Boolean {
        val key = regexFailedRecoveryKey(embedded)
        val state = regexFailedRecoveryStates[key] ?: return false
        if (state.failCount < REGEX_FAILED_RECOVERY_FAILURE_THRESHOLD) return false
        val elapsed = System.currentTimeMillis() - state.lastFailedAt
        return elapsed < REGEX_FAILED_RECOVERY_COOLDOWN_MS
    }

    private fun recordRegexFailedRecoveryFailure(embedded: EmbeddedSms) {
        val key = regexFailedRecoveryKey(embedded)
        val now = System.currentTimeMillis()
        regexFailedRecoveryStates.compute(key) { _, current ->
            if (current == null) {
                RegexFailureState(failCount = 1, lastFailedAt = now)
            } else if (now - current.lastFailedAt >= REGEX_FAILED_RECOVERY_COOLDOWN_MS) {
                RegexFailureState(failCount = 1, lastFailedAt = now)
            } else {
                current.copy(failCount = current.failCount + 1, lastFailedAt = now)
            }
        }
    }

    private fun clearRegexFailedRecoveryFailure(embedded: EmbeddedSms) {
        regexFailedRecoveryStates.remove(regexFailedRecoveryKey(embedded))
    }

    private fun regexFailedRecoveryKey(embedded: EmbeddedSms): String {
        val sender = normalizeAddress(embedded.input.address)
        val templateHash = embedded.template.hashCode().toUInt()
        return "$sender:$templateHash"
    }

    // ===== Phase4: 백그라운드 학습 큐 =====

    private fun enqueueDeferredLearning(task: DeferredLearningTask) {
        if (!learningDedupKeys.add(task.dedupKey)) {
            return
        }

        // 큐 상한 유지: 초과 시 oldest drop
        while (learningQueueSize.get() >= LEARNING_QUEUE_MAX_SIZE) {
            val dropped = learningQueue.poll() ?: break
            learningQueueSize.updateAndGet { current -> if (current > 0) current - 1 else 0 }
            learningDroppedCount.incrementAndGet()
            learningDedupKeys.remove(dropped.dedupKey)
        }

        learningQueue.offer(task)
        learningQueueSize.incrementAndGet()
        startLearningProcessorIfNeeded()
    }

    private fun startLearningProcessorIfNeeded() {
        if (!learningProcessorRunning.compareAndSet(false, true)) return

        learningScope.launch {
            var processed = 0
            var failed = 0
            try {
                while (true) {
                    val task = learningQueue.poll() ?: break
                    learningQueueSize.updateAndGet { current -> if (current > 0) current - 1 else 0 }

                    try {
                        when (task) {
                            is DeferredLearningTask.Payment -> {
                                registerPaymentPattern(
                                    embedded = task.embedded,
                                    analysis = task.analysis,
                                    source = task.source,
                                    amountRegex = task.amountRegex,
                                    storeRegex = task.storeRegex,
                                    cardRegex = task.cardRegex,
                                    isMainGroup = task.isMainGroup,
                                    groupMemberCount = task.groupMemberCount
                                )
                            }
                            is DeferredLearningTask.NonPayment -> {
                                registerNonPaymentPattern(task.embedded)
                            }
                        }
                        processed++
                        if (processed % LEARNING_PROCESSOR_BATCH_LOG == 0) {
                            MoneyTalkLogger.i(
                                "[learningQueue] 처리중: processed=$processed, failed=$failed, " +
                                    "remaining=${learningQueueSize.get()}, dropped=${learningDroppedCount.get()}"
                            )
                        }
                    } catch (e: Exception) {
                        failed++
                        MoneyTalkLogger.w("[learningQueue] 처리 실패: reason=${task.reasonCode}, msg=${e.message}")
                    } finally {
                        // dedup key는 작업 완료(성공/실패) 시점까지 유지하여
                        // in-flight 상태에서 동일 작업이 재적재되지 않도록 보장한다.
                        learningDedupKeys.remove(task.dedupKey)
                    }

                    if (processed % 10 == 0) {
                        yield()
                    }
                }
            } finally {
                learningProcessorRunning.set(false)
                if (learningQueue.isNotEmpty()) {
                    // 실행 중 적재된 작업이 남아있으면 재시작
                    startLearningProcessorIfNeeded()
                }
                if (processed > 0 || failed > 0) {
                    MoneyTalkLogger.i(
                        "[learningQueue] 처리 완료: processed=$processed, failed=$failed, " +
                            "remaining=${learningQueueSize.get()}, dropped=${learningDroppedCount.get()}"
                    )
                }
            }
        }
    }

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
     * @param forceSkipRegexAll true면 Step5 전체에서 regex 생성/검증을 생략하고 direct LLM 경로로만 처리
     * @param onProgress 진행률 콜백
     * @return 결제로 확인되고 파싱 성공한 결과 리스트
     */
    suspend fun classifyUnmatched(
        unmatchedList: List<EmbeddedSms>,
        forceSkipRegexAll: Boolean = false,
        onProgress: ((step: String, current: Int, total: Int) -> Unit)? = null
    ): List<SmsParseResult> {
        if (unmatchedList.isEmpty()) return emptyList()

        val results = mutableListOf<SmsParseResult>()

        // [5-1] 그룹핑 (Level 1~3: 발신번호 → 벡터 유사도 → 소그룹 병합)
        onProgress?.invoke("비슷한 문자 묶는 중...", 0, unmatchedList.size)
        val groups = groupByAddressThenSimilarity(unmatchedList) { current, total ->
            onProgress?.invoke("비슷한 문자 묶는 중...", current, total)
        }

        // [5-1.5] 발신번호 단위로 재집계 → SourceGroup 생성
        val sourceGroups = buildSourceGroups(groups)

        // [5-1.7] DB에서 발신번호별 메인 패턴 선조회
        // 이전 동기화에서 등록된 메인 그룹의 regex를 가져와 예외 그룹 regex 생성 시 참조로 사용
        val uniqueAddresses = sourceGroups.map { it.address }.distinct()
        val dbMainPatterns = uniqueAddresses
            .chunked(MAIN_PATTERN_QUERY_CHUNK_SIZE)
            .flatMap { chunk -> smsPatternDao.getMainPatternsBySenders(chunk) }
            .groupBy { it.senderAddress }
            .mapValues { (_, patterns) -> patterns.first() }

        // [5-2 ~ 5-4] 발신번호 단위로 처리
        // 각 SourceGroup 내에서: 메인 케이스 먼저 → 예외 케이스에 메인 컨텍스트 전달
        onProgress?.invoke("AI가 결제 내역 분석 중...", 0, sourceGroups.size)
        val semaphore = Semaphore(LLM_CONCURRENCY)
        val processedSources = AtomicInteger(0)
        val step5StartTime = System.currentTimeMillis()
        val degradedByBudget = AtomicInteger(0)
        val forcedSkipRegexCount = AtomicInteger(0)
        val outcomeCounts = ConcurrentHashMap<String, AtomicInteger>()

        // Semaphore가 동시성을 제어하므로 chunked 배리어 없이 전체 sourceGroup을 한번에 실행
        // (기존 chunked(20).awaitAll()은 느린 그룹 1개가 나머지 19개를 블로킹하는 문제 발생)
        coroutineScope {
            val allResults = sourceGroups.mapIndexed { index, sourceGroup ->
                async(Dispatchers.IO) {
                    semaphore.acquire()
                    try {
                        // Step5 소프트 예산 초과 → 이 sourceGroup은 regex 생성을 생략하고 빠른 경로로 처리
                        val step5Elapsed = System.currentTimeMillis() - step5StartTime
                        val forceDirectByBudget = step5Elapsed > STEP5_TIME_BUDGET_MS
                        val forceDirectLlm = forceSkipRegexAll || forceDirectByBudget
                        if (forceSkipRegexAll) {
                            forcedSkipRegexCount.addAndGet(sourceGroup.subGroups.sumOf { it.members.size })
                        } else if (forceDirectByBudget) {
                            degradedByBudget.addAndGet(sourceGroup.subGroups.sumOf { it.members.size })
                        }
                        processSourceGroup(
                            sourceGroup = sourceGroup,
                            dbMainPattern = dbMainPatterns[sourceGroup.address],
                            index = index,
                            forceSkipRegex = forceDirectLlm,
                            forceSkipRegexAll = forceSkipRegexAll,
                            onGroupOutcome = { outcome ->
                                if (outcome.isNotBlank()) {
                                    outcomeCounts.computeIfAbsent(outcome) { AtomicInteger(0) }.incrementAndGet()
                                }
                            }
                        )
                    } finally {
                        semaphore.release()
                        val done = processedSources.incrementAndGet()
                        onProgress?.invoke("AI가 결제 내역 분석 중...", done, sourceGroups.size)
                    }
                }
            }.awaitAll()
            results.addAll(allResults.flatten())
        }

        val outcomeSummary = outcomeCounts.entries
            .sortedByDescending { it.value.get() }
            .joinToString(", ") { "${it.key}:${it.value.get()}" }

        val step5TotalElapsed = System.currentTimeMillis() - step5StartTime
        MoneyTalkLogger.e(
            "[Step5 summary] sources=${sourceGroups.size}, parsed=${results.size}건, " +
                "degradedByBudget=${degradedByBudget.get()}건, softBudget=${STEP5_TIME_BUDGET_MS}ms, " +
                "forcedSkipRegex=${forcedSkipRegexCount.get()}건, " +
                "learningQueue=${learningQueueSize.get()}건, dropped=${learningDroppedCount.get()}건, " +
                "outcomes=[$outcomeSummary], elapsed=${step5TotalElapsed}ms"
        )

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
     * 1. 메인 그룹(가장 큰 서브그룹) → processGroup()으로 LLM 호출 (isMainGroup=true)
     * 2. 메인 결과를 MainCaseContext로 저장 (regex 없으면 DB 메인 패턴 fallback)
     * 3. 예외 그룹들 → processGroup(mainContext)로 LLM 호출
     *    - LLM에 "이 번호의 메인 형식은 [KB] 승인 SMS (카드: KB국민)"를 힌트로 전달
     *
     * 서브그룹이 1개면 DB 메인 패턴이 있을 때만 참조 전달.
     *
     * @param dbMainPattern DB에서 조회한 이전 동기화의 메인 그룹 패턴 (있으면 regex 참조로 활용)
     */
    private suspend fun processSourceGroup(
        sourceGroup: SourceGroup,
        dbMainPattern: SmsPatternEntity? = null,
        index : Int = 0,
        forceSkipRegex: Boolean = false,
        forceSkipRegexAll: Boolean = false,
        onGroupOutcome: ((String) -> Unit)? = null
    ): List<SmsParseResult> {
        val results = mutableListOf<SmsParseResult>()
        MoneyTalkLogger.i("[processSourceGroup_$index] 시작: addr=${sourceGroup.address}, subGroups=${sourceGroup.subGroups.size}")

        // DB 메인 패턴에서 MainCaseContext 구성 (현재 세션 메인 결과가 없을 때 fallback)
        val dbMainContext = if (dbMainPattern != null) {
            MainCaseContext(
                cardName = dbMainPattern.parsedCardName,
                template = dbMainPattern.smsTemplate,
                samples = emptyList(),
                isPayment = true,
                amountRegex = dbMainPattern.amountRegex,
                storeRegex = dbMainPattern.storeRegex,
                cardRegex = dbMainPattern.cardRegex
            )
        } else null

        if (sourceGroup.subGroups.size == 1) {
            // 서브그룹 1개 = 메인만 존재
            // DB 메인 패턴이 있으면 regex 참조로 전달 (같은 번호의 이전 메인 형식)
            val singleResult = processGroupWithPolicy(
                sourceGroup.mainGroup,
                mainContext = dbMainContext,
                isMainGroup = true,
                forceSkipRegex = forceSkipRegex,
                forceSkipRegexAll = forceSkipRegexAll,
                onGroupOutcome = onGroupOutcome
            )
            results.addAll(singleResult.results)
            return results
        }


        // Step 1: 메인 그룹 먼저 처리 (isMainGroup=true → DB에 메인 표시)
        val mainProcessResult = processGroupWithPolicy(
            sourceGroup.mainGroup,
            isMainGroup = true,
            forceSkipRegex = forceSkipRegex,
            forceSkipRegexAll = forceSkipRegexAll,
            onGroupOutcome = onGroupOutcome
        )
        results.addAll(mainProcessResult.results)

        // Step 2: 메인 결과로 컨텍스트 생성 (regex 참조 + 샘플 3건 포함)
        val mainSamples = sourceGroup.mainGroup.members
            .take(3)
            .map { it.input.body }
        val mainContext = if (mainProcessResult.results.isNotEmpty()) {
            MainCaseContext(
                cardName = mainProcessResult.results.first().analysis.cardName,
                template = sourceGroup.mainGroup.representative.template,
                samples = mainSamples,
                isPayment = true,
                amountRegex = mainProcessResult.amountRegex,
                storeRegex = mainProcessResult.storeRegex,
                cardRegex = mainProcessResult.cardRegex
            )
        } else {
            // 현재 세션 메인 결과 없음 → DB 메인 fallback
            dbMainContext ?: MainCaseContext(
                cardName = "",
                template = sourceGroup.mainGroup.representative.template,
                samples = mainSamples,
                isPayment = false
            )
        }

        // Step 3: 예외 그룹 처리 (메인 컨텍스트 + 메인 regex 전달)
        for (exceptionGroup in sourceGroup.exceptionGroups) {
            val exResults = processGroupWithPolicy(
                group = exceptionGroup,
                mainContext = mainContext,
                distributionSummary = sourceGroup.buildDistributionSummary(),
                forceSkipRegex = forceSkipRegex,
                forceSkipRegexAll = forceSkipRegexAll,
                onGroupOutcome = onGroupOutcome
            )
            results.addAll(exResults.results)
        }

        MoneyTalkLogger.i("[processSourceGroup_$index] 완료: addr=${sourceGroup.address}, results=${results.size}건")
        return results
    }

    /**
     * Phase 2(D+E-lite) 정책 적용 래퍼
     *
     * D:
     * - unstable + size<=10: regex를 시도하지 않고 direct LLM
     * - unstable + size>10: 1회 re-split 후 하위 그룹 처리
     *
     * E-lite:
     * - forceSkipRegex/unstable direct 경로는 동기화 결과만 저장하고 패턴 학습은 생략
     */
    private suspend fun processGroupWithPolicy(
        group: VectorGroup,
        mainContext: MainCaseContext? = null,
        distributionSummary: String? = null,
        isMainGroup: Boolean = false,
        forceSkipRegex: Boolean = false,
        forceSkipRegexAll: Boolean = false,
        allowResplit: Boolean = true,
        onGroupOutcome: ((String) -> Unit)? = null
    ): GroupProcessResult {
        val stability = evaluateGroupStability(group)
        val addr = group.representative.input.address.takeLast(4)

        if (stability.unstable && group.members.size <= UNSTABLE_DIRECT_LLM_MAX_SIZE) {
            MoneyTalkLogger.w(
                "[unstableGroup] addr=*$addr, members=${group.members.size}, " +
                    "median=${formatSimilarity(stability.medianSimilarity)}, p20=${formatSimilarity(stability.p20Similarity)} " +
                    "→ direct LLM (regex/학습 스킵)"
            )
            return processGroup(
                group = group,
                mainContext = mainContext,
                distributionSummary = distributionSummary,
                isMainGroup = isMainGroup,
                forceSkipRegex = true,
                forceSkipRegexAll = forceSkipRegexAll,
                directLlmOnly = true,
                skipLearning = true,
                onGroupOutcome = onGroupOutcome
            )
        }

        if (allowResplit && stability.unstable) {
            val reSplitGroups = reSplitUnstableGroup(group)
            if (reSplitGroups.size > 1) {
                MoneyTalkLogger.w(
                    "[unstableGroup] addr=*$addr, members=${group.members.size}, " +
                        "median=${formatSimilarity(stability.medianSimilarity)}, p20=${formatSimilarity(stability.p20Similarity)} " +
                        "→ re-split ${reSplitGroups.size}개"
                )

                val mergedResults = mutableListOf<SmsParseResult>()
                var seedRegex: GroupProcessResult? = null

                reSplitGroups.forEachIndexed { idx, subGroup ->
                    val subResult = processGroupWithPolicy(
                        group = subGroup,
                        mainContext = mainContext,
                        distributionSummary = distributionSummary,
                        isMainGroup = isMainGroup && idx == 0,
                        forceSkipRegex = forceSkipRegex,
                        forceSkipRegexAll = forceSkipRegexAll,
                        allowResplit = false,
                        onGroupOutcome = onGroupOutcome
                    )
                    if (idx == 0) {
                        seedRegex = subResult
                    }
                    mergedResults.addAll(subResult.results)
                }

                return GroupProcessResult(
                    results = mergedResults,
                    amountRegex = seedRegex?.amountRegex.orEmpty(),
                    storeRegex = seedRegex?.storeRegex.orEmpty(),
                    cardRegex = seedRegex?.cardRegex.orEmpty(),
                    outcomeCode = seedRegex?.outcomeCode.orEmpty()
                )
            }
        }

        // E-lite: Step5 소프트 예산으로 강등된 경우 학습 생략 (동기화 결과는 유지)
        return processGroup(
            group = group,
            mainContext = mainContext,
            distributionSummary = distributionSummary,
            isMainGroup = isMainGroup,
            forceSkipRegex = forceSkipRegex,
            forceSkipRegexAll = forceSkipRegexAll,
            skipLearning = forceSkipRegex,
            onGroupOutcome = onGroupOutcome
        )
    }

    /**
     * 단일 그룹 처리: LLM 배치 추출 → regex 생성 → 패턴 등록 → 멤버 파싱
     *
     * 처리 흐름:
     * 1. 그룹 대표 SMS 원본(input.body)을 LLM에 전달하여 결제 정보 추출
     *    - mainContext가 있으면 LLM 프롬프트에 메인 케이스 참조 정보 추가
     * 2. 결제 확인 시: regex 생성 (멤버 ≥3건) 또는 템플릿 폴백 regex
     *    - mainContext에 메인 regex가 있으면 참조로 전달하여 정확도 향상
     * 3. 패턴 DB에 등록 (향후 같은 형식 SMS는 Step 4에서 매칭)
     * 4. 등록된 regex로 그룹 전체 멤버의 원본 파싱 → SmsParseResult 생성
     *
     * @param group 처리할 벡터 그룹
     * @param mainContext 메인 케이스 참조 정보 (예외 그룹 처리 시 non-null)
     * @param distributionSummary 발신번호 전체 분포도 요약 (예외 그룹 처리 시 non-null)
     * @param isMainGroup 메인 그룹 여부 (DB 패턴 등록 시 isMainGroup 플래그에 사용)
     * @return 파싱 결과 + 생성된 regex 정보
     */
    private suspend fun processGroup(
        group: VectorGroup,
        mainContext: MainCaseContext? = null,
        distributionSummary: String? = null,
        isMainGroup: Boolean = false,
        forceSkipRegex: Boolean = false,
        forceSkipRegexAll: Boolean = false,
        directLlmOnly: Boolean = false,
        skipLearning: Boolean = false,
        onGroupOutcome: ((String) -> Unit)? = null
    ): GroupProcessResult {
        val groupStartTime = System.currentTimeMillis()
        val results = mutableListOf<SmsParseResult>()
        val representative = group.representative
        val addr = representative.input.address.takeLast(4)
        var outcome = ""  // G: reason code (REGEX_ACCEPTED, REGEX_REPAIRED, TEMPLATE_FALLBACK, DIRECT_LLM, NON_PAYMENT, LLM_FAILED, GROUP_BUDGET_SKIP_REGEX, STEP5_BUDGET_DIRECT_LLM, REGEX_FAILED_FALLBACK_DIRECT_LLM, REGEX_FAILED_HEURISTIC_KB, UNSTABLE_DIRECT_LLM, REGEX_ABORT_LOW_PASS)

        MoneyTalkLogger.i("[processGroup] 시작: addr=*$addr, members=${group.members.size}, isMain=$isMainGroup")

        // --- [5-2] LLM 배치 추출 ---
        // 그룹 멤버 최대 10건을 컨텍스트로 포함하여 LLM이 그룹 패턴을 파악하도록 함
        val groupContextBody = buildGroupContextLlmInput(group.members)
        val smsBodyForLlm = if (mainContext != null && distributionSummary != null) {
            buildContextualLlmInput(groupContextBody, mainContext, distributionSummary)
        } else {
            groupContextBody
        }
        var llmResult: GeminiSmsExtractor.LlmExtractionResult? = null
        var llmAttempts = 0
        while (llmResult == null && llmAttempts <= GROUP_LLM_NULL_RETRY_MAX) {
            llmAttempts++
            llmResult = smsExtractor.extractFromSmsBatch(
                smsMessages = listOf(smsBodyForLlm),
                smsTimestamps = listOf(representative.input.date)
            ).firstOrNull()
            if (llmResult == null && llmAttempts <= GROUP_LLM_NULL_RETRY_MAX) {
                MoneyTalkLogger.w(
                    "[processGroup] LLM 응답 null → 재시도 ${llmAttempts}/${GROUP_LLM_NULL_RETRY_MAX} " +
                        "(${GROUP_LLM_NULL_RETRY_DELAY_MS}ms 후)"
                )
                delay(GROUP_LLM_NULL_RETRY_DELAY_MS)
            }
        }
        if (llmResult == null) {
            if (forceSkipRegexAll) {
                val heuristicResults = group.members.mapNotNull { member ->
                    tryHeuristicParse(member)
                }
                if (heuristicResults.isNotEmpty()) {
                    val elapsed = System.currentTimeMillis() - groupStartTime
                    val fallbackOutcome = "REGEX_FAILED_HEURISTIC_KB"
                    onGroupOutcome?.invoke(fallbackOutcome)
                    MoneyTalkLogger.w(
                        "[processGroup] LLM null → 휴리스틱 보정 채택: addr=*$addr, results=${heuristicResults.size}건, elapsed=${elapsed}ms"
                    )
                    MoneyTalkLogger.e(
                        "[processGroup] 완료: addr=*$addr, outcome=$fallbackOutcome, source=heuristic, results=${heuristicResults.size}건, members=${group.members.size}, elapsed=${elapsed}ms"
                    )
                    return GroupProcessResult(results = heuristicResults, outcomeCode = fallbackOutcome)
                }
            }
            val elapsed = System.currentTimeMillis() - groupStartTime
            val failOutcome = "LLM_FAILED"
            onGroupOutcome?.invoke(failOutcome)
            MoneyTalkLogger.e("[processGroup] 완료: addr=*$addr, outcome=$failOutcome, results=0건, elapsed=${elapsed}ms")
            return GroupProcessResult(emptyList(), outcomeCode = failOutcome)
        }
        MoneyTalkLogger.i(
            "[processGroup] LLM 추출 완료: addr=*$addr, isPayment=${llmResult.isPayment}, " +
                "attempts=$llmAttempts, elapsed=${System.currentTimeMillis() - groupStartTime}ms"
        )

        // 비결제 판정 → 비결제 패턴으로 DB 등록 후 종료
        if (!llmResult.isPayment) {
            MoneyTalkLogger.w(
                "[processGroup] NON_PAYMENT 상세: addr=*$addr, amount=${llmResult.amount}, " +
                    "store=${llmResult.storeName}, card=${llmResult.cardName}, category=${llmResult.category}, " +
                    "date=${llmResult.dateTime}, representative=${representative.input.body.replace("\n", "↵").take(180)}"
            )
            if (!skipLearning) {
                registerNonPaymentPattern(representative)
            } else {
                enqueueDeferredLearning(
                    DeferredLearningTask.NonPayment(
                        embedded = representative,
                        reasonCode = outcome.ifBlank { "NON_PAYMENT" },
                        dedupKey = "NON:${representative.input.id}"
                    )
                )
                MoneyTalkLogger.i("[processGroup] 학습 큐 적재(non-payment): addr=*$addr")
            }
            val elapsed = System.currentTimeMillis() - groupStartTime
            val nonPaymentOutcome = "NON_PAYMENT"
            onGroupOutcome?.invoke(nonPaymentOutcome)
            MoneyTalkLogger.e("[processGroup] 완료: addr=*$addr, outcome=$nonPaymentOutcome, results=0건, elapsed=${elapsed}ms")
            return GroupProcessResult(emptyList(), outcomeCode = nonPaymentOutcome)
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

        // 메인 그룹의 regex 참조 정보 구성 (예외 그룹일 때)
        val mainRegexContext = if (mainContext != null &&
            mainContext.amountRegex.isNotBlank() && mainContext.storeRegex.isNotBlank()
        ) {
            GeminiSmsExtractor.MainRegexContext(
                amountRegex = mainContext.amountRegex,
                storeRegex = mainContext.storeRegex,
                cardRegex = mainContext.cardRegex,
                sampleBody = mainContext.samples.firstOrNull()?.replace("\n", " ")?.take(80) ?: ""
            )
        } else null

        // primary regex 시도 여부 (retry 스킵 판단용)
        var primaryRegexAttempted = false

        // F-lite: LLM 추출 후 시간 예산 체크 — 초과 시 regex 생성 스킵
        val preRegexElapsed = System.currentTimeMillis() - groupStartTime
        val skipRegexByBudget = directLlmOnly || forceSkipRegex || preRegexElapsed > GROUP_TIME_BUDGET_MS
        if (skipRegexByBudget) {
            if (directLlmOnly) {
                MoneyTalkLogger.w("[processGroup] unstable 그룹 정책 → regex 스킵, direct LLM")
                outcome = "UNSTABLE_DIRECT_LLM"
            } else if (forceSkipRegexAll) {
                MoneyTalkLogger.w("[processGroup] regexFailed fallback 모드 → regex 스킵, direct LLM")
                outcome = "REGEX_FAILED_FALLBACK_DIRECT_LLM"
            } else if (forceSkipRegex) {
                MoneyTalkLogger.w("[processGroup] Step5 소프트 예산 모드 → regex 스킵, 추출만")
                outcome = "STEP5_BUDGET_DIRECT_LLM"
            } else {
                MoneyTalkLogger.w("[processGroup] 그룹 시간 예산 초과 (${preRegexElapsed}ms>${GROUP_TIME_BUDGET_MS}ms) → regex 스킵, 추출만")
                outcome = "GROUP_BUDGET_SKIP_REGEX"
            }
        }

        // 멤버가 충분하고 쿨다운이 아니고 시간 예산 내이면 regex 생성 시도
        if (!skipRegexByBudget &&
            group.members.size >= REGEX_MIN_SAMPLES &&
            !shouldSkipRegexGeneration(representative.template)
        ) {
            primaryRegexAttempted = true
            val samples = group.members
                .take(REGEX_SAMPLE_SIZE)
                .map { it.input.body }
            val timestamps = group.members
                .take(REGEX_SAMPLE_SIZE)
                .map { it.input.date }

            val regexResult = smsExtractor.generateRegexForGroup(
                smsBodies = samples,
                smsTimestamps = timestamps,
                mainRegexContext = mainRegexContext
            )

            if (regexResult != null && regexResult.isPayment &&
                regexResult.amountRegex.isNotBlank() && regexResult.storeRegex.isNotBlank()
            ) {
                // regex 검증: 샘플 SMS에 실제 적용하여 파싱 성공률 확인
                val validation = validateRegexAgainstSamples(
                    amountRegex = regexResult.amountRegex,
                    storeRegex = regexResult.storeRegex,
                    cardRegex = regexResult.cardRegex,
                    samples = samples,
                    timestamps = timestamps
                )

                if (validation.passed) {
                    amountRegex = regexResult.amountRegex
                    storeRegex = regexResult.storeRegex
                    cardRegex = regexResult.cardRegex
                    parseSource = "llm_regex"
                    outcome = "REGEX_ACCEPTED"
                    clearRegexFailure(representative.template)
                } else {
                    var abortLowPass = false
                    // near-miss 구간 + 시간 예산 내이면 Classifier repair 1회 시도
                    val regexElapsed = System.currentTimeMillis() - groupStartTime
                    if (validation.passRatio >= REGEX_NEAR_MISS_MIN_RATIO &&
                        regexElapsed <= GROUP_TIME_BUDGET_MS
                    ) {
                        MoneyTalkLogger.w("regex near-miss (${(validation.passRatio * 100).toInt()}%) → Classifier repair 시도 (elapsed=${regexElapsed}ms)")
                        val failedBodies = validation.failedSampleIndices.map { samples[it] }
                        val repaired = smsExtractor.repairRegexFromClassifier(
                            currentRegex = regexResult,
                            allSamples = samples,
                            failedSampleBodies = failedBodies,
                            failedSampleDiagnostics = validation.failedSampleDiagnostics,
                            passRatio = validation.passRatio
                        )
                        if (repaired != null) {
                            val repairValidation = validateRegexAgainstSamples(
                                amountRegex = repaired.amountRegex,
                                storeRegex = repaired.storeRegex,
                                cardRegex = repaired.cardRegex,
                                samples = samples,
                                timestamps = timestamps
                            )
                            if (repairValidation.passed) {
                                MoneyTalkLogger.w("Classifier repair 성공 (${(repairValidation.passRatio * 100).toInt()}%) → regex 채택")
                                amountRegex = repaired.amountRegex
                                storeRegex = repaired.storeRegex
                                cardRegex = repaired.cardRegex
                                parseSource = "llm_regex"
                                outcome = "REGEX_REPAIRED"
                                clearRegexFailure(representative.template)
                            }
                        }
                    } else if (validation.passRatio < REGEX_NEAR_MISS_MIN_RATIO) {
                        // Phase 3-C: 저품질 regex(<60%)는 repair/템플릿 폴백에 시간 쓰지 않고 즉시 direct LLM
                        recordRegexFailure(representative.template)
                        abortLowPass = true
                        outcome = "REGEX_ABORT_LOW_PASS"
                        MoneyTalkLogger.w(
                            "regex abort: passRatio=${(validation.passRatio * 100).toInt()}% < ${(REGEX_NEAR_MISS_MIN_RATIO * 100).toInt()}% → direct LLM 전환"
                        )
                    }

                    if (regexElapsed > GROUP_TIME_BUDGET_MS) {
                        MoneyTalkLogger.w("[processGroup] 시간 예산 초과 (${regexElapsed}ms>${GROUP_TIME_BUDGET_MS}ms) → Classifier repair 스킵")
                    }

                    // repair 미시도/실패/시간초과 → 템플릿 폴백
                    // 단, 저품질 regex abort(<60%)는 템플릿 폴백 없이 direct LLM
                    if (amountRegex.isBlank() && !abortLowPass) {
                        MoneyTalkLogger.w("regex 검증 실패 (${(validation.passRatio * 100).toInt()}%) → 템플릿 폴백 시도")
                        recordRegexFailure(representative.template)
                        val fallback = buildTemplateFallbackRegex(representative.template)
                        if (fallback != null) {
                            amountRegex = fallback.amountRegex
                            storeRegex = fallback.storeRegex
                            cardRegex = fallback.cardRegex
                            parseSource = "template_regex"
                            outcome = "TEMPLATE_FALLBACK"
                        }
                    } else if (abortLowPass) {
                        MoneyTalkLogger.w("regex abort 정책 적용 → 템플릿 폴백 스킵")
                    }
                }
            } else {
                recordRegexFailure(representative.template)
                MoneyTalkLogger.w("regex 생성 실패 → 템플릿 폴백 시도")

                // 템플릿 기반 폴백 regex 시도
                val fallback = buildTemplateFallbackRegex(representative.template)
                if (fallback != null) {
                    amountRegex = fallback.amountRegex
                    storeRegex = fallback.storeRegex
                    cardRegex = fallback.cardRegex
                    parseSource = "template_regex"
                    outcome = "TEMPLATE_FALLBACK"
                }
            }
        } else if (!skipRegexByBudget && group.members.size < REGEX_MIN_SAMPLES) {
            // 멤버 부족 시에도 템플릿 폴백 시도
            val fallback = buildTemplateFallbackRegex(representative.template)
            if (fallback != null) {
                amountRegex = fallback.amountRegex
                storeRegex = fallback.storeRegex
                cardRegex = fallback.cardRegex
                parseSource = "template_regex"
                outcome = "TEMPLATE_FALLBACK"
            }
        }

        // 결제 패턴 DB 등록
        if (!skipLearning) {
            registerPaymentPattern(
                embedded = representative,
                analysis = llmAnalysis,
                source = parseSource,
                amountRegex = amountRegex,
                storeRegex = storeRegex,
                cardRegex = cardRegex,
                isMainGroup = isMainGroup,
                groupMemberCount = group.members.size
            )
            MoneyTalkLogger.i("[processGroup] 패턴 등록 완료: addr=*$addr, source=$parseSource, hasRegex=${amountRegex.isNotBlank()}")
        } else {
            enqueueDeferredLearning(
                DeferredLearningTask.Payment(
                    embedded = representative,
                    analysis = llmAnalysis,
                    source = parseSource,
                    amountRegex = amountRegex,
                    storeRegex = storeRegex,
                    cardRegex = cardRegex,
                    isMainGroup = isMainGroup,
                    groupMemberCount = group.members.size,
                    reasonCode = outcome.ifBlank { "DIRECT_LLM" },
                    dedupKey = "PAY:${representative.input.id}"
                )
            )
            MoneyTalkLogger.i("[processGroup] 학습 큐 적재(payment): addr=*$addr, source=$parseSource")
        }

        // --- [5-4] 그룹 전체 멤버 파싱 ---
        if (amountRegex.isNotBlank() && storeRegex.isNotBlank()) {
            // regex가 있으면 멤버 원본(body)에 적용
            val regexFailedMembers = mutableListOf<EmbeddedSms>()
            for (member in group.members) {
                val parsed = patternMatcher.parseWithRegex(
                    smsBody = member.input.body,
                    smsTimestamp = member.input.date,
                    amountRegex = amountRegex,
                    storeRegex = storeRegex,
                    cardRegex = cardRegex,
                    fallbackCardName = llmAnalysis.cardName.ifBlank { "기타" }
                )
                if (parsed != null && parsed.amount > 0) {
                    results.add(
                        SmsParseResult(
                            input = member.input,
                            analysis = parsed,
                            tier = 3,  // LLM 경로
                            confidence = 1.0f
                        )
                    )
                } else {
                    regexFailedMembers.add(member)
                }
            }

            MoneyTalkLogger.i("[processGroup] 멤버 regex 파싱: addr=*$addr, 성공=${group.members.size - regexFailedMembers.size}건, 실패=${regexFailedMembers.size}건")

            // regex 실패 멤버 → 개별 LLM 호출
            if (regexFailedMembers.isNotEmpty()) {
                MoneyTalkLogger.i("[processGroup] regex 실패 멤버 LLM 폴백 시작: addr=*$addr, ${regexFailedMembers.size}건")
                val failedBodies = regexFailedMembers.map { it.input.body }
                val failedTimestamps = regexFailedMembers.map { it.input.date }
                val failedLlmResults = smsExtractor.extractFromSmsBatch(
                    smsMessages = failedBodies,
                    smsTimestamps = failedTimestamps
                )
                regexFailedMembers.forEachIndexed { index, member ->
                    val memberResult = failedLlmResults.getOrNull(index)
                    if (memberResult == null || !memberResult.isPayment) return@forEachIndexed
                    val parsed = SmsAnalysisResult(
                        amount = memberResult.amount,
                        storeName = memberResult.storeName,
                        category = memberResult.category,
                        dateTime = memberResult.dateTime,
                        cardName = memberResult.cardName
                    )
                    if (parsed.amount > 0) {
                        results.add(
                            SmsParseResult(
                                input = member.input,
                                analysis = parsed,
                                tier = 3,
                                confidence = 1.0f
                            )
                        )
                    }
                }
            }
        } else {
            // regex 미생성 → 대표는 llmAnalysis 재사용, 나머지만 추가 처리
            val limitedMembers = if (directLlmOnly || forceSkipRegex) {
                group.members
            } else {
                group.members.take(LLM_FALLBACK_MAX_SAMPLES)
            }

            // 대표 멤버는 이미 LLM 추출 완료 → llmAnalysis 재사용 (중복 LLM 호출 방지)
            if (llmAnalysis.amount > 0) {
                results.add(
                    SmsParseResult(
                        input = representative.input,
                        analysis = llmAnalysis,
                        tier = 3,
                        confidence = 1.0f
                    )
                )
            } else if (forceSkipRegexAll) {
                // regexFailed fallback 모드에서 대표 LLM이 약할 때 KB 멀티라인 출금 휴리스틱 1회 보정
                val heuristicResult = tryHeuristicParse(
                    embedded = representative,
                    fallbackCardName = llmAnalysis.cardName.ifBlank { "KB" }
                )
                if (heuristicResult != null) {
                    results.add(heuristicResult)
                }
            }

            // 대표 제외 나머지 멤버 (representative는 항상 members[0])
            val remainingMembers = limitedMembers.drop(1)

            if (remainingMembers.isNotEmpty()) {
                // regex 미생성 → 나머지 멤버 개별 LLM 추출
                val skipReason = when {
                    directLlmOnly -> "unstable 그룹 direct LLM"
                    forceSkipRegexAll -> "regexFailed fallback 모드"
                    forceSkipRegex -> "Step5 예산 모드"
                    primaryRegexAttempted -> "regex 실패"
                    skipRegexByBudget -> "그룹 시간 예산 초과"
                    group.members.size < REGEX_MIN_SAMPLES -> "멤버 부족"
                    else -> "regex 미생성"
                }
                MoneyTalkLogger.i("[processGroup] $skipReason → LLM 개별 추출: addr=*$addr, remaining=${remainingMembers.size}")
                val chunks = remainingMembers.chunked(LLM_FALLBACK_MAX_SAMPLES)
                for (chunk in chunks) {
                    val memberBodies = chunk.map { member ->
                        if (mainContext != null && distributionSummary != null) {
                            buildContextualLlmInput(member.input.body, mainContext, distributionSummary)
                        } else {
                            member.input.body
                        }
                    }
                    val memberLlmResults = smsExtractor.extractFromSmsBatch(
                        smsMessages = memberBodies,
                        smsTimestamps = chunk.map { it.input.date }
                    )

                    chunk.forEachIndexed { index, member ->
                        val memberResult = memberLlmResults.getOrNull(index)
                        var added = false
                        if (memberResult != null && memberResult.isPayment) {
                            val parsed = SmsAnalysisResult(
                                amount = memberResult.amount,
                                storeName = memberResult.storeName,
                                category = memberResult.category,
                                dateTime = memberResult.dateTime,
                                cardName = memberResult.cardName
                            )

                            if (parsed.amount > 0) {
                                results.add(
                                    SmsParseResult(
                                        input = member.input,
                                        analysis = parsed,
                                        tier = 3,
                                        confidence = 1.0f
                                    )
                                )
                                added = true
                            }
                        }

                        if (!added && forceSkipRegexAll) {
                            val heuristicResult = tryHeuristicParse(
                                embedded = member,
                                fallbackCardName = llmAnalysis.cardName.ifBlank { "KB" }
                            )
                            if (heuristicResult != null) {
                                results.add(heuristicResult)
                                added = true
                            }
                        }

                        if (!added) return@forEachIndexed
                    }
                }
            }
            // remainingMembers가 비어있으면 (1멤버 그룹) 대표의 llmAnalysis만으로 완료
        }

        val groupElapsed = System.currentTimeMillis() - groupStartTime
        // outcome이 아직 빈 문자열이면 parseSource로 결정
        if (outcome.isEmpty()) {
            outcome = when (parseSource) {
                "llm_regex" -> "REGEX_ACCEPTED"
                "template_regex" -> "TEMPLATE_FALLBACK"
                else -> "DIRECT_LLM"
            }
        }
        onGroupOutcome?.invoke(outcome)
        MoneyTalkLogger.e("[processGroup] 완료: addr=*$addr, outcome=$outcome, source=$parseSource, results=${results.size}건, members=${group.members.size}, elapsed=${groupElapsed}ms")

        return GroupProcessResult(
            results = results,
            amountRegex = amountRegex,
            storeRegex = storeRegex,
            cardRegex = cardRegex,
            outcomeCode = outcome
        )
    }

    /**
     * 그룹 대표 추출용 LLM 입력 구성
     *
     * 동일 유형 SMS 샘플(최대 10건)을 컨텍스트로 포함하여
     * LLM이 그룹 전체 패턴을 파악한 뒤 최적의 결제 정보를 추출하도록 함.
     * 1건만 보내면 이례적 SMS(예: 카드 대금 자동이체)가 대표일 때 cardName 등이 왜곡되는 문제를 방지.
     *
     * @param members 그룹 멤버 전체
     * @return 컨텍스트 포함 SMS body 문자열
     */
    private fun buildGroupContextLlmInput(members: List<EmbeddedSms>): String {
        if (members.size <= 1) return members.first().input.body

        val samples = members.take(GROUP_CONTEXT_MAX_SAMPLES)
        val sampleText = samples.mapIndexed { idx, member ->
            "${idx + 1}. ${member.input.body.replace("\n", " ")}"
        }.joinToString("\n")

        return buildString {
            appendLine("[동일 유형 SMS 목록 (${samples.size}건)]")
            appendLine("아래 SMS들은 동일 발신자의 유사한 형태의 결제 문자입니다.")
            appendLine("한 건의 결제 데이터로 해석하되, 각 데이터를 참조하여 최적의 결제 정보(isPayment, amount, storeName, cardName, dateTime, category)를 추출해주세요.")
            appendLine()
            append(sampleText)
        }
    }

    /**
     * 서브그룹 LLM 호출을 위한 컨텍스트 포함 입력 구성
     *
     * 원본 SMS 본문 앞에 메인 케이스의 참조 정보(카드명, 템플릿, 샘플 3건)와
     * 분포도 요약을 추가. LLM이 메인 케이스를 참조하여 동일한 방식으로 분석하도록 유도.
     *
     * 예:
     * "[참조 정보 - 메인 케이스]
     *  같은 발신번호의 메인 케이스입니다. 아래를 참조하여 비슷한 형태로 분석하세요.
     *  발신번호 15881688 총 85건:
     *    - 서브그룹1 (메인): 80건(94%) | 원본: [KB]02/05 14:30 스타벅스 11,940원 승인
     *    - 서브그룹2: 3건(4%) | 원본: [KB]해외승인 02/05 STARBUCKS USD12.00
     *  메인 케이스 카드: KB국민
     *  메인 템플릿: [KB]{DATE} {TIME} {STORE} {AMOUNT}원 승인
     *  메인 샘플1: [KB]02/05 14:30 스타벅스 11,940원 승인
     *  메인 샘플2: [KB]02/06 09:15 GS25강남점 3,200원 승인
     *  메인 샘플3: [KB]02/07 18:00 쿠팡 45,000원 승인
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
        contextInfo.appendLine("[참조 정보 - 메인 케이스]")
        contextInfo.appendLine("같은 발신번호의 메인 케이스입니다. 아래를 참조하여 비슷한 형태로 분석하세요.")
        contextInfo.appendLine(distributionSummary)
        if (mainContext.isPayment && mainContext.cardName.isNotBlank()) {
            contextInfo.appendLine("메인 케이스 카드: ${mainContext.cardName}")
        }
        if (mainContext.template.isNotBlank()) {
            contextInfo.appendLine("메인 템플릿: ${mainContext.template}")
        }
        mainContext.samples.forEachIndexed { idx, sample ->
            contextInfo.appendLine("메인 샘플${idx + 1}: ${sample.replace("\n", " ").take(100)}")
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
     * Level 3: 소그룹(≤5멤버) → 최대 그룹에 흡수 (유사도 ≥ 0.90)
     *   - 같은 카드사의 변형 SMS (해외승인, ATM출금 등)를 주 그룹에 병합
     *   - 유사도 0.90 미만이면 독립 유지 (완전히 다른 형식 보호)
     *
     * @return 그룹 리스트 (멤버 많은 순 정렬)
     */
    private suspend fun groupByAddressThenSimilarity(
        embeddedSms: List<EmbeddedSms>,
        onProgress: ((current: Int, total: Int) -> Unit)? = null
    ): List<VectorGroup> {
        // Level 1: 발신번호 기준 분류
        val addressGroups = embeddedSms.groupBy { normalizeAddress(it.input.address) }


        val allGroups = mutableListOf<VectorGroup>()
        var processedCount = 0

        for ((_, smsInAddress) in addressGroups) {
            val subGroups = buildSubGroupsByTemplateThenSimilarity(smsInAddress)
            val merged = mergeSmallGroups(subGroups)
            allGroups.addAll(merged)

            processedCount += smsInAddress.size
            onProgress?.invoke(processedCount.coerceAtMost(embeddedSms.size), embeddedSms.size)
        }

        return allGroups.sortedByDescending { it.members.size }
    }

    /**
     * 발신번호 내 서브그룹 구성:
     * 1) 템플릿 기준으로 먼저 시드 그룹 생성 (O(n))
     * 2) 단건 템플릿은 기존 시드 대표와 유사도 매칭 후 병합
     * 3) 남은 단건만 유사도 클러스터링 (최대 SIMILARITY_GROUPING_MAX_SIZE)
     */
    private suspend fun buildSubGroupsByTemplateThenSimilarity(
        smsInAddress: List<EmbeddedSms>
    ): List<VectorGroup> {
        if (smsInAddress.size == 1) {
            return listOf(VectorGroup(smsInAddress[0], mutableListOf(smsInAddress[0])))
        }

        val byTemplate = smsInAddress.groupBy { it.template }
        val seededGroups = mutableListOf<VectorGroup>()
        val singles = mutableListOf<EmbeddedSms>()

        for ((template, members) in byTemplate) {
            if (members.size < TEMPLATE_SEED_MIN_BUCKET_SIZE) {
                singles.add(members.first())
                continue
            }

            val stability = evaluateMembersStability(
                representative = members.first(),
                members = members
            )

            // Phase 3-A: 템플릿 시드라도 cohesion gate를 통과해야 seed 그룹으로 확정
            if (!stability.unstable) {
                seededGroups.add(
                    VectorGroup(
                        representative = members.first(),
                        members = members.toMutableList()
                    )
                )
                continue
            }

            val sampleAddress = members.first().input.address.takeLast(4)
            if (members.size <= UNSTABLE_DIRECT_LLM_MAX_SIZE) {
                // 소규모 unstable 버킷은 seed 우회 금지 → singles로 내려 유사도 그룹핑/정책 분기로 처리
                singles.addAll(members)
                MoneyTalkLogger.w(
                    "[cohesionGate] template unstable -> singles: addr=*$sampleAddress, template=${template.take(40)}, " +
                        "size=${members.size}, median=${formatSimilarity(stability.medianSimilarity)}, p20=${formatSimilarity(stability.p20Similarity)}"
                )
            } else {
                // 대규모 unstable 버킷은 1회 유사도 재분할
                val splitGroups = groupBySimilarityInternal(members)
                if (splitGroups.size > 1) {
                    seededGroups.addAll(splitGroups)
                    MoneyTalkLogger.w(
                        "[cohesionGate] template unstable -> re-split: addr=*$sampleAddress, template=${template.take(40)}, " +
                            "size=${members.size}, split=${splitGroups.size}"
                    )
                } else {
                    singles.addAll(members)
                    MoneyTalkLogger.w(
                        "[cohesionGate] template unstable but unsplittable -> singles: addr=*$sampleAddress, template=${template.take(40)}, " +
                            "size=${members.size}"
                    )
                }
            }
        }

        if (seededGroups.isNotEmpty() && singles.isNotEmpty()) {
            val unresolved = mutableListOf<EmbeddedSms>()
            for (single in singles) {
                var bestGroup: VectorGroup? = null
                var bestSimilarity = 0f
                for (group in seededGroups) {
                    val similarity = patternMatcher.cosineSimilarity(
                        single.embedding,
                        group.representative.embedding
                    )
                    if (similarity > bestSimilarity) {
                        bestSimilarity = similarity
                        bestGroup = group
                    }
                }
                if (bestGroup != null && bestSimilarity >= GROUPING_SIMILARITY) {
                    bestGroup.members.add(single)
                } else {
                    unresolved.add(single)
                }
            }
            singles.clear()
            singles.addAll(unresolved)
        }

        if (singles.isEmpty()) return seededGroups

        if (singles.size > SIMILARITY_GROUPING_MAX_SIZE) {
            val singletonGroups = singles.map { single ->
                VectorGroup(single, mutableListOf(single))
            }
            return seededGroups + singletonGroups
        }

        val similarityGroups = if (singles.size == 1) {
            listOf(VectorGroup(singles.first(), mutableListOf(singles.first())))
        } else {
            groupBySimilarityInternal(singles)
        }

        return seededGroups + similarityGroups
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
     * 그룹 내부 순도(안정도) 평가
     *
     * 대표 임베딩 대비 멤버 유사도 분포를 사용해 unstable 여부를 판단한다.
     * - median < 0.92 또는 p20 < 0.88 이면 unstable
     */
    private fun evaluateGroupStability(group: VectorGroup): GroupStability {
        return evaluateMembersStability(
            representative = group.representative,
            members = group.members
        )
    }

    private fun evaluateMembersStability(
        representative: EmbeddedSms,
        members: List<EmbeddedSms>
    ): GroupStability {
        if (members.size <= 1) {
            return GroupStability(
                unstable = false,
                medianSimilarity = 1.0f,
                p20Similarity = 1.0f
            )
        }

        val representativeEmbedding = representative.embedding
        val similarities = members
            .map { member ->
                patternMatcher.cosineSimilarity(representativeEmbedding, member.embedding)
            }
            .sorted()

        val median = percentile(similarities, 0.5f)
        val p20 = percentile(similarities, 0.2f)
        val unstable = median < UNSTABLE_MEDIAN_MIN_SIMILARITY || p20 < UNSTABLE_P20_MIN_SIMILARITY

        return GroupStability(
            unstable = unstable,
            medianSimilarity = median,
            p20Similarity = p20
        )
    }

    /**
     * unstable 대형 그룹 1회 재분할
     *
     * 템플릿 시드로 섞인 이질 그룹을 유사도 기반으로 다시 분리한다.
     * mergeSmallGroups는 적용하지 않아 재분할 순도를 보존한다.
     */
    private suspend fun reSplitUnstableGroup(group: VectorGroup): List<VectorGroup> {
        if (group.members.size <= 1) return listOf(group)
        val reSplit = groupBySimilarityInternal(group.members)
        return if (reSplit.size > 1) reSplit else listOf(group)
    }

    private fun percentile(sortedValues: List<Float>, percentile: Float): Float {
        if (sortedValues.isEmpty()) return 0f
        val clamped = percentile.coerceIn(0f, 1f)
        val index = (clamped * (sortedValues.size - 1)).toInt()
        return sortedValues[index]
    }

    private fun formatSimilarity(value: Float): String {
        return String.format(Locale.US, "%.3f", value)
    }

    /**
     * 소그룹 병합
     *
     * 같은 발신번호 내 소그룹(멤버 ≤ 5)을 최대 그룹에 흡수.
     * 대표 벡터 간 유사도 ≥ 0.90이면 병합, 미만이면 독립 유지.
     */
    private fun mergeSmallGroups(subGroups: List<VectorGroup>): List<VectorGroup> {
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

    /**
     * 표본 타입 추론
     *
     * sender별 룰 생성 입력을 단순화하기 위해 본문 키워드로 1차 타입을 추론합니다.
     */
    private fun inferSampleType(body: String): String {
        val normalized = body.lowercase(Locale.ROOT)
        return when {
            normalized.contains("취소") -> "cancel"
            normalized.contains("해외") ||
                normalized.contains("usd") ||
                normalized.contains("jpy") ||
                normalized.contains("eur") -> "overseas"
            normalized.contains("입금") || normalized.contains("이체입금") -> "income"
            else -> "expense"
        }
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
        cardRegex: String = "",
        isMainGroup: Boolean = false,
        groupMemberCount: Int = 1
    ) {
        try {
            val pattern = SmsPatternEntity(
                smsTemplate = embedded.template,
                senderAddress = normalizeAddress(embedded.input.address),
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
                },
                isMainGroup = isMainGroup
            )
            smsPatternDao.insert(pattern)

            // RTDB 표본 수집: regex가 검증된 경우만 (원격 룰로 활용 가능한 표본만 수집)
            if (amountRegex.isNotBlank() && storeRegex.isNotBlank()) {
                collectSampleToRtdb(
                    embedded = embedded,
                    cardName = analysis.cardName,
                    parseSource = source,
                    amountRegex = amountRegex,
                    storeRegex = storeRegex,
                    cardRegex = cardRegex,
                    groupMemberCount = groupMemberCount
                )
            }
        } catch (e: Exception) {
            MoneyTalkLogger.e("패턴 등록 실패: ${e.message}")
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
                senderAddress = normalizeAddress(embedded.input.address),
                embedding = embedded.embedding,
                isPayment = false,
                parseSource = "llm"
            )
            smsPatternDao.insert(pattern)
        } catch (e: Exception) {
            MoneyTalkLogger.e("비결제 패턴 등록 실패: ${e.message}")
        }
    }

    // ===== [5-3] RTDB 표본 수집 =====

    /**
     * RTDB에 SMS 성공 샘플 수집
     *
     * 실제 적재 정책은 SmsOriginSampleCollector에서 통합 관리한다.
     */
    private fun collectSampleToRtdb(
        embedded: EmbeddedSms,
        cardName: String,
        parseSource: String,
        amountRegex: String,
        storeRegex: String,
        cardRegex: String,
        groupMemberCount: Int
    ) {
        val normalizedSender = normalizeAddress(embedded.input.address)
        if (normalizedSender.isBlank()) return

        runCatching {
            originSampleCollector.collectSuccess(
                SmsOriginSampleCollector.SuccessSample(
                    senderAddress = embedded.input.address,
                    normalizedSenderAddress = normalizedSender,
                    type = inferSampleType(embedded.input.body),
                    template = embedded.template,
                    originBody = embedded.input.body,
                    maskedBody = maskSmsBody(embedded.input.body),
                    parseSource = parseSource,
                    cardName = cardName,
                    groupMemberCount = groupMemberCount,
                    amountRegex = amountRegex,
                    storeRegex = storeRegex,
                    cardRegex = cardRegex
                )
            )
        }.onFailure { e ->
            MoneyTalkLogger.w("RTDB 성공 표본 수집 예외(무시): ${e.message}")
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
        masked = masked.replace(MASK_CARD_NUM) { match ->
            "*".repeat(match.value.length)
        }
        // 3) 날짜 마스킹
        masked = masked.replace(MASK_DATE) { match ->
            match.value.replace(MASK_SINGLE_DIGIT, "*")
        }
        // 4) 시간 마스킹
        masked = masked.replace(MASK_TIME) { match ->
            match.value.replace(MASK_SINGLE_DIGIT, "*")
        }
        // 5) 금액 마스킹 (쉼표 보존)
        masked = masked.replace(MASK_AMOUNT) { match ->
            match.value.replace(MASK_SINGLE_DIGIT, "*")
        }
        // 6) 남은 숫자 마스킹
        masked = masked.replace(MASK_REMAINING_DIGITS) { match ->
            "*".repeat(match.value.length)
        }

        return masked
    }

    // ===== [5-3] regex 검증 =====

    /**
     * LLM이 생성한 regex를 샘플 SMS에 실제 적용하여 유효성 검증
     *
     * 검증 기준:
     * - 각 샘플에 regex를 적용해 금액/가게명이 추출되는지 확인
     * - 전체 샘플 중 80% 이상 파싱 성공해야 유효 (REGEX_VALIDATION_MIN_PASS_RATIO)
     *
     * LLM hallucination으로 인한 깨진 regex가 DB에 등록되는 것을 방지.
     * near-miss 구간(60%~80%)이면 Classifier repair 시도 대상.
     *
     * @param amountRegex LLM이 생성한 금액 정규식
     * @param storeRegex LLM이 생성한 가게명 정규식
     * @param cardRegex LLM이 생성한 카드명 정규식
     * @param samples 검증에 사용할 SMS 본문 리스트
     * @param timestamps 검증에 사용할 SMS 타임스탬프 리스트
     * @return 검증 상세 결과 (통과 여부 + 성공 비율 + 실패 인덱스)
     */
    private fun validateRegexAgainstSamples(
        amountRegex: String,
        storeRegex: String,
        cardRegex: String,
        samples: List<String>,
        timestamps: List<Long>
    ): RegexValidationDetail {
        if (samples.isEmpty()) return RegexValidationDetail(
            passed = false,
            passRatio = 0f,
            failedSampleIndices = emptyList(),
            failedSampleDiagnostics = emptyList()
        )

        val amountPattern = runCatching { Regex(amountRegex) }.getOrNull()
        val storePattern = runCatching { Regex(storeRegex) }.getOrNull()
        val cardPattern = if (cardRegex.isNotBlank()) runCatching { Regex(cardRegex) }.getOrNull() else null

        var passCount = 0
        val failedIndices = mutableListOf<Int>()
        val failedDiagnostics = mutableListOf<String>()
        for (i in samples.indices) {
            val sampleBody = samples[i]
            val result = patternMatcher.parseWithRegex(
                smsBody = sampleBody,
                smsTimestamp = timestamps.getOrElse(i) { 0L },
                amountRegex = amountRegex,
                storeRegex = storeRegex,
                cardRegex = cardRegex
            )
            if (result != null && result.amount > 0) {
                passCount++
            } else {
                failedIndices.add(i)
                failedDiagnostics.add(
                    diagnoseRegexFailure(
                        sampleBody = sampleBody,
                        amountPattern = amountPattern,
                        storePattern = storePattern,
                        cardPattern = cardPattern
                    )
                )
            }
        }

        val passRatio = passCount.toFloat() / samples.size
        val passed = passRatio >= REGEX_VALIDATION_MIN_PASS_RATIO

        if (!passed) {
            MoneyTalkLogger.w("[validateRegex] 실패: passRatio=${(passRatio * 100).toInt()}% (${passCount}/${samples.size}), threshold=${(REGEX_VALIDATION_MIN_PASS_RATIO * 100).toInt()}%")
            MoneyTalkLogger.w("[validateRegex] amountRegex=[$amountRegex]")
            MoneyTalkLogger.w("[validateRegex] storeRegex=[$storeRegex]")
            failedIndices.forEachIndexed { idx, sampleIdx ->
                MoneyTalkLogger.w("[validateRegex] 실패 sample[$sampleIdx]: ${failedDiagnostics[idx]} | body=${samples[sampleIdx].replace("\n", "↵").take(100)}")
            }
        }

        return RegexValidationDetail(
            passed = passed,
            passRatio = passRatio,
            failedSampleIndices = failedIndices,
            failedSampleDiagnostics = failedDiagnostics
        )
    }

    /**
     * regex 검증 실패 원인을 샘플 단위로 진단.
     * repair 프롬프트에 전달되어 LLM이 실패 포인트를 정확히 수정하도록 돕는다.
     */
    private fun diagnoseRegexFailure(
        sampleBody: String,
        amountPattern: Regex?,
        storePattern: Regex?,
        cardPattern: Regex?
    ): String {
        val reasons = mutableListOf<String>()

        val amountValue = amountPattern?.let { extractGroup1(it, sampleBody) }
        if (amountPattern == null) {
            reasons.add("amountRegex compile failed")
        } else if (amountValue.isNullOrBlank()) {
            reasons.add("amountRegex no match")
        } else {
            val numericAmount = amountValue
                .replace(",", "")
                .replace(Regex("""[^\d]"""), "")
                .toIntOrNull()
            if (numericAmount == null || numericAmount <= 0) {
                reasons.add("amount capture invalid")
            }
        }

        val storeValue = storePattern?.let { extractGroup1(it, sampleBody) }
        if (storePattern == null) {
            reasons.add("storeRegex compile failed")
        } else if (storeValue.isNullOrBlank()) {
            reasons.add("storeRegex no match")
        }

        if (cardPattern != null) {
            val cardValue = extractGroup1(cardPattern, sampleBody)
            if (cardValue.isNullOrBlank()) {
                reasons.add("cardRegex no match")
            }
        }

        if (reasons.isEmpty()) {
            reasons.add("parseWithRegex returned null")
        }
        return reasons.joinToString(", ")
    }

    private fun extractGroup1(regex: Regex, text: String): String? {
        val match = regex.find(text) ?: return null
        return match.groupValues.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }
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
