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
import com.sanha.moneytalk.core.database.dao.SmsExclusionKeywordDao
import com.sanha.moneytalk.core.database.dao.SmsPatternDao
import com.sanha.moneytalk.core.database.dao.StoreEmbeddingDao
import com.sanha.moneytalk.core.database.entity.BudgetEntity
import com.sanha.moneytalk.core.database.entity.CategoryMappingEntity
import com.sanha.moneytalk.core.database.entity.ChatEntity
import com.sanha.moneytalk.core.database.entity.ChatSessionEntity
import com.sanha.moneytalk.core.database.entity.ExpenseEntity
import com.sanha.moneytalk.core.database.entity.IncomeEntity
import com.sanha.moneytalk.core.database.entity.OwnedCardEntity
import com.sanha.moneytalk.core.database.entity.SmsExclusionKeywordEntity
import com.sanha.moneytalk.core.database.entity.SmsPatternEntity
import com.sanha.moneytalk.core.database.entity.StoreEmbeddingEntity

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
        SmsExclusionKeywordEntity::class
    ],
    version = 1,
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

    companion object {
        /** 데이터베이스 파일명 */
        const val DATABASE_NAME = "moneytalk.db"
    }
}
