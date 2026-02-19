package com.sanha.moneytalk.core.sms2

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.sanha.moneytalk.core.firebase.GeminiApiKeyProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ===== SMS 템플릿화 + 임베딩 생성 엔진 (Step 3) =====
 *
 * 역할:
 * 1. SMS 원본을 플레이스홀더 템플릿으로 변환 (templateize)
 *    예: "[KB]02/05 스타벅스 11,940원 승인" → "[KB]{DATE} {STORE} {AMOUNT}원 승인"
 *
 * 2. 템플릿을 Gemini Embedding API에 보내 3072차원 벡터 생성 (embed)
 *
 * 템플릿화 이유:
 * - 같은 카드사/같은 형식의 SMS는 가게명/금액만 다름
 * - 변하는 부분을 플레이스홀더로 치환하면 "구조적 유사성"이 높아져
 *   벡터 유사도가 정확해짐 (스타벅스 결제든 카페 결제든 같은 KB 형식이면 유사)
 *
 * 호출 순서: SmsPipeline.batchEmbed() → [여기]
 *
 * API: Gemini Embedding API (REST)
 * - 단건: embedContent
 * - 배치: batchEmbedContents (최대 100건)
 * - 모델명: Firebase RTDB에서 원격 관리 (GeminiApiKeyProvider.modelConfig.embedding)
 */
