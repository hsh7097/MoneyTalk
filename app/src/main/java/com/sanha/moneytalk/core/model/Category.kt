package com.sanha.moneytalk.core.model

enum class Category(val emoji: String, val displayName: String) {
    FOOD("🍔", "식비"),
    CAFE("☕", "카페"),
    TRANSPORT("🚗", "교통"),
    SHOPPING("🛒", "쇼핑"),
    SUBSCRIPTION("📱", "구독"),
    HEALTH("💊", "의료/건강"),
    CULTURE("🎬", "문화/여가"),
    EDUCATION("📚", "교육"),
    LIVING("🏠", "생활"),
    ETC("📦", "기타");

    companion object {
        fun fromDisplayName(name: String): Category {
            return entries.find { it.displayName == name } ?: ETC
        }

        fun fromName(name: String): Category {
            return entries.find { it.name == name } ?: ETC
        }
    }
}
