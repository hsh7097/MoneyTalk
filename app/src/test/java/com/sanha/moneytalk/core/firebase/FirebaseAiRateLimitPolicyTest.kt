package com.sanha.moneytalk.core.firebase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FirebaseAiRateLimitPolicyTest {

    @Test
    fun recognizesFirebaseQuotaException() {
        assertTrue(
            FirebaseAiRateLimitPolicy.isRateLimitError(
                errorClassName = "com.google.firebase.ai.type.QuotaExceededException",
                errorMessage = "You exceeded your current quota"
            )
        )
    }

    @Test
    fun recognizesQuotaMessageWithout429Text() {
        assertTrue(
            FirebaseAiRateLimitPolicy.isRateLimitError(
                errorClassName = "java.lang.Exception",
                errorMessage = "Quota exceeded for metric: generate_content_free_tier_requests"
            )
        )
    }

    @Test
    fun ignoresUnrelatedServerError() {
        assertFalse(
            FirebaseAiRateLimitPolicy.isRateLimitError(
                errorClassName = "com.google.firebase.ai.type.ServerException",
                errorMessage = "Model is unavailable"
            )
        )
    }

    @Test
    fun recognizesInvalidAppCheckToken() {
        assertTrue(
            FirebaseAiRateLimitPolicy.isAppCheckFailure(
                errorClassName = "com.google.firebase.ai.type.ServerException",
                errorMessage = "Firebase App Check token is invalid."
            )
        )
    }

    @Test
    fun ignoresUnrelatedFirebaseServerErrorForAppCheckPolicy() {
        assertFalse(
            FirebaseAiRateLimitPolicy.isAppCheckFailure(
                errorClassName = "com.google.firebase.ai.type.ServerException",
                errorMessage = "Model is unavailable"
            )
        )
    }

    @Test
    fun recognizesAppCheckFailureInsideWrappedQueryError() {
        val error = Exception("쿼리 분석 실패", Exception("Firebase App Check token is invalid."))

        assertTrue(FirebaseAiRateLimitPolicy.isAppCheckFailure(error))
    }

    @Test
    fun preservesNormalQueryFallbackForQuotaAndParseFailures() {
        val quotaError = Exception("쿼리 분석 실패", Exception("429 RESOURCE_EXHAUSTED"))
        val parseError = Exception("쿼리 분석 실패", IllegalArgumentException("Invalid JSON token"))

        assertFalse(FirebaseAiRateLimitPolicy.isAppCheckFailure(quotaError))
        assertFalse(FirebaseAiRateLimitPolicy.isAppCheckFailure(parseError))
    }

    @Test
    fun recognizesTooManyAttemptsOnlyWithAppCheckContext() {
        assertTrue(FirebaseAiRateLimitPolicy.isAppCheckFailure(
            "com.google.firebase.appcheck.FirebaseAppCheckException", "Too many attempts."
        ))
        assertFalse(FirebaseAiRateLimitPolicy.isAppCheckFailure(
            "com.google.firebase.FirebaseException", "Too many attempts."
        ))
    }

    @Test
    fun parsesRetryAfterWithSafetyBuffer() {
        assertEquals(
            12_424L,
            FirebaseAiRateLimitPolicy.retryAfterMillis("Please retry in 11.923355489s.")
        )
    }
}
