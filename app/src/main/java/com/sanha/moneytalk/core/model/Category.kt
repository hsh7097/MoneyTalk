package com.sanha.moneytalk.core.model

/**
 * 지출 카테고리 (대/소 카테고리 계층 구조)
 *
 * parentCategory가 null이면 대 카테고리, non-null이면 소 카테고리.
 * 소 카테고리는 DB에 독립 저장되지만, 대 카테고리 검색 시 소 카테고리도 포함됨.
 *
 * 예: "식비" 검색 → 식비 + 배달 모두 표시
 *     "배달" 검색 → 배달만 표시
 */
enum class Category(
    val emoji: String,
    val displayName: String,
    val parentCategory: Category? = null
) {
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
    DELIVERY("🛵", "배달", FOOD),  // 식비의 소 카테고리
    ETC("📦", "기타"),
    UNCLASSIFIED("❓", "미분류");

    /** 이 카테고리가 대 카테고리인지 (소 카테고리가 아닌지) */
    val isParent: Boolean get() = parentCategory == null

    /** 이 카테고리의 소 카테고리 목록 */
    val subCategories: List<Category>
        get() = entries.filter { it.parentCategory == this }

    /** 이 카테고리 + 하위 소 카테고리의 displayName 목록 (필터링용) */
    val displayNamesIncludingSub: List<String>
        get() = listOf(displayName) + subCategories.map { it.displayName }

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

        /** 대 카테고리만 (UI 필터 드롭다운 등에서 사용) */
        val parentEntries: List<Category>
            get() = entries.filter { it.isParent }
    }
}
