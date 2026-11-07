package com.example.baseapp.data

import com.example.baseapp.audio.AudioFormat
import com.example.baseapp.transcriber.TranscriptSegment
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class TranscriptJsonExporterTest {

    private val transcript = StoredTranscript(
        id = 7L,
        title = "Встреча",
        text = "Добрый день! Привет.",
        originalText = "Добрый день! Привет.",
        audioFileName = "meeting.mp3",
        audioFormat = AudioFormat.MP3,
        createdAt = 10L,
        updatedAt = 20L,
        language = "ru",
        words = emptyList(),
        segments = listOf(
            TranscriptSegment("speaker_1", 1250L, 4380L, "Добрый день!"),
            TranscriptSegment("speaker_2", 4500L, 6000L, "Привет."),
        ),
        speakers = emptyList(),
        isEdited = false,
    )

    @Test
    fun `exports required transcript schema`() {
        val root = JSONObject(TranscriptJsonExporter.export(transcript))
        val rows = root.getJSONArray("transcript")

        assertEquals(2, rows.length())
        assertEquals(1, rows.getJSONObject(0).getInt("speaker_id"))
        assertEquals(1250L, rows.getJSONObject(0).getLong("start_ms"))
        assertEquals(4380L, rows.getJSONObject(0).getLong("end_ms"))
        assertEquals("Добрый день!", rows.getJSONObject(0).getString("text"))
        assertEquals(2, rows.getJSONObject(1).getInt("speaker_id"))
    }

    @Test
    fun `empty segments produce empty transcript array`() {
        val empty = transcript.copy(segments = emptyList())
        val root = JSONObject(TranscriptJsonExporter.export(empty))
        assertEquals(0, root.getJSONArray("transcript").length())
    }
}
