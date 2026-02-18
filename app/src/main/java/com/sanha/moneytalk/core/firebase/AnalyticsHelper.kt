package com.sanha.moneytalk.core.firebase

import android.os.Bundle
import android.util.Log
import com.google.firebase.analytics.FirebaseAnalytics
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Firebase Analytics 유틸리티
 *
 * 앱 전역에서 화면 PV 및 클릭 이벤트를 쉽게 로깅할 수 있도록 제공합니다.
 * - 화면 조회(screen_view) 이벤트
 * - 클릭(click) 이벤트
 * - 범용 커스텀 이벤트
 */
@Singleton
class AnalyticsHelper @Inject constructor(
    private val analytics: FirebaseAnalytics
) {
    private companion object {
        const val TAG = "AnalyticsHelper"
        const val EVENT_CLICK = "click"
        const val PARAM_BUTTON_NAME = "button_name"
    }

    /**
     * 화면 조회 이벤트 로깅
     * Navigation 화면 전환 시 자동 호출됩니다.
     */
    fun logScreenView(screenName: String) {
        try {
            val bundle = Bundle().apply {
                putString(FirebaseAnalytics.Param.SCREEN_NAME, screenName)
            }
            analytics.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW, bundle)
        } catch (e: Exception) {
            Log.w(TAG, "화면 PV 로깅 실패: ${e.message}")
        }
    }

    /**
     * 클릭 이벤트 로깅
     * 버튼/UI 인터랙션 시 호출합니다.
     *
     * @param screenName 이벤트 발생 화면
     * @param buttonName 클릭된 버튼/액션 이름
     */
    fun logClick(screenName: String, buttonName: String) {
        try {
            val bundle = Bundle().apply {
                putString(FirebaseAnalytics.Param.SCREEN_NAME, screenName)
                putString(PARAM_BUTTON_NAME, buttonName)
            }
            analytics.logEvent(EVENT_CLICK, bundle)
        } catch (e: Exception) {
            Log.w(TAG, "클릭 이벤트 로깅 실패: ${e.message}")
        }
    }

    /**
     * 범용 커스텀 이벤트 로깅
     */
    fun logEvent(eventName: String, params: Bundle? = null) {
        try {
            analytics.logEvent(eventName, params)
        } catch (e: Exception) {
            Log.w(TAG, "이벤트 로깅 실패: ${e.message}")
        }
    }
}
