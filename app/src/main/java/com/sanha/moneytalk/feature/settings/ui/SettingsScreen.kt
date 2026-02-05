package com.sanha.moneytalk.feature.settings.ui

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import com.sanha.moneytalk.core.util.DataBackupManager
import com.sanha.moneytalk.core.util.DriveBackupFile
import com.sanha.moneytalk.core.util.ExportFilter
import com.sanha.moneytalk.core.util.ExportFormat
import com.sanha.moneytalk.core.util.GoogleDriveHelper
import androidx.compose.ui.res.stringResource
import com.sanha.moneytalk.R
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val numberFormat = NumberFormat.getNumberInstance(Locale.KOREA)

    var showIncomeDialog by remember { mutableStateOf(false) }
    var showApiKeyDialog by remember { mutableStateOf(false) }
    var showMonthStartDayDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showRestoreConfirmDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showGoogleDriveDialog by remember { mutableStateOf(false) }
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }

    // 구글 로그인 런처
    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                viewModel.handleGoogleSignInResult(context, account)
                viewModel.loadDriveBackupFiles()
            } catch (e: ApiException) {
                // 로그인 실패
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
            pendingRestoreUri = it
            showRestoreConfirmDialog = true
        }
    }

    // 구글 로그인 상태 체크 및 데이터 새로고침
    LaunchedEffect(Unit) {
        viewModel.checkGoogleSignIn(context)
        viewModel.refresh()  // 다른 탭에서 변경된 데이터 반영 (미분류 항목 수 등)
    }

    // 백업 콘텐츠가 준비되면 파일 저장 다이얼로그 열기
    LaunchedEffect(uiState.backupContent) {
        uiState.backupContent?.let {
            if (!showExportDialog && !showGoogleDriveDialog) {
                val fileName = DataBackupManager.generateBackupFileName(uiState.exportFormat)
                backupLauncher.launch(fileName)
            }
        }
    }

    // 메시지 표시
    LaunchedEffect(uiState.message) {
        uiState.message?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearMessage()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Text(
                        text = stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                // 수입/예산 관리
                item {
                    SettingsSection(title = stringResource(R.string.settings_section_budget)) {
                        SettingsItem(
                            icon = Icons.Default.AttachMoney,
                            title = stringResource(R.string.settings_income_title),
                            subtitle = if (uiState.monthlyIncome > 0) {
                                stringResource(R.string.common_won, numberFormat.format(uiState.monthlyIncome))
                            } else {
                                stringResource(R.string.settings_income_subtitle_empty)
                            },
                            onClick = { showIncomeDialog = true }
                        )
                        SettingsItem(
                            icon = Icons.Default.CalendarMonth,
                            title = stringResource(R.string.settings_month_start_title),
                            subtitle = if (uiState.monthStartDay == 1) {
                                stringResource(R.string.settings_month_start_default)
                            } else {
                                stringResource(R.string.settings_month_start_custom, uiState.monthStartDay)
                            },
                            onClick = { showMonthStartDayDialog = true }
                        )
                        SettingsItem(
                            icon = Icons.Default.PieChart,
                            title = stringResource(R.string.settings_category_budget_title),
                            subtitle = stringResource(R.string.settings_category_budget_subtitle),
                            onClick = { /* TODO */ }
                        )
                    }
                }

                // API 설정
                item {
                    SettingsSection(title = stringResource(R.string.settings_section_ai)) {
                        SettingsItem(
                            icon = Icons.Default.Key,
                            title = stringResource(R.string.settings_api_key_title),
                            subtitle = if (uiState.hasApiKey) {
                                stringResource(R.string.settings_api_key_set, uiState.apiKey)
                            } else {
                                stringResource(R.string.settings_api_key_not_set)
                            },
                            onClick = { showApiKeyDialog = true }
                        )
                        SettingsItem(
                            icon = Icons.Default.AutoAwesome,
                            title = "AI 카테고리 자동 분류",
                            subtitle = if (!uiState.hasApiKey) {
                                "API 키를 먼저 설정해주세요"
                            } else if (uiState.unclassifiedCount > 0) {
                                "미분류 ${uiState.unclassifiedCount}건"
                            } else {
                                "미분류 항목 없음"
                            },
                            onClick = {
                                viewModel.classifyUnclassifiedExpenses()
                            }
                        )
                    }
                }

                // 데이터 관리
                item {
                    SettingsSection(title = stringResource(R.string.settings_section_data)) {
                        SettingsItem(
                            icon = Icons.Default.Backup,
                            title = stringResource(R.string.settings_export_title),
                            subtitle = stringResource(R.string.settings_export_subtitle),
                            onClick = { showExportDialog = true }
                        )
                        SettingsItem(
                            icon = Icons.Default.Cloud,
                            title = stringResource(R.string.settings_google_drive_title),
                            subtitle = if (uiState.isGoogleSignedIn) {
                                stringResource(R.string.settings_google_drive_connected, uiState.googleAccountName ?: "")
                            } else {
                                stringResource(R.string.settings_google_drive_not_connected)
                            },
                            onClick = {
                                if (uiState.isGoogleSignedIn) {
                                    viewModel.loadDriveBackupFiles()
                                    showGoogleDriveDialog = true
                                } else {
                                    val signInIntent = GoogleDriveHelper().getSignInIntent(context)
                                    googleSignInLauncher.launch(signInIntent)
                                }
                            }
                        )
                        SettingsItem(
                            icon = Icons.Default.Restore,
                            title = stringResource(R.string.settings_restore_local_title),
                            subtitle = stringResource(R.string.settings_restore_local_subtitle),
                            onClick = { restoreLauncher.launch(arrayOf("application/json")) }
                        )
                        SettingsItem(
                            icon = Icons.Default.ContentCopy,
                            title = "중복 데이터 삭제",
                            subtitle = "금액, 가게명, 시간이 같은 중복 항목 제거",
                            onClick = { viewModel.deleteDuplicates() }
                        )
                        SettingsItem(
                            icon = Icons.Default.DeleteForever,
                            title = stringResource(R.string.settings_delete_all_title),
                            subtitle = stringResource(R.string.settings_delete_all_subtitle),
                            onClick = { showDeleteConfirmDialog = true },
                            isDestructive = true
                        )
                    }
                }

                // 앱 정보
                item {
                    SettingsSection(title = stringResource(R.string.settings_section_app)) {
                        SettingsItem(
                            icon = Icons.Default.Info,
                            title = stringResource(R.string.settings_version_title),
                            subtitle = "1.0.0",
                            onClick = { }
                        )
                        SettingsItem(
                            icon = Icons.Default.Description,
                            title = stringResource(R.string.settings_privacy_title),
                            subtitle = "",
                            onClick = { /* TODO */ }
                        )
                    }
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
                        modifier = Modifier.padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(stringResource(R.string.common_processing))
                    }
                }
            }
        }
    }

    // 수입 설정 다이얼로그
    if (showIncomeDialog) {
        IncomeSettingDialog(
            initialValue = uiState.monthlyIncome,
            onDismiss = { showIncomeDialog = false },
            onConfirm = { amount ->
                viewModel.saveMonthlyIncome(amount)
                showIncomeDialog = false
            }
        )
    }

    // API 키 설정 다이얼로그
    if (showApiKeyDialog) {
        ApiKeySettingDialog(
            currentKeyHint = if (uiState.hasApiKey) "현재 설정됨" else "",
            onDismiss = { showApiKeyDialog = false },
            onConfirm = { key ->
                viewModel.saveApiKey(key)
                showApiKeyDialog = false
            }
        )
    }

    // 월 시작일 설정 다이얼로그
    if (showMonthStartDayDialog) {
        MonthStartDayDialog(
            initialValue = uiState.monthStartDay,
            onDismiss = { showMonthStartDayDialog = false },
            onConfirm = { day ->
                viewModel.saveMonthStartDay(day)
                showMonthStartDayDialog = false
            }
        )
    }

    // 내보내기 다이얼로그
    if (showExportDialog) {
        ExportDialog(
            availableCards = uiState.availableCards,
            availableCategories = uiState.availableCategories,
            currentFilter = uiState.exportFilter,
            currentFormat = uiState.exportFormat,
            isGoogleSignedIn = uiState.isGoogleSignedIn,
            onDismiss = { showExportDialog = false },
            onFilterChange = { viewModel.setExportFilter(it) },
            onFormatChange = { viewModel.setExportFormat(it) },
            onExportLocal = {
                viewModel.prepareBackup()
                showExportDialog = false
            },
            onExportGoogleDrive = {
                viewModel.prepareBackup()
                showExportDialog = false
                // prepareBackup 완료 후 드라이브에 업로드
                viewModel.exportToGoogleDrive()
            },
            onSignInGoogle = {
                val signInIntent = GoogleDriveHelper().getSignInIntent(context)
                googleSignInLauncher.launch(signInIntent)
            }
        )
    }

    // 구글 드라이브 다이얼로그
    if (showGoogleDriveDialog) {
        GoogleDriveDialog(
            backupFiles = uiState.driveBackupFiles,
            accountName = uiState.googleAccountName,
            onDismiss = { showGoogleDriveDialog = false },
            onRefresh = { viewModel.loadDriveBackupFiles() },
            onRestore = { fileId ->
                viewModel.restoreFromGoogleDrive(fileId)
                showGoogleDriveDialog = false
            },
            onDelete = { fileId ->
                viewModel.deleteDriveBackupFile(fileId)
            },
            onSignOut = {
                viewModel.signOutGoogle(context)
                showGoogleDriveDialog = false
            }
        )
    }

    // 전체 삭제 확인 다이얼로그
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
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
                    onClick = {
                        viewModel.deleteAllData()
                        showDeleteConfirmDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(R.string.common_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    // 복원 확인 다이얼로그
    if (showRestoreConfirmDialog && pendingRestoreUri != null) {
        AlertDialog(
            onDismissRequest = {
                showRestoreConfirmDialog = false
                pendingRestoreUri = null
            },
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
                        pendingRestoreUri?.let { viewModel.importBackup(context, it) }
                        showRestoreConfirmDialog = false
                        pendingRestoreUri = null
                    }
                ) {
                    Text(stringResource(R.string.common_restore))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showRestoreConfirmDialog = false
                        pendingRestoreUri = null
                    }
                ) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}

