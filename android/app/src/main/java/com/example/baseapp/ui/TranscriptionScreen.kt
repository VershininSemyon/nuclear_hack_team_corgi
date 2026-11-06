
package com.example.baseapp.ui

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.baseapp.appContainer
import com.example.baseapp.audio.AudioFormat
import com.example.baseapp.transcriber.model.ModelStatus
import com.example.baseapp.ui.transcription.TranscriptionViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranscriptionScreen(
    onOpenLibrary: () -> Unit,
    onOpenEditor: (Long) -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as Application
    val container = context.appContainer
    val viewModel: TranscriptionViewModel = viewModel(
        factory = TranscriptionViewModel.factory(app, container),
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        state.message?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.dismissMessage()
        }
    }
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    val recordPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            viewModel.startRecording()
        } else {
            Toast.makeText(
                context,
                "Нужно разрешение на запись звука",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    val openAudioLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let(viewModel::importAudio)
    }

    val openModelLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            viewModel.installModelFromUri(uri, queryDisplayName(context, uri))
        }
    }

    fun requestRecording() {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED

        if (granted) {
            viewModel.startRecording()
        } else {
            recordPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Расшифровка диалога")
                        Text(
                            text = "GigaAM v3 · локально · без облака",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                ModelStatusCard(
                    status = state.modelStatus,
                    onInstall = viewModel::installModel,
                    onPickArchive = {
                        openModelLauncher.launch(
                            arrayOf(
                                "application/x-bzip2",
                                "application/gzip",
                                "application/octet-stream",
                                "*/*",
                            ),
                        )
                    },
                    onRetry = viewModel::refreshModelStatus,
                )
            }

            item {
                AudioSourceCard(
                    audio = state.audio,
                    isRecording = state.isRecording,
                    isStartingRecording = state.isStartingRecording,
                    recordingElapsedMs = state.recordingElapsedMs,
                    isImporting = state.isImporting,
                    isTranscribing = state.isTranscribing,
                    onPickFile = {
                        openAudioLauncher.launch(AudioFormat.pickerMimeTypes)
                    },
                    onToggleRecording = {
                        if (state.isRecording) {
                            viewModel.stopRecording()
                        } else {
                            requestRecording()
                        }
                    },
                    onClear = viewModel::clearAudio,
                )
            }

            item {
                Button(
                    onClick = viewModel::transcribe,
                    enabled = state.canTranscribe,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                ) {
                    Text(
                        when {
                            state.isTranscribing -> "Распознавание..."
                            state.modelStatus !is ModelStatus.Installed -> {
                                "Сначала установите модель"
                            }

                            else -> "Распознать"
                        },
                    )
                }
            }

            if (state.isTranscribing) {
                item { TranscriptionProgressCard(state.progress) }
            }

            state.errorMessage?.let { message ->
                item { ErrorCard(message) }
            }

            state.result?.let { transcription ->
                item { ResultSummaryCard(transcription) }
                item { FullTextCard(transcription.text) }
                item {
                    SaveTranscriptCard(
                        isSaving = state.isSaving,
                        savedTranscriptId = state.savedTranscriptId,
                        onSave = {
                            viewModel.saveResult(
                                title = state.audio?.displayName
                                    ?.substringBeforeLast('.', "")
                                    .orEmpty(),
                            )
                        },
                        onOpenEditor = onOpenEditor,
                    )
                }

                if (transcription.segments.isNotEmpty()) {
                    item { SectionTitle("Реплики") }
                    itemsIndexed(transcription.segments) { _, segment ->
                        SegmentCard(segment = segment)
                    }
                }

                if (transcription.words.isNotEmpty()) {
                    item { SectionTitle("Слова и таймкоды") }
                    itemsIndexed(transcription.words) { _, word ->
                        WordTimestampRow(word)
                    }
                }
            }

            if (state.result == null && !state.isTranscribing && state.errorMessage == null) {
                item { EmptyStateCard() }
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }
        }
    }
}

private fun queryDisplayName(context: Context, uri: Uri): String? {
    return context.contentResolver.query(
        uri,
        arrayOf(OpenableColumns.DISPLAY_NAME),
        null,
        null,
        null,
    )?.use { cursor ->
        if (cursor.moveToFirst()) {
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0) cursor.getString(index) else null
        } else {
            null
        }
    }
}
