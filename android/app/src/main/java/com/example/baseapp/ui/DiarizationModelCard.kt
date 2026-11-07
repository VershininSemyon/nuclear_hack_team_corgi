
package com.example.baseapp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.baseapp.transcriber.model.DiarizationModelStatus

@Composable
fun DiarizationModelCard(
    status: DiarizationModelStatus,
    onInstall: () -> Unit,
    onRetry: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = when (status) {
                is DiarizationModelStatus.Installed ->
                    MaterialTheme.colorScheme.secondaryContainer

                is DiarizationModelStatus.Error ->
                    MaterialTheme.colorScheme.errorContainer

                else -> MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Text(
                text = "Диаризация",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )

            Spacer(Modifier.height(6.dp))

            when (status) {
                is DiarizationModelStatus.Installed -> {
                    Text(
                        text = "Готова",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                is DiarizationModelStatus.Missing -> {
                    Text(
                        text = "Скачайте модели спикеров.",
                        style = MaterialTheme.typography.bodySmall,
                    )

                    Spacer(Modifier.height(8.dp))

                    Button(onClick = onInstall) {
                        Text("Скачать")
                    }
                }

                is DiarizationModelStatus.Working -> {
                    Text(
                        text = status.message,
                        style = MaterialTheme.typography.bodySmall,
                    )

                    Spacer(Modifier.height(8.dp))

                    if (status.progress == null || status.progress <= 0f) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        LinearProgressIndicator(
                            progress = { status.progress.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                        )

                        Spacer(Modifier.height(4.dp))

                        Text(
                            text = "${(status.progress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }

                is DiarizationModelStatus.Error -> {
                    Text(
                        text = status.message,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                    )

                    Spacer(Modifier.height(10.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(onClick = onRetry) {
                            Text("Повторить")
                        }

                        OutlinedButton(onClick = onRetry) {
                            Text("Проверить")
                        }
                    }
                }
            }
        }
    }
}
