package com.example.baseapp.transcriber.sherpa

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioChunkerTest {

    private val sampleRate = 16_000

    @Test
    fun `empty audio produces no chunks`() {
        assertTrue(AudioChunker.split(FloatArray(0), sampleRate, 25f).isEmpty())
    }

    @Test
    fun `short audio is returned as a single chunk`() {
        val samples = tone(seconds = 2f)
        val chunks = AudioChunker.split(samples, sampleRate, 25f)

        assertEquals(1, chunks.size)
        assertEquals(0f, chunks.first().offsetSeconds, 0.0001f)
        assertEquals(2f, chunks.first().durationSeconds, 0.0001f)
        assertEquals(samples.size, chunks.first().samples.size)
    }

    @Test
    fun `digital silence is not sent to the model`() {
        val chunks = AudioChunker.split(FloatArray(sampleRate * 3), sampleRate, 25f)
        assertTrue(chunks.isEmpty())
    }

    @Test
    fun `long audio is split into bounded chunks without gaps`() {
        val seconds = 61f
        val samples = tone(seconds)
        val chunks = AudioChunker.split(samples, sampleRate, 25f)

        assertTrue(chunks.size >= 3)
        var expectedStart = 0
        chunks.forEach { chunk ->
            assertTrue(chunk.samples.isNotEmpty())
            assertTrue(chunk.samples.size <= 25 * sampleRate)
            val start = (chunk.offsetSeconds * sampleRate).toInt()
            assertEquals(expectedStart, start)
            assertEquals(
                chunk.samples.size,
                (chunk.durationSeconds * sampleRate).toInt(),
            )
            expectedStart = start + chunk.samples.size
        }
        assertEquals(samples.size, expectedStart)
    }

    @Test
    fun `chunk boundary prefers a pause`() {
        // Две секунды тона, затем пауза, затем снова тон: граница 25 секунд
        // должна попасть в тихое место, а не разрезать сигнал произвольно.
        val samples = FloatArray(sampleRate * 30)
        fillTone(samples, 0, sampleRate * 20)
        fillTone(samples, sampleRate * 22, samples.size)

        val chunks = AudioChunker.split(samples, sampleRate, 25f)
        assertTrue(chunks.size >= 2)
        val boundary = (chunks.first().durationSeconds * sampleRate).toInt()
        assertTrue(
            "Граница $boundary должна попасть в паузу",
            boundary in (sampleRate * 20)..(sampleRate * 25),
        )
    }

    private fun tone(seconds: Float): FloatArray {
        val samples = FloatArray((seconds * sampleRate).toInt())
        fillTone(samples, 0, samples.size)
        return samples
    }

    private fun fillTone(samples: FloatArray, from: Int, to: Int) {
        for (i in from until to) {
            samples[i] = 0.25f * kotlin.math.sin(2.0 * Math.PI * 440.0 * i / sampleRate).toFloat()
        }
    }
}
