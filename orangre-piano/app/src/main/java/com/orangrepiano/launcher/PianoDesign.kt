package com.orangrepiano.launcher

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.min

// ORANGRE / PIANO DESIGN SYSTEM — one warm material shared across all surfaces.
internal object PianoTone {
    val base = Color(0xFF110F0F)
    val ink = Color(0xFF171313)
    val copper = Color(0xFFDC936F)
    val peach = Color(0xFFF4C5A5)
    val pearl = Color(0xFFF9EBDD)
    val secondary = Color(0xFFBCA496)
    val glass = Color(0xC725201F)
    val border = Color(0x66F5CEB1)
    val highlight = Color(0x99FFF2E3)
}

@Composable
internal fun PianoAtmosphere(modifier: Modifier = Modifier, wallpaperPresent: Boolean = false) {
    Canvas(modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        if (!wallpaperPresent) {
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF0C0C0F), Color(0xFF261C1B), Color(0xFF131013)),
                    start = Offset(0f, 0f),
                    end = Offset(w, h)
                )
            )
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x663F241D), Color.Transparent),
                    center = Offset(w * 0.77f, h * 0.22f),
                    radius = w * .90f
                )
            )
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x332C202B), Color.Transparent),
                    center = Offset(w * .05f, h * .90f),
                    radius = w * .75f
                )
            )
        }
        val spacing = 26.dp.toPx()
        var x = spacing
        while (x < w) {
            drawLine(PianoTone.peach.copy(alpha = if (wallpaperPresent) .019f else .040f),
                Offset(x, 0f), Offset(x, h), strokeWidth = .55.dp.toPx())
            x += spacing
        }
        var y = spacing
        while (y < h) {
            drawLine(PianoTone.peach.copy(alpha = if (wallpaperPresent) .014f else .030f),
                Offset(0f, y), Offset(w, y), strokeWidth = .55.dp.toPx())
            y += spacing
        }
        // A tiny bespoke off-grid pixel constellation, not an 8-bit wallpaper pattern.
        repeat(18) { i ->
            val gx = ((i * 13 + 7) % 29) / 29f
            val gy = ((i * 19 + 11) % 37) / 37f
            val side = if (i % 7 == 0) 2.dp.toPx() else 1.dp.toPx()
            drawRect(
                color = PianoTone.peach.copy(alpha = if (wallpaperPresent) .055f else .13f),
                topLeft = Offset(w * gx, h * gy),
                size = Size(side, side)
            )
        }
    }
}

@Composable
internal fun PianoGlassSurface(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(19.dp)
    val fill = if (enabled) Brush.verticalGradient(
        listOf(Color(0xC4433431), Color(0xB6252022), Color(0xDF1C191B))
    ) else Brush.verticalGradient(
        listOf(Color(0xF21A1717), Color(0xF2141213))
    )
    val outline = Brush.linearGradient(
        colors = listOf(
            PianoTone.highlight.copy(alpha = if (enabled) .72f else .30f),
            PianoTone.copper.copy(alpha = .28f),
            Color(0x22FFFFFF),
            PianoTone.copper.copy(alpha = .50f)
        ),
        start = Offset.Zero,
        end = Offset(1300f, 900f)
    )
    // Draw glass sheen on the surface itself, never as a fillMaxSize child:
    // a measuring Canvas child would stretch wrap-content panels to screen height.
    Box(
        modifier = modifier
            .clip(shape)
            .background(fill)
            .drawBehind {
                val w = size.width
                drawLine(
                    brush = Brush.horizontalGradient(
                        listOf(Color.Transparent, PianoTone.highlight.copy(alpha = .51f), Color.Transparent)
                    ),
                    start = Offset(14.dp.toPx(), 1.5.dp.toPx()),
                    end = Offset(w - 14.dp.toPx(), 1.5.dp.toPx()),
                    strokeWidth = .85.dp.toPx()
                )
                drawRect(
                    brush = Brush.radialGradient(
                        listOf(Color(0x1AFFFFFF), Color.Transparent),
                        center = Offset(w * .04f, 0f),
                        radius = w * .65f
                    )
                )
            }
            .border(1.dp, outline, shape)
    ) {
        content()
    }
}

