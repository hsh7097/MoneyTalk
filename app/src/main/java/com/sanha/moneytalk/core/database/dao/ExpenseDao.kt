package com.sanha.moneytalk.core.database.dao

import androidx.room.*
import com.sanha.moneytalk.core.database.entity.ExpenseEntity
import kotlinx.coroutines.flow.Flow

/**
 * 지출 내역 DAO
 *
 * 카드 결제 SMS에서 파싱된 지출 데이터에 대한 CRUD 및 다양한 조회 쿼리를 제공합니다.
 *
 * 주요 기능:
 * - 기본 CRUD (삽입, 수정, 삭제)
 * - 기간별/카테고리별/카드사별 필터링 조회
 * - 통계 쿼리 (일별/월별 합계, 카테고리별 합계)
 * - 중복 데이터 관리 (중복 조회/삭제)
 * - 카테고리 일괄 변경 (가게명 기준)
 * - 검색 (가게명, 카테고리, 카드명)
 *
 * Flow 반환: 실시간 UI 업데이트용 (Compose에서 collectAsState)
 * suspend 반환: 일회성 조회/수정용
 *
 * @see ExpenseEntity
 */
@Dao
interface ExpenseDao {

    /** 단일 지출 삽입 (충돌 시 교체) */
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

    @Query("SELECT * FROM expenses WHERE category = :category ORDER BY dateTime DESC")
    suspend fun getExpensesByCategoryOnce(category: String): List<ExpenseEntity>

    @Query("SELECT SUM(amount) FROM expenses WHERE dateTime BETWEEN :startTime AND :endTime")
    suspend fun getTotalExpenseByDateRange(startTime: Long, endTime: Long): Int?

    @Query("SELECT category, SUM(amount) as total FROM expenses WHERE dateTime BETWEEN :startTime AND :endTime GROUP BY category")
    suspend fun getExpenseSumByCategory(startTime: Long, endTime: Long): List<CategorySum>

