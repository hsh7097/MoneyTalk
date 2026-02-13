package com.sanha.moneytalk.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.sanha.moneytalk.core.database.entity.BudgetEntity
import kotlinx.coroutines.flow.Flow

/**
 * 예산 DAO
 *
 * 카테고리별 월간 예산 한도에 대한 CRUD를 제공합니다.
 * yearMonth 기준으로 월별 독립적인 예산을 관리합니다.
 *
 * @see BudgetEntity
 */
@Dao
interface BudgetDao {

    /** 예산 설정 삽입/업데이트 (충돌 시 교체) */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(budget: BudgetEntity)

    @Update
    suspend fun update(budget: BudgetEntity)

    @Delete
    suspend fun delete(budget: BudgetEntity)

    @Query("SELECT * FROM budgets WHERE yearMonth = :yearMonth")
    fun getBudgetsByMonth(yearMonth: String): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets WHERE category = :category AND yearMonth = :yearMonth")
    suspend fun getBudgetByCategory(category: String, yearMonth: String): BudgetEntity?

    @Query("SELECT SUM(monthlyLimit) FROM budgets WHERE yearMonth = :yearMonth")
    suspend fun getTotalBudgetByMonth(yearMonth: String): Int?

    @Query("DELETE FROM budgets WHERE yearMonth = :yearMonth")
    suspend fun deleteAllByMonth(yearMonth: String)

    @Query("DELETE FROM budgets")
    suspend fun deleteAll()
}
