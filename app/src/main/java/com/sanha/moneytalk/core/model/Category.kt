package com.sanha.moneytalk.core.model

enum class Category(val emoji: String, val displayName: String) {
    FOOD("🍔", "식비"),
    CAFE("☕", "카페"),
    DRINKING("🍺", "술/유흥"),
    TRANSPORT("🚗", "교통"),
    SHOPPING("🛒", "쇼핑"),
    SUBSCRIPTION("📱", "구독"),
    HEALTH("💊", "의료/건강"),
    FITNESS("💪", "운동"),
    CULTURE("🎬", "문화/여가"),
    EDUCATION("📚", "교육"),
    HOUSING("🏢", "주거"),
    LIVING("🏠", "생활"),
    EVENTS("🎁", "경조"),
    ETC("📦", "기타"),
    UNCLASSIFIED("❓", "미분류");

    companion object {
        fun fromDisplayName(name: String): Category {
            return entries.find { it.displayName == name } ?: ETC
        }

        fun fromName(name: String): Category {
            return entries.find { it.name == name } ?: ETC
        }

        /** 분류용 카테고리 목록 (미분류 제외) */
        val classifiableEntries: List<Category>
            get() = entries.filter { it != UNCLASSIFIED }
    }
}
