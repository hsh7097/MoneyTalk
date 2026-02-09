package com.sanha.moneytalk.core.model

import androidx.annotation.DrawableRes
import com.sanha.moneytalk.R

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
    @DrawableRes val iconRes: Int,
    val parentCategory: Category? = null
) {
    FOOD("🍔", "식비", R.drawable.ic_category_food),
    CAFE("☕", "카페", R.drawable.ic_category_cafe),
    DRINKING("🍺", "술/유흥", R.drawable.ic_category_drinking),
    TRANSPORT("🚗", "교통", R.drawable.ic_category_transport),
    SHOPPING("🛒", "쇼핑", R.drawable.ic_category_shopping),
    SUBSCRIPTION("📱", "구독", R.drawable.ic_category_subscription),
    HEALTH("💊", "의료/건강", R.drawable.ic_category_health),
    FITNESS("💪", "운동", R.drawable.ic_category_fitness),
    CULTURE("🎬", "문화/여가", R.drawable.ic_category_culture),
    EDUCATION("📚", "교육", R.drawable.ic_category_education),
    HOUSING("🏢", "주거", R.drawable.ic_category_housing),
    LIVING("🏠", "생활", R.drawable.ic_category_living),
    INSURANCE("🛡️", "보험", R.drawable.ic_category_insurance),
    TRANSFER("🔄", "계좌이체", R.drawable.ic_category_transfer),
    EVENTS("🎁", "경조", R.drawable.ic_category_events),
    DELIVERY("🛵", "배달", R.drawable.ic_category_delivery, FOOD),  // 식비의 소 카테고리
    ETC("📦", "기타", R.drawable.ic_category_etc),
    UNCLASSIFIED("❓", "미분류", R.drawable.ic_category_unclassified);

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
