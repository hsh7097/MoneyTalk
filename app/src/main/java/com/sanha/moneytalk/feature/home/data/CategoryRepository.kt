package com.sanha.moneytalk.feature.home.data

import com.sanha.moneytalk.core.database.dao.CategoryMappingDao
import com.sanha.moneytalk.core.database.entity.CategoryMappingEntity
import com.sanha.moneytalk.core.model.Category
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CategoryRepository @Inject constructor(
    private val categoryMappingDao: CategoryMappingDao
) {
    /**
     * 가게명으로 카테고리 조회
     * 1. 정확히 일치하는 매핑 검색
     * 2. 부분 일치하는 매핑 검색
     * 3. 없으면 null 반환
     */
    suspend fun getCategoryByStoreName(storeName: String): String? {
        // 정확히 일치
        categoryMappingDao.getCategoryByStoreName(storeName)?.let {
            return it.category
        }

        // 부분 일치
        categoryMappingDao.getCategoryByStoreNamePartial(storeName)?.let {
            return it.category
        }

        return null
    }

    /**
     * 새로운 가게명-카테고리 매핑 저장
     */
    suspend fun saveMapping(storeName: String, category: String, source: String = "local") {
        val mapping = CategoryMappingEntity(
            storeName = storeName,
            category = category,
            source = source
        )
        categoryMappingDao.insertOrReplace(mapping)
    }

    /**
     * 여러 매핑 일괄 저장
     */
    suspend fun saveMappings(mappings: List<Pair<String, String>>, source: String = "gemini") {
        val entities = mappings.map { (storeName, category) ->
            CategoryMappingEntity(
                storeName = storeName,
                category = category,
                source = source
            )
        }
        categoryMappingDao.insertAll(entities)
    }

    /**
     * 기존 매핑의 카테고리 업데이트
     */
    suspend fun updateCategory(storeName: String, category: String, source: String = "user") {
        categoryMappingDao.updateCategoryByStoreName(storeName, category, source)
    }

    /**
     * 모든 매핑 조회
     */
    fun getAllMappings(): Flow<List<CategoryMappingEntity>> {
        return categoryMappingDao.getAllMappings()
    }

    /**
     * 모든 매핑 조회 (한 번)
     */
    suspend fun getAllMappingsOnce(): List<CategoryMappingEntity> {
        return categoryMappingDao.getAllMappingsOnce()
    }

    /**
     * 매핑 개수
     */
    suspend fun getMappingCount(): Int {
        return categoryMappingDao.getMappingCount()
    }

    /**
     * 매핑 삭제
     */
    suspend fun deleteMapping(id: Long) {
        categoryMappingDao.deleteById(id)
    }

    /**
     * 모든 매핑 삭제
     */
    suspend fun deleteAllMappings() {
        categoryMappingDao.deleteAll()
    }

    /**
     * 사용 가능한 카테고리 목록
     */
    fun getAvailableCategories(): List<String> {
        return Category.entries.map { it.displayName }
    }
}
