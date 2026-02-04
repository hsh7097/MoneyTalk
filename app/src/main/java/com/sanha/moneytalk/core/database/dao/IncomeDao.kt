package com.sanha.moneytalk.core.database.dao

import androidx.room.*
import com.sanha.moneytalk.core.database.entity.IncomeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface IncomeDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(income: IncomeEntity): Long

    @Update
    suspend fun update(income: IncomeEntity)

    @Delete
    suspend fun delete(income: IncomeEntity)

    @Query("DELETE FROM incomes WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM incomes ORDER BY dateTime DESC")
    fun getAllIncomes(): Flow<List<IncomeEntity>>

    @Query("SELECT * FROM incomes WHERE id = :id")
    suspend fun getIncomeById(id: Long): IncomeEntity?

    @Query("SELECT * FROM incomes WHERE isRecurring = 1")
    fun getRecurringIncomes(): Flow<List<IncomeEntity>>

    @Query("SELECT SUM(amount) FROM incomes WHERE dateTime BETWEEN :startTime AND :endTime")
    suspend fun getTotalIncomeByDateRange(startTime: Long, endTime: Long): Int?

    @Query("SELECT * FROM incomes WHERE dateTime BETWEEN :startTime AND :endTime ORDER BY dateTime DESC")
    fun getIncomesByDateRange(startTime: Long, endTime: Long): Flow<List<IncomeEntity>>
}
