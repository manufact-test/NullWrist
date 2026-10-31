package com.orangrepiano.launcher

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Six editable local notes on the same row baselines as app favorites. */
@Composable
internal fun PianoTodoPanel(
    todos: List<PianoTodo>,
    lineHeight: Dp,
    compact: Boolean,
    quiet: Boolean,
    language: String,
    onEdit: (Int) -> Unit,
    onToggle: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        repeat(6) { index ->
            val todo = todos.getOrNull(index) ?: PianoTodo()
            val filled = todo.text.isNotBlank()
            val shape = RoundedCornerShape(7.dp)
            val tileTint by animateColorAsState(
                targetValue = when {
                    todo.done -> Color(0x5B3A302C)
                    filled -> Color(0x96433530)
                    else -> Color(0x59382E2B)
                },
                animationSpec = tween(if (quiet) 1 else 240),
                label = "todo_ink_$index"
            )
            Box(Modifier.fillMaxWidth().height(lineHeight)) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(lineHeight - 3.dp)
                        .align(Alignment.Center)
                        .clip(shape)
                        .background(Brush.horizontalGradient(listOf(
                            tileTint, tileTint.copy(alpha = .51f)
                        )))
                        .border(.7.dp, PianoTone.border.copy(
                            alpha = if (filled && !todo.done) .48f else .25f), shape)
                        .clickable(
                            onClickLabel = if (language == "ru") "Редактировать задачу" else "Edit task",
                            onClick = { onEdit(index) }
                        )
                        .padding(start = 5.dp, end = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Delicate copper key-line and two-digit index echo the pixel clock.
                    Box(Modifier.size(width = 2.dp, height = 13.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(PianoTone.copper.copy(alpha = if (filled) .83f else .29f)))
                    Text(
                        (index + 1).toString().padStart(2, '0'),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 7.sp,
                        color = PianoTone.copper.copy(alpha = if (filled) .82f else .5f)
                    )
                    Box(
                        modifier = Modifier.size(15.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .then(if (filled) Modifier.clickable(
                                onClickLabel = if (language == "ru") "Отметить выполнение" else "Toggle done",
                                role = Role.Checkbox,
                                onClick = { onToggle(index) }
                            ) else Modifier)
                            .border(1.dp,
                                PianoTone.copper.copy(alpha = if (todo.done) 1f else .57f),
                                RoundedCornerShape(3.dp))
                            .background(if (todo.done) PianoTone.copper.copy(alpha = .34f)
                                else Color(0x2A110F0F)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (todo.done) {
                            Canvas(Modifier.size(10.dp)) {
                                drawLine(PianoTone.pearl,
                                    Offset(size.width * .15f, size.height * .50f),
                                    Offset(size.width * .40f, size.height * .75f),
                                    strokeWidth = 1.4.dp.toPx())
                                drawLine(PianoTone.pearl,
                                    Offset(size.width * .40f, size.height * .75f),
                                    Offset(size.width * .90f, size.height * .18f),
                                    strokeWidth = 1.4.dp.toPx())
                            }
                        } else if (!filled) {
                            Text("+", fontSize = 10.sp, lineHeight = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = PianoTone.peach.copy(alpha = .8f))
                        }
                    }
                    Text(
                        text = if (filled) todo.text.replace('\n', ' ')
                            else if (language == "ru") "Добавить" else "Add note",
                        fontSize = if (compact) 10.sp else 12.sp,
                        fontWeight = if (filled) FontWeight.Medium else FontWeight.Normal,
                        color = when {
                            todo.done -> PianoTone.secondary.copy(alpha = .74f)
                            filled -> PianoTone.pearl
                            else -> PianoTone.secondary.copy(alpha = .63f)
                        },
                        textDecoration = if (todo.done) TextDecoration.LineThrough else TextDecoration.None,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
