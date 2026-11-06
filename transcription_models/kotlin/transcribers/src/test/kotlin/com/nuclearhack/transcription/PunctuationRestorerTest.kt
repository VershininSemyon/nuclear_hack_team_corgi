package com.nuclearhack.transcription

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PunctuationRestorerTest {

    @Test
    fun usesLlmWhenWordsArePreserved() {
        val llm = FakeLlm { "Привет, как дела?" }
        val restorer = PunctuationRestorer(llm, PunctuationOptions(maxWordsPerRequest = 100))
        val source = result(
            WordTimestamp("привет", 0.0f, 0.4f),
            WordTimestamp("как", 0.4f, 0.7f),
            WordTimestamp("дела", 1.5f, 1.9f),
        )

        val output = restorer.restoreDetailed(source)

        assertEquals("Привет, как дела?", output.result.text)
        assertEquals(listOf("Привет,", "как", "дела?"), output.result.words.map { it.word })
        assertEquals(1, output.stats.llmChunks)
        assertEquals(0, output.stats.fallbackChunks)
        assertTrue(output.stats.usedLlm)
    }

    @Test
    fun keepsWordTimingsUntouched() {
        val llm = FakeLlm { "Привет, мир!" }
        val restorer = PunctuationRestorer(llm)
        val source = result(
            WordTimestamp("привет", 1.25f, 1.75f),
            WordTimestamp("мир", 2.0f, 2.5f),
        )

        val output = restorer.restore(source)

        assertEquals(1.25f, output.words[0].start, 0.0001f)
        assertEquals(1.75f, output.words[0].end, 0.0001f)
        assertEquals(2.0f, output.words[1].start, 0.0001f)
        assertEquals(2.5f, output.words[1].end, 0.0001f)
    }

    @Test
    fun fallsBackWhenLlmThrows() {
        val llm = FakeLlm { error("model crashed") }
        val restorer = PunctuationRestorer(llm)
        val source = result(
            WordTimestamp("привет", 0.0f, 0.4f),
            WordTimestamp("мир", 0.4f, 0.8f),
        )

        val output = restorer.restoreDetailed(source)

        assertEquals("Привет мир.", output.result.text)
        assertEquals(0, output.stats.llmChunks)
        assertEquals(1, output.stats.fallbackChunks)
    }

    @Test
    fun fallsBackWhenLlmRewritesWords() {
        val llm = FakeLlm { "Совсем другой текст." }
        val restorer = PunctuationRestorer(llm)
        val source = result(
            WordTimestamp("привет", 0.0f, 0.4f),
            WordTimestamp("мир", 0.4f, 0.8f),
        )

        val output = restorer.restoreDetailed(source)

        assertEquals("Привет мир.", output.result.text)
        assertEquals(1, output.stats.fallbackChunks)
    }

    @Test
    fun splitsLongTextIntoSeveralRequests() {
        val llm = FakeLlm { raw -> "$raw." }
        val restorer = PunctuationRestorer(
            llm,
            PunctuationOptions(maxWordsPerRequest = 100),
        )
        val words = (1..300).map { index ->
            WordTimestamp("слово$index", index * 0.5f, index * 0.5f + 0.4f)
        }

        val output = restorer.restoreDetailed(result(*words.toTypedArray()))

        assertEquals(3, output.stats.chunks)
        assertEquals(3, output.stats.llmChunks)
        assertEquals(3, llm.calls)
        assertEquals(300, output.result.words.size)
    }

    @Test
    fun chunkBoundaryPrefersPause() {
        val restorer = PunctuationRestorer(
            llm = null,
            options = PunctuationOptions(maxWordsPerRequest = 100),
        )
        val words = (0 until 200).map { index ->
            val start = if (index == 91) 200f else index.toFloat()
            WordTimestamp("слово$index", start, start + 0.2f)
        }

        val chunks = restorer.chunkWords(words)

        assertEquals(91, chunks.first().size)
    }

    @Test
    fun addParagraphsFalseIgnoresLlmBreaks() {
        val llm = FakeLlm { "а б.\n\nв г." }
        val source = result(
            WordTimestamp("а", 0.0f, 0.1f),
            WordTimestamp("б", 0.1f, 0.2f),
            WordTimestamp("в", 0.2f, 0.3f),
            WordTimestamp("г", 0.3f, 0.4f),
        )

        val withBreaks = PunctuationRestorer(llm).restore(source)
        assertEquals("А б.\n\nВ г.", withBreaks.text)

        val withoutBreaks = PunctuationRestorer(
            llm,
            PunctuationOptions(addParagraphs = false),
        ).restore(source)
        assertEquals("А б. в г.", withoutBreaks.text)
    }

    @Test
    fun restoresPlainTextWithoutTimings() {
        val llm = FakeLlm { "Привет, как дела?" }
        val restorer = PunctuationRestorer(llm)

        assertEquals("Привет, как дела?", restorer.restore("привет как дела"))
    }

    @Test
    fun addsPauseParagraphsWhenLlmGivesNone() {
        val llm = FakeLlm { raw ->
            val words = raw.split(" ").toMutableList()
            words[34] = words[34] + "."
            words.joinToString(" ") + "."
        }
        val restorer = PunctuationRestorer(llm)
        val words = (0 until 70).map { index ->
            val start = index * 0.5f + if (index >= 35) 2f else 0f
            WordTimestamp("слово$index", start, start + 0.4f)
        }

        val output = restorer.restore(result(*words.toTypedArray()))

        assertTrue(output.text.contains("\n\n"))
        val firstParagraph = output.text.substringBefore("\n\n").split(" ")
        assertEquals(35, firstParagraph.size)
    }

    @Test
    fun groupsSentencesIntoParagraphsWhenNoPauses() {
        val llm = FakeLlm { raw ->
            val words = raw.split(" ").toMutableList()
            for (index in words.indices) {
                if (index % 5 == 4) words[index] = words[index] + "."
            }
            words.joinToString(" ") + "."
        }
        val restorer = PunctuationRestorer(llm)
        val words = (0 until 70).map { index ->
            WordTimestamp("слово$index", index * 0.5f, index * 0.5f + 0.4f)
        }

        val output = restorer.restore(result(*words.toTypedArray()))

        assertTrue(output.text.split("\n\n").size >= 2)
    }

    @Test
    fun correctsObviousErrorAndKeepsOriginalTiming() {
        val llm = FakeLlm { "Москва красивая." }
        val restorer = PunctuationRestorer(llm)
        val source = result(
            WordTimestamp("масква", 1.0f, 1.5f),
            WordTimestamp("красивая", 1.5f, 2.0f),
        )

        val output = restorer.restoreDetailed(source)

        assertEquals("Москва красивая.", output.result.text)
        assertEquals(listOf("Москва", "красивая."), output.result.words.map { it.word })
        assertEquals(1.0f, output.result.words[0].start, 0.0001f)
        assertEquals(1.5f, output.result.words[0].end, 0.0001f)
        assertEquals(1, output.stats.correctedWords)
        assertFalse(output.result.approximateTimestamps)
    }

    @Test
    fun removesFillerWordsAndKeepsRemainingTimings() {
        val llm = FakeLlm { "Привет, как дела?" }
        val restorer = PunctuationRestorer(llm)
        val source = result(
            WordTimestamp("ну", 0.0f, 0.2f),
            WordTimestamp("привет", 0.3f, 0.6f),
            WordTimestamp("ээ", 0.7f, 0.9f),
            WordTimestamp("как", 1.0f, 1.2f),
            WordTimestamp("дела", 1.3f, 1.6f),
        )

        val output = restorer.restoreDetailed(source)

        assertEquals(listOf("Привет,", "как", "дела?"), output.result.words.map { it.word })
        assertEquals(0.3f, output.result.words[0].start, 0.0001f)
        assertEquals(1.0f, output.result.words[1].start, 0.0001f)
        assertEquals(1.3f, output.result.words[2].start, 0.0001f)
        assertEquals(2, output.stats.removedWords)
        assertEquals(0, output.stats.insertedWords)
        assertFalse(output.result.approximateTimestamps)
    }

    @Test
    fun insertedWordGetsInterpolatedTiming() {
        val llm = FakeLlm { "Привет, дорогой мир!" }
        val restorer = PunctuationRestorer(llm)
        val source = result(
            WordTimestamp("привет", 0.0f, 0.5f),
            WordTimestamp("мир", 1.0f, 1.5f),
        )

        val output = restorer.restoreDetailed(source)

        assertEquals(listOf("Привет,", "дорогой", "мир!"), output.result.words.map { it.word })
        assertEquals(0.5f, output.result.words[1].start, 0.0001f)
        assertEquals(1.0f, output.result.words[1].end, 0.0001f)
        assertEquals(1, output.stats.insertedWords)
        assertTrue(output.result.approximateTimestamps)
    }

    @Test
    fun strictModeKeepsOriginalSpelling() {
        val llm = FakeLlm { "Москва." }
        val restorer = PunctuationRestorer(
            llm,
            PunctuationOptions(editing = EditingMode.STRICT),
        )
        val source = result(WordTimestamp("масква", 0.0f, 0.5f))

        val output = restorer.restoreDetailed(source)

        assertEquals("Масква.", output.result.text)
        assertEquals(0, output.stats.correctedWords)
    }

    @Test
    fun correctionModeFallsBackOnWholesaleRewrite() {
        val llm = FakeLlm { "Совсем другой текст." }
        val restorer = PunctuationRestorer(llm)
        val source = result(
            WordTimestamp("один", 0.0f, 0.1f),
            WordTimestamp("два", 0.1f, 0.2f),
            WordTimestamp("три", 0.2f, 0.3f),
            WordTimestamp("четыре", 0.3f, 0.4f),
            WordTimestamp("пять", 0.4f, 0.5f),
        )

        val output = restorer.restoreDetailed(source)

        assertFalse(output.result.text.contains("Совсем"))
        assertEquals(1, output.stats.fallbackChunks)
    }

    @Test
    fun chunksAreDroppedWhenWordCountChanges() {
        val llm = FakeLlm { "Привет, как дела?" }
        val restorer = PunctuationRestorer(llm)
        val words = listOf(
            WordTimestamp("ну", 0.0f, 0.2f),
            WordTimestamp("привет", 0.3f, 0.6f),
            WordTimestamp("как", 1.0f, 1.2f),
            WordTimestamp("дела", 1.3f, 1.6f),
        )
        val chunks = words.map { word ->
            AudioSlice(
                start = word.start,
                end = word.end,
                samples = FloatArray(10),
            )
        }
        val source = TranscriptionWithAudio(
            text = words.joinToString(" ") { it.word },
            words = words,
            chunks = chunks,
        )

        val output = restorer.restore(source)

        assertEquals(3, output.words.size)
        assertTrue(output.chunks.isEmpty())
    }

    @Test
    fun fallbackKeepsLowercaseAtChunkBoundary() {
        val restorer = PunctuationRestorer(
            llm = null,
            options = PunctuationOptions(
                maxWordsPerRequest = 2,
                sentencePauseSeconds = 10f,
                paragraphPauseSeconds = 10f,
                maxSentenceWords = 100,
                ensureFinalPunctuation = false,
            ),
        )
        val source = result(
            WordTimestamp("один", 0.0f, 0.1f),
            WordTimestamp("два", 0.1f, 0.2f),
            WordTimestamp("три", 0.2f, 0.3f),
            WordTimestamp("четыре", 0.3f, 0.4f),
        )

        val output = restorer.restore(source)

        assertEquals("Один два три четыре", output.text)
    }

    @Test
    fun emptyResultIsReturnedAsIs() {
        val llm = FakeLlm { "не должно вызываться" }
        val restorer = PunctuationRestorer(llm)
        val source = TranscriptionResult(text = "привет", words = emptyList())

        val output = restorer.restoreDetailed(source)

        assertEquals("привет", output.result.text)
        assertEquals(0, output.stats.chunks)
        assertEquals(0, llm.calls)
        assertFalse(output.stats.usedLlm)
    }

    @Test
    fun closeReleasesLlm() {
        val llm = FakeLlm { "" }

        PunctuationRestorer(llm).close()

        assertTrue(llm.closed)
    }

    private fun result(vararg words: WordTimestamp): TranscriptionResult =
        TranscriptionResult(
            text = words.joinToString(" ") { it.word },
            words = words.toList(),
        )

    private class FakeLlm(
        private val answer: (String) -> String,
    ) : LocalLlm {
        var calls = 0
        var closed = false

        override fun generate(
            systemPrompt: String,
            userPrompt: String,
            maxTokens: Int,
        ): String {
            calls++
            val raw = userPrompt
                .substringAfterLast("<<<")
                .substringBefore(">>>")
                .trim('\n', '\r', ' ')
            return answer(raw)
        }

        override fun close() {
            closed = true
        }
    }
}
