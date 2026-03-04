package com.sanha.moneytalk.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.sanha.moneytalk.core.database.entity.CustomCategoryEntity
import kotlinx.coroutines.flow.Flow

/**
 * 커스텀 카테고리 DAO.
 */
@Dao
interface CustomCategoryDao {

    @Query("SELECT * FROM custom_categories ORDER BY displayOrder ASC, createdAt ASC")
    fun getAllFlow(): Flow<List<CustomCategoryEntity>>

    @Query("SELECT * FROM custom_categories ORDER BY displayOrder ASC, createdAt ASC")
    suspend fun getAll(): List<CustomCategoryEntity>

    @Query("SELECT * FROM custom_categories WHERE categoryType = :type ORDER BY displayOrder ASC, createdAt ASC")
    suspend fun getByType(type: String): List<CustomCategoryEntity>

    @Query("SELECT * FROM custom_categories WHERE displayName = :name AND categoryType = :type LIMIT 1")
    suspend fun findByNameAndType(name: String, type: String): CustomCategoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: CustomCategoryEntity): Long

    @Update
    suspend fun update(entity: CustomCategoryEntity)

    @Query("DELETE FROM custom_categories WHERE id = :id")
    suspend fun deleteById(id: Long)
}
