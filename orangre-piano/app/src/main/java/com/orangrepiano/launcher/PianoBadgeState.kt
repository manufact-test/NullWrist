package com.orangrepiano.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Notification badge state, updated only by the user-authorized system listener. */
internal object PianoNotificationCounts {
    var counts by mutableStateOf<Map<String, Int>>(emptyMap())
        private set

    fun update(countsByPackage: Map<String, Int>) {
        counts = countsByPackage.filterValues { it > 0 }.toMap()
    }

    fun clear() { counts = emptyMap() }
}

internal fun pianoHasNotificationAccess(context: Context): Boolean {
    val allowed = Settings.Secure.getString(
        context.contentResolver, "enabled_notification_listeners"
    ) ?: return false
    val expected = ComponentName(context.packageName,
        "com.orangrepiano.launcher.PianoNotificationListener")
    return allowed.split(':').any { ComponentName.unflattenFromString(it) == expected }
}

internal fun pianoOpenNotificationAccess(context: Context) {
    val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try { context.startActivity(intent) }
    catch (_: Exception) {
        context.startActivity(Intent(Settings.ACTION_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
