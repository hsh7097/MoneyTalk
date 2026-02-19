package com.sanha.moneytalk.core.sms2

import android.util.Log
import com.sanha.moneytalk.core.database.dao.SmsPatternDao
import com.sanha.moneytalk.core.database.entity.SmsPatternEntity
import com.sanha.moneytalk.core.model.SmsAnalysisResult
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

/**
 * ===== SMS 패턴 매칭기 (Step 4) =====
 *
 * 역할: 임베딩된 SMS를 DB에 저장된 기존 패턴(SmsPatternEntity)과 벡터 유사도 비교.
 *       매칭 성공 시 해당 패턴에 저장된 LLM regex로 원본 SMS(input.body)를 파싱.
 *
 * 매칭 판정 기준:
 * ┌─────────────────────┬───────────────────────────────────────┐
 * │ 유사도              │ 판정                                   │
 * ├─────────────────────┼───────────────────────────────────────┤
 * │ ≥ 0.97             │ 비결제 패턴 매칭 → 제외 (결제 아님)      │
 * │ ≥ 0.92             │ 결제 확정 + 패턴의 regex로 파싱          │
 * │ < 0.92             │ 미매칭 → Step 5 (그룹핑+LLM)으로        │
 * └─────────────────────┴───────────────────────────────────────┘
 *
 * 파싱 방법:
 * - 패턴에 저장된 LLM regex(amountRegex/storeRegex/cardRegex)로 원본(body) 파싱
 * - regex 파싱 실패 시 패턴의 캐시값(parsedAmount/StoreName 등) 폴백
 * - 그래도 실패 시 미매칭으로 처리 (Step 5로 전달)
 *
 * 순서: SmsPipeline → batchEmbed() → [여기] → SmsGroupClassifier
 *
 * 의존성: SmsPatternDao (DB 패턴 조회), SmsAnalysisResult (결과 모델)
 * ※ core/sms 패키지 미참조 — 벡터 연산/regex 파싱을 자체 구현
 */
