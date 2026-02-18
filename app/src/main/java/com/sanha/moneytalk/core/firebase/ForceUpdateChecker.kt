package com.sanha.moneytalk.core.firebase

import android.util.Log
import com.sanha.moneytalk.BuildConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 강제 업데이트 판정기
 *
 * Firebase Realtime Database의 min_version_code와 앱의 VERSION_CODE를 비교하여
 * 강제 업데이트가 필요한지 판단합니다.
 *
 * ## 판정 기준
 * - 앱 VERSION_CODE < 서버 min_version_code → 강제 업데이트 필요
 * - 기본값 min_version_code=1 이므로, 서버 미설정 시 업데이트 불필요
 */
@Singleton
class ForceUpdateChecker @Inject constructor(
    private val premiumManager: PremiumManager
) {
    companion object {
        private const val TAG = "ForceUpdateChecker"
    }

    /**
     * 강제 업데이트 필요 여부 Flow
     * - PremiumConfig 변경 시 자동으로 재평가
     */
    val forceUpdateRequired: Flow<ForceUpdateState> = premiumManager.premiumConfig
        .map { config ->
            val currentVersionCode = BuildConfig.VERSION_CODE
            if (currentVersionCode < config.minVersionCode) {
                Log.w(TAG, "강제 업데이트 필요: currentVersionCode=$currentVersionCode < minVersionCode=${config.minVersionCode}")
                ForceUpdateState.Required(
                    currentVersion = BuildConfig.VERSION_NAME,
                    requiredVersion = config.minVersionName,
                    message = config.forceUpdateMessage
                )
            } else {
                ForceUpdateState.NotRequired
            }
        }
        .distinctUntilChanged()
}

/**
 * 강제 업데이트 상태
 */
sealed class ForceUpdateState {
    /** 업데이트 불필요 */
    data object NotRequired : ForceUpdateState()

    /** 업데이트 필요 — 앱 사용 차단 */
    data class Required(
        val currentVersion: String,
        val requiredVersion: String,
        val message: String
    ) : ForceUpdateState()
}
