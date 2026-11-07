package com.nuclearhack.diarization

import com.nuclearhack.transcription.WordTimestamp
import org.junit.Assert.assertEquals
import org.junit.Test

class WordSpeakerAssignerTest {

    private val segments = listOf(
        DiarizationSegment(0f, 2f, speaker = 0),
        DiarizationSegment(2f, 4f, speaker = 1),
        DiarizationSegment(5f, 7f, speaker = 0),
    )

    @Test
    fun wordInsideSegmentGetsItsSpeaker() {
        val words = listOf(
            WordTimestamp("привет", 0.2f, 0.8f),
            WordTimestamp("мир", 2.5f, 3.0f),
        )
        val result = WordSpeakerAssigner.assign(words, segments)
        assertEquals(listOf(0, 1), result.map { it.speaker })
        assertEquals("привет", result[0].word)
        assertEquals(0.2f, result[0].start)
        assertEquals(0.8f, result[0].end)
    }

    @Test
    fun wordStraddlingBoundaryUsesLargerOverlap() {
        // 1.8..2.9: 0.2 с у спикера 0 и 0.9 с у спикера 1.
        val words = listOf(WordTimestamp("граница", 1.8f, 2.9f))
        val result = WordSpeakerAssigner.assign(words, segments)
        assertEquals(1, result.single().speaker)
    }

    @Test
    fun wordInGapGoesToNearestSegment() {
        val words = listOf(
            WordTimestamp("пауза", 4.2f, 4.4f), // ближе к сегменту 2..4
            WordTimestamp("другая", 4.8f, 4.9f), // ближе к сегменту 5..7
        )
        val result = WordSpeakerAssigner.assign(words, segments)
        assertEquals(1, result[0].speaker)
        assertEquals(0, result[1].speaker)
    }

    @Test
    fun zeroLengthWordAtBoundaryIsHandled() {
        val words = listOf(WordTimestamp("x", 2f, 2f))
        val result = WordSpeakerAssigner.assign(words, segments)
        org.junit.Assert.assertTrue(result.single().speaker in setOf(0, 1))
    }

    @Test
    fun emptyWordListReturnsEmpty() {
        assertEquals(
            emptyList<SpeakerWord>(),
            WordSpeakerAssigner.assign(emptyList(), segments),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun emptySegmentsFail() {
        WordSpeakerAssigner.assign(
            listOf(WordTimestamp("x", 0f, 1f)),
            emptyList(),
        )
    }
}
