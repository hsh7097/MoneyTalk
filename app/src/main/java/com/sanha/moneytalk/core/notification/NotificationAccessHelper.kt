package com.sanha.moneytalk.core.notification

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings

object NotificationAccessHelper {

    private const val ENABLED_NOTIFICATION_LISTENERS = "enabled_notification_listeners"

    fun isNotificationListenerEnabled(
        context: Context,
        listenerServiceClass: Class<*>
    ): Boolean {
        val enabledListeners = Settings.Secure.getString(
            context.contentResolver,
            ENABLED_NOTIFICATION_LISTENERS
        ).orEmpty()
        if (enabledListeners.isBlank()) return false

        val targetComponent = ComponentName(context, listenerServiceClass).flattenToString()
        return enabledListeners
            .split(':')
            .any { it == targetComponent }
    }

    fun openNotificationListenerSettings(context: Context) {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        runCatching {
            context.startActivity(intent)
        }.onFailure {
            context.startActivity(
                Intent(Settings.ACTION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }
    }
}
