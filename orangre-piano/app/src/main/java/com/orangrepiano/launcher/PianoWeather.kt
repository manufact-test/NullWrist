package com.orangrepiano.launcher

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume

internal data class PianoWeather(
    val temperature: Int,
    val code: Int,
    val updatedAtMillis: Long
)

internal fun weatherDescription(code: Int, ru: Boolean): String = when (code) {
    0 -> if (ru) "Ясно" else "Clear"
    1, 2 -> if (ru) "Облачно" else "Partly cloudy"
    3 -> if (ru) "Пасмурно" else "Overcast"
    45, 48 -> if (ru) "Туман" else "Fog"
    51, 53, 55, 56, 57 -> if (ru) "Морось" else "Drizzle"
    61, 63, 65, 66, 67 -> if (ru) "Дождь" else "Rain"
    71, 73, 75, 77, 85, 86 -> if (ru) "Снег" else "Snow"
    80, 81, 82 -> if (ru) "Ливень" else "Showers"
    95, 96, 99 -> if (ru) "Гроза" else "Thunder"
    else -> if (ru) "Погода" else "Weather"
}

internal fun weatherSymbol(code: Int): String = when (code) {
    0 -> "☀"
    1, 2 -> "◐"
    3, 45, 48 -> "☁"
    71, 73, 75, 77, 85, 86 -> "❄"
    95, 96, 99 -> "ϟ"
    else -> "☂"
}

internal class PianoWeatherRepository(private val context: Context) {
    private val cache = context.getSharedPreferences("piano_weather", Context.MODE_PRIVATE)

    fun cached(): PianoWeather? {
        if (!cache.contains("temperature")) return null
        return PianoWeather(
            cache.getInt("temperature", 0),
            cache.getInt("code", 0),
            cache.getLong("updated", 0)
        )
    }

    fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    suspend fun refresh(force: Boolean = false): PianoWeather? {
        val old = cached()
        if (!force && old != null &&
            System.currentTimeMillis() - old.updatedAtMillis < 45 * 60 * 1000) return old
        if (!hasLocationPermission()) return old
        return try {
            val location = withTimeoutOrNull(9_000L) { locate() } ?: return old
            val latest = withContext(Dispatchers.IO) {
                val lat = location.latitude
                val lon = location.longitude
                val url = URL("https://api.open-meteo.com/v1/forecast" +
                    "?latitude=$lat&longitude=$lon" +
                    "&current=temperature_2m,weather_code&timezone=auto")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 6000
                    readTimeout = 6000
                    requestMethod = "GET"
                    setRequestProperty("Accept", "application/json")
                }
                try {
                    if (conn.responseCode !in 200..299) return@withContext null
                    val payload = conn.inputStream.bufferedReader().use { it.readText() }
                    val current = JSONObject(payload).getJSONObject("current")
                    PianoWeather(
                        kotlin.math.round(current.getDouble("temperature_2m")).toInt(),
                        current.getInt("weather_code"),
                        System.currentTimeMillis()
                    )
                } finally { conn.disconnect() }
            } ?: return old
            cache.edit().putInt("temperature", latest.temperature)
                .putInt("code", latest.code).putLong("updated", latest.updatedAtMillis).apply()
            latest
        } catch (_: Exception) { old }
    }

    @Suppress("MissingPermission")
    private suspend fun locate(): Location? {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (!hasLocationPermission()) return null
        val last = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .mapNotNull { provider ->
                try { manager.getLastKnownLocation(provider) }
                catch (_: Exception) { null }
            }.maxByOrNull { it.time }
        if (last != null && System.currentTimeMillis() - last.time < 3 * 60 * 60 * 1000) return last
        if (Build.VERSION.SDK_INT >= 30) {
            val provider = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
                .firstOrNull { manager.isProviderEnabled(it) } ?: return last
            return suspendCancellableCoroutine { continuation ->
                val signal = android.os.CancellationSignal()
                continuation.invokeOnCancellation { signal.cancel() }
                try {
                    manager.getCurrentLocation(provider, signal, context.mainExecutor) { location ->
                        if (continuation.isActive) continuation.resume(location ?: last)
                    }
                } catch (_: Exception) {
                    if (continuation.isActive) continuation.resume(last)
                }
            }
        }
        return last
    }
}
