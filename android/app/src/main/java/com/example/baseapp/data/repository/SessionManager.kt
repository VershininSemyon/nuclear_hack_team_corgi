package com.example.baseapp.data.repository

import android.content.Context
import com.example.baseapp.data.network.TokenManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class SessionState {
    NOT_SELECTED,
    GUEST,
    AUTHENTICATED,
}

class SessionManager(context: Context) {

    private val preferences = context.applicationContext
        .getSharedPreferences("app_session", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(readInitialState())
    val state: StateFlow<SessionState> = _state.asStateFlow()

    private fun readInitialState(): SessionState {
        if (TokenManager.accessToken != null) {
            return SessionState.AUTHENTICATED
        }

        val savedMode = preferences.getString(KEY_MODE, null)

        return when (savedMode) {
            MODE_GUEST -> SessionState.GUEST
            else -> SessionState.NOT_SELECTED
        }
    }

    fun continueAsGuest() {
        preferences.edit()
            .putString(KEY_MODE, MODE_GUEST)
            .apply()

        _state.value = SessionState.GUEST
    }

    fun markAuthenticated() {
        preferences.edit()
            .putString(KEY_MODE, MODE_AUTHENTICATED)
            .apply()

        _state.value = SessionState.AUTHENTICATED
    }

    fun clearSession() {
        preferences.edit()
            .remove(KEY_MODE)
            .apply()

        _state.value = SessionState.NOT_SELECTED
    }

    companion object {
        private const val KEY_MODE = "session_mode"
        private const val MODE_GUEST = "guest"
        private const val MODE_AUTHENTICATED = "authenticated"
    }
}