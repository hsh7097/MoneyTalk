package com.sanha.moneytalk.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
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

    /** SMS ID 존재 여부 확인 (전체 행 로드 대신 EXISTS로 효율적 확인) */
    @Query("SELECT EXISTS(SELECT 1 FROM incomes WHERE smsId = :smsId)")
    suspend fun existsBySmsId(smsId: String): Boolean

    @Query("SELECT smsId FROM incomes WHERE smsId IS NOT NULL")
    suspend fun getAllSmsIds(): List<String>

    /** 주어진 smsId 목록 중 이미 저장된 항목만 조회 (중복 체크 최적화) */
    @Query("SELECT smsId FROM incomes WHERE smsId IN (:smsIds)")
    suspend fun getExistingSmsIds(smsIds: List<String>): List<String>

    /** 전체 수입 건수 조회 (Auto Backup 감지용) */
    @Query("SELECT COUNT(*) FROM incomes")
    suspend fun getIncomeCount(): Int

    @Query("SELECT * FROM incomes WHERE isRecurring = 1")
    fun getRecurringIncomes(): Flow<List<IncomeEntity>>

    @Query("SELECT SUM(amount) FROM incomes WHERE dateTime BETWEEN :startTime AND :endTime")
    suspend fun getTotalIncomeByDateRange(startTime: Long, endTime: Long): Int?

    /** 날짜별 수입 합계 (달력 셀 표시용) - ExpenseDao.getDailyTotals()와 동일 패턴 */
    @Query("SELECT date(dateTime/1000, 'unixepoch', 'localtime') as date, SUM(amount) as total FROM incomes WHERE dateTime BETWEEN :startTime AND :endTime GROUP BY date ORDER BY date DESC")
    suspend fun getDailyTotals(startTime: Long, endTime: Long): List<DailySum>

    /** 월별 수입 합계 (6개월 바 차트용) */
    @Query("SELECT strftime('%Y-%m', dateTime/1000, 'unixepoch', 'localtime') as month, SUM(amount) as total FROM incomes GROUP BY month ORDER BY month DESC")
    suspend fun getMonthlyTotals(): List<MonthlySum>

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

    /** 메모 업데이트 */
    @Query("UPDATE incomes SET memo = :memo WHERE id = :incomeId")
    suspend fun updateMemo(incomeId: Long, memo: String?)

    /** 검색 (설명, 유형, 출처, 메모에서 검색) */
    @Query(
        """
        SELECT * FROM incomes
        WHERE description LIKE '%' || :query || '%'
           OR type LIKE '%' || :query || '%'
           OR source LIKE '%' || :query || '%'
           OR memo LIKE '%' || :query || '%'
        ORDER BY dateTime DESC
    """
    )
    suspend fun searchIncomes(query: String): List<IncomeEntity>

}
