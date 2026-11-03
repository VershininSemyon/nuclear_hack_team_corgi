package com.example.baseapp.ui

import android.Manifest
import android.content.pm.PackageManager
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.baseapp.audio.AudioFileInfo
import com.example.baseapp.audio.AudioFileStore
import com.example.baseapp.audio.WavRecorder
import com.example.baseapp.transcriber.TranscriberFactory
import com.example.baseapp.transcriber.TranscriptionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranscriptionScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val recorder = remember { WavRecorder(context.applicationContext) }
    val transcriber = remember { TranscriberFactory.create(context.applicationContext) }

    var selectedAudio by remember { mutableStateOf<AudioFileInfo?>(null) }
    var isRecording by remember { mutableStateOf(false) }
    var isStartingRecording by remember { mutableStateOf(false) }
    var recordingElapsedMs by remember { mutableStateOf(0L) }
    var isImporting by remember { mutableStateOf(false) }
    var isTranscribing by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var result by remember { mutableStateOf<TranscriptionResult?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            recorder.release()
            transcriber.release()
        }
    }

    LaunchedEffect(isRecording) {
        if (isRecording) {
            val startedAt = System.currentTimeMillis()
            while (isRecording) {
                recordingElapsedMs = System.currentTimeMillis() - startedAt
                delay(200)
            }
        } else {
            recordingElapsedMs = 0L
        }
    }

    fun startRecording() {
        if (isRecording || isStartingRecording || isTranscribing) return
        isStartingRecording = true
        scope.launch {
            try {
                errorMessage = null
                result = null
                recorder.start()
                isRecording = true
            } catch (t: Throwable) {
                errorMessage = t.message ?: "Не удалось начать запись"
            } finally {
                isStartingRecording = false
            }
        }
    }

    fun stopRecording() {
        if (!isRecording) return
        scope.launch {
            try {
                val file = recorder.stop()
                isRecording = false
                if (file != null) {
                    selectedAudio = AudioFileStore.describe(file)
                    result = null
                    errorMessage = null
                } else {
                    errorMessage = "Запись получилась пустой"
                }
            } catch (t: Throwable) {
                isRecording = false
                errorMessage = t.message ?: "Не удалось остановить запись"
            }
        }
    }

    val recordPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            startRecording()
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
        if (uri != null) {
            scope.launch {
                isImporting = true
                errorMessage = null
                try {
                    val info = withContext(Dispatchers.IO) {
                        AudioFileStore.importFromUri(context, uri)
                    }
                    selectedAudio = info
                    result = null
                } catch (t: Throwable) {
                    errorMessage = t.message ?: "Не удалось загрузить аудиофайл"
                } finally {
                    isImporting = false
                }
            }
        }
    }

    fun requestRecording() {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED

        if (granted) {
            startRecording()
        } else {
            recordPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    fun transcribe() {
        val audio = selectedAudio ?: return
        if (isTranscribing || isRecording) return

        isTranscribing = true
        progress = 0f
        errorMessage = null
        result = null

        scope.launch {
            try {
                val transcription = withContext(Dispatchers.Default) {
                    transcriber.transcribe(audio.path) { value ->
                        progress = value.coerceIn(0f, 1f)
                    }
                }
                result = transcription
                progress = 1f
            } catch (t: Throwable) {
                errorMessage = t.message ?: "Ошибка распознавания"
            } finally {
                isTranscribing = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Расшифровка диалога")
                        Text(
                            text = "Локально · без облака",
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
            item { DemoModeCard() }

            item {
                AudioSourceCard(
                    audio = selectedAudio,
                    isRecording = isRecording,
                    isStartingRecording = isStartingRecording,
                    recordingElapsedMs = recordingElapsedMs,
                    isImporting = isImporting,
                    isTranscribing = isTranscribing,
                    onPickFile = { openAudioLauncher.launch(arrayOf("audio/*")) },
                    onToggleRecording = {
                        if (isRecording) stopRecording() else requestRecording()
                    },
                    onClear = {
                        selectedAudio = null
                        result = null
                        errorMessage = null
                    },
                )
            }

            item {
                Button(
                    onClick = { transcribe() },
                    enabled = selectedAudio != null &&
                        !isTranscribing &&
                        !isRecording &&
                        !isStartingRecording &&
                        !isImporting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                ) {
                    Text(if (isTranscribing) "Распознавание..." else "Распознать")
                }
            }

            if (isTranscribing) {
                item { TranscriptionProgressCard(progress) }
            }

            errorMessage?.let { message ->
                item { ErrorCard(message) }
            }

            result?.let { transcription ->
                item { ResultSummaryCard(transcription) }
                item { FullTextCard(transcription.text) }

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

            if (result == null && !isTranscribing && errorMessage == null) {
                item { EmptyStateCard() }
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }
        }
    }
}
