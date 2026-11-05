
package com.example.baseapp.data.repository

import com.example.baseapp.data.network.RetrofitClient
import com.example.baseapp.data.network.TokenManager
import com.example.baseapp.data.network.dto.LoginRequestDto
import com.example.baseapp.data.network.dto.RegisterRequestDto

class AuthRepository(
    private val sessionManager: SessionManager,
) {

    suspend fun login(
        username: String,
        password: String,
    ): Result<Unit> = runCatching {

        val response = RetrofitClient.api.login(
            LoginRequestDto(
                username = username.trim(),
                password = password,
            )
        )

        if (!response.isSuccessful) {
            throw Exception(readError(response.code(), response.errorBody()?.string()))
        }

        val tokens = response.body()
            ?: throw Exception("Сервер не вернул токены авторизации")

        TokenManager.saveTokens(
            access = tokens.access,
            refresh = tokens.refresh,
        )

        sessionManager.markAuthenticated()
    }

    suspend fun register(
        username: String,
        email: String,
        password: String,
    ): Result<Unit> = runCatching {

        val cleanUsername = username.trim()
        val cleanEmail = email.trim()

        require(cleanUsername.isNotBlank()) {
            "Введите имя пользователя"
        }

        require(cleanEmail.isNotBlank()) {
            "Введите электронную почту"
        }

        require(password.length >= 8) {
            "Пароль должен содержать минимум 8 символов"
        }

        val registrationResponse = RetrofitClient.api.register(
            RegisterRequestDto(
                username = cleanUsername,
                email = cleanEmail,
                password = password,
            )
        )

        if (!registrationResponse.isSuccessful) {
            throw Exception(
                readError(
                    registrationResponse.code(),
                    registrationResponse.errorBody()?.string(),
                )
            )
        }

        // После регистрации сразу авторизуем пользователя.
        val loginResponse = RetrofitClient.api.login(
            LoginRequestDto(
                username = cleanUsername,
                password = password,
            )
        )

        if (!loginResponse.isSuccessful) {
            throw Exception(
                "Аккаунт создан, но автоматический вход не удался. " +
                    "Попробуйте войти вручную."
            )
        }

        val tokens = loginResponse.body()
            ?: throw Exception("Сервер не вернул токены авторизации")

        TokenManager.saveTokens(
            access = tokens.access,
            refresh = tokens.refresh,
        )

        sessionManager.markAuthenticated()
    }

    fun continueAsGuest() {
        sessionManager.continueAsGuest()
    }

    suspend fun logout() {
        // Локальный выход не должен зависеть от доступности backend.
        TokenManager.clearTokens()
        sessionManager.clearSession()
    }

    fun isLoggedIn(): Boolean =
        sessionManager.state.value == SessionState.AUTHENTICATED

    private fun readError(
        statusCode: Int,
        body: String?,
    ): String {
        return when (statusCode) {
            400, 409 -> "Пользователь с такими данными уже существует"
            401, 403 -> "Неверный логин или пароль"
            422 -> "Проверьте правильность введённых данных"
            else -> body?.takeIf { it.isNotBlank() }
                ?: "Ошибка сервера ($statusCode)"
        }
    }
}
