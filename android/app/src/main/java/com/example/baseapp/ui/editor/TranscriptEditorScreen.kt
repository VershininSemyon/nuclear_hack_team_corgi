
package com.example.baseapp.ui.editor

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.baseapp.appContainer
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
                            (result.exceptionOrNull()?.localizedMessage
                                ?: "неизвестная ошибка"),
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Редактор расшифровки") },
                navigationIcon = {
                    TextButton(
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
                        TextButton(
                            onClick = {
                                val fileName = state.title
                                    .replace(
                                        Regex("[^a-zA-Zа-яА-Я0-9_-]"),
                                        "_",
                                    )
                                    .ifBlank { "transcript" }

                                exportLauncher.launch("$fileName.json")
                            },
                        ) {
                            Text("JSON")
                        }

                        TextButton(
                            onClick = { showDeleteDialog = true },
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
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "Восстановлены несохранённые изменения",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f),
                                )

                                TextButton(onClick = viewModel::cancelChanges) {
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
                        label = { Text("Название") },
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
                            onClick = { showOriginalText = true },
                            enabled = state.originalText.isNotEmpty(),
                        ) {
                            Text("Исходный текст")
                        }

                        OutlinedButton(
                            onClick = { showTimestamps = true },
                            enabled = state.segments.isNotEmpty(),
                        ) {
                            Text("Реплики (${state.segments.size})")
                        }
                    }

                    if (state.timestampsOutdated) {
                        Spacer(modifier = Modifier.height(8.dp))
                        TimestampDisclaimer()
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = state.text,
                        onValueChange = viewModel::onTextChanged,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        label = { Text("Текст расшифровки") },
                        enabled = !state.isSaving,
                        minLines = 10,
                        maxLines = Int.MAX_VALUE,
                    )

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
                                if (state.isSaving) "Сохранение..."
                                else "Сохранить",
                            )
                        }
                    }
                }
            }
        }
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("Несохранённые изменения") },
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
                        onClick = { showExitDialog = false },
                    ) {
                        Text("Отмена")
                    }
                }
            },
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Удалить расшифровку?") },
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
                    onClick = { showDeleteDialog = false },
                ) {
                    Text("Отмена")
                }
            },
        )
    }

    if (showOriginalText) {
        AlertDialog(
            onDismissRequest = { showOriginalText = false },
            title = { Text("Исходный результат ASR") },
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
                    onClick = { showOriginalText = false },
                ) {
                    Text("Закрыть")
                }
            },
        )
    }

    if (showTimestamps) {
        AlertDialog(
            onDismissRequest = { showTimestamps = false },
            title = { Text("Реплики и таймкоды") },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        text = "Таймкоды относятся к исходному результату " +
                            "распознавания.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    state.segments.forEach { segment ->
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
                                    text = segment.speaker,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f),
                                )

                                Text(
                                    text = "${formatTimestamp(segment.startMs)} → " +
                                        formatTimestamp(segment.endMs),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            SelectionContainer {
                                Text(
                                    text = segment.text,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
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
                    onClick = { showTimestamps = false },
                ) {
                    Text("Закрыть")
                }
            },
        )
    }
}
