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
)

class AudioLibraryViewModel(
    application: Application,
    private val repository: AudioRepository,
) : AndroidViewModel(application) {

    private val recorder = WavRecorder(application)

    val audios: StateFlow<List<AudioEntity>> = repository.audios.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    private val _state = MutableStateFlow(AudioLibraryUiState())
    val state = _state.asStateFlow()

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
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(
                    modelClass: Class<T>,
                ): T {
                    return AudioLibraryViewModel(
                        application,
                        repository,
                    ) as T
                }
            }
    }
}