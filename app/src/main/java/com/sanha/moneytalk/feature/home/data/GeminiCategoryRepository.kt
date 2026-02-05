package com.sanha.moneytalk.feature.home.data

import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.generationConfig
import com.sanha.moneytalk.core.datastore.SettingsDataStore
import com.sanha.moneytalk.core.model.Category
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gemini API를 사용한 카테고리 분류 Repository
 */
@Singleton
class GeminiCategoryRepository @Inject constructor(
    private val settingsDataStore: SettingsDataStore
) {
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
     * @param storeNames 분류할 가게명 목록
     * @return Map<가게명, 카테고리>
     */
    suspend fun classifyStoreNames(storeNames: List<String>): Map<String, String> = withContext(Dispatchers.IO) {
        val apiKey = settingsDataStore.getGeminiApiKey()
        if (apiKey.isBlank()) {
            Log.e("GeminiCategory", "API 키가 설정되지 않음")
            return@withContext emptyMap()
        }

        if (generativeModel == null) {
            initModel(apiKey)
        }

        val model = generativeModel ?: return@withContext emptyMap()

        // 사용 가능한 카테고리 목록
        val categories = Category.entries.map { it.displayName }

        // 배치 처리 (한 번에 최대 50개, API 부하 방지를 위해 배치 간 딜레이 추가)
        val results = mutableMapOf<String, String>()
        val batches = storeNames.chunked(50)

        Log.d("GeminiCategory", "총 ${storeNames.size}개를 ${batches.size}개 배치로 처리")

        for ((index, batch) in batches.withIndex()) {
            try {
                // 첫 번째 배치 이후에는 2초 딜레이 (Rate Limit 방지)
                if (index > 0) {
                    Log.d("GeminiCategory", "배치 ${index + 1}/${batches.size} 처리 전 대기 중...")
                    kotlinx.coroutines.delay(2000)
                }

                val prompt = buildClassificationPrompt(batch, categories)
                val response = model.generateContent(prompt)
                val text = response.text ?: continue

                // 응답 파싱
                parseClassificationResponse(text, batch).forEach { (store, category) ->
                    results[store] = category
                }

                Log.d("GeminiCategory", "배치 ${index + 1}/${batches.size} 완료: ${batch.size}개 중 ${results.size}개 성공")
            } catch (e: Exception) {
                Log.e("GeminiCategory", "배치 ${index + 1} 분류 실패: ${e.message}")
                // 실패 시 더 긴 딜레이 후 재시도하지 않고 다음 배치로 진행
                kotlinx.coroutines.delay(3000)
            }
        }

        results
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
