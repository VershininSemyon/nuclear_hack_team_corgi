
package com.example.baseapp.ui.library

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
) : AndroidViewModel(application) {

    private val transcriber: Transcriber =
        TranscriberFactory.create(application, modelManager)

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

    fun recognize(filePath: String, title: String) {
        val current = _state.value
        if (current.isTranscribing || current.isSaving || current.isCompleted) return

        if (current.modelStatus !is ModelStatus.Installed) {
            _state.update {
                it.copy(errorMessage = "Модель GigaAM v3 не установлена")
            }
            return
        }

        viewModelScope.launch {
            _state.update {
                it.copy(
                    isTranscribing = true,
                    isSaving = false,
                    progress = 0f,
                    message = null,
                    errorMessage = null,
                )
            }

            try {
                val audio = withContext(Dispatchers.IO) {
                    val file = java.io.File(filePath)
                    check(file.isFile) { "Файл аудио не найден" }
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
                            message = "Речь не распознана. Проверьте аудиофайл.",
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
                        message = "Расшифровка сохранена",
                    )
                }
            } catch (t: Throwable) {
                _state.update {
                    it.copy(
                        isTranscribing = false,
                        isSaving = false,
                        errorMessage = t.message ?: "Ошибка распознавания",
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
                    return AudioRecognitionViewModel(
                        application = application,
                        modelManager = container.modelManager,
                        transcriptRepository = container.transcriptRepository,
                    ) as T
                }
            }
    }
}
