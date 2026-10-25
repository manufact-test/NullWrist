package com.orangrepiano.launcher

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
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
 * All privileges are optional. Only the dedicated badges build can request notification access.
 */
@Composable
internal fun PianoSetupDialog(
    language: String,
    badgesSupported: Boolean,
    notificationEnabled: Boolean,
    onNotification: () -> Unit,
    weatherEnabled: Boolean,
    cameraEnabled: Boolean,
    hideStatusBar: Boolean,
    onHome: () -> Unit,
    onLocation: () -> Unit,
    onCameraAccess: () -> Unit,
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
            Column(
                modifier = Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Text(t("Personalize your launcher. Every permission is optional.",
                    "Настройте лаунчер под себя. Все разрешения — по желанию."),
                    color = PianoTone.secondary, fontSize = 12.sp)
                Spacer(Modifier.height(2.dp))
                SetupStep("01", t("DEFAULT HOME", "ГЛАВНЫЙ ЭКРАН"),
                    t("Choose orangre piano as Home", "Выбрать orangre piano"), onHome)
                HorizontalDivider(color = PianoTone.border.copy(alpha = .18f))
                if (badgesSupported) {
                    SetupStep("•", t("NOTIFICATION BADGES", "СЧЁТЧИКИ УВЕДОМЛЕНИЙ"),
                        if (notificationEnabled) t("ACCESS ✓", "ДОСТУП ✓")
                        else t("Open permission settings", "Разрешить доступ"),
                        onNotification
                    )
                    Text(t(
                        "Android requires your consent. Direct browser downloads may be blocked by Play Protect.",
                        "Android запросит согласие. Play Защита может блокировать загрузки расширенной версии из браузера."
                    ), fontSize = 10.sp, color = PianoTone.secondary, lineHeight = 13.sp)
                    HorizontalDivider(color = PianoTone.border.copy(alpha = .18f))
                }
                SetupStep("02", t("LOCAL WEATHER", "ПОГОДА"),
                    if (weatherEnabled) t("LOCATION ✓", "ГЕОЛОКАЦИЯ ✓")
                    else t("Allow approximate location", "Разрешить примерную геолокацию"), onLocation)
                HorizontalDivider(color = PianoTone.border.copy(alpha = .18f))
                SetupStep("03", t("FLASHLIGHT", "ФОНАРИК"),
                    if (cameraEnabled) t("CAMERA ACCESS ✓", "ДОСТУП ✓")
                    else t("Allow camera access", "Разрешить доступ к камере"), onCameraAccess)
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
                    "Weather data: Open-Meteo. Approximate location is sent only for forecasts; notification counts, when enabled, stay on-device.",
                    "Источник погоды: Open-Meteo. Примерная геолокация нужна только для прогноза; счётчики уведомлений остаются на устройстве."
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
