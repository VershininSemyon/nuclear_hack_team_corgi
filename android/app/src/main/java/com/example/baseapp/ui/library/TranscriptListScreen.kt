
package com.example.baseapp.ui.library

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.baseapp.BaseApp
import kotlinx.coroutines.launch

@Composable
fun TranscriptListScreen(
    onTranscriptClick: (Long) -> Unit,
    onNewTranscriptClick: () -> Unit,
) {
    val app = androidx.compose.ui.platform.LocalContext.current.applicationContext as BaseApp
    val viewModel: TranscriptListViewModel = viewModel(
        factory = TranscriptListViewModel.factory(
            app.container.transcriptRepository,
            app.container.authRepository,
            app.container.syncManager,
        ),
    )
    val state by viewModel.uiState.collectAsState()
    var showLoginDialog by remember { mutableStateOf(false) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = onNewTranscriptClick) {
                Text("+")
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Мои расшифровки", style = MaterialTheme.typography.headlineSmall)

                if (state.isLoggedIn) {
                    TextButton(onClick = { viewModel.logout() }) {
                        Text("Выйти")
                    }
                } else {
                    TextButton(onClick = { showLoginDialog = true }) {
                        Text("Войти")
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (state.isLoggedIn) {
                Button(
                    onClick = { viewModel.sync() },
                    enabled = !state.isSyncing,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Синхронизация...")
                    } else {
                        Text("Синхронизировать")
                    }
                }
            } else {
                Text(
                    "Гостевой режим: данные хранятся на устройстве.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            state.syncMessage?.let { message ->
                Text(
                    text = message,
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (state.transcripts.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Пока нет расшифровок")
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.transcripts) { transcript ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { onTranscriptClick(transcript.id) },
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = transcript.title,
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                    Text(
                                        text = transcript.audioFileName,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                                TextButton(
                                    onClick = { viewModel.delete(transcript.id) },
                                ) {
                                    Text("Удалить")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showLoginDialog) {
        AlertDialog(
            onDismissRequest = { showLoginDialog = false },
            title = { Text("Вход") },
            text = {
                Column {
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Имя пользователя") },
                        singleLine = true,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Пароль") },
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.login(username, password)
                        showLoginDialog = false
                    },
                ) {
                    Text("Войти")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLoginDialog = false }) {
                    Text("Отмена")
                }
            },
        )
    }
}
