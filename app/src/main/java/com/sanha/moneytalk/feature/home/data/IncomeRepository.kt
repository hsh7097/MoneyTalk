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

    suspend fun getIncomesBySmsIds(smsIds: List<String>): List<IncomeEntity> =
        if (smsIds.isEmpty()) emptyList() else incomeDao.getIncomesBySmsIds(smsIds)

    suspend fun existsBySmsId(smsId: String): Boolean = incomeDao.existsBySmsId(smsId)

    suspend fun getAllSmsIds(): List<String> = incomeDao.getAllSmsIds()

    /** 주어진 smsId 목록 중 이미 저장된 항목 조회 (중복 체크 최적화) */
    suspend fun getExistingSmsIds(smsIds: List<String>): Set<String> =
        incomeDao.getExistingSmsIds(smsIds).toHashSet()

    /** 전체 수입 건수 조회 */
    suspend fun getIncomeCount(): Int = incomeDao.getIncomeCount()

    suspend fun getTotalIncomeByDateRange(startTime: Long, endTime: Long): Int =
        incomeDao.getTotalIncomeByDateRange(startTime, endTime) ?: 0

    /** 날짜별 수입 합계 (달력 셀 표시용) */
    suspend fun getDailyTotals(startTime: Long, endTime: Long): Map<String, Int> =
        incomeDao.getDailyTotals(startTime, endTime).associate { it.date to it.total }

    /** 월별 수입 합계 (6개월 바 차트용) */
    suspend fun getMonthlyTotals(): Map<String, Int> =
        incomeDao.getMonthlyTotals().associate { it.month to it.total }

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

    /** 미분류 수입 조회 (분류 파이프라인용) */
    suspend fun getUnclassifiedIncomes(): List<IncomeEntity> =
        incomeDao.getUnclassifiedIncomes()

    /** 미분류 수입 수 조회 */
    suspend fun getUnclassifiedIncomeCount(): Int =
        incomeDao.getUnclassifiedIncomeCount()

    /** source 기준 카테고리 일괄 변경 (미분류 항목만) */
    suspend fun updateCategoryBySource(source: String, newCategory: String): Int =
        incomeDao.updateCategoryBySource(source, newCategory)

}
