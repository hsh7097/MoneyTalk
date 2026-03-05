package com.sanha.moneytalk.core.model

import com.sanha.moneytalk.core.database.CustomCategoryRepository
import com.sanha.moneytalk.core.database.entity.CustomCategoryEntity
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 카테고리 통합 제공자.
 *
 * 기본(enum) 카테고리와 사용자 커스텀 카테고리를 통합하여
 * 하나의 목록으로 제공한다.
 *
 * 인메모리 캐시를 통해 동기 접근도 지원하며,
 * 카테고리 추가/삭제 시 [invalidateCache]를 호출하여 갱신한다.
 */
@Singleton
class CategoryProvider @Inject constructor(
    private val customCategoryRepository: CustomCategoryRepository
) {
    init {
        instance = this
    }

    /** 인메모리 캐시 */
    @Volatile
    private var cachedCustomCategories: List<CustomCategoryInfo>? = null

    /**
     * 커스텀 카테고리 목록 로드 (캐시 우선).
     */
    suspend fun getCustomCategories(): List<CustomCategoryInfo> {
        cachedCustomCategories?.let { return it }
        val entities = customCategoryRepository.getAll()
        val result = entities.map { it.toCategoryInfo() }
        cachedCustomCategories = result
        return result
    }

    /**
     * 캐시된 커스텀 카테고리 (동기 접근용).
     * 캐시가 없으면 빈 리스트 반환.
     */
    fun getCachedCustomCategories(): List<CustomCategoryInfo> {
        return cachedCustomCategories.orEmpty()
    }

    // ── 타입별 통합 목록 (suspend) ──

    suspend fun getExpenseEntries(): List<CategoryInfo> {
        return Category.expenseEntries + getCustomCategories().filter {
            it.categoryType == CategoryType.EXPENSE
        }
    }

    suspend fun getIncomeEntries(): List<CategoryInfo> {
        return Category.incomeEntries + getCustomCategories().filter {
            it.categoryType == CategoryType.INCOME
        }
    }

    suspend fun getTransferEntries(): List<CategoryInfo> {
        return Category.transferEntries + getCustomCategories().filter {
            it.categoryType == CategoryType.TRANSFER
        }
    }

    // ── 타입별 통합 목록 (캐시 기반 동기) ──

    fun getCachedExpenseEntries(): List<CategoryInfo> {
        return Category.expenseEntries + getCachedCustomCategories().filter {
            it.categoryType == CategoryType.EXPENSE
        }
    }

    fun getCachedIncomeEntries(): List<CategoryInfo> {
        return Category.incomeEntries + getCachedCustomCategories().filter {
            it.categoryType == CategoryType.INCOME
        }
    }

    fun getCachedTransferEntries(): List<CategoryInfo> {
        return Category.transferEntries + getCachedCustomCategories().filter {
            it.categoryType == CategoryType.TRANSFER
        }
    }

    // ── Gemini 프롬프트용 ──

    /**
     * 지출 카테고리 displayName 목록 (Gemini 프롬프트용).
     */
    suspend fun getExpenseDisplayNames(): List<String> {
        return getExpenseEntries().map { it.displayName }
    }

    /**
     * 수입 카테고리 displayName 목록 (Gemini 프롬프트용).
     */
    suspend fun getIncomeDisplayNames(): List<String> {
        return getIncomeEntries().map { it.displayName }
    }

    /**
     * 유효한 지출 카테고리 이름 셋 (Gemini 응답 검증용).
     */
    suspend fun getValidExpenseNames(): Set<String> {
        return getExpenseEntries().map { it.displayName }.toSet()
    }

    /**
     * 유효한 수입 카테고리 이름 셋 (Gemini 응답 검증용).
     */
    suspend fun getValidIncomeNames(): Set<String> {
        return getIncomeEntries().map { it.displayName }.toSet()
    }

    // ── 통합 조회 ──

    /**
     * displayName으로 CategoryInfo 조회 (enum + custom).
     * 못 찾으면 null 반환.
     */
    suspend fun fromDisplayName(
        name: String,
        type: CategoryType? = null
    ): CategoryInfo? {
        // enum 먼저 검색
        val enumMatch = Category.entries.find {
            it.displayName == name && (type == null || it.categoryType == type)
        }
        if (enumMatch != null) return enumMatch

        // 커스텀 검색
        val customs = getCustomCategories()
        return customs.find {
            it.displayName == name && (type == null || it.categoryType == type)
        }
    }

    /**
     * 캐시 무효화 (카테고리 추가/삭제 시 호출).
     */
    fun invalidateCache() {
        cachedCustomCategories = null
    }

    /** Composable에서 Hilt 주입 없이 CategoryProvider 접근용 EntryPoint */
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Provider {
        fun categoryProvider(): CategoryProvider
    }

    companion object {
        @Volatile
        private var instance: CategoryProvider? = null

        /**
         * displayName으로 이모지 조회 (enum + 캐시).
         * 동기 접근 전용. 커스텀 카테고리 이모지도 정확히 반환.
         */
        fun resolveEmoji(displayName: String): String {
            val enumMatch = Category.entries.find { it.displayName == displayName }
            if (enumMatch != null) return enumMatch.emoji
            val customMatch = instance?.getCachedCustomCategories()
                ?.find { it.displayName == displayName }
            return customMatch?.emoji ?: Category.ETC.emoji
        }

        /**
         * displayName으로 CategoryInfo 조회 (enum + 캐시, 동기).
         * 못 찾으면 null 반환.
         */
        fun resolveCategoryInfo(displayName: String): CategoryInfo? {
            val enumMatch = Category.entries.find { it.displayName == displayName }
            if (enumMatch != null) return enumMatch
            return instance?.getCachedCustomCategories()
                ?.find { it.displayName == displayName }
        }
    }

    private fun CustomCategoryEntity.toCategoryInfo(): CustomCategoryInfo {
        val type = try {
            CategoryType.valueOf(categoryType)
        } catch (e: IllegalArgumentException) {
            CategoryType.EXPENSE
        }
        return CustomCategoryInfo(
            id = id,
            displayName = displayName,
            emoji = emoji,
            categoryType = type,
            displayOrder = displayOrder
        )
    }
}
