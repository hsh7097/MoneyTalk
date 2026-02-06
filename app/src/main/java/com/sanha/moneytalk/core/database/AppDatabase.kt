package com.sanha.moneytalk.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.sanha.moneytalk.core.database.converter.VectorConverters
import com.sanha.moneytalk.core.database.dao.BudgetDao
import com.sanha.moneytalk.core.database.dao.ChatDao
import com.sanha.moneytalk.core.database.dao.ExpenseDao
import com.sanha.moneytalk.core.database.dao.IncomeDao
import com.sanha.moneytalk.core.database.dao.MerchantVectorDao
import com.sanha.moneytalk.core.database.dao.SmsPatternDao
import com.sanha.moneytalk.core.database.entity.BudgetEntity
import com.sanha.moneytalk.core.database.entity.ChatEntity
import com.sanha.moneytalk.core.database.entity.ChatSessionEntity
import com.sanha.moneytalk.core.database.entity.ExpenseEntity
import com.sanha.moneytalk.core.database.entity.IncomeEntity
import com.sanha.moneytalk.core.database.entity.MerchantVectorEntity
import com.sanha.moneytalk.core.database.entity.SmsPatternEntity

@Database(
    entities = [
        ExpenseEntity::class,
        IncomeEntity::class,
        BudgetEntity::class,
        ChatEntity::class,
        ChatSessionEntity::class,
        SmsPatternEntity::class,
        MerchantVectorEntity::class
    ],
    version = 3,
    exportSchema = false
)
@TypeConverters(VectorConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun expenseDao(): ExpenseDao
    abstract fun incomeDao(): IncomeDao
    abstract fun budgetDao(): BudgetDao
    abstract fun chatDao(): ChatDao
    abstract fun smsPatternDao(): SmsPatternDao
    abstract fun merchantVectorDao(): MerchantVectorDao

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

        // 마이그레이션 2 → 3: 벡터 기반 SMS 패턴 및 가맹점 테이블 추가
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // SMS 패턴 테이블 생성
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS sms_patterns (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        vector BLOB NOT NULL,
                        smsTemplate TEXT NOT NULL,
                        cardName TEXT NOT NULL,
                        amountPattern TEXT NOT NULL,
                        storeNamePattern TEXT NOT NULL,
                        dateTimePattern TEXT NOT NULL,
                        parsingSource TEXT NOT NULL,
                        successCount INTEGER NOT NULL DEFAULT 1,
                        lastUsedAt INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                """)

                // 가맹점 벡터 테이블 생성
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS merchant_vectors (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        merchantName TEXT NOT NULL,
                        vector BLOB NOT NULL,
                        category TEXT NOT NULL,
                        aliases TEXT NOT NULL DEFAULT '',
                        matchCount INTEGER NOT NULL DEFAULT 1,
                        lastMatchedAt INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                """)
            }
        }
    }
}
