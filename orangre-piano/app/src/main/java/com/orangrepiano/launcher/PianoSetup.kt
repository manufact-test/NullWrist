package com.orangrepiano.launcher

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * First-launch opt-in walkthrough: system roles/settings cannot be auto-granted.
 * Nothing prevents continuing without notification or location access.
 */
@Composable
internal fun PianoSetupDialog(
    language: String,
    notificationEnabled: Boolean,
    weatherEnabled: Boolean,
    hideStatusBar: Boolean,
    onHome: () -> Unit,
    onNotification: () -> Unit,
    onLocation: () -> Unit,
    onHideBar: (Boolean) -> Unit,
    onDone: () -> Unit
) {
    val ru = language == "ru"
    fun t(en: String, russian: String): String = if (ru) russian else en
    AlertDialog(
        onDismissRequest = onDone,
        containerColor = androidx.compose.ui.graphics.Color(0xFF25201F),
        title = {
            Text(t("WELCOME TO PIANO", "ДОБРО ПОЖАЛОВАТЬ"),
                fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                color = PianoTone.peach, fontSize = 18.sp)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(t("Four small steps to make your launcher yours. Permissions are optional.",
                    "Четыре шага настройки лаунчера. Все разрешения — по желанию."),
                    color = PianoTone.secondary, fontSize = 12.sp)
                Spacer(Modifier.height(2.dp))
                SetupStep("01", t("DEFAULT HOME", "ГЛАВНЫЙ ЭКРАН"),
                    t("Choose orangre piano as Home", "Выбрать orangre piano"), onHome)
                HorizontalDivider(color = PianoTone.border.copy(alpha = .18f))
                SetupStep("02", t("NOTIFICATIONS", "УВЕДОМЛЕНИЯ"),
                    if (notificationEnabled) t("CONNECTED ✓", "ПОДКЛЮЧЕНО ✓")
                    else t("Enable app counters", "Включить счётчики"), onNotification)
                HorizontalDivider(color = PianoTone.border.copy(alpha = .18f))
                SetupStep("03", t("LOCAL WEATHER", "ПОГОДА"),
                    if (weatherEnabled) t("LOCATION ✓", "ГЕОЛОКАЦИЯ ✓")
                    else t("Allow approximate location", "Разрешить примерную геолокацию"), onLocation)
                HorizontalDivider(color = PianoTone.border.copy(alpha = .18f))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text("04  " + t("HIDE TOP BAR", "СКРЫТЬ ВЕРХНЮЮ СТРОКУ"),
                            fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                            color = PianoTone.pearl)
                        Text(t("The notification shade still works", "Шторка остаётся доступна"),
                            fontSize = 10.sp, color = PianoTone.secondary)
                    }
                    Switch(checked = hideStatusBar, onCheckedChange = onHideBar)
                }
                Text(t(
                    "Weather by Open-Meteo · location is sent only for the forecast. Notification counts are calculated on-device; no message content is saved.",
                    "Погода: Open-Meteo · местоположение отправляется только для прогноза. Счётчики вычисляются на устройстве, тексты уведомлений не сохраняются."
                ), fontSize = 10.sp, color = PianoTone.secondary, lineHeight = 14.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = onDone) {
                Text(t("START USING PIANO", "НАЧАТЬ"), color = PianoTone.peach)
            }
        }
    )
}

@Composable
private fun SetupStep(number: String, title: String, action: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween) {
        Column(Modifier.weight(1f)) {
            Text("$number  $title", fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                color = PianoTone.pearl)
        }
        TextButton(onClick = onClick) {
            Text(action, fontFamily = FontFamily.Monospace, fontSize = 10.sp,
                color = PianoTone.copper)
        }
    }
}
