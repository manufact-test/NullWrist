package com.orangrepiano.launcher

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Full badge edition only. Reads notification metadata entirely on-device
 * and publishes package -> active notification count; no payloads are persisted.
 */
class PianoNotificationListener : NotificationListenerService() {
    override fun onListenerConnected() = refresh()
    override fun onNotificationPosted(sbn: StatusBarNotification?) = refresh()
    override fun onNotificationRemoved(sbn: StatusBarNotification?) = refresh()
    override fun onListenerDisconnected() { PianoNotificationCounts.clear() }

    private fun refresh() {
        try {
            val map = mutableMapOf<String, Int>()
            activeNotifications?.forEach { sbn ->
                val flags = sbn.notification.flags
                val ignore = Notification.FLAG_GROUP_SUMMARY or
                    Notification.FLAG_ONGOING_EVENT or Notification.FLAG_FOREGROUND_SERVICE
                if (sbn.isClearable && (flags and ignore) == 0) {
                    map[sbn.packageName] = (map[sbn.packageName] ?: 0) + 1
                }
            }
            PianoNotificationCounts.update(map)
        } catch (_: Exception) {
            PianoNotificationCounts.clear()
        }
    }
}
