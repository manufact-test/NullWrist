package com.orangrepiano.launcher

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal enum class PianoIcon { Back, Camera, Flashlight, Phone, Wallet }

@Composable
internal fun PianoSquareButton(
    icon: PianoIcon,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    edge: Dp = 40.dp
) {
    val shape = RoundedCornerShape(11.dp)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) .90f else 1f,
        animationSpec = tween(160),
        label = "square_press"
    )
    val glow by animateColorAsState(
        if (active) Color(0xFFB86D53) else Color(0xFF332623),
        animationSpec = tween(230),
        label = "square_glow"
    )
    val foreground = if (active) Color(0xFFF9EBDD) else Color(0xFFF4C6A6)
    Box(
        modifier = modifier.size(edge)
            .clip(shape)
            .background(Brush.verticalGradient(
                listOf(if (active) Color(0xFF9E614A) else Color(0x99564540), glow)
            ))
            .border(1.dp, if (active) Color(0xFFF6C8A4) else Color(0x88F4C6A6), shape)
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClickLabel = description,
                role = Role.Button,
                onClick = onClick
            )
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(22.dp)) {
            val w = size.width
            val h = size.height
            val stroke = 1.9.dp.toPx()
            fun segment(x1: Float, y1: Float, x2: Float, y2: Float) {
                drawLine(
                    color = foreground,
                    start = Offset(w * x1, h * y1),
                    end = Offset(w * x2, h * y2),
                    strokeWidth = stroke
                )
            }
            when (icon) {
                PianoIcon.Back -> {
                    segment(.67f, .17f, .32f, .50f)
                    segment(.32f, .50f, .67f, .83f)
                    segment(.33f, .50f, .83f, .50f)
                }
                PianoIcon.Camera -> {
                    drawRoundRect(
                        color = foreground,
                        topLeft = Offset(w * .1f, h * .29f),
                        size = Size(w * .8f, h * .58f),
                        cornerRadius = CornerRadius(2.dp.toPx()),
                        style = Stroke(stroke)
                    )
                    segment(.25f, .29f, .36f, .14f)
                    segment(.36f, .14f, .64f, .14f)
                    segment(.64f, .14f, .75f, .29f)
                    drawCircle(
                        color = foreground,
                        radius = w * .17f,
                        center = Offset(w * .5f, h * .57f),
                        style = Stroke(stroke)
                    )
                }
                PianoIcon.Phone -> {
                    // Classic receiver silhouette: outlined, balanced with the camera glyph.
                    val receiver = Path().apply {
                        moveTo(w * .20f, h * .16f)
                        cubicTo(w * .10f, h * .23f, w * .17f, h * .43f, w * .32f, h * .62f)
                        cubicTo(w * .48f, h * .82f, w * .70f, h * .92f, w * .80f, h * .78f)
                        lineTo(w * .68f, h * .63f)
                        lineTo(w * .57f, h * .70f)
                        cubicTo(w * .43f, h * .61f, w * .34f, h * .49f, w * .30f, h * .38f)
                        lineTo(w * .38f, h * .28f)
                        close()
                    }
                    drawPath(receiver, foreground, style = Stroke(stroke,
                        cap = androidx.compose.ui.graphics.StrokeCap.Round,
                        join = androidx.compose.ui.graphics.PathJoin.Round))
                }
                PianoIcon.Wallet -> {
                    drawRoundRect(
                        color = foreground,
                        topLeft = Offset(w * .13f, h * .25f),
                        size = Size(w * .74f, h * .61f),
                        cornerRadius = CornerRadius(3.dp.toPx()),
                        style = Stroke(stroke)
                    )
                    segment(.20f, .23f, .71f, .23f)
                    drawCircle(
                        color = foreground,
                        radius = w * .18f,
                        center = Offset(w * .50f, h * .55f),
                        style = Stroke(stroke)
                    )
                    segment(.50f, .47f, .50f, .64f)
                    segment(.45f, .50f, .55f, .50f)
                }
                PianoIcon.Flashlight -> {
                    val head = Path().apply {
                        moveTo(w * .26f, h * .10f)
                        lineTo(w * .74f, h * .10f)
                        lineTo(w * .78f, h * .35f)
                        lineTo(w * .62f, h * .49f)
                        lineTo(w * .62f, h * .86f)
                        lineTo(w * .38f, h * .86f)
                        lineTo(w * .38f, h * .49f)
                        lineTo(w * .22f, h * .35f)
                        close()
                    }
                    drawPath(head, color = foreground, style = Stroke(stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round))
                    segment(.26f, .34f, .74f, .34f)
                    if (active) {
                        segment(.14f, .07f, .04f, .02f)
                        segment(.86f, .07f, .96f, .02f)
                    }
                }
            }
        }
    }
}
