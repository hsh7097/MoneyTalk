package com.sanha.moneytalk.core.database.dao

import androidx.room.*
import com.sanha.moneytalk.core.database.entity.MerchantVectorEntity

@Dao
interface MerchantVectorDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(merchant: MerchantVectorEntity): Long

    @Update
    suspend fun update(merchant: MerchantVectorEntity)

    @Query("SELECT * FROM merchant_vectors ORDER BY matchCount DESC")
    suspend fun getAllMerchants(): List<MerchantVectorEntity>

    @Query("SELECT * FROM merchant_vectors WHERE merchantName = :name LIMIT 1")
    suspend fun getMerchantByName(name: String): MerchantVectorEntity?

    @Query("SELECT * FROM merchant_vectors WHERE category = :category ORDER BY matchCount DESC")
    suspend fun getMerchantsByCategory(category: String): List<MerchantVectorEntity>

    @Query("SELECT * FROM merchant_vectors WHERE id = :id")
    suspend fun getMerchantById(id: Long): MerchantVectorEntity?

    @Query("UPDATE merchant_vectors SET matchCount = matchCount + 1, lastMatchedAt = :timestamp WHERE id = :id")
    suspend fun incrementMatchCount(id: Long, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE merchant_vectors SET category = :newCategory WHERE id = :id")
    suspend fun updateCategory(id: Long, newCategory: String)

    @Query("SELECT COUNT(*) FROM merchant_vectors")
    suspend fun getMerchantCount(): Int

    @Delete
    suspend fun delete(merchant: MerchantVectorEntity)
}
