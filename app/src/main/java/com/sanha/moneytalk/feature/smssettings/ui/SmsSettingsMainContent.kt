package com.sanha.moneytalk.feature.smssettings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sanha.moneytalk.R
import com.sanha.moneytalk.core.ui.component.settings.SettingsItemCompose
import com.sanha.moneytalk.core.ui.component.settings.SettingsItemInfo
import com.sanha.moneytalk.core.ui.component.settings.SettingsSectionCompose
import com.sanha.moneytalk.core.util.DateUtils

@Composable
internal fun SmsSettingsMainContent(
    uiState: SmsSettingsUiState,
    onRequestSync: () -> Unit,
    onOpenBlockedPhrases: () -> Unit,
    onOpenBlockedSenders: () -> Unit,
    onOpenExcludedCards: () -> Unit
) {
    val userPhraseCount = uiState.exclusionKeywords.count { it.source != "default" }
    val defaultPhraseCount = uiState.exclusionKeywords.count { it.source == "default" }
    val excludedCardCount = uiState.ownedCards.count { !it.isOwned }
    val syncSubtitle = if (uiState.lastSyncTime > 0L) {
        stringResource(
            R.string.sms_settings_sync_subtitle_last,
            DateUtils.formatDateTime(uiState.lastSyncTime)
        )
    } else {
        stringResource(R.string.sms_settings_sync_subtitle)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item {
            SettingsSectionCompose(title = stringResource(R.string.sms_settings_section_analysis)) {
                SettingsItemCompose(
                    info = object : SettingsItemInfo {
                        override val icon = Icons.Default.Refresh
                        override val title = stringResource(R.string.sms_settings_sync_title)
                        override val subtitle = syncSubtitle
                    },
                    onClick = onRequestSync
                )
            }
        }

        item {
            SettingsSectionCompose(title = stringResource(R.string.sms_settings_section_block)) {
                SettingsItemCompose(
                    info = object : SettingsItemInfo {
                        override val icon = Icons.Default.Sms
                        override val title = stringResource(R.string.sms_settings_blocked_phrase_title)
                        override val subtitle = stringResource(
                            R.string.sms_settings_blocked_phrase_subtitle_count,
                            userPhraseCount,
                            defaultPhraseCount
                        )
                    },
                    onClick = onOpenBlockedPhrases
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingsItemCompose(
                    info = object : SettingsItemInfo {
                        override val icon = Icons.Default.Block
                        override val title = stringResource(R.string.sms_settings_blocked_sender_title)
                        override val subtitle = if (uiState.blockedSenders.isNotEmpty()) {
                            stringResource(
                                R.string.sms_settings_blocked_sender_subtitle_count,
                                uiState.blockedSenders.size
                            )
                        } else {
                            stringResource(R.string.sms_settings_blocked_sender_subtitle_empty)
                        }
                    },
                    onClick = onOpenBlockedSenders
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingsItemCompose(
                    info = object : SettingsItemInfo {
                        override val icon = Icons.Default.AccountBalanceWallet
                        override val title = stringResource(R.string.sms_settings_excluded_card_title)
                        override val subtitle = if (excludedCardCount > 0) {
                            stringResource(
                                R.string.sms_settings_excluded_card_subtitle_count,
                                excludedCardCount,
                                uiState.ownedCards.size
                            )
                        } else {
                            stringResource(R.string.sms_settings_excluded_card_subtitle_empty)
                        }
                    },
                    onClick = onOpenExcludedCards
                )
            }
        }
    }
}
