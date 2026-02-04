package com.sanha.moneytalk.data.remote.dto

import com.google.gson.annotations.SerializedName

// Request
data class ClaudeRequest(
    val model: String = "claude-3-haiku-20240307",
    @SerializedName("max_tokens")
    val maxTokens: Int = 1024,
    val messages: List<ClaudeMessage>
)

data class ClaudeMessage(
    val role: String,  // "user" 또는 "assistant"
    val content: String
)

// Response
data class ClaudeResponse(
    val id: String,
    val type: String,
    val role: String,
    val content: List<ContentBlock>,
    val model: String,
    @SerializedName("stop_reason")
    val stopReason: String?,
    val usage: Usage?
)

data class ContentBlock(
    val type: String,
    val text: String
)

data class Usage(
    @SerializedName("input_tokens")
    val inputTokens: Int,
    @SerializedName("output_tokens")
    val outputTokens: Int
)

// 문자 분석 결과
data class SmsAnalysisResult(
    val amount: Int,
    val storeName: String,
    val category: String,
    val dateTime: String,
    val cardName: String
)
