
package com.example.baseapp.ui.editor

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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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

    val speakersById = remember(speakers) {
        speakers.associateBy { it.id }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        segments.forEachIndexed { index, segment ->
            val speaker = speakersById[segment.speaker]
            val speakerName = speaker?.name ?: segment.speaker

            val speakerColor = speaker?.let {
                Color(it.color)
            } ?: MaterialTheme.colorScheme.primary

            var showEditDialog by remember(segment.text) {
                mutableStateOf(false)
            }

            TranscriptSegmentCard(
                speakerName = speakerName,
                speakerColor = speakerColor,
                segment = segment,
                enabled = enabled,
                onCopy = {
                    copySegment(context, segment.text)
                },
                onEdit = {
                    showEditDialog = true
                },
            )

            if (showEditDialog) {
                var editedText by remember {
                    mutableStateOf(segment.text)
                }

                AlertDialog(
                    onDismissRequest = {
                        showEditDialog = false
                    },
                    title = {
                        Text("Редактирование реплики")
                    },
                    text = {
                        Column {
                            Text(
                                text = "${
                                    formatTimestamp(segment.startMs)
                                } – ${
                                    formatTimestamp(segment.endMs)
                                }",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )

                            Spacer(Modifier.height(10.dp))

                            OutlinedTextField(
                                value = editedText,
                                onValueChange = {
                                    editedText = it
                                },
                                modifier = Modifier.fillMaxWidth(),
                                label = {
                                    Text("Текст реплики")
                                },
                                minLines = 3,
                                maxLines = 8,
                                enabled = enabled,
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                onSegmentTextChanged(index, editedText)
                                showEditDialog = false
                            },
                            enabled = enabled,
                        ) {
                            Text("Применить")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                showEditDialog = false
                            },
                        ) {
                            Text("Отмена")
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun TranscriptSegmentCard(
    speakerName: String,
    speakerColor: Color,
    segment: TranscriptSegment,
    enabled: Boolean,
    onCopy: () -> Unit,
    onEdit: () -> Unit,
) {
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = speakerName,
                    color = speakerColor,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )

                Text(
                    text = "${formatTimestamp(segment.startMs)} – " +
                        formatTimestamp(segment.endMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(10.dp))

            Text(
                text = segment.text.ifBlank {
                    "Пустая реплика"
                },
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onCopy,
                    enabled = enabled,
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Копировать реплику",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                IconButton(
                    onClick = onEdit,
                    enabled = enabled,
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Редактировать реплику",
                        tint = MaterialTheme.colorScheme.primary,
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
            "Реплика",
            text,
        ),
    )

    Toast.makeText(
        context,
        "Реплика скопирована",
        Toast.LENGTH_SHORT,
    ).show()
}
