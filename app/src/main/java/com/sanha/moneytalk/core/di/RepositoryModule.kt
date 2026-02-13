package com.sanha.moneytalk.core.di

import com.sanha.moneytalk.feature.chat.data.ChatRepository
import com.sanha.moneytalk.feature.chat.data.ChatRepositoryImpl
import com.sanha.moneytalk.feature.chat.data.GeminiRepository
import com.sanha.moneytalk.feature.chat.data.GeminiRepositoryImpl
import com.sanha.moneytalk.feature.home.data.CategoryClassifierService
import com.sanha.moneytalk.feature.home.data.CategoryClassifierServiceImpl
import com.sanha.moneytalk.feature.home.data.GeminiCategoryRepository
import com.sanha.moneytalk.feature.home.data.GeminiCategoryRepositoryImpl
import com.sanha.moneytalk.feature.home.data.StoreEmbeddingRepository
import com.sanha.moneytalk.feature.home.data.StoreEmbeddingRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindChatRepository(impl: ChatRepositoryImpl): ChatRepository

    @Binds
    @Singleton
    abstract fun bindGeminiRepository(impl: GeminiRepositoryImpl): GeminiRepository

    @Binds
    @Singleton
    abstract fun bindStoreEmbeddingRepository(impl: StoreEmbeddingRepositoryImpl): StoreEmbeddingRepository

    @Binds
    @Singleton
    abstract fun bindCategoryClassifierService(impl: CategoryClassifierServiceImpl): CategoryClassifierService

    @Binds
    @Singleton
    abstract fun bindGeminiCategoryRepository(impl: GeminiCategoryRepositoryImpl): GeminiCategoryRepository
}
