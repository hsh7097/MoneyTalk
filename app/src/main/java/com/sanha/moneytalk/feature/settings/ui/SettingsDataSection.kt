package com.sanha.moneytalk.feature.settings.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sanha.moneytalk.BuildConfig
import com.sanha.moneytalk.R
import com.sanha.moneytalk.core.ui.component.settings.SettingsItemCompose
import com.sanha.moneytalk.core.ui.component.settings.SettingsItemInfo
import com.sanha.moneytalk.core.ui.component.settings.SettingsSectionCompose

@Composable
internal fun SettingsDataSection(
    uiState: SettingsUiState,
    onIntent: (SettingsIntent) -> Unit,
    onOpenNotificationAccess: () -> Unit,
    onOpenSmsSettings: () -> Unit,
    onOpenGoogleDrive: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        SettingsSectionCompose(title = stringResource(R.string.settings_section_data)) {
            // 거래 알림 토글
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 60.dp)
                    .clickable {
                        onIntent(
                            SettingsIntent.ToggleNotification(!uiState.notificationEnabled)
                        )
                    }
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_notification_title),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = stringResource(R.string.settings_notification_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
                Switch(
                    checked = uiState.notificationEnabled,
                    onCheckedChange = { checked ->
                        onIntent(SettingsIntent.ToggleNotification(checked))
                    }
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SettingsItemCompose(
                info = object : SettingsItemInfo {
                    override val icon = if (uiState.notificationAccessEnabled) {
                        Icons.Default.Notifications
                    } else {
                        Icons.Default.Warning
                    }
                    override val title = stringResource(R.string.settings_notification_access_title)
                    override val subtitle = if (uiState.notificationAccessEnabled) {
                        stringResource(R.string.settings_notification_access_subtitle_enabled)
                    } else {
                        stringResource(R.string.settings_notification_access_subtitle_disabled)
                    }
                },
                onClick = {
                    onOpenNotificationAccess()
                }
            )
            if (!uiState.notificationAccessEnabled) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text(
                    text = stringResource(R.string.settings_notification_access_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SettingsItemCompose(
                info = object : SettingsItemInfo {
                    override val icon = Icons.Default.Sms
                    override val title = stringResource(R.string.sms_settings_title)
                    override val subtitle = stringResource(R.string.sms_settings_subtitle)
                },
                onClick = {
                    onOpenSmsSettings()
                }
            )
            if (BuildConfig.DEBUG) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingsItemCompose(
                    info = object : SettingsItemInfo {
                        override val icon = Icons.Default.Refresh
                        override val title = stringResource(R.string.settings_debug_full_sync_title)
                        override val subtitle = stringResource(R.string.settings_debug_full_sync_subtitle)
                    },
                    onClick = {
                        onIntent(SettingsIntent.DebugFullSyncAllMessages)
                    }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingsItemCompose(
                    info = object : SettingsItemInfo {
                        override val icon = Icons.Default.Sms
                        override val title = stringResource(R.string.settings_debug_today_sync_title)
                        override val subtitle = stringResource(R.string.settings_debug_today_sync_subtitle)
                    },
                    onClick = {
                        onIntent(SettingsIntent.DebugSyncTodayMessages)
                    }
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SettingsItemCompose(
                info = object : SettingsItemInfo {
                    override val icon = Icons.Default.Backup
                    override val title = stringResource(R.string.settings_export_title)
                    override val subtitle =
                        stringResource(R.string.settings_export_subtitle)
                },
                onClick = { onIntent(SettingsIntent.ShowExportDialog) }
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SettingsItemCompose(
                info = object : SettingsItemInfo {
                    override val icon = Icons.Default.Cloud
                    override val title =
                        stringResource(R.string.settings_google_drive_title)
                    override val subtitle = if (uiState.isGoogleSignedIn) {
                        stringResource(
                            R.string.settings_google_drive_connected,
                            uiState.googleAccountName ?: ""
                        )
                    } else {
                        stringResource(R.string.settings_google_drive_not_connected)
                    }
                },
                onClick = onOpenGoogleDrive
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SettingsItemCompose(
                info = object : SettingsItemInfo {
                    override val icon = Icons.Default.Restore
                    override val title =
                        stringResource(R.string.settings_restore_local_title)
                    override val subtitle =
                        stringResource(R.string.settings_restore_local_subtitle)
                },
                onClick = { onIntent(SettingsIntent.OpenRestoreFilePicker) }
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SettingsItemCompose(
                info = object : SettingsItemInfo {
                    override val icon = Icons.Default.ContentCopy
                    override val title = stringResource(R.string.settings_duplicate_title)
                    override val subtitle = stringResource(R.string.settings_duplicate_subtitle)
                },
                onClick = { onIntent(SettingsIntent.DeleteDuplicates) }
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SettingsItemCompose(
                info = object : SettingsItemInfo {
                    override val icon = Icons.Default.DeleteForever
                    override val title = stringResource(R.string.settings_delete_all_title)
                    override val subtitle =
                        stringResource(R.string.settings_delete_all_subtitle)
                    override val isDestructive = true
                },
                onClick = { onIntent(SettingsIntent.ShowDeleteConfirmDialog) }
            )
        }
    } // Box (settings_data)
}
