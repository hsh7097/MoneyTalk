package com.sanha.moneytalk.core.di

import android.content.Context
import androidx.room.Room
import com.sanha.moneytalk.core.database.AppDatabase
import com.sanha.moneytalk.core.database.dao.BudgetDao
import com.sanha.moneytalk.core.database.dao.CategoryMappingDao
import com.sanha.moneytalk.core.database.dao.ChatDao
import com.sanha.moneytalk.core.database.dao.ExpenseDao
import com.sanha.moneytalk.core.database.dao.IncomeDao
import com.sanha.moneytalk.core.database.dao.OwnedCardDao
import com.sanha.moneytalk.core.database.dao.SmsPatternDao
import com.sanha.moneytalk.core.database.dao.StoreEmbeddingDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 데이터베이스 Hilt DI 모듈
 *
 * Room 데이터베이스와 모든 DAO를 싱글톤으로 제공합니다.
 * 앱 전체에서 하나의 DB 인스턴스를 공유하여 일관성을 보장합니다.
 *
 * 마이그레이션 전략:
 * - fallbackToDestructiveMigration() 사용
 * - DB 스키마 변경 시 기존 데이터를 삭제하고 재생성
 * - 주의: 프로덕션 배포 전에 Migration 전략으로 전환 필요
 *
 * @see AppDatabase
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /**
     * AppDatabase 싱글톤 인스턴스 제공
     * @param context 애플리케이션 컨텍스트
     * @return Room 데이터베이스 인스턴스
     */
    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            AppDatabase.DATABASE_NAME
        )
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)
            .fallbackToDestructiveMigration()
            .build()
    }

    /** 지출 DAO 제공 - 카드 결제 내역 CRUD */
    @Provides
    @Singleton
    fun provideExpenseDao(database: AppDatabase): ExpenseDao {
        return database.expenseDao()
    }

    /** 수입 DAO 제공 - 입금/급여 내역 CRUD */
    @Provides
    @Singleton
    fun provideIncomeDao(database: AppDatabase): IncomeDao {
        return database.incomeDao()
    }

    /** 예산 DAO 제공 - 카테고리별 월간 예산 관리 */
    @Provides
    @Singleton
    fun provideBudgetDao(database: AppDatabase): BudgetDao {
        return database.budgetDao()
    }

    /** 채팅 DAO 제공 - AI 채팅 메시지 및 세션 관리 */
    @Provides
    @Singleton
    fun provideChatDao(database: AppDatabase): ChatDao {
        return database.chatDao()
    }

    /** 카테고리 매핑 DAO 제공 - 가게명→카테고리 캐시 관리 */
    @Provides
    @Singleton
    fun provideCategoryMappingDao(database: AppDatabase): CategoryMappingDao {
        return database.categoryMappingDao()
    }

    /** SMS 패턴 DAO 제공 - 벡터 유사도 기반 SMS 분류 패턴 관리 */
    @Provides
    @Singleton
    fun provideSmsPatternDao(database: AppDatabase): SmsPatternDao {
        return database.smsPatternDao()
    }

    /** 가게명 임베딩 DAO 제공 - 카테고리 벡터 캐싱 관리 */
    @Provides
    @Singleton
    fun provideStoreEmbeddingDao(database: AppDatabase): StoreEmbeddingDao {
        return database.storeEmbeddingDao()
    }

    /** 보유 카드 DAO 제공 - 카드 화이트리스트 관리 */
    @Provides
    @Singleton
    fun provideOwnedCardDao(database: AppDatabase): OwnedCardDao {
        return database.ownedCardDao()
    }
}
