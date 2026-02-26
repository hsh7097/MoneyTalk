package com.sanha.moneytalk.core.firebase

import com.sanha.moneytalk.core.util.MoneyTalkLogger

import com.sanha.moneytalk.BuildConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 강제 업데이트 판정기
 *
 * Firebase Realtime Database의 min_version_name과 앱의 VERSION_NAME을 비교하여
 * 강제 업데이트가 필요한지 판단합니다.
 *
 * ## 판정 기준
 * - 앱 VERSION_NAME < 서버 min_version_name → 강제 업데이트 필요
 * - 기본값 min_version_name="1.0.0" 이므로, 서버 미설정 시 업데이트 불필요
 * - 버전명 비교: "1.2.3" 형식을 major.minor.patch 숫자로 분리하여 순차 비교
 */
@Singleton
class ForceUpdateChecker @Inject constructor(
    private val premiumManager: PremiumManager
) {
    companion object {
        private const val TAG = "MoneyTalkLog"

        /**
         * 버전명 비교 (semantic versioning)
         * @return 음수: v1 < v2, 0: 동일, 양수: v1 > v2
         */
        fun compareVersionNames(v1: String, v2: String): Int {
            val parts1 = v1.split(".").map { it.toIntOrNull() ?: 0 }
            val parts2 = v2.split(".").map { it.toIntOrNull() ?: 0 }
            val maxLen = maxOf(parts1.size, parts2.size)
            for (i in 0 until maxLen) {
                val p1 = parts1.getOrElse(i) { 0 }
                val p2 = parts2.getOrElse(i) { 0 }
                if (p1 != p2) return p1 - p2
            }
            return 0
        }
    }

    /**
     * 강제 업데이트 필요 여부 Flow
     * - PremiumConfig 변경 시 자동으로 재평가
     */
    val forceUpdateRequired: Flow<ForceUpdateState> = premiumManager.premiumConfig
        .map { config ->
            val currentVersionName = BuildConfig.VERSION_NAME
            if (compareVersionNames(currentVersionName, config.minVersionName) < 0) {
                MoneyTalkLogger.w("강제 업데이트 필요: currentVersion=$currentVersionName < minVersion=${config.minVersionName}")
                ForceUpdateState.Required(
                    currentVersion = currentVersionName,
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
