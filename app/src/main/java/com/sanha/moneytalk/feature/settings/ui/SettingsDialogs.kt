package com.sanha.moneytalk.feature.settings.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.sanha.moneytalk.R

@Composable
internal fun SettingsDialogs(
    uiState: SettingsUiState,
    onIntent: (SettingsIntent) -> Unit,
    onExportGoogleDrive: () -> Unit,
    onSignInGoogle: () -> Unit
) {
    when (uiState.activeDialog) {
        SettingsDialog.MONTH_START_DAY -> {
            MonthStartDayDialog(
                initialValue = uiState.monthStartDay,
                onDismiss = { onIntent(SettingsIntent.DismissDialog) },
                onConfirm = { day -> onIntent(SettingsIntent.SaveMonthStartDay(day)) }
            )
        }

        SettingsDialog.THEME -> {
            ThemeModeDialog(
                currentMode = uiState.themeMode,
                onModeChange = { onIntent(SettingsIntent.SaveThemeMode(it)) },
                onDismiss = { onIntent(SettingsIntent.DismissDialog) }
            )
        }

        SettingsDialog.EXPORT -> {
            ExportDialog(
                availableCards = uiState.availableCards,
                availableCategories = uiState.availableCategories,
                currentFilter = uiState.exportFilter,
                currentFormat = uiState.exportFormat,
                isGoogleSignedIn = uiState.isGoogleSignedIn,
                onDismiss = { onIntent(SettingsIntent.DismissDialog) },
                onFilterChange = { onIntent(SettingsIntent.SetExportFilter(it)) },
                onFormatChange = { onIntent(SettingsIntent.SetExportFormat(it)) },
                onExportLocal = {
                    onIntent(SettingsIntent.PrepareBackup)
                    onIntent(SettingsIntent.DismissDialog)
                },
                onExportGoogleDrive = onExportGoogleDrive,
                onSignInGoogle = onSignInGoogle
            )
        }

        SettingsDialog.GOOGLE_DRIVE -> {
            GoogleDriveDialog(
                backupFiles = uiState.driveBackupFiles,
                accountName = uiState.googleAccountName,
                onDismiss = { onIntent(SettingsIntent.DismissDialog) },
                onRefresh = { onIntent(SettingsIntent.LoadDriveBackupFiles) },
                onRestore = { fileId ->
                    onIntent(SettingsIntent.RestoreDriveBackup(fileId))
                    onIntent(SettingsIntent.DismissDialog)
                },
                onDelete = { fileId ->
                    onIntent(SettingsIntent.DeleteDriveBackup(fileId))
                },
                onSignOut = {
                    onIntent(SettingsIntent.SignOutGoogle)
                    onIntent(SettingsIntent.DismissDialog)
                }
            )
        }

        SettingsDialog.DELETE_CONFIRM -> {
            AlertDialog(
                onDismissRequest = { onIntent(SettingsIntent.DismissDialog) },
                icon = {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                },
                title = { Text(stringResource(R.string.dialog_delete_all_title)) },
                text = {
                    Text(stringResource(R.string.dialog_delete_all_message))
                },
                confirmButton = {
                    TextButton(
                        onClick = { onIntent(SettingsIntent.DeleteAllData) },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text(stringResource(R.string.common_delete))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { onIntent(SettingsIntent.DismissDialog) }) {
                        Text(stringResource(R.string.common_cancel))
                    }
                }
            )
        }

        SettingsDialog.RESTORE_CONFIRM -> {
            AlertDialog(
                onDismissRequest = { onIntent(SettingsIntent.DismissDialog) },
                icon = {
                    Icon(
                        Icons.Default.Restore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                title = { Text(stringResource(R.string.dialog_restore_title)) },
                text = {
                    Text(stringResource(R.string.dialog_restore_message))
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            uiState.pendingRestoreUri?.let { onIntent(SettingsIntent.ImportBackup(it)) }
                            onIntent(SettingsIntent.DismissDialog)
                        }
                    ) {
                        Text(stringResource(R.string.common_restore))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { onIntent(SettingsIntent.DismissDialog) }) {
                        Text(stringResource(R.string.common_cancel))
                    }
                }
            )
        }

        SettingsDialog.APP_INFO -> {
            AppInfoDialog(onDismiss = { onIntent(SettingsIntent.DismissDialog) })
        }

        SettingsDialog.PRIVACY -> {
            PrivacyPolicyDialog(onDismiss = { onIntent(SettingsIntent.DismissDialog) })
        }

        SettingsDialog.MONTHLY_BUDGET -> {
            MonthlyBudgetDialog(
                initialAmount = uiState.monthlyBudget ?: 0,
                onDismiss = { onIntent(SettingsIntent.DismissDialog) },
                onConfirm = { amount -> onIntent(SettingsIntent.SaveMonthlyBudget(amount)) }
            )
        }

        SettingsDialog.BUDGET_BOTTOM_SHEET -> {
            BudgetBottomSheet(
                currentTotalBudget = uiState.monthlyBudget,
                currentCategoryBudgets = uiState.categoryBudgets,
                onDismiss = { onIntent(SettingsIntent.DismissDialog) },
                onSave = { total, categories ->
                    onIntent(SettingsIntent.SaveBudgets(total, categories))
                }
            )
        }

        null -> { /* 다이얼로그 미표시 */
        }
    }
}
