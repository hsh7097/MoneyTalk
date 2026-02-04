package com.sanha.moneytalk.data.local.dao

import androidx.room.*
import com.sanha.moneytalk.data.local.entity.ExpenseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(expense: ExpenseEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(expenses: List<ExpenseEntity>)

    @Update
    suspend fun update(expense: ExpenseEntity)

    @Delete
    suspend fun delete(expense: ExpenseEntity)

    @Query("DELETE FROM expenses WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM expenses ORDER BY dateTime DESC")
    fun getAllExpenses(): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE id = :id")
    suspend fun getExpenseById(id: Long): ExpenseEntity?

    @Query("SELECT * FROM expenses WHERE smsId = :smsId LIMIT 1")
    suspend fun getExpenseBySmsId(smsId: String): ExpenseEntity?

    @Query("SELECT * FROM expenses WHERE dateTime BETWEEN :startTime AND :endTime ORDER BY dateTime DESC")
    fun getExpensesByDateRange(startTime: Long, endTime: Long): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE category = :category ORDER BY dateTime DESC")
    fun getExpensesByCategory(category: String): Flow<List<ExpenseEntity>>

    @Query("SELECT SUM(amount) FROM expenses WHERE dateTime BETWEEN :startTime AND :endTime")
    suspend fun getTotalExpenseByDateRange(startTime: Long, endTime: Long): Int?

    @Query("SELECT category, SUM(amount) as total FROM expenses WHERE dateTime BETWEEN :startTime AND :endTime GROUP BY category")
    suspend fun getExpenseSumByCategory(startTime: Long, endTime: Long): List<CategorySum>

    @Query("SELECT * FROM expenses ORDER BY dateTime DESC LIMIT :limit")
    suspend fun getRecentExpenses(limit: Int): List<ExpenseEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM expenses WHERE smsId = :smsId)")
    suspend fun existsBySmsId(smsId: String): Boolean
}

data class CategorySum(
    val category: String,
    val total: Int
)
