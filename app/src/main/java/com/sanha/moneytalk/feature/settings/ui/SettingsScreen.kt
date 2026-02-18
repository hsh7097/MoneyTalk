package com.sanha.moneytalk.feature.settings.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import com.sanha.moneytalk.BuildConfig
import com.sanha.moneytalk.R
import com.sanha.moneytalk.core.theme.ThemeMode
import com.sanha.moneytalk.core.ui.component.settings.SettingsItemCompose
import com.sanha.moneytalk.core.ui.component.settings.SettingsItemInfo
import com.sanha.moneytalk.core.ui.component.settings.SettingsSectionCompose
import com.sanha.moneytalk.core.util.DataBackupManager
import kotlinx.coroutines.launch

/** 설정 탭 메인 화면. API 키, 월 시작일, 카드 관리, 데이터 관리 등 앱 설정 항목을 표시 */
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()

    // 플랫폼 연동 상태 (ActivityResult 런처와 직접 연결되어 Compose-local 유지)
    var isExportingToGoogleDrive by remember { mutableStateOf(false) }
    var googleSignInSource by remember { mutableStateOf("settings") }

    // 구글 로그인 런처
    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            viewModel.handleGoogleSignInResult(context, account)
            viewModel.loadDriveBackupFiles()
            if (googleSignInSource == "settings") {
                viewModel.onIntent(SettingsIntent.ShowGoogleDriveDialog)
            }
        } catch (e: ApiException) {
            viewModel.checkGoogleSignIn(context)
            if (viewModel.uiState.value.isGoogleSignedIn) {
                viewModel.loadDriveBackupFiles()
                if (googleSignInSource == "settings") {
                    viewModel.onIntent(SettingsIntent.ShowGoogleDriveDialog)
                }
            }
        }
    }

    // 백업 파일 생성 런처
    val backupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        uri?.let { viewModel.exportBackup(context, it) }
    }

    // 복원 파일 선택 런처
    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            viewModel.onIntent(SettingsIntent.SetPendingRestoreUri(it))
        }
    }

    // 구글 로그인 상태 체크 및 데이터 새로고침
    LaunchedEffect(Unit) {
        viewModel.checkGoogleSignIn(context)
        viewModel.refresh()
    }

    // 백업 콘텐츠가 준비되면 파일 저장 다이얼로그 열기 (로컬 내보내기일 때만)
    LaunchedEffect(uiState.backupContent) {
        uiState.backupContent?.let {
            if (uiState.activeDialog != SettingsDialog.EXPORT &&
                uiState.activeDialog != SettingsDialog.GOOGLE_DRIVE &&
                !isExportingToGoogleDrive
            ) {
                val fileName = DataBackupManager.generateBackupFileName(uiState.exportFormat)
                backupLauncher.launch(fileName)
            }
        }
    }

    // Google Drive export 완료 감지
    LaunchedEffect(uiState.isLoading) {
        if (isExportingToGoogleDrive && !uiState.isLoading) {
            isExportingToGoogleDrive = false
        }
    }

    // 복원 파일 선택 트리거
    LaunchedEffect(uiState.triggerRestoreFilePicker) {
        if (uiState.triggerRestoreFilePicker) {
            restoreLauncher.launch(arrayOf("application/json"))
            viewModel.consumeRestoreFilePickerTrigger()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    text = stringResource(R.string.settings_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            // 화면 설정 (테마)
            item {
                val themeModeLabel = when (uiState.themeMode) {
                    ThemeMode.SYSTEM -> stringResource(R.string.settings_theme_system)
                    ThemeMode.LIGHT -> stringResource(R.string.settings_theme_light)
                    ThemeMode.DARK -> stringResource(R.string.settings_theme_dark)
                }
                SettingsSectionCompose(title = stringResource(R.string.settings_section_display)) {
                    SettingsItemCompose(
                        info = object : SettingsItemInfo {
                            override val icon = when (uiState.themeMode) {
                                ThemeMode.SYSTEM -> Icons.Default.Settings
                                ThemeMode.LIGHT -> Icons.Default.LightMode
                                ThemeMode.DARK -> Icons.Default.DarkMode
                            }
                            override val title = stringResource(R.string.settings_theme_label)
                            override val subtitle = themeModeLabel
                        },
                        onClick = { viewModel.onIntent(SettingsIntent.ShowThemeDialog) }
                    )
                }
            }

            // 기간 설정
            item {
                SettingsSectionCompose(title = stringResource(R.string.settings_section_budget)) {
                    SettingsItemCompose(
                        info = object : SettingsItemInfo {
                            override val icon = Icons.Default.CalendarMonth
                            override val title = stringResource(R.string.settings_month_start_title)
                            override val subtitle = if (uiState.monthStartDay == 1) {
                                stringResource(R.string.settings_month_start_default)
                            } else {
                                stringResource(
                                    R.string.settings_month_start_custom,
                                    uiState.monthStartDay
                                )
                            }
                        },
                        onClick = { viewModel.onIntent(SettingsIntent.ShowMonthStartDayDialog) }
                    )
                }
            }

            // AI 설정
            item {
                SettingsSectionCompose(title = stringResource(R.string.settings_section_ai)) {
                    // 카테고리 정리 버튼 (커스텀 레이아웃 - 공통 컴포넌트 미적용)
                    val isClassifyEnabled = uiState.hasApiKey &&
                            uiState.unclassifiedCount > 0 &&
                            !uiState.isBackgroundClassifying &&
                            !uiState.isClassifying
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (isClassifyEnabled) {
                                    Modifier.clickable { viewModel.onIntent(SettingsIntent.ClassifyUnclassified) }
                                } else {
                                    Modifier
                                }
                            )
                            .alpha(if (uiState.isBackgroundClassifying || uiState.isClassifying) 0.6f else 1f)
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.settings_classify_title),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (uiState.isBackgroundClassifying) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(12.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Text(
                                        text = when {
                                            !uiState.hasApiKey -> stringResource(R.string.settings_classify_no_api_key)
                                            uiState.isBackgroundClassifying -> stringResource(R.string.settings_classify_background)
                                            uiState.unclassifiedCount > 0 -> stringResource(R.string.settings_classify_unclassified, uiState.unclassifiedCount)
                                            else -> stringResource(R.string.settings_classify_done)
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                    }
                }
            }

            // 데이터 관리
            item {
                val userKeywordCount = uiState.exclusionKeywords.count { it.source != "default" }
                val defaultKeywordCount = uiState.exclusionKeywords.count { it.source == "default" }

                SettingsSectionCompose(title = stringResource(R.string.settings_section_data)) {
                    SettingsItemCompose(
                        info = object : SettingsItemInfo {
                            override val icon = Icons.Default.Block
                            override val title = stringResource(R.string.settings_exclusion_title)
                            override val subtitle = if (uiState.exclusionKeywords.isNotEmpty()) {
                                stringResource(R.string.settings_exclusion_subtitle_count, userKeywordCount, defaultKeywordCount)
                            } else {
                                stringResource(R.string.settings_exclusion_subtitle_empty)
                            }
                        },
                        onClick = { viewModel.onIntent(SettingsIntent.ShowExclusionKeywordDialog) }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SettingsItemCompose(
                        info = object : SettingsItemInfo {
                            override val icon = Icons.Default.Backup
                            override val title = stringResource(R.string.settings_export_title)
                            override val subtitle =
                                stringResource(R.string.settings_export_subtitle)
                        },
                        onClick = { viewModel.onIntent(SettingsIntent.ShowExportDialog) }
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
                        onClick = {
                            googleSignInSource = "settings"
                            coroutineScope.launch {
                                val signInIntent = viewModel.tryOpenGoogleDrive(context)
                                if (signInIntent == null) {
                                    viewModel.loadDriveBackupFiles()
                                    viewModel.onIntent(SettingsIntent.ShowGoogleDriveDialog)
                                } else {
                                    googleSignInLauncher.launch(signInIntent)
                                }
                            }
                        }
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
                        onClick = { viewModel.onIntent(SettingsIntent.OpenRestoreFilePicker) }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SettingsItemCompose(
                        info = object : SettingsItemInfo {
                            override val icon = Icons.Default.ContentCopy
                            override val title = stringResource(R.string.settings_duplicate_title)
                            override val subtitle = stringResource(R.string.settings_duplicate_subtitle)
                        },
                        onClick = { viewModel.onIntent(SettingsIntent.DeleteDuplicates) }
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
                        onClick = { viewModel.onIntent(SettingsIntent.ShowDeleteConfirmDialog) }
                    )
                }
            }

            // 앱 정보
            item {
                SettingsSectionCompose(title = stringResource(R.string.settings_section_app)) {
                    SettingsItemCompose(
                        info = object : SettingsItemInfo {
                            override val icon = Icons.Default.Info
                            override val title = stringResource(R.string.settings_version_title)
                            override val subtitle = BuildConfig.VERSION_NAME
                        },
                        onClick = { viewModel.onIntent(SettingsIntent.ShowAppInfoDialog) }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SettingsItemCompose(
                        info = object : SettingsItemInfo {
                            override val icon = Icons.Default.Description
                            override val title = stringResource(R.string.settings_privacy_title)
                        },
                        onClick = { viewModel.onIntent(SettingsIntent.ShowPrivacyDialog) }
                    )
                }
            }
        }

        // 로딩 인디케이터
        if (uiState.isLoading || uiState.isClassifying) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(32.dp)
                            .widthIn(min = 200.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (uiState.isClassifying) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(48.dp),
                                strokeWidth = 4.dp
                            )
                            Spacer(modifier = Modifier.height(16.dp))

                            if (uiState.classifyProgressTotal > 0) {
                                val progress =
                                    uiState.classifyProgressCurrent.toFloat() / uiState.classifyProgressTotal.toFloat()
                                LinearProgressIndicator(
                                    progress = { progress.coerceIn(0f, 1f) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp),
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = stringResource(R.string.settings_classify_progress, uiState.classifyProgressCurrent, uiState.classifyProgressTotal),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                            }

                            Text(
                                text = uiState.classifyProgress.ifBlank { stringResource(R.string.common_processing) },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        } else {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(stringResource(R.string.common_processing))
                        }
                    }
                }
            }
        }
    }

    // ========== 다이얼로그 (activeDialog 기반) ==========

    when (uiState.activeDialog) {
        SettingsDialog.API_KEY -> {
            // API 키 직접 설정 UI 제거됨 (서버 키 사용)
            viewModel.onIntent(SettingsIntent.DismissDialog)
        }

        SettingsDialog.MONTH_START_DAY -> {
            MonthStartDayDialog(
                initialValue = uiState.monthStartDay,
                onDismiss = { viewModel.onIntent(SettingsIntent.DismissDialog) },
                onConfirm = { day -> viewModel.onIntent(SettingsIntent.SaveMonthStartDay(day)) }
            )
        }

        SettingsDialog.THEME -> {
            ThemeModeDialog(
                currentMode = uiState.themeMode,
                onModeChange = { viewModel.onIntent(SettingsIntent.SaveThemeMode(it)) },
                onDismiss = { viewModel.onIntent(SettingsIntent.DismissDialog) }
            )
        }

        SettingsDialog.EXPORT -> {
            ExportDialog(
                availableCards = uiState.availableCards,
                availableCategories = uiState.availableCategories,
                currentFilter = uiState.exportFilter,
                currentFormat = uiState.exportFormat,
                isGoogleSignedIn = uiState.isGoogleSignedIn,
                onDismiss = { viewModel.onIntent(SettingsIntent.DismissDialog) },
                onFilterChange = { viewModel.setExportFilter(it) },
                onFormatChange = { viewModel.setExportFormat(it) },
                onExportLocal = {
                    viewModel.prepareBackup()
                    viewModel.onIntent(SettingsIntent.DismissDialog)
                },
                onExportGoogleDrive = {
                    isExportingToGoogleDrive = true
                    viewModel.prepareBackup()
                    viewModel.onIntent(SettingsIntent.DismissDialog)
                    viewModel.exportToGoogleDrive()
                },
                onSignInGoogle = {
                    googleSignInSource = "export"
                    coroutineScope.launch {
                        val signInIntent = viewModel.tryOpenGoogleDrive(context)
                        if (signInIntent == null) {
                            viewModel.checkGoogleSignIn(context)
                        } else {
                            googleSignInLauncher.launch(signInIntent)
                        }
                    }
                }
            )
        }

        SettingsDialog.GOOGLE_DRIVE -> {
            GoogleDriveDialog(
                backupFiles = uiState.driveBackupFiles,
                accountName = uiState.googleAccountName,
                onDismiss = { viewModel.onIntent(SettingsIntent.DismissDialog) },
                onRefresh = { viewModel.loadDriveBackupFiles() },
                onRestore = { fileId ->
                    viewModel.restoreFromGoogleDrive(fileId)
                    viewModel.onIntent(SettingsIntent.DismissDialog)
                },
                onDelete = { fileId ->
                    viewModel.deleteDriveBackupFile(fileId)
                },
                onSignOut = {
                    viewModel.signOutGoogle(context)
                    viewModel.onIntent(SettingsIntent.DismissDialog)
                }
            )
        }

        SettingsDialog.DELETE_CONFIRM -> {
            AlertDialog(
                onDismissRequest = { viewModel.onIntent(SettingsIntent.DismissDialog) },
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
                        onClick = { viewModel.onIntent(SettingsIntent.DeleteAllData) },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text(stringResource(R.string.common_delete))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.onIntent(SettingsIntent.DismissDialog) }) {
                        Text(stringResource(R.string.common_cancel))
                    }
                }
            )
        }

        SettingsDialog.RESTORE_CONFIRM -> {
            AlertDialog(
                onDismissRequest = { viewModel.onIntent(SettingsIntent.DismissDialog) },
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
                            uiState.pendingRestoreUri?.let { viewModel.importBackup(context, it) }
                            viewModel.onIntent(SettingsIntent.DismissDialog)
                        }
                    ) {
                        Text(stringResource(R.string.common_restore))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.onIntent(SettingsIntent.DismissDialog) }) {
                        Text(stringResource(R.string.common_cancel))
                    }
                }
            )
        }

        SettingsDialog.APP_INFO -> {
            AppInfoDialog(onDismiss = { viewModel.onIntent(SettingsIntent.DismissDialog) })
        }

        SettingsDialog.PRIVACY -> {
            PrivacyPolicyDialog(onDismiss = { viewModel.onIntent(SettingsIntent.DismissDialog) })
        }

        SettingsDialog.EXCLUSION_KEYWORD -> {
            ExclusionKeywordDialog(
                keywords = uiState.exclusionKeywords,
                onDismiss = { viewModel.onIntent(SettingsIntent.DismissDialog) },
                onAdd = { keyword -> viewModel.onIntent(SettingsIntent.AddExclusionKeyword(keyword)) },
                onRemove = { keyword ->
                    viewModel.onIntent(
                        SettingsIntent.RemoveExclusionKeyword(
                            keyword
                        )
                    )
                }
            )
        }

        null -> { /* 다이얼로그 미표시 */
        }
    }
}
