package com.sanha.moneytalk.core.util

import java.util.Locale

enum class ChatCreditTier(val cost: Int) {
    FREE_LOOKUP(0),
    LIGHT_ADVICE(1),
    STANDARD_ANALYSIS(3),
    DEEP_ANALYSIS(10)
}

data class ChatCreditDecision(
    val tier: ChatCreditTier,
    val cost: Int = tier.cost
)

object ChatCreditPolicy {

    fun estimate(message: String): ChatCreditDecision {
        val normalized = message.trim().lowercase(Locale.KOREA)
        if (normalized.isBlank()) return ChatCreditDecision(ChatCreditTier.FREE_LOOKUP)
        if (isSimpleConversation(normalized)) return ChatCreditDecision(ChatCreditTier.FREE_LOOKUP)
        if (hasAny(normalized, deepAnalysisKeywords)) {
            return ChatCreditDecision(ChatCreditTier.DEEP_ANALYSIS)
        }
        if (hasAny(normalized, standardAnalysisKeywords)) {
            return ChatCreditDecision(ChatCreditTier.STANDARD_ANALYSIS)
        }
        if (hasAny(normalized, lightAdviceKeywords)) {
            return ChatCreditDecision(ChatCreditTier.LIGHT_ADVICE)
        }
        return ChatCreditDecision(ChatCreditTier.FREE_LOOKUP)
    }

    private fun isSimpleConversation(message: String): Boolean {
        return simpleConversationKeywords.any { keyword ->
            message == keyword || message == "$keyword." || message == "$keyword!"
        }
    }

    private fun hasAny(message: String, keywords: List<String>): Boolean {
        return keywords.any { message.contains(it) }
    }

    private val simpleConversationKeywords = listOf(
        "안녕",
        "고마워",
        "감사",
        "오케이",
        "ok",
        "ㅇㅋ"
    )

    private val deepAnalysisKeywords = listOf(
        "심층",
        "상세 리포트",
        "상세한 리포트",
        "전체 데이터 분석",
        "json 분석",
        "정밀 분석",
        "종합 리포트"
    )

    private val standardAnalysisKeywords = listOf(
        "분석",
        "흐름",
        "추세",
        "비교",
        "늘어",
        "증가",
        "감소",
        "패턴",
        "소비 형태",
        "적절",
        "4인가족",
        "가족 기준",
        "재무 상담",
        "상담해",
        "상담해줘"
    )

    private val lightAdviceKeywords = listOf(
        "어때",
        "많아",
        "많은",
        "많이",
        "괜찮",
        "줄일",
        "줄여",
        "절약",
        "추천",
        "조언",
        "평가",
        "봐줘",
        "과소비",
        "낭비",
        "부담"
    )
}
