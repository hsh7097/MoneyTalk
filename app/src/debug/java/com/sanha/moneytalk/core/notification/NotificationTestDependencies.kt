package com.sanha.moneytalk.core.notification

import com.sanha.moneytalk.core.database.AppDatabase
import com.sanha.moneytalk.core.datastore.SettingsDataStore
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** 기기 회귀 테스트에서 실제 앱 인스턴스를 조회한다. release에는 포함되지 않는다. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface NotificationTestDependencies {
    fun database(): AppDatabase
    fun settings(): SettingsDataStore
}
