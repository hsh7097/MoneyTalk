package com.sanha.moneytalk

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.sanha.moneytalk.core.firebase.CrashlyticsHelper
import com.sanha.moneytalk.core.firebase.PremiumManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class MoneyTalkApplication : Application() {

    @Inject
    lateinit var premiumManager: PremiumManager

    override fun onCreate() {
        super.onCreate()

        // Firebase 초기화
        FirebaseApp.initializeApp(this)

        // Crashlytics 설정
        FirebaseCrashlytics.getInstance().apply {
            setCrashlyticsCollectionEnabled(!BuildConfig.DEBUG)
        }
        CrashlyticsHelper.setCustomKey("app_version", BuildConfig.VERSION_NAME)

        // Firebase Realtime Database 서버 설정 실시간 감시 시작
        premiumManager.startObservingConfig()
    }
}
