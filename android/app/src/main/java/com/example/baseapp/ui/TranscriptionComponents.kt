package com.example.baseapp.ui

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.baseapp.audio.AudioFileInfo
import com.example.baseapp.transcriber.TranscriptSegment
import com.example.baseapp.transcriber.TranscriptionResult
import com.example.baseapp.transcriber.WordTimestamp
import com.example.baseapp.transcriber.model.ModelStage
import com.example.baseapp.transcriber.model.ModelStatus
import java.util.Locale

@Composable
fun ModelStatusCard(
    status: ModelStatus,
    onInstall: () -> Unit,
    onPickArchive: () -> Unit,
    onRetry: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (status) {
                is ModelStatus.Installed -> MaterialTheme.colorScheme.secondaryContainer
                is ModelStatus.Error -> MaterialTheme.colorScheme.errorContainer
                is ModelStatus.Missing -> MaterialTheme.colorScheme.surfaceVariant
                is ModelStatus.Working -> MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "GigaAM v3",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(6.dp))

            when (status) {
                is ModelStatus.Installed -> {
                    Text(
                        text = tr("Готова", "Ready"),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                is ModelStatus.Missing -> {
                    Text(
                        text = tr("Скачайте модель (~160 МБ).", "Download the model (~160 MB)."),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onInstall) { Text(tr("Скачать", "Download")) }
                        OutlinedButton(onClick = onPickArchive) {
                            Text(tr("Из файла", "From file"))
                        }
                    }
                }

                is ModelStatus.Working -> {
                    Text(
                        text = when (status.stage) {
                            ModelStage.DOWNLOADING -> tr("Скачивание модели...", "Downloading model...")
                            ModelStage.EXTRACTING -> tr("Распаковка модели...", "Extracting model...")
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (status.progress == null || status.progress <= 0f) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    } else {
                        LinearProgressIndicator(
                            progress = { status.progress.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${(status.progress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }

                is ModelStatus.Error -> {
                    Text(
                        text = status.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onRetry) { Text(tr("Повторить", "Retry")) }
                        OutlinedButton(onClick = onPickArchive) {
                            Text(tr("Из файла", "From file"))
                        }
                    }
                }
            }
            }

            Spacer(Modifier.width(14.dp))

            StatusDot(
                ready = status is ModelStatus.Installed,
                working = status is ModelStatus.Working,
            )
        }
    }
}


@Composable
fun AudioSourceCard(
    audio: AudioFileInfo?,
    isRecording: Boolean,
    isStartingRecording: Boolean,
    recordingElapsedMs: Long,
    isImporting: Boolean,
    isTranscribing: Boolean,
    onPickFile: () -> Unit,
    onToggleRecording: () -> Unit,
    onClear: () -> Unit,
) {
    Card {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = tr("Аудио", "Audio"),
                    style = MaterialTheme.typography.titleMedium,
                )
                if (isRecording) {
                    Text(
                        text = "● ${formatDuration(recordingElapsedMs)}",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (audio == null) {
                Text(
                    text = tr("Загрузите аудиофайл или запишите разговор с микрофона. ", "Load an audio file or record a conversation. ") +
                        tr("Поддерживаются WAV, MP3 и MP4.", "WAV, MP3 and MP4 are supported."),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = audio.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = buildString {
                        append(audio.format.displayName)
                        append(" · ")
                        append(
                            if (audio.durationMs > 0) {
                                formatDuration(audio.durationMs)
                            } else {
                                tr("длительность неизвестна", "unknown duration")
                            },
                        )
                        append(" · ")
                        append(formatFileSize(audio.sizeBytes))
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(4.dp))
                TextButton(
                    onClick = onClear,
                    enabled = !isRecording && !isStartingRecording && !isTranscribing,
                ) {
                    Text(tr("Очистить выбор", "Clear selection"))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onPickFile,
                    modifier = Modifier.weight(1f),
                    enabled = !isRecording &&
                        !isStartingRecording &&
                        !isTranscribing &&
                        !isImporting,
                ) {
                    Text(if (isImporting) tr("Загрузка...", "Loading...") else tr("Загрузить", "Load"))
                }

                Button(
                    onClick = onToggleRecording,
                    modifier = Modifier.weight(1f),
                    enabled = !isStartingRecording && !isTranscribing && !isImporting,
                    colors = if (isRecording) {
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        )
                    } else {
                        ButtonDefaults.buttonColors()
                    },
                ) {
                    Text(
                        when {
                            isStartingRecording -> tr("Запуск...", "Starting...")
                            isRecording -> tr("Стоп", "Stop")
                            else -> tr("Записать", "Record")
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun TranscriptionProgressCard(progress: Float) {
    val safeProgress = progress.coerceIn(0f, 1f)
    Card {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = tr("Распознавание...", "Recognizing..."),
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { safeProgress },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${(safeProgress * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun ErrorCard(message: String) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.onErrorContainer,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
fun ResultSummaryCard(result: TranscriptionResult) {
    val speakers = (result.segments.map { it.speaker } +
        result.words.mapNotNull { it.speaker })
        .filter { it.isNotBlank() }
        .distinct()

    Card {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = tr("Результат", "Result"),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
            ) {
                StatItem(tr("Реплик", "Utterances"), result.segments.size.toString())
                StatItem(tr("Спикеров", "Speakers"), speakers.size.toString())
                StatItem(tr("Слов", "Words"), result.words.size.toString())
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun FullTextCard(text: String) {
    val context = LocalContext.current

    Card {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = tr("Полный текст", "Full text"),
                    style = MaterialTheme.typography.titleMedium,
                )
                TextButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
                            as ClipboardManager
                        clipboard.setPrimaryClip(
                            ClipData.newPlainText(tr("Расшифровка", "Transcript"), text),
                        )
                        Toast.makeText(context, tr("Текст скопирован", "Text copied"), Toast.LENGTH_SHORT).show()
                    },
                ) {
                    Text(tr("Копировать", "Copy"))
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            SelectionContainer {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(top = 4.dp),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
fun SegmentCard(segment: TranscriptSegment) {
    Card {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SpeakerBadge(segment.speaker)
                Text(
                    text = "${formatTimestamp(segment.startMs)} — " +
                        formatTimestamp(segment.endMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = segment.text,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun SpeakerBadge(speaker: String) {
    val scheme = MaterialTheme.colorScheme
    val palette = listOf(
        scheme.primaryContainer to scheme.onPrimaryContainer,
        scheme.secondaryContainer to scheme.onSecondaryContainer,
        scheme.tertiaryContainer to scheme.onTertiaryContainer,
    )
    val index = Math.floorMod(speaker.hashCode(), palette.size)
    val (container, content) = palette[index]

    Surface(
        color = container,
        contentColor = content,
        shape = RoundedCornerShape(50),
    ) {
        Text(
            text = speaker,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
fun WordTimestampRow(word: WordTimestamp) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = word.word,
                    style = MaterialTheme.typography.bodyMedium,
                )
                word.speaker?.let { speaker ->
                    Text(
                        text = speaker,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = "${formatTimestamp(word.startMs)} → " +
                    formatTimestamp(word.endMs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant,
        )
    }
}

@Composable
fun SaveTranscriptCard(
    isSaving: Boolean,
    savedTranscriptId: Long?,
    onSave: () -> Unit,
    onOpenEditor: (Long) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = if (savedTranscriptId == null) {
                    tr("Сохранить расшифровку", "Save transcript")
                } else {
                    tr("Расшифровка сохранена", "Transcript saved")
                },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = tr("Текст, слова, таймкоды и реплики будут сохранены ", "Text, words, timestamps and utterances will be saved ") +
                    tr("локально. Исходный результат ASR останется неизменным ", "locally. The original ASR result stays unchanged ") +
                    tr("после ручного редактирования.", "after manual editing."),
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(modifier = Modifier.height(10.dp))
            if (savedTranscriptId == null) {
                Button(
                    onClick = onSave,
                    enabled = !isSaving,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (isSaving) tr("Сохранение...", "Saving...") else tr("Сохранить расшифровку", "Save transcript"))
                }
            } else {
                Button(
                    onClick = { onOpenEditor(savedTranscriptId) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(tr("Открыть в редакторе", "Open in editor"))
                }
            }
        }
    }
}

@Composable
fun TimestampDisclaimer() {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        ),
    ) {
        Text(
            text = tr("Текст изменён вручную. Таймкоды ниже относятся к исходному ", "Text was edited manually. The timestamps below refer to the original ") +
                tr("результату распознавания и не гарантированно совпадают с ", "recognition result and may not match the ") +
                tr("исправленным текстом.", "edited text."),
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
        )
    }
}

@Composable
fun EmptyStateCard() {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = tr("Пока нет расшифровки", "No transcript yet"),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = tr("Загрузите аудиофайл или запишите разговор, ", "Load an audio file or record a conversation, ") +
                    tr("затем нажмите «Распознать».", "then tap Recognize."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%02d:%02d", minutes, seconds)
}

fun formatTimestamp(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val millis = ms % 1000
    return String.format(Locale.US, "%02d:%02d.%03d", minutes, seconds, millis)
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes >= 1024L * 1024L ->
            String.format(Locale.US, tr("%.1f МБ", "%.1f MB"), bytes / 1024.0 / 1024.0)

        bytes >= 1024L ->
            String.format(Locale.US, tr("%.1f КБ", "%.1f KB"), bytes / 1024.0)

        else -> tr("$bytes Б", "$bytes B")
    }
}
