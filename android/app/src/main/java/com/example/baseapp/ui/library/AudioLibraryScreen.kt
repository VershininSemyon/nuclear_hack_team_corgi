
package com.example.baseapp.ui.library

import com.example.baseapp.i18n.tr

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.baseapp.appContainer
import com.example.baseapp.audio.AudioFormat
import com.example.baseapp.data.db.AudioEntity
import com.example.baseapp.ui.ModelStatusCard
import java.io.File
import kotlinx.coroutines.delay
import com.example.baseapp.ui.DiarizationModelCard
import com.example.baseapp.ui.LocalLlmModelCard
import com.example.baseapp.ui.LlmPromptCard
import com.example.baseapp.ui.LanguageSwitch
import com.example.baseapp.ui.theme.LocalDarkTheme
import com.example.baseapp.ui.theme.LocalToggleTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioLibraryScreen(
    onOpenAudioDetails: (Long) -> Unit,
) {
    val context = LocalContext.current
    val application = context.applicationContext as Application
    val container = context.appContainer

    val viewModel: AudioLibraryViewModel = viewModel(
        factory = AudioLibraryViewModel.factory(
            application = application,
            repository = container.audioRepository,
            modelManager = container.modelManager,
            diarizationModelManager = container.diarizationModelManager,
            localLlmModelManager = container.localLlmModelManager,
        ),
    )

    val audios by viewModel.audios.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()

    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var activeAudioId by remember { mutableStateOf<Long?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var positionMs by remember { mutableStateOf(0) }
    var durationMs by remember { mutableStateOf(0) }
    var playbackError by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            player?.release()
            player = null
        }
    }

    // Пока идёт воспроизведение, раз в четверть секунды обновляем позицию.
    LaunchedEffect(activeAudioId, isPlaying) {
        while (isPlaying) {
            val current = player
            if (current == null) break
            positionMs = runCatching { current.currentPosition }.getOrDefault(positionMs)
            durationMs = runCatching { current.duration }.getOrDefault(durationMs)
            delay(250)
        }
    }

    fun releasePlayer() {
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
        activeAudioId = null
        isPlaying = false
        positionMs = 0
        durationMs = 0
    }

    fun playFrom(audio: AudioEntity, startMs: Int) {
        try {
            releasePlayer()
            val file = File(audio.filePath)
            check(file.exists()) { tr("Файл аудио не найден", "Audio file not found") }

            val newPlayer = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setOnPreparedListener {
                    durationMs = it.duration.coerceAtLeast(0)
                    it.seekTo(startMs.coerceIn(0, durationMs))
                    it.start()
                    isPlaying = true
                    positionMs = startMs
                }
                setOnCompletionListener { completed ->
                    // По завершении убираем ленту промотки и освобождаем плеер.
                    isPlaying = false
                    positionMs = 0
                    durationMs = 0
                    activeAudioId = null
                    runCatching { completed.release() }
                    if (player === completed) player = null
                }
                setOnErrorListener { _, _, _ ->
                    isPlaying = false
                    playbackError = tr("Ошибка воспроизведения", "Playback error")
                    true
                }
            }
            player = newPlayer
            activeAudioId = audio.id
            newPlayer.prepareAsync()
        } catch (t: Throwable) {
            playbackError = t.message
                ?: tr("Не удалось воспроизвести файл", "Could not play the file")
            releasePlayer()
        }
    }

    fun togglePlayPause(audio: AudioEntity) {
        val current = player
        if (current == null || activeAudioId != audio.id) {
            playFrom(audio, 0)
            return
        }
        try {
            if (isPlaying) {
                current.pause()
                positionMs = current.currentPosition
                isPlaying = false
            } else {
                if (positionMs >= durationMs - 50) {
                    current.seekTo(0)
                    positionMs = 0
                }
                current.start()
                isPlaying = true
            }
        } catch (t: Throwable) {
            playbackError = t.message
                ?: tr("Ошибка воспроизведения", "Playback error")
        }
    }

    fun seekTo(ms: Int) {
        val current = player ?: return
        val target = ms.coerceIn(0, durationMs.coerceAtLeast(0))
        runCatching {
            current.seekTo(target)
            positionMs = target
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        uri?.let(viewModel::importAudio)
    }

    val modelArchiveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        uri?.let {
            viewModel.installModelFromUri(
                uri = it,
                fileName = queryDisplayName(context, it),
            )
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            viewModel.startRecording()
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
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(tr("Мои аудиозаписи", "My recordings"))
                },
                actions = {
                    val darkTheme = LocalDarkTheme.current
                    val toggleTheme = LocalToggleTheme.current

                    IconButton(onClick = toggleTheme) {
                        Icon(
                            imageVector = if (darkTheme) {
                                Icons.Filled.LightMode
                            } else {
                                Icons.Filled.DarkMode
                            },
                            contentDescription = if (darkTheme) {
                                tr("Светлая тема", "Light theme")
                            } else {
                                tr("Тёмная тема", "Dark theme")
                            },
                        )
                    }

                    LanguageSwitch()
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            ModelStatusCard(
                status = state.modelStatus,
                onInstall = viewModel::installModel,
                onPickArchive = {
                    modelArchiveLauncher.launch(
                        arrayOf(
                            "application/x-bzip2",
                            "application/octet-stream",
                            "*/*",
                        ),
                    )
                },
                onRetry = viewModel::refreshModelStatus,
            )

            Spacer(Modifier.height(12.dp))

            DiarizationModelCard(
                status = state.diarizationStatus,
                onInstall = viewModel::installDiarizationModels,
                onRetry = viewModel::refreshModelStatus,
            )

            Spacer(Modifier.height(12.dp))

            LocalLlmModelCard(
                status = state.localLlmStatus,
                onInstall = viewModel::installLocalLlm,
                onRetry = viewModel::refreshModelStatus,
            )

            Spacer(Modifier.height(12.dp))

            LlmPromptCard(
                prompt = state.llmPrompt,
                onSave = viewModel::saveLlmPrompt,
                onReset = viewModel::resetLlmPrompt,
            )

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = {
                        importLauncher.launch(AudioFormat.pickerMimeTypes)
                    },
                    enabled = !state.isImporting &&
                        !state.isRecording &&
                        !state.isStartingRecording,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (state.isImporting) tr("Импорт...", "Importing...") else tr("Импорт", "Import"))
                }

                Button(
                    onClick = {
                        if (state.isRecording) {
                            viewModel.stopRecording()
                        } else {
                            requestRecording()
                        }
                    },
                    enabled = !state.isImporting &&
                        !state.isStartingRecording,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        when {
                            state.isStartingRecording -> tr("Запуск...", "Starting...")
                            state.isRecording -> tr("Стоп", "Stop")
                            else -> tr("Запись", "Record")
                        },
                    )
                }
            }

            if (state.isRecording) {
                Text(
                    text = tr("Идёт запись...", "Recording..."),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            }

            state.errorMessage?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }

            state.message?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }

            Spacer(Modifier.height(16.dp))

            if (audios.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 56.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = tr("Пока пусто", "Nothing here yet"),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    audios.forEach { audio ->
                        AudioItem(
                            audio = audio,
                            isActive = activeAudioId == audio.id,
                            isPlaying = isPlaying && activeAudioId == audio.id,
                            positionMs = if (activeAudioId == audio.id) positionMs else 0,
                            durationMs = if (activeAudioId == audio.id) durationMs else 0,
                            onClick = {
                                onOpenAudioDetails(audio.id)
                            },
                            onPlayFromStart = {
                                playFrom(audio, 0)
                            },
                            onTogglePlayPause = {
                                togglePlayPause(audio)
                            },
                            onStop = {
                                releasePlayer()
                            },
                            onSeek = { position ->
                                seekTo(position)
                            },
                        )
                    }
                }
            }
        }
    }

    playbackError?.let { message ->
        AlertDialog(
            onDismissRequest = {
                playbackError = null
            },
            title = {
                Text(tr("Ошибка воспроизведения", "Playback error"))
            },
            text = {
                Text(message)
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        playbackError = null
                    },
                ) {
                    Text(tr("Закрыть", "Close"))
                }
            },
        )
    }
}

