package com.sanha.moneytalk.core.di

import com.sanha.moneytalk.core.util.MoneyTalkLogger

import android.content.Context
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {


    @Provides
    @Singleton
    fun provideFirebaseAnalytics(@ApplicationContext context: Context): FirebaseAnalytics? {
        return try {
            FirebaseAnalytics.getInstance(context)
        } catch (e: Exception) {
            MoneyTalkLogger.w("FirebaseAnalytics 초기화 실패: ${e.message}")
            null
        }
    }

    @Provides
    @Singleton
    fun provideFirebaseDatabase(): FirebaseDatabase? {
        return try {
            FirebaseDatabase.getInstance()
        } catch (e: Exception) {
            MoneyTalkLogger.w("FirebaseDatabase 초기화 실패: ${e.message}")
            null
        }
    }

    @Provides
    @Singleton
    fun provideFirebaseCrashlytics(): FirebaseCrashlytics? {
        return try {
            FirebaseCrashlytics.getInstance()
        } catch (e: Exception) {
            MoneyTalkLogger.w("FirebaseCrashlytics 초기화 실패: ${e.message}")
            null
        }
    }

    @Provides
    @Singleton
    fun provideFirebaseRemoteConfig(): FirebaseRemoteConfig? {
        return try {
            val config = FirebaseRemoteConfig.getInstance()
            val configSettings = remoteConfigSettings {
                minimumFetchIntervalInSeconds = 3600 // 1시간
            }
            config.setConfigSettingsAsync(configSettings)
            config
        } catch (e: Exception) {
            MoneyTalkLogger.w("FirebaseRemoteConfig 초기화 실패: ${e.message}")
            null
        }
    }
}
