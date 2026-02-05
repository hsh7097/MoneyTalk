package com.sanha.moneytalk.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.sanha.moneytalk.feature.chat.ui.ChatScreen
import com.sanha.moneytalk.feature.history.ui.HistoryScreen
import com.sanha.moneytalk.feature.home.ui.HomeScreen
import com.sanha.moneytalk.feature.settings.ui.SettingsScreen
import com.sanha.moneytalk.feature.splash.ui.SplashScreen

@Composable
fun NavGraph(
    navController: NavHostController,
    onRequestSmsPermission: (onGranted: () -> Unit) -> Unit,
    autoSyncOnStart: Boolean = false,
    showSplash: Boolean = true
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
                autoSyncOnStart = autoSyncOnStart
            )
        }

        composable(Screen.History.route) {
            HistoryScreen()
        }

        composable(Screen.Chat.route) {
            ChatScreen()
        }

        composable(Screen.Settings.route) {
            SettingsScreen()
        }
    }
}
