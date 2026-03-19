package com.sanha.moneytalk

import com.sanha.moneytalk.core.util.MoneyTalkLogger

import android.app.Application
import com.google.android.gms.ads.MobileAds
import com.google.firebase.FirebaseApp
import com.sanha.moneytalk.core.sms2.DeletedSmsTracker
import com.sanha.moneytalk.core.firebase.CrashlyticsHelper
import com.sanha.moneytalk.core.firebase.PremiumManager
import com.sanha.moneytalk.core.notification.SmsNotificationManager
import com.sanha.moneytalk.receiver.MmsContentObserver
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class MoneyTalkApplication : Application() {

    @Inject
    lateinit var premiumManager: PremiumManager

    @Inject
    lateinit var smsNotificationManager: SmsNotificationManager

    @Inject
    lateinit var mmsContentObserver: MmsContentObserver

    override fun onCreate() {
        super.onCreate()

        // 사용자 삭제 SMS 추적기 복원 (동기화 시 재삽입 방지)
        DeletedSmsTracker.init(this)

        // SMS 거래 알림 채널 등록
        smsNotificationManager.createNotificationChannel()

        // MMS 수신 실시간 감시 등록
        mmsContentObserver.register(contentResolver)

        // Firebase 초기화 (google-services.json 없으면 스킵)
        val firebaseAvailable = initializeFirebase()

        if (firebaseAvailable) {
            // Crashlytics 설정
            CrashlyticsHelper.setCrashlyticsCollectionEnabled(!BuildConfig.DEBUG)
            CrashlyticsHelper.setCustomKey("app_version", BuildConfig.VERSION_NAME)

            // Firebase Realtime Database 서버 설정 실시간 감시 시작
            premiumManager.startObservingConfig()
        }

        // Google AdMob 초기화 (디버그 빌드는 Google 공식 테스트 광고 ID 사용)
        MobileAds.initialize(this) {}
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
