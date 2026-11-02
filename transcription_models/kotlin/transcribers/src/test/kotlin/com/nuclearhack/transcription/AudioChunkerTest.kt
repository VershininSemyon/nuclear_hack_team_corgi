package com.nuclearhack.transcription

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class AudioChunkerTest {

    @Test
    fun emptyAudioReturnsNoChunks() {
        assertTrue(AudioChunker.split(FloatArray(0), 16_000, 25f).isEmpty())
    }

    @Test
    fun shortAudioReturnsSingleChunk() {
        val samples = FloatArray(16_000) { 0.1f }

        val chunks = AudioChunker.split(samples, 16_000, 25f)

        assertEquals(1, chunks.size)
        assertEquals(0.0f, chunks[0].offsetSeconds, 0.0001f)
        assertEquals(1.0f, chunks[0].durationSeconds, 0.0001f)
        assertEquals(samples.size, chunks[0].samples.size)
    }

    @Test
    fun silentAudioReturnsNoChunks() {
        assertTrue(AudioChunker.split(FloatArray(32_000), 16_000, 25f).isEmpty())
    }

    @Test
    fun longAudioIsSplitWithinLimitAndKeepsOffsets() {
        val sampleRate = 1_000
        val samples = FloatArray(2_500) { 0.1f }

        val chunks = AudioChunker.split(samples, sampleRate, maxSeconds = 1f)

        assertTrue(chunks.isNotEmpty())
        var expectedOffset = 0.0f
        var total = 0
        for (chunk in chunks) {
            assertTrue(chunk.samples.isNotEmpty())
            assertTrue(chunk.durationSeconds <= 1.0f + 0.0001f)
            assertEquals(expectedOffset, chunk.offsetSeconds, 0.0001f)
            expectedOffset += chunk.durationSeconds
            total += chunk.samples.size
        }
        assertEquals(samples.size, total)
        assertEquals(2.5f, expectedOffset, 0.0001f)
    }

    @Test
    fun silentGapIsSkippedButOffsetsStayCorrect() {
        val sampleRate = 1_000
        val samples = FloatArray(3_000) { index ->
            if (index in 1_000 until 2_000) 0.0f else 0.1f
        }

        val chunks = AudioChunker.split(samples, sampleRate, maxSeconds = 1f)

        assertTrue(chunks.isNotEmpty())
        assertTrue(chunks.all { chunk -> chunk.samples.any { abs(it) > 0.01f } })
        assertTrue(chunks.any { chunk -> abs(chunk.offsetSeconds - 2.0f) < 0.001f })
        for (chunk in chunks) {
            assertTrue(chunk.durationSeconds <= 1.0f + 0.0001f)
        }
    }

    @Test
    fun tinyMaxSecondsDoesNotCrash() {
        val sampleRate = 1_000
        val samples = FloatArray(100) { 0.1f }

        val chunks = AudioChunker.split(samples, sampleRate, maxSeconds = 0.005f)

        assertTrue(chunks.isNotEmpty())
        assertTrue(chunks.all { it.samples.size <= 5 })
        assertEquals(samples.size, chunks.sumOf { it.samples.size })
    }

    @Test
    fun continuousAudioKeepsAllSamples() {
        val sampleRate = 16_000
        val samples = FloatArray(sampleRate * 7) { index ->
            if (index % 2 == 0) 0.1f else -0.1f
        }

        val chunks = AudioChunker.split(samples, sampleRate, maxSeconds = 1f)

        assertEquals(samples.size, chunks.sumOf { it.samples.size })
        assertTrue(chunks.all { it.durationSeconds <= 1.0f + 0.0001f })
        assertEquals(0.0f, chunks.first().offsetSeconds, 0.0001f)
        assertEquals(7.0f, chunks.last().offsetSeconds + chunks.last().durationSeconds, 0.0001f)
    }
}
