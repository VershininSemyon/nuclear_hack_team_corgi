package com.nuclearhack.diarization

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeakerCountSweepTest {

    @Test
    fun picksMostFrequentCount() {
        val counts = intArrayOf(2, 3, 4, 4, 4, 3, 2)
        val index = SpeakerCountSweep.choose(counts)
        assertEquals(4, counts[index])
        assertTrue(index in 2..4)
    }

    @Test
    fun picksMiddleOfPlateau() {
        val counts = intArrayOf(3, 3, 3, 2, 1)
        val index = SpeakerCountSweep.choose(counts)
        assertEquals(3, counts[index])
        assertEquals(1, index)
    }

    @Test
    fun singleRunIsReturnedAsIs() {
        assertEquals(0, SpeakerCountSweep.choose(intArrayOf(5)))
    }

    @Test
    fun tieFallsBackToMedianCount() {
        val counts = intArrayOf(1, 2, 3, 4)
        val index = SpeakerCountSweep.choose(counts)
        assertEquals(2, counts[index])
        assertEquals(1, index)
    }

    @Test
    fun plateauWithTwoCountsPrefersMedianCount() {
        val counts = intArrayOf(5, 5, 4, 4, 2)
        val index = SpeakerCountSweep.choose(counts)
        assertEquals(4, counts[index])
    }

    @Test
    fun describeContainsThresholdsAndResult() {
        val text = SpeakerCountSweep.describe(
            floatArrayOf(0.4f, 0.5f, 0.6f),
            intArrayOf(2, 3, 3),
        )
        assertTrue(text.contains("0.40=2"))
        assertTrue(text.contains("K=3"))
    }
}
