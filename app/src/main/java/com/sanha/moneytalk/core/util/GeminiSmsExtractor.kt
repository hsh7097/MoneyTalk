package com.sanha.moneytalk.core.util

import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import com.google.gson.JsonParser
import com.sanha.moneytalk.core.datastore.SettingsDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
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
    private val settingsDataStore: SettingsDataStore
) {
    companion object {
        private const val TAG = "GeminiSmsExtractor"

        private const val SYSTEM_INSTRUCTION = """당신은 한국 카드 결제 SMS에서 정보를 추출하는 전문가입니다.

[역할]
SMS 본문에서 결제 정보를 정확히 추출하여 JSON으로 반환합니다.

[추출 항목]
1. isPayment: 결제 문자인지 여부 (true/false)
2. amount: 결제 금액 (숫자만, 콤마 없이)
3. storeName: 가게명/상호명
4. cardName: 카드사명
5. dateTime: 결제 날짜와 시간 (추출 가능한 경우)
6. category: 추정 카테고리 (식비, 카페, 술/유흥, 교통, 쇼핑, 구독, 의료/건강, 운동, 문화/여가, 교육, 주거, 생활, 경조, 기타 중 하나)

[응답 규칙]
1. 반드시 JSON만 반환 (다른 텍스트 없이)
2. 금액은 정수만 (콤마 제거)
3. 가게명이 불분명하면 "결제"로 기입
4. 카드사가 불분명하면 "기타"로 기입
5. 결제 문자가 아니면 isPayment: false

[응답 형식]
{"isPayment": true, "amount": 15000, "storeName": "스타벅스", "cardName": "KB국민", "dateTime": "2025-01-15 14:30", "category": "카페"}"""

        /** 배치 추출용 시스템 명령어 */
        private const val BATCH_SYSTEM_INSTRUCTION = """당신은 한국 카드 결제 SMS에서 정보를 추출하는 전문가입니다.

[역할]
여러 SMS 본문에서 각각 결제 정보를 추출하여 JSON 배열로 반환합니다.

[추출 항목]
1. no: SMS 번호 (입력에서 제공된 번호)
2. isPayment: 결제 문자인지 여부 (true/false)
3. amount: 결제 금액 (숫자만, 콤마 없이)
4. storeName: 가게명/상호명
5. cardName: 카드사명
6. dateTime: 결제 날짜와 시간 (추출 가능한 경우)
7. category: 추정 카테고리 (식비, 카페, 술/유흥, 교통, 쇼핑, 구독, 의료/건강, 운동, 문화/여가, 교육, 주거, 생활, 경조, 기타 중 하나)

[응답 규칙]
1. 반드시 JSON 배열만 반환 (다른 텍스트 없이)
2. 금액은 정수만 (콤마 제거)
3. 가게명이 불분명하면 "결제"로 기입
4. 카드사가 불분명하면 "기타"로 기입
5. 결제 문자가 아니면 해당 항목은 {"no": N, "isPayment": false}만 반환
6. 입력된 모든 SMS에 대해 반드시 결과를 반환

[응답 형식 예시]
[
  {"no": 1, "isPayment": true, "amount": 15000, "storeName": "스타벅스", "cardName": "KB국민", "dateTime": "2025-01-15 14:30", "category": "카페"},
  {"no": 2, "isPayment": false},
  {"no": 3, "isPayment": true, "amount": 8000, "storeName": "GS25", "cardName": "신한", "dateTime": "2025-01-15 09:00", "category": "쇼핑"}
]"""

        /** 배치 추출 최대 재시도 */
        private const val BATCH_MAX_RETRIES = 2
    }

    private var extractorModel: GenerativeModel? = null
    private var batchExtractorModel: GenerativeModel? = null

    private suspend fun getModel(): GenerativeModel? {
        val apiKey = settingsDataStore.getGeminiApiKey()
        if (apiKey.isBlank()) return null

        if (extractorModel == null) {
            extractorModel = GenerativeModel(
                modelName = "gemini-2.5-flash-lite",
                apiKey = apiKey,
                generationConfig = generationConfig {
                    temperature = 0.1f  // 정확한 추출을 위해 낮은 온도
                    maxOutputTokens = 256
                },
                systemInstruction = content { text(SYSTEM_INSTRUCTION) }
            )
        }
        return extractorModel
    }

    /** 배치 추출용 모델 (maxOutputTokens가 더 큼) */
    private suspend fun getBatchModel(): GenerativeModel? {
        val apiKey = settingsDataStore.getGeminiApiKey()
        if (apiKey.isBlank()) return null

        if (batchExtractorModel == null) {
            batchExtractorModel = GenerativeModel(
                modelName = "gemini-2.5-flash-lite",
                apiKey = apiKey,
                generationConfig = generationConfig {
                    temperature = 0.1f
                    maxOutputTokens = 4096  // 배치 응답용 확장
                },
                systemInstruction = content { text(BATCH_SYSTEM_INSTRUCTION) }
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

    /**
     * SMS에서 결제 정보 추출 (Gemini LLM 사용)
     *
     * @param smsBody SMS 원본 본문
     * @return 추출된 결제 정보, 실패 시 null
     */
    suspend fun extractFromSms(smsBody: String): LlmExtractionResult? = withContext(Dispatchers.IO) {
        try {
            val model = getModel()
            if (model == null) {
                Log.e(TAG, "API 키가 설정되지 않음")
                return@withContext null
            }

            val prompt = """다음 SMS에서 결제 정보를 추출해주세요:

$smsBody"""

            Log.d(TAG, "LLM 추출 요청: ${smsBody.take(80)}...")
            val response = model.generateContent(prompt)
            val responseText = response.text ?: return@withContext null

            Log.d(TAG, "LLM 응답: $responseText")

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

            LlmExtractionResult(
                isPayment = json.get("isPayment")?.asBoolean ?: false,
                amount = json.get("amount")?.asInt ?: 0,
                storeName = json.get("storeName")?.asString ?: "결제",
                cardName = json.get("cardName")?.asString ?: "기타",
                dateTime = json.get("dateTime")?.asString ?: "",
                category = json.get("category")?.asString ?: "기타"
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
     * @return 각 SMS의 추출 결과 (순서 보장, 실패 시 null)
     */
    suspend fun extractFromSmsBatch(
        smsMessages: List<String>
    ): List<LlmExtractionResult?> = withContext(Dispatchers.IO) {
        if (smsMessages.isEmpty()) return@withContext emptyList()

        // 1건이면 단일 추출 사용
        if (smsMessages.size == 1) {
            return@withContext listOf(extractFromSms(smsMessages[0]))
        }

        try {
            val model = getBatchModel()
            if (model == null) {
                Log.e(TAG, "API 키가 설정되지 않음")
                return@withContext smsMessages.map { null }
            }

            // 번호를 매겨서 프롬프트 구성
            val smsListText = smsMessages.mapIndexed { idx, body ->
                "${idx + 1}번: $body"
            }.joinToString("\n\n")

            val prompt = """다음 ${smsMessages.size}개 SMS에서 각각 결제 정보를 추출해주세요:

$smsListText"""

            Log.d(TAG, "LLM 배치 추출 요청: ${smsMessages.size}건")

            // 재시도 로직
            for (attempt in 0 until BATCH_MAX_RETRIES) {
                try {
                    val response = model.generateContent(prompt)
                    val responseText = response.text
                    if (responseText == null) {
                        Log.e(TAG, "LLM 배치 응답 없음 (시도 ${attempt + 1})")
                        continue
                    }

                    Log.d(TAG, "LLM 배치 응답 (${responseText.length}자): ${responseText.take(200)}...")

                    val parsed = parseBatchExtractionResponse(responseText, smsMessages.size)
                    if (parsed != null) {
                        Log.d(TAG, "LLM 배치 추출 성공: ${parsed.count { it != null }}/${smsMessages.size}건 파싱 성공")
                        return@withContext parsed
                    }
                } catch (e: Exception) {
                    val isRateLimit = e.message?.contains("429") == true ||
                            e.message?.contains("RESOURCE_EXHAUSTED") == true
                    if (isRateLimit && attempt < BATCH_MAX_RETRIES - 1) {
                        Log.w(TAG, "LLM 배치 429 Rate Limit, ${2000 * (attempt + 1)}ms 후 재시도")
                        delay(2000L * (attempt + 1))
                        continue
                    }
                    Log.e(TAG, "LLM 배치 추출 실패 (시도 ${attempt + 1}): ${e.message}")
                }
            }

            // 배치 실패 시 개별 추출로 폴백
            Log.w(TAG, "LLM 배치 실패, 개별 추출로 폴백 (${smsMessages.size}건)")
            smsMessages.map { sms ->
                try {
                    val result = extractFromSms(sms)
                    delay(500) // 개별 호출 시 짧은 딜레이
                    result
                } catch (e: Exception) {
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

                    val result = LlmExtractionResult(
                        isPayment = json.get("isPayment")?.asBoolean ?: false,
                        amount = json.get("amount")?.asInt ?: 0,
                        storeName = json.get("storeName")?.asString ?: "결제",
                        cardName = json.get("cardName")?.asString ?: "기타",
                        dateTime = try { json.get("dateTime")?.asString ?: "" } catch (e: Exception) { "" },
                        category = json.get("category")?.asString ?: "기타"
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
