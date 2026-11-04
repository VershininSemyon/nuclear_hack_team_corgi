
package com.example.baseapp.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.baseapp.data.TranscriptRepository
import com.example.baseapp.data.TranscriptSummary
import com.example.baseapp.data.repository.AuthRepository
import com.example.baseapp.data.repository.SyncManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ListUiState(
    val transcripts: List<TranscriptSummary> = emptyList(),
    val isSyncing: Boolean = false,
    val isLoggedIn: Boolean = false,
    val syncMessage: String? = null,
)

class TranscriptListViewModel(
    private val repository: TranscriptRepository,
    private val authRepository: AuthRepository,
    private val syncManager: SyncManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ListUiState(isLoggedIn = authRepository.isLoggedIn()))
    val uiState: StateFlow<ListUiState> = _uiState.asStateFlow()

    init {
        repository.transcripts.onEach { list ->
            _uiState.update { it.copy(transcripts = list) }
        }.launchIn(viewModelScope)
    }

    fun delete(id: Long) { viewModelScope.launch { repository.delete(id) } }

    fun sync() {
        if (_uiState.value.isSyncing || !_uiState.value.isLoggedIn) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true, syncMessage = null) }
            try {
                syncManager.sync()
                _uiState.update { it.copy(isSyncing = false, syncMessage = "Синхронизация завершена") }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSyncing = false, syncMessage = "Ошибка: ${e.message}") }
            }
        }
    }

    fun login(username: String, password: String) {
        viewModelScope.launch {
            authRepository.login(username, password).onSuccess {
                _uiState.update { it.copy(isLoggedIn = true, syncMessage = "Вход выполнен") }
                sync()
            }.onFailure {
                _uiState.update { it.copy(syncMessage = it.message) }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
            _uiState.update { it.copy(isLoggedIn = false) }
        }
    }

    fun dismissMessage() { _uiState.update { it.copy(syncMessage = null) } }

    companion object {
        fun factory(repo: TranscriptRepository, auth: AuthRepository, sync: SyncManager): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = TranscriptListViewModel(repo, auth, sync) as T
            }
    }
}
