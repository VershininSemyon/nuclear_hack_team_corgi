package com.example.baseapp.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun formatDateTime(timestampMs: Long): String =
    SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(timestampMs))

fun formatDurationLabel(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%02d:%02d", minutes, seconds)
}
