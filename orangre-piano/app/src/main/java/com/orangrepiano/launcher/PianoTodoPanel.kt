package com.orangrepiano.launcher

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
    language: String,
    onEdit: (Int) -> Unit,
    onToggle: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        repeat(6) { index ->
            val todo = todos.getOrNull(index) ?: PianoTodo()
            val filled = todo.text.isNotBlank()
            Row(
                modifier = Modifier.fillMaxWidth().height(lineHeight)
                    .clickable(
                        onClickLabel = if (language == "ru") "Редактировать задачу" else "Edit task",
                        onClick = { onEdit(index) }
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier.size(14.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .then(if (filled) Modifier.clickable(
                            onClickLabel = if (language == "ru") "Отметить выполнение" else "Toggle done",
                            role = Role.Checkbox,
                            onClick = { onToggle(index) }
                        ) else Modifier)
                        .border(1.dp, PianoTone.copper.copy(alpha = if (todo.done) 1f else .55f),
                            RoundedCornerShape(3.dp))
                        .background(if (todo.done) PianoTone.copper.copy(alpha = .24f)
                            else Color.Transparent),
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
                            color = PianoTone.copper.copy(alpha = .80f))
                    }
                }
                Text(
                    text = if (filled) todo.text.replace('\n', ' ')
                        else if (language == "ru") "Задача" else "New task",
                    fontSize = if (compact) 12.sp else 14.sp,
                    fontWeight = if (filled) FontWeight.Medium else FontWeight.Normal,
                    color = when {
                        todo.done -> PianoTone.secondary.copy(alpha = .66f)
                        filled -> PianoTone.pearl
                        else -> PianoTone.secondary.copy(alpha = .60f)
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
