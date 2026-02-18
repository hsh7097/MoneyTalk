package com.sanha.moneytalk.core.ad

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.sanha.moneytalk.core.datastore.SettingsDataStore
import com.sanha.moneytalk.core.firebase.PremiumManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 리워드 광고 상태
 */
sealed class AdState {
    /** 초기 상태 (광고 미로드) */
    data object Idle : AdState()
    /** 광고 로딩 중 */
    data object Loading : AdState()
    /** 광고 준비 완료 */
    data object Ready : AdState()
    /** 광고 표시 중 */
    data object Showing : AdState()
    /** 광고 로드 실패 */
    data class Error(val message: String) : AdState()
}

/**
 * 리워드 광고 관리자
 *
 * Google AdMob 리워드 광고의 로드, 표시, 보상 처리를 담당합니다.
 * Firebase RTDB의 reward_ad_enabled 설정에 따라 동작하며,
 * 광고 시청 시 reward_ad_chat_count만큼 채팅 횟수를 충전합니다.
 *
 * ## 테스트 ID (현재 사용 중)
 * - 앱 ID: ca-app-pub-3940256099942544~3347511713
 * - 리워드 광고 ID: ca-app-pub-3940256099942544/5224354917
 */
@Singleton
class RewardAdManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsDataStore: SettingsDataStore,
    private val premiumManager: PremiumManager
) {
    companion object {
        private const val TAG = "RewardAdManager"
        /** Google 공식 테스트 리워드 광고 ID */
        private const val TEST_REWARD_AD_ID = "ca-app-pub-3940256099942544/5224354917"
        private const val MAX_RETRY_COUNT = 3
    }

    private var rewardedAd: RewardedAd? = null
    private var retryCount = 0

    private val _adState = MutableStateFlow<AdState>(AdState.Idle)
    val adState: StateFlow<AdState> = _adState.asStateFlow()

    /** 리워드 채팅 잔여 횟수 Flow (UI에서 표시용) */
    val rewardChatRemainingFlow: Flow<Int> = settingsDataStore.rewardChatRemainingFlow

    /**
     * 리워드 광고 미리 로드
     * 광고 기능이 활성화되어 있을 때만 로드합니다.
     */
    fun preloadAd() {
        if (!premiumManager.premiumConfig.value.rewardAdEnabled) {
            Log.d(TAG, "리워드 광고 비활성 상태, 로드 스킵")
            return
        }

        if (_adState.value is AdState.Loading || _adState.value is AdState.Ready) {
            Log.d(TAG, "이미 로드 중이거나 준비 완료 상태")
            return
        }

        _adState.value = AdState.Loading
        Log.d(TAG, "리워드 광고 로드 시작")

        val adRequest = AdRequest.Builder().build()
        RewardedAd.load(context, TEST_REWARD_AD_ID, adRequest,
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAd = ad
                    retryCount = 0
                    _adState.value = AdState.Ready
                    Log.d(TAG, "리워드 광고 로드 완료")
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    rewardedAd = null
                    Log.e(TAG, "리워드 광고 로드 실패: ${error.message} (code: ${error.code})")

                    if (retryCount < MAX_RETRY_COUNT) {
                        retryCount++
                        Log.d(TAG, "재시도 $retryCount/$MAX_RETRY_COUNT")
                        _adState.value = AdState.Idle
                        preloadAd()
                    } else {
                        _adState.value = AdState.Error(error.message)
                        retryCount = 0
                    }
                }
            }
        )
    }

    /**
     * 리워드 광고 표시
     *
     * @param activity 광고를 표시할 Activity
     * @param onRewarded 보상 지급 콜백 (광고 시청 완료)
     * @param onFailed 실패 콜백
     */
    fun showAd(activity: Activity, onRewarded: () -> Unit, onFailed: () -> Unit) {
        val ad = rewardedAd
        if (ad == null) {
            Log.e(TAG, "광고가 로드되지 않음")
            _adState.value = AdState.Error("광고가 준비되지 않았습니다.")
            onFailed()
            // 다시 로드 시도
            preloadAd()
            return
        }

        _adState.value = AdState.Showing

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                Log.d(TAG, "광고 닫힘")
                rewardedAd = null
                _adState.value = AdState.Idle
                // 다음 광고 미리 로드
                preloadAd()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                Log.e(TAG, "광고 표시 실패: ${error.message}")
                rewardedAd = null
                _adState.value = AdState.Error(error.message)
                onFailed()
                // 다시 로드 시도
                preloadAd()
            }

            override fun onAdShowedFullScreenContent() {
                Log.d(TAG, "광고 표시 시작")
            }
        }

        ad.show(activity) { rewardItem ->
            Log.d(TAG, "보상 지급: type=${rewardItem.type}, amount=${rewardItem.amount}")
            onRewarded()
        }
    }

    /**
     * 리워드 채팅 횟수 1회 차감
     * @return true면 차감 성공, false면 잔여 횟수 부족
     */
    suspend fun consumeRewardChat(): Boolean {
        if (!premiumManager.premiumConfig.value.rewardAdEnabled) {
            return true // 광고 비활성 시 항상 성공
        }

        val remaining = settingsDataStore.getRewardChatRemaining()
        if (remaining <= 0) return false

        settingsDataStore.saveRewardChatRemaining(remaining - 1)
        Log.d(TAG, "리워드 채팅 차감: ${remaining}회 → ${remaining - 1}회")
        return true
    }

    /**
     * 리워드 채팅 횟수 충전 (광고 시청 보상)
     * PremiumConfig의 rewardAdChatCount만큼 추가
     */
    suspend fun addRewardChats() {
        val config = premiumManager.premiumConfig.value
        val current = settingsDataStore.getRewardChatRemaining()
        val newCount = current + config.rewardAdChatCount
        settingsDataStore.saveRewardChatRemaining(newCount)
        Log.d(TAG, "리워드 채팅 충전: ${current}회 → ${newCount}회 (+${config.rewardAdChatCount})")
    }

    /**
     * 광고 시청이 필요한지 확인
     * @return true면 광고 시청 필요 (광고 활성 && 잔여 횟수 0)
     */
    suspend fun isAdRequired(): Boolean {
        if (!premiumManager.premiumConfig.value.rewardAdEnabled) return false
        return settingsDataStore.getRewardChatRemaining() <= 0
    }

    /**
     * 전체 동기화 해제 (광고 시청 완료 후 호출)
     * DataStore에 전체 동기화 해제 상태를 저장합니다.
     */
    suspend fun unlockFullSync() {
        settingsDataStore.saveFullSyncUnlocked(true)
        Log.d(TAG, "전체 동기화 해제 완료 (광고 시청 보상)")
    }

    /**
     * 전체 동기화가 이미 해제되었는지 확인
     */
    suspend fun isFullSyncUnlocked(): Boolean {
        return settingsDataStore.isFullSyncUnlocked()
    }

    /**
     * 리워드 광고 기능이 활성화되어 있는지 확인
     */
    fun isRewardAdEnabled(): Boolean {
        return premiumManager.premiumConfig.value.rewardAdEnabled
    }

    /**
     * 리워드 1회 시청 시 충전되는 횟수
     */
    fun getRewardChatCount(): Int {
        return premiumManager.premiumConfig.value.rewardAdChatCount
    }
}
