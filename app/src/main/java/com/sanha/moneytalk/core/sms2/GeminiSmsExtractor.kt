package com.sanha.moneytalk.core.sms2

import android.content.Context
import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import com.google.gson.JsonParser
import com.sanha.moneytalk.R
import com.sanha.moneytalk.core.firebase.GeminiApiKeyProvider
import com.sanha.moneytalk.core.firebase.GeminiModelConfig
import com.sanha.moneytalk.core.model.Category
import com.sanha.moneytalk.core.util.CategoryReferenceProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gemini LLM 기반 SMS 데이터 추출기
 *
 * 정규식으로 파싱이 실패한 결제 문자에서
 * Gemini를 사용하여 금액, 가게명, 카드사 등을 추출합니다.
 *
 * 모델명은 Firebase RTDB에서 원격 관리됩니다 (GeminiModelConfig).
 *
 * 3-tier 시스템의 3번째 단계: Regex → Vector → **LLM 추출**
 */
@Singleton
class GeminiSmsExtractor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val categoryReferenceProvider: CategoryReferenceProvider,
    private val apiKeyProvider: GeminiApiKeyProvider
) {
    companion object {
        private const val TAG = "gemini"

        /** 배치 추출 최대 재시도 */
        private const val BATCH_MAX_RETRIES = 2

        /** 배치 재시도 기본 대기(ms) — 병렬 호출 환경에서 과도한 직렬 대기 방지 */
        private const val BATCH_RETRY_BASE_DELAY_MS = 1000L

        /** 배치 실패 후 개별 폴백 호출 간 최소 대기(ms) */
        private const val FALLBACK_SINGLE_DELAY_MS = 50L

        /** 정규식 생성 재시도(수선) 최대 횟수 (0=수선 비활성, 실패 시 즉시 llm_fallback) */
        private const val REGEX_REPAIR_MAX_RETRIES = 0

        /** 그룹 정규식 생성 시 사용할 최대 샘플 수 */
        private const val REGEX_GROUP_MAX_SAMPLES = 3

        /** 정규식 채택 최소 성공률 (그룹 샘플 기준) */
        private const val REGEX_MIN_SUCCESS_RATIO = 0.8f

        /** 정규식 검증 시 최소 금액 기준 (날짜/시간 오탐 방지) */
        private const val REGEX_MIN_AMOUNT = 100

        /** 정규식 검증용 숫자 외 문자 패턴 */
        private val NON_DIGIT_PATTERN = Regex("""[^\d]""")

        /** 정규식 검증용 가게명 후보 제외 패턴 */
        private val STORE_NUMBER_ONLY_PATTERN = Regex("""^[\d,.:/\-\s]+$""")
        private val STORE_DATE_OR_TIME_PATTERN =
            Regex("""^(?:\d{1,2}[/.-]\d{1,2}(?:\s+\d{1,2}:\d{2})?|\d{1,2}:\d{2})$""")
        private val STORE_CARD_MASK_PATTERN = Regex("""^\d+\*+\d+$""")
        private val STORE_INVALID_KEYWORDS = listOf(
            "승인", "결제", "출금", "입금", "누적", "잔액", "일시불", "할부", "이용", "카드"
        )

        /** 앱에서 사용하는 유효한 카테고리 목록 (미분류 제외) */
        private val VALID_CATEGORIES = Category.entries
            .filter { it != Category.UNCLASSIFIED }
            .map { it.displayName }
            .toSet()

        /**
         * LLM이 반환한 카테고리를 앱의 유효 카테고리로 정규화
         *
         * LLM이 "온라인쇼핑", "편의점", "헬스" 등 앱에 없는 카테고리를 반환할 수 있으므로
         * 매핑 테이블을 통해 교정합니다.
         */
        private val CATEGORY_MAPPING = mapOf(
            // 쇼핑 관련
            "온라인쇼핑" to "쇼핑", "편의점" to "식비", "마트" to "쇼핑",
            "인터넷쇼핑" to "쇼핑", "온라인" to "쇼핑",
            // 의료/건강 관련
            "의료" to "의료/건강", "건강" to "의료/건강", "병원" to "의료/건강",
            "약국" to "의료/건강",
            // 보험 (SmsParser/CategoryClassifierService의 "보험" 카테고리와 일치)
            "보험" to "보험", "보험료" to "보험",
            // 문화/여가 관련
            "문화" to "문화/여가", "여가" to "문화/여가", "여행" to "문화/여가",
            "엔터테인먼트" to "문화/여가", "오락" to "문화/여가", "레저" to "문화/여가",
            // 술/유흥 관련
            "술" to "술/유흥", "유흥" to "술/유흥", "음주" to "술/유흥",
            "바" to "술/유흥", "호프" to "술/유흥",
            // 교통 관련
            "대중교통" to "교통", "택시" to "교통", "주유" to "교통",
            // 운동 관련
            "헬스" to "운동", "피트니스" to "운동", "스포츠" to "운동", "체육" to "운동",
            // 주거 관련
            "부동산" to "주거", "임대" to "주거", "월세" to "주거", "전세" to "주거",
            // 생활 관련
            "공과금" to "생활", "통신" to "생활",
            // 경조 관련
            "경조사" to "경조", "축의금" to "경조", "조의금" to "경조", "부조" to "경조",
            // 계좌이체 관련 ("출금"은 일반 카드 결제에도 쓰이므로 제외)
            "이체" to "계좌이체", "송금" to "계좌이체",
            // 기타 변환
            "미분류" to "기타", "알수없음" to "기타", "불명" to "기타",
            "음식" to "식비", "식사" to "식비",
            "배달음식" to "배달", "배민" to "배달", "요기요" to "배달",
            "커피" to "카페", "디저트" to "카페"
        )

        /**
         * LLM 응답 카테고리를 앱의 유효 카테고리로 변환
         */
        fun normalizeCategory(rawCategory: String): String {
            val trimmed = rawCategory.trim()

            // 이미 유효한 카테고리면 그대로 반환
            if (trimmed in VALID_CATEGORIES) return trimmed

            // 매핑 테이블에서 정확히 찾기
            CATEGORY_MAPPING[trimmed]?.let { return it }

            // 부분 일치: 유효 카테고리 중 포함 관계 확인
            VALID_CATEGORIES.forEach { validCat ->
                if (trimmed.contains(validCat) || validCat.contains(trimmed)) {
                    return validCat
                }
            }

            // 매핑 테이블 키와 부분 일치
            CATEGORY_MAPPING.entries.forEach { (key, value) ->
                if (trimmed.contains(key, ignoreCase = true)) {
                    return value
                }
            }

            Log.w(TAG, "알 수 없는 LLM 카테고리 → 기타로 변환: '$rawCategory'")
            return "기타"
        }
    }

    private var extractorModel: GenerativeModel? = null
    private var batchExtractorModel: GenerativeModel? = null
    private var regexExtractorModel: GenerativeModel? = null
    private var cachedApiKey: String? = null
    private var cachedModelConfig: GeminiModelConfig? = null

    private suspend fun getModel(): GenerativeModel? {
        val apiKey = apiKeyProvider.getApiKey()
        if (apiKey.isBlank()) return null

        val currentModelConfig = apiKeyProvider.modelConfig
        if (extractorModel == null || apiKey != cachedApiKey || currentModelConfig != cachedModelConfig) {
            cachedApiKey = apiKey
            cachedModelConfig = currentModelConfig
            // 모델 설정 변경 시 다른 모델들도 함께 재생성
            extractorModel = null
            batchExtractorModel = null
            regexExtractorModel = null
            extractorModel = GenerativeModel(
                modelName = currentModelConfig.smsExtractor,
                apiKey = apiKey,
                generationConfig = generationConfig {
                    temperature = 0.1f  // 정확한 추출을 위해 낮은 온도
                    // 단건 추출은 응답 길이가 짧아 1024면 충분
                    maxOutputTokens = 1024
                },
                systemInstruction = content { text(context.getString(R.string.prompt_sms_extract_system)) }
            )
        }
        return extractorModel
    }

    /** 배치 추출용 모델 (maxOutputTokens가 더 큼) */
    private suspend fun getBatchModel(): GenerativeModel? {
        val apiKey = apiKeyProvider.getApiKey()
        if (apiKey.isBlank()) return null

        val currentModelConfig = apiKeyProvider.modelConfig
        if (batchExtractorModel == null || apiKey != cachedApiKey || currentModelConfig != cachedModelConfig) {
            cachedApiKey = apiKey
            cachedModelConfig = currentModelConfig
            // 모델 설정 변경 시 다른 모델들도 함께 재생성
            extractorModel = null
            batchExtractorModel = null
            regexExtractorModel = null
            batchExtractorModel = GenerativeModel(
                modelName = currentModelConfig.smsBatchExtractor,
                apiKey = apiKey,
                generationConfig = generationConfig {
                    temperature = 0.1f
                    maxOutputTokens = 4096  // 배치 응답용 확장
                },
                systemInstruction = content { text(context.getString(R.string.prompt_sms_batch_extract_system)) }
            )
        }
        return batchExtractorModel
    }

    /** 정규식 생성용 모델 */
    private suspend fun getRegexModel(): GenerativeModel? {
        val apiKey = apiKeyProvider.getApiKey()
        if (apiKey.isBlank()) return null

        val currentModelConfig = apiKeyProvider.modelConfig
        if (regexExtractorModel == null || apiKey != cachedApiKey || currentModelConfig != cachedModelConfig) {
            cachedApiKey = apiKey
            cachedModelConfig = currentModelConfig
            extractorModel = null
            batchExtractorModel = null
            regexExtractorModel = null
            Log.d(TAG, "[extractRegex] 모델 초기화: ${currentModelConfig.smsRegexExtractor}")
            regexExtractorModel = GenerativeModel(
                modelName = currentModelConfig.smsRegexExtractor,
                apiKey = apiKey,
                generationConfig = generationConfig {
                    temperature = 0.0f
                    // responseSchema 제거: constrained decoding + 정규식 이스케이프 조합이
                    // 내부 토큰 소비를 늘려 MAX_TOKENS를 유발. JSON만 강제하고 구조는 프롬프트로 제어
                    responseMimeType = "application/json"
                    maxOutputTokens = 8192
                },
                systemInstruction = content { text(context.getString(R.string.prompt_sms_regex_extract_system)) }
            )
        }
        return regexExtractorModel
    }

    /**
     * LLM 추출 결과
     */
    data class LlmExtractionResult(
        val isPayment: Boolean = false,
        val amount: Int = 0,
        val storeName: String = "결제",
        val cardName: String = "기타",
        val dateTime: String = "",
        val category: String = "기타"
    )

    /**
     * LLM 정규식 생성 결과
     *
     * 각 정규식은 첫 번째 캡처 그룹으로 값을 추출해야 합니다.
     */
    data class LlmRegexResult(
        val isPayment: Boolean = false,
        val amountRegex: String = "",
        val storeRegex: String = "",
        val cardRegex: String = ""
    )

    private data class RegexValidationResult(
        val isValid: Boolean,
        val reason: String,
        val successRatio: Float = 0f
    )

    private val smsDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA)

    /**
     * SMS에서 결제 정보 추출 (Gemini LLM 사용)
     *
     * @param smsBody SMS 원본 본문
     * @param smsTimestamp SMS 수신 시간 (밀리초). 연도 정보를 LLM에 제공하기 위해 사용
     * @return 추출된 결제 정보, 실패 시 null
     */
    suspend fun extractFromSms(smsBody: String, smsTimestamp: Long = 0L): LlmExtractionResult? =
        withContext(Dispatchers.IO) {
            try {
                val model = getModel()
                if (model == null) {
                    Log.e(TAG, "API 키가 설정되지 않음")
                    return@withContext null
                }

                val dateInfo = if (smsTimestamp > 0) {
                    "\n(SMS 수신 날짜: ${smsDateFormat.format(Date(smsTimestamp))})"
                } else ""

                // 참조 리스트 추가
                val referenceText = try {
                    categoryReferenceProvider.getSmsExtractionReference()
                } catch (e: Exception) {
                    ""
                }

                val prompt = """다음 SMS에서 결제 정보를 추출해주세요:$dateInfo
$referenceText
$smsBody"""

                val startTime = System.currentTimeMillis()
                Log.d(TAG, "[extractSingle] Gemini 단일 LLM 호출 시작: ${smsBody.take(60)}...")
                val response = model.generateContent(prompt)
                val elapsed = System.currentTimeMillis() - startTime
                val responseText = response.text ?: return@withContext null

                Log.d(
                    TAG,
                    "[extractSingle] Gemini 단일 LLM 완료 (${elapsed}ms): ${responseText.take(100)}"
                )

                // JSON 파싱
                parseExtractionResponse(responseText)
            } catch (e: Exception) {
                Log.e(TAG, "LLM 추출 실패: ${e.message}", e)
                null
            }
        }

    /**
     * 결제 SMS용 정규식 생성
     *
     * 반환된 amountRegex/storeRegex/cardRegex는 첫 번째 캡처 그룹에서 값을 읽습니다.
     * 예: amountRegex = "결제금액\\s*([\\d,]+)원"
     */
    suspend fun generateRegexForSms(
        smsBody: String,
        smsTimestamp: Long = 0L
    ): LlmRegexResult? {
        return generateRegexForGroup(
            smsBodies = listOf(smsBody),
            smsTimestamps = listOf(smsTimestamp),
            minSuccessRatio = 1.0f
        )
    }

    /**
     * 같은 그룹 샘플 SMS로 공통 정규식 생성
     *
     * - 샘플(최대 3건) 전체 기준으로 검증
     * - 실패 시 1회 repair(수선) 재요청
     * - 성공률이 minSuccessRatio 미만이면 채택하지 않음
     */
    suspend fun generateRegexForGroup(
        smsBodies: List<String>,
        smsTimestamps: List<Long> = emptyList(),
        minSuccessRatio: Float = REGEX_MIN_SUCCESS_RATIO
    ): LlmRegexResult? = withContext(Dispatchers.IO) {
        try {
            val model = getRegexModel()
            if (model == null) {
                Log.e(TAG, "API 키가 설정되지 않음")
                return@withContext null
            }

            val samples = smsBodies
                .filter { it.isNotBlank() }
                .take(REGEX_GROUP_MAX_SAMPLES)
            if (samples.isEmpty()) return@withContext null

            val startTime = System.currentTimeMillis()
            Log.d(TAG, "[extractRegex] Gemini 정규식 생성 호출 시작: ${samples.size}건 샘플")
            val responseText = requestRegexWithTokenFallback(
                model = model,
                primaryPrompt = buildRegexPrompt(samples, smsTimestamps),
                compactPrompt = buildRegexCompactPrompt(samples),
                ultraCompactPrompt = buildRegexUltraCompactPrompt(samples)
            ) ?: return@withContext null
            val elapsed = System.currentTimeMillis() - startTime
            Log.d(TAG, "[extractRegex] Gemini 정규식 생성 완료 (${elapsed}ms)")

            var candidate = parseRegexResponse(responseText)
            var validation = validateRegexResult(candidate, samples, minSuccessRatio)
            var previousResponseText = responseText
            if (validation.isValid && candidate != null) {
                return@withContext candidate
            }

            // 디버깅: 검증 실패 시 LLM 응답 + 샘플 로깅
            Log.e("MT_DEBUG", "[extractRegex] 검증실패: reason=${validation.reason} | response=${responseText.take(300)}")
            samples.forEachIndexed { i, s ->
                Log.e("MT_DEBUG", "[extractRegex] 샘플${i+1}: ${s.replace("\n", "\\n").take(150)}")
            }

            // 1회 수선(repair) 재요청
            for (attempt in 1..REGEX_REPAIR_MAX_RETRIES) {
                Log.w(
                    TAG,
                    "[extractRegex] 정규식 수선 시도 ${attempt}/${REGEX_REPAIR_MAX_RETRIES} (이유=${validation.reason})"
                )

                val repairResponse = requestRegexWithTokenFallback(
                    model = model,
                    primaryPrompt = buildRegexRepairPrompt(
                        samples = samples,
                        smsTimestamps = smsTimestamps,
                        previousResponse = previousResponseText,
                        failureReason = validation.reason
                    ),
                    compactPrompt = buildRegexCompactPrompt(samples),
                    ultraCompactPrompt = buildRegexUltraCompactPrompt(samples)
                ) ?: continue

                previousResponseText = repairResponse
                candidate = parseRegexResponse(repairResponse)
                validation = validateRegexResult(candidate, samples, minSuccessRatio)
                if (validation.isValid && candidate != null) {
                    return@withContext candidate
                }
            }

            Log.w(
                TAG,
                "[extractRegex] 정규식 생성 최종 실패 (reason=${validation.reason}, ratio=${validation.successRatio})"
            )
            null
        } catch (e: Exception) {
            Log.e(TAG, "정규식 생성 실패: ${e.message}", e)
            null
        }
    }

    /**
     * LLM 응답에서 JSON 파싱
     */
    private fun parseExtractionResponse(response: String): LlmExtractionResult? {
        return try {
            val jsonStr = extractFirstJsonObject(response) ?: return null

            val json = JsonParser.parseString(jsonStr).asJsonObject

            val rawCategory = json.get("category")?.asString ?: "기타"
            val normalizedCategory = normalizeCategory(rawCategory)

            LlmExtractionResult(
                isPayment = json.get("isPayment")?.asBoolean ?: false,
                amount = json.get("amount")?.asInt ?: 0,
                storeName = json.get("storeName")?.asString ?: "결제",
                cardName = json.get("cardName")?.asString ?: "기타",
                dateTime = json.get("dateTime")?.asString ?: "",
                category = normalizedCategory
            )
        } catch (e: Exception) {
            Log.e(TAG, "LLM 응답 파싱 실패: ${e.message}")
            null
        }
    }

    /**
     * LLM 정규식 응답 JSON 파싱
     */
    private fun parseRegexResponse(response: String): LlmRegexResult? {
        return try {
            val jsonStr = extractFirstJsonObject(response) ?: return null

            val json = JsonParser.parseString(jsonStr).asJsonObject
            LlmRegexResult(
                isPayment = json.get("isPayment")?.asBoolean ?: false,
                amountRegex = json.get("amountRegex")?.asString?.trim().orEmpty(),
                storeRegex = json.get("storeRegex")?.asString?.trim().orEmpty(),
                cardRegex = json.get("cardRegex")?.asString?.trim().orEmpty()
            )
        } catch (e: Exception) {
            Log.e(TAG, "정규식 응답 파싱 실패: ${e.message}")
            null
        }
    }

    private fun validateRegexResult(
        result: LlmRegexResult?,
        samples: List<String>,
        minSuccessRatio: Float
    ): RegexValidationResult {
        if (result == null) {
            return RegexValidationResult(isValid = false, reason = "json_parse_failed")
        }
        if (!result.isPayment) {
            return RegexValidationResult(isValid = false, reason = "isPayment_false")
        }
        if (result.amountRegex.isBlank() || result.storeRegex.isBlank()) {
            return RegexValidationResult(isValid = false, reason = "required_regex_blank")
        }

        val amountRegex = try {
            Regex(result.amountRegex)
        } catch (e: Exception) {
            return RegexValidationResult(isValid = false, reason = "amount_regex_compile_failed")
        }
        val storeRegex = try {
            Regex(result.storeRegex)
        } catch (e: Exception) {
            return RegexValidationResult(isValid = false, reason = "store_regex_compile_failed")
        }

        if (result.cardRegex.isNotBlank()) {
            try {
                Regex(result.cardRegex)
            } catch (e: Exception) {
                return RegexValidationResult(isValid = false, reason = "card_regex_compile_failed")
            }
        }

        var successCount = 0
        for (sample in samples) {
            val amountRaw = extractGroup1(amountRegex, sample)
            val storeRaw = extractGroup1(storeRegex, sample)

            val amountNumber = amountRaw?.replace(NON_DIGIT_PATTERN, "")?.toIntOrNull()
            val storeName = storeRaw?.trim()?.takeIf(::isValidRegexStoreName)

            val ok = amountNumber != null && amountNumber >= REGEX_MIN_AMOUNT && storeName != null
            if (ok) successCount++
        }

        val ratio = successCount.toFloat() / samples.size.toFloat()
        if (ratio >= minSuccessRatio) {
            return RegexValidationResult(isValid = true, reason = "ok", successRatio = ratio)
        }
        return RegexValidationResult(
            isValid = false,
            reason = "sample_match_ratio_${String.format("%.2f", ratio)}",
            successRatio = ratio
        )
    }

    private fun buildRegexPrompt(
        samples: List<String>,
        smsTimestamps: List<Long>
    ): String {
        val sampleText = samples.mapIndexed { index, body ->
            val dateInfo = smsTimestamps.getOrNull(index)?.let { ts ->
                if (ts > 0) "${smsDateFormat.format(Date(ts))} " else ""
            } ?: ""
            "${index + 1}) ${dateInfo}${toCompactRegexSample(body, 180)}"
        }.joinToString("\n")

        return """같은 형식의 결제 SMS 샘플입니다. 공통 정규식을 JSON으로만 반환하세요.
필드: isPayment, amountRegex, storeRegex, cardRegex
조건: amountRegex/storeRegex는 group1 캡처 필수.
샘플:
$sampleText"""
    }

    /**
     * MAX_TOKENS 발생 시 축약 프롬프트로 1회 재시도
     */
    private suspend fun requestRegexWithTokenFallback(
        model: GenerativeModel,
        primaryPrompt: String,
        compactPrompt: String,
        @Suppress("UNUSED_PARAMETER") ultraCompactPrompt: String
    ): String? {
        // 토큰 디버깅: 프롬프트 길이 + countTokens
        try {
            val tokenCount = model.countTokens(primaryPrompt)
            Log.e("MT_DEBUG", "[extractRegex] 토큰정보: inputTokens=${tokenCount.totalTokens}, promptLen=${primaryPrompt.length}자, maxOutput=8192")
        } catch (e: Exception) {
            Log.e("MT_DEBUG", "[extractRegex] countTokens 실패: ${e.message}, promptLen=${primaryPrompt.length}자")
        }

        return try {
            val response = model.generateContent(primaryPrompt)
            val text = response.text
            Log.e("MT_DEBUG", "[extractRegex] 응답 성공: responseLen=${text?.length ?: 0}자, candidates=${response.candidates.size}")
            text
        } catch (e: Exception) {
            if (!isMaxTokensError(e)) throw e
            Log.w(TAG, "[extractRegex] MAX_TOKENS 발생 (primaryPromptLen=${primaryPrompt.length}자) → 축약 프롬프트 재시도")

            try {
                val compactTokenCount = model.countTokens(compactPrompt)
                Log.e("MT_DEBUG", "[extractRegex] compact 토큰정보: inputTokens=${compactTokenCount.totalTokens}, promptLen=${compactPrompt.length}자")
            } catch (te: Exception) {
                Log.e("MT_DEBUG", "[extractRegex] compact countTokens 실패: ${te.message}")
            }

            try {
                val response2 = model.generateContent(compactPrompt)
                val text2 = response2.text
                Log.e("MT_DEBUG", "[extractRegex] compact 응답 성공: responseLen=${text2?.length ?: 0}자")
                text2
            } catch (e2: Exception) {
                if (isMaxTokensError(e2)) {
                    Log.w(TAG, "[extractRegex] MAX_TOKENS 재발생 (compactPromptLen=${compactPrompt.length}자) → 즉시 포기")
                    return null
                }
                throw e2
            }
        }
    }

    private fun isMaxTokensError(e: Exception): Boolean {
        val message = e.message.orEmpty()
        return message.contains("MAX_TOKENS", ignoreCase = true)
    }

    private fun buildRegexCompactPrompt(samples: List<String>): String {
        val compactSamples = samples.mapIndexed { index, body ->
            val sliced = toCompactRegexSample(body, 140)
            "${index + 1}: $sliced"
        }.joinToString("\n")

        return """결제 SMS 샘플 공통 정규식 JSON만 반환.
필드: isPayment, amountRegex, storeRegex, cardRegex
제약: amountRegex/storeRegex는 group1 필수.
샘플:
$compactSamples"""
    }

    private fun buildRegexUltraCompactPrompt(samples: List<String>): String {
        val shortest = samples
            .map { toCompactRegexSample(it, 120) }
            .minByOrNull { it.length }
            .orEmpty()

        return """JSON만:
{"isPayment":true/false,"amountRegex":"","storeRegex":"","cardRegex":""}
규칙: 결제면 amountRegex/storeRegex group1 필수.
샘플: $shortest"""
    }

    private fun buildRegexRepairPrompt(
        samples: List<String>,
        smsTimestamps: List<Long>,
        previousResponse: String,
        failureReason: String
    ): String {
        val sampleText = samples.mapIndexed { index, body ->
            val dateInfo = smsTimestamps.getOrNull(index)?.let { ts ->
                if (ts > 0) "${smsDateFormat.format(Date(ts))} " else ""
            } ?: ""
            "${index + 1}) ${dateInfo}${toCompactRegexSample(body, 180)}"
        }.joinToString("\n")
        val previousShort = previousResponse.replace("\n", " ").take(240)

        return """이전 정규식 응답이 검증에 실패했습니다.
실패 이유: $failureReason, 이전 응답: $previousShort
다음 샘플 전체를 만족하도록 수정하세요.
$sampleText

반드시 JSON만 반환하세요."""
    }

    private fun toCompactRegexSample(text: String, maxLen: Int): String {
        val oneLine = text
            .replace("\n", "\\n")
            .replace(Regex("""\d{1,2}[/.-]\d{1,2}"""), "{DATE}")
            .replace(Regex("""\d{1,2}:\d{2}"""), "{TIME}")
            .replace(Regex("""\d+\*+\d+"""), "{CARD_NUM}")
            .replace(Regex("""[\d,]+원"""), "{AMOUNT}원")
            .replace(Regex("""\b[\d,]{3,}\b"""), "{NUM}")

        return if (oneLine.length > maxLen) oneLine.take(maxLen) else oneLine
    }

    private fun tryExtractGroup1(pattern: String, text: String): String? {
        return try {
            val regex = Regex(pattern)
            extractGroup1(regex, text)
        } catch (e: Exception) {
            null
        }
    }

    private fun isValidRegexStoreName(value: String): Boolean {
        val trimmed = value.trim()
        if (trimmed.length < 2 || trimmed.length > 30) return false
        if (trimmed.contains("{")) return false
        if (STORE_NUMBER_ONLY_PATTERN.matches(trimmed)) return false
        if (STORE_DATE_OR_TIME_PATTERN.matches(trimmed)) return false
        if (STORE_CARD_MASK_PATTERN.matches(trimmed)) return false
        if (STORE_INVALID_KEYWORDS.any { keyword -> trimmed.contains(keyword, ignoreCase = true) }) {
            return false
        }
        return true
    }

    private fun extractGroup1(regex: Regex, text: String): String? {
        val match = regex.find(text) ?: return null
        val value = match.groupValues.getOrNull(1)?.trim().orEmpty()
        return value.takeIf { it.isNotBlank() }
    }

    /**
     * 응답 텍스트에서 첫 번째 JSON 객체를 안전하게 추출
     * - 코드블록/설명문이 섞여 있어도 파싱 가능
     */
    private fun extractFirstJsonObject(response: String): String? {
        val text = stripCodeFence(response)
        val start = text.indexOf('{')
        if (start < 0) return null

        var depth = 0
        var inString = false
        var escaped = false

        for (i in start until text.length) {
            val ch = text[i]
            if (inString) {
                if (escaped) {
                    escaped = false
                } else if (ch == '\\') {
                    escaped = true
                } else if (ch == '"') {
                    inString = false
                }
                continue
            }

            when (ch) {
                '"' -> inString = true
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return text.substring(start, i + 1)
                }
            }
        }
        return null
    }

    /**
     * 응답 텍스트에서 첫 번째 JSON 배열을 안전하게 추출
     * - 배치 추출에서 모델이 불필요한 안내문을 섞을 때 대응
     */
    private fun extractFirstJsonArray(response: String): String? {
        val text = stripCodeFence(response)
        val start = when {
            text.contains("[{") -> text.indexOf("[{")
            text.contains("[\n{") -> text.indexOf("[\n{")
            else -> text.indexOf('[')
        }
        if (start < 0) return null

        var depth = 0
        var inString = false
        var escaped = false

        for (i in start until text.length) {
            val ch = text[i]
            if (inString) {
                if (escaped) {
                    escaped = false
                } else if (ch == '\\') {
                    escaped = true
                } else if (ch == '"') {
                    inString = false
                }
                continue
            }

            when (ch) {
                '"' -> inString = true
                '[' -> depth++
                ']' -> {
                    depth--
                    if (depth == 0) return text.substring(start, i + 1)
                }
            }
        }
        return null
    }

    private fun stripCodeFence(response: String): String {
        return response
            .replace(Regex("```json\\s*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("```\\s*"), "")
            .trim()
    }

    /**
     * 여러 SMS를 한번에 추출 (배치 LLM 호출)
     *
     * 최대 20개 SMS를 하나의 프롬프트에 번호를 매겨 전송하고,
     * JSON 배열로 일괄 응답을 받아 파싱합니다.
     *
     * @param smsMessages SMS 본문 목록
     * @param smsTimestamps 각 SMS의 수신 시간 목록 (밀리초). smsMessages와 같은 크기. 연도 정보 제공용.
     * @return 각 SMS의 추출 결과 (순서 보장, 실패 시 null)
     */
    suspend fun extractFromSmsBatch(
        smsMessages: List<String>,
        smsTimestamps: List<Long> = emptyList()
    ): List<LlmExtractionResult?> = withContext(Dispatchers.IO) {
        if (smsMessages.isEmpty()) return@withContext emptyList()

        // 1건이면 단일 추출 사용
        if (smsMessages.size == 1) {
            val ts = smsTimestamps.firstOrNull() ?: 0L
            return@withContext listOf(extractFromSms(smsMessages[0], ts))
        }

        try {
            val model = getBatchModel()
            if (model == null) {
                Log.e(TAG, "API 키가 설정되지 않음")
                return@withContext smsMessages.map { null }
            }

            // 번호를 매겨서 프롬프트 구성 (수신 날짜 포함)
            val smsListText = smsMessages.mapIndexed { idx, body ->
                val dateInfo = smsTimestamps.getOrNull(idx)?.let { ts ->
                    if (ts > 0) " (수신: ${smsDateFormat.format(Date(ts))})" else ""
                } ?: ""
                "${idx + 1}번$dateInfo: $body"
            }.joinToString("\n\n")

            // 참조 리스트 추가
            val referenceText = try {
                categoryReferenceProvider.getSmsExtractionReference()
            } catch (e: Exception) {
                ""
            }

            val prompt = """다음 ${smsMessages.size}개 SMS에서 각각 결제 정보를 추출해주세요:
$referenceText

$smsListText"""

            Log.d(TAG, "[extractBatch] LLM 배치 추출 요청: ${smsMessages.size}건")

            // 재시도 로직
            for (attempt in 0 until BATCH_MAX_RETRIES) {
                try {
                    val startTime = System.currentTimeMillis()
                    Log.d(
                        TAG,
                        "[extractBatch] Gemini 배치 LLM 호출 시작 (시도 ${attempt + 1}/${BATCH_MAX_RETRIES}, ${smsMessages.size}건)"
                    )
                    val response = model.generateContent(prompt)
                    val elapsed = System.currentTimeMillis() - startTime
                    val responseText = response.text
                    if (responseText == null) {
                        Log.e(TAG, "[extractBatch] LLM 배치 응답 없음 (${elapsed}ms, 시도 ${attempt + 1})")
                        continue
                    }

                    Log.d(
                        TAG,
                        "[extractBatch] LLM 배치 응답 수신 (${elapsed}ms, ${responseText.length}자)"
                    )

                    val parsed = parseBatchExtractionResponse(responseText, smsMessages.size)
                    if (parsed != null) {
                        Log.d(
                            TAG,
                            "[extractBatch] LLM 배치 추출 성공: ${parsed.count { it != null }}/${smsMessages.size}건 파싱"
                        )
                        return@withContext parsed
                    }
                } catch (e: Exception) {
                    val isRateLimit = e.message?.contains("429") == true ||
                            e.message?.contains("RESOURCE_EXHAUSTED") == true
                    if (isRateLimit && attempt < BATCH_MAX_RETRIES - 1) {
                        val retryDelayMs = BATCH_RETRY_BASE_DELAY_MS * (attempt + 1)
                        Log.w(
                            TAG,
                            "[extractBatch] ⚠️ 429 Rate Limit 발생! (GeminiSmsExtractor.extractFromSmsBatch) ${retryDelayMs}ms 후 재시도"
                        )
                        delay(retryDelayMs)
                        continue
                    }
                    Log.e(TAG, "[extractBatch] LLM 배치 추출 실패 (시도 ${attempt + 1}): ${e.message}")
                }
            }

            // 배치 실패 시 개별 추출로 폴백
            Log.w(TAG, "[extractBatch] LLM 배치 실패, 개별 추출로 폴백 (${smsMessages.size}건)")
            smsMessages.mapIndexed { idx, sms ->
                try {
                    val ts = smsTimestamps.getOrNull(idx) ?: 0L
                    val startTime = System.currentTimeMillis()
                    Log.d(
                        TAG,
                        "[extractBatch→fallback] 개별 LLM ${idx + 1}/${smsMessages.size}: ${
                            sms.take(40)
                        }..."
                    )
                    val result = extractFromSms(sms, ts)
                    val elapsed = System.currentTimeMillis() - startTime
                    Log.d(
                        TAG,
                        "[extractBatch→fallback] 개별 LLM ${idx + 1}/${smsMessages.size} 완료 (${elapsed}ms): isPayment=${result?.isPayment}"
                    )
                    if (idx < smsMessages.lastIndex) {
                        delay(FALLBACK_SINGLE_DELAY_MS)
                    }
                    result
                } catch (e: Exception) {
                    Log.e(TAG, "[extractBatch→fallback] 개별 LLM ${idx + 1} 실패: ${e.message}")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "LLM 배치 추출 전체 실패: ${e.message}", e)
            smsMessages.map { null }
        }
    }

    /**
     * 배치 LLM 응답에서 JSON 배열 파싱
     *
     * @param response LLM 응답 텍스트 (JSON 배열)
     * @param expectedSize 예상 결과 수
     * @return 각 SMS의 추출 결과 리스트 (순서 보장)
     */
    private fun parseBatchExtractionResponse(
        response: String,
        expectedSize: Int
    ): List<LlmExtractionResult?>? {
        return try {
            val jsonStr = extractFirstJsonArray(response) ?: return null

            val jsonArray = JsonParser.parseString(jsonStr).asJsonArray

            // no 필드 기반으로 결과 매핑 (1-indexed)
            val resultMap = mutableMapOf<Int, LlmExtractionResult>()

            for (element in jsonArray) {
                try {
                    val json = element.asJsonObject
                    val no = json.get("no")?.asInt ?: continue

                    val batchRawCategory = json.get("category")?.asString ?: "기타"
                    val batchNormalizedCategory = normalizeCategory(batchRawCategory)

                    val result = LlmExtractionResult(
                        isPayment = json.get("isPayment")?.asBoolean ?: false,
                        amount = json.get("amount")?.asInt ?: 0,
                        storeName = json.get("storeName")?.asString ?: "결제",
                        cardName = json.get("cardName")?.asString ?: "기타",
                        dateTime = try {
                            json.get("dateTime")?.asString ?: ""
                        } catch (e: Exception) {
                            ""
                        },
                        category = batchNormalizedCategory
                    )

                    resultMap[no] = result
                } catch (e: Exception) {
                    Log.w(TAG, "배치 응답 항목 파싱 실패: ${e.message}")
                }
            }

            // 1-indexed no를 0-indexed 리스트로 변환
            val results = (1..expectedSize).map { no ->
                resultMap[no]
            }

            // 절반 이상 파싱 성공해야 유효한 결과
            val parsedCount = results.count { it != null }
            if (parsedCount < expectedSize / 2) {
                Log.w(TAG, "배치 파싱 결과가 너무 적음: $parsedCount/$expectedSize")
                return null
            }

            results
        } catch (e: Exception) {
            Log.e(TAG, "배치 응답 JSON 배열 파싱 실패: ${e.message}")
            null
        }
    }

    /**
     * API 키 변경 시 모델 초기화
     */
    fun resetModel() {
        extractorModel = null
        batchExtractorModel = null
        regexExtractorModel = null
    }
}
