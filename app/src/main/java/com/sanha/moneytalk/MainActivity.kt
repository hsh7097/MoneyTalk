package com.sanha.moneytalk

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.sanha.moneytalk.core.datastore.SettingsDataStore
import com.sanha.moneytalk.core.firebase.AnalyticsEvent
import com.sanha.moneytalk.core.firebase.AnalyticsHelper
import com.sanha.moneytalk.core.firebase.ForceUpdateChecker
import com.sanha.moneytalk.core.firebase.ForceUpdateState
import com.sanha.moneytalk.core.notification.SmsNotificationManager
import com.sanha.moneytalk.core.sms.SmsPipeline
import com.sanha.moneytalk.core.theme.MoneyTalkTheme
import com.sanha.moneytalk.core.theme.ThemeMode
import com.sanha.moneytalk.core.ui.AppSnackbarBus
import com.sanha.moneytalk.core.ui.ForceUpdateDialog
import com.sanha.moneytalk.core.util.DateUtils
import com.sanha.moneytalk.core.util.toDpTextUnit
import com.sanha.moneytalk.navigation.NavGraph
import com.sanha.moneytalk.navigation.Screen
import com.sanha.moneytalk.navigation.bottomNavItems
import androidx.activity.viewModels
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var snackbarBus: AppSnackbarBus
    @Inject
    lateinit var settingsDataStore: SettingsDataStore
    @Inject
    lateinit var forceUpdateChecker: ForceUpdateChecker
    @Inject
    lateinit var analyticsHelper: AnalyticsHelper
    @Inject
    lateinit var smsNotificationManager: SmsNotificationManager

    /** Activity-scoped MainViewModel (동기화/권한/광고 통합 관리) */
    private val mainViewModel: MainViewModel by viewModels()

    /** SMS 권한 요청 후 콜백 (권한 획득 시 호출) */
    private var pendingPermissionCallback: (() -> Unit)? = null

    private val smsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            Toast.makeText(this, getString(R.string.permission_sms_granted), Toast.LENGTH_SHORT).show()
            pendingPermissionCallback?.invoke()
        } else {
            Toast.makeText(this, getString(R.string.permission_sms_denied), Toast.LENGTH_LONG).show()
        }
        pendingPermissionCallback = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val themeModeStr by settingsDataStore.themeModeFlow.collectAsStateWithLifecycle(initialValue = "SYSTEM")
            val themeMode = try {
                ThemeMode.valueOf(themeModeStr)
            } catch (_: Exception) {
                ThemeMode.SYSTEM
            }

            MoneyTalkTheme(themeMode = themeMode) {
                // 강제 업데이트 체크 (앱 사용 중 RTDB 변경 시 실시간 대응)
                val forceUpdateState by forceUpdateChecker.forceUpdateRequired
                    .collectAsStateWithLifecycle(initialValue = ForceUpdateState.NotRequired)

                if (forceUpdateState is ForceUpdateState.Required) {
                    ForceUpdateDialog(
                        state = forceUpdateState as ForceUpdateState.Required,
                        onUpdate = {
                            val intent = Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://play.google.com/store/apps/details?id=$packageName")
                            )
                            startActivity(intent)
                        },
                        onExit = { finish() }
                    )
                }

                MoneyTalkApp(
                    onRequestSmsPermission = { onGranted ->
                        checkAndRequestSmsPermission(onGranted)
                    },
                    onExitApp = { finish() },
                    snackbarBus = snackbarBus,
                    analyticsHelper = analyticsHelper
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        smsNotificationManager.clearTransactionNotifications()
        mainViewModel.onAppResume()
    }

    private fun checkAndRequestSmsPermission(onGranted: () -> Unit) {
        val allGranted = SMS_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

        if (allGranted) {
            onGranted()
        } else {
            pendingPermissionCallback = onGranted
            smsPermissionLauncher.launch(SMS_PERMISSIONS)
        }
    }

    companion object {
        private val SMS_PERMISSIONS = arrayOf(
            Manifest.permission.READ_SMS,
            Manifest.permission.RECEIVE_SMS
        )
    }
}

/**
 * 앱 루트 Composable.
 * Scaffold + BottomNavigation + NavGraph + 전역 스낵바 + Activity 레벨 동기화 다이얼로그를 구성.
 *
 * MainViewModel(Activity-scoped)을 통해 SMS 동기화, 권한, 광고 상태를 관리하고,
 * 동기화 다이얼로그를 Activity 레벨에서 표시하여 탭 이동과 무관하게 진행 상태를 확인 가능.
 */
