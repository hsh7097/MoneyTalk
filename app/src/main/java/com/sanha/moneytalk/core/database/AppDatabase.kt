package com.sanha.moneytalk.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.sanha.moneytalk.core.database.converter.FloatListConverter
import com.sanha.moneytalk.core.database.dao.BudgetDao
import com.sanha.moneytalk.core.database.dao.CategoryMappingDao
import com.sanha.moneytalk.core.database.dao.ChatDao
import com.sanha.moneytalk.core.database.dao.ExpenseDao
import com.sanha.moneytalk.core.database.dao.IncomeDao
import com.sanha.moneytalk.core.database.dao.OwnedCardDao
import com.sanha.moneytalk.core.database.dao.SmsBlockedSenderDao
import com.sanha.moneytalk.core.database.dao.SmsChannelProbeLogDao
import com.sanha.moneytalk.core.database.dao.SmsExclusionKeywordDao
import com.sanha.moneytalk.core.database.dao.SmsPatternDao
import com.sanha.moneytalk.core.database.dao.SmsRegexRuleDao
import com.sanha.moneytalk.core.database.dao.CustomCategoryDao
import com.sanha.moneytalk.core.database.dao.StoreEmbeddingDao
import com.sanha.moneytalk.core.database.dao.StoreRuleDao
import com.sanha.moneytalk.core.database.dao.SyncCoverageDao
import com.sanha.moneytalk.core.database.entity.BudgetEntity
import com.sanha.moneytalk.core.database.entity.CustomCategoryEntity
import com.sanha.moneytalk.core.database.entity.CategoryMappingEntity
import com.sanha.moneytalk.core.database.entity.ChatEntity
import com.sanha.moneytalk.core.database.entity.ChatSessionEntity
import com.sanha.moneytalk.core.database.entity.ExpenseEntity
import com.sanha.moneytalk.core.database.entity.IncomeEntity
import com.sanha.moneytalk.core.database.entity.OwnedCardEntity
import com.sanha.moneytalk.core.database.entity.SmsBlockedSenderEntity
import com.sanha.moneytalk.core.database.entity.SmsChannelProbeLogEntity
import com.sanha.moneytalk.core.database.entity.SmsExclusionKeywordEntity
import com.sanha.moneytalk.core.database.entity.SmsPatternEntity
import com.sanha.moneytalk.core.database.entity.SmsRegexRuleEntity
import com.sanha.moneytalk.core.database.entity.StoreEmbeddingEntity
import com.sanha.moneytalk.core.database.entity.StoreRuleEntity
import com.sanha.moneytalk.core.database.entity.SyncCoverageEntity

/**
 * MoneyTalk 앱의 Room 데이터베이스 정의
 *
 * 앱에서 사용하는 모든 엔티티와 DAO를 관리하는 중앙 데이터베이스 클래스입니다.
 *
 * 포함 엔티티:
 * - ExpenseEntity: 지출 내역 (SMS 파싱 결과)
 * - IncomeEntity: 수입 내역 (입금 SMS 파싱 결과 또는 수동 입력)
 * - BudgetEntity: 카테고리별 월간 예산
 * - ChatEntity: 채팅 메시지 (사용자/AI 대화 내역)
 * - ChatSessionEntity: 채팅 세션 (Rolling Summary 포함)
 * - CategoryMappingEntity: 가게명→카테고리 매핑 캐시
 * - SmsPatternEntity: SMS 임베딩 패턴 (벡터 유사도 분류용)
 * - StoreEmbeddingEntity: 가게명 임베딩 벡터 (카테고리 벡터 캐싱용)
 * - OwnedCardEntity: 보유 카드 관리 (사용자 카드 화이트리스트)
 * - SmsExclusionKeywordEntity: SMS 제외 키워드 (블랙리스트)
 * - SmsBlockedSenderEntity: SMS 수신거부 발신번호
 *
 * TypeConverters:
 * - FloatListConverter: 임베딩 벡터(List<Float>)를 JSON String으로 변환
 *
 * DB 파일: moneytalk.db
 *
 * @see DatabaseModule Hilt DI 모듈에서 싱글톤으로 생성
 */