    @Query("SELECT * FROM expenses ORDER BY dateTime DESC LIMIT :limit")
    suspend fun getRecentExpenses(limit: Int): List<ExpenseEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM expenses WHERE smsId = :smsId)")
    suspend fun existsBySmsId(smsId: String): Boolean

    /** 모든 smsId 목록 조회 (배치 중복 체크용 인메모리 Set 구성) */
    @Query("SELECT smsId FROM expenses")
    suspend fun getAllSmsIds(): List<String>

    // 모든 필터 적용 (카드사 + 카테고리 + 기간)
    @Query("SELECT * FROM expenses WHERE (:cardName IS NULL OR cardName = :cardName) AND (:category IS NULL OR category = :category) AND dateTime BETWEEN :startTime AND :endTime ORDER BY dateTime DESC")
    fun getExpensesFiltered(cardName: String?, category: String?, startTime: Long, endTime: Long): Flow<List<ExpenseEntity>>

    // 대 카테고리 필터 (소 카테고리 포함, 예: "식비" 선택 시 "배달"도 포함)
    @Query("SELECT * FROM expenses WHERE (:cardName IS NULL OR cardName = :cardName) AND category IN (:categories) AND dateTime BETWEEN :startTime AND :endTime ORDER BY dateTime DESC")
    fun getExpensesFilteredByCategories(cardName: String?, categories: List<String>, startTime: Long, endTime: Long): Flow<List<ExpenseEntity>>

    /** 기간 + 카테고리 목록 필터링 조회 (일회성, 채팅 쿼리용) */
    @Query("SELECT * FROM expenses WHERE category IN (:categories) AND dateTime BETWEEN :startTime AND :endTime ORDER BY dateTime DESC")
    suspend fun getExpensesByCategoriesAndDateRangeOnce(categories: List<String>, startTime: Long, endTime: Long): List<ExpenseEntity>

    /** 기간 + 카테고리 목록으로 총 지출 합산 (채팅 쿼리용) */
    @Query("SELECT SUM(amount) FROM expenses WHERE category IN (:categories) AND dateTime BETWEEN :startTime AND :endTime")
    suspend fun getTotalExpenseByCategoriesAndDateRange(categories: List<String>, startTime: Long, endTime: Long): Int?

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

    // 가게명으로 지출 조회 (정확히 일치)
    @Query("SELECT * FROM expenses WHERE storeName = :storeName ORDER BY dateTime DESC")
    suspend fun getExpensesByStoreName(storeName: String): List<ExpenseEntity>

    // 가게명에 키워드 포함된 지출 조회
    @Query("SELECT * FROM expenses WHERE storeName LIKE '%' || :keyword || '%' ORDER BY dateTime DESC")
    suspend fun getExpensesByStoreNameContaining(keyword: String): List<ExpenseEntity>

    // 미분류 항목 조회
    @Query("SELECT * FROM expenses WHERE category = '미분류' ORDER BY dateTime DESC LIMIT :limit")
    suspend fun getUncategorizedExpenses(limit: Int): List<ExpenseEntity>

    // 가게명으로 카테고리 일괄 변경
    @Query("UPDATE expenses SET category = :newCategory WHERE storeName = :storeName")
    suspend fun updateCategoryByStoreName(storeName: String, newCategory: String): Int

    // 키워드 포함 가게명의 카테고리 일괄 변경
    @Query("UPDATE expenses SET category = :newCategory WHERE storeName LIKE '%' || :keyword || '%'")
    suspend fun updateCategoryByStoreNameContaining(keyword: String, newCategory: String): Int

    // 특정 ID의 카테고리 변경
    @Query("UPDATE expenses SET category = :newCategory WHERE id = :expenseId")
    suspend fun updateCategoryById(expenseId: Long, newCategory: String): Int

    // 중복 데이터 조회 (금액, 가게명, 날짜시간이 동일한 항목)
    @Query("""
        SELECT * FROM expenses
        WHERE id NOT IN (
            SELECT MIN(id) FROM expenses
            GROUP BY amount, storeName, dateTime
        )
    """)
    suspend fun getDuplicateExpenses(): List<ExpenseEntity>

    // 중복 데이터 삭제 (금액, 가게명, 날짜시간이 동일한 항목 중 가장 오래된 것만 남김)
    @Query("""
        DELETE FROM expenses
        WHERE id NOT IN (
            SELECT MIN(id) FROM expenses
            GROUP BY amount, storeName, dateTime
        )
    """)
    suspend fun deleteDuplicates(): Int

    // 검색 (가게명, 카테고리, 카드명, 메모에서 검색)
    @Query("""
        SELECT * FROM expenses
        WHERE storeName LIKE '%' || :query || '%'
           OR category LIKE '%' || :query || '%'
           OR cardName LIKE '%' || :query || '%'
           OR memo LIKE '%' || :query || '%'
        ORDER BY dateTime DESC
    """)
    suspend fun searchExpenses(query: String): List<ExpenseEntity>

    /** 키워드 기반 일괄 삭제 (가게명, 카테고리, 카드명, 메모에서 검색하여 삭제) */
    @Query("""
        DELETE FROM expenses
        WHERE storeName LIKE '%' || :keyword || '%'
           OR category LIKE '%' || :keyword || '%'
           OR cardName LIKE '%' || :keyword || '%'
           OR memo LIKE '%' || :keyword || '%'
    """)
    suspend fun deleteByKeyword(keyword: String): Int

    /** 메모 업데이트 */
    @Query("UPDATE expenses SET memo = :memo WHERE id = :expenseId")
    suspend fun updateMemo(expenseId: Long, memo: String?): Int

    /** 가게명 업데이트 */
    @Query("UPDATE expenses SET storeName = :newStoreName WHERE id = :expenseId")
    suspend fun updateStoreName(expenseId: Long, newStoreName: String): Int

    /** 금액 업데이트 */
    @Query("UPDATE expenses SET amount = :newAmount WHERE id = :expenseId")
    suspend fun updateAmount(expenseId: Long, newAmount: Int): Int

    // ========== OwnedCard 필터링 쿼리 ==========

    /** 내 카드 기준 기간별 지출 조회 (Flow) */
    @Query("SELECT * FROM expenses WHERE cardName IN (:ownedCardNames) AND dateTime BETWEEN :startTime AND :endTime ORDER BY dateTime DESC")
    fun getExpensesByOwnedCards(ownedCardNames: List<String>, startTime: Long, endTime: Long): Flow<List<ExpenseEntity>>

    /** 내 카드 기준 총 지출 합계 */
    @Query("SELECT SUM(amount) FROM expenses WHERE cardName IN (:ownedCardNames) AND dateTime BETWEEN :startTime AND :endTime")
    suspend fun getTotalExpenseByOwnedCards(ownedCardNames: List<String>, startTime: Long, endTime: Long): Int?

    /** 내 카드 기준 카테고리별 합계 */
    @Query("SELECT category, SUM(amount) as total FROM expenses WHERE cardName IN (:ownedCardNames) AND dateTime BETWEEN :startTime AND :endTime GROUP BY category")
    suspend fun getExpenseSumByCategoryOwned(ownedCardNames: List<String>, startTime: Long, endTime: Long): List<CategorySum>
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
