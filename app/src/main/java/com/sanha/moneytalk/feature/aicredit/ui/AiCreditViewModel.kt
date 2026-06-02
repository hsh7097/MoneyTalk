package com.sanha.moneytalk.feature.aicredit.ui

import android.app.Activity
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sanha.moneytalk.core.ad.RewardAdManager
import com.sanha.moneytalk.core.database.AiCreditRepository
import com.sanha.moneytalk.core.database.entity.AiCreditLedgerEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@Stable
data class AiCreditUiState(
    val balance: Int = 0,
    val recentLedger: List<AiCreditLedgerEntity> = emptyList(),
    val rewardCreditCount: Int = 0,
    val isRewardAdEnabled: Boolean = false
)

@HiltViewModel
class AiCreditViewModel @Inject constructor(
    private val aiCreditRepository: AiCreditRepository,
    private val rewardAdManager: RewardAdManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(AiCreditUiState())
    val uiState: StateFlow<AiCreditUiState> = _uiState.asStateFlow()

    init {
        observeCreditState()
        observeRewardAdState()
    }

    private fun observeCreditState() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                aiCreditRepository.ensureLegacyRewardChatMigrated()
            }
            aiCreditRepository.balanceFlow
                .combine(aiCreditRepository.observeRecentLedger()) { balance, ledger ->
                    balance to ledger
                }
                .collect { (balance, ledger) ->
                    _uiState.update {
                        it.copy(
                            balance = balance,
                            recentLedger = ledger
                        )
                    }
                }
        }
    }

    private fun observeRewardAdState() {
        viewModelScope.launch {
            rewardAdManager.isRewardAdEnabledFlow
                .combine(rewardAdManager.rewardCreditCountFlow) { enabled, rewardCount ->
                    enabled to rewardCount
                }
                .collect { (enabled, rewardCount) ->
                    _uiState.update {
                        it.copy(
                            isRewardAdEnabled = enabled,
                            rewardCreditCount = rewardCount
                        )
                    }
                    if (enabled) {
                        rewardAdManager.preloadAd()
                    }
                }
        }
    }

    fun showRewardAd(activity: Activity) {
        rewardAdManager.showAd(
            activity = activity,
            onRewarded = { grantRewardCredits() },
            onFailed = {}
        )
    }

    private fun grantRewardCredits() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                rewardAdManager.addRewardChats()
            }
        }
    }
}
