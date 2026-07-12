package com.sanha.moneytalk.core.sms

import com.sanha.moneytalk.core.util.StoreAliasManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Normalizer
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

/**
 * Creates deterministic local text vectors for SMS templates and store names.
 *
 * The 768-dimensional contract is retained so the existing vector pipeline can keep its data
 * shape without exposing a Gemini API key. Character n-grams favor structural similarity, which
 * is the signal needed after SMS amounts, dates, and store values have been templateized.
 */
@Singleton
class SmsEmbeddingService @Inject constructor() {

    companion object {
        const val EMBEDDING_DIMENSION = 768

        private const val FNV_OFFSET_BASIS = 2166136261L
        private const val FNV_PRIME = 16777619L
        private const val UINT_MASK = 0xffffffffL
        private val WHITESPACE = Regex("\\s+")
    }

    suspend fun generateEmbedding(text: String): List<Float>? = withContext(Dispatchers.Default) {
        createEmbedding(text)
    }

    suspend fun generateEmbeddings(texts: List<String>): List<List<Float>?> =
        withContext(Dispatchers.Default) {
            texts.map(::createEmbedding)
        }

    suspend fun generateStoreEmbedding(storeName: String): List<Float>? =
        withContext(Dispatchers.Default) {
            createStoreEmbedding(storeName)
        }

    suspend fun generateStoreEmbeddings(storeNames: List<String>): List<List<Float>?> =
        withContext(Dispatchers.Default) {
            storeNames.map(::createStoreEmbedding)
        }

    internal fun createEmbedding(text: String): List<Float>? {
        val normalized = normalize(text) ?: return null

        val vector = FloatArray(EMBEDDING_DIMENSION)
        addFeature(vector, "full:$normalized", 3f)

        val bounded = "^$normalized$"
        for (size in 1..3) {
            val weight = when (size) {
                1 -> 0.5f
                2 -> 1.0f
                else -> 1.5f
            }
            for (index in 0..bounded.length - size) {
                addFeature(
                    vector = vector,
                    feature = "$size:${bounded.substring(index, index + size)}",
                    weight = weight
                )
            }
        }

        var squaredNorm = 0f
        vector.forEach { value -> squaredNorm += value * value }
        if (squaredNorm == 0f) return null

        val norm = sqrt(squaredNorm)
        return vector.map { it / norm }
    }

    internal fun createStoreEmbedding(storeName: String): List<Float>? {
        val canonicalName = StoreAliasManager.normalizeStoreName(storeName) ?: storeName
        return createEmbedding(canonicalName)
    }

    private fun normalize(text: String): String? {
        val tokens = Normalizer.normalize(text, Normalizer.Form.NFKC)
            .lowercase(Locale.KOREA)
            .trim()
            .split(WHITESPACE)
            .filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return null

        return tokens.joinToString(separator = "")
    }

    private fun addFeature(vector: FloatArray, feature: String, weight: Float) {
        var hash = FNV_OFFSET_BASIS
        feature.forEach { char ->
            hash = (hash xor char.code.toLong()) * FNV_PRIME and UINT_MASK
        }
        val index = (hash and Int.MAX_VALUE.toLong()).rem(EMBEDDING_DIMENSION).toInt()
        val sign = if (hash and 0x80000000L == 0L) 1f else -1f
        vector[index] += sign * weight
    }

}
