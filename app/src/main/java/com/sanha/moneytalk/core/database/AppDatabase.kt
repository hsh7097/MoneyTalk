package com.sanha.moneytalk.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.sanha.moneytalk.core.database.dao.BudgetDao
import com.sanha.moneytalk.core.database.dao.ChatDao
import com.sanha.moneytalk.core.database.dao.ExpenseDao
import com.sanha.moneytalk.core.database.dao.IncomeDao
import com.sanha.moneytalk.core.database.entity.BudgetEntity
import com.sanha.moneytalk.core.database.entity.ChatEntity
import com.sanha.moneytalk.core.database.entity.ExpenseEntity
import com.sanha.moneytalk.core.database.entity.IncomeEntity

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
