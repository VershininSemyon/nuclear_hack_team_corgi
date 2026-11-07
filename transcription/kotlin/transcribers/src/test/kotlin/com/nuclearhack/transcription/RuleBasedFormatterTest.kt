package com.nuclearhack.transcription

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleBasedFormatterTest {

    @Test
    fun splitsSentenceByPause() {
        val words = listOf(
            WordTimestamp("привет", 0.0f, 0.4f),
            WordTimestamp("как", 0.4f, 0.7f),
            WordTimestamp("дела", 1.5f, 1.9f),
        )

        val output = RuleBasedFormatter.format(
            source = words,
            addParagraphs = false,
            sentencePauseSeconds = 0.55f,
            paragraphPauseSeconds = 10f,
            maxSentenceWords = 24,
        )

        assertEquals(listOf("Привет", "как.", "Дела."), output.words)
        assertTrue(output.breakAfter.isEmpty())
    }

    @Test
    fun longPauseStartsParagraph() {
        val words = listOf(
            WordTimestamp("привет", 0.0f, 0.4f),
            WordTimestamp("как", 0.4f, 0.7f),
            WordTimestamp("дела", 2.5f, 2.9f),
        )

        val output = RuleBasedFormatter.format(
            source = words,
            addParagraphs = true,
            sentencePauseSeconds = 0.55f,
            paragraphPauseSeconds = 1.2f,
            maxSentenceWords = 24,
        )

        assertEquals(setOf(1), output.breakAfter)
    }

    @Test
    fun existingPunctuationStartsNewSentence() {
        val words = listOf(
            WordTimestamp("привет.", 0.0f, 0.4f),
            WordTimestamp("мир", 0.4f, 0.8f),
        )

        val output = RuleBasedFormatter.format(
            source = words,
            addParagraphs = false,
            sentencePauseSeconds = 0.55f,
            paragraphPauseSeconds = 10f,
            maxSentenceWords = 24,
        )

        assertEquals(listOf("Привет.", "Мир."), output.words)
    }

    @Test
    fun capsLongSentenceWithoutPauses() {
        val words = listOf("один", "два", "три", "четыре", "пять").mapIndexed { index, word ->
            WordTimestamp(word, index.toFloat(), index.toFloat() + 0.1f)
        }

        val output = RuleBasedFormatter.format(
            source = words,
            addParagraphs = false,
            sentencePauseSeconds = 10f,
            paragraphPauseSeconds = 10f,
            maxSentenceWords = 3,
        )

        assertEquals(listOf("Один", "два", "три.", "Четыре", "пять."), output.words)
    }

    @Test
    fun doesNotDuplicateFinalPunctuation() {
        val words = listOf(
            WordTimestamp("как", 0.0f, 0.3f),
            WordTimestamp("дела?", 0.3f, 0.7f),
        )

        val output = RuleBasedFormatter.format(
            source = words,
            addParagraphs = false,
            sentencePauseSeconds = 10f,
            paragraphPauseSeconds = 10f,
            maxSentenceWords = 24,
        )

        assertEquals(listOf("Как", "дела?"), output.words)
    }

    @Test
    fun canKeepLowercaseAtChunkStart() {
        val output = RuleBasedFormatter.format(
            source = listOf(
                WordTimestamp("и", 0.0f, 0.1f),
                WordTimestamp("мир", 0.1f, 0.2f),
            ),
            addParagraphs = false,
            sentencePauseSeconds = 10f,
            paragraphPauseSeconds = 10f,
            maxSentenceWords = 24,
            ensureFinalPunctuation = false,
            capitalizeFirstWord = false,
        )

        assertEquals(listOf("и", "мир"), output.words)
    }

    @Test
    fun emptyInputReturnsEmptyOutput() {
        val output = RuleBasedFormatter.format(
            source = emptyList(),
            addParagraphs = true,
            sentencePauseSeconds = 0.5f,
            paragraphPauseSeconds = 1f,
            maxSentenceWords = 24,
        )

        assertTrue(output.words.isEmpty())
        assertTrue(output.breakAfter.isEmpty())
    }

    @Test
    fun textModeSplitsByExistingPunctuation() {
        val output = RuleBasedFormatter.formatText(
            source = listOf("привет.", "как", "дела"),
            addParagraphs = false,
            maxSentenceWords = 24,
        )

        assertEquals(listOf("Привет.", "Как", "дела."), output.words)
    }
}
