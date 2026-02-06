package com.sanha.moneytalk.core.util

import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.sanha.moneytalk.core.datastore.SettingsDataStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Google Text Embedding API 연동
 * text-embedding-004 모델을 사용하여 텍스트를 벡터로 변환
 *
 * 캐싱 전략:
 * - 인메모리 LRU 캐시로 중복 임베딩 API 호출 방지
 * - 동일 텍스트는 캐시에서 즉시 반환
 */
@Singleton
class EmbeddingRepository @Inject constructor(
    private val settingsDataStore: SettingsDataStore
) {
    companion object {
        private const val TAG = "EmbeddingRepository"
        private const val MODEL_NAME = "text-embedding-004"
        private const val CACHE_MAX_SIZE = 200
    }

    private var embeddingModel: GenerativeModel? = null
    private var cachedApiKey: String? = null

    // 인메모리 임베딩 캐시 (LRU)
    private val embeddingCache = LinkedHashMap<String, FloatArray>(CACHE_MAX_SIZE, 0.75f, true)
    private val cacheMutex = Mutex()

    private suspend fun getModel(): GenerativeModel? {
        val apiKey = settingsDataStore.getGeminiApiKey()
        if (apiKey.isBlank()) return null

        if (embeddingModel == null || cachedApiKey != apiKey) {
            cachedApiKey = apiKey
            embeddingModel = GenerativeModel(
                modelName = MODEL_NAME,
                apiKey = apiKey
            )
        }
        return embeddingModel
    }

    /**
     * 텍스트를 벡터로 변환 (캐싱 포함)
     * @param text 변환할 텍스트
     * @return FloatArray 임베딩 벡터, 실패 시 null
     */
    suspend fun embed(text: String): FloatArray? {
        if (text.isBlank()) return null

        // 캐시 확인
        cacheMutex.withLock {
            embeddingCache[text]?.let { cached ->
                Log.d(TAG, "Cache hit for: ${text.take(30)}...")
                return cached
            }
        }

        return try {
            val model = getModel() ?: return null
            val response = model.embedContent(content { text(text) })
            val vector = response.embedding.values.toFloatArray()

            // 캐시에 저장
            cacheMutex.withLock {
                if (embeddingCache.size >= CACHE_MAX_SIZE) {
                    // LRU: 가장 오래된 항목 제거
                    val oldest = embeddingCache.keys.first()
                    embeddingCache.remove(oldest)
                }
                embeddingCache[text] = vector
            }

            Log.d(TAG, "Embedded text (${vector.size}d): ${text.take(30)}...")
            vector
        } catch (e: Exception) {
            Log.e(TAG, "Embedding failed for: ${text.take(30)}...", e)
            null
        }
    }

    /**
     * 여러 텍스트를 배치로 벡터 변환
     * 캐시에 있는 것은 캐시에서, 없는 것만 API 호출
     */
    suspend fun embedBatch(texts: List<String>): Map<String, FloatArray> {
        val results = mutableMapOf<String, FloatArray>()

        for (text in texts) {
            val vector = embed(text)
            if (vector != null) {
                results[text] = vector
            }
        }

        return results
    }

    /**
     * 캐시 초기화
     */
    suspend fun clearCache() {
        cacheMutex.withLock {
            embeddingCache.clear()
        }
    }

    /**
     * 현재 캐시 크기
     */
    suspend fun getCacheSize(): Int {
        return cacheMutex.withLock { embeddingCache.size }
    }
}
