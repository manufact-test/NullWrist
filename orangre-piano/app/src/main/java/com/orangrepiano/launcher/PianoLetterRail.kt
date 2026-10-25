package com.orangrepiano.launcher

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

// Letter selection is a discrete tactile instrument: gentle, responsive, no global gesture interception.
@Composable
internal fun PianoLetterRail(
    letters: List<String>,
    selected: String?,
    quiet: Boolean,
    onLetter: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    val scrolling = rememberScrollState()
    val itemHeight = 23.dp
    val density = androidx.compose.ui.platform.LocalDensity.current
    val rowHeightPx = with(density) { itemHeight.toPx() }
    var lastTouched by remember(letters) { mutableStateOf<String?>(null) }

    fun choose(letter: String) {
        if (letter != lastTouched) {
            lastTouched = letter
            if (!quiet) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onLetter(letter)
        }
    }
    val gesture = Modifier.pointerInput(letters, scrolling.value) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            fun indexAt(y: Float): Int =
                ((y + scrolling.value) / rowHeightPx).toInt().coerceIn(0, (letters.size - 1).coerceAtLeast(0))
            if (letters.isNotEmpty()) choose(letters[indexAt(down.position.y)])
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val pointer = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!pointer.pressed) break
                if (letters.isNotEmpty()) choose(letters[indexAt(pointer.position.y)])
            } while (true)
            lastTouched = null
        }
    }

    Column(
        modifier.width(35.dp).fillMaxHeight()
            .then(gesture).verticalScroll(scrolling),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        letters.forEach { letter ->
            val active = selected == letter
            val scale by animateFloatAsState(
                targetValue = if (active) 1.22f else 1f,
                animationSpec = tween(if (quiet) 1 else 170), label = "letter_focus"
            )
            Box(
                modifier = Modifier.width(31.dp).height(itemHeight)
                    .padding(horizontal = 2.dp, vertical = 1.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (active) Color(0xAA754B3E) else Color.Transparent)
                    .border(
                        if (active) 1.dp else 0.dp,
                        if (active) PianoTone.copper.copy(alpha = .6f) else Color.Transparent,
                        RoundedCornerShape(6.dp)
                    )
                    .clickable { choose(letter); lastTouched = null },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = letter, fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                    color = if (active) PianoTone.pearl else PianoTone.secondary.copy(alpha = .78f),
                    modifier = Modifier.graphicsLayer(scaleX = scale, scaleY = scale)
                )
            }
        }
    }
}

@Composable
internal fun PianoLetterPreview(
    letter: String?,
    quiet: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = letter != null,
        enter = fadeIn(tween(if (quiet) 1 else 130)) +
            scaleIn(initialScale = .82f, animationSpec = tween(if (quiet) 1 else 200)),
        exit = fadeOut(tween(if (quiet) 1 else 290)) +
            scaleOut(targetScale = .88f, animationSpec = tween(if (quiet) 1 else 290)),
        modifier = modifier
    ) {
        PianoGlassSurface(modifier = Modifier.size(77.dp)) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    letter ?: "",
                    color = PianoTone.pearl, fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold, fontSize = 38.sp
                )
            }
        }
    }
}
