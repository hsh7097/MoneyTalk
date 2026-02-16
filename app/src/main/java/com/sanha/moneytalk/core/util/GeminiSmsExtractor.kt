package com.sanha.moneytalk.core.util

import android.content.Context
import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import com.google.gson.JsonParser
import com.sanha.moneytalk.R
import com.sanha.moneytalk.core.firebase.GeminiApiKeyProvider
import com.sanha.moneytalk.core.model.Category
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
    private var cachedApiKey: String? = null

    private suspend fun getModel(): GenerativeModel? {
        val apiKey = apiKeyProvider.getApiKey()
        if (apiKey.isBlank()) return null

        if (extractorModel == null || apiKey != cachedApiKey) {
            cachedApiKey = apiKey
            extractorModel = GenerativeModel(
                modelName = "gemini-2.5-flash-lite",
                apiKey = apiKey,
                generationConfig = generationConfig {
                    temperature = 0.1f  // 정확한 추출을 위해 낮은 온도
                    maxOutputTokens = 256
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

        if (batchExtractorModel == null || apiKey != cachedApiKey) {
            cachedApiKey = apiKey
            batchExtractorModel = GenerativeModel(
                modelName = "gemini-2.5-flash-lite",
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
     * LLM 응답에서 JSON 파싱
     */
    private fun parseExtractionResponse(response: String): LlmExtractionResult? {
        return try {
            // JSON 부분만 추출 (마크다운 코드블록 가능성 대비)
            val jsonStr = response
                .replace(Regex("```json\\s*"), "")
                .replace(Regex("```\\s*"), "")
                .trim()

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
                        Log.w(
                            TAG,
                            "[extractBatch] ⚠️ 429 Rate Limit 발생! (GeminiSmsExtractor.extractFromSmsBatch) ${2000 * (attempt + 1)}ms 후 재시도"
                        )
                        delay(2000L * (attempt + 1))
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
                    delay(200) // 개별 호출 시 최소 딜레이
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
            // JSON 배열 부분만 추출 (마크다운 코드블록 대비)
            val jsonStr = response
                .replace(Regex("```json\\s*"), "")
                .replace(Regex("```\\s*"), "")
                .trim()

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
    }
}
