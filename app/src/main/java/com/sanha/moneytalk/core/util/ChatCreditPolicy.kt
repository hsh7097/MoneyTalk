package com.sanha.moneytalk.core.util

enum class ChatCreditTier(val cost: Int) {
    FREE_LOOKUP(0),
    CHAT_MESSAGE(1)
}

data class ChatCreditDecision(
    val tier: ChatCreditTier,
    val cost: Int = tier.cost
)

object ChatCreditPolicy {

    fun estimate(message: String): ChatCreditDecision {
        return if (message.isBlank()) {
            ChatCreditDecision(ChatCreditTier.FREE_LOOKUP)
        } else {
            ChatCreditDecision(ChatCreditTier.CHAT_MESSAGE)
        }
    }
}
