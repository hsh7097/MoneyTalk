package com.sanha.moneytalk.core.database.dao

import androidx.room.*
import com.sanha.moneytalk.core.database.entity.IncomeEntity
import kotlinx.coroutines.flow.Flow

/**
 * 수입 내역 DAO
 *
 * 입금 SMS에서 자동 파싱된 수입 및 수동 입력 수입에 대한 CRUD를 제공합니다.
 *
 * 주요 기능:
 * - 기본 CRUD (삽입, 수정, 삭제)
 * - 기간별 수입 조회 및 합계
 * - 고정 수입 조회 (isRecurring = true)
 * - SMS ID 기반 중복 방지
 *
 * @see IncomeEntity
 */
@Dao
interface IncomeDao {

    /** 단일 수입 삽입 */
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

    @Query("SELECT * FROM incomes WHERE smsId = :smsId LIMIT 1")
    suspend fun getIncomeBySmsId(smsId: String): IncomeEntity?

    @Query("SELECT smsId FROM incomes WHERE smsId IS NOT NULL")
    suspend fun getAllSmsIds(): List<String>

    @Query("SELECT * FROM incomes WHERE isRecurring = 1")
    fun getRecurringIncomes(): Flow<List<IncomeEntity>>

    @Query("SELECT SUM(amount) FROM incomes WHERE dateTime BETWEEN :startTime AND :endTime")
    suspend fun getTotalIncomeByDateRange(startTime: Long, endTime: Long): Int?

    @Query("SELECT * FROM incomes WHERE dateTime BETWEEN :startTime AND :endTime ORDER BY dateTime DESC")
    fun getIncomesByDateRange(startTime: Long, endTime: Long): Flow<List<IncomeEntity>>

    /** 기간별 수입 일회성 조회 (채팅 쿼리용) */
    @Query("SELECT * FROM incomes WHERE dateTime BETWEEN :startTime AND :endTime ORDER BY dateTime DESC")
    suspend fun getIncomesByDateRangeOnce(startTime: Long, endTime: Long): List<IncomeEntity>

    // 백업용 - 모든 수입 한번에 가져오기
    @Query("SELECT * FROM incomes ORDER BY dateTime DESC")
    suspend fun getAllIncomesOnce(): List<IncomeEntity>

    // 여러 건 삽입
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(incomes: List<IncomeEntity>)

    // 모든 데이터 삭제 (초기화용)
    @Query("DELETE FROM incomes")
    suspend fun deleteAll()
}