@Database(
    entities = [
        ExpenseEntity::class,
        IncomeEntity::class,
        BudgetEntity::class,
        ChatEntity::class,
        ChatSessionEntity::class,
        CategoryMappingEntity::class,
        SmsPatternEntity::class,
        StoreEmbeddingEntity::class,
        OwnedCardEntity::class,
        SmsExclusionKeywordEntity::class,
        SmsBlockedSenderEntity::class,
        SmsChannelProbeLogEntity::class,
        SmsRegexRuleEntity::class,
        CustomCategoryEntity::class,
        StoreRuleEntity::class,
        SyncCoverageEntity::class
    ],
    version = 4,
    exportSchema = false
)
@TypeConverters(FloatListConverter::class)
abstract class AppDatabase : RoomDatabase() {

    /** 지출 내역 DAO */
    abstract fun expenseDao(): ExpenseDao

    /** 수입 내역 DAO */
    abstract fun incomeDao(): IncomeDao

    /** 예산 관리 DAO */
    abstract fun budgetDao(): BudgetDao

    /** 채팅 메시지 및 세션 DAO */
    abstract fun chatDao(): ChatDao

    /** 카테고리 매핑 DAO */
    abstract fun categoryMappingDao(): CategoryMappingDao

    /** SMS 패턴 임베딩 DAO (벡터 유사도 분류용) */
    abstract fun smsPatternDao(): SmsPatternDao

    /** 가게명 임베딩 DAO (카테고리 벡터 캐싱용) */
    abstract fun storeEmbeddingDao(): StoreEmbeddingDao

    /** 보유 카드 DAO (카드 화이트리스트 관리) */
    abstract fun ownedCardDao(): OwnedCardDao

    /** SMS 제외 키워드 DAO (블랙리스트 관리) */
    abstract fun smsExclusionKeywordDao(): SmsExclusionKeywordDao

    /** SMS 수신거부 발신번호 DAO */
    abstract fun smsBlockedSenderDao(): SmsBlockedSenderDao

    /** 채널 진단 로그 DAO */
    abstract fun smsChannelProbeLogDao(): SmsChannelProbeLogDao

    /** sender 기반 regex 룰 DAO */
    abstract fun smsRegexRuleDao(): SmsRegexRuleDao

    /** 커스텀 카테고리 DAO */
    abstract fun customCategoryDao(): CustomCategoryDao

    /** 거래처 규칙 DAO */
    abstract fun storeRuleDao(): StoreRuleDao

    /** 실제 동기화 구간 DAO */
    abstract fun syncCoverageDao(): SyncCoverageDao

    companion object {
        /** 데이터베이스 파일명 */
        const val DATABASE_NAME = "moneytalk.db"

        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS sms_channel_probe_logs (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        normalizedSenderAddress TEXT NOT NULL,
                        channel TEXT NOT NULL,
                        stage TEXT NOT NULL,
                        originBody TEXT NOT NULL,
                        maskedBody TEXT NOT NULL,
                        normalizedBody TEXT NOT NULL,
                        messageTimestamp INTEGER NOT NULL,
                        note TEXT NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_sms_channel_probe_logs_normalizedSenderAddress ON sms_channel_probe_logs(normalizedSenderAddress)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_sms_channel_probe_logs_createdAt ON sms_channel_probe_logs(createdAt)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_sms_channel_probe_logs_channel_stage ON sms_channel_probe_logs(channel, stage)"
                )
            }
        }

        val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS sync_coverage (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        startMillis INTEGER NOT NULL,
                        endMillis INTEGER NOT NULL,
                        trigger TEXT NOT NULL,
                        expenseCount INTEGER NOT NULL,
                        incomeCount INTEGER NOT NULL,
                        reconciledExpenseCount INTEGER NOT NULL,
                        reconciledIncomeCount INTEGER NOT NULL,
                        syncedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_sync_coverage_startMillis ON sync_coverage(startMillis)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_sync_coverage_endMillis ON sync_coverage(endMillis)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_sync_coverage_syncedAt ON sync_coverage(syncedAt)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_sync_coverage_trigger ON sync_coverage(trigger)"
                )
            }
        }

        val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("DROP INDEX IF EXISTS index_incomes_smsId")
                db.execSQL(
                    """
                    DELETE FROM incomes
                    WHERE smsId IS NOT NULL
                      AND id NOT IN (
                        SELECT MIN(id)
                        FROM incomes
                        WHERE smsId IS NOT NULL
                        GROUP BY smsId
                      )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_incomes_smsId ON incomes(smsId)"
                )
            }
        }
    }
}
