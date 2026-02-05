package com.sanha.moneytalk.core.database.dao

import androidx.room.*
import com.sanha.moneytalk.core.database.entity.ExpenseEntity
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

    @Query("SELECT * FROM expenses WHERE dateTime BETWEEN :startTime AND :endTime ORDER BY dateTime DESC")
    suspend fun getExpensesByDateRangeOnce(startTime: Long, endTime: Long): List<ExpenseEntity>

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

    // 카드사별 필터링
    @Query("SELECT * FROM expenses WHERE cardName = :cardName ORDER BY dateTime DESC")
    fun getExpensesByCardName(cardName: String): Flow<List<ExpenseEntity>>

    // 카드사 + 기간 필터링
    @Query("SELECT * FROM expenses WHERE cardName = :cardName AND dateTime BETWEEN :startTime AND :endTime ORDER BY dateTime DESC")
    fun getExpensesByCardNameAndDateRange(cardName: String, startTime: Long, endTime: Long): Flow<List<ExpenseEntity>>

    // 카테고리 + 기간 필터링
    @Query("SELECT * FROM expenses WHERE category = :category AND dateTime BETWEEN :startTime AND :endTime ORDER BY dateTime DESC")
    fun getExpensesByCategoryAndDateRange(category: String, startTime: Long, endTime: Long): Flow<List<ExpenseEntity>>

    // 모든 필터 적용 (카드사 + 카테고리 + 기간)
    @Query("SELECT * FROM expenses WHERE (:cardName IS NULL OR cardName = :cardName) AND (:category IS NULL OR category = :category) AND dateTime BETWEEN :startTime AND :endTime ORDER BY dateTime DESC")
    fun getExpensesFiltered(cardName: String?, category: String?, startTime: Long, endTime: Long): Flow<List<ExpenseEntity>>

    // 모든 카드사 목록 가져오기
    @Query("SELECT DISTINCT cardName FROM expenses ORDER BY cardName")
    suspend fun getAllCardNames(): List<String>

    // 모든 카테고리 목록 가져오기
    @Query("SELECT DISTINCT category FROM expenses ORDER BY category")
    suspend fun getAllCategories(): List<String>

    // 날짜별 총액 (일별 합계)
    @Query("SELECT date(dateTime/1000, 'unixepoch', 'localtime') as date, SUM(amount) as total FROM expenses WHERE dateTime BETWEEN :startTime AND :endTime GROUP BY date ORDER BY date DESC")
    suspend fun getDailyTotals(startTime: Long, endTime: Long): List<DailySum>

    // 월별 총액
    @Query("SELECT strftime('%Y-%m', dateTime/1000, 'unixepoch', 'localtime') as month, SUM(amount) as total FROM expenses GROUP BY month ORDER BY month DESC")
    suspend fun getMonthlyTotals(): List<MonthlySum>

    // 백업용 - 모든 지출 한번에 가져오기
    @Query("SELECT * FROM expenses ORDER BY dateTime DESC")
    suspend fun getAllExpensesOnce(): List<ExpenseEntity>

    // 모든 데이터 삭제 (초기화용)
    @Query("DELETE FROM expenses")
    suspend fun deleteAll()
}

data class CategorySum(
    val category: String,
    val total: Int
)

data class DailySum(
    val date: String,
    val total: Int
)

data class MonthlySum(
    val month: String,
    val total: Int
)
