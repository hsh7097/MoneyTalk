package com.sanha.moneytalk

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.sanha.moneytalk.core.ad.RewardAdManager
import com.sanha.moneytalk.core.firebase.AnalyticsEvent
import com.sanha.moneytalk.core.firebase.AnalyticsHelper
import com.sanha.moneytalk.core.ui.AppSnackbarBus
import com.sanha.moneytalk.core.util.DateUtils
import com.sanha.moneytalk.core.util.toDpTextUnit
import com.sanha.moneytalk.navigation.NavGraph
import com.sanha.moneytalk.navigation.Screen
import com.sanha.moneytalk.navigation.bottomNavItems

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

    SmsSyncProgressDialog(dialogUiState, mainViewModel::dismissSyncDialog)
    SmsEngineSummaryDialog(dialogUiState, mainViewModel::dismissEngineSummary)

    // 월별 SMS 동기화 크레딧 충전 다이얼로그
    if (dialogUiState.showFullSyncAdDialog) {
        val context = LocalContext.current
        val activity = context as? android.app.Activity
        val adYear = dialogUiState.fullSyncAdYear
        val adMonth = dialogUiState.fullSyncAdMonth
        val (effYear, effMonth) = DateUtils.getEffectiveCurrentMonth(dialogUiState.monthStartDay)
        val isCurrentMonth = adYear == effYear && adMonth == effMonth
        val currentMonthLabel = stringResource(R.string.home_current_month_sync_label)
        val syncMonthLabelFormat = stringResource(R.string.home_sync_month_label_format)
        val monthLabel = if (isCurrentMonth) {
            currentMonthLabel
        } else {
            String.format(syncMonthLabelFormat, adMonth)
        }
        val requiredCreditCost = RewardAdManager.MONTH_SYNC_CREDIT_COST
        val rewardCreditCount = mainViewModel.adManager.getRewardChatCount()
        AlertDialog(
            onDismissRequest = { mainViewModel.dismissFullSyncAdDialog() },
            title = { Text(stringResource(R.string.full_sync_ad_dialog_title, monthLabel)) },
            text = {
                Text(
                    stringResource(
                        R.string.full_sync_ad_dialog_message,
                        monthLabel,
                        requiredCreditCost,
                        rewardCreditCount
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (activity != null) {
                            onRequestSmsPermission {
                                mainViewModel.hideFullSyncAdDialogForRewardAd()
                                mainViewModel.adManager.showCreditAd(
                                    activity = activity,
                                    onRewarded = {
                                        mainViewModel.onFullSyncRewardAdWatched(adYear, adMonth)
                                    },
                                    onFailed = {
                                        mainViewModel.dismissFullSyncAdDialog()
                                    }
                                )
                            }
                        }
                    }
                ) {
                    Text(stringResource(R.string.full_sync_ad_watch_button, rewardCreditCount))
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
