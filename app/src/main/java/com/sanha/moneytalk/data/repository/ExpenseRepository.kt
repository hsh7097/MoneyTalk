package com.sanha.moneytalk.data.repository

import com.sanha.moneytalk.data.local.dao.CategorySum
import com.sanha.moneytalk.data.local.dao.ExpenseDao
import com.sanha.moneytalk.data.local.entity.ExpenseEntity
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

    fun getExpensesByCategory(category: String): Flow<List<ExpenseEntity>> =
        expenseDao.getExpensesByCategory(category)

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
}
