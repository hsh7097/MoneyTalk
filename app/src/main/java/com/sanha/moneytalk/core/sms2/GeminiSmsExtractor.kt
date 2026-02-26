package com.sanha.moneytalk.core.sms2

import com.sanha.moneytalk.core.util.MoneyTalkLogger

import android.content.Context
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import com.google.gson.JsonObject
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

        /** 배치 추출 최대 재시도 */
        private const val BATCH_MAX_RETRIES = 2

        /** 배치 재시도 기본 대기(ms) — 병렬 호출 환경에서 과도한 직렬 대기 방지 */
        private const val BATCH_RETRY_BASE_DELAY_MS = 1000L

        /** 배치 실패 후 개별 폴백 호출 간 최소 대기(ms) */
        private const val FALLBACK_SINGLE_DELAY_MS = 50L

        /** 정규식 생성 재시도(수선) 최대 횟수 */
        private const val REGEX_REPAIR_MAX_RETRIES = 1

        /** 그룹 정규식 생성 시 사용할 최대 샘플 수 */
        private const val REGEX_GROUP_MAX_SAMPLES = 10

        /** LLM 응답에서 코드 펜스 제거용 정규식 (사전 컴파일) */
        private val CODE_FENCE_JSON = Regex("```json\\s*", RegexOption.IGNORE_CASE)
        private val CODE_FENCE = Regex("```\\s*")

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

            MoneyTalkLogger.w("알 수 없는 LLM 카테고리 → 기타로 변환: '$rawCategory'")
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

    /**
     * 메인 그룹 정규식 참조 정보
     *
     * 예외 그룹 정규식 생성 시, 메인 그룹의 정규식과 원본 샘플을 참조로 전달하여
     * LLM이 "메인 형식과 어떻게 다른지"에 집중하도록 함.
     *
     * @property amountRegex 메인 그룹의 금액 정규식
     * @property storeRegex 메인 그룹의 가게명 정규식
     * @property cardRegex 메인 그룹의 카드명 정규식
     * @property sampleBody 메인 그룹의 원본 SMS 샘플 (1건)
     */
    data class MainRegexContext(
        val amountRegex: String,
        val storeRegex: String,
        val cardRegex: String,
        val sampleBody: String
    )

    private data class RegexValidationResult(
        val isValid: Boolean,
        val reason: String
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
                    MoneyTalkLogger.e("API 키가 설정되지 않음")
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

                val prompt = context.getString(
                    R.string.prompt_sms_extract_user,
                    dateInfo,
                    referenceText,
                    smsBody
                )

                val startTime = System.currentTimeMillis()
                val response = model.generateContent(prompt)
                val elapsed = System.currentTimeMillis() - startTime
                val responseText = response.text ?: return@withContext null


                // JSON 파싱
                parseExtractionResponse(responseText)
            } catch (e: Exception) {
                MoneyTalkLogger.e("LLM 추출 실패: ${e.message}", e)
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
            smsTimestamps = listOf(smsTimestamp)
        )
    }

    /**
     * 같은 그룹 샘플 SMS로 공통 정규식 생성
     *
     * - 샘플(최대 10건)로 LLM 정규식 생성
     * - 실패 시 1회 repair(수선) 재요청
     * - JSON 파싱 + regex 컴파일 + 필수필드만 검증 (샘플 성공률은 Classifier에서 검증)
     *
     * @param mainRegexContext 메인 그룹의 정규식 참조 (예외 그룹 처리 시 non-null).
     *        같은 발신번호의 메인 형식 정규식을 참조하여 정확도를 높임.
     */
    @Suppress("UNUSED_PARAMETER")
    suspend fun generateRegexForGroup(
        smsBodies: List<String>,
        smsTimestamps: List<Long> = emptyList(),
        mainRegexContext: MainRegexContext? = null
    ): LlmRegexResult? = withContext(Dispatchers.IO) {
        try {
            val model = getRegexModel()
            if (model == null) {
                MoneyTalkLogger.e("API 키가 설정되지 않음")
                return@withContext null
            }

            val samples = smsBodies
                .filter { it.isNotBlank() }
                .take(REGEX_GROUP_MAX_SAMPLES)
            if (samples.isEmpty()) return@withContext null

            val startTime = System.currentTimeMillis()
            val responseText = requestRegexWithTokenFallback(
                model = model,
                primaryPrompt = buildRegexPrompt(samples, mainRegexContext),
                compactPrompt = buildRegexCompactPrompt(samples, mainRegexContext),
                ultraCompactPrompt = buildRegexUltraCompactPrompt(samples)
            ) ?: return@withContext null
            val elapsed = System.currentTimeMillis() - startTime

            var candidate = parseRegexResponse(responseText)
            var validation = validateRegexResult(candidate)
            var previousResponseText = responseText
            if (validation.isValid && candidate != null) {
                return@withContext candidate
            }

            // 디버깅: 검증 실패 시 LLM 응답 + 샘플 로깅
            samples.forEachIndexed { i, s ->
            }

            // 1회 수선(repair) 재요청
            for (attempt in 1..REGEX_REPAIR_MAX_RETRIES) {
                MoneyTalkLogger.w("[extractRegex] 정규식 수선 시도 ${attempt}/${REGEX_REPAIR_MAX_RETRIES} (이유=${validation.reason})"
                )

                val repairResponse = requestRegexWithTokenFallback(
                    model = model,
                    primaryPrompt = buildRegexRepairPrompt(
                        samples = samples,
                        previousResponse = previousResponseText,
                        failureReason = validation.reason
                    ),
                    compactPrompt = buildRegexCompactPrompt(samples),
                    ultraCompactPrompt = buildRegexUltraCompactPrompt(samples)
                ) ?: continue

                previousResponseText = repairResponse
                candidate = parseRegexResponse(repairResponse)
                validation = validateRegexResult(candidate)
                if (validation.isValid && candidate != null) {
                    return@withContext candidate
                }
            }

            MoneyTalkLogger.w("[extractRegex] 정규식 생성 최종 실패 (reason=${validation.reason})")
            null
        } catch (e: Exception) {
            MoneyTalkLogger.e("정규식 생성 실패: ${e.message}", e)
            null
        }
    }

    /**
     * Classifier에서 호출하는 regex repair (near-miss 구간용)
     *
     * SmsGroupClassifier.validateRegexAgainstSamples() 실패 시,
     * 실패한 샘플 정보를 포함하여 repair 프롬프트를 구성하고 LLM에 1회 수선 요청.
     *
     * @param currentRegex 현재 검증 실패한 regex 결과
     * @param allSamples 전체 샘플 SMS 본문
     * @param failedSampleBodies 파싱 실패한 샘플 SMS 본문 (최대 5건)
     * @param failedSampleDiagnostics 파싱 실패 사유 (failedSampleBodies와 동일 순서)
     * @param passRatio 현재 파싱 성공 비율
     * @return 수선된 regex 결과 (형식 검증 통과 시), null이면 수선 실패
     */
    suspend fun repairRegexFromClassifier(
        currentRegex: LlmRegexResult,
        allSamples: List<String>,
        failedSampleBodies: List<String>,
        failedSampleDiagnostics: List<String>,
        passRatio: Float
    ): LlmRegexResult? = withContext(Dispatchers.IO) {
        try {
            val model = getRegexModel() ?: return@withContext null

            // 이전 응답 JSON 재구성 (Gson으로 안전한 이스케이프)
            val previousJson = JsonObject().apply {
                addProperty("isPayment", true)
                addProperty("amountRegex", currentRegex.amountRegex)
                addProperty("storeRegex", currentRegex.storeRegex)
                addProperty("cardRegex", currentRegex.cardRegex)
            }

            // 실패 사유: 인간 친화적 메시지 + 실패 샘플별 regex 진단 포함
            val totalCount = allSamples.size
            val passCount = totalCount - failedSampleBodies.size
            val failedSamplesWithDiag = failedSampleBodies.take(3).mapIndexed { idx, body ->
                val diag = failedSampleDiagnostics.getOrNull(idx)
                    ?: diagnoseRegexFailure(currentRegex, body)
                "실패${idx + 1}: ${toCompactRegexSample(body, 120)}\n  → $diag"
            }.joinToString("\n")

            val failureReason = "${totalCount}개 샘플 중 ${passCount}개만 매칭됩니다 " +
                "(${(passRatio * 100).toInt()}%). " +
                "아래 실패 샘플과 진단을 참고하여 regex를 수정하세요:\n$failedSamplesWithDiag"

            MoneyTalkLogger.w("[repairFromClassifier] repair 시도 (passRatio=${(passRatio * 100).toInt()}%, failed=${failedSampleBodies.size}건)")

            val repairPrompt = buildRegexRepairPrompt(
                samples = allSamples,
                previousResponse = previousJson.toString(),
                failureReason = failureReason
            )

            val responseText = requestRegexWithTokenFallback(
                model = model,
                primaryPrompt = repairPrompt,
                compactPrompt = buildRegexCompactPrompt(allSamples),
                ultraCompactPrompt = buildRegexUltraCompactPrompt(allSamples)
            ) ?: return@withContext null

            val candidate = parseRegexResponse(responseText)
            val validation = validateRegexResult(candidate)

            if (validation.isValid && candidate != null) {
                MoneyTalkLogger.w("[repairFromClassifier] repair 응답 형식 검증 통과")
                candidate
            } else {
                MoneyTalkLogger.w("[repairFromClassifier] repair 응답 형식 검증 실패 (reason=${validation.reason})")
                null
            }
        } catch (e: Exception) {
            MoneyTalkLogger.e("Classifier repair 실패: ${e.message}", e)
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
            MoneyTalkLogger.e("LLM 응답 파싱 실패: ${e.message}")
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
            MoneyTalkLogger.e("정규식 응답 파싱 실패: ${e.message}")
            null
        }
    }

    /**
     * regex 응답 기본 검증 (JSON 파싱 + regex 컴파일 + 필수필드 체크)
     *
     * 샘플 성공률 검증은 SmsGroupClassifier.validateRegexAgainstSamples()에서 수행.
     * Extractor에서는 regex가 유효한 형식인지만 확인하여,
     * 0.5~0.79 사이 regex가 Classifier 검증까지 도달할 수 있도록 함.
     */
    private fun validateRegexResult(
        result: LlmRegexResult?
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

        try {
            Regex(result.amountRegex)
        } catch (e: Exception) {
            return RegexValidationResult(isValid = false, reason = "amount_regex_compile_failed")
        }
        try {
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

        return RegexValidationResult(isValid = true, reason = "ok")
    }

    private fun buildRegexPrompt(
        samples: List<String>,
        mainRegexContext: MainRegexContext? = null
    ): String {
        val sampleText = samples.mapIndexed { index, body ->
            "${index + 1}) ${toCompactRegexSample(body, 180)}"
        }.joinToString("\n")

        return if (mainRegexContext != null) {
            val mainSample = toCompactRegexSample(mainRegexContext.sampleBody, 150)
            val cardRegexLine = if (mainRegexContext.cardRegex.isNotBlank()) {
                "메인 cardRegex: ${mainRegexContext.cardRegex}"
            } else ""
            context.getString(
                R.string.prompt_sms_regex_with_main_ref,
                mainSample,
                mainRegexContext.amountRegex,
                mainRegexContext.storeRegex,
                cardRegexLine,
                sampleText
            )
        } else {
            context.getString(R.string.prompt_sms_regex_generate, sampleText)
        }
    }

    /**
     * MAX_TOKENS 발생 시 축약 프롬프트로 1회 재시도
     */
    private suspend fun requestRegexWithTokenFallback(
        model: GenerativeModel,
        primaryPrompt: String,
        compactPrompt: String,
        ultraCompactPrompt: String
    ): String? {
        // 토큰 디버깅: 프롬프트 길이 + countTokens
        try {
            val tokenCount = model.countTokens(primaryPrompt)
        } catch (e: Exception) {
        }

        return try {
            val response = model.generateContent(primaryPrompt)
            val text = response.text
            text
        } catch (e: Exception) {
            if (!isMaxTokensError(e)) throw e
            MoneyTalkLogger.w("[extractRegex] MAX_TOKENS 발생 (primaryPromptLen=${primaryPrompt.length}자) → compact 프롬프트 재시도")

            try {
                val response2 = model.generateContent(compactPrompt)
                val text2 = response2.text
                text2
            } catch (e2: Exception) {
                if (!isMaxTokensError(e2)) throw e2
                MoneyTalkLogger.w("[extractRegex] MAX_TOKENS 재발생 (compactPromptLen=${compactPrompt.length}자) → ultraCompact 3차 시도")

                try {
                    val response3 = model.generateContent(ultraCompactPrompt)
                    val text3 = response3.text
                    text3
                } catch (e3: Exception) {
                    if (isMaxTokensError(e3)) {
                        MoneyTalkLogger.w("[extractRegex] MAX_TOKENS 3차 발생 (ultraCompactLen=${ultraCompactPrompt.length}자) → 포기")
                        return null
                    }
                    throw e3
                }
            }
        }
    }

    private fun isMaxTokensError(e: Exception): Boolean {
        val message = e.message.orEmpty()
        return message.contains("MAX_TOKENS", ignoreCase = true)
    }

    private fun buildRegexCompactPrompt(
        samples: List<String>,
        mainRegexContext: MainRegexContext? = null
    ): String {
        val compactSamples = samples.mapIndexed { index, body ->
            val sliced = toCompactRegexSample(body, 140)
            "${index + 1}: $sliced"
        }.joinToString("\n")

        return if (mainRegexContext != null) {
            context.getString(
                R.string.prompt_sms_regex_compact_with_main_ref,
                mainRegexContext.amountRegex,
                mainRegexContext.storeRegex,
                mainRegexContext.cardRegex,
                compactSamples
            )
        } else {
            context.getString(R.string.prompt_sms_regex_compact, compactSamples)
        }
    }

    private fun buildRegexUltraCompactPrompt(samples: List<String>): String {
        val shortest = samples
            .map { toCompactRegexSample(it, 120) }
            .minByOrNull { it.length }
            .orEmpty()

        return context.getString(R.string.prompt_sms_regex_ultra_compact, shortest)
    }

    private fun buildRegexRepairPrompt(
        samples: List<String>,
        previousResponse: String,
        failureReason: String
    ): String {
        val sampleText = samples.mapIndexed { index, body ->
            "${index + 1}) ${toCompactRegexSample(body, 180)}"
        }.joinToString("\n")
        val previousShort = previousResponse.replace("\n", " ").take(240)
        val humanReason = toHumanFriendlyReason(failureReason)

        return context.getString(
            R.string.prompt_sms_regex_repair,
            humanReason,
            previousShort,
            sampleText
        )
    }

    /**
     * 기술적 검증 실패 사유를 LLM이 이해하기 쉬운 메시지로 변환
     */
    private fun toHumanFriendlyReason(reason: String): String {
        return when (reason) {
            "json_parse_failed" -> context.getString(R.string.sms_regex_reason_json_parse_failed)
            "isPayment_false" -> context.getString(R.string.sms_regex_reason_is_payment_false)
            "required_regex_blank" -> context.getString(R.string.sms_regex_reason_required_regex_blank)
            "amount_regex_compile_failed" -> context.getString(R.string.sms_regex_reason_amount_compile_failed)
            "store_regex_compile_failed" -> context.getString(R.string.sms_regex_reason_store_compile_failed)
            "card_regex_compile_failed" -> context.getString(R.string.sms_regex_reason_card_compile_failed)
            else -> reason
        }
    }

    /**
     * 실패 샘플에 대해 각 regex의 매칭 여부를 진단
     *
     * repair 프롬프트에 "어떤 regex가 실패했는지" 구체적 정보를 제공하여
     * LLM이 정확한 부분만 수정하도록 유도.
     *
     * 예: "amountRegex OK(12,000), storeRegex 매칭 안됨"
     */
    private fun diagnoseRegexFailure(regex: LlmRegexResult, sampleBody: String): String {
        val diags = mutableListOf<String>()

        // amountRegex 진단
        try {
            val match = Regex(regex.amountRegex).find(sampleBody)
            val captured = match?.groupValues?.getOrNull(1)?.trim()
            when {
                match == null -> diags.add("amountRegex 매칭 안됨")
                captured.isNullOrBlank() -> diags.add("amountRegex 매칭되나 캡처그룹 비어있음")
                else -> diags.add("amountRegex OK(${captured.take(15)})")
            }
        } catch (e: Exception) {
            diags.add("amountRegex 오류")
        }

        // storeRegex 진단
        try {
            val match = Regex(regex.storeRegex).find(sampleBody)
            val captured = match?.groupValues?.getOrNull(1)?.trim()
            when {
                match == null -> diags.add("storeRegex 매칭 안됨")
                captured.isNullOrBlank() -> diags.add("storeRegex 매칭되나 캡처그룹 비어있음")
                else -> diags.add("storeRegex OK(${captured.take(15)})")
            }
        } catch (e: Exception) {
            diags.add("storeRegex 오류")
        }

        // cardRegex 진단 (있는 경우만)
        if (regex.cardRegex.isNotBlank()) {
            try {
                val match = Regex(regex.cardRegex).find(sampleBody)
                val captured = match?.groupValues?.getOrNull(1)?.trim()
                when {
                    match == null -> diags.add("cardRegex 매칭 안됨")
                    captured.isNullOrBlank() -> diags.add("cardRegex 캡처그룹 비어있음")
                    else -> diags.add("cardRegex OK(${captured.take(15)})")
                }
            } catch (e: Exception) {
                diags.add("cardRegex 오류")
            }
        }

        return diags.joinToString(", ")
    }

    /**
     * 정규식 생성 프롬프트용 샘플 축약
     *
     * 줄바꿈만 이스케이프하고 maxLen으로 자름.
     * 날짜/시간/금액 등을 플레이스홀더로 치환하지 않음 —
     * LLM이 실제 형식을 보아야 정확한 정규식을 생성할 수 있고,
     * 검증은 원본 SMS로 수행되므로 일치해야 함.
     */
    private fun toCompactRegexSample(text: String, maxLen: Int): String {
        val oneLine = text.replace("\n", "\\n")
        return if (oneLine.length > maxLen) oneLine.take(maxLen) else oneLine
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
            .replace(CODE_FENCE_JSON, "")
            .replace(CODE_FENCE, "")
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
                MoneyTalkLogger.e("API 키가 설정되지 않음")
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

            val prompt = context.getString(
                R.string.prompt_sms_batch_extract_user,
                smsMessages.size.toString(),
                referenceText,
                smsListText
            )


            // 재시도 로직
            for (attempt in 0 until BATCH_MAX_RETRIES) {
                try {
                    val startTime = System.currentTimeMillis()
                    val response = model.generateContent(prompt)
                    val elapsed = System.currentTimeMillis() - startTime
                    val responseText = response.text
                    if (responseText == null) {
                        MoneyTalkLogger.e("[extractBatch] LLM 배치 응답 없음 (${elapsed}ms, 시도 ${attempt + 1})")
                        continue
                    }


                    val parsed = parseBatchExtractionResponse(responseText, smsMessages.size)
                    if (parsed != null) {
                        return@withContext parsed
                    }
                } catch (e: Exception) {
                    val isRateLimit = e.message?.contains("429") == true ||
                            e.message?.contains("RESOURCE_EXHAUSTED") == true
                    if (isRateLimit && attempt < BATCH_MAX_RETRIES - 1) {
                        val retryDelayMs = BATCH_RETRY_BASE_DELAY_MS * (attempt + 1)
                        MoneyTalkLogger.w("[extractBatch] ⚠️ 429 Rate Limit 발생! (GeminiSmsExtractor.extractFromSmsBatch) ${retryDelayMs}ms 후 재시도"
                        )
                        delay(retryDelayMs)
                        continue
                    }
                    MoneyTalkLogger.e("[extractBatch] LLM 배치 추출 실패 (시도 ${attempt + 1}): ${e.message}")
                }
            }

            // 배치 실패 시 개별 추출로 폴백
            MoneyTalkLogger.w("[extractBatch] LLM 배치 실패, 개별 추출로 폴백 (${smsMessages.size}건)")
            smsMessages.mapIndexed { idx, sms ->
                try {
                    val ts = smsTimestamps.getOrNull(idx) ?: 0L
                    val startTime = System.currentTimeMillis()
                    val result = extractFromSms(sms, ts)
                    val elapsed = System.currentTimeMillis() - startTime
                    if (idx < smsMessages.lastIndex) {
                        delay(FALLBACK_SINGLE_DELAY_MS)
                    }
                    result
                } catch (e: Exception) {
                    MoneyTalkLogger.e("[extractBatch→fallback] 개별 LLM ${idx + 1} 실패: ${e.message}")
                    null
                }
            }
        } catch (e: Exception) {
            MoneyTalkLogger.e("LLM 배치 추출 전체 실패: ${e.message}", e)
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
                    MoneyTalkLogger.w("배치 응답 항목 파싱 실패: ${e.message}")
                }
            }

            // 1-indexed no를 0-indexed 리스트로 변환
            val results = (1..expectedSize).map { no ->
                resultMap[no]
            }

            // 절반 이상 파싱 성공해야 유효한 결과
            val parsedCount = results.count { it != null }
            if (parsedCount < expectedSize / 2) {
                MoneyTalkLogger.w("배치 파싱 결과가 너무 적음: $parsedCount/$expectedSize")
                return null
            }

            results
        } catch (e: Exception) {
            MoneyTalkLogger.e("배치 응답 JSON 배열 파싱 실패: ${e.message}")
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
