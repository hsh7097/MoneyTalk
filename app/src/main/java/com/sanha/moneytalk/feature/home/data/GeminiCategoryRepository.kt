package com.sanha.moneytalk.feature.home.data

import android.content.Context
import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.generationConfig
import com.sanha.moneytalk.R
import com.sanha.moneytalk.core.datastore.SettingsDataStore
import com.sanha.moneytalk.core.model.Category
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min
import kotlin.random.Random

/**
 * Gemini API를 사용한 카테고리 분류 Repository
 */
@Singleton
class GeminiCategoryRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsDataStore: SettingsDataStore,
    private val categoryReferenceProvider: com.sanha.moneytalk.core.util.CategoryReferenceProvider
) {
    companion object {
        private const val TAG = "gemini"
        private const val BATCH_SIZE = 50
        private const val MAX_RETRIES = 3
        private const val RETRY_BASE_DELAY_MS = 1000L  // 재시도 기본 딜레이 (1초)
        private const val MAX_RETRY_DELAY_MS = 30000L  // 최대 30초 딜레이
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
            modelName = "gemini-2.5-flash-lite",
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
    suspend fun classifyStoreNames(storeNames: List<String>): Map<String, String> =
        withContext(Dispatchers.IO) {
            Log.e(
                "sanha",
                "GeminiCategoryRepository[classifyStoreNames] : === 호출됨 (${storeNames.size}건) ==="
            )
            val apiKey = settingsDataStore.getGeminiApiKey()
            Log.e(
                "sanha",
                "GeminiCategoryRepository[classifyStoreNames] : API 키 상태: ${
                    if (apiKey.isBlank()) "❌ 비어있음" else "✅ 설정됨 (길이=${apiKey.length}, prefix=${
                        apiKey.take(10)
                    }...)"
                }"
            )
            if (apiKey.isBlank()) {
                Log.e(TAG, "API 키가 설정되지 않음")
                return@withContext emptyMap()
            }

            if (generativeModel == null) {
                Log.e(
                    "sanha",
                    "GeminiCategoryRepository[classifyStoreNames] : 모델 초기화 필요 → initModel() 호출"
                )
                initModel(apiKey)
            }

            val model = generativeModel
            if (model == null) {
                Log.e(
                    "sanha",
                    "GeminiCategoryRepository[classifyStoreNames] : ❌ 모델 초기화 실패 → 빈 결과 반환"
                )
                return@withContext emptyMap()
            }
            Log.e("sanha", "GeminiCategoryRepository[classifyStoreNames] : ✅ 모델 준비 완료")

            // 사용 가능한 카테고리 목록
            val categories = Category.entries.map { it.displayName }

            // 참조 리스트 미리 로드 (배치마다 중복 호출 방지)
            val referenceText = try {
                categoryReferenceProvider.getCategoryClassificationReference()
            } catch (e: Exception) {
                ""
            }

            // 배치 처리 (한 번에 최대 50개)
            val results = mutableMapOf<String, String>()
            val batches = storeNames.chunked(BATCH_SIZE)
            val failedBatches = mutableListOf<Pair<Int, List<String>>>()

            Log.d(TAG, "총 ${storeNames.size}개를 ${batches.size}개 배치로 처리")

            var interBatchDelay = 0L  // 적응형 배치 간 딜레이 (429 발생 시에만 활성화)

            for ((index, batch) in batches.withIndex()) {
                // 이전 배치에서 429가 발생했으면 다음 배치 전에 딜레이
                if (interBatchDelay > 0) {
                    Log.d(TAG, "배치 ${index + 1}/${batches.size} 전 적응형 딜레이 ${interBatchDelay}ms")
                    delay(interBatchDelay)
                }

                val (batchResult, hitRateLimit) = processBatchWithRetry(
                    model,
                    batch,
                    categories,
                    index,
                    batches.size,
                    referenceText
                )
                if (batchResult != null) {
                    batchResult.forEach { (store, category) ->
                        results[store] = category
                    }
                } else {
                    // 재시도 후에도 실패한 배치 기록
                    failedBatches.add(index to batch)
                }

                // 429 발생 → 다음 배치에 딜레이 추가, 정상 → 딜레이 해제
                interBatchDelay = if (hitRateLimit) {
                    val newDelay = if (interBatchDelay == 0L) RETRY_BASE_DELAY_MS else min(
                        interBatchDelay * 2,
                        MAX_RETRY_DELAY_MS
                    )
                    Log.d(TAG, "429 감지 → 배치 간 딜레이 ${newDelay}ms로 설정")
                    newDelay
                } else {
                    0L
                }
            }

            // 실패한 배치 요약 로그
            if (failedBatches.isNotEmpty()) {
                Log.w(
                    TAG,
                    "총 ${failedBatches.size}개 배치 처리 실패: ${failedBatches.map { it.first + 1 }}"
                )
            }

            Log.d(TAG, "분류 완료: ${storeNames.size}개 중 ${results.size}개 성공")
            results
        }

    /**
     * 지수 백오프 재시도 로직이 적용된 배치 처리
     */
    /**
     * @return Pair(결과 맵 or null, 429 발생 여부)
     */
    private suspend fun processBatchWithRetry(
        model: GenerativeModel,
        batch: List<String>,
        categories: List<String>,
        batchIndex: Int,
        totalBatches: Int,
        referenceText: String = ""
    ): Pair<Map<String, String>?, Boolean> {
        var lastException: Exception? = null
        var currentDelay = RETRY_BASE_DELAY_MS
        var hitRateLimit = false

        for (attempt in 1..MAX_RETRIES) {
            try {
                val prompt = buildClassificationPrompt(batch, categories, referenceText)

                Log.d(TAG, "=== REQUEST [배치 ${batchIndex + 1}/$totalBatches, 시도 $attempt] ===")
                Log.d(TAG, "모델: gemini-2.5-flash-lite, 배치 크기: ${batch.size}개")
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
                    return parsed to hitRateLimit
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

                if (isRateLimitError) {
                    hitRateLimit = true

                    if (attempt < MAX_RETRIES) {
                        // 지수 백오프 + jitter (1초 → 2초 → 4초, max 30초)
                        val jitter = Random.nextLong(0, 500)
                        val actualDelay = min(currentDelay + jitter, MAX_RETRY_DELAY_MS)
                        Log.w(
                            TAG,
                            "⚠️ 429 Rate Limit 발생! (GeminiCategoryRepository.classifyStoreNames 배치 ${batchIndex + 1}/$totalBatches) ${actualDelay}ms 후 재시도 ($attempt/$MAX_RETRIES)"
                        )
                        delay(actualDelay)
                        currentDelay *= 2
                    } else {
                        Log.e(
                            TAG,
                            "❌ 최대 재시도 횟수($MAX_RETRIES) 초과 (GeminiCategoryRepository.classifyStoreNames)"
                        )
                    }
                } else {
                    // Rate Limit이 아닌 다른 에러는 바로 실패 처리
                    Log.e(TAG, "비 Rate Limit 에러로 즉시 실패 처리")
                    break
                }
            }
        }

        Log.e(TAG, "배치 ${batchIndex + 1} 최종 실패: ${lastException?.message}")
        return null to hitRateLimit
    }

    private fun buildClassificationPrompt(
        storeNames: List<String>,
        categories: List<String>,
        referenceText: String = ""
    ): String {
        val categoriesText =
            categories.mapIndexed { idx, cat -> "${idx + 1}. $cat" }.joinToString("\n")
        val storeNamesText =
            storeNames.mapIndexed { idx, name -> "${idx + 1}. $name" }.joinToString("\n")
        return context.getString(
            R.string.prompt_category_classification,
            categoriesText,
            referenceText,
            storeNamesText
        )
    }

    private fun parseClassificationResponse(
        response: String,
        storeNames: List<String>
    ): Map<String, String> {
        val results = mutableMapOf<String, String>()
        val validCategories = Category.entries.map { it.displayName }.toSet()

        // 잘못된 카테고리명을 올바른 카테고리명으로 매핑
        val categoryMapping = mapOf(
            // 의료/건강 관련
            "의료" to "의료/건강",
            "건강" to "의료/건강",
            "병원" to "의료/건강",
            "약국" to "의료/건강",
            // 보험 (SmsParser의 "보험" 카테고리와 일치)
            "보험료" to "보험",
            // 문화/여가 관련
            "문화" to "문화/여가",
            "여가" to "문화/여가",
            "엔터테인먼트" to "문화/여가",
            "오락" to "문화/여가",
            "레저" to "문화/여가",
            // 술/유흥 관련
            "술" to "술/유흥",
            "유흥" to "술/유흥",
            "음주" to "술/유흥",
            "바" to "술/유흥",
            "호프" to "술/유흥",
            // 교통 관련
            "대중교통" to "교통",
            "택시" to "교통",
            "주유" to "교통",
            // 쇼핑 관련
            "마트" to "쇼핑",
            "온라인쇼핑" to "쇼핑",
            "편의점" to "쇼핑",
            // 운동 관련
            "헬스" to "운동",
            "피트니스" to "운동",
            "스포츠" to "운동",
            "체육" to "운동",
            // 주거 관련
            "부동산" to "주거",
            "임대" to "주거",
            "월세" to "주거",
            "전세" to "주거",
            // 생활 관련
            "공과금" to "생활",
            "통신" to "생활",
            // 경조 관련
            "경조사" to "경조",
            "축의금" to "경조",
            "조의금" to "경조",
            "부조" to "경조",
            // 기타 표현들
            "미분류" to "기타",
            "알수없음" to "기타",
            "불명" to "기타"
        )

        response.lines().forEach { line ->
            // ":" 또는 " - " 등 다양한 구분자 처리
            val parts = line.split(Regex("[:\\-]"), limit = 2)
            if (parts.size >= 2) {
                val storeName = parts[0].trim().replace(Regex("^\\d+\\.\\s*"), "")
                var category = parts[1].trim()

                // 괄호 안의 추가 정보 제거 (예: "보험 (의료/건강)" -> "보험", "생활 (구독)" -> "생활")
                category = category.replace(Regex("\\s*\\([^)]*\\)"), "").trim()

                // 카테고리 정규화
                category = normalizeCategory(category, validCategories, categoryMapping)

                // 유효한 카테고리인지 확인
                if (category in validCategories) {
                    // 원래 가게명 찾기 (정확히 일치 우선, 그 다음 부분 일치)
                    val originalName = storeNames.find { it == storeName }
                        ?: storeNames.find { it.contains(storeName) || storeName.contains(it) }

                    if (originalName != null) {
                        results[originalName] = category
                        Log.d(TAG, "파싱 성공: $originalName -> $category")
                    }
                } else {
                    Log.w(TAG, "유효하지 않은 카테고리 무시: $storeName -> $category")
                }
            }
        }

        return results
    }

    /**
     * 카테고리명 정규화 - 유효하지 않은 카테고리명을 앱의 카테고리로 변환
     */
    private fun normalizeCategory(
        rawCategory: String,
        validCategories: Set<String>,
        categoryMapping: Map<String, String>
    ): String {
        val trimmed = rawCategory.trim()

        // 이미 유효한 카테고리면 그대로 반환
        if (trimmed in validCategories) {
            return trimmed
        }

        // 매핑 테이블에서 찾기
        categoryMapping[trimmed]?.let { return it }

        // 부분 일치로 찾기
        validCategories.forEach { validCat ->
            if (trimmed.contains(validCat) || validCat.contains(trimmed)) {
                return validCat
            }
        }

        // 매핑 테이블 키와 부분 일치
        categoryMapping.entries.forEach { (key, value) ->
            if (trimmed.contains(key, ignoreCase = true)) {
                return value
            }
        }

        // 찾지 못하면 기타로 반환
        Log.w(TAG, "알 수 없는 카테고리 -> 기타로 변환: $rawCategory")
        return "기타"
    }

    /**
     * 단일 가게명 분류
     */
    suspend fun classifySingleStore(storeName: String): String? {
        val result = classifyStoreNames(listOf(storeName))
        return result[storeName]
    }
}
