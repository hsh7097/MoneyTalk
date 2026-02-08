package com.sanha.moneytalk.feature.home.data

import com.sanha.moneytalk.core.database.dao.IncomeDao
import com.sanha.moneytalk.core.database.entity.IncomeEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IncomeRepository @Inject constructor(
    private val incomeDao: IncomeDao
) {
    fun getAllIncomes(): Flow<List<IncomeEntity>> = incomeDao.getAllIncomes()

    fun getRecurringIncomes(): Flow<List<IncomeEntity>> = incomeDao.getRecurringIncomes()

    fun getIncomesByDateRange(startTime: Long, endTime: Long): Flow<List<IncomeEntity>> =
        incomeDao.getIncomesByDateRange(startTime, endTime)

    suspend fun insert(income: IncomeEntity): Long = incomeDao.insert(income)

    suspend fun update(income: IncomeEntity) = incomeDao.update(income)

    suspend fun delete(income: IncomeEntity) = incomeDao.delete(income)

    suspend fun deleteById(id: Long) = incomeDao.deleteById(id)

    suspend fun getIncomeById(id: Long): IncomeEntity? = incomeDao.getIncomeById(id)

    suspend fun getIncomeBySmsId(smsId: String): IncomeEntity? = incomeDao.getIncomeBySmsId(smsId)

    suspend fun existsBySmsId(smsId: String): Boolean = incomeDao.getIncomeBySmsId(smsId) != null

    suspend fun getAllSmsIds(): List<String> = incomeDao.getAllSmsIds()

    suspend fun getTotalIncomeByDateRange(startTime: Long, endTime: Long): Int =
        incomeDao.getTotalIncomeByDateRange(startTime, endTime) ?: 0

    /** 기간별 수입 일회성 조회 (채팅 쿼리용) */
    suspend fun getIncomesByDateRangeOnce(startTime: Long, endTime: Long): List<IncomeEntity> =
        incomeDao.getIncomesByDateRangeOnce(startTime, endTime)

    // 백업용 - 모든 수입 한번에 가져오기
    suspend fun getAllIncomesOnce(): List<IncomeEntity> =
        incomeDao.getAllIncomesOnce()

    // 여러 건 삽입
    suspend fun insertAll(incomes: List<IncomeEntity>) = incomeDao.insertAll(incomes)

    // 모든 데이터 삭제
    suspend fun deleteAll() = incomeDao.deleteAll()

    /** 메모 업데이트 */
    suspend fun updateMemo(incomeId: Long, memo: String?) =
        incomeDao.updateMemo(incomeId, memo)

    /** 수입 검색 (설명, 유형, 출처, 메모) */
    suspend fun searchIncomes(query: String): List<IncomeEntity> =
        incomeDao.searchIncomes(query)
}
