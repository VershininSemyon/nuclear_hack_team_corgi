package com.example.baseapp.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.baseapp.data.repository.AuthRepository
import kotlinx.coroutines.launch

enum class AuthMode {
    LOGIN,
    REGISTER,
}

@Composable
fun AuthScreen(
    mode: AuthMode,
    authRepository: AuthRepository,
    onBack: () -> Unit,
) {
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()

    val isRegistration = mode == AuthMode.REGISTER

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = if (isRegistration) {
                "Регистрация"
            } else {
                "Вход"
            },
            style = MaterialTheme.typography.headlineLarge,
        )

        Spacer(modifier = Modifier.height(24.dp))

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
            enabled = !isLoading,
        )

        if (isRegistration) {
            Spacer(modifier = Modifier.height(12.dp))

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
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                ),
                singleLine = true,
                enabled = !isLoading,
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = password,
            onValueChange = {
                password = it
                errorMessage = null
            },
            modifier = Modifier.fillMaxWidth(),
            label = {
                Text("Пароль")
            },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
            ),
            singleLine = true,
            enabled = !isLoading,
        )

        if (isRegistration) {
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Пароль должен содержать минимум 8 символов.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        errorMessage?.let { message ->
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                errorMessage = null

                if (username.isBlank()) {
                    errorMessage = "Введите имя пользователя"
                    return@Button
                }

                if (password.isBlank()) {
                    errorMessage = "Введите пароль"
                    return@Button
                }

                if (isRegistration && email.isBlank()) {
                    errorMessage = "Введите электронную почту"
                    return@Button
                }

                if (isRegistration && password.length < 8) {
                    errorMessage = "Пароль должен содержать минимум 8 символов"
                    return@Button
                }

                scope.launch {
                    isLoading = true

                    val result = if (isRegistration) {
                        authRepository.register(
                            username = username,
                            email = email,
                            password = password,
                        )
                    } else {
                        authRepository.login(
                            username = username,
                            password = password,
                        )
                    }

                    isLoading = false

                    result
                        .onSuccess {
                            onBack()
                        }
                        .onFailure {
                            errorMessage = it.message
                                ?: "Не удалось выполнить запрос"
                        }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading,
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.height(20.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                Text(
                    if (isRegistration) {
                        "Зарегистрироваться"
                    } else {
                        "Войти"
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading,
        ) {
            Text("Назад")
        }
    }
}
