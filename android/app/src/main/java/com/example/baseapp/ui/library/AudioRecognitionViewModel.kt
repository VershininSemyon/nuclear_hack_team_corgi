
package com.example.baseapp.ui.library

import com.example.baseapp.i18n.tr

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.baseapp.AppContainer
import com.example.baseapp.audio.AudioFileStore
import com.example.baseapp.data.TranscriptRepository
import com.example.baseapp.transcriber.Transcriber
import com.example.baseapp.transcriber.TranscriberFactory
import com.example.baseapp.transcriber.model.GigaAmModelManager
import com.example.baseapp.transcriber.model.ModelStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AudioRecognitionState(
    val modelStatus: ModelStatus,
    val isCheckingExisting: Boolean = false,
    val isAlreadyTranscribed: Boolean = false,
    val isTranscribing: Boolean = false,
    val isSaving: Boolean = false,
    val isCompleted: Boolean = false,
    val progress: Float = 0f,
    val message: String? = null,
    val errorMessage: String? = null,
)

class AudioRecognitionViewModel(
    application: Application,
    private val modelManager: GigaAmModelManager,
    private val transcriptRepository: TranscriptRepository,
    private val transcriber: Transcriber,
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(
        AudioRecognitionState(modelStatus = modelManager.status.value),
    )

    val state: StateFlow<AudioRecognitionState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            modelManager.status.collect { status ->
                _state.update { it.copy(modelStatus = status) }
            }
        }
    }

    fun recognize(audioId: Long, filePath: String, title: String) {
        val current = _state.value

        if (
            current.isCheckingExisting ||
            current.isTranscribing ||
            current.isSaving ||
            current.isCompleted ||
            current.isAlreadyTranscribed
        ) return

        if (current.modelStatus !is ModelStatus.Installed) {
            _state.update {
                it.copy(errorMessage = tr("Модель GigaAM v3 не установлена", "GigaAM v3 model is not installed"))
            }
            return
        }

        viewModelScope.launch {
            _state.update {
                it.copy(
                    isCheckingExisting = true,
                    message = null,
                    errorMessage = null,
                )
            }

            try {
                // Сначала проверяем наличие расшифровки в базе.
                val alreadyTranscribed = withContext(Dispatchers.IO) {
                    transcriptRepository.hasTranscriptForAudio(audioId)
                }

                if (alreadyTranscribed) {
                    _state.update {
                        it.copy(
                            isCheckingExisting = false,
                            isAlreadyTranscribed = true,
                            message = tr("Аудио уже расшифровано", "Audio already transcribed"),
                        )
                    }
                    return@launch
                }

                _state.update {
                    it.copy(
                        isCheckingExisting = false,
                        isTranscribing = true,
                        isSaving = false,
                        progress = 0f,
                        message = null,
                        errorMessage = null,
                    )
                }

                val audio = withContext(Dispatchers.IO) {
                    val file = java.io.File(filePath)
                    check(file.isFile) { tr("Файл аудио не найден", "Audio file not found") }
                    AudioFileStore.describe(file)
                }

                val result = withContext(Dispatchers.Default) {
                    transcriber.transcribe(audio.path) { value ->
                        _state.update { state ->
                            state.copy(progress = value.coerceIn(0f, 1f))
                        }
                    }
                }

                if (result.text.isBlank() && result.words.isEmpty()) {
                    _state.update {
                        it.copy(
                            isTranscribing = false,
                            progress = 1f,
                            message = tr("Речь не распознана. Проверьте аудиофайл.", "No speech recognized. Check the audio file."),
                        )
                    }
                    return@launch
                }

                _state.update {
                    it.copy(
                        isTranscribing = false,
                        isSaving = true,
                        progress = 1f,
                    )
                }

                withContext(Dispatchers.IO) {
                    transcriptRepository.saveNew(
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
                        isCompleted = true,
                        isAlreadyTranscribed = true,
                        message = tr("Расшифровка сохранена", "Transcript saved"),
                    )
                }
            } catch (t: Throwable) {
                _state.update {
                    it.copy(
                        isCheckingExisting = false,
                        isTranscribing = false,
                        isSaving = false,
                        errorMessage = t.message ?: tr("Ошибка распознавания", "Recognition error"),
                    )
                }
            }
        }
    }

    fun refreshModelStatus() {
        modelManager.refresh()
    }

    override fun onCleared() {
        transcriber.release()
        super.onCleared()
    }

    companion object {
        fun factory(
            application: Application,
            container: AppContainer,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val transcriber = TranscriberFactory.create(
                        context = application,
                        modelManager = container.modelManager,
                        diarizationModelManager = container.diarizationModelManager,
                    )

                    return AudioRecognitionViewModel(
                        application = application,
                        modelManager = container.modelManager,
                        transcriptRepository = container.transcriptRepository,
                        transcriber = transcriber,
                    ) as T
                }
            }
    }
}
