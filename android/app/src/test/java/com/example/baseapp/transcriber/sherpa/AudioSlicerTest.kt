package com.example.baseapp.transcriber.sherpa

import com.example.baseapp.transcriber.WordTimestamp
import org.junit.Assert.assertEquals
import org.junit.Test

class AudioSlicerTest {

    private val sampleRate = 16_000

    @Test
    fun `slice extracts requested range`() {
        val samples = FloatArray(sampleRate * 4) { it / (sampleRate * 4f) }
        val slice = AudioSlicer.slice(
            samples = samples,
            sampleRate = sampleRate,
            range = TimeRange(start = 1f, end = 2.5f),
        )

        assertEquals(1f, slice.start, 0.0001f)
        assertEquals(2.5f, slice.end, 0.0001f)
        assertEquals((1.5f * sampleRate).toInt(), slice.samples.size)
        assertEquals(samples[sampleRate], slice.samples.first())
        assertEquals(samples[(2.5f * sampleRate).toInt() - 1], slice.samples.last())
    }

    @Test
    fun `range is clamped to audio bounds`() {
        val samples = FloatArray(sampleRate)
        val slice = AudioSlicer.slice(samples, sampleRate, TimeRange(-2f, 5f))

        assertEquals(0f, slice.start, 0.0001f)
        assertEquals(1f, slice.end, 0.0001f)
        assertEquals(sampleRate, slice.samples.size)
    }

    @Test
    fun `reversed range is normalized`() {
        val samples = FloatArray(sampleRate * 2)
        val slice = AudioSlicer.slice(samples, sampleRate, TimeRange(1.5f, 0.5f))

        assertEquals(0.5f, slice.start, 0.0001f)
        assertEquals(1.5f, slice.end, 0.0001f)
    }

    @Test
    fun `word timestamps are converted to time ranges`() {
        val words = listOf(
            WordTimestamp("привет", 100L, 900L),
            WordTimestamp("мир", 900L, 1200L),
        )
        assertEquals(
            listOf(TimeRange(0.1f, 0.9f), TimeRange(0.9f, 1.2f)),
            AudioSlicer.timeRanges(words),
        )
    }

    @Test
    fun `sliceWords returns one slice per word`() {
        val samples = FloatArray(sampleRate * 2)
        val words = listOf(
            WordTimestamp("а", 0L, 500L),
            WordTimestamp("б", 500L, 1000L),
        )

        val slices = AudioSlicer.sliceWords(samples, sampleRate, words)

        assertEquals(2, slices.size)
        assertEquals(8_000, slices[0].samples.size)
        assertEquals(8_000, slices[1].samples.size)
    }
}
