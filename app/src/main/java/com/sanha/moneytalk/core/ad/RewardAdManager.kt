package com.sanha.moneytalk.core.ad

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.sanha.moneytalk.core.database.AiCreditRepository
import com.sanha.moneytalk.core.datastore.SettingsDataStore
import com.sanha.moneytalk.core.firebase.PremiumConfig
import com.sanha.moneytalk.core.firebase.PremiumManager
import com.sanha.moneytalk.core.firebase.ServiceTier
import com.sanha.moneytalk.core.util.BuildVariantPolicy
import com.sanha.moneytalk.core.util.MoneyTalkLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
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

data class MonthSyncCreditConsumption(
    val canSync: Boolean,
    val charged: Boolean
)

/**
 * 리워드 광고 관리자
 *
 * Google AdMob 리워드 광고의 로드, 표시, 보상 처리를 담당합니다.
 * Firebase RTDB의 reward_ad_enabled 설정은 공통 광고 노출을 제어하고,
 * credit_ad_enable 설정은 AI 크레딧 표시/차감/충전 흐름을 제어합니다.
 * release가 아닌 빌드에서는 설정값과 무관하게 광고와 크레딧 쓰기를 수행하지 않습니다.
 * 광고 시청 완료 시 reward_ad_chat_count만큼 AI 크레딧을 충전합니다.
 *
 * ## 광고 ID
 * - 앱 ID: ca-app-pub-4707673176609005~5012288836
 * - 리워드 광고 ID: ca-app-pub-4707673176609005/2566523665
 */
