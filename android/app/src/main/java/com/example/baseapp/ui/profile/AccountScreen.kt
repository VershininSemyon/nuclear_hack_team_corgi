package com.example.baseapp.ui.profile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.baseapp.data.network.RetrofitClient
import com.example.baseapp.data.network.UserUpdateRequestDto
import com.example.baseapp.data.repository.AuthRepository
import com.example.baseapp.data.repository.SessionState
import com.example.baseapp.data.repository.SessionManager
import com.example.baseapp.data.repository.SyncManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    authRepository: AuthRepository,
    syncManager: SyncManager,
    onBack: () -> Unit,
    onLogin: () -> Unit,
    onRegister: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val app = context.applicationContext as com.example.baseapp.BaseApp
    val sessionManager = app.container.sessionManager

    val sessionState by sessionManager.state.collectAsState()

    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }

    var isLoadingUser by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var isSyncing by remember { mutableStateOf(false) }
    var isLoggingOut by remember { mutableStateOf(false) }

    var showDeleteAccountDialog by remember { mutableStateOf(false) }

    var errorMessage by remember { mutableStateOf<String?>(null) }
    var infoMessage by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()

    val isAuthenticated = sessionState == SessionState.AUTHENTICATED
    val isBusy = isLoadingUser || isSaving || isSyncing || isLoggingOut

    BackHandler(enabled = isSyncing || isLoggingOut) {
        // Во время синхронизации и выхода блокируем навигацию.
    }

    LaunchedEffect(sessionState) {
        if (sessionState == SessionState.AUTHENTICATED) {
            isLoadingUser = true
            errorMessage = null

            try {
                val response = RetrofitClient.api.getCurrentUser()

                if (!response.isSuccessful) {
                    throw IllegalStateException(
                        "Не удалось загрузить профиль: HTTP ${response.code()}",
                    )
                }

                val user = response.body()
                    ?: throw IllegalStateException(
                        "Сервер не вернул данные пользователя",
                    )

                username = user.username
                email = user.email
            } catch (t: Throwable) {
                errorMessage = t.message ?: "Не удалось загрузить профиль"
            } finally {
                isLoadingUser = false
            }
        } else {
            username = ""
            email = ""
            errorMessage = null
            infoMessage = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Профиль")
                },
                navigationIcon = {
                    TextButton(
                        onClick = onBack,
                        enabled = !isBusy,
                    ) {
                        Text("Назад")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.Top,
        ) {
            if (!isAuthenticated) {
                Text(
                    text = "Вы используете приложение без аккаунта",
                    style = MaterialTheme.typography.headlineSmall,
                )

                Spacer(Modifier.height(12.dp))

                Text(
                    text = "Ваши аудиозаписи и расшифровки сохраняются " +
                        "локально на этом устройстве. Войдите или " +
                        "зарегистрируйтесь, чтобы привязать данные к аккаунту " +
                        "и синхронизировать их с сервером.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(28.dp))

                Button(
                    onClick = onLogin,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Войти")
                }

                Spacer(Modifier.height(12.dp))

                OutlinedButton(
                    onClick = onRegister,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Зарегистрироваться")
                }
            } else {
                Text(
                    text = "Управление аккаунтом",
                    style = MaterialTheme.typography.headlineSmall,
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text = "Изменяйте данные профиля и синхронизируйте " +
                        "аудиозаписи с сервером.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(24.dp))

                OutlinedTextField(
                    value = username,
                    onValueChange = {
                        username = it
                        errorMessage = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = {
                        Text("Имя пользователя")
                    },
                    singleLine = true,
                    enabled = !isBusy,
                )

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = email,
                    onValueChange = {
                        email = it
                        errorMessage = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = {
                        Text("Электронная почта")
                    },
                    singleLine = true,
                    enabled = !isBusy,
                )

                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = {
                        scope.launch {
                            isSaving = true
                            errorMessage = null
                            infoMessage = null

                            try {
                                require(username.isNotBlank()) {
                                    "Введите имя пользователя"
                                }
                                require(email.isNotBlank()) {
                                    "Введите электронную почту"
                                }

                                val response = RetrofitClient.api.updateCurrentUser(
                                    UserUpdateRequestDto(
                                        username = username.trim(),
                                        email = email.trim(),
                                    ),
                                )

                                if (!response.isSuccessful) {
                                    throw IllegalStateException(
                                        "Не удалось обновить профиль: " +
                                            "HTTP ${response.code()}",
                                    )
                                }

                                val updatedUser = response.body()
                                    ?: throw IllegalStateException(
                                        "Сервер не вернул обновлённый профиль",
                                    )

                                username = updatedUser.username
                                email = updatedUser.email
                                infoMessage = "Данные аккаунта обновлены"
                            } catch (t: Throwable) {
                                errorMessage = t.message
                                    ?: "Не удалось сохранить изменения"
                            } finally {
                                isSaving = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isBusy,
                ) {
                    Text(
                        if (isSaving) "Сохранение..." else "Сохранить изменения",
                    )
                }

                Spacer(Modifier.height(28.dp))

                Text(
                    text = "Синхронизация",
                    style = MaterialTheme.typography.titleLarge,
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text = "Сначала на сервер отправятся ваши локальные " +
                        "изменения, затем приложение получит актуальные " +
                        "аудиозаписи и расшифровки с сервера.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(12.dp))

                Button(
                    onClick = {
                        scope.launch {
                            isSyncing = true
                            errorMessage = null
                            infoMessage = null

                            try {
                                syncManager.sync()
                                infoMessage = "Синхронизация успешно завершена"
                            } catch (t: Throwable) {
                                errorMessage = t.message
                                    ?: "Не удалось выполнить синхронизацию"
                            } finally {
                                isSyncing = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isBusy,
                ) {
                    Text("Синхронизировать")
                }

                Spacer(Modifier.height(28.dp))

                OutlinedButton(
                    onClick = {
                        scope.launch {
                            isLoggingOut = true
                            errorMessage = null

                            try {
                                authRepository.logout()
                            } catch (t: Throwable) {
                                errorMessage = t.message
                                    ?: "Не удалось выйти из аккаунта"
                            } finally {
                                isLoggingOut = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isBusy,
                ) {
                    Text("Выйти из аккаунта")
                }

                Spacer(Modifier.height(12.dp))

                TextButton(
                    onClick = {
                        showDeleteAccountDialog = true
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    enabled = !isBusy,
                ) {
                    Text(
                        text = "Удалить аккаунт",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            infoMessage?.let {
                Spacer(Modifier.height(16.dp))

                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            errorMessage?.let {
                Spacer(Modifier.height(16.dp))

                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }

    if (showDeleteAccountDialog) {
        AlertDialog(
            onDismissRequest = {
                showDeleteAccountDialog = false
            },
            title = {
                Text("Удалить аккаунт?")
            },
            text = {
                Text(
                    "Это действие удалит аккаунт на сервере. " +
                        "Локальные данные приложения также будут очищены. " +
                        "Восстановить аккаунт этим действием нельзя.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteAccountDialog = false

                        scope.launch {
                            isLoggingOut = true
                            errorMessage = null

                            try {
                                val response = RetrofitClient.api.deleteCurrentUser()

                                if (!response.isSuccessful) {
                                    throw IllegalStateException(
                                        "Не удалось удалить аккаунт: " +
                                            "HTTP ${response.code()}",
                                    )
                                }

                                authRepository.logout()
                            } catch (t: Throwable) {
                                errorMessage = t.message
                                    ?: "Не удалось удалить аккаунт"
                            } finally {
                                isLoggingOut = false
                            }
                        }
                    },
                ) {
                    Text(
                        "Удалить",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDeleteAccountDialog = false
                    },
                ) {
                    Text("Отмена")
                }
            },
        )
    }

    if (isSyncing || isLoggingOut) {
        Dialog(
            onDismissRequest = {
                // Диалог нельзя закрыть во время операции.
            },
        ) {
            androidx.compose.material3.Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column(
                    modifier = Modifier.padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator()

                    Spacer(Modifier.height(18.dp))

                    Text(
                        text = when {
                            isSyncing -> "Синхронизация данных..."
                            else -> "Выход из аккаунта..."
                        },
                        style = MaterialTheme.typography.bodyLarge,
                    )

                    Spacer(Modifier.height(6.dp))

                    Text(
                        text = "Пожалуйста, подождите",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}