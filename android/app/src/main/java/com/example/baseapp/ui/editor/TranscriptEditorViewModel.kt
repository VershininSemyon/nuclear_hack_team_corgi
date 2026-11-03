package com.example.baseapp.ui.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.example.baseapp.audio.AudioFormat
import com.example.baseapp.data.TranscriptDraftStore
import com.example.baseapp.data.TranscriptRepository
import com.example.baseapp.transcriber.TranscriptSegment
import com.example.baseapp.transcriber.WordTimestamp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class TranscriptEditorUiState(
    val id: Long = 0L,
    val isLoading: Boolean = true,
    val notFound: Boolean = false,
    val title: String = "",
    val text: String = "",
    val originalText: String = "",
    val savedTitle: String = "",
    val savedText: String = "",
    val audioFileName: String = "",
    val audioFormat: AudioFormat = AudioFormat.WAV,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val isEdited: Boolean = false,
    val words: List<WordTimestamp> = emptyList(),
    val segments: List<TranscriptSegment> = emptyList(),
    val isSaving: Boolean = false,
    val hasUnsavedChanges: Boolean = false,
    val draftRestored: Boolean = false,
    val deleted: Boolean = false,
    val message: String? = null,
    val errorMessage: String? = null,
) {
    val timestampsOutdated: Boolean
        get() = words.isNotEmpty() && (isEdited || text != originalText)
}

class TranscriptEditorViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val repository: TranscriptRepository,
    private val draftStore: TranscriptDraftStore,
) : ViewModel() {

    private val transcriptId: Long = checkNotNull(savedStateHandle.get<Long>(ARG_ID)) {
        "Не передан id расшифровки"
    }

    private val _state = MutableStateFlow(
        TranscriptEditorUiState(id = transcriptId),
    )
    val state: StateFlow<TranscriptEditorUiState> = _state.asStateFlow()

    private var draftJob: Job? = null

    init {
        load()
    }

    fun onTitleChanged(value: String) {
        _state.update {
            it.copy(
                title = value,
                hasUnsavedChanges = value != it.savedTitle || it.text != it.savedText,
                draftRestored = false,
            )
        }
        savedStateHandle[KEY_TITLE] = value
        scheduleDraftSave()
    }

    fun onTextChanged(value: String) {
        _state.update {
            it.copy(
                text = value,
                hasUnsavedChanges = it.title != it.savedTitle || value != it.savedText,
                draftRestored = false,
            )
        }
        savedStateHandle[KEY_TEXT] = value
        scheduleDraftSave()
    }

    fun save() {
        val snapshot = _state.value
        if (snapshot.isLoading || snapshot.isSaving || snapshot.deleted) return
        if (snapshot.title.isBlank()) {
            _state.update { it.copy(errorMessage = "Название не может быть пустым") }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, errorMessage = null, message = null) }
            try {
                val success = withContext(Dispatchers.IO) {
                    repository.saveEdits(
                        id = transcriptId,
                        title = snapshot.title,
                        text = snapshot.text,
                    )
                }
                if (!success) {
                    _state.update {
                        it.copy(
                            isSaving = false,
                            notFound = true,
                            errorMessage = "Расшифровка не найдена",
                        )
                    }
                    return@launch
                }

                draftStore.clear(transcriptId)
                _state.update {
                    it.copy(
                        isSaving = false,
                        savedTitle = it.title,
                        savedText = it.text,
                        isEdited = it.text != it.originalText,
                        updatedAt = System.currentTimeMillis(),
                        hasUnsavedChanges = false,
                        draftRestored = false,
                        message = "Изменения сохранены",
                    )
                }
            } catch (t: Throwable) {
                _state.update {
                    it.copy(
                        isSaving = false,
                        errorMessage = t.message ?: "Не удалось сохранить изменения",
                    )
                }
            }
        }
    }

    fun cancelChanges() {
        if (!_state.value.hasUnsavedChanges) return
        draftStore.clear(transcriptId)
        _state.update {
            it.copy(
                title = it.savedTitle,
                text = it.savedText,
                hasUnsavedChanges = false,
                draftRestored = false,
                message = "Изменения отменены",
            )
        }
        savedStateHandle[KEY_TITLE] = _state.value.title
        savedStateHandle[KEY_TEXT] = _state.value.text
    }

    fun delete() {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { repository.delete(transcriptId) }
                draftStore.clear(transcriptId)
                _state.update { it.copy(deleted = true, message = "Расшифровка удалена") }
            } catch (t: Throwable) {
                _state.update {
                    it.copy(errorMessage = t.message ?: "Не удалось удалить расшифровку")
                }
            }
        }
    }

    /** Вызывается экраном при уходе в фон, чтобы не потерять ввод. */
    fun persistDraft() {
        draftJob?.cancel()
        val snapshot = _state.value
        if (snapshot.isLoading || snapshot.deleted) return
        if (snapshot.hasUnsavedChanges) {
            draftStore.save(transcriptId, snapshot.title, snapshot.text)
        } else {
            draftStore.clear(transcriptId)
        }
    }

    fun dismissMessage() {
        _state.update { it.copy(message = null) }
    }

    fun dismissError() {
        _state.update { it.copy(errorMessage = null) }
    }

    private fun load() {
        viewModelScope.launch {
            val transcript = withContext(Dispatchers.IO) { repository.get(transcriptId) }
            if (transcript == null) {
                _state.update {
                    it.copy(isLoading = false, notFound = true)
                }
                return@launch
            }

            val draft = draftStore.load(transcriptId)
            val useDraft = draft != null &&
                (draft.title != transcript.title || draft.text != transcript.text)

            val title = savedStateHandle.get<String>(KEY_TITLE)
                ?: draft?.takeIf { useDraft }?.title
                ?: transcript.title
            val text = savedStateHandle.get<String>(KEY_TEXT)
                ?: draft?.takeIf { useDraft }?.text
                ?: transcript.text

            _state.value = TranscriptEditorUiState(
                id = transcript.id,
                isLoading = false,
                title = title,
                text = text,
                originalText = transcript.originalText,
                savedTitle = transcript.title,
                savedText = transcript.text,
                audioFileName = transcript.audioFileName,
                audioFormat = transcript.audioFormat,
                createdAt = transcript.createdAt,
                updatedAt = transcript.updatedAt,
                isEdited = transcript.isEdited,
                words = transcript.words,
                segments = transcript.segments,
                hasUnsavedChanges = title != transcript.title || text != transcript.text,
                draftRestored = useDraft,
            )
        }
    }

    private fun scheduleDraftSave() {
        draftJob?.cancel()
        draftJob = viewModelScope.launch {
            delay(700)
            val snapshot = _state.value
            if (snapshot.isLoading || snapshot.deleted) return@launch
            if (snapshot.hasUnsavedChanges) {
                draftStore.save(transcriptId, snapshot.title, snapshot.text)
            } else {
                draftStore.clear(transcriptId)
            }
        }
    }

    override fun onCleared() {
        persistDraft()
        super.onCleared()
    }

    companion object {
        const val ARG_ID = "id"
        private const val KEY_TITLE = "editor_title"
        private const val KEY_TEXT = "editor_text"

        fun factory(
            repository: TranscriptRepository,
            draftStore: TranscriptDraftStore,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(
                modelClass: Class<T>,
                extras: CreationExtras,
            ): T {
                return TranscriptEditorViewModel(
                    savedStateHandle = extras.createSavedStateHandle(),
                    repository = repository,
                    draftStore = draftStore,
                ) as T
            }
        }
    }
}
