package com.sanha.moneytalk.navigation

sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Home : Screen("home")
    object History : Screen("history?category={category}") {
        fun createRoute(category: String? = null): String {
            return if (category != null) "history?category=$category" else "history"
        }
    }
    object Chat : Screen("chat")
    object Settings : Screen("settings")
}
