package com.sanha.moneytalk.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.sanha.moneytalk.feature.chat.ui.ChatScreen
import com.sanha.moneytalk.feature.history.ui.HistoryScreen
import com.sanha.moneytalk.feature.home.ui.HomeScreen
import com.sanha.moneytalk.feature.settings.ui.SettingsScreen
import com.sanha.moneytalk.feature.splash.ui.SplashScreen
import kotlinx.coroutines.flow.SharedFlow

/** 앱 전체 네비게이션 그래프. 스플래시, 홈, 내역, 채팅, 설정 화면 간 라우팅 정의 */
@Composable
fun NavGraph(
    navController: NavHostController,
    onRequestSmsPermission: (onGranted: () -> Unit) -> Unit,
    autoSyncOnStart: Boolean = false,
    onAutoSyncConsumed: () -> Unit = {},
    showSplash: Boolean = true,
    homeTabReClickEvent: SharedFlow<Unit>? = null,
    historyTabReClickEvent: SharedFlow<Unit>? = null
) {
    NavHost(
        navController = navController,
        startDestination = if (showSplash) Screen.Splash.route else Screen.Home.route
    ) {
        composable(Screen.Splash.route) {
            SplashScreen(
                onSplashFinished = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Home.route) {
            HomeScreen(
                onRequestSmsPermission = onRequestSmsPermission,
                autoSyncOnStart = autoSyncOnStart,
                onAutoSyncConsumed = onAutoSyncConsumed,
                homeTabReClickEvent = homeTabReClickEvent,
                onNavigateToHistory = { category ->
                    navController.navigate(Screen.History.createRoute(category)) {
                        popUpTo(Screen.Home.route) { saveState = true }
                        launchSingleTop = true
                        restoreState = false
                    }
                }
            )
        }

        composable(
            route = Screen.History.route,
            arguments = listOf(
                navArgument("category") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val category = backStackEntry.arguments?.getString("category")
            HistoryScreen(
                filterCategory = category,
                historyTabReClickEvent = historyTabReClickEvent
            )
        }

        composable(Screen.Chat.route) {
            ChatScreen()
        }

        composable(Screen.Settings.route) {
            SettingsScreen()
        }
    }
}
