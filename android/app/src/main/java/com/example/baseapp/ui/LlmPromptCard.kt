package com.example.baseapp.ui

import com.example.baseapp.i18n.tr

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.baseapp.transcriber.LlmPromptStore

/**
 * Карточка на главном экране: показывает текущий системный промпт LLM
 * и открывает диалог редактирования.
 */
@Composable
fun LlmPromptCard(
    prompt: String,
    onSave: (String) -> Unit,
    onReset: () -> Unit,
) {
    var showDialog by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
        ) {
            Text(
                text = tr("Промпт LLM", "LLM prompt"),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = prompt,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = tr("Сбросить к стандартному", "Reset to default"),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(onClick = onReset)
                        .padding(vertical = 4.dp),
                )

                Spacer(Modifier.weight(1f))

                OutlinedButton(onClick = { showDialog = true }) {
                    Text(tr("Изменить", "Edit"))
                }
            }
        }
    }

    if (showDialog) {
        var draft by remember(prompt) { mutableStateOf(prompt) }

        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(tr("Системный промпт", "System prompt")) },
            text = {
                Column {
                    Text(
                        text = tr("Инструкция, с которой локальная LLM правит каждую ", "Instruction the local LLM uses to fix each ") +
                            tr("реплику. Слова и тайминги не меняются.", "utterance. Words and timings stay unchanged."),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Spacer(Modifier.height(10.dp))

                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp),
                        textStyle = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onSave(draft)
                        showDialog = false
                    },
                ) {
                    Text(tr("Сохранить", "Save"))
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            onReset()
                            showDialog = false
                        },
                    ) {
                        Text(tr("Сбросить", "Reset"))
                    }
                    TextButton(onClick = { showDialog = false }) {
                        Text(tr("Отмена", "Cancel"))
                    }
                }
            },
        )
    }
}
