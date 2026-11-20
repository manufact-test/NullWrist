package com.orangrepiano.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

private data class BatteryReading(val percent: Int = -1, val charging: Boolean = false)

private fun reading(intent: Intent?): BatteryReading {
    if (intent == null) return BatteryReading()
    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
    val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
    return BatteryReading(
        percent = if (level >= 0 && scale > 0) (level * 100 / scale).coerceIn(0, 100) else -1,
        charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
    )
}

/** Compact local battery readout. No polling, permission or background service. */
@Composable
internal fun PianoBatteryLine() {
    val context = LocalContext.current
    var battery by remember { mutableStateOf(BatteryReading()) }
    DisposableEffect(context) {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                battery = reading(intent)
            }
        }
        val sticky = ContextCompat.registerReceiver(
            context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED
        )
        if (sticky != null) battery = reading(sticky)
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }

    Row(
        Modifier.width(175.dp).padding(start = 6.dp, top = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            if (battery.charging) "↯ PWR" else "PWR",
            color = PianoTone.secondary,
            fontFamily = FontFamily.Monospace, fontSize = 9.sp,
            fontWeight = FontWeight.Medium, letterSpacing = 0.7.sp
        )
        Spacer(Modifier.width(9.dp))
        Box(
            Modifier.width(57.dp).height(3.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(PianoTone.copper.copy(alpha = .18f))
        ) {
            if (battery.percent >= 0) Box(
                Modifier.fillMaxWidth(battery.percent / 100f).fillMaxHeight()
                    .background(Brush.horizontalGradient(
                        listOf(PianoTone.copper, PianoTone.peach)
                    ))
            )
        }
        Spacer(Modifier.width(9.dp))
        Text(
            if (battery.percent >= 0) "${battery.percent}%" else "—%",
            color = if (battery.percent in 0..15) Color(0xFFFFBA97) else PianoTone.pearl,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium, fontSize = 10.sp
        )
    }
}
