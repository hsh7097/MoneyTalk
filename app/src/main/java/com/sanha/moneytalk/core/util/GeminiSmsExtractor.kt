package com.sanha.moneytalk.core.util

import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import com.google.gson.JsonParser
import com.sanha.moneytalk.core.datastore.SettingsDataStore
import kotlinx.coroutines.Dispatchers
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
6. category: 추정 카테고리 (식비, 카페, 교통, 쇼핑, 구독, 의료/건강, 문화/여가, 교육, 생활, 기타 중 하나)

[응답 규칙]
1. 반드시 JSON만 반환 (다른 텍스트 없이)
2. 금액은 정수만 (콤마 제거)
3. 가게명이 불분명하면 "결제"로 기입
4. 카드사가 불분명하면 "기타"로 기입
5. 결제 문자가 아니면 isPayment: false

[응답 형식]
{"isPayment": true, "amount": 15000, "storeName": "스타벅스", "cardName": "KB국민", "dateTime": "2025-01-15 14:30", "category": "카페"}"""
    }

    private var extractorModel: GenerativeModel? = null

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
     * API 키 변경 시 모델 초기화
     */
    fun resetModel() {
        extractorModel = null
    }
}
