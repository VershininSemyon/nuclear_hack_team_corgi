package com.example.baseapp.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.baseapp.appContainer
import com.example.baseapp.data.TranscriptSummary
import com.example.baseapp.ui.formatDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranscriptListScreen(
    onBack: () -> Unit,
    onOpenTranscript: (Long) -> Unit,
) {
    val container = LocalContext.current.appContainer
    val viewModel: TranscriptListViewModel = viewModel(
        factory = TranscriptListViewModel.factory(container.transcriptRepository),
    )
    val transcripts by viewModel.transcripts.collectAsStateWithLifecycle()
    var pendingDelete by remember { mutableStateOf<TranscriptSummary?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Мои расшифровки") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Назад") }
                },
            )
        },
    ) { innerPadding ->
        if (transcripts.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Пока нет сохранённых расшифровок",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Распознайте аудио и нажмите «Сохранить расшифровку».",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(16.dp))
                TextButton(onClick = onBack) { Text("Вернуться к распознаванию") }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(transcripts, key = { it.id }) { transcript ->
                    TranscriptCard(
                        transcript = transcript,
                        onOpen = { onOpenTranscript(transcript.id) },
                        onDelete = { pendingDelete = transcript },
                    )
                }
            }
        }
    }

    pendingDelete?.let { transcript ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Удалить расшифровку?") },
            text = {
                Text(
                    "«${transcript.title}» будет удалена вместе с текстом, " +
                        "словами, таймкодами и репликами. Действие нельзя отменить.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.delete(transcript.id)
                        pendingDelete = null
                    },
                ) {
                    Text("Удалить", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Отмена") }
            },
        )
    }
}

@Composable
private fun TranscriptCard(
    transcript: TranscriptSummary,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = transcript.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = transcript.audioFormat.displayName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = transcript.snippet.ifBlank { "Текст расшифровки пуст" },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Создано: ${formatDateTime(transcript.createdAt)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (transcript.updatedAt != transcript.createdAt) {
                Text(
                    text = "Изменено: ${formatDateTime(transcript.updatedAt)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (transcript.isEdited) {
                Text(
                    text = "Текст исправлен вручную",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDelete) {
                    Text("Удалить", color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = onOpen) { Text("Открыть") }
            }
        }
    }
}
