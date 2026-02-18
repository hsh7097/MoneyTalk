package com.sanha.moneytalk.core.firebase

/**
 * Firebase Analytics 이벤트/화면 이름 상수
 *
 * 네이밍 규칙:
 * - 화면명: snake_case (예: "home", "history")
 * - 클릭 이벤트: snake_case (예: "sync_sms", "send_chat")
 */
object AnalyticsEvent {

    // ── Screen names ────────────────────────────────────────
    const val SCREEN_HOME = "home"
    const val SCREEN_HISTORY = "history"
    const val SCREEN_CHAT = "chat"
    const val SCREEN_SETTINGS = "settings"

    // ── Click events ────────────────────────────────────────
    const val CLICK_SYNC_SMS = "sync_sms"
    const val CLICK_SEND_CHAT = "send_chat"
    const val CLICK_CATEGORY_FILTER = "category_filter"
    const val CLICK_BACKUP = "backup"
    const val CLICK_RESTORE = "restore"
    const val CLICK_THEME_CHANGE = "theme_change"
    const val CLICK_BOTTOM_NAV = "bottom_nav"
}