@Singleton
class RewardAdManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsDataStore: SettingsDataStore,
    private val premiumManager: PremiumManager,
    private val aiCreditRepository: AiCreditRepository
) {
    companion object {
        private const val REWARD_AD_ID = "ca-app-pub-4707673176609005/2566523665"
        private const val TEST_REWARD_AD_ID = "ca-app-pub-3940256099942544/5224354917"
        private const val MAX_RETRY_COUNT = 3
        const val MONTH_SYNC_CREDIT_COST = 1
        const val REWARD_AD_CREDIT_AMOUNT = 2
    }

    private var rewardedAd: RewardedAd? = null
    private var retryCount = 0
    @Volatile
    private var currentServiceTier = ServiceTier.FREE
    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _adState = MutableStateFlow<AdState>(AdState.Idle)
    val adState: StateFlow<AdState> = _adState.asStateFlow()

    /** AI 크레딧 잔액 Flow (UI에서 표시용) */
    val rewardChatRemainingFlow: Flow<Int> = aiCreditRepository.balanceFlow

    init {
        managerScope.launch {
            settingsDataStore.serviceTierFlow.collect { tier ->
                currentServiceTier = tier
            }
        }
    }

    suspend fun prepareCreditBalance() {
        if (!isCreditFeatureEnabledNow()) return
        aiCreditRepository.ensureLegacyRewardChatMigrated()
        aiCreditRepository.grantInitialAppLaunchRewardIfNeeded()
    }

    /**
     * 리워드 광고 미리 로드
     * 광고 기능이 활성화되어 있을 때만 로드합니다.
     */
    fun preloadAd() {
        if (!isAdFeatureEnabled()) {
            rewardedAd = null
            retryCount = 0
            _adState.value = AdState.Idle
            return
        }

        if (_adState.value is AdState.Loading || _adState.value is AdState.Ready) {
            return
        }

        _adState.value = AdState.Loading

        val adRequest = AdRequest.Builder().build()
        RewardedAd.load(context, rewardAdId(), adRequest,
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAd = ad
                    retryCount = 0
                    _adState.value = AdState.Ready
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    rewardedAd = null
                    MoneyTalkLogger.e("리워드 광고 로드 실패: ${error.message} (code: ${error.code})")

                    if (retryCount < MAX_RETRY_COUNT) {
                        retryCount++
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
        if (!isAdFeatureEnabled()) {
            rewardedAd = null
            retryCount = 0
            _adState.value = AdState.Idle
            onFailed()
            return
        }

        val ad = rewardedAd
        if (ad == null) {
            MoneyTalkLogger.e("광고가 로드되지 않음")
            _adState.value = AdState.Error("광고가 준비되지 않았습니다.")
            onFailed()
            // 다시 로드 시도
            preloadAd()
            return
        }

        _adState.value = AdState.Showing
        var rewardEarned = false
        var failureNotified = false
        fun notifyFailedOnce() {
            if (!rewardEarned && !failureNotified) {
                failureNotified = true
                onFailed()
            }
        }

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                rewardedAd = null
                _adState.value = AdState.Idle
                notifyFailedOnce()
                // 다음 광고 미리 로드
                preloadAd()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                MoneyTalkLogger.e("광고 표시 실패: ${error.message}")
                rewardedAd = null
                _adState.value = AdState.Error(error.message)
                notifyFailedOnce()
                // 다시 로드 시도
                preloadAd()
            }

            override fun onAdShowedFullScreenContent() {
            }
        }

        ad.show(activity) {
            rewardEarned = true
            onRewarded()
        }
    }

    /** AI 크레딧 충전용 리워드 광고 미리 로드 */
    fun preloadCreditAd() {
        if (!isCreditRewardAdEnabled()) return
        preloadAd()
    }

    /** AI 크레딧 충전용 리워드 광고 표시 */
    fun showCreditAd(activity: Activity, onRewarded: () -> Unit, onFailed: () -> Unit) {
        if (!isCreditRewardAdEnabled()) {
            onFailed()
            return
        }
        showAd(
            activity = activity,
            onRewarded = onRewarded,
            onFailed = onFailed
        )
    }

    /** 채팅 1회분 AI 크레딧 차감. true면 차감 성공, false면 잔여 크레딧 부족 */
    suspend fun consumeRewardChat(cost: Int = AiCreditRepository.LIGHT_CHAT_COST): Boolean {
        if (!isCreditRewardAdEnabledNow()) {
            return true
        }

        return aiCreditRepository.spendForChat(cost = cost)
    }

    /** 이전 월 문자 기록 가져오기 1회분 크레딧 차감. */
    suspend fun consumeMonthSyncCredit(
        cost: Int = MONTH_SYNC_CREDIT_COST
    ): MonthSyncCreditConsumption {
        if (!isCreditRewardAdEnabledNow()) {
            return MonthSyncCreditConsumption(canSync = true, charged = false)
        }

        val consumed = aiCreditRepository.spendForMonthSync(cost = cost)
        return MonthSyncCreditConsumption(canSync = consumed, charged = consumed)
    }

    suspend fun refundMonthSyncCredit(amount: Int = MONTH_SYNC_CREDIT_COST) {
        if (amount <= 0) return
        aiCreditRepository.refundCredits(
            amount = amount,
            reason = AiCreditRepository.REASON_MONTH_SYNC_REFUND
        )
    }

    /**
     * AI 크레딧 충전 (광고 시청 보상)
     * Rewarded ad 1회당 2크레딧을 충전한다.
     */
    suspend fun addRewardChats() {
        if (!isCreditRewardAdEnabledNow()) return
        aiCreditRepository.grantRewardAdCredits(REWARD_AD_CREDIT_AMOUNT)
    }

    suspend fun refundChatCredits(amount: Int, relatedSessionId: Long? = null) {
        if (amount <= 0) return
        if (!isCreditFeatureEnabledNow()) return
        aiCreditRepository.refundCredits(
            amount = amount,
            reason = AiCreditRepository.REASON_CHAT_REFUND,
            relatedSessionId = relatedSessionId
        )
    }

    /**
     * 광고 시청이 필요한지 확인
     * @return true면 광고 시청 필요 (광고 활성 && AI 크레딧 부족)
     */
    suspend fun isAdRequired(cost: Int = AiCreditRepository.LIGHT_CHAT_COST): Boolean {
        if (!isCreditRewardAdEnabledNow()) return false
        return !aiCreditRepository.hasEnoughCredits(cost)
    }

    /** 이전 월 문자 기록 가져오기에 충전이 필요한지 확인. */
    suspend fun isMonthSyncCreditRequired(cost: Int = MONTH_SYNC_CREDIT_COST): Boolean {
        if (!isCreditRewardAdEnabledNow()) return false
        return !aiCreditRepository.hasEnoughCredits(cost)
    }

    /**
     * 리워드 광고 기능이 활성화되어 있는지 확인
     */
    fun isRewardAdEnabled(): Boolean {
        return isAdFeatureEnabled()
    }

    /** 공통 리워드 광고 활성화 여부 Flow (월별 동기화 광고 등) */
    val isRewardAdEnabledFlow: Flow<Boolean> = premiumManager.premiumConfig
        .map { isAdFeatureEnabled(it.rewardAdEnabled) }
        .distinctUntilChanged()

    /** AI 크레딧 기능 표시 여부 Flow */
    val isCreditFeatureEnabledFlow: Flow<Boolean> = premiumManager.premiumConfig
        .combine(settingsDataStore.serviceTierFlow) { config, tier ->
            isCreditFeatureEnabled(config, tier)
        }
        .distinctUntilChanged()

    /** AI 크레딧 충전/차감용 리워드 광고 활성화 여부 Flow */
    val isCreditRewardAdEnabledFlow: Flow<Boolean> = premiumManager.premiumConfig
        .combine(settingsDataStore.serviceTierFlow) { config, tier ->
            isCreditRewardAdEnabled(config, tier)
        }
        .distinctUntilChanged()

    /** 배너 광고 노출 여부 Flow (RTDB 활성 + 앱 진입 5회 이상) */
    val isBannerAdEnabledFlow: Flow<Boolean> = premiumManager.premiumConfig
        .combine(settingsDataStore.appEntryCountFlow) { config, appEntryCount ->
            BannerAdVisibilityPolicy.canShowBanner(
                rewardAdEnabled = isAdFeatureEnabled(config.rewardAdEnabled),
                appEntryCount = appEntryCount
            )
        }
        .distinctUntilChanged()

    /** 리워드 1회 시청 시 충전되는 AI 크레딧 */
    fun getRewardChatCount(): Int {
        if (!isCreditRewardAdEnabled()) return 0
        return REWARD_AD_CREDIT_AMOUNT
    }

    /** 리워드 1회 시청 시 충전되는 AI 크레딧 Flow */
    val rewardCreditCountFlow: Flow<Int>
        get() = premiumManager.premiumConfig
            .combine(settingsDataStore.serviceTierFlow) { config, tier ->
                if (isCreditRewardAdEnabled(config, tier)) {
                    REWARD_AD_CREDIT_AMOUNT
                } else {
                    0
                }
            }
            .distinctUntilChanged()

    /**
     * RTDB에서 설정된 무료 동기화 허용 횟수 (기본 3회)
     */
    fun getFreeSyncCount(): Int {
        return premiumManager.premiumConfig.value.freeSyncCount
    }

    /** 무료 동기화 허용 횟수 Flow (Compose 관찰용) */
    val freeSyncCountFlow: Flow<Int>
        get() = premiumManager.premiumConfig.map { it.freeSyncCount }

    private fun isAdFeatureEnabled(): Boolean {
        return isAdFeatureEnabled(premiumManager.premiumConfig.value.rewardAdEnabled)
    }

    private fun isAdFeatureEnabled(rewardAdEnabled: Boolean): Boolean {
        return BuildVariantPolicy.isMonetizationEnabled &&
            (rewardAdEnabled || BuildVariantPolicy.isMonetizationTestOverride)
    }

    private suspend fun isCreditFeatureEnabledNow(): Boolean {
        return isCreditFeatureEnabled(
            config = premiumManager.premiumConfig.value,
            tier = settingsDataStore.getServiceTier()
        )
    }

    private fun isCreditFeatureEnabled(config: PremiumConfig): Boolean {
        return isCreditFeatureEnabled(config, currentServiceTier)
    }

    private fun isCreditFeatureEnabled(config: PremiumConfig, tier: ServiceTier): Boolean {
        val testOverride = BuildVariantPolicy.isMonetizationTestOverride
        return CreditFeaturePolicy.canShowCreditFeature(
            isMonetizationEnabled = BuildVariantPolicy.isMonetizationEnabled,
            creditAdEnabled = config.creditAdEnabled || testOverride,
            serviceTier = tier
        )
    }

    fun isCreditRewardAdEnabled(): Boolean {
        return isCreditRewardAdEnabled(premiumManager.premiumConfig.value, currentServiceTier)
    }

    private suspend fun isCreditRewardAdEnabledNow(): Boolean {
        return isCreditRewardAdEnabled(
            config = premiumManager.premiumConfig.value,
            tier = settingsDataStore.getServiceTier()
        )
    }

    private fun isCreditRewardAdEnabled(config: PremiumConfig, tier: ServiceTier): Boolean {
        val testOverride = BuildVariantPolicy.isMonetizationTestOverride
        return CreditFeaturePolicy.canUseCreditRewardAd(
            isMonetizationEnabled = BuildVariantPolicy.isMonetizationEnabled,
            creditAdEnabled = config.creditAdEnabled || testOverride,
            rewardAdEnabled = config.rewardAdEnabled || testOverride,
            serviceTier = tier
        )
    }

    private fun rewardAdId(): String {
        return if (BuildVariantPolicy.shouldUseProductionAdUnits) {
            REWARD_AD_ID
        } else {
            TEST_REWARD_AD_ID
        }
    }
}
