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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.sanha.moneytalk.core.datastore.SettingsDataStore
import com.sanha.moneytalk.core.firebase.AnalyticsEvent
import com.sanha.moneytalk.core.firebase.AnalyticsHelper
import com.sanha.moneytalk.core.firebase.ForceUpdateChecker
import com.sanha.moneytalk.core.firebase.ForceUpdateState
import com.sanha.moneytalk.core.theme.MoneyTalkTheme
import com.sanha.moneytalk.core.theme.ThemeMode
import com.sanha.moneytalk.core.ui.AppSnackbarBus
import com.sanha.moneytalk.core.util.toDpTextUnit
import com.sanha.moneytalk.navigation.NavGraph
import com.sanha.moneytalk.navigation.Screen
import com.sanha.moneytalk.navigation.bottomNavItems
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

    private var pendingSyncAction: (() -> Unit)? = null
    private var permissionChecked = mutableStateOf(false)
    private var permissionGranted = mutableStateOf(false)
    private var shouldAutoSync = mutableStateOf(false)

    private val smsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        permissionGranted.value = allGranted
        permissionChecked.value = true

        if (allGranted) {
            Toast.makeText(this, getString(R.string.permission_sms_granted), Toast.LENGTH_SHORT).show()
            // 앱 시작 시 권한 획득 후 자동 동기화 플래그 설정
            shouldAutoSync.value = true
            pendingSyncAction?.invoke()
        } else {
            Toast.makeText(this, getString(R.string.permission_sms_denied), Toast.LENGTH_LONG).show()
        }
        pendingSyncAction = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 앱 시작 시 권한 체크
        checkInitialPermissions()

        setContent {
            val themeModeStr by settingsDataStore.themeModeFlow.collectAsStateWithLifecycle(initialValue = "SYSTEM")
            val themeMode = try {
                ThemeMode.valueOf(themeModeStr)
            } catch (_: Exception) {
                ThemeMode.SYSTEM
            }

            MoneyTalkTheme(themeMode = themeMode) {
                // 강제 업데이트 체크
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
                    permissionChecked = permissionChecked.value,
                    permissionGranted = permissionGranted.value,
                    shouldAutoSync = shouldAutoSync.value,
                    onAutoSyncConsumed = { shouldAutoSync.value = false },
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

    private fun checkInitialPermissions() {
        val permissions = arrayOf(
            Manifest.permission.READ_SMS,
            Manifest.permission.RECEIVE_SMS
        )

        val allGranted = permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

        if (allGranted) {
            permissionGranted.value = true
            permissionChecked.value = true
            // 이미 권한이 있으면 자동 동기화 실행
            shouldAutoSync.value = true
        } else {
            // 앱 시작 시 권한 요청
            smsPermissionLauncher.launch(permissions)
        }
    }

    private fun checkAndRequestSmsPermission(onGranted: () -> Unit) {
        val permissions = arrayOf(
            Manifest.permission.READ_SMS,
            Manifest.permission.RECEIVE_SMS
        )

        val allGranted = permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

        if (allGranted) {
            onGranted()
        } else {
            pendingSyncAction = onGranted
            smsPermissionLauncher.launch(permissions)
        }
    }
}

/** 앱 루트 Composable. Scaffold + BottomNavigation + NavGraph + 전역 스낵바를 구성 */
@Composable
fun MoneyTalkApp(
    permissionChecked: Boolean,
    permissionGranted: Boolean,
    shouldAutoSync: Boolean,
    onAutoSyncConsumed: () -> Unit,
    onRequestSmsPermission: (onGranted: () -> Unit) -> Unit,
    onExitApp: () -> Unit,
    snackbarBus: AppSnackbarBus,
    analyticsHelper: AnalyticsHelper
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // 스플래시 화면에서는 하단 네비게이션 숨김
    val showBottomBar = currentRoute != Screen.Splash.route

    val snackbarHostState = remember { SnackbarHostState() }

    // 탭 재클릭 → 오늘 페이지로 이동 이벤트
    val homeTabReClickEvent = remember { kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 1) }
    val historyTabReClickEvent = remember { kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 1) }

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

    // 권한 체크 중일 때 로딩 표시
    if (!permissionChecked) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            // 권한 요청 중 로딩 화면 (선택적)
        }
        return
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (showBottomBar) {
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
                                        // 다른 탭에서 이동 → 네비게이션 + 오늘 페이지/필터 초기화
                                        navController.navigate(item.route) {
                                            popUpTo(Screen.Home.route) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                        // 탭 전환 시에도 오늘 페이지 + 필터 초기화
                                        if (item.route == Screen.Home.route) {
                                            homeTabReClickEvent.tryEmit(Unit)
                                        } else if (item.route.startsWith("history")) {
                                            historyTabReClickEvent.tryEmit(Unit)
                                        }
                                    } else if (item.route == Screen.Home.route) {
                                        // 홈 탭 재클릭 → 오늘 페이지로 이동
                                        homeTabReClickEvent.tryEmit(Unit)
                                    } else if (item.route.startsWith("history")) {
                                        // 내역 탭 재클릭 → 오늘 페이지로 이동 + 필터 초기화
                                        historyTabReClickEvent.tryEmit(Unit)
                                    }
                                },
                                icon = {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(top = 4.dp),
                                        contentAlignment = androidx.compose.ui.Alignment.Center
                                    ) {
                                        Column(
                                            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
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
                autoSyncOnStart = shouldAutoSync,
                onAutoSyncConsumed = onAutoSyncConsumed,
                homeTabReClickEvent = homeTabReClickEvent,
                historyTabReClickEvent = historyTabReClickEvent
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

    BackHandler(enabled = currentRoute != Screen.Splash.route) {
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

/** 강제 업데이트 다이얼로그. 닫기 불가 — 업데이트 또는 종료만 가능 */
@Composable
fun ForceUpdateDialog(
    state: ForceUpdateState.Required,
    onUpdate: () -> Unit,
    onExit: () -> Unit
) {
    val message = if (state.message.isNotBlank()) {
        state.message
    } else {
        stringResource(
            R.string.force_update_message,
            state.requiredVersion,
            state.currentVersion
        )
    }

    AlertDialog(
        onDismissRequest = { /* 닫기 차단 — 강제 업데이트 */ },
        title = {
            Text(
                text = stringResource(R.string.force_update_title),
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            TextButton(onClick = onUpdate) {
                Text(stringResource(R.string.force_update_button))
            }
        },
        dismissButton = {
            TextButton(onClick = onExit) {
                Text(stringResource(R.string.force_update_exit))
            }
        }
    )
}
