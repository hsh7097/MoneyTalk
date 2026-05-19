package com.sanha.moneytalk.core.di

import com.sanha.moneytalk.core.notification.FinancialAppCandidateAnalyzer
import com.sanha.moneytalk.core.notification.FinancialAppLlmAnalyzer
import com.sanha.moneytalk.core.notification.RemoteFinancialAppRepository
import com.sanha.moneytalk.core.notification.RemoteFinancialAppSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class FinancialAppModule {

    @Binds
    @Singleton
    abstract fun bindRemoteFinancialAppSource(
        impl: RemoteFinancialAppRepository
    ): RemoteFinancialAppSource

    @Binds
    @Singleton
    abstract fun bindFinancialAppCandidateAnalyzer(
        impl: FinancialAppLlmAnalyzer
    ): FinancialAppCandidateAnalyzer
}