@Singleton
class SmsPatternMatcher @Inject constructor(
    private val smsPatternDao: SmsPatternDao
) {

    companion object {
        private const val TAG = "SmsPatternMatcher"

        // ===== 유사도 임계값 (SmsPatternSimilarityPolicy 기준) =====

        /** 비결제 패턴 캐시 히트 임계값 — 이 이상이면 비결제 확정 */
        private const val NON_PAYMENT_THRESHOLD = 0.97f

        /** 결제 패턴 매칭 임계값 — 이 이상이면 기존 regex로 파싱 시도 */
        private const val PAYMENT_MATCH_THRESHOLD = 0.92f
    }

    // ===== 벡터 유사도 연산 =====

    /**
     * 두 벡터 간의 코사인 유사도 계산
     *
     * 코사인 유사도 = (A·B) / (|A| × |B|)
     * 범위: -1 ~ 1 (1에 가까울수록 유사)
     *
     * 3072차원 벡터 간 연산 — O(n) 시간복잡도
     * RandomAccess 체크로 ArrayList 등에서 boxing/unboxing 최적화
     *
     * @param vectorA 첫 번째 벡터
     * @param vectorB 두 번째 벡터
     * @return 코사인 유사도 (-1 ~ 1)
     */
    fun cosineSimilarity(vectorA: List<Float>, vectorB: List<Float>): Float {
        if (vectorA.size != vectorB.size || vectorA.isEmpty()) return 0f

        val a = if (vectorA is java.util.RandomAccess) vectorA else vectorA.toList()
        val b = if (vectorB is java.util.RandomAccess) vectorB else vectorB.toList()
        val size = a.size

        var dotProduct = 0f
        var normA = 0f
        var normB = 0f

        for (i in 0 until size) {
            val ai = a[i]
            val bi = b[i]
            dotProduct += ai * bi
            normA += ai * ai
            normB += bi * bi
        }

        val denominator = sqrt(normA) * sqrt(normB)
        return if (denominator == 0f) 0f else dotProduct / denominator
    }

    /**
     * 패턴 목록에서 가장 유사한 패턴 1개 찾기
     *
     * 모든 패턴과 유사도를 계산하여 최소 임계값 이상 중 최고값 반환.
     * 패턴이 많아질수록 O(n)이므로, 향후 최적화 필요 시 인덱싱 고려.
     *
     * @param queryVector 검색할 임베딩 벡터
     * @param patterns DB에서 조회한 패턴 목록
     * @param minSimilarity 최소 유사도 임계값
     * @return (최고 유사도 패턴, 유사도 점수) 또는 null
     */
    private fun findBestMatch(
        queryVector: List<Float>,
        patterns: List<SmsPatternEntity>,
        minSimilarity: Float
    ): Pair<SmsPatternEntity, Float>? {
        var bestPattern: SmsPatternEntity? = null
        var bestSimilarity = 0f

        for (pattern in patterns) {
            val similarity = cosineSimilarity(queryVector, pattern.embedding)
            if (similarity >= minSimilarity && similarity > bestSimilarity) {
                bestPattern = pattern
                bestSimilarity = similarity
            }
        }

        return bestPattern?.let { it to bestSimilarity }
    }

    // ===== 패턴 매칭 =====

    /**
     * 임베딩된 SMS 리스트를 기존 패턴 DB와 매칭
     *
     * 처리 흐름:
     * 1. DB에서 비결제/결제 패턴 로드 (각각 1회 쿼리)
     * 2. 각 SMS의 임베딩 벡터와 패턴 벡터의 코사인 유사도 비교
     *    → 비결제 패턴 우선 확인 (빠른 제외, ≥0.97)
     *    → 결제 패턴 매칭 (≥0.92)
     * 3. 매칭 성공: 패턴의 regex로 원본(input.body) 파싱
     *    → 파싱 성공: SmsParseResult 생성 → matched
     *    → 파싱 실패: unmatched로 이동 (Step 5에서 재시도)
     * 4. 매칭 실패: unmatched에 추가 (EmbeddedSms 보존 → Step 5에서 그룹핑)
     *
     * @param embeddedSmsList Step 3에서 임베딩 완료된 SMS 리스트
     * @return Pair(매칭+파싱 성공 결과, 미매칭 SMS 리스트)
     */
    suspend fun matchPatterns(
        embeddedSmsList: List<EmbeddedSms>
    ): Pair<List<SmsParseResult>, List<EmbeddedSms>> {
        val matched = mutableListOf<SmsParseResult>()
        val unmatched = mutableListOf<EmbeddedSms>()

        // DB에서 패턴 1회 로드 (매 SMS마다 쿼리하지 않음)
        val nonPaymentPatterns = smsPatternDao.getAllNonPaymentPatterns()
        val paymentPatterns = smsPatternDao.getAllPaymentPatterns()

        Log.d(TAG, "패턴 로드: 결제 ${paymentPatterns.size}건, 비결제 ${nonPaymentPatterns.size}건")

        for (embedded in embeddedSmsList) {
            // --- [1] 비결제 패턴 우선 확인 (빠른 제외) ---
            // 유사도 ≥ 0.97이면 비결제 확정 → 결과에서 완전 제외
            // (matched에도 unmatched에도 넣지 않음 = 파이프라인 결과에서 사라짐)
            val nonPaymentMatch = findBestMatch(
                embedded.embedding, nonPaymentPatterns, NON_PAYMENT_THRESHOLD
            )
            if (nonPaymentMatch != null) {
                Log.d(TAG, "비결제 매칭 (${nonPaymentMatch.second}): ${embedded.input.body.take(30)}")
                // 비결제 패턴 matchCount 갱신
                smsPatternDao.incrementMatchCount(nonPaymentMatch.first.id)
                continue  // 다음 SMS로
            }

            // --- [2] 결제 패턴 매칭 ---
            val paymentMatch = findBestMatch(
                embedded.embedding, paymentPatterns, PAYMENT_MATCH_THRESHOLD
            )

            if (paymentMatch != null) {
                val (pattern, similarity) = paymentMatch

                // regex로 원본(input.body) 파싱 시도
                val analysis = parseWithPatternRegex(
                    body = embedded.input.body,
                    timestamp = embedded.input.date,
                    pattern = pattern
                )

                if (analysis != null) {
                    // 파싱 성공 → SmsParseResult 생성
                    matched.add(
                        SmsParseResult(
                            input = embedded.input,
                            analysis = analysis,
                            tier = 2,  // 벡터매칭
                            confidence = similarity
                        )
                    )
                    // 패턴 사용 빈도 갱신
                    smsPatternDao.incrementMatchCount(pattern.id)
                    Log.d(TAG, "벡터매칭 성공 ($similarity): ${analysis.storeName} ${analysis.amount}원")
                } else {
                    // regex 파싱 실패 → 미매칭으로 전환 (Step 5에서 LLM 재시도)
                    Log.w(TAG, "벡터매칭 OK ($similarity) but 파싱 실패: ${embedded.input.body.take(30)}")
                    unmatched.add(embedded)
                }
            } else {
                // --- [3] 미매칭 (<0.92) → Step 5로 ---
                unmatched.add(embedded)
            }
        }

        Log.d(TAG, "매칭 결과: ${matched.size}건 성공, ${unmatched.size}건 미매칭")
        return matched to unmatched
    }

    // ===== regex 파싱 (self-contained) =====

    /** 문자열 정규식 컴파일 캐시 (재컴파일 비용 절감) */
    private val regexCache = ConcurrentHashMap<String, Regex>()

    /** 숫자 이외 문자 제거용 패턴 */
    private val NON_DIGIT_PATTERN = Regex("""[^\d]""")

    /** 가게명 검증: 숫자/기호만으로 구성된 값 제외 */
    private val STORE_NUMBER_ONLY_PATTERN = Regex("""^[\d,.:/\-\s]+$""")

    /** 가게명 검증: 날짜/시간 형태 제외 */
    private val STORE_DATE_OR_TIME_PATTERN =
        Regex("""^(?:\d{1,2}[/.-]\d{1,2}(?:\s+\d{1,2}:\d{2})?|\d{1,2}:\d{2})$""")

    /** 가게명으로 부적절한 키워드 */
    private val STORE_INVALID_KEYWORDS = listOf(
        "승인", "결제", "출금", "입금", "누적", "잔액", "일시불", "할부", "이용", "카드"
    )

    /** 카드명 검증: 숫자/기호만으로 구성된 값 제외 */
    private val CARD_NUMBER_ONLY_PATTERN = Regex("""^[\d,.:/\-\s]+$""")

    /** 카드명으로 부적절한 키워드 */
    private val CARD_INVALID_KEYWORDS = listOf(
        "web발신", "국외발신", "국제발신", "해외발신", "광고", "안내", "알림"
    )

    /** 날짜/시간 추출 패턴 (MM/DD HH:mm 등) */
    private val DATE_PATTERN = Regex("""(\d{1,2})[/.-](\d{1,2})""")
    private val TIME_PATTERN = Regex("""(\d{1,2}):(\d{2})""")

    /**
     * 패턴에 저장된 regex로 원본 SMS를 파싱하여 SmsAnalysisResult 생성
     *
     * 파싱 전략:
     * 1차: 패턴의 amountRegex/storeRegex/cardRegex로 추출
     * 2차: regex 추출 실패 시 패턴 캐시값(parsedAmount 등) 폴백
     * 3차: 금액이 0 이하이거나 가게명이 없으면 null 반환 (미매칭 처리)
     *
     * @param body 원본 SMS 본문 (SmsInput.body)
     * @param timestamp SMS 수신 시간 (ms)
     * @param pattern 매칭된 SmsPatternEntity
     * @return 파싱 성공 시 SmsAnalysisResult, 실패 시 null
     */
    private fun parseWithPatternRegex(
        body: String,
        timestamp: Long,
        pattern: SmsPatternEntity
    ): SmsAnalysisResult? {
        // regex가 있는 경우에만 regex 파싱 시도
        if (pattern.amountRegex.isNotBlank() && pattern.storeRegex.isNotBlank()) {
            val result = parseWithRegex(
                smsBody = body,
                smsTimestamp = timestamp,
                amountRegex = pattern.amountRegex,
                storeRegex = pattern.storeRegex,
                cardRegex = pattern.cardRegex,
                fallbackAmount = pattern.parsedAmount,
                fallbackStoreName = pattern.parsedStoreName,
                fallbackCardName = pattern.parsedCardName,
                fallbackCategory = pattern.parsedCategory
            )
            if (result != null) return result
        }

        // regex 없거나 파싱 실패 → 패턴 캐시값으로 직접 구성
        if (pattern.parsedAmount > 0 && pattern.parsedStoreName.isNotBlank()) {
            return SmsAnalysisResult(
                amount = pattern.parsedAmount,
                storeName = pattern.parsedStoreName,
                category = pattern.parsedCategory.ifBlank { "기타" },
                dateTime = extractDateTime(body, timestamp),
                cardName = pattern.parsedCardName.ifBlank { "기타" }
            )
        }

        // 모든 폴백 실패
        return null
    }

    /**
     * regex 기반 SMS 파싱
     *
     * amountRegex/storeRegex/cardRegex의 첫 번째 캡처 그룹에서 값 추출.
     * 각 필드별 검증 후, 부적절한 값은 폴백으로 대체.
     *
     * @param smsBody 원본 SMS 본문
     * @param smsTimestamp SMS 수신 시간 (ms)
     * @param amountRegex 금액 추출 정규식 (캡처 그룹 1)
     * @param storeRegex 가게명 추출 정규식 (캡처 그룹 1)
     * @param cardRegex 카드명 추출 정규식 (캡처 그룹 1)
     * @param fallbackAmount 패턴 캐시 금액 (regex 실패 시)
     * @param fallbackStoreName 패턴 캐시 가게명 (regex 실패 시)
     * @param fallbackCardName 패턴 캐시 카드명 (regex 실패 시)
     * @param fallbackCategory 패턴 캐시 카테고리
     * @return 파싱 성공 시 SmsAnalysisResult, 금액 0 이하면 null
     */
    fun parseWithRegex(
        smsBody: String,
        smsTimestamp: Long,
        amountRegex: String,
        storeRegex: String,
        cardRegex: String = "",
        fallbackAmount: Int = 0,
        fallbackStoreName: String = "",
        fallbackCardName: String = "",
        fallbackCategory: String = ""
    ): SmsAnalysisResult? {
        val amountPattern = compileRegex(amountRegex) ?: return null
        val storePattern = compileRegex(storeRegex) ?: return null
        val cardPattern = compileRegex(cardRegex)

        // --- 금액 추출 ---
        val amountFromRegex = extractAmount(amountPattern, smsBody)
        val amount = amountFromRegex ?: fallbackAmount
        if (amount <= 0) return null

        // --- 가게명 추출 ---
        val storeFromRegex = extractGroup1(storePattern, smsBody)
            ?.let(::sanitizeStoreName)
            ?.takeIf(::isValidStoreCandidate)
        val fallbackStore = sanitizeStoreName(fallbackStoreName)
        val parsedStore = storeFromRegex
            ?: fallbackStore.takeIf { it.isNotBlank() }
            ?: return null

        // --- 카드명 추출 ---
        val cardFromRegex = extractGroup1(cardPattern, smsBody)
            ?.trim()
            ?.takeIf(::isValidCardCandidate)
            .orEmpty()
        val parsedCard = when {
            cardFromRegex.isNotBlank() -> cardFromRegex
            fallbackCardName.isNotBlank() -> fallbackCardName
            else -> "기타"
        }

        // --- 카테고리 ---
        val category = when {
            fallbackCategory.isNotBlank() &&
                fallbackStore.isNotBlank() &&
                parsedStore.equals(fallbackStore, ignoreCase = true) -> fallbackCategory
            else -> fallbackCategory.ifBlank { "기타" }
        }

        return SmsAnalysisResult(
            amount = amount,
            storeName = parsedStore,
            category = category,
            dateTime = extractDateTime(smsBody, smsTimestamp),
            cardName = parsedCard
        )
    }

    /**
     * 정규식 문자열을 Regex 객체로 컴파일 (캐시 사용)
     *
     * 동일 정규식의 반복 컴파일 방지 — ConcurrentHashMap으로 스레드 안전.
     * 컴파일 실패 시 null 반환 (잘못된 정규식 무시).
     */
    private fun compileRegex(pattern: String): Regex? {
        if (pattern.isBlank()) return null
        regexCache[pattern]?.let { return it }

        return try {
            val compiled = Regex(pattern)
            regexCache[pattern] = compiled
            compiled
        } catch (e: Exception) {
            Log.w(TAG, "정규식 컴파일 실패: ${pattern.take(80)} (${e.message})")
            null
        }
    }

    /**
     * 금액 추출: regex 캡처 그룹 1에서 숫자만 추출하여 Int 변환
     *
     * 예: "15,000원" 매칭 → group1="15,000" → "15000" → 15000
     */
    private fun extractAmount(regex: Regex, smsBody: String): Int? {
        val raw = extractGroup1(regex, smsBody) ?: return null
        val normalized = raw.replace(NON_DIGIT_PATTERN, "")
        return normalized.toIntOrNull()
    }

    /**
     * regex 첫 번째 캡처 그룹 값 추출
     *
     * @return 캡처 그룹 1의 트림된 값, 없으면 null
     */
    private fun extractGroup1(regex: Regex?, smsBody: String): String? {
        if (regex == null) return null
        val match = regex.find(smsBody) ?: return null
        val value = match.groupValues.getOrNull(1)?.trim().orEmpty()
        return value.takeIf { it.isNotBlank() }
    }

    /** 가게명 정제: 앞뒤 공백 제거 + 최대 20자 */
    private fun sanitizeStoreName(value: String): String {
        return value.trim().take(20)
    }

    /**
     * 가게명 유효성 검증
     *
     * false 조건:
     * - 2자 미만 또는 30자 초과
     * - 플레이스홀더({...}) 포함
     * - 숫자/기호만으로 구성 (금액, 날짜 등)
     * - 날짜/시간 형태
     * - 구조 키워드(승인, 결제, 출금 등) 포함
     */
    private fun isValidStoreCandidate(value: String): Boolean {
        val trimmed = value.trim()
        if (trimmed.length < 2 || trimmed.length > 30) return false
        if (trimmed.contains("{")) return false
        if (STORE_NUMBER_ONLY_PATTERN.matches(trimmed)) return false
        if (STORE_DATE_OR_TIME_PATTERN.matches(trimmed)) return false
        if (STORE_INVALID_KEYWORDS.any { trimmed.contains(it, ignoreCase = true) }) return false
        return true
    }

    /**
     * 카드명 유효성 검증
     *
     * false 조건:
     * - 2자 미만 또는 20자 초과
     * - 숫자/기호만으로 구성
     * - 발신 관련 키워드(web발신, 국외발신 등) 포함
     */
    private fun isValidCardCandidate(value: String): Boolean {
        val trimmed = value.trim()
        if (trimmed.length < 2 || trimmed.length > 20) return false
        if (CARD_NUMBER_ONLY_PATTERN.matches(trimmed)) return false
        val lower = trimmed.lowercase()
        if (CARD_INVALID_KEYWORDS.any { lower.contains(it) }) return false
        return true
    }

    /**
     * SMS 본문에서 날짜/시간 추출
     *
     * 추출 순서:
     * 1. MM/DD (또는 MM-DD, MM.DD) 패턴 → 날짜
     * 2. HH:mm 패턴 → 시간
     * 3. 없으면 SMS 수신 타임스탬프를 포맷
     *
     * @param body SMS 원본 본문
     * @param timestamp SMS 수신 시간 (ms)
     * @return "yyyy-MM-dd HH:mm" 형식 문자열
     */
    private fun extractDateTime(body: String, timestamp: Long): String {
        val calendar = java.util.Calendar.getInstance().apply {
            timeInMillis = timestamp
        }

        // SMS 본문에서 날짜 추출
        val dateMatch = DATE_PATTERN.find(body)
        if (dateMatch != null) {
            val month = dateMatch.groupValues[1].toIntOrNull() ?: (calendar.get(java.util.Calendar.MONTH) + 1)
            val day = dateMatch.groupValues[2].toIntOrNull() ?: calendar.get(java.util.Calendar.DAY_OF_MONTH)
            calendar.set(java.util.Calendar.MONTH, month - 1)
            calendar.set(java.util.Calendar.DAY_OF_MONTH, day)
        }

        // SMS 본문에서 시간 추출
        val timeMatch = TIME_PATTERN.find(body)
        if (timeMatch != null) {
            val hour = timeMatch.groupValues[1].toIntOrNull() ?: calendar.get(java.util.Calendar.HOUR_OF_DAY)
            val minute = timeMatch.groupValues[2].toIntOrNull() ?: calendar.get(java.util.Calendar.MINUTE)
            calendar.set(java.util.Calendar.HOUR_OF_DAY, hour)
            calendar.set(java.util.Calendar.MINUTE, minute)
        }

        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.KOREA)
        return sdf.format(calendar.time)
    }
}
