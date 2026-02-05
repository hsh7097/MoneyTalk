package com.sanha.moneytalk.core.database.dao

import androidx.room.*
import com.sanha.moneytalk.core.database.entity.BudgetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetDao {

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
}
