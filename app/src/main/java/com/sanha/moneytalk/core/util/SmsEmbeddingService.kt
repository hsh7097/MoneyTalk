package com.sanha.moneytalk.core.util

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
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
 * SMS 텍스트 임베딩 생성 서비스
 *
 * Gemini Embedding API (gemini-embedding-001)를 사용하여
 * SMS 본문의 임베딩 벡터를 생성합니다.
 *
 * REST API를 직접 호출 (SDK에 embedding 메서드가 없으므로)
 */
@Singleton
class SmsEmbeddingService @Inject constructor(
    private val apiKeyProvider: GeminiApiKeyProvider
) {
    companion object {
        private const val TAG = "gemini"
        private const val EMBEDDING_MODEL = "gemini-embedding-001"
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

    /**
     * SMS 본문을 템플릿화 (금액/날짜를 플레이스홀더로 치환)
     *
     * 동일한 카드사에서 오는 결제 문자의 구조적 유사성을 높이기 위해
     * 변하는 부분(금액, 날짜, 시간)을 치환합니다.
     *
     * 예: "[KB]12/25 14:30 스타벅스 15,000원 승인"
     *   → "[KB]{DATE} {TIME} 스타벅스 {AMOUNT}원 승인"
     */
    fun templateizeSms(smsBody: String): String {
        var template = smsBody

        // 금액 치환 (숫자+원)
        template = template.replace(Regex("""[\d,]+원"""), "{AMOUNT}원")
        // 순수 금액 (줄바꿈 사이 숫자)
        template = template.replace(Regex("""\n[\d,]{3,}\n"""), "\n{AMOUNT}\n")
        // 날짜 치환 (MM/DD, MM-DD, MM.DD)
        template = template.replace(Regex("""\d{1,2}[/.-]\d{1,2}"""), "{DATE}")
        // 시간 치환 (HH:mm)
        template = template.replace(Regex("""\d{1,2}:\d{2}"""), "{TIME}")
        // 잔액 치환
        template = template.replace(Regex("""잔액[\d,]+"""), "잔액{BALANCE}")
        // 카드번호 마스킹 치환
        template = template.replace(Regex("""\d+\*+\d+"""), "{CARD_NUM}")

        return template
    }

    /**
     * 단일 텍스트의 임베딩 벡터 생성
     *
     * @param text 임베딩할 텍스트
     * @return 임베딩 벡터 (768차원), 실패 시 null
     */
    suspend fun generateEmbedding(text: String): List<Float>? = withContext(Dispatchers.IO) {
        try {
            val apiKey = apiKeyProvider.getApiKey()
            if (apiKey.isBlank()) {
                Log.e(TAG, "API 키가 설정되지 않음")
                return@withContext null
            }

            val url = "$BASE_URL/$EMBEDDING_MODEL:embedContent?key=$apiKey"

            val requestBody = JsonObject().apply {
                add("model", gson.toJsonTree("models/$EMBEDDING_MODEL"))
                add("content", JsonObject().apply {
                    add("parts", gson.toJsonTree(listOf(mapOf("text" to text))))
                })
            }

            val request = Request.Builder()
                .url(url)
                .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            Log.d(TAG, "임베딩 요청: ${text.take(50)}...")

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (!response.isSuccessful) {
                Log.e(TAG, "임베딩 API 실패: ${response.code} - ${responseBody?.take(200)}")
                return@withContext null
            }

            if (responseBody == null) {
                Log.e(TAG, "응답 본문이 비어 있음")
                return@withContext null
            }

            // JSON 파싱: { "embedding": { "values": [0.1, 0.2, ...] } }
            val json = JsonParser.parseString(responseBody).asJsonObject
            val embeddingObj = json.getAsJsonObject("embedding")
            val values = embeddingObj.getAsJsonArray("values")

            val embedding = values.map { it.asFloat }
            Log.d(TAG, "임베딩 생성 성공: ${embedding.size}차원")

            embedding
        } catch (e: Exception) {
            Log.e(TAG, "임베딩 생성 실패: ${e.message}", e)
            null
        }
    }

    /**
     * 배치 임베딩 생성 (여러 텍스트를 한번에)
     * 429 Rate Limit 발생 시 지수 백오프로 최대 3회 재시도합니다.
     *
     * @param texts 임베딩할 텍스트 목록
     * @return 각 텍스트의 임베딩 벡터 목록, 실패한 항목은 null
     */
    suspend fun generateEmbeddings(texts: List<String>): List<List<Float>?> =
        withContext(Dispatchers.IO) {
            try {
                val apiKey = apiKeyProvider.getApiKey()
                if (apiKey.isBlank()) {
                    Log.e(TAG, "API 키가 설정되지 않음")
                    return@withContext texts.map { null }
                }

                val url = "$BASE_URL/$EMBEDDING_MODEL:batchEmbedContents?key=$apiKey"

                val requests = texts.map { text ->
                    mapOf(
                        "model" to "models/$EMBEDDING_MODEL",
                        "content" to mapOf(
                            "parts" to listOf(mapOf("text" to text))
                        )
                    )
                }

                val requestBody = mapOf("requests" to requests)
                val jsonBody = gson.toJson(requestBody)

                val overallStartTime = System.currentTimeMillis()
                Log.d(TAG, "[batchEmbed] 배치 임베딩 요청: ${texts.size}건")

                // 429 Rate Limit 재시도 (지수 백오프)
                // Quota 초과("exceeded your current quota")는 재시도 불가 → 즉시 실패
                var lastError: String? = null
                for (attempt in 0 until MAX_RETRIES) {
                    if (attempt > 0) {
                        val delayMs = INITIAL_RETRY_DELAY_MS * (1L shl (attempt - 1)) // 2s, 4s
                        Log.w(
                            TAG,
                            "[batchEmbed] ⚠️ 429 재시도 ${attempt}/${MAX_RETRIES - 1}, ${delayMs}ms 대기... (SmsEmbeddingService.generateEmbeddings)"
                        )
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
                            Log.e(TAG, "[batchEmbed] ❌ 임베딩 일일 할당량(Quota) 초과 - 재시도 불가")
                            return@withContext texts.map { null }
                        }
                        Log.w(
                            TAG,
                            "[batchEmbed] ⚠️ 429 Rate Limit 발생! (SmsEmbeddingService.generateEmbeddings, 시도 ${attempt + 1}/$MAX_RETRIES, ${texts.size}건)"
                        )
                        continue // Rate Limit은 재시도
                    }

                    if (!response.isSuccessful) {
                        Log.e(TAG, "배치 임베딩 API 실패: ${response.code} - ${responseBody?.take(200)}")
                        return@withContext texts.map { null }
                    }

                    if (responseBody == null) {
                        return@withContext texts.map { null }
                    }

                    // JSON 파싱: { "embeddings": [{ "values": [...] }, ...] }
                    val json = JsonParser.parseString(responseBody).asJsonObject
                    val embeddings = json.getAsJsonArray("embeddings")

                    val result = embeddings.map { embeddingElement ->
                        val embeddingObj = embeddingElement.asJsonObject
                        val values = embeddingObj.getAsJsonArray("values")
                        values.map { it.asFloat }
                    }

                    val batchElapsed = System.currentTimeMillis() - overallStartTime
                    Log.d(TAG, "[batchEmbed] 배치 임베딩 성공: ${result.size}건 (${batchElapsed}ms)")
                    return@withContext result
                }

                // 모든 재시도 실패
                Log.e(TAG, "배치 임베딩 최종 실패 (${MAX_RETRIES}회 시도): $lastError")
                texts.map { null }
            } catch (e: Exception) {
                Log.e(TAG, "배치 임베딩 실패: ${e.message}", e)
                texts.map { null }
            }
        }
}
