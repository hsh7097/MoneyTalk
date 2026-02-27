package com.sanha.moneytalk.feature.smssettings.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.sanha.moneytalk.R
import com.sanha.moneytalk.core.database.entity.SmsBlockedSenderEntity
import com.sanha.moneytalk.core.database.entity.SmsExclusionKeywordEntity
import com.sanha.moneytalk.core.ui.component.settings.SettingsItemCompose
import com.sanha.moneytalk.core.ui.component.settings.SettingsItemInfo
import com.sanha.moneytalk.core.ui.component.settings.SettingsSectionCompose
import com.sanha.moneytalk.core.util.DateUtils

private object SmsSettingsRoute {
    const val MAIN = "main"
    const val BLOCKED_PHRASES = "blocked_phrases"
    const val BLOCKED_SENDERS = "blocked_senders"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsSettingsScreen(
    onBack: () -> Unit,
    viewModel: SmsSettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: SmsSettingsRoute.MAIN

    BackHandler(enabled = currentRoute != SmsSettingsRoute.MAIN) {
        navController.popBackStack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when (currentRoute) {
                            SmsSettingsRoute.BLOCKED_PHRASES -> stringResource(R.string.sms_settings_blocked_phrase_page_title)
                            SmsSettingsRoute.BLOCKED_SENDERS -> stringResource(R.string.sms_settings_blocked_sender_page_title)
                            else -> stringResource(R.string.sms_settings_title)
                        }
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (currentRoute == SmsSettingsRoute.MAIN) {
                                onBack()
                            } else {
                                navController.popBackStack()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = SmsSettingsRoute.MAIN,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            composable(SmsSettingsRoute.MAIN) {
                SmsSettingsMainContent(
                    uiState = uiState,
                    onRequestSync = {
                        viewModel.requestSmsAnalysisUpdate()
                    },
                    onOpenBlockedPhrases = { navController.navigate(SmsSettingsRoute.BLOCKED_PHRASES) },
                    onOpenBlockedSenders = { navController.navigate(SmsSettingsRoute.BLOCKED_SENDERS) }
                )
            }

            composable(SmsSettingsRoute.BLOCKED_PHRASES) {
                BlockedPhraseManageScreen(
                    keywords = uiState.exclusionKeywords,
                    onAdd = viewModel::addExclusionKeyword,
                    onRemove = viewModel::removeExclusionKeyword
                )
            }

            composable(SmsSettingsRoute.BLOCKED_SENDERS) {
                BlockedSenderManageScreen(
                    blockedSenders = uiState.blockedSenders,
                    onAdd = viewModel::addBlockedSender,
                    onRemove = viewModel::removeBlockedSender
                )
            }
        }
    }
}

@Composable
private fun SmsSettingsMainContent(
    uiState: SmsSettingsUiState,
    onRequestSync: () -> Unit,
    onOpenBlockedPhrases: () -> Unit,
    onOpenBlockedSenders: () -> Unit
) {
    val userPhraseCount = uiState.exclusionKeywords.count { it.source != "default" }
    val defaultPhraseCount = uiState.exclusionKeywords.count { it.source == "default" }
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
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
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
            }
        }
    }
}

@Composable
private fun BlockedPhraseManageScreen(
    keywords: List<SmsExclusionKeywordEntity>,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit
) {
    var newKeyword by remember { mutableStateOf("") }
    val defaultKeywords = keywords.filter { it.source == "default" }
    val userKeywords = keywords.filter { it.source != "default" }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(
                text = stringResource(R.string.settings_exclusion_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = newKeyword,
                    onValueChange = { newKeyword = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(stringResource(R.string.settings_exclusion_input_hint)) },
                    singleLine = true
                )
                TextButton(
                    onClick = {
                        val value = newKeyword.trim()
                        if (value.isNotBlank()) {
                            onAdd(value)
                            newKeyword = ""
                        }
                    },
                    enabled = newKeyword.isNotBlank()
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null)
                    Text(text = stringResource(R.string.common_add))
                }
            }
        }

        if (userKeywords.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.settings_exclusion_user_section, userKeywords.size),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            items(userKeywords, key = { it.keyword }) { keyword ->
                BlockedPhraseItem(
                    keyword = keyword.keyword,
                    source = keyword.source,
                    canDelete = true,
                    onDelete = { onRemove(keyword.keyword) }
                )
            }
        }

        if (defaultKeywords.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.settings_exclusion_default_section, defaultKeywords.size),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            items(defaultKeywords, key = { it.keyword }) { keyword ->
                BlockedPhraseItem(
                    keyword = keyword.keyword,
                    source = keyword.source,
                    canDelete = false,
                    onDelete = {}
                )
            }
        }

        if (keywords.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.settings_exclusion_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun BlockedPhraseItem(
    keyword: String,
    source: String,
    canDelete: Boolean,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = keyword,
                style = MaterialTheme.typography.bodyLarge
            )
            if (source == "chat") {
                Text(
                    text = stringResource(R.string.settings_exclusion_source_chat),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 6.dp)
                )
            }
        }
        IconButton(
            onClick = onDelete,
            enabled = canDelete
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = stringResource(R.string.common_delete),
                tint = if (canDelete) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
private fun BlockedSenderManageScreen(
    blockedSenders: List<SmsBlockedSenderEntity>,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit
) {
    var newAddress by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(
                text = stringResource(R.string.sms_settings_blocked_sender_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = newAddress,
                    onValueChange = { newAddress = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(stringResource(R.string.sms_settings_blocked_sender_input_hint)) },
                    singleLine = true
                )
                TextButton(
                    onClick = {
                        val value = newAddress.trim()
                        if (value.isNotBlank()) {
                            onAdd(value)
                            newAddress = ""
                        }
                    },
                    enabled = newAddress.isNotBlank()
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null)
                    Text(text = stringResource(R.string.common_add))
                }
            }
        }

        if (blockedSenders.isNotEmpty()) {
            items(blockedSenders, key = { it.address }) { sender ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = sender.rawAddress.ifBlank { sender.address },
                            style = MaterialTheme.typography.bodyLarge
                        )
                        if (sender.rawAddress.isNotBlank() && sender.rawAddress != sender.address) {
                            Text(
                                text = sender.address,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = { onRemove(sender.address) }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(R.string.common_delete),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        } else {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.sms_settings_blocked_sender_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
