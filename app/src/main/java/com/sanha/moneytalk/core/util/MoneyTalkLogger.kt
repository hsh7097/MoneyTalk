package com.sanha.moneytalk.core.util

import android.util.Log
import com.sanha.moneytalk.BuildConfig

/**
 * 앱 전역 공용 로거.
 *
 * - 태그를 MoneyTalkLog로 통일
 * - d/i 레벨은 디버그 빌드에서만 출력
 * - w/e 레벨은 릴리즈에서도 출력
 */
object MoneyTalkLogger {

    private const val TAG = "MoneyTalkLog"

    fun d(message: String) {
        if (!BuildConfig.DEBUG) return
        Log.d(TAG, message)
    }

    fun i(message: String) {
        if (!BuildConfig.DEBUG) return
        Log.i(TAG, message)
    }

    fun w(message: String) {
        Log.w(TAG, message)
    }

    fun w(message: String, throwable: Throwable) {
        Log.w(TAG, message, throwable)
    }

    fun e(message: String) {
        Log.e(TAG, message)
    }

    fun e(message: String, throwable: Throwable) {
        Log.e(TAG, message, throwable)
    }
}
