
package com.example.baseapp.data

import com.example.baseapp.audio.AudioFormat
import com.example.baseapp.transcriber.TranscriptSegment
import com.example.baseapp.transcriber.WordTimestamp

data class StoredTranscript(
    val id: Long,
    val title: String,
    val text: String,
    val originalText: String,
    val audioFileName: String,
    val audioFormat: AudioFormat,
    val createdAt: Long,
    val updatedAt: Long,
    val language: String,
    val words: List<WordTimestamp>,
    val segments: List<TranscriptSegment>,
    val speakers: List<SpeakerProfile>,
    val isEdited: Boolean,
) {
    val hasTimestamps: Boolean
        get() = words.isNotEmpty()

    val timestampsMatchCurrentText: Boolean
        get() = !isEdited && text == originalText
}

data class TranscriptSummary(
    val id: Long,
    val title: String,
    val snippet: String,
    val audioFileName: String,
    val audioFormat: AudioFormat,
    val createdAt: Long,
    val updatedAt: Long,
    val isEdited: Boolean,
)
