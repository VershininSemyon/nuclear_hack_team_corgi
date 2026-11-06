
package com.example.baseapp.ui.library

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.baseapp.audio.AudioFileStore
import com.example.baseapp.audio.WavRecorder
import com.example.baseapp.data.db.AudioEntity
import com.example.baseapp.data.repository.AudioRepository
import com.example.baseapp.transcriber.model.GigaAmModelManager
import com.example.baseapp.transcriber.model.ModelStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AudioLibraryUiState(
    val isImporting: Boolean = false,
    val isRecording: Boolean = false,
    val isStartingRecording: Boolean = false,
    val errorMessage: String? = null,
    val message: String? = null,
    val modelStatus: ModelStatus,
)

class AudioLibraryViewModel(
    application: Application,
    private val repository: AudioRepository,
    private val modelManager: GigaAmModelManager,
) : AndroidViewModel(application) {

    private val recorder = WavRecorder(application)

    val audios: StateFlow<List<AudioEntity>> = repository.audios.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    private val _state = MutableStateFlow(
        AudioLibraryUiState(
            modelStatus = modelManager.status.value,
        ),
    )

    val state = _state.asStateFlow()

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
        if (_state.value.isImporting || _state.value.isRecording) return

        viewModelScope.launch {
            _state.update {
                it.copy(
                    isImporting = true,
                    errorMessage = null,
                    message = null,
                )
            }

            try {
                withContext(Dispatchers.IO) {
                    val info = AudioFileStore.importFromUri(
                        getApplication(),
                        uri,
                    )
                    repository.save(info)
                }

                _state.update {
                    it.copy(
                        isImporting = false,
                        message = "Аудиозапись добавлена",
                    )
                }
            } catch (t: Throwable) {
                _state.update {
                    it.copy(
                        isImporting = false,
                        errorMessage = t.message
                            ?: "Не удалось импортировать аудио",
                    )
                }
            }
        }
    }

    fun startRecording() {
        if (
            _state.value.isRecording ||
            _state.value.isStartingRecording ||
            _state.value.isImporting
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

                _state.update {
                    it.copy(
                        isStartingRecording = false,
                        isRecording = true,
                    )
                }
            } catch (t: Throwable) {
                _state.update {
                    it.copy(
                        isStartingRecording = false,
                        errorMessage = t.message
                            ?: "Не удалось начать запись",
                    )
                }
            }
        }
    }

    fun stopRecording() {
        if (!_state.value.isRecording) return

        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val file = recorder.stop()
                        ?: error("Запись получилась пустой")

                    val info = AudioFileStore.describe(
                        file = file,
                        displayName = file.name,
                        mimeType = "audio/wav",
                    )

                    repository.save(info)
                }

                _state.update {
                    it.copy(
                        isRecording = false,
                        message = "Запись сохранена",
                    )
                }
            } catch (t: Throwable) {
                _state.update {
                    it.copy(
                        isRecording = false,
                        errorMessage = t.message
                            ?: "Не удалось сохранить запись",
                    )
                }
            }
        }
    }

    /**
     * Скачивает официальную модель GigaAM v3.
     * Прогресс и ошибки приходят через modelManager.status.
     */
    fun installModel() {
        viewModelScope.launch {
            try {
                modelManager.install()
            } catch (_: Throwable) {
                // Ошибка уже отражена в modelManager.status.
            }
        }
    }

    /**
     * Устанавливает модель из выбранного пользователем архива .tar.bz2.
     */
    fun installModelFromUri(uri: Uri, fileName: String?) {
        viewModelScope.launch {
            try {
                modelManager.installFromUri(uri, fileName)
            } catch (_: Throwable) {
                // Ошибка уже отражена в modelManager.status.
            }
        }
    }

    fun refreshModelStatus() {
        modelManager.refresh()
    }

    fun dismissMessage() {
        _state.update { it.copy(message = null) }
    }

    fun dismissError() {
        _state.update { it.copy(errorMessage = null) }
    }

    override fun onCleared() {
        recorder.release()
        super.onCleared()
    }

    companion object {
        fun factory(
            application: Application,
            repository: AudioRepository,
            modelManager: GigaAmModelManager,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {

                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(
                    modelClass: Class<T>,
                ): T {
                    return AudioLibraryViewModel(
                        application = application,
                        repository = repository,
                        modelManager = modelManager,
                    ) as T
                }
            }
    }
}
