
package com.example.baseapp.ui.library

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.Add
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.baseapp.appContainer
import com.example.baseapp.data.TranscriptSummary
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranscriptListScreen(
    onTranscriptClick: (Long) -> Unit,
    onNewTranscriptClick: () -> Unit,
    viewModel: TranscriptListViewModel = viewModel(factory = TranscriptListViewModel.factory(
        LocalContext.current.appContainer.transcriptRepository,
        LocalContext.current.appContainer.authRepository,
        LocalContext.current.appContainer.syncManager
    )),
) {
    val state by viewModel.uiState.collectAsState()
    var showLoginDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Мои расшифровки") },
                actions = {
                    if (state.isLoggedIn) {
                        IconButton(onClick = { viewModel.sync() }, enabled = !state.isSyncing) {
                            if (state.isSyncing) CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            else Icon(Icons.Default.Sync, contentDescription = "Синхронизировать")
                        }
                        TextButton(onClick = { viewModel.logout() }) { Text("Выйти") }
                    } else {
                        TextButton(onClick = { showLoginDialog = true }) { Text("Войти") }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNewTranscriptClick) { Icon(Icons.Default.Add, contentDescription = "Добавить") }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (state.syncMessage != null) {
                Card(modifier = Modifier.fillMaxWidth().padding(8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(state.syncMessage!!, modifier = Modifier.weight(1f))
                        TextButton(onClick = { viewModel.dismissMessage() }) { Text("OK") }
                    }
                }
            }
            
            if (state.transcripts.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Нет расшифровок") }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.transcripts.size) { index ->
                        val item = state.transcripts[index]
                        TranscriptListItem(item = item, onClick = { onTranscriptClick(item.id) }, onDelete = { viewModel.delete(item.id) })
                        if (index < state.transcripts.lastIndex) HorizontalDivider()
                    }
                }
            }
        }
    }

    if (showLoginDialog) {
        LoginDialog(onDismiss = { showLoginDialog = false }, onLogin = { u, p -> viewModel.login(u, p); showLoginDialog = false })
    }
}

@Composable
fun LoginDialog(onDismiss: () -> Unit, onLogin: (String, String) -> Unit) {
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Вход в аккаунт") },
        text = {
            Column {
                OutlinedTextField(value = user, onValueChange = { user = it }, label = { Text("Логин") }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = pass, onValueChange = { pass = it }, label = { Text("Пароль") }, singleLine = true)
            }
        },
        confirmButton = { TextButton(onClick = { if (user.isNotBlank() && pass.isNotBlank()) onLogin(user, pass) }) { Text("Войти") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

@Composable
fun TranscriptListItem(item: TranscriptSummary, onClick: () -> Unit, onDelete: () -> Unit) {
    ListItem(
        headlineContent = { Text(item.title) },
        supportingContent = { Text(item.snippet, maxLines = 2) },
        trailingContent = { TextButton(onClick = onDelete) { Text("Удалить") } },
        modifier = Modifier.clickable(onClick = onClick)
    )
}
