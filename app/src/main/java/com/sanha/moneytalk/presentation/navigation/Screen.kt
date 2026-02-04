package com.sanha.moneytalk.presentation.navigation

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object History : Screen("history")
    object Chat : Screen("chat")
    object Settings : Screen("settings")
}
