package com.sanha.moneytalk

import com.sanha.moneytalk.core.util.MoneyTalkLogger

import android.app.Application
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.firebase.FirebaseApp
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

        // 디버그 빌드: 에뮬레이터 및 테스트 기기 등록 (실제 광고 노출 방지)
        if (BuildConfig.DEBUG) {
            val testDeviceIds = listOf(
                com.google.android.gms.ads.AdRequest.DEVICE_ID_EMULATOR
                // 실제 기기 추가 시: Logcat에서 "Use RequestConfiguration...addTestDeviceIds" 메시지의 ID 복사
            )
            val configuration = RequestConfiguration.Builder()
                .setTestDeviceIds(testDeviceIds)
                .build()
            MobileAds.setRequestConfiguration(configuration)
        }
    }

    private fun initializeFirebase(): Boolean {
        return try {
            FirebaseApp.initializeApp(this)
            true
        } catch (e: Exception) {
            MoneyTalkLogger.w("Firebase 초기화 실패 (google-services.json 미설정): ${e.message}")
            false
        }
    }
}
