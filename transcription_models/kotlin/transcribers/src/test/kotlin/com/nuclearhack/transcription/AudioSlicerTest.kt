package com.nuclearhack.transcription

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioSlicerTest {

    private val sampleRate = 16_000

    @Test
    fun slicesSingleRangeAndRoundsOutward() {
        val samples = FloatArray(sampleRate) { it.toFloat() }

        val slice = AudioSlicer.slice(
            samples = samples,
            sampleRate = sampleRate,
            range = TimeRange(0.25f, 0.75f),
        )

        assertEquals(0.25f, slice.start, 0.0001f)
        assertEquals(0.75f, slice.end, 0.0001f)
        assertEquals(8_000, slice.samples.size)
        assertEquals(4_000f, slice.samples.first(), 0.0001f)
        assertEquals(11_999f, slice.samples.last(), 0.0001f)
        assertEquals(0.5f, slice.duration, 0.0001f)
    }

    @Test
    fun clampsRangeToFileBounds() {
        val samples = FloatArray(sampleRate) { 0.1f }

        val slice = AudioSlicer.slice(
            samples = samples,
            sampleRate = sampleRate,
            range = TimeRange(-1.0f, 2.0f),
        )

        assertEquals(0.0f, slice.start, 0.0001f)
        assertEquals(1.0f, slice.end, 0.0001f)
        assertEquals(sampleRate, slice.samples.size)
    }

    @Test
    fun reversedMarksAreNormalized() {
        val samples = FloatArray(sampleRate)

        val slice = AudioSlicer.slice(
            samples = samples,
            sampleRate = sampleRate,
            range = TimeRange(0.75f, 0.25f),
        )

        assertEquals(0.25f, slice.start, 0.0001f)
        assertEquals(0.75f, slice.end, 0.0001f)
    }

    @Test
    fun degenerateRangeReturnsEmptySlice() {
        val samples = FloatArray(sampleRate)

        val slice = AudioSlicer.slice(
            samples = samples,
            sampleRate = sampleRate,
            range = TimeRange(0.5f, 0.5f),
        )

        assertTrue(slice.samples.isEmpty())
        assertEquals(0.5f, slice.start, 0.0001f)
        assertEquals(0.5f, slice.end, 0.0001f)
    }

    @Test
    fun outOfRangeReturnsEmptySliceAtEnd() {
        val samples = FloatArray(sampleRate)

        val slice = AudioSlicer.slice(
            samples = samples,
            sampleRate = sampleRate,
            range = TimeRange(2.0f, 3.0f),
        )

        assertTrue(slice.samples.isEmpty())
        assertEquals(1.0f, slice.start, 0.0001f)
        assertEquals(1.0f, slice.end, 0.0001f)
    }

    @Test
    fun multipleRangesKeepOrderAndCount() {
        val samples = FloatArray(sampleRate * 2) { it.toFloat() }
        val ranges = listOf(
            TimeRange(0.0f, 0.2f),
            TimeRange(0.5f, 0.6f),
            TimeRange(1.5f, 1.7f),
        )

        val slices = AudioSlicer.slice(samples, sampleRate, ranges)

        assertEquals(ranges.size, slices.size)
        assertEquals(0.0f, slices[0].start, 0.0001f)
        assertEquals(0.5f, slices[1].start, 0.0001f)
        assertEquals(1.5f, slices[2].start, 0.0001f)
        assertTrue(slices.all { it.samples.isNotEmpty() })
    }

    @Test
    fun sliceWordsReturnsOneChunkPerWord() {
        val samples = FloatArray(sampleRate * 5) { 0.1f }
        val words = listOf(
            WordTimestamp("привет", 0.1f, 0.4f),
            WordTimestamp("мир", 1.0f, 1.2f),
        )

        val slices = AudioSlicer.sliceWords(samples, sampleRate, words)

        assertEquals(words.size, slices.size)
        assertEquals(0.1f, slices[0].start, 0.0001f)
        assertEquals(0.4f, slices[0].end, 0.0001f)
        assertEquals(1.0f, slices[1].start, 0.0001f)
        assertEquals(1.2f, slices[1].end, 0.0001f)
        assertEquals(4_800, slices[0].samples.size)
        assertEquals(3_200, slices[1].samples.size)
    }

    @Test
    fun timeRangesMapsWordsToTwoMarks() {
        val result = TranscriptionResult(
            text = "привет мир",
            words = listOf(
                WordTimestamp("привет", 0.1f, 0.4f),
                WordTimestamp("мир", 1.0f, 1.2f),
            ),
        )

        val ranges = AudioSlicer.timeRanges(result)

        assertEquals(listOf(TimeRange(0.1f, 0.4f), TimeRange(1.0f, 1.2f)), ranges)
    }
}
