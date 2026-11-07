
package com.example.baseapp.ui.editor

import com.example.baseapp.i18n.localizedSpeakerName
import com.example.baseapp.i18n.tr

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit

import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

import com.example.baseapp.data.SpeakerProfile
import com.example.baseapp.transcriber.TranscriptSegment
import com.example.baseapp.ui.formatTimestamp


@Composable
fun TranscriptFormattedText(
    segments: List<TranscriptSegment>,
    speakers: List<SpeakerProfile>,
    onSegmentTextChanged: (Int, String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val context = LocalContext.current

    val speakersById = speakers.associateBy { it.id }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        segments.forEachIndexed { index, segment ->

            val speaker = speakersById[segment.speaker]

            val speakerName = localizedSpeakerName(speaker?.name ?: segment.speaker)

            val speakerColor = speaker?.let {
                Color(it.color)
            } ?: MaterialTheme.colorScheme.primary

            TranscriptSegmentCard(
                segment = segment,
                speakerName = speakerName,
                speakerColor = speakerColor,
                enabled = enabled,
                onTextChanged = { newText ->
                    onSegmentTextChanged(index, newText)
                },
                onCopy = {
                    copySegment(
                        context = context,
                        text = segment.text,
                    )
                },
            )
        }
    }
}


@Composable
private fun TranscriptSegmentCard(
    segment: TranscriptSegment,
    speakerName: String,
    speakerColor: Color,
    enabled: Boolean,
    onTextChanged: (String) -> Unit,
    onCopy: () -> Unit,
) {
    var isEditing by remember(segment) {
        mutableStateOf(false)
    }

    var editedText by remember(segment) {
        mutableStateOf(segment.text)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
        ) {
            // Заголовок: спикер и таймкод.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = speakerName,
                    modifier = Modifier.weight(1f),
                    color = speakerColor,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )

                Text(
                    text = "${formatTimestamp(segment.startMs)} – " +
                        formatTimestamp(segment.endMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // В режиме редактирования показываем текстовое поле.
            if (isEditing) {
                OutlinedTextField(
                    value = editedText,
                    onValueChange = { editedText = it },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = enabled,
                    minLines = 2,
                    maxLines = 8,
                    shape = RoundedCornerShape(12.dp),
                    textStyle = MaterialTheme.typography.bodyLarge,
                    placeholder = {
                        Text(tr("Текст реплики", "Utterance text"))
                    },
                )
            } else {
                // Обычный режим — только отображение текста.
                Text(
                    text = segment.text,
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Кнопки редактирования и копирования.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = {
                        if (isEditing) {
                            onTextChanged(editedText)
                            isEditing = false
                        } else {
                            editedText = segment.text
                            isEditing = true
                        }
                    },
                    enabled = enabled,
                ) {
                    Icon(
                        imageVector = if (isEditing) {
                            Icons.Default.Check
                        } else {
                            Icons.Default.Edit
                        },
                        contentDescription = if (isEditing) {
                            tr("Сохранить изменения", "Save changes")
                        } else {
                            tr("Редактировать реплику", "Edit utterance")
                        },
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }

                IconButton(
                    onClick = onCopy,
                    enabled = segment.text.isNotEmpty(),
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = tr("Копировать реплику", "Copy utterance"),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}


private fun copySegment(
    context: Context,
    text: String,
) {
    val clipboard = context.getSystemService(
        Context.CLIPBOARD_SERVICE,
    ) as ClipboardManager

    clipboard.setPrimaryClip(
        ClipData.newPlainText(
            tr("Реплика", "Utterance"),
            text,
        ),
    )

    Toast.makeText(
        context,
        tr("Реплика скопирована", "Utterance copied"),
        Toast.LENGTH_SHORT,
    ).show()
}
