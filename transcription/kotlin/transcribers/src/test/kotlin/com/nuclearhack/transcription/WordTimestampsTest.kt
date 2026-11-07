package com.nuclearhack.transcription

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WordTimestampsTest {

    @Test
    fun gigaAmCharLevelSplitsWordsBySpaceToken() {
        val tokens = arrayOf("п", "р", "и", "в", "е", "т", " ", "м", "и", "р")
        val timestamps = floatArrayOf(0.1f, 0.2f, 0.3f, 0.4f, 0.5f, 0.6f, 0.7f, 0.8f, 0.9f, 1.0f)

        val words = WordTimestamps.fromTokens(
            tokens = tokens,
            timestamps = timestamps,
            durations = FloatArray(0),
            chunkDuration = 2.0f,
            offset = 0.0f,
        )

        assertEquals(2, words.size)
        assertWord(words, 0, "привет", 0.1f, 0.7f)
        assertWord(words, 1, "мир", 0.8f, 1.06f)
    }

    @Test
    fun sentencePieceMarkerStartsNewWord() {
        val words = WordTimestamps.fromTokens(
            tokens = arrayOf("\u2581привет", "\u2581мир"),
            timestamps = floatArrayOf(0.0f, 0.5f),
            durations = floatArrayOf(0.4f, 0.4f),
            chunkDuration = 1.0f,
            offset = 0.0f,
        )

        assertEquals(2, words.size)
        assertWord(words, 0, "привет", 0.0f, 0.4f)
        assertWord(words, 1, "мир", 0.5f, 0.9f)
    }

    @Test
    fun whisperLeadingSpacesAndPunctuationAreMerged() {
        val words = WordTimestamps.fromTokens(
            tokens = arrayOf(" Привет", ",", " мир", "."),
            timestamps = floatArrayOf(0.0f, 0.3f, 0.4f, 0.7f),
            durations = floatArrayOf(0.3f, 0.05f, 0.3f, 0.05f),
            chunkDuration = 1.0f,
            offset = 0.0f,
        )

        assertEquals(2, words.size)
        assertWord(words, 0, "Привет,", 0.0f, 0.35f)
        assertWord(words, 1, "мир.", 0.4f, 0.75f)
    }

    @Test
    fun offsetIsAddedToWordTimestamps() {
        val words = WordTimestamps.fromTokens(
            tokens = arrayOf("\u2581да"),
            timestamps = floatArrayOf(0.0f),
            durations = floatArrayOf(0.2f),
            chunkDuration = 1.0f,
            offset = 10.0f,
        )

        assertEquals(1, words.size)
        assertWord(words, 0, "да", 10.0f, 10.2f)
    }

    @Test
    fun specialTokensAreSkipped() {
        val words = WordTimestamps.fromTokens(
            tokens = arrayOf("<blk>", "\u2581да", "<sos/eos>"),
            timestamps = floatArrayOf(0.0f, 0.1f, 0.2f),
            durations = floatArrayOf(0.0f, 0.1f, 0.0f),
            chunkDuration = 1.0f,
            offset = 0.0f,
        )

        assertEquals(1, words.size)
        assertWord(words, 0, "да", 0.1f, 0.2f)
    }

    @Test
    fun punctuationWithoutPreviousWordIsDropped() {
        val words = WordTimestamps.fromTokens(
            tokens = arrayOf(",", "\u2581да"),
            timestamps = floatArrayOf(0.0f, 0.1f),
            durations = floatArrayOf(0.05f, 0.1f),
            chunkDuration = 1.0f,
            offset = 0.0f,
        )

        assertEquals(1, words.size)
        assertWord(words, 0, "да", 0.1f, 0.2f)
    }

    @Test
    fun multiWordTokenIsSplitWithProportionalTiming() {
        val words = WordTimestamps.fromTokens(
            tokens = arrayOf("\u2581hello\u2581world"),
            timestamps = floatArrayOf(0.0f),
            durations = floatArrayOf(0.5f),
            chunkDuration = 1.0f,
            offset = 0.0f,
        )

        assertEquals(2, words.size)
        assertWord(words, 0, "hello", 0.0f, 0.25f)
        assertWord(words, 1, "world", 0.25f, 0.5f)
    }

    @Test
    fun missingTimestampsFallBackToChunkDuration() {
        val words = WordTimestamps.fromTokens(
            tokens = arrayOf("\u2581раз", "\u2581два"),
            timestamps = FloatArray(0),
            durations = FloatArray(0),
            chunkDuration = 2.0f,
            offset = 0.0f,
        )

        assertEquals(2, words.size)
        assertEquals("раз", words[0].word)
        assertEquals("два", words[1].word)
        assertTrue(words[0].start >= 0f)
        assertTrue(words[0].end <= 2.0f)
        assertTrue(words[1].start >= words[0].end)
        assertTrue(words[1].end <= 2.0f)
        assertEquals(0.0f, words[0].start, 0.001f)
        assertEquals(1.0f, words[1].start, 0.001f)
    }

    private fun assertWord(
        words: List<WordTimestamp>,
        index: Int,
        text: String,
        start: Float,
        end: Float,
        eps: Float = 0.001f,
    ) {
        val word = words[index]
        assertEquals(text, word.word)
        assertEquals(start, word.start, eps)
        assertEquals(end, word.end, eps)
    }
}
