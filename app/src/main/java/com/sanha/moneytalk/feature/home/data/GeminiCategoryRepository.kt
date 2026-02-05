package com.sanha.moneytalk.feature.home.data

import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.generationConfig
import com.sanha.moneytalk.core.datastore.SettingsDataStore
import com.sanha.moneytalk.core.model.Category
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

/**
 * Gemini API를 사용한 카테고리 분류 Repository
 */
@Singleton
class GeminiCategoryRepository @Inject constructor(
    private val settingsDataStore: SettingsDataStore
) {
    companion object {
        private const val TAG = "GeminiCategory"
        private const val BATCH_SIZE = 50
        private const val MAX_RETRIES = 3
        private const val INITIAL_DELAY_MS = 5000L  // 5초 기본 딜레이
        private const val MAX_DELAY_MS = 60000L     // 최대 60초 딜레이
        private const val MAX_LOG_LENGTH = 3000     // Logcat 한 줄 최대 길이

        /**
         * 긴 문자열을 Logcat에서 잘리지 않도록 분할 출력
         */
        private fun logLongString(tag: String, label: String, text: String) {
            Log.d(tag, "=== $label (총 ${text.length}자) ===")
            if (text.length <= MAX_LOG_LENGTH) {
                Log.d(tag, text)
            } else {
                val chunks = text.chunked(MAX_LOG_LENGTH)
                chunks.forEachIndexed { index, chunk ->
                    Log.d(tag, "[$label ${index + 1}/${chunks.size}]\n$chunk")
                }
            }
            Log.d(tag, "=== $label 끝 ===")
        }
    }

    private var generativeModel: GenerativeModel? = null

    /**
     * Gemini API 키 설정
     */
    suspend fun setApiKey(apiKey: String) {
        settingsDataStore.saveGeminiApiKey(apiKey)
        initModel(apiKey)
    }

    /**
     * API 키 존재 여부 확인
     */
    suspend fun hasApiKey(): Boolean {
        return settingsDataStore.getGeminiApiKey().isNotEmpty()
    }

    /**
     * API 키 가져오기
     */
    suspend fun getApiKey(): String {
        return settingsDataStore.getGeminiApiKey()
    }

    private fun initModel(apiKey: String) {
        if (apiKey.isBlank()) return

        generativeModel = GenerativeModel(
            modelName = "gemini-2.0-flash",
            apiKey = apiKey,
            generationConfig = generationConfig {
                temperature = 0.1f  // 낮은 온도로 일관된 분류
                maxOutputTokens = 1024
            }
        )
    }

    /**
     * 가게명 목록을 카테고리로 분류
     * Rate Limit (429) 에러 발생 시 지수 백오프로 재시도
     *
     * @param storeNames 분류할 가게명 목록
     * @return Map<가게명, 카테고리>
     */
    suspend fun classifyStoreNames(storeNames: List<String>): Map<String, String> = withContext(Dispatchers.IO) {
        val apiKey = settingsDataStore.getGeminiApiKey()
        if (apiKey.isBlank()) {
            Log.e(TAG, "API 키가 설정되지 않음")
            return@withContext emptyMap()
        }

        if (generativeModel == null) {
            initModel(apiKey)
        }

        val model = generativeModel ?: return@withContext emptyMap()

        // 사용 가능한 카테고리 목록
        val categories = Category.entries.map { it.displayName }

        // 배치 처리 (한 번에 최대 50개)
        val results = mutableMapOf<String, String>()
        val batches = storeNames.chunked(BATCH_SIZE)
        val failedBatches = mutableListOf<Pair<Int, List<String>>>()

        Log.d(TAG, "총 ${storeNames.size}개를 ${batches.size}개 배치로 처리")

        for ((index, batch) in batches.withIndex()) {
            // 첫 번째 배치 이후에는 기본 딜레이 적용 (Rate Limit 방지)
            if (index > 0) {
                Log.d(TAG, "배치 ${index + 1}/${batches.size} 처리 전 ${INITIAL_DELAY_MS}ms 대기 중...")
                delay(INITIAL_DELAY_MS)
            }

            val batchResult = processBatchWithRetry(model, batch, categories, index, batches.size)
            if (batchResult != null) {
                batchResult.forEach { (store, category) ->
                    results[store] = category
                }
            } else {
                // 재시도 후에도 실패한 배치 기록
                failedBatches.add(index to batch)
            }
        }

        // 실패한 배치 요약 로그
        if (failedBatches.isNotEmpty()) {
            Log.w(TAG, "총 ${failedBatches.size}개 배치 처리 실패: ${failedBatches.map { it.first + 1 }}")
        }

        Log.d(TAG, "분류 완료: ${storeNames.size}개 중 ${results.size}개 성공")
        results
    }

    /**
     * 지수 백오프 재시도 로직이 적용된 배치 처리
     */
    private suspend fun processBatchWithRetry(
        model: GenerativeModel,
        batch: List<String>,
        categories: List<String>,
        batchIndex: Int,
        totalBatches: Int
    ): Map<String, String>? {
        var lastException: Exception? = null
        var currentDelay = INITIAL_DELAY_MS

        for (attempt in 1..MAX_RETRIES) {
            try {
                val prompt = buildClassificationPrompt(batch, categories)

                Log.d(TAG, "=== REQUEST [배치 ${batchIndex + 1}/$totalBatches, 시도 $attempt] ===")
                Log.d(TAG, "모델: gemini-2.0-flash, 배치 크기: ${batch.size}개")
                Log.d(TAG, "가게명 목록: ${batch.joinToString(", ")}")
                logLongString(TAG, "PROMPT", prompt)

                val startTime = System.currentTimeMillis()
                val response = model.generateContent(prompt)
                val elapsed = System.currentTimeMillis() - startTime

                val text = response.text

                Log.d(TAG, "=== RESPONSE [배치 ${batchIndex + 1}/$totalBatches] (${elapsed}ms) ===")
                if (text != null) {
                    logLongString(TAG, "RESPONSE", text)
                } else {
                    Log.d(TAG, "응답: (null)")
                }

                if (text != null) {
                    val parsed = parseClassificationResponse(text, batch)
                    Log.d(TAG, "파싱 결과: ${parsed.size}/${batch.size}개 성공")
                    return parsed
                }
            } catch (e: Exception) {
                lastException = e
                val errorMessage = e.message ?: ""

                Log.e(TAG, "=== ERROR [배치 ${batchIndex + 1}/$totalBatches, 시도 $attempt] ===")
                Log.e(TAG, "에러 클래스: ${e.javaClass.name}")
                Log.e(TAG, "에러 메시지: $errorMessage")

                // Rate Limit 에러인지 확인
                val isRateLimitError = errorMessage.contains("429") ||
                        errorMessage.contains("RESOURCE_EXHAUSTED") ||
                        errorMessage.contains("rate limit", ignoreCase = true)

                if (isRateLimitError && attempt < MAX_RETRIES) {
                    // 지수 백오프로 딜레이 증가 (최대 60초)
                    val actualDelay = min(currentDelay, MAX_DELAY_MS)
                    Log.w(TAG, "⚠️ Rate Limit 발생! ${actualDelay}ms 후 재시도 ($attempt/$MAX_RETRIES)")
                    delay(actualDelay)
                    currentDelay *= 2  // 지수 백오프: 5초 -> 10초 -> 20초
                } else if (!isRateLimitError) {
                    // Rate Limit이 아닌 다른 에러는 바로 실패 처리
                    Log.e(TAG, "비 Rate Limit 에러로 즉시 실패 처리")
                    break
                } else {
                    Log.e(TAG, "최대 재시도 횟수($MAX_RETRIES) 초과")
                }
            }
        }

        Log.e(TAG, "배치 ${batchIndex + 1} 최종 실패: ${lastException?.message}")
        return null
    }

    private fun buildClassificationPrompt(storeNames: List<String>, categories: List<String>): String {
        return """
다음 가게명들을 아래 카테고리 중 하나로 분류해주세요.

카테고리 목록:
${categories.joinToString(", ")}

가게명 목록:
${storeNames.mapIndexed { idx, name -> "${idx + 1}. $name" }.joinToString("\n")}

응답 형식 (각 줄에 "가게명: 카테고리" 형태로):
예시)
스타벅스: 카페
이마트: 쇼핑
맥도날드: 식비

분류 결과:
""".trimIndent()
    }

    private fun parseClassificationResponse(response: String, storeNames: List<String>): Map<String, String> {
        val results = mutableMapOf<String, String>()
        val validCategories = Category.entries.map { it.displayName }.toSet()

        response.lines().forEach { line ->
            val parts = line.split(":")
            if (parts.size >= 2) {
                val storeName = parts[0].trim().replace(Regex("^\\d+\\.\\s*"), "")
                val category = parts[1].trim()

                // 유효한 카테고리인지 확인
                if (category in validCategories && storeNames.any { it.contains(storeName) || storeName.contains(it) }) {
                    // 원래 가게명 찾기
                    val originalName = storeNames.find { it.contains(storeName) || storeName.contains(it) }
                    if (originalName != null) {
                        results[originalName] = category
                    }
                }
            }
        }

        return results
    }

    /**
     * 단일 가게명 분류
     */
    suspend fun classifySingleStore(storeName: String): String? {
        val result = classifyStoreNames(listOf(storeName))
        return result[storeName]
    }
}
