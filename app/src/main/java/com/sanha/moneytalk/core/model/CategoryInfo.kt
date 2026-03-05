package com.sanha.moneytalk.core.model

/**
 * 카테고리 통합 인터페이스.
 *
 * 기본(enum) 카테고리와 사용자 커스텀 카테고리를 동일하게 취급하기 위한 공통 계약.
 * - [Category] enum이 이 인터페이스를 구현한다.
 * - [CustomCategoryInfo]가 커스텀 카테고리용으로 이 인터페이스를 구현한다.
 */
interface CategoryInfo {
    val displayName: String
    val emoji: String
    val categoryType: CategoryType
    val isCustom: Boolean get() = false
}

/**
 * 커스텀 카테고리 정보.
 * Room [CustomCategoryEntity]를 UI/비즈니스 레이어에서 사용하기 위한 래퍼.
 */
data class CustomCategoryInfo(
    val id: Long,
    override val displayName: String,
    override val emoji: String,
    override val categoryType: CategoryType,
    val displayOrder: Int = 0
) : CategoryInfo {
    override val isCustom: Boolean get() = true
}
