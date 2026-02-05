package com.sanha.moneytalk.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.sanha.moneytalk.core.database.dao.BudgetDao
import com.sanha.moneytalk.core.database.dao.CategoryMappingDao
import com.sanha.moneytalk.core.database.dao.ChatDao
import com.sanha.moneytalk.core.database.dao.ExpenseDao
import com.sanha.moneytalk.core.database.dao.IncomeDao
import com.sanha.moneytalk.core.database.entity.BudgetEntity
import com.sanha.moneytalk.core.database.entity.CategoryMappingEntity
import com.sanha.moneytalk.core.database.entity.ChatEntity
import com.sanha.moneytalk.core.database.entity.ChatSessionEntity
import com.sanha.moneytalk.core.database.entity.ExpenseEntity
import com.sanha.moneytalk.core.database.entity.IncomeEntity

@Database(
    entities = [
        ExpenseEntity::class,
        IncomeEntity::class,
        BudgetEntity::class,
        ChatEntity::class,
        ChatSessionEntity::class,
        CategoryMappingEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun expenseDao(): ExpenseDao
    abstract fun incomeDao(): IncomeDao
    abstract fun budgetDao(): BudgetDao
    abstract fun chatDao(): ChatDao
    abstract fun categoryMappingDao(): CategoryMappingDao

    companion object {
        const val DATABASE_NAME = "moneytalk_db"

        // 마이그레이션 1 → 2: 채팅 세션 테이블 추가 및 기존 채팅에 세션 연결
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. chat_sessions 테이블 생성
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS chat_sessions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        title TEXT NOT NULL DEFAULT '새 대화',
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                """)

                // 2. 기존 채팅이 있으면 기본 세션 생성
                db.execSQL("""
                    INSERT OR IGNORE INTO chat_sessions (id, title, createdAt, updatedAt)
                    SELECT 1, '이전 대화',
                           COALESCE((SELECT MIN(timestamp) FROM chat_history), ${System.currentTimeMillis()}),
                           COALESCE((SELECT MAX(timestamp) FROM chat_history), ${System.currentTimeMillis()})
                    WHERE EXISTS (SELECT 1 FROM chat_history LIMIT 1)
                """)

                // 3. 새 chat_history 테이블 생성 (sessionId 포함)
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS chat_history_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        sessionId INTEGER NOT NULL DEFAULT 1,
                        message TEXT NOT NULL,
                        isUser INTEGER NOT NULL,
                        timestamp INTEGER NOT NULL,
                        FOREIGN KEY(sessionId) REFERENCES chat_sessions(id) ON DELETE CASCADE
                    )
                """)

                // 4. 기존 데이터 마이그레이션
                db.execSQL("""
                    INSERT INTO chat_history_new (id, sessionId, message, isUser, timestamp)
                    SELECT id, 1, message, isUser, timestamp FROM chat_history
                """)

                // 5. 기존 테이블 삭제 및 새 테이블 이름 변경
                db.execSQL("DROP TABLE chat_history")
                db.execSQL("ALTER TABLE chat_history_new RENAME TO chat_history")

                // 6. 인덱스 생성
                db.execSQL("CREATE INDEX IF NOT EXISTS index_chat_history_sessionId ON chat_history(sessionId)")
            }
        }

        // 마이그레이션 2 → 3: 카테고리 매핑 테이블 추가
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS category_mappings (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        storeName TEXT NOT NULL,
                        category TEXT NOT NULL,
                        source TEXT NOT NULL DEFAULT 'local',
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                """)
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_category_mappings_storeName ON category_mappings(storeName)")
            }
        }
    }
}
