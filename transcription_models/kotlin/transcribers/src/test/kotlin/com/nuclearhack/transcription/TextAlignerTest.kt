package com.nuclearhack.transcription

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TextAlignerTest {

    @Test
    fun attachesPunctuationAndCapitalization() {
        val aligned = TextAligner.align(
            original = listOf("привет", "как", "дела"),
            formatted = "Привет, как дела?",
        )

        assertEquals(listOf("Привет,", "как", "дела?"), aligned!!.words)
        assertEquals(3, aligned.matched)
        assertTrue(aligned.breakAfter.isEmpty())
    }

    @Test
    fun transfersUppercaseWithoutChangingSpelling() {
        val aligned = TextAligner.align(
            original = listOf("москва", "сити"),
            formatted = "Москва Сити",
        )

        assertEquals(listOf("Москва", "Сити"), aligned!!.words)
    }

    @Test
    fun keepsAbbreviationUppercase() {
        val aligned = TextAligner.align(
            original = listOf("ссср"),
            formatted = "СССР.",
        )

        assertEquals(listOf("СССР."), aligned!!.words)
    }

    @Test
    fun rejectsTextWithDifferentWords() {
        assertNull(
            TextAligner.align(
                original = listOf("привет", "мир"),
                formatted = "Здравствуй, мир!",
            ),
        )
    }

    @Test
    fun rejectsSummaryInsteadOfTranscript() {
        val original = (1..10).map { "слово$it" }

        assertNull(
            TextAligner.align(
                original = original,
                formatted = "Короткое содержание разговора.",
            ),
        )
    }

    @Test
    fun acceptsCoverageExactlyAtThreshold() {
        val original = (1..10).map { "слово$it" }
        val formatted = original.take(9).joinToString(" ") + "."

        val aligned = TextAligner.align(original, formatted)

        assertEquals(9, aligned!!.matched)
        assertEquals(10, aligned.words.size)
        assertEquals("слово9.", aligned.words[8])
        assertEquals("слово10", aligned.words[9])
    }

    @Test
    fun skipsStandalonePunctuation() {
        val aligned = TextAligner.align(
            original = listOf("привет", "мир"),
            formatted = "Привет — мир.",
        )

        assertEquals(listOf("Привет", "мир."), aligned!!.words)
        assertEquals(2, aligned.matched)
    }

    @Test
    fun stripsMarkdownAroundWords() {
        val aligned = TextAligner.align(
            original = listOf("привет", "мир"),
            formatted = "**Привет**, мир!",
        )

        assertEquals(listOf("Привет,", "мир!"), aligned!!.words)
    }

    @Test
    fun mapsParagraphsToOriginalIndices() {
        val aligned = TextAligner.align(
            original = listOf("а", "б", "в", "г", "д", "е"),
            formatted = "а б.\n\nв г.\n\nд е.",
        )

        assertEquals(setOf(1, 3), aligned!!.breakAfter)
        assertEquals(6, aligned.matched)
    }

    @Test
    fun emptyOrPunctuationOnlyResponseIsRejected() {
        assertNull(TextAligner.align(listOf("привет"), ""))
        assertNull(TextAligner.align(listOf("привет"), "... — !"))
    }

    @Test
    fun duplicateWordsKeepOrder() {
        val aligned = TextAligner.align(
            original = listOf("да", "да", "нет"),
            formatted = "Да, да. Нет!",
        )

        assertEquals(listOf("Да,", "да.", "Нет!"), aligned!!.words)
    }

    @Test
    fun mergesHyphenatedWordsBackIntoSeparateOnes() {
        val aligned = TextAligner.align(
            original = listOf("в", "санкт", "петербург"),
            formatted = "В Санкт-Петербург.",
        )

        assertEquals(listOf("В", "Санкт", "Петербург."), aligned!!.words)
        assertEquals(3, aligned.matched)
    }

    @Test
    fun capitalizesBothPartsOfHyphenatedWord() {
        val aligned = TextAligner.align(
            original = listOf("санкт-петербург"),
            formatted = "Санкт-Петербург.",
        )

        assertEquals(listOf("Санкт-Петербург."), aligned!!.words)
    }

    @Test
    fun transfersInternalCapitalLetters() {
        val aligned = TextAligner.align(
            original = listOf("gigaam", "и", "mp3"),
            formatted = "GigaAm и MP3.",
        )

        assertEquals(listOf("GigaAm", "и", "MP3."), aligned!!.words)
    }

    @Test
    fun mapsChangedWordFormButKeepsOriginalWord() {
        val aligned = TextAligner.align(
            original = listOf("если", "модель", "ошибётся", "мы", "уйдём"),
            formatted = "Если модель ошибается, мы уйдём.",
        )

        assertEquals(
            listOf("Если", "модель", "ошибётся,", "мы", "уйдём."),
            aligned!!.words,
        )
        assertEquals(5, aligned.matched)
    }

    @Test
    fun realQwenOutputKeepsProperNamesAndParagraphs() {
        val original = (
            "вчера в москве на красной площади прошёл концерт там было очень " +
                "много людей а сегодня мы поедем в санкт петербург"
            ).split(" ")
        val formatted = (
            "Вчера в Москве на Красной площади прошёл концерт, там было очень " +
                "много людей.\n\nА сегодня мы поедем в Санкт-Петербург."
            )

        val aligned = TextAligner.align(original, formatted)!!

        assertEquals("Вчера", aligned.words[0])
        assertEquals("Москве", aligned.words[2])
        assertEquals("Красной", aligned.words[4])
        assertEquals("Санкт", aligned.words[18])
        assertEquals("Петербург.", aligned.words[19])
        assertEquals(setOf(12), aligned.breakAfter)
    }

    @Test
    fun correctedModeFixesSpellingAndMapsOriginalIndex() {
        val aligned = TextAligner.alignCorrected(
            original = listOf("масква", "красивая"),
            formatted = "Москва красивая.",
        )

        assertEquals(listOf("Москва", "красивая."), aligned!!.words)
        assertEquals(listOf<Int?>(0, 1), aligned.originalIndices)
        assertEquals(1, aligned.corrected)
        assertEquals(0, aligned.removed)
        assertEquals(0, aligned.inserted)
    }

    @Test
    fun correctedModeRemovesFillerWords() {
        val aligned = TextAligner.alignCorrected(
            original = listOf("ну", "привет", "ээ", "как", "дела"),
            formatted = "Привет, как дела?",
        )

        assertEquals(listOf("Привет,", "как", "дела?"), aligned!!.words)
        assertEquals(listOf<Int?>(1, 3, 4), aligned.originalIndices)
        assertEquals(2, aligned.removed)
        assertEquals(0, aligned.inserted)
    }

    @Test
    fun correctedModeInsertsWordWithNullIndex() {
        val aligned = TextAligner.alignCorrected(
            original = listOf("привет", "мир"),
            formatted = "Привет, дорогой мир!",
        )

        assertEquals(listOf("Привет,", "дорогой", "мир!"), aligned!!.words)
        assertEquals(listOf<Int?>(0, null, 1), aligned.originalIndices)
        assertEquals(1, aligned.inserted)
        assertTrue(aligned.approximate)
    }

    @Test
    fun correctedModeRejectsWholesaleRewrite() {
        assertNull(
            TextAligner.alignCorrected(
                original = (1..10).map { "слово$it" },
                formatted = "Совсем другой текст.",
            ),
        )
    }

    @Test
    fun punctuationInsideWordIsKeptFromOriginal() {
        val aligned = TextAligner.align(
            original = listOf("кое-кто", "пришёл"),
            formatted = "Кое-кто пришёл.",
        )

        assertEquals(listOf("Кое-кто", "пришёл."), aligned!!.words)
    }
}
