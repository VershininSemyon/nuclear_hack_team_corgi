package com.example.baseapp.transcriber

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TranscriptionResultBuilderTest {

    @Test
    fun `all words go to a single speaker segment`() {
        val words = listOf(
            WordTimestamp("Привет", 0L, 500L, "Спикер 1"),
            WordTimestamp("мир", 500L, 900L, "Спикер 1"),
        )

        val result = TranscriptionResultBuilder.build("Привет мир", words)

        assertEquals("Привет мир", result.text)
        assertEquals("ru", result.language)
        assertEquals(1, result.segments.size)
        assertEquals("Спикер 1", result.segments.single().speaker)
        assertEquals(0L, result.segments.single().startMs)
        assertEquals(900L, result.segments.single().endMs)
        assertEquals(words, result.segments.single().words)
    }

    @Test
    fun `empty result does not produce segments`() {
        val result = TranscriptionResultBuilder.build("", emptyList())

        assertEquals("", result.text)
        assertTrue(result.words.isEmpty())
        assertTrue(result.segments.isEmpty())
        assertEquals("ru", result.language)
    }

    @Test
    fun `text falls back to words when model returned no text`() {
        val words = listOf(WordTimestamp("тест", 100L, 400L))

        val result = TranscriptionResultBuilder.build("   ", words)

        assertEquals("тест", result.text)
        assertEquals(1, result.segments.size)
    }

    @Test
    fun `text is trimmed`() {
        val result = TranscriptionResultBuilder.build("  привет  ", emptyList())
        assertEquals("привет", result.text)
    }
}
