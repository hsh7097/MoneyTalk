package com.sanha.moneytalk.core.database

import com.sanha.moneytalk.core.database.dao.CustomCategoryDao
import com.sanha.moneytalk.core.database.entity.CustomCategoryEntity
import com.sanha.moneytalk.core.model.Category
import com.sanha.moneytalk.core.model.CategoryType
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 커스텀 카테고리 Repository.
 *
 * CRUD 위임 + displayName 중복 검증.
 */
@Singleton
class CustomCategoryRepository @Inject constructor(
    private val customCategoryDao: CustomCategoryDao
) {
    fun getAllFlow(): Flow<List<CustomCategoryEntity>> = customCategoryDao.getAllFlow()

    suspend fun getAll(): List<CustomCategoryEntity> = customCategoryDao.getAll()

    suspend fun getByType(type: CategoryType): List<CustomCategoryEntity> =
        customCategoryDao.getByType(type.name)

    suspend fun add(displayName: String, emoji: String, categoryType: CategoryType): Long {
        val entity = CustomCategoryEntity(
            displayName = displayName,
            emoji = emoji,
            categoryType = categoryType.name
        )
        return customCategoryDao.insert(entity)
    }

    suspend fun insertAll(entities: List<CustomCategoryEntity>) {
        if (entities.isNotEmpty()) {
            customCategoryDao.insertAll(entities)
        }
    }

    suspend fun update(entity: CustomCategoryEntity) = customCategoryDao.update(entity)

    suspend fun delete(id: Long) = customCategoryDao.deleteById(id)

    /**
     * displayName 중복 확인 (enum + DB).
     */
    suspend fun isDuplicate(name: String, type: CategoryType): Boolean {
        val enumExists = Category.entries.any {
            it.displayName == name && it.categoryType == type
        }
        if (enumExists) return true
        return customCategoryDao.findByNameAndType(name, type.name) != null
    }
}
