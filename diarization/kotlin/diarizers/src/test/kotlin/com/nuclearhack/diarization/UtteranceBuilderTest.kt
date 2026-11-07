package com.nuclearhack.diarization

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UtteranceBuilderTest {

    private val labels = mapOf(0 to "speaker_1", 1 to "speaker_2")

    private fun word(word: String, start: Float, end: Float, speaker: Int) =
        SpeakerWord(word, start, end, speaker)

    @Test
    fun splitsOnSpeakerChange() {
        val words = listOf(
            word("привет", 0f, 0.5f, 0),
            word("мир", 0.6f, 1.0f, 0),
            word("как", 1.1f, 1.3f, 1),
            word("дела", 1.4f, 1.8f, 1),
        )
        val result = UtteranceBuilder.build(words, labels)
        assertEquals(2, result.size)
        assertEquals("speaker_1", result[0].speakerId)
        assertEquals("привет мир", result[0].text)
        assertEquals(0L, result[0].startMs)
        assertEquals(1000L, result[0].endMs)
        assertEquals("speaker_2", result[1].speakerId)
        assertEquals("как дела", result[1].text)
    }

    @Test
    fun splitsOnLongGap() {
        val words = listOf(
            word("первая", 0f, 0.5f, 0),
            word("фраза", 0.6f, 1.0f, 0),
            word("вторая", 3.0f, 3.4f, 0),
        )
        val result = UtteranceBuilder.build(words, labels, maxGapSeconds = 1.2f)
        assertEquals(2, result.size)
        assertEquals("первая фраза", result[0].text)
        assertEquals("вторая", result[1].text)
    }

    @Test
    fun splitsOnMaxDuration() {
        val words = listOf(
            word("а", 0f, 4f, 0),
            word("б", 4.1f, 8f, 0),
            word("в", 8.1f, 12f, 0),
        )
        val result = UtteranceBuilder.build(words, labels, maxUtteranceSeconds = 10f)
        assertEquals(2, result.size)
        assertEquals("а б", result[0].text)
        assertEquals("в", result[1].text)
    }

    @Test
    fun usesProvidedLabelOrDefault() {
        val words = listOf(word("текст", 0f, 1f, 7))
        val result = UtteranceBuilder.build(words, emptyMap())
        assertEquals("speaker_8", result.single().speakerId)
    }

    @Test
    fun skipsBlankWordsAndSortsByTime() {
        val words = listOf(
            word("второе", 2f, 2.5f, 0),
            word("   ", 1f, 1.2f, 0),
            word("первое", 0f, 0.5f, 0),
        )
        val result = UtteranceBuilder.build(words, labels, maxGapSeconds = 10f)
        assertEquals("первое второе", result.single().text)
    }

    @Test
    fun emptyInputReturnsEmpty() {
        assertTrue(UtteranceBuilder.build(emptyList(), labels).isEmpty())
    }
}
