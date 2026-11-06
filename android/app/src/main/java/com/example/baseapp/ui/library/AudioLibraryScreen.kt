
package com.example.baseapp.ui.library

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioLibraryScreen(
    onOpenProfile: () -> Unit,
    onOpenAudioDetails: (Long) -> Unit,
    onOpenTranscription: () -> Unit,
) {
    val context = LocalContext.current
    val application = context.applicationContext as Application
    val container = context.appContainer

    val viewModel: AudioLibraryViewModel = viewModel(
        factory = AudioLibraryViewModel.factory(
            application = application,
            repository = container.audioRepository,
            modelManager = container.modelManager,
        ),
    )

    val audios by viewModel.audios.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()

    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var playingAudioId by remember { mutableStateOf<Long?>(null) }
    var playbackError by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            player?.release()
            player = null
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
                    Text("Мои аудиозаписи")
                },
                actions = {
                    TextButton(
                        onClick = onOpenTranscription,
                    ) {
                        Text("Расшифровка")
                    }

                    TextButton(
                        onClick = onOpenProfile,
                    ) {
                        Text("Профиль")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
        ) {
            // Состояние модели и управление её установкой.
            // Карточка использует тот же GigaAmModelManager,
            // что и существующий экран расшифровки.
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
                    Text(
                        if (state.isImporting) "Импорт..." else "Загрузить аудио",
                    )
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
                            state.isStartingRecording -> "Запуск..."
                            state.isRecording -> "Остановить"
                            else -> "Записать"
                        },
                    )
                }
            }

            if (state.isRecording) {
                Text(
                    text = "Идёт запись...",
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
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Аудиозаписей пока нет.\n" +
                            "Загрузите файл или сделайте новую запись.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(
                        items = audios,
                        key = { it.id },
                    ) { audio ->
                        AudioItem(
                            audio = audio,
                            isPlaying = playingAudioId == audio.id,
                            onClick = {
                                onOpenAudioDetails(audio.id)
                            },
                            onPlay = {
                                try {
                                    player?.release()
                                    player = null
                                    playingAudioId = null

                                    val file = File(audio.filePath)
                                    check(file.exists()) {
                                        "Файл аудио не найден"
                                    }

                                    val newPlayer = MediaPlayer()
                                    player = newPlayer

                                    newPlayer.setDataSource(file.absolutePath)

                                    newPlayer.setOnPreparedListener {
                                        it.start()
                                        playingAudioId = audio.id
                                    }

                                    newPlayer.setOnCompletionListener {
                                        it.release()
                                        player = null
                                        playingAudioId = null
                                    }

                                    newPlayer.prepareAsync()
                                } catch (t: Throwable) {
                                    playbackError = t.message
                                        ?: "Не удалось воспроизвести файл"

                                    player?.release()
                                    player = null
                                    playingAudioId = null
                                }
                            },
                            onStop = {
                                player?.release()
                                player = null
                                playingAudioId = null
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
                Text("Ошибка воспроизведения")
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
                    Text("Закрыть")
                }
            },
        )
    }
}

@Composable
private fun AudioItem(
    audio: AudioEntity,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    onStop: () -> Unit,
) {
    val extension = File(audio.filePath).extension.uppercase()
    val exists = File(audio.filePath).exists()

    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
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
                        if (exists) "На устройстве" else "Файл не найден"
                    }",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            TextButton(
                onClick = {
                    if (isPlaying) onStop() else onPlay()
                },
                enabled = exists,
            ) {
                Text(if (isPlaying) "Стоп" else "▶")
            }
        }
    }
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
