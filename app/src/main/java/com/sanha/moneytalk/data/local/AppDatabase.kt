package com.sanha.moneytalk.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.sanha.moneytalk.data.local.dao.BudgetDao
import com.sanha.moneytalk.data.local.dao.ChatDao
import com.sanha.moneytalk.data.local.dao.ExpenseDao
import com.sanha.moneytalk.data.local.dao.IncomeDao
import com.sanha.moneytalk.data.local.entity.BudgetEntity
import com.sanha.moneytalk.data.local.entity.ChatEntity
import com.sanha.moneytalk.data.local.entity.ExpenseEntity
import com.sanha.moneytalk.data.local.entity.IncomeEntity

@Database(
    entities = [
        ExpenseEntity::class,
        IncomeEntity::class,
        BudgetEntity::class,
        ChatEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun expenseDao(): ExpenseDao
    abstract fun incomeDao(): IncomeDao
    abstract fun budgetDao(): BudgetDao
    abstract fun chatDao(): ChatDao

    companion object {
        const val DATABASE_NAME = "moneytalk_db"
    }
}
