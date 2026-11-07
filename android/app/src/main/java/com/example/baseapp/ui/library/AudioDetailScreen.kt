
package com.example.baseapp.ui.library

import com.example.baseapp.i18n.tr

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.baseapp.appContainer
import com.example.baseapp.data.AudioTranscriptSummary
import com.example.baseapp.transcriber.model.ModelStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioDetailScreen(
    audioId: Long,
    onBack: () -> Unit,
    onOpenTranscript: (Long) -> Unit,
) {
    val context = LocalContext.current
    val container = context.appContainer
    val application = context.applicationContext as Application
    val scope = rememberCoroutineScope()

    val recognitionViewModel: AudioRecognitionViewModel = viewModel(
        key = "audio_recognition_$audioId",
        factory = AudioRecognitionViewModel.factory(application, container),
    )
    val recognitionState by recognitionViewModel.state.collectAsStateWithLifecycle()

    val audio by container.audioRepository
        .observe(audioId)
        .collectAsStateWithLifecycle(initialValue = null)

    val transcripts by container.transcriptRepository
        .observeForAudio(audioId)
        .collectAsStateWithLifecycle(initialValue = emptyList())

    var title by remember(audioId) { mutableStateOf("") }
    var initialized by remember(audioId) { mutableStateOf(false) }

    var isSaving by remember { mutableStateOf(false) }
    var isDeleting by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(audio) {
        if (audio != null && !initialized) {
            title = audio!!.title
            initialized = true
        }
    }

    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    OutlinedButton(
                        onClick = onBack,
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ),
                    ) {
                        Text(tr("Назад", "Back"))
                    }
                },
                actions = {
                    OutlinedButton(
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ),
                        onClick = { showDeleteDialog = true },
                        enabled = !isDeleting,
                    ) {
                        Text(
                            tr("Удалить", "Delete"),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }

                    Spacer(Modifier.width(10.dp))

                    Text(
                        text = tr("Аудиозапись", "Recording"),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(end = 12.dp),
                    )
                },
            )
        },
    ) { padding ->
        when {
            audio == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(tr("Аудиозапись не найдена", "Recording not found"))
                }
            }

            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(16.dp),
                ) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(tr("Название", "Title")) },
                        singleLine = true,
                        enabled = !isSaving && !isDeleting,
                    )

                    Spacer(Modifier.height(8.dp))

                    Text(
                        text = File(audio!!.filePath).name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Spacer(Modifier.height(12.dp))

                    // Кнопка распознавания.
                    Button(
                        onClick = {
                            recognitionViewModel.recognize(
                                audioId = audioId,
                                filePath = audio!!.filePath,
                                title = title.trim().ifBlank {
                                    File(audio!!.filePath).nameWithoutExtension
                                },
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = transcripts.isEmpty() &&
                            !recognitionState.isAlreadyTranscribed &&
                            !recognitionState.isCheckingExisting &&
                            !isSaving &&
                            !isDeleting &&
                            !recognitionState.isTranscribing &&
                            !recognitionState.isSaving &&
                            !recognitionState.isCompleted &&
                            recognitionState.modelStatus is ModelStatus.Installed,
                    ) {
                        Text(
                            when {
                                transcripts.isNotEmpty() ||
                                    recognitionState.isAlreadyTranscribed ->
                                    tr("Аудио уже расшифровано", "Audio already transcribed")

                                recognitionState.isCheckingExisting ->
                                    tr("Проверка расшифровки...", "Checking transcript...")

                                recognitionState.isTranscribing ->
                                    tr("Распознавание...", "Recognizing...")

                                recognitionState.isSaving ->
                                    tr("Сохранение расшифровки...", "Saving transcript...")

                                recognitionState.isCompleted ->
                                    tr("Расшифровка готова", "Transcript ready")

                                else -> tr("Распознать", "Recognize")
                            },
                        )
                    }

                    if (recognitionState.modelStatus !is ModelStatus.Installed) {
                        Text(
                            text = tr("Для распознавания установите модель GigaAM v3 ", "To recognize speech, install the GigaAM v3 model ") +
                                tr("на экране «Расшифровка диалога».", "on the Transcribe dialogue screen."),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }

                    if (recognitionState.isTranscribing) {
                        LinearProgressIndicator(
                            progress = { recognitionState.progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                        )
                    }

                    recognitionState.errorMessage?.let { error ->
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }

                    recognitionState.message?.let { info ->
                        Text(
                            text = info,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    Button(
                        onClick = {
                            scope.launch {
                                isSaving = true
                                errorMessage = null
                                message = null

                                try {
                                    val success = withContext(Dispatchers.IO) {
                                        container.audioRepository.updateDetails(
                                            id = audioId,
                                            title = title,
                                        )
                                    }

                                    if (success) {
                                        message = tr("Изменения сохранены", "Changes saved")
                                    } else {
                                        errorMessage = tr("Аудиозапись не найдена", "Recording not found")
                                    }
                                } catch (t: Throwable) {
                                    errorMessage = t.message
                                        ?: tr("Не удалось сохранить изменения", "Could not save changes")
                                } finally {
                                    isSaving = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isSaving &&
                            !isDeleting &&
                            title.isNotBlank(),
                    ) {
                        Text(
                            if (isSaving) {
                                tr("Сохранение...", "Saving...")
                            } else {
                                tr("Сохранить изменения", "Save changes")
                            },
                        )
                    }

                    message?.let {
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }

                    errorMessage?.let {
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }

                    Spacer(Modifier.height(20.dp))

                    Text(
                        text = tr("Расшифровки (${transcripts.size})", "Transcripts (${transcripts.size})"),
                        style = MaterialTheme.typography.titleLarge,
                    )

                    Spacer(Modifier.height(10.dp))

                    if (transcripts.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                tr("Для этой аудиозаписи пока нет расшифровок", "No transcripts for this recording yet"),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(
                                items = transcripts,
                                key = { it.id },
                            ) { transcript ->
                                AudioTranscriptItem(
                                    transcript = transcript,
                                    onClick = {
                                        onOpenTranscript(transcript.id)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(tr("Удалить аудиозапись?", "Delete recording?")) },
            text = {
                Text(
                    tr("Аудиозапись и все связанные с ней расшифровки ", "The recording and all related transcripts ") +
                        tr("будут удалены из локальной библиотеки. ", "will be deleted from the local library. ") +
                        tr("Действие нельзя отменить.", "This cannot be undone."),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        scope.launch {
                            isDeleting = true
                            errorMessage = null

                            try {
                                val success = withContext(Dispatchers.IO) {
                                    container.audioRepository.delete(audioId)
                                }

                                if (success) {
                                    onBack()
                                } else {
                                    isDeleting = false
                                    errorMessage = tr("Аудиозапись не найдена", "Recording not found")
                                }
                            } catch (t: Throwable) {
                                isDeleting = false
                                errorMessage = t.message
                                    ?: tr("Не удалось удалить аудиозапись", "Could not delete the recording")
                            }
                        }
                    },
                ) {
                    Text(
                        tr("Удалить", "Delete"),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteDialog = false },
                ) {
                    Text(tr("Отмена", "Cancel"))
                }
            },
        )
    }
}

@Composable
private fun AudioTranscriptItem(
    transcript: AudioTranscriptSummary,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Text(
                text = transcript.title,
                style = MaterialTheme.typography.titleMedium,
            )

            Spacer(Modifier.height(6.dp))

            Text(
                text = transcript.snippet.ifBlank { tr("Текст отсутствует", "No text") },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(10.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    tr("Спикеров: ${transcript.speakersCount}", "Speakers: ${transcript.speakersCount}"),
                    style = MaterialTheme.typography.labelSmall,
                )
                Text(
                    tr("Слов: ${transcript.wordsCount}", "Words: ${transcript.wordsCount}"),
                    style = MaterialTheme.typography.labelSmall,
                )
                Text(
                    tr("Фраз: ${transcript.phrasesCount}", "Phrases: ${transcript.phrasesCount}"),
                    style = MaterialTheme.typography.labelSmall,
                )
            }

            if (transcript.isEdited) {
                Spacer(Modifier.height(6.dp))
                Text(
                    tr("Изменена вручную", "Edited manually"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
