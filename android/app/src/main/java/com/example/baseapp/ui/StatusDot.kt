package com.example.baseapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Индикатор состояния модели в правом верхнем углу карточки:
 * зелёный — загружена и работает, жёлтый — качается, красный — нет.
 */
@Composable
fun StatusDot(
    ready: Boolean,
    working: Boolean = false,
) {
    val color = when {
        ready -> Color(0xFF2E7D32)
        working -> Color(0xFFF2A104)
        else -> MaterialTheme.colorScheme.error
    }

    Box(
        modifier = Modifier
            .size(12.dp)
            .background(color, CircleShape),
    )
}
