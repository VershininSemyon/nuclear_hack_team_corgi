
package com.example.baseapp.ui.editor

import android.net.Uri
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.baseapp.appContainer
import com.example.baseapp.data.SpeakerColors
import com.example.baseapp.data.TranscriptJsonExporter
import com.example.baseapp.ui.TimestampDisclaimer
import com.example.baseapp.ui.formatDateTime
import com.example.baseapp.ui.formatTimestamp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranscriptEditorScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
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
    val lifecycleOwner = LocalLifecycleOwner.current

    var showExitDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showOriginalText by remember { mutableStateOf(false) }
    var showTimestamps by remember { mutableStateOf(false) }
    var showSpeakers by remember { mutableStateOf(false) }
    var editingSegmentPosition by remember { mutableStateOf<Int?>(null) }
    var exitAfterSave by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                val result = runCatching {
                    val transcript = container.transcriptRepository.get(state.id)
                        ?: error("Расшифровка не найдена")

                    val json = TranscriptJsonExporter.export(transcript)

                    val output = context.contentResolver.openOutputStream(uri)
                        ?: error("Не удалось открыть файл для записи")

                    output.use {
                        it.write(json.toByteArray(Charsets.UTF_8))
                    }
                }

                if (result.isSuccess) {
                    snackbarHostState.showSnackbar("JSON-файл сохранён")
                } else {
                    snackbarHostState.showSnackbar(
                        "Не удалось экспортировать JSON: " +
                            (
                                result.exceptionOrNull()?.localizedMessage
                                    ?: "неизвестная ошибка"
                                ),
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
            TopAppBar(
                title = {
                    Text("Редактор расшифровки")
                },
                navigationIcon = {
                    OutlinedButton(
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ),
                        onClick = {
                            if (state.hasUnsavedChanges) {
                                showExitDialog = true
                            } else {
                                onBack()
                            }
                        },
                    ) {
                        Text("Назад")
                    }
                },
                actions = {
                    if (!state.isLoading && !state.notFound) {
                        OutlinedButton(
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            ),
                            onClick = {
                                val fileName = state.title.replace(
                                    Regex("[^a-zA-Zа-яА-Я0-9_-]"),
                                    "_",
                                ).ifBlank {
                                    "transcript"
                                }

                                exportLauncher.launch("$fileName.json")
                            },
                        ) {
                            Text("JSON")
                        }

                        OutlinedButton(
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            ),
                            onClick = {
                                showDeleteDialog = true
                            },
                        ) {
                            Text(
                                "Удалить",
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        when {
            state.isLoading -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
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
                    Text(
                        text = "Расшифровка не найдена",
                        style = MaterialTheme.typography.titleMedium,
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    TextButton(onClick = onBack) {
                        Text("Вернуться")
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
                                    .padding(
                                        horizontal = 12.dp,
                                        vertical = 8.dp,
                                    ),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "Восстановлены несохранённые изменения",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f),
                                )

                                TextButton(
                                    onClick = viewModel::cancelChanges,
                                ) {
                                    Text("Сбросить")
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    OutlinedTextField(
                        value = state.title,
                        onValueChange = viewModel::onTitleChanged,
                        modifier = Modifier.fillMaxWidth(),
                        label = {
                            Text("Название")
                        },
                        singleLine = true,
                        enabled = !state.isSaving,
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "${state.audioFileName} · ${state.audioFormat.displayName} · " +
                            "создано ${formatDateTime(state.createdAt)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = {
                                showOriginalText = true
                            },
                            enabled = state.originalText.isNotEmpty(),
                        ) {
                            Text("Исходный текст")
                        }

                        OutlinedButton(
                            onClick = {
                                showTimestamps = true
                            },
                            enabled = state.segments.isNotEmpty(),
                        ) {
                            Text("Реплики (${state.segments.size})")
                        }

                        OutlinedButton(
                            onClick = {
                                showSpeakers = true
                            },
                            enabled = state.speakers.isNotEmpty(),
                        ) {
                            Text("Спикеры (${state.speakers.size})")
                        }
                    }

                    if (state.timestampsOutdated) {
                        Spacer(modifier = Modifier.height(8.dp))
                        TimestampDisclaimer()
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (state.segments.isNotEmpty()) {
                        Text(
                            text = "Транскрипция по репликам",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 220.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            ),
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState())
                                    .padding(12.dp),
                            ) {
                                TranscriptFormattedText(
                                    segments = state.segments,
                                    speakers = state.speakers,
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    // Нижнего поля редактирования текста больше нет.
                    // Текст меняется непосредственно в окне выбранной реплики.

                    Spacer(modifier = Modifier.weight(1f))

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = viewModel::cancelChanges,
                            enabled = state.hasUnsavedChanges && !state.isSaving,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Отменить")
                        }

                        Button(
                            onClick = viewModel::save,
                            enabled = state.hasUnsavedChanges && !state.isSaving,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                if (state.isSaving) {
                                    "Сохранение..."
                                } else {
                                    "Сохранить"
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    // Диалог выхода с несохранёнными изменениями.
    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = {
                showExitDialog = false
            },
            title = {
                Text("Несохранённые изменения")
            },
            text = {
                Text(
                    "Текст или название изменены, но ещё не сохранены. " +
                        "Сохранить изменения перед выходом?",
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
                    Text("Сохранить")
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
                        Text("Не сохранять")
                    }

                    TextButton(
                        onClick = {
                            showExitDialog = false
                        },
                    ) {
                        Text("Отмена")
                    }
                }
            },
        )
    }

    // Диалог удаления расшифровки.
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = {
                showDeleteDialog = false
            },
            title = {
                Text("Удалить расшифровку?")
            },
            text = {
                Text(
                    "Запись будет удалена вместе с исходным текстом, словами " +
                        "и таймкодами. Действие нельзя отменить.",
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
                        "Удалить",
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
                    Text("Отмена")
                }
            },
        )
    }

    // Диалог исходного результата распознавания.
    if (showOriginalText) {
        AlertDialog(
            onDismissRequest = {
                showOriginalText = false
            },
            title = {
                Text("Исходный результат ASR")
            },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        text = "Этот текст не изменяется при ручном редактировании.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    SelectionContainer {
                        Text(state.originalText)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showOriginalText = false
                    },
                ) {
                    Text("Закрыть")
                }
            },
        )
    }

    // Диалог реплик и таймкодов.
    if (showTimestamps) {
        AlertDialog(
            onDismissRequest = {
                showTimestamps = false
            },
            title = {
                Text("Реплики и таймкоды")
            },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        text = "Нажмите на текст реплики, чтобы изменить его. " +
                            "Таймкоды сохраняют исходные временные границы.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    state.segments.forEachIndexed { segmentIndex, segment ->
                        val speaker = state.speakers.firstOrNull {
                            it.id == segment.speaker
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = speaker?.name ?: segment.speaker,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f),
                                    color = speaker?.let {
                                        Color(it.color)
                                    } ?: MaterialTheme.colorScheme.onSurface,
                                )

                                Text(
                                    text = "${formatTimestamp(segment.startMs)} → " +
                                        formatTimestamp(segment.endMs),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = segment.text,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            editingSegmentPosition = segmentIndex
                                        }
                                        .padding(vertical = 4.dp),
                                )

                                TextButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(
                                            Context.CLIPBOARD_SERVICE,
                                        ) as ClipboardManager

                                        clipboard.setPrimaryClip(
                                            ClipData.newPlainText(
                                                "Реплика",
                                                segment.text,
                                            ),
                                        )

                                        Toast.makeText(
                                            context,
                                            "Реплика скопирована",
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    },
                                    modifier = Modifier.height(32.dp),
                                ) {
                                    Text(
                                        "⧉",
                                        style = MaterialTheme.typography.titleSmall,
                                    )
                                }
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
                        showTimestamps = false
                    },
                ) {
                    Text("Закрыть")
                }
            },
        )
    }

    // Отдельное окно редактирования выбранной реплики.
    editingSegmentPosition?.let { position ->
        val segment = state.segments.getOrNull(position)

        if (segment != null) {
            var editedText by remember(position, segment.text) {
                mutableStateOf(segment.text)
            }

            AlertDialog(
                onDismissRequest = {
                    editingSegmentPosition = null
                },
                title = {
                    Text("Редактирование реплики")
                },
                text = {
                    Column {
                        Text(
                            "${formatTimestamp(segment.startMs)} → " +
                                formatTimestamp(segment.endMs),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        Spacer(Modifier.height(8.dp))

                        OutlinedTextField(
                            value = editedText,
                            onValueChange = {
                                editedText = it
                            },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3,
                            maxLines = 8,
                            label = {
                                Text("Текст реплики")
                            },
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.updateSegmentText(
                                position,
                                editedText,
                            )
                            editingSegmentPosition = null
                        },
                    ) {
                        Text("Применить")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            editingSegmentPosition = null
                        },
                    ) {
                        Text("Отмена")
                    }
                },
            )
        }
    }

    // Диалог управления профилями спикеров.
    if (showSpeakers) {
        AlertDialog(
            onDismissRequest = {
                showSpeakers = false
            },
            title = {
                Text("Список спикеров")
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
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f),
                                )

                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .background(
                                            color = Color(speaker.color),
                                            shape = CircleShape,
                                        ),
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            OutlinedTextField(
                                value = editingName,
                                onValueChange = {
                                    editingName = it
                                },
                                modifier = Modifier.fillMaxWidth(),
                                label = {
                                    Text("Имя спикера")
                                },
                                singleLine = true,
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "Цвет",
                                style = MaterialTheme.typography.labelMedium,
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                SpeakerColors.palette.forEach { color ->
                                    val selected = speaker.color == color

                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .background(
                                                color = Color(color),
                                                shape = CircleShape,
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
                                                        color = MaterialTheme.colorScheme.outline,
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
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

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
                                Text("Сохранить имя")
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
                    Text("Готово")
                }
            },
        )
    }
}
