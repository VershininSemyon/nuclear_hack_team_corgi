
package com.example.baseapp.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.example.baseapp.data.db.AppDatabase
import com.example.baseapp.data.db.SyncStatus
import com.example.baseapp.data.network.RetrofitClient
import com.example.baseapp.data.network.TokenManager
import com.example.baseapp.data.network.dto.LoginRequestDto
import com.example.baseapp.data.network.dto.RegisterRequestDto
import java.io.File

class AuthRepository(
    private val sessionManager: SessionManager,
    private val database: AppDatabase,
    private val context: Context,
    private val syncManager: SyncManager,
) {

    suspend fun login(
        username: String,
        password: String,
    ): Result<Unit> = runCatching {
        val wasGuest = sessionManager.state.value == SessionState.GUEST

        val response = RetrofitClient.api.login(
            LoginRequestDto(
                username = username.trim(),
                password = password,
            ),
        )

        if (!response.isSuccessful) {
            throw Exception(
                readError(response.code(), response.errorBody()?.string()),
            )
        }

        val tokens = response.body()
            ?: throw Exception("Сервер не вернул токены авторизации")

        TokenManager.saveTokens(
            access = tokens.access,
            refresh = tokens.refresh,
        )

        sessionManager.markAuthenticated()

        if (wasGuest) {
            prepareGuestDataForUpload()
            syncManager.sync()
        }
    }

    suspend fun register(
        username: String,
        email: String,
        password: String,
    ): Result<Unit> = runCatching {
        val wasGuest = sessionManager.state.value == SessionState.GUEST
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
            ),
        )

        if (!registrationResponse.isSuccessful) {
            throw Exception(
                readError(
                    registrationResponse.code(),
                    registrationResponse.errorBody()?.string(),
                ),
            )
        }

        val loginResponse = RetrofitClient.api.login(
            LoginRequestDto(
                username = cleanUsername,
                password = password,
            ),
        )

        if (!loginResponse.isSuccessful) {
            throw Exception(
                "Аккаунт создан, но автоматический вход не удался. " +
                    "Попробуйте войти вручную.",
            )
        }

        val tokens = loginResponse.body()
            ?: throw Exception("Сервер не вернул токены авторизации")

        TokenManager.saveTokens(
            access = tokens.access,
            refresh = tokens.refresh,
        )

        sessionManager.markAuthenticated()

        if (wasGuest) {
            prepareGuestDataForUpload()
            syncManager.sync()
        }
    }

    private suspend fun prepareGuestDataForUpload() {
        database.withTransaction {
            val audioDao = database.audioDao()
            val transcriptDao = database.transcriptDao()

            // Гостевые удаления не нужно отправлять на сервер:
            // соответствующих серверных записей ещё не существует.
            for (transcript in transcriptDao.getUnsynced()) {
                if (transcript.deletedAt != null) {
                    transcriptDao.hardDelete(transcript.id)
                }
            }

            for (audio in audioDao.getAll()) {
                if (audio.deletedAt != null) {
                    File(audio.filePath).takeIf { it.exists() }?.delete()
                    audioDao.hardDelete(audio.id)
                }
            }

            // Все оставшиеся локальные данные гостя должны быть созданы
            // на сервере после перехода в авторизованный режим.
            for (audio in audioDao.getAll()) {
                audioDao.markSyncStatus(audio.id, SyncStatus.CREATED)
            }

            for (transcript in transcriptDao.getUnsynced()) {
                transcriptDao.markSyncStatus(
                    transcript.id,
                    SyncStatus.CREATED,
                )
            }

            // Часть гостевых расшифровок могла иметь статус SYNCED.
            // Получаем полный список через связанные с аудио записи ниже.
            for (audio in audioDao.getAll()) {
                for (transcript in transcriptDao.getByAudioId(audio.id)) {
                    transcriptDao.markSyncStatus(
                        transcript.id,
                        SyncStatus.CREATED,
                    )
                }
            }
        }
    }

    fun continueAsGuest() {
        sessionManager.continueAsGuest()
    }

    suspend fun logout() {
        // Сначала удаляем локальные данные, затем очищаем токены.
        // Никаких DELETE-запросов к backend здесь нет.
        database.withTransaction {
            database.transcriptDao().clearAll()
            database.audioDao().clearAll()
        }

        listOf("downloads", "uploads").forEach { directoryName ->
            val directory = File(context.filesDir, directoryName)
            if (directory.exists() && !directory.deleteRecursively()) {
                throw IllegalStateException(
                    "Не удалось очистить каталог $directoryName",
                )
            }
        }

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
