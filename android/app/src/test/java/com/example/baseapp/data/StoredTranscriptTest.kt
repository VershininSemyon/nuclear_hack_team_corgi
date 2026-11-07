package com.example.baseapp.data

import com.example.baseapp.audio.AudioFormat
import com.example.baseapp.transcriber.TranscriptSegment
import com.example.baseapp.transcriber.WordTimestamp
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StoredTranscriptTest {

    private fun transcript(
        text: String = "привет",
        originalText: String = "привет",
        words: List<WordTimestamp> = emptyList(),
        isEdited: Boolean = false,
    ) = StoredTranscript(
        id = 1L,
        title = "Запись",
        text = text,
        originalText = originalText,
        audioFileName = "audio.wav",
        audioFormat = AudioFormat.WAV,
        createdAt = 1L,
        updatedAt = 2L,
        language = "ru",
        words = words,
        segments = listOf(
            TranscriptSegment("speaker_1", 0L, 1000L, text),
        ),
        speakers = emptyList(),
        isEdited = isEdited,
    )

    @Test
    fun `hasTimestamps reflects words`() {
        assertFalse(transcript().hasTimestamps)
        assertTrue(
            transcript(
                words = listOf(WordTimestamp("привет", 0L, 500L)),
            ).hasTimestamps,
        )
    }

    @Test
    fun `timestamps match only untouched text`() {
        assertTrue(transcript().timestampsMatchCurrentText)
        assertFalse(transcript(text = "привет!", originalText = "привет").timestampsMatchCurrentText)
        assertFalse(transcript(isEdited = true).timestampsMatchCurrentText)
    }
}