@Singleton
class SmsTemplateEngine @Inject constructor(
    private val apiKeyProvider: GeminiApiKeyProvider
) {

    companion object {
        private const val TAG = "SmsTemplateEngine"
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"

        /** 429 Rate Limit 재시도 최대 횟수 */
        private const val MAX_RETRIES = 3

        /** 초기 재시도 대기 시간 (ms) - 지수 백오프: 2s, 4s */
        private const val INITIAL_RETRY_DELAY_MS = 2000L
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    // ===== 템플릿화 =====

    /**
     * SMS 본문을 플레이스홀더 템플릿으로 변환
     *
     * 치환 순서 (우선순위):
     * 1. 금액+원 → {AMOUNT}원 (예: "15,000원" → "{AMOUNT}원")
     * 2. 줄바꿈 사이 숫자 → {AMOUNT} (예: "\n15000\n" → "\n{AMOUNT}\n")
     * 3. 날짜 → {DATE} (예: "02/25" → "{DATE}")
     * 4. 시간 → {TIME} (예: "14:30" → "{TIME}")
     * 5. 잔액 → 잔액{BALANCE} (예: "잔액100,000" → "잔액{BALANCE}")
     * 6. 카드번호 마스킹 → {CARD_NUM} (예: "1234*567" → "{CARD_NUM}")
     * 7. 가게명 줄 → {STORE} (4줄 이상 SMS에서, 구조 키워드가 아닌 텍스트 줄)
     *
     * @param smsBody 원본 SMS 본문
     * @return 플레이스홀더로 치환된 템플릿 텍스트
     */
    fun templateize(smsBody: String): String {
        var template = smsBody

        // 1. 금액+원 치환
        template = template.replace(Regex("""[\d,]+원"""), "{AMOUNT}원")
        // 2. 줄바꿈 사이 순수 숫자 치환
        template = template.replace(Regex("""\n[\d,]{3,}\n"""), "\n{AMOUNT}\n")
        // 3. 날짜 치환 (MM/DD, MM-DD, MM.DD)
        template = template.replace(Regex("""\d{1,2}[/.-]\d{1,2}"""), "{DATE}")
        // 4. 시간 치환 (HH:mm)
        template = template.replace(Regex("""\d{1,2}:\d{2}"""), "{TIME}")
        // 5. 잔액 치환
        template = template.replace(Regex("""잔액[\d,]+"""), "잔액{BALANCE}")
        // 6. 카드번호 마스킹 치환
        template = template.replace(Regex("""\d+\*+\d+"""), "{CARD_NUM}")

        // 7. 가게명 줄 치환 (줄바꿈 구분 SMS 전용)
        val lines = template.split("\n")
        if (lines.size >= 4) {
            var storeReplaced = false
            val result = lines.map { line ->
                val trimmed = line.trim()
                if (!storeReplaced && isLikelyStoreName(trimmed)) {
                    storeReplaced = true
                    "{STORE}"
                } else {
                    line
                }
            }
            template = result.joinToString("\n")
        }

        return template
    }

    /**
     * 구조적 키워드 — 가게명이 아닌 SMS 형식 구성 요소
     *
     * 이 키워드가 포함된 줄은 가게명 후보에서 제외됨.
     */
    private val structuralKeywords = setOf(
        "출금", "입금", "승인", "결제", "이체", "잔액",
        "[web발신]", "누적", "일시불", "할부", "체크카드", "해외승인"
    )

    /**
     * 해당 줄이 가게명일 가능성이 있는지 판별
     *
     * 가게명이 아닌 조건 (false 반환):
     * - 빈 줄, 2자 미만, 20자 초과
     * - 플레이스홀더({...}) 포함
     * - 구조 키워드(출금, 승인 등) 포함
     * - 숫자+콤마만으로 구성 (금액)
     *
     * 가게명 후보 조건 (true 반환):
     * - 첫 글자가 한글/영문/괄호/별표
     */
    internal fun isLikelyStoreName(line: String): Boolean {
        if (line.isEmpty() || line.length < 2 || line.length > 20) return false
        if (line.contains("{")) return false
        if (structuralKeywords.any { line.lowercase().contains(it) }) return false
        if (line.all { it.isDigit() || it == ',' }) return false
        val firstChar = line.first()
        return firstChar.isLetter() || firstChar == '(' || firstChar == '*'
    }

    // ===== 임베딩 생성 =====

    /**
     * 배치 임베딩 생성
     *
     * 여러 템플릿을 한 번에 Gemini Embedding API에 보내 벡터를 생성.
     * batchEmbedContents API 사용 (최대 100건).
     *
     * 429 Rate Limit 발생 시:
     * - 지수 백오프로 최대 [MAX_RETRIES]회 재시도 (2s → 4s)
     * - Quota 초과(일일 한도)는 재시도 불가 → 즉시 실패
     *
     * @param templates 템플릿화된 텍스트 목록 (최대 100건)
     * @return 각 텍스트의 임베딩 벡터 목록 (실패한 항목은 null)
     */
    suspend fun batchEmbed(templates: List<String>): List<List<Float>?> =
        withContext(Dispatchers.IO) {
            try {
                val apiKey = apiKeyProvider.getApiKey()
                if (apiKey.isBlank()) {
                    Log.e(TAG, "API 키가 설정되지 않음")
                    return@withContext templates.map { null }
                }

                val embeddingModel = apiKeyProvider.modelConfig.embedding
                val url = "$BASE_URL/$embeddingModel:batchEmbedContents?key=$apiKey"

                val requests = templates.map { text ->
                    mapOf(
                        "model" to "models/$embeddingModel",
                        "content" to mapOf(
                            "parts" to listOf(mapOf("text" to text))
                        )
                    )
                }

                val jsonBody = gson.toJson(mapOf("requests" to requests))

                val startTime = System.currentTimeMillis()
                Log.d(TAG, "배치 임베딩 요청: ${templates.size}건")

                // 429 Rate Limit 재시도 (지수 백오프)
                var lastError: String? = null
                for (attempt in 0 until MAX_RETRIES) {
                    if (attempt > 0) {
                        val delayMs = INITIAL_RETRY_DELAY_MS * (1L shl (attempt - 1))
                        Log.w(TAG, "429 재시도 ${attempt}/$MAX_RETRIES, ${delayMs}ms 대기")
                        kotlinx.coroutines.delay(delayMs)
                    }

                    val request = Request.Builder()
                        .url(url)
                        .post(jsonBody.toRequestBody("application/json".toMediaType()))
                        .build()

                    val response = client.newCall(request).execute()
                    val responseBody = response.body?.string()

                    if (response.code == 429) {
                        lastError = responseBody?.take(300)
                        // Quota 초과 vs Rate Limit 구분
                        val isQuotaExceeded =
                            responseBody?.contains("exceeded your current quota") == true
                        if (isQuotaExceeded) {
                            Log.e(TAG, "임베딩 일일 할당량(Quota) 초과 - 재시도 불가")
                            return@withContext templates.map { null }
                        }
                        Log.w(TAG, "429 Rate Limit (시도 ${attempt + 1}/$MAX_RETRIES, ${templates.size}건)")
                        continue
                    }

                    if (!response.isSuccessful) {
                        Log.e(TAG, "배치 임베딩 API 실패: ${response.code} - ${responseBody?.take(200)}")
                        return@withContext templates.map { null }
                    }

                    if (responseBody == null) {
                        return@withContext templates.map { null }
                    }

                    // JSON 파싱: { "embeddings": [{ "values": [...] }, ...] }
                    val json = JsonParser.parseString(responseBody).asJsonObject
                    val embeddings = json.getAsJsonArray("embeddings")

                    val result = embeddings.map { embeddingElement ->
                        val embeddingObj = embeddingElement.asJsonObject
                        val values = embeddingObj.getAsJsonArray("values")
                        values.map { it.asFloat }
                    }

                    val elapsed = System.currentTimeMillis() - startTime
                    Log.d(TAG, "배치 임베딩 성공: ${result.size}건 (${elapsed}ms)")
                    return@withContext result
                }

                // 모든 재시도 실패
                Log.e(TAG, "배치 임베딩 최종 실패 (${MAX_RETRIES}회 시도): $lastError")
                templates.map { null }
            } catch (e: Exception) {
                Log.e(TAG, "배치 임베딩 실패: ${e.message}", e)
                templates.map { null }
            }
        }
}