@Composable
private fun AudioItem(
    audio: AudioEntity,
    isActive: Boolean,
    isPlaying: Boolean,
    positionMs: Int,
    durationMs: Int,
    onClick: () -> Unit,
    onPlayFromStart: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onStop: () -> Unit,
    onSeek: (Int) -> Unit,
) {
    val extension = File(audio.filePath).extension.uppercase()
    val exists = File(audio.filePath).exists()

    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = audio.title,
                        style = MaterialTheme.typography.titleMedium,
                    )

                    Spacer(Modifier.height(4.dp))

                    Text(
                        text = "$extension · ${
                            if (exists) tr("На устройстве", "On device") else tr("Файл не найден", "File not found")
                        }",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                IconButton(
                    onClick = onPlayFromStart,
                    enabled = exists,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Replay,
                        contentDescription = tr("С начала", "From start"),
                    )
                }

                IconButton(
                    onClick = onTogglePlayPause,
                    enabled = exists,
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) {
                            tr("Пауза", "Pause")
                        } else {
                            tr("Играть", "Play")
                        },
                    )
                }

                if (isActive) {
                    IconButton(onClick = onStop) {
                        Icon(
                            imageVector = Icons.Filled.Stop,
                            contentDescription = tr("Стоп", "Stop"),
                        )
                    }
                }
            }

            if (isActive) {
                Spacer(Modifier.height(8.dp))

                val max = durationMs.coerceAtLeast(1).toFloat()
                Slider(
                    value = positionMs.toFloat().coerceIn(0f, max),
                    onValueChange = { onSeek(it.toInt()) },
                    valueRange = 0f..max,
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = formatTime(positionMs),
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Text(
                        text = formatTime(durationMs),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}

private fun formatTime(ms: Int): String {
    val total = ms.coerceAtLeast(0) / 1000
    return "%02d:%02d".format(total / 60, total % 60)
}

private fun queryDisplayName(
    context: Context,
    uri: Uri,
): String? {
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
