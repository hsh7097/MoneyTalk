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

    /**
     * v2 -> v3
     * sms_patterns 테이블에 메인 그룹 식별 컬럼 추가
     * 같은 발신번호의 메인 형식 패턴을 표시하여, 다음 동기화 시 DB에서 메인 regex 참조 가능
     */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE sms_patterns ADD COLUMN isMainGroup INTEGER NOT NULL DEFAULT 0"
            )
        }
    }

    /**
     * v3 -> v4
     * 임베딩 차원 변경 (3072 → 768): 기존 벡터 데이터와 호환 불가 → 삭제
     * - sms_patterns: SMS 임베딩 패턴 (재동기화 시 768차원으로 재생성)
     * - store_embeddings: 가게명 임베딩 벡터 (사용 시 768차원으로 재생성)
     */
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DELETE FROM sms_patterns")
            db.execSQL("DELETE FROM store_embeddings")
        }
    }

    /**
     * v4 -> v5
     * SMS 수신거부 발신번호 테이블 추가
     */
    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS sms_blocked_senders (
                    address TEXT NOT NULL PRIMARY KEY,
                    rawAddress TEXT NOT NULL DEFAULT '',
                    createdAt INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent()
            )
        }
    }

    /**
     * v5 -> v6
     * expenses/incomes 테이블에 발신번호(senderAddress) 컬럼 및 인덱스 추가
     */
    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE expenses ADD COLUMN senderAddress TEXT NOT NULL DEFAULT ''"
            )
            db.execSQL(
                "ALTER TABLE incomes ADD COLUMN senderAddress TEXT NOT NULL DEFAULT ''"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_expenses_senderAddress ON expenses(senderAddress)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_incomes_senderAddress ON incomes(senderAddress)"
            )
        }
    }
}
