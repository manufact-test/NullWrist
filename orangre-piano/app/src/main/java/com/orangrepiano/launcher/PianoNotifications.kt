package com.orangrepiano.launcher

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Only aggregated counts leave the NotificationListenerService.
 * No titles, message bodies, contacts or notification content are stored.
 */
internal object PianoNotificationCounts {
    var counts by mutableStateOf<Map<String, Int>>(emptyMap())
        private set

    fun update(notifications: Array<StatusBarNotification>?) {
        val result = mutableMapOf<String, Int>()
        notifications?.forEach { sbn ->
            val flags = sbn.notification.flags
            val ignored = Notification.FLAG_GROUP_SUMMARY or
                Notification.FLAG_ONGOING_EVENT or Notification.FLAG_FOREGROUND_SERVICE
            if ((flags and ignored) == 0 && sbn.isClearable) {
                result[sbn.packageName] = (result[sbn.packageName] ?: 0) + 1
            }
        }
        counts = result.toMap()
    }

    fun clear() { counts = emptyMap() }
}

class PianoNotificationListener : NotificationListenerService() {
    override fun onListenerConnected() = refresh()
    override fun onNotificationPosted(sbn: StatusBarNotification?) = refresh()
    override fun onNotificationRemoved(sbn: StatusBarNotification?) = refresh()
    override fun onListenerDisconnected() { PianoNotificationCounts.clear() }

    private fun refresh() {
        try { PianoNotificationCounts.update(activeNotifications) }
        catch (_: Exception) { PianoNotificationCounts.clear() }
    }
}

internal fun hasNotificationAccess(context: Context): Boolean {
    val listeners = Settings.Secure.getString(
        context.contentResolver, "enabled_notification_listeners"
    ) ?: return false
    val expected = ComponentName(context, PianoNotificationListener::class.java)
    return listeners.split(':').any {
        ComponentName.unflattenFromString(it) == expected
    }
}

internal fun openNotificationAccessSettings(context: Context) {
    try {
        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {
        context.startActivity(Intent(Settings.ACTION_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
