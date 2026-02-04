package com.sanha.moneytalk.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.sanha.moneytalk.presentation.chat.ChatScreen
import com.sanha.moneytalk.presentation.history.HistoryScreen
import com.sanha.moneytalk.presentation.home.HomeScreen
import com.sanha.moneytalk.presentation.settings.SettingsScreen

@Composable
fun NavGraph(
    navController: NavHostController,
    onRequestSmsPermission: () -> Unit
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onRequestSmsPermission = onRequestSmsPermission
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
