
package com.example.baseapp.ui.editor

import com.example.baseapp.i18n.tr

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.baseapp.appContainer
import com.example.baseapp.data.SpeakerColors
import com.example.baseapp.data.SpeakerProfile
import com.example.baseapp.data.TranscriptJsonExporter
import com.example.baseapp.ui.TimestampDisclaimer
import com.example.baseapp.ui.formatDateTime
import kotlinx.coroutines.launch
import androidx.compose.foundation.clickable

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TranscriptEditorScreen(
    onBack: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val container = context.appContainer
    val coroutineScope = rememberCoroutineScope()

    val viewModel: TranscriptEditorViewModel = viewModel(
        factory = TranscriptEditorViewModel.factory(
            repository = container.transcriptRepository,
            draftStore = container.draftStore,
        ),
    )

    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    var showExitDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showOriginalText by remember { mutableStateOf(false) }
    var showSpeakers by remember { mutableStateOf(false) }
    var customColorSpeaker by remember { mutableStateOf<SpeakerProfile?>(null) }
    var exitAfterSave by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                val result = runCatching {
                    val transcript = container.transcriptRepository.get(state.id)
                        ?: error(tr("Расшифровка не найдена", "Transcript not found"))

                    val json = TranscriptJsonExporter.export(transcript)

                    val output = context.contentResolver.openOutputStream(uri)
                        ?: error(tr("Не удалось открыть файл для записи", "Could not open the file for writing"))

                    output.use {
                        it.write(json.toByteArray(Charsets.UTF_8))
                    }
                }

                if (result.isSuccess) {
                    snackbarHostState.showSnackbar(tr("JSON-файл сохранён", "JSON file saved"))
                } else {
                    snackbarHostState.showSnackbar(
                        "Ошибка экспорта: ${
                            result.exceptionOrNull()?.localizedMessage
                                ?: tr("неизвестная ошибка", "unknown error")
                        }",
                    )
                }
            }
        }
    }

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                viewModel.persistDraft()
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.persistDraft()
        }
    }

    LaunchedEffect(state.message) {
        state.message?.let { message ->
            snackbarHostState.showSnackbar(message)

            if (exitAfterSave) {
                exitAfterSave = false
                onBack()
            }

            viewModel.dismissMessage()
        }
    }

    LaunchedEffect(state.deleted) {
        if (state.deleted) onBack()
    }

    BackHandler {
        if (state.hasUnsavedChanges) {
            showExitDialog = true
        } else {
            viewModel.persistDraft()
            onBack()
        }
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(snackbarHostState)
        },
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Заголовок строго по центру.
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = tr("Редактирование расшифровки", "Edit transcript"),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                Spacer(Modifier.height(10.dp))

                // Все управляющие кнопки в отдельном ряду.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        onClick = {
                            if (state.hasUnsavedChanges) {
                                showExitDialog = true
                            } else {
                                onBack()
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(tr("Назад", "Back"))
                    }

                    if (!state.isLoading && !state.notFound) {
                        OutlinedButton(
                            onClick = {
                                val fileName = state.title.replace(
                                    Regex("[^a-zA-Zа-яА-Я0-9_-]"),
                                    "_",
                                ).ifBlank {
                                    "transcript"
                                }

                                exportLauncher.launch("$fileName.json")
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("JSON")
                        }

                        OutlinedButton(
                            onClick = {
                                showDeleteDialog = true
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error,
                            ),
                        ) {
                            Text(tr("Удалить", "Delete"))
                        }
                    }
                }
            }
        },
    ) { innerPadding ->

        when {
            state.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            state.notFound -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(tr("Расшифровка не найдена", "Transcript not found"))

                    TextButton(onClick = onBack) {
                        Text(tr("Вернуться", "Go back"))
                    }
                }
            }

            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .imePadding(),
                ) {
                    if (state.draftRestored) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            ),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = tr("Восстановлены несохранённые изменения", "Unsaved changes restored"),
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodySmall,
                                )

                                TextButton(
                                    onClick = viewModel::cancelChanges,
                                ) {
                                    Text(tr("Сбросить", "Reset"))
                                }
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                    }

                    OutlinedTextField(
                        value = state.title,
                        onValueChange = viewModel::onTitleChanged,
                        modifier = Modifier.fillMaxWidth(),
                        label = {
                            Text(tr("Название", "Title"))
                        },
                        singleLine = true,
                        enabled = !state.isSaving,
                    )

                    Spacer(Modifier.height(6.dp))

                    Text(
                        text = "${state.audioFileName} · " +
                            "${state.audioFormat.displayName} · " +
                            tr("создано ${formatDateTime(state.createdAt)}", "created ${formatDateTime(state.createdAt)}"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Spacer(Modifier.height(10.dp))

                    // Кнопки исходного текста и спикеров.
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = {
                                showOriginalText = true
                            },
                            enabled = state.originalText.isNotEmpty(),
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(tr("Исходный текст", "Original text"))
                        }

                        OutlinedButton(
                            onClick = {
                                showSpeakers = true
                            },
                            enabled = state.speakers.isNotEmpty(),
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(tr("Спикеры (${state.speakers.size})", "Speakers (${state.speakers.size})"))
                        }
                    }

                    if (state.timestampsOutdated) {
                        Spacer(Modifier.height(8.dp))
                        TimestampDisclaimer()
                    }

                    Spacer(Modifier.height(14.dp))

                    Text(
                        text = tr("Транскрипция", "Transcription"),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )

                    Spacer(Modifier.height(10.dp))

                    // Основной список реплик.
                    // Каждая реплика — отдельная чат-карточка.
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        if (state.segments.isEmpty()) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                ),
                            ) {
                                Text(
                                    text = tr("Реплики отсутствуют", "No utterances"),
                                    modifier = Modifier.padding(16.dp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else {
                            TranscriptFormattedText(
                                segments = state.segments,
                                speakers = state.speakers,
                                enabled = !state.isSaving,
                                onSegmentTextChanged = { position, text ->
                                    viewModel.updateSegmentText(
                                        position,
                                        text,
                                    )
                                },
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = viewModel::cancelChanges,
                            enabled = state.hasUnsavedChanges && !state.isSaving,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(tr("Отменить", "Revert"))
                        }

                        Button(
                            onClick = viewModel::save,
                            enabled = state.hasUnsavedChanges && !state.isSaving,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                if (state.isSaving) {
                                    tr("Сохранение...", "Saving...")
                                } else {
                                    tr("Сохранить", "Save")
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    // Диалог выхода.
    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = {
                showExitDialog = false
            },
            title = {
                Text(tr("Несохранённые изменения", "Unsaved changes"))
            },
            text = {
                Text(
                    tr("Сохранить изменения перед выходом?", "Save changes before leaving?"),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExitDialog = false
                        exitAfterSave = true
                        viewModel.save()
                    },
                ) {
                    Text(tr("Сохранить", "Save"))
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            showExitDialog = false
                            viewModel.cancelChanges()
                            onBack()
                        },
                    ) {
                        Text(tr("Не сохранять", "Do not save"))
                    }

                    TextButton(
                        onClick = {
                            showExitDialog = false
                        },
                    ) {
                        Text(tr("Отмена", "Cancel"))
                    }
                }
            },
        )
    }

    // Удаление.
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = {
                showDeleteDialog = false
            },
            title = {
                Text(tr("Удалить расшифровку?", "Delete transcript?"))
            },
            text = {
                Text(
                    tr("Расшифровка будет удалена вместе с репликами, ", "The transcript will be deleted together with its utterances, ") +
                        tr("словами и таймкодами.", "words and timestamps."),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.delete()
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
                    onClick = {
                        showDeleteDialog = false
                    },
                ) {
                    Text(tr("Отмена", "Cancel"))
                }
            },
        )
    }

    // Исходный результат распознавания.
    if (showOriginalText) {
        AlertDialog(
            onDismissRequest = {
                showOriginalText = false
            },
            title = {
                Text(tr("Исходный результат ASR", "Original ASR result"))
            },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        text = tr("Первоначальный результат распознавания.", "The initial recognition result."),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Spacer(Modifier.height(8.dp))

                    Text(state.originalText)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showOriginalText = false
                    },
                ) {
                    Text(tr("Закрыть", "Close"))
                }
            },
        )
    }

    // Управление спикерами.
    if (showSpeakers) {
        AlertDialog(
            onDismissRequest = {
                showSpeakers = false
            },
            title = {
                Text(tr("Список спикеров", "Speakers"))
            },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 440.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    state.speakers.forEach { speaker ->
                        var editingName by remember(
                            speaker.id,
                            speaker.name,
                        ) {
                            mutableStateOf(speaker.name)
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = speaker.id,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )

                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .background(
                                            Color(speaker.color),
                                            CircleShape,
                                        ),
                                )
                            }

                            Spacer(Modifier.height(6.dp))

                            OutlinedTextField(
                                value = editingName,
                                onValueChange = {
                                    editingName = it
                                },
                                modifier = Modifier.fillMaxWidth(),
                                label = {
                                    Text(tr("Имя спикера", "Speaker name"))
                                },
                                singleLine = true,
                            )

                            Spacer(Modifier.height(8.dp))

                            Text(
                                tr("Цвет", "Color"),
                                style = MaterialTheme.typography.labelMedium,
                            )

                            Spacer(Modifier.height(6.dp))

                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                maxItemsInEachRow = 4,
                            ) {
                                SpeakerColors.palette.forEach { color ->
                                    val selected = speaker.color == color

                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .background(
                                                Color(color),
                                                CircleShape,
                                            )
                                            .then(
                                                if (selected) {
                                                    Modifier.border(
                                                        width = 3.dp,
                                                        color = MaterialTheme.colorScheme.onSurface,
                                                        shape = CircleShape,
                                                    )
                                                } else {
                                                    Modifier.border(
                                                        width = 1.dp,
                                                        color = MaterialTheme.colorScheme.outlineVariant,
                                                        shape = CircleShape,
                                                    )
                                                },
                                            )
                                            .clickable {
                                                viewModel.changeSpeakerColor(
                                                    speaker.id,
                                                    color,
                                                )
                                            },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        if (selected) {
                                            Box(
                                                modifier = Modifier
                                                    .size(10.dp)
                                                    .background(
                                                        MaterialTheme.colorScheme.onSurface,
                                                        CircleShape,
                                                    ),
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(Modifier.height(10.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        customColorSpeaker = speaker
                                    },
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .background(
                                            Brush.sweepGradient(
                                                listOf(
                                                    Color(0xFFFF0000),
                                                    Color(0xFFFFFF00),
                                                    Color(0xFF00FF00),
                                                    Color(0xFF00FFFF),
                                                    Color(0xFF0000FF),
                                                    Color(0xFFFF00FF),
                                                    Color(0xFFFF0000),
                                                ),
                                            ),
                                            CircleShape,
                                        )
                                        .border(
                                            width = 1.dp,
                                            color = MaterialTheme.colorScheme.outlineVariant,
                                            shape = CircleShape,
                                        ),
                                )

                                Spacer(Modifier.width(10.dp))

                                Text(
                                    text = tr("Свой цвет…", "Custom color…"),
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }

                            Spacer(Modifier.height(8.dp))

                            Button(
                                onClick = {
                                    viewModel.renameSpeaker(
                                        speaker.id,
                                        editingName,
                                    )
                                },
                                enabled = editingName.isNotBlank() &&
                                    editingName.trim() != speaker.name,
                            ) {
                                Text(tr("Сохранить имя", "Save name"))
                            }
                        }

                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSpeakers = false
                    },
                ) {
                    Text(tr("Готово", "Done"))
                }
            },
        )
    }

    customColorSpeaker?.let { speaker ->
        HsvColorPickerDialog(
            initialColor = speaker.color,
            onDismiss = { customColorSpeaker = null },
            onConfirm = { color ->
                viewModel.changeSpeakerColor(speaker.id, color)
                customColorSpeaker = null
            },
        )
    }
}