@Composable
fun ExportDialog(
    availableCards: List<String>,
    availableCategories: List<String>,
    currentFilter: ExportFilter,
    currentFormat: ExportFormat,
    isGoogleSignedIn: Boolean,
    onDismiss: () -> Unit,
    onFilterChange: (ExportFilter) -> Unit,
    onFormatChange: (ExportFormat) -> Unit,
    onExportLocal: () -> Unit,
    onExportGoogleDrive: () -> Unit,
    onSignInGoogle: () -> Unit
) {
    var selectedCards by remember { mutableStateOf(currentFilter.cardNames.toSet()) }
    var selectedCategories by remember { mutableStateOf(currentFilter.categories.toSet()) }
    var includeExpenses by remember { mutableStateOf(currentFilter.includeExpenses) }
    var includeIncomes by remember { mutableStateOf(currentFilter.includeIncomes) }
    var selectedFormat by remember { mutableStateOf(currentFormat) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.export_dialog_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 형식 선택
                Text(stringResource(R.string.export_format), style = MaterialTheme.typography.labelLarge)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedFormat == ExportFormat.JSON,
                        onClick = {
                            selectedFormat = ExportFormat.JSON
                            onFormatChange(ExportFormat.JSON)
                        },
                        label = { Text(stringResource(R.string.export_format_json)) },
                        leadingIcon = if (selectedFormat == ExportFormat.JSON) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        } else null
                    )
                    FilterChip(
                        selected = selectedFormat == ExportFormat.CSV,
                        onClick = {
                            selectedFormat = ExportFormat.CSV
                            onFormatChange(ExportFormat.CSV)
                        },
                        label = { Text(stringResource(R.string.export_format_csv)) },
                        leadingIcon = if (selectedFormat == ExportFormat.CSV) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        } else null
                    )
                }

                HorizontalDivider()

                // 데이터 유형
                Text(stringResource(R.string.export_data_type), style = MaterialTheme.typography.labelLarge)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = includeExpenses,
                        onClick = {
                            includeExpenses = !includeExpenses
                            onFilterChange(currentFilter.copy(includeExpenses = !includeExpenses))
                        },
                        label = { Text(stringResource(R.string.export_expense)) },
                        leadingIcon = if (includeExpenses) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        } else null
                    )
                    FilterChip(
                        selected = includeIncomes,
                        onClick = {
                            includeIncomes = !includeIncomes
                            onFilterChange(currentFilter.copy(includeIncomes = !includeIncomes))
                        },
                        label = { Text(stringResource(R.string.export_income)) },
                        leadingIcon = if (includeIncomes) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        } else null
                    )
                }

                // 카드 필터
                if (availableCards.isNotEmpty()) {
                    HorizontalDivider()
                    Text(stringResource(R.string.export_card_filter), style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        availableCards.forEach { card ->
                            FilterChip(
                                selected = card in selectedCards,
                                onClick = {
                                    selectedCards = if (card in selectedCards) {
                                        selectedCards - card
                                    } else {
                                        selectedCards + card
                                    }
                                    onFilterChange(currentFilter.copy(cardNames = selectedCards.toList()))
                                },
                                label = { Text(card, maxLines = 1) }
                            )
                        }
                    }
                }

                // 카테고리 필터
                if (availableCategories.isNotEmpty()) {
                    HorizontalDivider()
                    Text(stringResource(R.string.export_category_filter), style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        availableCategories.take(10).forEach { category ->
                            FilterChip(
                                selected = category in selectedCategories,
                                onClick = {
                                    selectedCategories = if (category in selectedCategories) {
                                        selectedCategories - category
                                    } else {
                                        selectedCategories + category
                                    }
                                    onFilterChange(currentFilter.copy(categories = selectedCategories.toList()))
                                },
                                label = { Text(category, maxLines = 1) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Column {
                // 로컬 저장
                Button(
                    onClick = onExportLocal,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Download, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.export_save_local))
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 구글 드라이브
                if (isGoogleSignedIn) {
                    OutlinedButton(
                        onClick = onExportGoogleDrive,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Cloud, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.export_save_google_drive))
                    }
                } else {
                    OutlinedButton(
                        onClick = onSignInGoogle,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Cloud, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.export_google_login))
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}

@Composable
fun GoogleDriveDialog(
    backupFiles: List<DriveBackupFile>,
    accountName: String?,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onRestore: (String) -> Unit,
    onDelete: (String) -> Unit,
    onSignOut: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.KOREA) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.google_drive_title))
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.common_refresh))
                }
            }
        },
        text = {
            Column {
                // 계정 정보
                accountName?.let {
                    Text(
                        text = stringResource(R.string.google_drive_account, it),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                if (backupFiles.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.google_drive_no_backup),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 300.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(backupFiles) { file ->
                            DriveBackupFileItem(
                                file = file,
                                dateFormat = dateFormat,
                                onRestore = { onRestore(file.id) },
                                onDelete = { onDelete(file.id) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_close))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onSignOut,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text(stringResource(R.string.google_drive_logout))
            }
        }
    )
}

@Composable
fun DriveBackupFileItem(
    file: DriveBackupFile,
    dateFormat: SimpleDateFormat,
    onRestore: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = dateFormat.format(Date(file.createdTime)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            Row {
                IconButton(onClick = onRestore) {
                    Icon(
                        Icons.Default.Restore,
                        contentDescription = stringResource(R.string.common_restore),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.common_delete),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    isDestructive: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
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
                imageVector = icon,
                contentDescription = null,
                tint = if (isDestructive) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                }
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isDestructive) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
                if (subtitle.isNotEmpty()) {
                    Text(
                        text = subtitle,
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

@Composable
fun IncomeSettingDialog(
    initialValue: Int = 0,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var incomeText by remember {
        mutableStateOf(if (initialValue > 0) initialValue.toString() else "")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_income_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.dialog_income_message),
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = incomeText,
                    onValueChange = { incomeText = it.filter { char -> char.isDigit() } },
                    label = { Text(stringResource(R.string.dialog_income_label)) },
                    suffix = { Text("원") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    incomeText.toIntOrNull()?.let { onConfirm(it) }
                },
                enabled = incomeText.isNotBlank()
            ) {
                Text(stringResource(R.string.common_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}

@Composable
fun ApiKeySettingDialog(
    currentKeyHint: String = "",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var apiKey by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_api_key_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.dialog_api_key_message_settings),
                    style = MaterialTheme.typography.bodySmall
                )
                if (currentKeyHint.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = currentKeyHint,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text(stringResource(R.string.dialog_api_key_label)) },
                    placeholder = { Text(stringResource(R.string.dialog_api_key_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(apiKey) },
                enabled = apiKey.isNotBlank()
            ) {
                Text(stringResource(R.string.common_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}

@Composable
fun MonthStartDayDialog(
    initialValue: Int = 1,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var dayText by remember {
        mutableStateOf(initialValue.toString())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_month_start_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.dialog_month_start_message),
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = dayText,
                    onValueChange = {
                        val filtered = it.filter { char -> char.isDigit() }
                        val number = filtered.toIntOrNull() ?: 0
                        if (number <= 31) {
                            dayText = filtered
                        }
                    },
                    label = { Text(stringResource(R.string.dialog_month_start_label)) },
                    suffix = { Text("일") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.dialog_month_start_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val day = dayText.toIntOrNull()?.coerceIn(1, 31) ?: 1
                    onConfirm(day)
                },
                enabled = dayText.isNotBlank()
            ) {
                Text(stringResource(R.string.common_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}
