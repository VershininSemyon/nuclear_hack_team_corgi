package com.example.baseapp.transcriber.sherpa

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WordTimestampsTest {

    @Test
    fun `empty token stream produces no words`() {
        assertTrue(
            WordTimestamps.fromTokens(
                tokens = emptyArray(),
                timestamps = FloatArray(0),
                durations = FloatArray(0),
                chunkDuration = 10f,
                offset = 0f,
            ).isEmpty(),
        )
    }

    @Test
    fun `sentencepiece tokens are joined into words`() {
        val words = WordTimestamps.fromTokens(
            tokens = arrayOf("\u2581Привет", "\u2581мир", "!"),
            timestamps = floatArrayOf(0.1f, 0.6f, 0.9f),
            durations = floatArrayOf(0.4f, 0.3f, 0.05f),
            chunkDuration = 1.2f,
            offset = 2f,
            speaker = "Спикер 1",
        )

        assertEquals(2, words.size)
        assertEquals("Привет", words[0].word)
        assertEquals(2_100L, words[0].startMs)
        assertEquals(2_500L, words[0].endMs)
        assertEquals("мир!", words[1].word)
        assertEquals(2_600L, words[1].startMs)
        assertEquals(2_950L, words[1].endMs)
        assertEquals("Спикер 1", words[0].speaker)
    }

    @Test
    fun `timestamps are converted to milliseconds without losing precision`() {
        val words = WordTimestamps.fromTokens(
            tokens = arrayOf("тест"),
            timestamps = floatArrayOf(1.2345f),
            durations = floatArrayOf(0.5f),
            chunkDuration = 5f,
            offset = 0f,
        )

        assertEquals(1, words.size)
        // (1.2345 * 1000).roundToLong() == 1235
        assertEquals(1_235L, words[0].startMs)
        assertEquals(1_735L, words[0].endMs)
    }

    @Test
    fun `missing timestamps are distributed over the chunk`() {
        val words = WordTimestamps.fromTokens(
            tokens = arrayOf("\u2581один", "\u2581два"),
            timestamps = floatArrayOf(0f, 0f),
            durations = floatArrayOf(0f, 0f),
            chunkDuration = 2f,
            offset = 10f,
        )

        assertEquals(2, words.size)
        assertEquals(10_000L, words[0].startMs)
        assertEquals(11_143L, words[1].startMs)
        assertEquals(11_323L, words[1].endMs)
    }

    @Test
    fun `special tokens are ignored`() {
        val words = WordTimestamps.fromTokens(
            tokens = arrayOf("<s>", "\u2581да", "</s>", "<blk>"),
            timestamps = floatArrayOf(0f, 0.2f, 0.4f, 0.5f),
            durations = floatArrayOf(0f, 0.2f, 0f, 0f),
            chunkDuration = 1f,
            offset = 0f,
        )

        assertEquals(listOf("да"), words.map { it.word })
    }

    @Test
    fun `punctuation is attached to the previous word`() {
        val words = WordTimestamps.fromTokens(
            tokens = arrayOf("\u2581Привет", ",", "\u2581как", "\u2581дела", "?"),
            timestamps = floatArrayOf(0f, 0.3f, 0.4f, 0.7f, 1f),
            durations = floatArrayOf(0.3f, 0.05f, 0.3f, 0.25f, 0.05f),
            chunkDuration = 1.2f,
            offset = 0f,
        )

        assertEquals(listOf("Привет,", "как", "дела?"), words.map { it.word })
    }

    @Test
    fun `end time never goes backwards`() {
        val words = WordTimestamps.fromTokens(
            tokens = arrayOf("\u2581а", "\u2581б"),
            timestamps = floatArrayOf(2f, 1f),
            durations = floatArrayOf(0f, 0f),
            chunkDuration = 3f,
            offset = 0f,
        )

        words.forEach { word ->
            assertTrue(word.startMs >= 0L)
            assertTrue(word.endMs >= word.startMs)
            assertTrue(word.endMs <= 3_000L)
        }
    }
}
