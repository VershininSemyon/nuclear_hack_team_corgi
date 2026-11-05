
package com.example.baseapp.ui.transcription

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.baseapp.AppContainer
import com.example.baseapp.audio.AudioException
import com.example.baseapp.audio.AudioFileInfo
import com.example.baseapp.audio.AudioFileStore
import com.example.baseapp.audio.AudioFormat
import com.example.baseapp.audio.WavRecorder
import com.example.baseapp.data.TranscriptRepository
import com.example.baseapp.transcriber.Transcriber
import com.example.baseapp.transcriber.TranscriberFactory
import com.example.baseapp.transcriber.TranscriptionResult
import com.example.baseapp.transcriber.model.GigaAmModelManager
import com.example.baseapp.transcriber.model.ModelStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class TranscriptionUiState(
    val audio: AudioFileInfo? = null,
    val result: TranscriptionResult? = null,
    val savedTranscriptId: Long? = null,
    val progress: Float = 0f,
    val isImporting: Boolean = false,
    val isRecording: Boolean = false,
    val isStartingRecording: Boolean = false,
    val isTranscribing: Boolean = false,
    val isSaving: Boolean = false,
    val recordingElapsedMs: Long = 0L,
    val errorMessage: String? = null,
    val message: String? = null,
    val modelStatus: ModelStatus,
) {
    val canTranscribe: Boolean
        get() = audio != null &&
            modelStatus is ModelStatus.Installed &&
            !isTranscribing &&
            !isRecording &&
            !isImporting

    val canSave: Boolean
        get() = result != null &&
            audio != null &&
            !isSaving &&
            savedTranscriptId == null
}

