
package com.example.baseapp.ui.library

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
                title = { Text("Аудиозапись") },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("Назад")
                    }
                },
                actions = {
                    TextButton(
                        onClick = { showDeleteDialog = true },
                        enabled = !isDeleting,
                    ) {
                        Text(
                            "Удалить",
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
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
                    Text("Аудиозапись не найдена")
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
                        label = { Text("Название") },
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

                    Button(
                        onClick = {
                            recognitionViewModel.recognize(
                                filePath = audio!!.filePath,
                                title = title.trim().ifBlank {
                                    File(audio!!.filePath).nameWithoutExtension
                                },
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isSaving &&
                            !isDeleting &&
                            !recognitionState.isTranscribing &&
                            !recognitionState.isSaving &&
                            !recognitionState.isCompleted &&
                            recognitionState.modelStatus is ModelStatus.Installed,
                    ) {
                        Text(
                            when {
                                recognitionState.isTranscribing -> "Распознавание..."
                                recognitionState.isSaving -> "Сохранение расшифровки..."
                                recognitionState.isCompleted -> "Расшифровка готова"
                                else -> "Распознать"
                            },
                        )
                    }

                    if (recognitionState.modelStatus !is ModelStatus.Installed) {
                        Text(
                            text = "Для распознавания установите модель GigaAM v3 " +
                                "на экране «Расшифровка диалога».",
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
                                        message = "Изменения сохранены"
                                    } else {
                                        errorMessage = "Аудиозапись не найдена"
                                    }
                                } catch (t: Throwable) {
                                    errorMessage = t.message
                                        ?: "Не удалось сохранить изменения"
                                } finally {
                                    isSaving = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isSaving && !isDeleting && title.isNotBlank(),
                    ) {
                        Text(if (isSaving) "Сохранение..." else "Сохранить изменения")
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
                        text = "Расшифровки (${transcripts.size})",
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
                                "Для этой аудиозаписи пока нет расшифровок",
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
            title = { Text("Удалить аудиозапись?") },
            text = {
                Text(
                    "Аудиозапись и все связанные с ней расшифровки " +
                        "будут удалены из локальной библиотеки. " +
                        "Действие нельзя отменить.",
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
                                    errorMessage = "Аудиозапись не найдена"
                                }
                            } catch (t: Throwable) {
                                isDeleting = false
                                errorMessage = t.message
                                    ?: "Не удалось удалить аудиозапись"
                            }
                        }
                    },
                ) {
                    Text(
                        "Удалить",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteDialog = false },
                ) {
                    Text("Отмена")
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
                text = transcript.snippet.ifBlank { "Текст отсутствует" },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(10.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "Спикеров: ${transcript.speakersCount}",
                    style = MaterialTheme.typography.labelSmall,
                )
                Text(
                    "Слов: ${transcript.wordsCount}",
                    style = MaterialTheme.typography.labelSmall,
                )
                Text(
                    "Фраз: ${transcript.phrasesCount}",
                    style = MaterialTheme.typography.labelSmall,
                )
            }

            if (transcript.isEdited) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Изменена вручную",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
