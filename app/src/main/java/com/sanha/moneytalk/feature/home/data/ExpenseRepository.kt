package com.sanha.moneytalk.feature.home.data

import com.sanha.moneytalk.core.database.dao.CategorySum
import com.sanha.moneytalk.core.database.dao.DailySum
import com.sanha.moneytalk.core.database.dao.ExpenseDao
import com.sanha.moneytalk.core.database.dao.MonthlySum
import com.sanha.moneytalk.core.database.entity.ExpenseEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 지출 데이터 Repository
 *
 * Room DB의 ExpenseDao를 감싸는 레이어로, 지출 관련 모든 데이터 작업을 처리합니다.
 * ViewModel에서 직접 DAO를 사용하지 않고 이 Repository를 통해 데이터에 접근합니다.
 *
 * 주요 기능:
 * - 지출 데이터 CRUD (생성, 조회, 수정, 삭제)
 * - 기간별/카테고리별/카드별 필터링 조회
 * - 통계 데이터 집계 (일별, 월별, 카테고리별 합계)
 * - 중복 데이터 관리
 */
@Singleton
class ExpenseRepository @Inject constructor(
    private val expenseDao: ExpenseDao
) {
    /** 모든 지출 목록을 Flow로 조회 (실시간 관찰) */
    /** 모든 지출 목록을 Flow로 조회 (실시간 관찰) */
    fun getAllExpenses(): Flow<List<ExpenseEntity>> = expenseDao.getAllExpenses()

    /**
     * 특정 기간의 지출 목록 조회 (Flow)
     * @param startTime 시작 시간 (밀리초)
     * @param endTime 종료 시간 (밀리초)
     */
    fun getExpensesByDateRange(startTime: Long, endTime: Long): Flow<List<ExpenseEntity>> =
        expenseDao.getExpensesByDateRange(startTime, endTime)

    /**
     * 특정 기간의 지출 목록 조회 (일회성, suspend)
     * Pull-to-Refresh나 즉시 데이터가 필요한 경우 사용
     */
    suspend fun getExpensesByDateRangeOnce(startTime: Long, endTime: Long): List<ExpenseEntity> =
        expenseDao.getExpensesByDateRangeOnce(startTime, endTime)

    /** 특정 카테고리의 지출 목록 조회 (Flow) */
    fun getExpensesByCategory(category: String): Flow<List<ExpenseEntity>> =
        expenseDao.getExpensesByCategory(category)

    /** 특정 카드의 지출 목록 조회 (Flow) */
    fun getExpensesByCardName(cardName: String): Flow<List<ExpenseEntity>> =
        expenseDao.getExpensesByCardName(cardName)

    /** 카드 + 기간 복합 필터 조회 (Flow) */
    fun getExpensesByCardNameAndDateRange(cardName: String, startTime: Long, endTime: Long): Flow<List<ExpenseEntity>> =
        expenseDao.getExpensesByCardNameAndDateRange(cardName, startTime, endTime)

    /** 카테고리 + 기간 복합 필터 조회 (Flow) */
    fun getExpensesByCategoryAndDateRange(category: String, startTime: Long, endTime: Long): Flow<List<ExpenseEntity>> =
        expenseDao.getExpensesByCategoryAndDateRange(category, startTime, endTime)

    /**
     * 다중 조건 필터 조회 (Flow)
     * 카드명, 카테고리가 null이면 해당 조건 무시
     */
    fun getExpensesFiltered(cardName: String?, category: String?, startTime: Long, endTime: Long): Flow<List<ExpenseEntity>> =
        expenseDao.getExpensesFiltered(cardName, category, startTime, endTime)

    /** 지출 항목 삽입 (신규) */
    suspend fun insert(expense: ExpenseEntity): Long = expenseDao.insert(expense)

    /** 여러 지출 항목 일괄 삽입 */
    suspend fun insertAll(expenses: List<ExpenseEntity>) = expenseDao.insertAll(expenses)

    /** 지출 항목 수정 */
    suspend fun update(expense: ExpenseEntity) = expenseDao.update(expense)

    /** 지출 항목 삭제 (엔티티 기준) */
    suspend fun delete(expense: ExpenseEntity) = expenseDao.delete(expense)

    /** 지출 항목 삭제 (ID 기준) */
    suspend fun deleteById(id: Long) = expenseDao.deleteById(id)

    /** ID로 지출 항목 조회 */
    suspend fun getExpenseById(id: Long): ExpenseEntity? = expenseDao.getExpenseById(id)

    /** SMS ID로 지출 항목 조회 (중복 체크용) */
    suspend fun getExpenseBySmsId(smsId: String): ExpenseEntity? = expenseDao.getExpenseBySmsId(smsId)

    /** SMS ID 존재 여부 확인 (중복 방지) */
    suspend fun existsBySmsId(smsId: String): Boolean = expenseDao.existsBySmsId(smsId)

    /** 모든 SMS ID 조회 (배치 중복 체크용 인메모리 Set 구성) */
    suspend fun getAllSmsIds(): Set<String> = expenseDao.getAllSmsIds().toHashSet()

    // ========================
    // 통계 집계 메소드
    // ========================

    /** 특정 기간의 총 지출 금액 합계 */
    suspend fun getTotalExpenseByDateRange(startTime: Long, endTime: Long): Int =
        expenseDao.getTotalExpenseByDateRange(startTime, endTime) ?: 0

    /** 특정 기간의 카테고리별 지출 합계 (차트/통계용) */
    suspend fun getExpenseSumByCategory(startTime: Long, endTime: Long): List<CategorySum> =
        expenseDao.getExpenseSumByCategory(startTime, endTime)

    /** 최근 지출 N건 조회 */
    suspend fun getRecentExpenses(limit: Int): List<ExpenseEntity> =
        expenseDao.getRecentExpenses(limit)

    /** 모든 카드명 목록 조회 (필터 드롭다운용) */
    suspend fun getAllCardNames(): List<String> =
        expenseDao.getAllCardNames()

    /** 모든 카테고리 목록 조회 (필터 드롭다운용) */
    suspend fun getAllCategories(): List<String> =
        expenseDao.getAllCategories()

    /** 일별 지출 합계 조회 (차트용) */
    suspend fun getDailyTotals(startTime: Long, endTime: Long): List<DailySum> =
        expenseDao.getDailyTotals(startTime, endTime)

    /** 월별 지출 합계 조회 (차트용) */
    suspend fun getMonthlyTotals(): List<MonthlySum> =
        expenseDao.getMonthlyTotals()

    // ========================
    // 백업/복원 관련 메소드
    // ========================

    /** 모든 지출 데이터 한번에 조회 (백업용) */
    suspend fun getAllExpensesOnce(): List<ExpenseEntity> =
        expenseDao.getAllExpensesOnce()

    /** 모든 지출 데이터 삭제 (복원 전 초기화용) */
    suspend fun deleteAll() = expenseDao.deleteAll()

    // ========================
    // 가게명 기반 조회/수정 메소드
    // ========================

    /** 가게명으로 지출 조회 */
    suspend fun getExpensesByStoreName(storeName: String): List<ExpenseEntity> =
        expenseDao.getExpensesByStoreName(storeName)

    /** 가게명 + 기간으로 지출 조회 */
    suspend fun getExpensesByStoreNameAndDateRange(storeName: String, startTime: Long, endTime: Long): List<ExpenseEntity> =
        expenseDao.getExpensesByStoreNameAndDateRange(storeName, startTime, endTime)

    /** 가게명에 키워드가 포함된 지출 조회 (LIKE 검색) */
    suspend fun getExpensesByStoreNameContaining(keyword: String): List<ExpenseEntity> =
        expenseDao.getExpensesByStoreNameContaining(keyword)

    /** 가게명으로 총 지출 조회 */
    suspend fun getTotalExpenseByStoreName(storeName: String, startTime: Long, endTime: Long): Int =
        expenseDao.getTotalExpenseByStoreName(storeName, startTime, endTime) ?: 0

    // ========================
    // 카테고리 분류 관련 메소드
    // ========================

    /** 미분류("기타") 항목 조회 (Gemini 분류 대상) */
    suspend fun getUncategorizedExpenses(limit: Int): List<ExpenseEntity> =
        expenseDao.getUncategorizedExpenses(limit)

    /** 가게명으로 카테고리 일괄 변경 (학습 효과: 동일 가게 모두 변경) */
    suspend fun updateCategoryByStoreName(storeName: String, newCategory: String): Int =
        expenseDao.updateCategoryByStoreName(storeName, newCategory)

    /** 키워드 포함 가게명 카테고리 일괄 변경 */
    suspend fun updateCategoryByStoreNameContaining(keyword: String, newCategory: String): Int =
        expenseDao.updateCategoryByStoreNameContaining(keyword, newCategory)

    /** 특정 ID의 카테고리 변경 */
    suspend fun updateCategoryById(expenseId: Long, newCategory: String): Int =
        expenseDao.updateCategoryById(expenseId, newCategory)

    /** 특정 카테고리의 지출 조회 (일회성) */
    suspend fun getExpensesByCategoryOnce(category: String): List<ExpenseEntity> =
        expenseDao.getExpensesByCategoryOnce(category)

    // ========================
    // 중복 데이터 관리 메소드
    // ========================

    /** 중복 데이터 조회 (금액, 가게명, 날짜시간이 동일한 항목) */
    suspend fun getDuplicateExpenses(): List<ExpenseEntity> =
        expenseDao.getDuplicateExpenses()

    /**
     * 중복 데이터 삭제
     * 금액, 가게명, 날짜시간이 동일한 항목 중 하나만 남기고 나머지 삭제
     * @return 삭제된 항목 수
     */
    suspend fun deleteDuplicates(): Int =
        expenseDao.deleteDuplicates()

    // ========================
    // 검색 기능
    // ========================

    /**
     * 지출 검색
     * 가게명, 카테고리, 카드명에서 검색어 포함 여부 확인
     */
    suspend fun searchExpenses(query: String): List<ExpenseEntity> =
        expenseDao.searchExpenses(query)
}
