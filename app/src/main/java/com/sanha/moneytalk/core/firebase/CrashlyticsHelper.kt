package com.sanha.moneytalk.core.firebase

import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * Firebase Crashlytics 유틸리티
 *
 * 앱 전역에서 크래시 리포팅을 쉽게 사용할 수 있도록 제공합니다.
 * - 비-치명적 에러 기록
 * - 커스텀 키/로그 기록
 * - 사용자 식별 정보 설정
 */
object CrashlyticsHelper {
    private const val TAG = "CrashlyticsHelper"

    private val crashlytics: FirebaseCrashlytics
        get() = FirebaseCrashlytics.getInstance()

    /**
     * 비-치명적(non-fatal) 예외 기록
     * try-catch에서 잡은 예외를 Crashlytics에 보고합니다.
     */
    fun recordException(throwable: Throwable) {
        try {
            crashlytics.recordException(throwable)
        } catch (e: Exception) {
            Log.w(TAG, "Crashlytics 예외 기록 실패: ${e.message}")
        }
    }

    /**
     * 커스텀 로그 메시지 기록
     * 크래시 발생 시 디버깅에 도움이 되는 컨텍스트 정보를 남깁니다.
     */
    fun log(message: String) {
        try {
            crashlytics.log(message)
        } catch (e: Exception) {
            Log.w(TAG, "Crashlytics 로그 기록 실패: ${e.message}")
        }
    }

    /**
     * 커스텀 키-값 쌍 설정
     * 크래시 발생 시 상태 정보를 확인할 수 있습니다.
     */
    fun setCustomKey(key: String, value: String) {
        try {
            crashlytics.setCustomKey(key, value)
        } catch (e: Exception) {
            Log.w(TAG, "Crashlytics 커스텀 키 설정 실패: ${e.message}")
        }
    }

    fun setCustomKey(key: String, value: Boolean) {
        try {
            crashlytics.setCustomKey(key, value)
        } catch (e: Exception) {
            Log.w(TAG, "Crashlytics 커스텀 키 설정 실패: ${e.message}")
        }
    }

    fun setCustomKey(key: String, value: Int) {
        try {
            crashlytics.setCustomKey(key, value)
        } catch (e: Exception) {
            Log.w(TAG, "Crashlytics 커스텀 키 설정 실패: ${e.message}")
        }
    }

    /**
     * 사용자 식별자 설정 (개인정보 주의)
     * 기기별 고유 ID 등을 설정하여 특정 사용자의 크래시를 추적합니다.
     */
    fun setUserId(userId: String) {
        try {
            crashlytics.setUserId(userId)
        } catch (e: Exception) {
            Log.w(TAG, "Crashlytics 사용자 ID 설정 실패: ${e.message}")
        }
    }

    /**
     * Crashlytics 데이터 수집 활성화/비활성화
     * 사용자 동의 기반으로 제어할 때 사용합니다.
     */
    fun setCrashlyticsCollectionEnabled(enabled: Boolean) {
        try {
            crashlytics.setCrashlyticsCollectionEnabled(enabled)
            Log.d(TAG, "Crashlytics 수집 ${if (enabled) "활성화" else "비활성화"}")
        } catch (e: Exception) {
            Log.w(TAG, "Crashlytics 수집 설정 실패: ${e.message}")
        }
    }
}
