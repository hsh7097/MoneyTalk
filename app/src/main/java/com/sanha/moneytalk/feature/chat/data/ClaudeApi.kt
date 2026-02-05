package com.sanha.moneytalk.feature.chat.data

import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface ClaudeApi {

    @POST("v1/messages")
    suspend fun sendMessage(
        @Header("x-api-key") apiKey: String,
        @Header("anthropic-version") version: String = "2023-06-01",
        @Header("content-type") contentType: String = "application/json",
        @Body request: ClaudeRequest
    ): ClaudeResponse

    companion object {
        const val BASE_URL = "https://api.anthropic.com/"
    }
}
