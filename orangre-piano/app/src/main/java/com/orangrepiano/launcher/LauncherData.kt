package com.orangrepiano.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.util.Locale
import java.text.Collator

data class AppEntry(
    val label: String,
    val packageName: String,
    val className: String
) {
    val key: String get() = packageName + "/" + className
}

fun installedApps(context: Context): List<AppEntry> {
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val pm = context.packageManager
    val activities = if (android.os.Build.VERSION.SDK_INT >= 33) {
        pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
    } else {
        @Suppress("DEPRECATION")
        pm.queryIntentActivities(intent, 0)
    }
    val collator = Collator.getInstance(Locale.getDefault())
    return activities.asSequence()
        .filter { it.activityInfo?.packageName != context.packageName }
        .mapNotNull { info ->
            val activity = info.activityInfo ?: return@mapNotNull null
            AppEntry(info.loadLabel(pm).toString(), activity.packageName, activity.name)
        }
        .distinctBy { it.key }
        .sortedWith { a, b ->
            val result = collator.compare(a.label, b.label)
            if (result != 0) result else a.key.compareTo(b.key)
        }
        .toList()
}

fun openApp(context: Context, app: AppEntry) {
    try {
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setClassName(app.packageName, app.className)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        context.startActivity(intent)
    } catch (_: Exception) {
        // An app may have been removed or disabled since the list was loaded.
        android.widget.Toast.makeText(context, "Cannot open " + app.label, android.widget.Toast.LENGTH_SHORT).show()
    }
}

class LauncherPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("orangre_piano", Context.MODE_PRIVATE)
    val slots: Int get() = prefs.getInt("slots", 6).coerceIn(5, 6)
    val favorites: List<String> get() =
        (0 until slots).map { prefs.getString("fav_" + it, "") ?: "" }
    val glass: Boolean get() = prefs.getBoolean("glass", true)
    val quiet: Boolean get() = prefs.getBoolean("quiet", false)
    val hideFavoritesInDrawer: Boolean get() = prefs.getBoolean("hide_favorites_drawer", false)
    val language: String get() = prefs.getString("language", "en").let { if (it == "ru") "ru" else "en" }
    val hideStatusBar: Boolean get() = prefs.getBoolean("hide_status_bar", false)

    fun setFavorite(index: Int, key: String) {
        val current = favorites.toMutableList()
        if (index !in current.indices) return
        for (i in current.indices) if (i != index && current[i] == key) current[i] = ""
        current[index] = key
        val edit = prefs.edit()
        current.forEachIndexed { i, k -> edit.putString("fav_" + i, k) }
        edit.apply()
    }
    fun moveFavorite(from: Int, direction: Int) {
        val current = favorites.toMutableList()
        val target = from + direction
        if (from !in current.indices || target !in current.indices) return
        val tmp = current[from]
        current[from] = current[target]
        current[target] = tmp
        val edit = prefs.edit()
        current.forEachIndexed { i, k -> edit.putString("fav_" + i, k) }
        edit.apply()
    }
    fun setSlots(n: Int) { prefs.edit().putInt("slots", n.coerceIn(5, 6)).apply() }
    fun setGlass(v: Boolean) { prefs.edit().putBoolean("glass", v).apply() }
    fun setQuiet(v: Boolean) { prefs.edit().putBoolean("quiet", v).apply() }
    fun setHideFavorites(v: Boolean) { prefs.edit().putBoolean("hide_favorites_drawer", v).apply() }
    fun setLanguage(v: String) { prefs.edit().putString("language", if (v == "ru") "ru" else "en").apply() }
    fun setHideStatusBar(v: Boolean) { prefs.edit().putBoolean("hide_status_bar", v).apply() }
}

private fun decodeScaled(input: java.io.InputStream, sample: Int): Bitmap? =
    BitmapFactory.decodeStream(input, null, BitmapFactory.Options().apply { inSampleSize = sample })

fun importWallpaper(context: Context, uri: Uri): Bitmap? {
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / sample > 1600 || bounds.outHeight / sample > 1600) sample *= 2
        val bitmap = context.contentResolver.openInputStream(uri)?.use { decodeScaled(it, sample) }
            ?: return null
        File(context.filesDir, "piano-wallpaper.jpg").outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 88, out)
        }
        bitmap
    } catch (_: Exception) { null }
}

fun getWallpaper(context: Context): Bitmap? {
    val file = File(context.filesDir, "piano-wallpaper.jpg")
    return if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
}

fun removeWallpaper(context: Context) {
    File(context.filesDir, "piano-wallpaper.jpg").delete()
}


// Android intents rather than vendor-specific package names; compatible with alternate defaults.
fun openDefaultClock(context: Context, language: String) {
    val actions = listOfNotNull(
        context.packageManager.getLaunchIntentForPackage("com.android.deskclock"),
        context.packageManager.getLaunchIntentForPackage("com.google.android.deskclock"),
        Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS)
    )
    launchFirstActivity(context, actions, language, "Clock app not found", "Приложение часов не найдено")
}

fun openDefaultCalendar(context: Context, language: String) {
    val actions = listOf(
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALENDAR),
        Intent(Intent.ACTION_VIEW, android.provider.CalendarContract.CONTENT_URI)
    )
    launchFirstActivity(context, actions, language, "Calendar app not found", "Приложение календаря не найдено")
}

private fun launchFirstActivity(
    context: Context,
    actions: List<Intent>,
    language: String,
    enError: String,
    ruError: String
) {
    for (intent in actions) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (_: Exception) {
            // Missing or restricted activity: try the next standard Android intent.
        }
    }
    android.widget.Toast.makeText(
        context,
        if (language == "ru") ruError else enError,
        android.widget.Toast.LENGTH_SHORT
    ).show()
}

fun openDefaultCamera(context: Context, language: String) {
    launchFirstActivity(
        context,
        listOf(
            Intent(android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA),
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CAMERA)
        ),
        language,
        "Camera app not found",
        "Приложение камеры не найдено"
    )
}
