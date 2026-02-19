package com.sanha.moneytalk

import android.app.Application
import android.util.Log
import com.google.android.gms.ads.MobileAds
import com.google.firebase.FirebaseApp
import com.sanha.moneytalk.core.firebase.CrashlyticsHelper
import com.sanha.moneytalk.core.firebase.PremiumManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class MoneyTalkApplication : Application() {

    companion object {
        private const val TAG = "MoneyTalkApplication"
    }

    @Inject
    lateinit var premiumManager: PremiumManager

    override fun onCreate() {
        super.onCreate()

        // Firebase 초기화 (google-services.json 없으면 스킵)
        val firebaseAvailable = initializeFirebase()

        if (firebaseAvailable) {
            // Crashlytics 설정
            CrashlyticsHelper.setCrashlyticsCollectionEnabled(!BuildConfig.DEBUG)
            CrashlyticsHelper.setCustomKey("app_version", BuildConfig.VERSION_NAME)

            // Firebase Realtime Database 서버 설정 실시간 감시 시작
            premiumManager.startObservingConfig()
        }

        // Google AdMob 초기화
        MobileAds.initialize(this) {}
    }

    private fun initializeFirebase(): Boolean {
        return try {
            FirebaseApp.initializeApp(this)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Firebase 초기화 실패 (google-services.json 미설정): ${e.message}")
            false
        }
    }
}
