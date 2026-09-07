package com.sanha.moneytalk.feature.settings.ui

import androidx.compose.runtime.DisposableEffect
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import com.sanha.moneytalk.R
import com.sanha.moneytalk.core.notification.NotificationAccessHelper
import com.sanha.moneytalk.core.ui.coachmark.CoachMarkOverlay
import com.sanha.moneytalk.core.ui.coachmark.CoachMarkState
import com.sanha.moneytalk.core.ui.coachmark.CoachMarkTargetRegistry
import com.sanha.moneytalk.core.ui.coachmark.onboardingTarget
import com.sanha.moneytalk.core.util.DataBackupManager
import com.sanha.moneytalk.feature.aicredit.ui.AiCreditActivity
import com.sanha.moneytalk.feature.categorysettings.ui.CategorySettingsActivity
import com.sanha.moneytalk.feature.settings.ui.coachmark.settingsCoachMarkSteps
import com.sanha.moneytalk.feature.smssettings.ui.SmsSettingsActivity
import com.sanha.moneytalk.feature.storerulesettings.ui.StoreRuleSettingsActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 설정 탭 메인 화면. 월 시작일, 예산, 카드 관리, 데이터 관리 등 앱 설정 항목을 표시 */
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

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
        if (uri != null) {
            viewModel.exportBackup(context, uri)
        } else {
            viewModel.clearBackupContent()
        }
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
        viewModel.refreshNotificationAccess(context)
    }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshNotificationAccess(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // 백업 콘텐츠가 준비된 뒤 실제 내보내기 동작을 시작한다.
    LaunchedEffect(uiState.backupContent) {
        uiState.backupContent?.let {
            if (uiState.activeDialog != SettingsDialog.EXPORT &&
                uiState.activeDialog != SettingsDialog.GOOGLE_DRIVE
            ) {
                if (isExportingToGoogleDrive) {
                    viewModel.exportToGoogleDrive()
                } else {
                    val fileName = DataBackupManager.generateBackupFileName(uiState.exportFormat)
                    backupLauncher.launch(fileName)
                }
            }
        }
    }

    // Google Drive export 완료 감지
    LaunchedEffect(uiState.isLoading, uiState.backupContent) {
        if (isExportingToGoogleDrive && !uiState.isLoading && uiState.backupContent == null) {
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

    // ===== 코치마크 (화면별 온보딩) =====
    val coachMarkRegistry = remember { CoachMarkTargetRegistry() }
    val coachMarkState = remember { CoachMarkState() }
    val allSettingsSteps = remember { settingsCoachMarkSteps() }
    val registeredTargetKeys = coachMarkRegistry.targets.keys.toSet()
    val coachMarkTargetItemIndices = remember(uiState.isCreditFeatureEnabled) {
        val creditSectionOffset = if (uiState.isCreditFeatureEnabled) 1 else 0
        mapOf(
            "settings_period" to 2,
            "settings_category" to 3 + creditSectionOffset,
            "settings_data" to 4 + creditSectionOffset
        )
    }
    val hasSeenSettingsOnboarding by viewModel.hasSeenScreenOnboardingFlow("settings")
        .collectAsStateWithLifecycle(initialValue = true)

    LaunchedEffect(hasSeenSettingsOnboarding, registeredTargetKeys) {
        if (!hasSeenSettingsOnboarding &&
            !coachMarkState.isVisible &&
            "settings_period" in registeredTargetKeys
        ) {
            delay(300)
            coachMarkState.show(allSettingsSteps)
        }
    }

    LaunchedEffect(coachMarkState.isVisible, coachMarkState.currentStep?.targetKey) {
        if (!coachMarkState.isVisible) return@LaunchedEffect

        val targetKey = coachMarkState.currentStep?.targetKey ?: return@LaunchedEffect
        if (targetKey !in registeredTargetKeys) {
            coachMarkTargetItemIndices[targetKey]?.let { itemIndex ->
                listState.animateScrollToItem(itemIndex)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            state = listState,
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
                SettingsDisplaySection(
                    uiState = uiState,
                    onIntent = viewModel::onIntent
                )
            }

            // 기간/예산 설정
            item {
                SettingsBudgetSection(
                    uiState = uiState,
                    onIntent = viewModel::onIntent,
                    modifier = Modifier.onboardingTarget("settings_period", coachMarkRegistry)
                )
            }

            // AI 크레딧
            if (uiState.isCreditFeatureEnabled) {
                item {
                    SettingsCreditSection(
                        balance = uiState.aiCreditBalance,
                        onOpenCredit = { AiCreditActivity.open(context) }
                    )
                }
            }

            // 카테고리 관리
            item {
                SettingsCategorySection(
                    uiState = uiState,
                    onIntent = viewModel::onIntent,
                    onOpenCategories = { CategorySettingsActivity.open(context) },
                    onOpenStoreRules = { StoreRuleSettingsActivity.open(context) },
                    modifier = Modifier.onboardingTarget("settings_category", coachMarkRegistry)
                )
            }

            // 데이터 관리
            item {
                SettingsDataSection(
                    uiState = uiState,
                    onIntent = viewModel::onIntent,
                    onOpenNotificationAccess = { NotificationAccessHelper.openNotificationListenerSettings(context) },
                    onOpenSmsSettings = { SmsSettingsActivity.open(context) },
                    onOpenGoogleDrive = {
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
                    },
                    modifier = Modifier.onboardingTarget("settings_data", coachMarkRegistry)
                )
            }

            // 앱 정보
            item {
                SettingsAppSection(
                    onIntent = viewModel::onIntent,
                    resetGuideModifier = Modifier.onboardingTarget("settings_reset", coachMarkRegistry)
                )
            }
        }

        SettingsLoadingOverlay(uiState)

        // 코치마크 오버레이
        CoachMarkOverlay(
            state = coachMarkState,
            targetRegistry = coachMarkRegistry,
            onComplete = { viewModel.markScreenOnboardingSeen("settings") }
        )
    }

    // ========== 다이얼로그 (activeDialog 기반) ==========

    SettingsDialogs(
        uiState = uiState,
        onIntent = viewModel::onIntent,
        onExportGoogleDrive = {
            isExportingToGoogleDrive = true
            viewModel.prepareBackup()
            viewModel.onIntent(SettingsIntent.DismissDialog)
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
