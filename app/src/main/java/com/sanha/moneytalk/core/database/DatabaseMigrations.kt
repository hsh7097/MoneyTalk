package com.sanha.moneytalk.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room DB 마이그레이션 정의
 */
object DatabaseMigrations {

    /**
     * v1 -> v2
     * sms_patterns 테이블에 LLM 동적 정규식 컬럼 추가
     */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE sms_patterns ADD COLUMN amountRegex TEXT NOT NULL DEFAULT ''"
            )
            db.execSQL(
                "ALTER TABLE sms_patterns ADD COLUMN storeRegex TEXT NOT NULL DEFAULT ''"
            )
            db.execSQL(
                "ALTER TABLE sms_patterns ADD COLUMN cardRegex TEXT NOT NULL DEFAULT ''"
            )
        }
    }
}