class TranscriptionViewModel(
    application: Application,
    private val modelManager: GigaAmModelManager,
    private val repository: TranscriptRepository,
) : AndroidViewModel(application) {

    private val app = application

    private val recorder = WavRecorder(application)

    private val transcriber: Transcriber =
        TranscriberFactory.create(application, modelManager)

    private val _state = MutableStateFlow(
        TranscriptionUiState(
            modelStatus = modelManager.status.value,
        )
    )

    val state: StateFlow<TranscriptionUiState> = _state.asStateFlow()

    private var recordingStartedAt = 0L
    private var recordingTicker: Job? = null

    init {
        viewModelScope.launch {
            modelManager.status.collect { status ->
                _state.update {
                    it.copy(modelStatus = status)
                }
            }
        }
    }

    fun importAudio(uri: Uri) {
        if (
            _state.value.isImporting ||
            _state.value.isRecording ||
            _state.value.isTranscribing
        ) return

        viewModelScope.launch {
            _state.update {
                it.copy(
                    isImporting = true,
                    errorMessage = null,
                    message = null,
                )
            }

            try {
                val info = withContext(Dispatchers.IO) {
                    AudioFileStore.importFromUri(app, uri)
                }

                _state.update {
                    it.copy(
                        audio = info,
                        result = null,
                        savedTranscriptId = null,
                        progress = 0f,
                        isImporting = false,
                    )
                }
            } catch (t: Throwable) {
                _state.update {
                    it.copy(
                        isImporting = false,
                        errorMessage = t.userMessage(
                            "Не удалось загрузить аудиофайл"
                        ),
                    )
                }
            }
        }
    }

    fun startRecording() {
        val current = _state.value

        if (
            current.isRecording ||
            current.isStartingRecording ||
            current.isTranscribing
        ) return

        viewModelScope.launch {
            _state.update {
                it.copy(
                    isStartingRecording = true,
                    errorMessage = null,
                    message = null,
                )
            }

            try {
                withContext(Dispatchers.IO) {
                    recorder.start()
                }

                recordingStartedAt = System.currentTimeMillis()

                _state.update {
                    it.copy(
                        isRecording = true,
                        isStartingRecording = false,
                        recordingElapsedMs = 0L,
                        audio = null,
                        result = null,
                        savedTranscriptId = null,
                        progress = 0f,
                    )
                }

                startTicker()
            } catch (t: Throwable) {
                _state.update {
                    it.copy(
                        isStartingRecording = false,
                        errorMessage = t.userMessage(
                            "Не удалось начать запись"
                        ),
                    )
                }
            }
        }
    }

    fun stopRecording() {
        if (!_state.value.isRecording) return

        stopTicker()

        viewModelScope.launch {
            try {
                val file = withContext(Dispatchers.IO) {
                    recorder.stop()
                }

                if (file == null) {
                    _state.update {
                        it.copy(
                            isRecording = false,
                            recordingElapsedMs = 0L,
                            errorMessage = "Запись получилась пустой",
                        )
                    }
                    return@launch
                }

                val info = withContext(Dispatchers.IO) {
                    AudioFileStore.describeValidated(
                        file = file,
                        displayName = file.name,
                        format = AudioFormat.WAV,
                        mimeType = "audio/wav",
                    )
                }

                _state.update {
                    it.copy(
                        isRecording = false,
                        recordingElapsedMs = 0L,
                        audio = info,
                        result = null,
                        savedTranscriptId = null,
                        progress = 0f,
                    )
                }
            } catch (t: Throwable) {
                _state.update {
                    it.copy(
                        isRecording = false,
                        recordingElapsedMs = 0L,
                        errorMessage = t.userMessage(
                            "Не удалось остановить запись"
                        ),
                    )
                }
            }
        }
    }

    fun clearAudio() {
        if (
            _state.value.isRecording ||
            _state.value.isTranscribing
        ) return

        _state.update {
            it.copy(
                audio = null,
                result = null,
                savedTranscriptId = null,
                progress = 0f,
                errorMessage = null,
                message = null,
            )
        }
    }

    fun transcribe() {
        val audio = _state.value.audio ?: return

        if (!_state.value.canTranscribe) {
            if (_state.value.modelStatus !is ModelStatus.Installed) {
                _state.update {
                    it.copy(
                        errorMessage = "Модель GigaAM v3 не установлена",
                    )
                }
            }
            return
        }

        viewModelScope.launch {
            _state.update {
                it.copy(
                    isTranscribing = true,
                    progress = 0f,
                    result = null,
                    errorMessage = null,
                    message = null,
                    savedTranscriptId = null,
                )
            }

            try {
                val result = withContext(Dispatchers.Default) {
                    transcriber.transcribe(audio.path) { value ->
                        _state.update { state ->
                            state.copy(
                                progress = value.coerceIn(0f, 1f),
                            )
                        }
                    }
                }

                val empty = result.text.isBlank() &&
                    result.words.isEmpty()

                _state.update {
                    it.copy(
                        isTranscribing = false,
                        progress = 1f,
                        result = if (empty) null else result,
                        message = if (empty) {
                            "Речь не распознана. Проверьте, что в аудио есть речь " +
                                "и модель GigaAM v3 установлена."
                        } else {
                            null
                        },
                    )
                }
            } catch (t: Throwable) {
                _state.update {
                    it.copy(
                        isTranscribing = false,
                        errorMessage = t.userMessage(
                            "Ошибка распознавания"
                        ),
                    )
                }
            }
        }
    }

    fun saveResult(title: String) {
        val snapshot = _state.value
        val result = snapshot.result ?: return
        val audio = snapshot.audio ?: return

        if (
            snapshot.isSaving ||
            snapshot.savedTranscriptId != null
        ) return

        viewModelScope.launch {
            _state.update {
                it.copy(
                    isSaving = true,
                    errorMessage = null,
                )
            }

            try {
                val id = withContext(Dispatchers.IO) {
                    repository.saveNew(
                        result = result,
                        title = title,
                        audioFileName = audio.displayName,
                        audioFormat = audio.format,
                        audioFile = audio.file,
                    )
                }

                _state.update {
                    it.copy(
                        isSaving = false,
                        savedTranscriptId = id,
                        message = "Расшифровка сохранена",
                    )
                }
            } catch (t: Throwable) {
                _state.update {
                    it.copy(
                        isSaving = false,
                        errorMessage = t.userMessage(
                            "Не удалось сохранить расшифровку"
                        ),
                    )
                }
            }
        }
    }

    fun installModel() {
        viewModelScope.launch {
            try {
                modelManager.install()
            } catch (_: Throwable) {
                // Ошибка установки отображается через modelStatus.
            }
        }
    }

    fun installModelFromUri(uri: Uri, fileName: String?) {
        viewModelScope.launch {
            try {
                modelManager.installFromUri(uri, fileName)
            } catch (_: Throwable) {
                // Ошибка установки отображается через modelStatus.
            }
        }
    }

    fun refreshModelStatus() {
        modelManager.refresh()
    }

    fun dismissMessage() {
        _state.update {
            it.copy(message = null)
        }
    }

    fun dismissError() {
        _state.update {
            it.copy(errorMessage = null)
        }
    }

    private fun startTicker() {
        stopTicker()

        recordingTicker = viewModelScope.launch {
            while (_state.value.isRecording) {
                _state.update {
                    it.copy(
                        recordingElapsedMs =
                            System.currentTimeMillis() - recordingStartedAt,
                    )
                }

                delay(200)
            }
        }
    }

    private fun stopTicker() {
        recordingTicker?.cancel()
        recordingTicker = null
    }

    override fun onCleared() {
        stopTicker()
        recorder.release()
        transcriber.release()
        super.onCleared()
    }

    private fun Throwable.userMessage(fallback: String): String {
        val error = message?.trim()

        return when {
            this is AudioException && !error.isNullOrBlank() -> error
            !error.isNullOrBlank() -> error
            else -> fallback
        }
    }

    companion object {
        fun factory(
            application: Application,
            container: AppContainer,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {

                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(
                    modelClass: Class<T>,
                ): T {
                    return TranscriptionViewModel(
                        application = application,
                        modelManager = container.modelManager,
                        repository = container.transcriptRepository,
                    ) as T
                }
            }
    }
}
