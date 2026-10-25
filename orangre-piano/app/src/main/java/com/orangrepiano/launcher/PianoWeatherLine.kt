package com.orangrepiano.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A narrow weather ticker beneath the date. The attribution stays visible.
 * It's a data label, not a giant widget or an unsolicited forecast card.
 */
@Composable
internal fun PianoWeatherLine(
    weather: PianoWeather?,
    language: String,
    loading: Boolean,
    onClick: () -> Unit
) {
    val ru = language == "ru"
    Column(
        Modifier.width(175.dp)
            .padding(top = 7.dp)
            .background(
                Brush.horizontalGradient(listOf(Color(0x393C2926), Color.Transparent)),
                RoundedCornerShape(5.dp)
            )
            .clickable(onClick = onClick)
            .padding(start = 6.dp, top = 3.dp, bottom = 3.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                when {
                    weather != null -> weatherSymbol(weather.code)
                    loading -> "◌"
                    else -> "◇"
                },
                color = PianoTone.copper, fontSize = 12.sp
            )
            Spacer(Modifier.width(7.dp))
            Text(
                when {
                    weather != null -> "${weather.temperature}°  ·  ${weatherDescription(weather.code, ru)}"
                    loading -> if (ru) "Загрузка погоды" else "Loading weather"
                    else -> if (ru) "Включить погоду" else "Enable weather"
                },
                color = PianoTone.pearl, fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium, fontSize = 10.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        Text("OPEN-METEO", color = PianoTone.secondary.copy(alpha = .65f),
            fontFamily = FontFamily.Monospace, fontSize = 7.sp, letterSpacing = .5.sp)
    }
}
