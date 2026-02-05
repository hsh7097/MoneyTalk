package com.sanha.moneytalk.feature.home.data

import com.sanha.moneytalk.core.database.dao.CategorySum
import com.sanha.moneytalk.core.database.dao.DailySum
import com.sanha.moneytalk.core.database.dao.ExpenseDao
import com.sanha.moneytalk.core.database.dao.MonthlySum
import com.sanha.moneytalk.core.database.entity.ExpenseEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExpenseRepository @Inject constructor(
    private val expenseDao: ExpenseDao
) {
    fun getAllExpenses(): Flow<List<ExpenseEntity>> = expenseDao.getAllExpenses()

    fun getExpensesByDateRange(startTime: Long, endTime: Long): Flow<List<ExpenseEntity>> =
        expenseDao.getExpensesByDateRange(startTime, endTime)

    suspend fun getExpensesByDateRangeOnce(startTime: Long, endTime: Long): List<ExpenseEntity> =
        expenseDao.getExpensesByDateRangeOnce(startTime, endTime)

    fun getExpensesByCategory(category: String): Flow<List<ExpenseEntity>> =
        expenseDao.getExpensesByCategory(category)

    fun getExpensesByCardName(cardName: String): Flow<List<ExpenseEntity>> =
        expenseDao.getExpensesByCardName(cardName)

    fun getExpensesByCardNameAndDateRange(cardName: String, startTime: Long, endTime: Long): Flow<List<ExpenseEntity>> =
        expenseDao.getExpensesByCardNameAndDateRange(cardName, startTime, endTime)

    fun getExpensesByCategoryAndDateRange(category: String, startTime: Long, endTime: Long): Flow<List<ExpenseEntity>> =
        expenseDao.getExpensesByCategoryAndDateRange(category, startTime, endTime)

    fun getExpensesFiltered(cardName: String?, category: String?, startTime: Long, endTime: Long): Flow<List<ExpenseEntity>> =
        expenseDao.getExpensesFiltered(cardName, category, startTime, endTime)

    suspend fun insert(expense: ExpenseEntity): Long = expenseDao.insert(expense)

    suspend fun insertAll(expenses: List<ExpenseEntity>) = expenseDao.insertAll(expenses)

    suspend fun update(expense: ExpenseEntity) = expenseDao.update(expense)

    suspend fun delete(expense: ExpenseEntity) = expenseDao.delete(expense)

    suspend fun deleteById(id: Long) = expenseDao.deleteById(id)

    suspend fun getExpenseById(id: Long): ExpenseEntity? = expenseDao.getExpenseById(id)

    suspend fun getExpenseBySmsId(smsId: String): ExpenseEntity? = expenseDao.getExpenseBySmsId(smsId)

    suspend fun existsBySmsId(smsId: String): Boolean = expenseDao.existsBySmsId(smsId)

    suspend fun getTotalExpenseByDateRange(startTime: Long, endTime: Long): Int =
        expenseDao.getTotalExpenseByDateRange(startTime, endTime) ?: 0

    suspend fun getExpenseSumByCategory(startTime: Long, endTime: Long): List<CategorySum> =
        expenseDao.getExpenseSumByCategory(startTime, endTime)

    suspend fun getRecentExpenses(limit: Int): List<ExpenseEntity> =
        expenseDao.getRecentExpenses(limit)

    suspend fun getAllCardNames(): List<String> =
        expenseDao.getAllCardNames()

    suspend fun getAllCategories(): List<String> =
        expenseDao.getAllCategories()

    suspend fun getDailyTotals(startTime: Long, endTime: Long): List<DailySum> =
        expenseDao.getDailyTotals(startTime, endTime)

    suspend fun getMonthlyTotals(): List<MonthlySum> =
        expenseDao.getMonthlyTotals()

    // 백업용 - 모든 지출 한번에 가져오기
    suspend fun getAllExpensesOnce(): List<ExpenseEntity> =
        expenseDao.getAllExpensesOnce()

    // 모든 데이터 삭제
    suspend fun deleteAll() = expenseDao.deleteAll()

    // 가게명으로 지출 조회
    suspend fun getExpensesByStoreName(storeName: String): List<ExpenseEntity> =
        expenseDao.getExpensesByStoreName(storeName)

    // 가게명 + 기간으로 지출 조회
    suspend fun getExpensesByStoreNameAndDateRange(storeName: String, startTime: Long, endTime: Long): List<ExpenseEntity> =
        expenseDao.getExpensesByStoreNameAndDateRange(storeName, startTime, endTime)

    // 가게명에 키워드 포함된 지출 조회
    suspend fun getExpensesByStoreNameContaining(keyword: String): List<ExpenseEntity> =
        expenseDao.getExpensesByStoreNameContaining(keyword)

    // 가게명으로 총 지출 조회
    suspend fun getTotalExpenseByStoreName(storeName: String, startTime: Long, endTime: Long): Int =
        expenseDao.getTotalExpenseByStoreName(storeName, startTime, endTime) ?: 0

    // 미분류 항목 조회
    suspend fun getUncategorizedExpenses(limit: Int): List<ExpenseEntity> =
        expenseDao.getUncategorizedExpenses(limit)

    // 가게명으로 카테고리 일괄 변경
    suspend fun updateCategoryByStoreName(storeName: String, newCategory: String): Int =
        expenseDao.updateCategoryByStoreName(storeName, newCategory)

    // 키워드 포함 가게명 카테고리 일괄 변경
    suspend fun updateCategoryByStoreNameContaining(keyword: String, newCategory: String): Int =
        expenseDao.updateCategoryByStoreNameContaining(keyword, newCategory)

    // 특정 ID의 카테고리 변경
    suspend fun updateCategoryById(expenseId: Long, newCategory: String): Int =
        expenseDao.updateCategoryById(expenseId, newCategory)
}
