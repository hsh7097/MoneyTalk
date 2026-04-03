package com.sanha.moneytalk.feature.smssettings.ui

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sanha.moneytalk.core.database.SmsBlockedSenderRepository
import com.sanha.moneytalk.core.database.SmsExclusionRepository
import com.sanha.moneytalk.core.database.entity.SmsBlockedSenderEntity
import com.sanha.moneytalk.core.database.entity.SmsExclusionKeywordEntity
import com.sanha.moneytalk.core.datastore.SettingsDataStore
import com.sanha.moneytalk.core.util.DataRefreshEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@Stable
data class SmsSettingsUiState(
    val exclusionKeywords: List<SmsExclusionKeywordEntity> = emptyList(),
    val blockedSenders: List<SmsBlockedSenderEntity> = emptyList(),
    val lastSyncTime: Long = 0L
)

@HiltViewModel
class SmsSettingsViewModel @Inject constructor(
    private val smsExclusionRepository: SmsExclusionRepository,
    private val smsBlockedSenderRepository: SmsBlockedSenderRepository,
    private val settingsDataStore: SettingsDataStore,
    private val dataRefreshEvent: DataRefreshEvent
) : ViewModel() {

    private val _uiState = MutableStateFlow(SmsSettingsUiState())
    val uiState: StateFlow<SmsSettingsUiState> = _uiState.asStateFlow()

    init {
        observeBlockedSenders()
        observeLastSyncTime()
        loadExclusionKeywords()
    }

    fun requestSmsAnalysisUpdate() {
        dataRefreshEvent.emit(DataRefreshEvent.RefreshType.SMS_RECEIVED)
    }

    fun addExclusionKeyword(keyword: String) {
        viewModelScope.launch {
            val added = smsExclusionRepository.addKeyword(keyword, source = "user")
            if (added) {
                loadExclusionKeywords()
                dataRefreshEvent.emit(DataRefreshEvent.RefreshType.CATEGORY_UPDATED)
            }
        }
    }

    fun removeExclusionKeyword(keyword: String) {
        viewModelScope.launch {
            val deleted = smsExclusionRepository.removeKeyword(keyword)
            if (deleted > 0) {
                loadExclusionKeywords()
                dataRefreshEvent.emit(DataRefreshEvent.RefreshType.CATEGORY_UPDATED)
            }
        }
    }

    fun addBlockedSender(address: String) {
        viewModelScope.launch {
            smsBlockedSenderRepository.addBlockedSender(address)
        }
    }

    fun removeBlockedSender(address: String) {
        viewModelScope.launch {
            smsBlockedSenderRepository.removeBlockedSender(address)
        }
    }

    private fun observeBlockedSenders() {
        viewModelScope.launch {
            smsBlockedSenderRepository.observeBlockedSenders().collect { senders ->
                _uiState.update { it.copy(blockedSenders = senders) }
            }
        }
    }

    private fun loadExclusionKeywords() {
        viewModelScope.launch {
            val keywords = smsExclusionRepository.getAllKeywords()
            _uiState.update { it.copy(exclusionKeywords = keywords) }
        }
    }

    private fun observeLastSyncTime() {
        viewModelScope.launch {
            settingsDataStore.lastSyncTimeFlow.collect { lastSyncTime ->
                _uiState.update { it.copy(lastSyncTime = lastSyncTime) }
            }
        }
    }
}