@Composable
fun MoneyTalkApp(
    onRequestSmsPermission: (onGranted: () -> Unit) -> Unit,
    onExitApp: () -> Unit,
    snackbarBus: AppSnackbarBus,
    analyticsHelper: AnalyticsHelper
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val density = LocalDensity.current
    val isImeVisible = WindowInsets.ime.getBottom(density) > 0

    val snackbarHostState = remember { SnackbarHostState() }

    // Activity-scoped MainViewModel (동기화/권한/광고 통합 관리)
    // ON_RESUME 라이프사이클은 MainActivity.onCreate()에서 직접 관리
    val mainViewModel: MainViewModel = hiltViewModel()
    val dialogUiState by mainViewModel.dialogUiState
        .collectAsStateWithLifecycle(initialValue = MainDialogUiState())

    // App-wide snackbar (toast-like): collect one-off events at the root
    LaunchedEffect(snackbarBus) {
        snackbarBus.events.collect { event ->
            snackbarHostState.showSnackbar(
                message = event.message,
                actionLabel = event.actionLabel,
                withDismissAction = event.withDismissAction,
                duration = event.duration
            )
        }
    }

    // 화면 전환 시 PV 트래킹
    LaunchedEffect(currentRoute) {
        val screenName = when {
            currentRoute == Screen.Home.route -> AnalyticsEvent.SCREEN_HOME
            currentRoute?.startsWith("history") == true -> AnalyticsEvent.SCREEN_HISTORY
            currentRoute == Screen.Chat.route -> AnalyticsEvent.SCREEN_CHAT
            currentRoute == Screen.Settings.route -> AnalyticsEvent.SCREEN_SETTINGS
            else -> null
        }
        screenName?.let { analyticsHelper.logScreenView(it) }
    }

    // 뒤로가기 처리
    BackPressHandler(
        navController = navController,
        currentRoute = currentRoute,
        onExitApp = onExitApp
    )

    // ===== Activity 레벨 다이얼로그 (탭 이동과 무관하게 표시) =====

    // SMS 동기화 진행 다이얼로그 (Stepper UI + 스킵 가능)
    if (dialogUiState.showSyncDialog) {
        AlertDialog(
            onDismissRequest = { mainViewModel.dismissSyncDialog() },
            properties = DialogProperties(dismissOnClickOutside = false),
            title = { Text(stringResource(R.string.home_sync_dialog_title)) },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SyncStepIndicator(
                        currentStep = dialogUiState.syncStepIndex,
                        totalSteps = SmsPipeline.TOTAL_STEPS
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    if (dialogUiState.syncProgressTotal > 0) {
                        val progress =
                            dialogUiState.syncProgressCurrent.toFloat() / dialogUiState.syncProgressTotal.toFloat()
                        LinearProgressIndicator(
                            progress = { progress.coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .padding(horizontal = 8.dp),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "${dialogUiState.syncProgressCurrent} / ${dialogUiState.syncProgressTotal}건",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    } else {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .padding(horizontal = 8.dp),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    Text(
                        text = dialogUiState.syncProgress,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = { },
            dismissButton = {
                TextButton(onClick = { mainViewModel.dismissSyncDialog() }) {
                    Text(stringResource(R.string.sync_dialog_dismiss))
                }
            }
        )
    }

    // AI 성과 요약 카드 (초기 동기화 완료 후)
    if (dialogUiState.showEngineSummary) {
        AlertDialog(
            onDismissRequest = { mainViewModel.dismissEngineSummary() },
            title = {
                Text(
                    text = stringResource(R.string.engine_summary_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (dialogUiState.engineSummaryTotalSms > 0) {
                        Text(
                            text = stringResource(R.string.engine_summary_sms_analyzed, dialogUiState.engineSummaryTotalSms),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                    if (dialogUiState.engineSummaryPatterns > 0) {
                        Text(
                            text = stringResource(R.string.engine_summary_patterns_learned, dialogUiState.engineSummaryPatterns),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                    val parts = mutableListOf<String>()
                    if (dialogUiState.engineSummaryExpenses > 0) {
                        parts.add(stringResource(R.string.engine_summary_expense_count, dialogUiState.engineSummaryExpenses))
                    }
                    if (dialogUiState.engineSummaryIncomes > 0) {
                        parts.add(stringResource(R.string.engine_summary_income_count, dialogUiState.engineSummaryIncomes))
                    }
                    if (parts.isNotEmpty()) {
                        Text(
                            text = stringResource(R.string.engine_summary_registered, parts.joinToString(" · ")),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }

                }
            },
            confirmButton = {
                TextButton(onClick = { mainViewModel.dismissEngineSummary() }) {
                    Text(stringResource(R.string.common_confirm))
                }
            }
        )
    }

    // 전체 동기화 해제 광고 다이얼로그
    if (dialogUiState.showFullSyncAdDialog) {
        val context = LocalContext.current
        val activity = context as? android.app.Activity
        val adYear = dialogUiState.fullSyncAdYear
        val adMonth = dialogUiState.fullSyncAdMonth
        val (effYear, effMonth) = DateUtils.getEffectiveCurrentMonth(dialogUiState.monthStartDay)
        val isCurrentMonth = adYear == effYear && adMonth == effMonth
        val monthLabel = if (isCurrentMonth) "이번달" else "${adMonth}월"
        AlertDialog(
            onDismissRequest = { mainViewModel.dismissFullSyncAdDialog() },
            title = { Text(stringResource(R.string.full_sync_ad_dialog_title, monthLabel)) },
            text = { Text(stringResource(R.string.full_sync_ad_dialog_message, monthLabel)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (activity != null) {
                            mainViewModel.dismissFullSyncAdDialog()
                            mainViewModel.adManager.showAd(
                                activity = activity,
                                onRewarded = {
                                    onRequestSmsPermission {
                                        mainViewModel.unlockFullSync(adYear, adMonth)
                                    }
                                },
                                onFailed = {
                                    onRequestSmsPermission {
                                        mainViewModel.unlockFullSync(adYear, adMonth)
                                    }
                                }
                            )
                        }
                    }
                ) {
                    Text(stringResource(R.string.full_sync_ad_watch_button, monthLabel))
                }
            },
            dismissButton = {
                TextButton(onClick = { mainViewModel.dismissFullSyncAdDialog() }) {
                    Text(stringResource(R.string.full_sync_ad_later))
                }
            }
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (!isImeVisible) {
                Column(
                    modifier = Modifier
                        .windowInsetsPadding(WindowInsets.navigationBars)
                ) {
                    HorizontalDivider(
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                    NavigationBar(
                        modifier = Modifier.height(64.dp),
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 0.dp,
                        windowInsets = WindowInsets(0)
                    ) {
                        bottomNavItems.forEach { item ->
                            val title = stringResource(item.titleRes)
                            val isSelected = currentRoute?.startsWith(item.route.substringBefore("?")) == true
                            NavigationBarItem(
                                selected = isSelected,
                                onClick = {
                                    if (!isSelected) {
                                        navController.navigate(item.route) {
                                            popUpTo(Screen.Home.route) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                        if (item.route == Screen.Home.route) {
                                            mainViewModel.homeTabReClickEvent.tryEmit(Unit)
                                        } else if (item.route.startsWith("history")) {
                                            mainViewModel.historyTabReClickEvent.tryEmit(Unit)
                                        }
                                    } else if (item.route == Screen.Home.route) {
                                        mainViewModel.homeTabReClickEvent.tryEmit(Unit)
                                    } else if (item.route.startsWith("history")) {
                                        mainViewModel.historyTabReClickEvent.tryEmit(Unit)
                                    }
                                },
                                icon = {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(top = 4.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Icon(
                                                imageVector = if (isSelected) {
                                                    item.selectedIcon
                                                } else {
                                                    item.unselectedIcon
                                                },
                                                contentDescription = title,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Text(
                                                text = title,
                                                fontSize = 12.dp.toDpTextUnit
                                            )
                                        }
                                    }
                                },
                                label = null,
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.onSurface,
                                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurface.copy(
                                        alpha = 0.5f
                                    ),
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurface.copy(
                                        alpha = 0.5f
                                    ),
                                    indicatorColor = MaterialTheme.colorScheme.surface
                                )
                            )
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            NavGraph(
                navController = navController,
                onRequestSmsPermission = onRequestSmsPermission,
                homeTabReClickEvent = mainViewModel.homeTabReClickEvent,
                historyTabReClickEvent = mainViewModel.historyTabReClickEvent
            )
        }
    }
}

/** 뒤로가기 핸들러. 채팅방 내부에서 뒤로가기 시 채팅방 목록으로 복귀 처리 */
@Composable
fun BackPressHandler(
    navController: NavHostController,
    currentRoute: String?,
    onExitApp: () -> Unit
) {
    val context = LocalContext.current
    var backPressedTime by remember { mutableStateOf(0L) }

    BackHandler {
        when (currentRoute) {
            Screen.Home.route -> {
                // 홈화면에서는 두 번 눌러 종료
                val currentTime = System.currentTimeMillis()
                if (currentTime - backPressedTime < 2000) {
                    onExitApp()
                } else {
                    backPressedTime = currentTime
                    Toast.makeText(context, context.getString(R.string.exit_confirm), Toast.LENGTH_SHORT).show()
                }
            }

            else -> {
                // 다른 화면에서는 홈으로 이동
                navController.navigate(Screen.Home.route) {
                    popUpTo(Screen.Home.route) {
                        inclusive = true
                    }
                    launchSingleTop = true
                }
            }
        }
    }
}

/** 동기화 진행 단계 인디케이터 (5단계 Stepper) */
@Composable
private fun SyncStepIndicator(
    currentStep: Int,
    totalSteps: Int,
    modifier: Modifier = Modifier
) {
    val stepLabels = stringArrayResource(R.array.sync_step_labels)

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until totalSteps) {
            val isCompleted = i < currentStep
            val isCurrent = i == currentStep
            val animatedDotSize by animateDpAsState(
                targetValue = if (isCurrent) 12.dp else 8.dp,
                label = "dotSize"
            )
            val dotColor = when {
                isCompleted || isCurrent -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.surfaceVariant
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(animatedDotSize)
                        .background(color = dotColor, shape = CircleShape)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stepLabels.getOrElse(i) { "" },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isCompleted || isCurrent) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    },
                    fontSize = 9.toDpTextUnit
                )
            }

            // 단계 사이 연결선
            if (i < totalSteps - 1) {
                Box(
                    modifier = Modifier
                        .width(16.dp)
                        .height(2.dp)
                        .background(
                            color = if (i < currentStep) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                        )
                )
            }
        }
    }
}