private val pixelFont = mapOf(
    '0' to listOf("01110","11011","11011","11011","11011","11011","01110"),
    '1' to listOf("00110","01110","00110","00110","00110","00110","01111"),
    '2' to listOf("11110","00011","00011","01110","11000","11000","11111"),
    '3' to listOf("11110","00011","00011","01110","00011","00011","11110"),
    '4' to listOf("11011","11011","11011","11111","00011","00011","00011"),
    '5' to listOf("11111","11000","11000","11110","00011","00011","11110"),
    '6' to listOf("01111","11000","11000","11110","11011","11011","01110"),
    '7' to listOf("11111","00011","00011","00110","00110","01100","01100"),
    '8' to listOf("01110","11011","11011","01110","11011","11011","01110"),
    '9' to listOf("01110","11011","11011","01111","00011","00011","11110"),
    ':' to listOf("000","010","010","000","010","010","000")
)

@Composable
internal fun PianoPixelClock(
    time: String,
    compact: Boolean,
    quiet: Boolean,
    description: String,
    onClick: () -> Unit
) {
    Crossfade(targetState = time, animationSpec = tween(if (quiet) 1 else 360), label = "pixel_minute") { shown ->
        Canvas(
            Modifier
                .width(if (compact) 169.dp else 183.dp)
                .height(if (compact) 50.dp else 57.dp)
                .clip(RoundedCornerShape(6.dp))
                .clickable(onClickLabel = description, role = Role.Button, onClick = onClick)
                .semantics { contentDescription = description }
        ) {
            val raw = shown.map { pixelFont[it] ?: pixelFont.getValue('0') }
            val columns = raw.sumOf { it[0].length } + (raw.size - 1) * 2
            val unit = min(size.width / (columns + .5f), size.height / 7.9f)
            val left = (size.width - columns * unit) / 2f
            val top = (size.height - 7 * unit) / 2f
            var column = 0
            raw.forEach { matrix ->
                matrix.forEachIndexed { row, line ->
                    line.forEachIndexed { col, c ->
                        if (c == '1') {
                            val origin = Offset(left + (column + col) * unit, top + row * unit)
                            val squareSize = Size(unit * .79f, unit * .79f)
                            drawRoundRect(
                                color = PianoTone.copper.copy(alpha = .13f),
                                topLeft = origin + Offset(0f, unit * .20f),
                                size = squareSize,
                                cornerRadius = CornerRadius(unit * .15f)
                            )
                            drawRoundRect(
                                brush = Brush.verticalGradient(
                                    colors = listOf(PianoTone.pearl, PianoTone.peach, PianoTone.copper),
                                    startY = top,
                                    endY = top + 7 * unit
                                ),
                                topLeft = origin,
                                size = squareSize,
                                cornerRadius = CornerRadius(unit * .12f)
                            )
                        }
                    }
                }
                column += matrix[0].length + 2
            }
        }
    }
}

@Composable
internal fun PianoAmbientPulse(enabled: Boolean, modifier: Modifier = Modifier) {
    if (!enabled) return
    val pulse = rememberInfiniteTransition(label = "copper_ambient")
    val opacity by pulse.animateFloat(
        initialValue = .24f, targetValue = .55f,
        animationSpec = infiniteRepeatable(tween(3500), RepeatMode.Reverse),
        label = "ambient_opacity"
    )
    Canvas(modifier) {
        val r = min(size.width, size.height) * .45f
        drawCircle(
            brush = Brush.radialGradient(
                listOf(PianoTone.copper.copy(alpha = opacity * .22f), Color.Transparent)
            ),
            radius = r,
            center = center
        )
    }
}
