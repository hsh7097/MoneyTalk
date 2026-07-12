package com.sanha.moneytalk.core.firebase

import kotlin.math.ceil

internal object FirebaseAiRateLimitPolicy {

    private const val RETRY_BUFFER_MS = 500L
    private const val MAX_RETRY_DELAY_MS = 65_000L
    private val retryAfterRegex = Regex(
        pattern = """retry in\s+([0-9]+(?:\.[0-9]+)?)s""",
        option = RegexOption.IGNORE_CASE
    )

    fun isRateLimitError(errorClassName: String, errorMessage: String): Boolean {
        return errorClassName.contains("QuotaExceededException") ||
            errorMessage.contains("429") ||
            errorMessage.contains("RESOURCE_EXHAUSTED") ||
            errorMessage.contains("rate limit", ignoreCase = true) ||
            errorMessage.contains("quota exceeded", ignoreCase = true) ||
            errorMessage.contains("exceeded your current quota", ignoreCase = true)
    }

    fun retryAfterMillis(errorMessage: String): Long? {
        val seconds = retryAfterRegex.find(errorMessage)
            ?.groupValues
            ?.getOrNull(1)
            ?.toDoubleOrNull()
            ?: return null
        return (ceil(seconds * 1000).toLong() + RETRY_BUFFER_MS)
            .coerceAtMost(MAX_RETRY_DELAY_MS)
    }
}
