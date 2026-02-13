package com.sanha.moneytalk

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.sanha.moneytalk.core.datastore.SettingsDataStore
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
            Toast.makeText(this, "SMS 권한이 허용되었습니다", Toast.LENGTH_SHORT).show()
            // 앱 시작 시 권한 획득 후 자동 동기화 플래그 설정
            shouldAutoSync.value = true
            pendingSyncAction?.invoke()
        } else {
            Toast.makeText(this, "SMS 권한이 거부되었습니다. 설정에서 권한을 허용해주세요.", Toast.LENGTH_LONG).show()
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
                MoneyTalkApp(
                    permissionChecked = permissionChecked.value,
                    permissionGranted = permissionGranted.value,
                    shouldAutoSync = shouldAutoSync.value,
                    onAutoSyncConsumed = { shouldAutoSync.value = false },
                    onRequestSmsPermission = { onGranted ->
                        checkAndRequestSmsPermission(onGranted)
                    },
                    onExitApp = { finish() },
                    snackbarBus = snackbarBus
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

@Composable
fun MoneyTalkApp(
    permissionChecked: Boolean,
    permissionGranted: Boolean,
    shouldAutoSync: Boolean,
    onAutoSyncConsumed: () -> Unit,
    onRequestSmsPermission: (onGranted: () -> Unit) -> Unit,
    onExitApp: () -> Unit,
    snackbarBus: AppSnackbarBus
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // 스플래시 화면에서는 하단 네비게이션 숨김
    val showBottomBar = currentRoute != Screen.Splash.route

    val snackbarHostState = remember { SnackbarHostState() }

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
                Column {
                    HorizontalDivider(
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                    NavigationBar(
                        modifier = Modifier.height(64.dp),
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 0.dp
                    ) {
                        bottomNavItems.forEach { item ->
                            val isSelected = currentRoute == item.route
                            NavigationBarItem(
                                selected = isSelected,
                                onClick = {
                                    if (currentRoute != item.route) {
                                        navController.navigate(item.route) {
                                            popUpTo(Screen.Home.route) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
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
                                                contentDescription = item.title,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Text(
                                                text = item.title,
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
                onAutoSyncConsumed = onAutoSyncConsumed
            )
        }
    }
}

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
                    Toast.makeText(context, "한 번 더 누르면 종료됩니다", Toast.LENGTH_SHORT).show()
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
